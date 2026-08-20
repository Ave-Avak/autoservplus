-- =============================================================================
-- Planification par postes et intervalles.
--
-- Le modele initial generait des creneaux de duree fixe a partir des plages
-- d ouverture. Il ne savait exprimer ni la capacite de l atelier (plusieurs
-- rendez-vous en parallele) ni la duree variable d un rendez-vous (une vidange
-- n occupe pas un pont aussi longtemps qu une distribution). Ce modele est
-- remplace par une planification sur ressources : un rendez-vous occupe un
-- intervalle [debut, fin) sur un poste, et la disponibilite est calculee.
--
-- Toutes les regles d atelier deviennent des parametres modifiables par le
-- garage, avec valeurs par defaut et bornes verifiees par la base.
-- =============================================================================

-- btree_gist permet de combiner une egalite (poste_id) et un chevauchement
-- d intervalles (&&) dans une meme contrainte d exclusion. Extension de
-- confiance : le proprietaire de la base peut la creer sans etre superutilisateur.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- -----------------------------------------------------------------------------
-- Postes de travail : la capacite de l atelier est le nombre de postes actifs.
-- -----------------------------------------------------------------------------
CREATE TABLE poste_atelier (
                               id          BIGSERIAL PRIMARY KEY,
                               reference   UUID         NOT NULL DEFAULT gen_random_uuid(),
                               libelle     VARCHAR(80)  NOT NULL,
                               description TEXT,
                               ordre       SMALLINT     NOT NULL DEFAULT 0,
                               actif       BOOLEAN      NOT NULL DEFAULT TRUE,
                               created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                               updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                               created_by  VARCHAR(120),
                               updated_by  VARCHAR(120),
                               deleted_at  TIMESTAMPTZ,
                               deleted_by  VARCHAR(120),
                               CONSTRAINT uq_poste_reference UNIQUE (reference)
);

CREATE UNIQUE INDEX uq_poste_libelle_actif
    ON poste_atelier (lower(libelle))
    WHERE deleted_at IS NULL;

COMMENT ON TABLE poste_atelier IS
    'Ressource planifiable (pont, baie). Un mecanicien ou des competences pourront y etre rattaches.';

-- -----------------------------------------------------------------------------
-- Indisponibilites : fermetures du garage (poste_id NULL) ou blocage d un poste.
-- Couvre jours feries, conges, formation, panne d un pont, rendez-vous fournisseur.
-- -----------------------------------------------------------------------------
CREATE TABLE indisponibilite (
                                 id          BIGSERIAL PRIMARY KEY,
                                 reference   UUID         NOT NULL DEFAULT gen_random_uuid(),
                                 poste_id    BIGINT       REFERENCES poste_atelier (id) ON UPDATE CASCADE ON DELETE CASCADE,
                                 debut       TIMESTAMPTZ  NOT NULL,
                                 fin         TIMESTAMPTZ  NOT NULL,
                                 motif       VARCHAR(200) NOT NULL,
                                 created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                 updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                 created_by  VARCHAR(120),
                                 updated_by  VARCHAR(120),
                                 deleted_at  TIMESTAMPTZ,
                                 deleted_by  VARCHAR(120),
                                 CONSTRAINT uq_indispo_reference UNIQUE (reference),
                                 CONSTRAINT ck_indispo_intervalle CHECK (fin > debut)
);

CREATE INDEX ix_indispo_intervalle
    ON indisponibilite USING gist (tstzrange(debut, fin, '[)'))
    WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- Parametres d atelier : une seule ligne en V1 (mono-tenant). La colonne id est
-- contrainte a 1 pour interdire une seconde ligne. En V2 multi-tenant, la
-- contrainte sera remplacee par une cle garage_id.
-- -----------------------------------------------------------------------------
CREATE TABLE parametre_atelier (
                                   id                              SMALLINT    PRIMARY KEY DEFAULT 1,
                                   fuseau_horaire                  VARCHAR(60) NOT NULL DEFAULT 'Europe/Brussels',
                                   pas_minutes                     SMALLINT    NOT NULL DEFAULT 30,
                                   tampon_minutes                  SMALLINT    NOT NULL DEFAULT 10,
                                   delai_minimal_heures            SMALLINT    NOT NULL DEFAULT 24,
                                   horizon_jours                   SMALLINT    NOT NULL DEFAULT 60,
                                   delai_annulation_heures         SMALLINT    NOT NULL DEFAULT 24,
                                   confirmation_automatique        BOOLEAN     NOT NULL DEFAULT FALSE,
                                   max_rdv_en_attente_par_membre   SMALLINT    NOT NULL DEFAULT 3,
                                   updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                   updated_by                      VARCHAR(120),
                                   CONSTRAINT ck_param_ligne_unique     CHECK (id = 1),
                                   CONSTRAINT ck_param_pas              CHECK (pas_minutes IN (15, 30, 45, 60)),
                                   CONSTRAINT ck_param_tampon           CHECK (tampon_minutes BETWEEN 0 AND 120),
                                   CONSTRAINT ck_param_delai_minimal    CHECK (delai_minimal_heures BETWEEN 0 AND 168),
                                   CONSTRAINT ck_param_horizon          CHECK (horizon_jours BETWEEN 1 AND 365),
                                   CONSTRAINT ck_param_delai_annulation CHECK (delai_annulation_heures BETWEEN 0 AND 168),
                                   CONSTRAINT ck_param_max_attente      CHECK (max_rdv_en_attente_par_membre BETWEEN 1 AND 20)
);

-- La ligne de parametres fait partie de la structure : l application ne peut pas
-- fonctionner sans elle. Ce n est pas une donnee de demonstration.
INSERT INTO parametre_atelier (id) VALUES (1);

COMMENT ON COLUMN parametre_atelier.fuseau_horaire IS
    'Les plages d ouverture sont en heure locale ; ce fuseau sert a les projeter en instants UTC.';

-- -----------------------------------------------------------------------------
-- Rendez-vous : de la reference a un creneau vers un intervalle sur un poste.
-- -----------------------------------------------------------------------------
DROP INDEX IF EXISTS uq_rdv_creneau_actif;
ALTER TABLE rdv DROP CONSTRAINT fk_rdv_creneau;
ALTER TABLE rdv DROP COLUMN creneau_id;

ALTER TABLE rdv
    ADD COLUMN debut    TIMESTAMPTZ NOT NULL,
    ADD COLUMN fin      TIMESTAMPTZ NOT NULL,
    ADD COLUMN poste_id BIGINT      NOT NULL
        REFERENCES poste_atelier (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    ADD CONSTRAINT ck_rdv_intervalle CHECK (fin > debut);

CREATE INDEX ix_rdv_poste_debut ON rdv (poste_id, debut) WHERE deleted_at IS NULL;

-- Deux rendez-vous actifs ne peuvent pas se chevaucher sur un meme poste.
-- La garantie est portee par le moteur : deux transactions simultanees sur le
-- meme intervalle voient la seconde rejetee, quel que soit l etat du code.
ALTER TABLE rdv
    ADD CONSTRAINT ex_rdv_poste_intervalle
    EXCLUDE USING gist (poste_id WITH =, tstzrange(debut, fin, '[)') WITH &&)
    WHERE (deleted_at IS NULL AND statut IN ('EN_ATTENTE', 'CONFIRME'));

-- -----------------------------------------------------------------------------
-- La table des creneaux generes n a plus de raison d etre.
-- -----------------------------------------------------------------------------
DROP TABLE creneau_horaire;