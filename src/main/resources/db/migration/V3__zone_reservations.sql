-- =====================================================================================
-- Zone 3 : vehicules et reservations
-- Le parc de vehicules des membres, les horaires d ouverture du garage, les creneaux
-- generes a partir de ces horaires, et les rendez-vous qui les occupent.
-- Reference : livrable 08, livrable 09, F9 a F11, F16
-- =====================================================================================

CREATE TABLE vehicule (
                          id             BIGSERIAL     PRIMARY KEY,
                          reference      UUID          NOT NULL DEFAULT gen_random_uuid(),
                          membre_id      BIGINT        NOT NULL,
                          plaque         VARCHAR(15)   NOT NULL,
                          marque         VARCHAR(60)   NOT NULL,
                          modele         VARCHAR(80)   NOT NULL,
                          motorisation   VARCHAR(20)   NOT NULL,
                          annee          SMALLINT,
                          kilometrage    INTEGER,
                          numero_chassis VARCHAR(20),
                          actif          BOOLEAN       NOT NULL DEFAULT TRUE,
                          created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          created_by     VARCHAR(120),
                          updated_by     VARCHAR(120),
                          deleted_at     TIMESTAMPTZ,
                          deleted_by     VARCHAR(120),
                          CONSTRAINT uq_vehicule_reference UNIQUE (reference),
                          CONSTRAINT fk_vehicule_membre FOREIGN KEY (membre_id)
                              REFERENCES utilisateur (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                          CONSTRAINT ck_vehicule_motorisation CHECK (motorisation IN ('ESSENCE', 'DIESEL', 'HYBRIDE', 'ELECTRIQUE', 'GPL', 'AUTRE')),
                          CONSTRAINT ck_vehicule_annee       CHECK (annee IS NULL OR (annee >= 1900 AND annee <= 2100)),
                          CONSTRAINT ck_vehicule_kilometrage CHECK (kilometrage IS NULL OR kilometrage >= 0)
);
COMMENT ON COLUMN vehicule.plaque IS 'Unique parmi les vehicules non supprimes : une plaque peut etre reattribuee.';
CREATE UNIQUE INDEX uq_vehicule_plaque_active ON vehicule (plaque) WHERE deleted_at IS NULL;
CREATE INDEX ix_vehicule_membre ON vehicule (membre_id) WHERE deleted_at IS NULL;

CREATE TABLE plage_ouverture (
                                 id           BIGSERIAL   PRIMARY KEY,
                                 jour_semaine SMALLINT    NOT NULL,
                                 heure_debut  TIME        NOT NULL,
                                 heure_fin    TIME        NOT NULL,
                                 actif        BOOLEAN     NOT NULL DEFAULT TRUE,
                                 created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                                 updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                                 created_by   VARCHAR(120),
                                 updated_by   VARCHAR(120),
                                 deleted_at   TIMESTAMPTZ,
                                 deleted_by   VARCHAR(120),
                                 CONSTRAINT ck_plage_jour   CHECK (jour_semaine BETWEEN 1 AND 7),
                                 CONSTRAINT ck_plage_heures CHECK (heure_fin > heure_debut)
);
COMMENT ON COLUMN plage_ouverture.jour_semaine IS '1 = lundi ... 7 = dimanche, norme ISO 8601.';

CREATE TABLE creneau_horaire (
                                 id         BIGSERIAL   PRIMARY KEY,
                                 reference  UUID        NOT NULL DEFAULT gen_random_uuid(),
                                 debut      TIMESTAMPTZ NOT NULL,
                                 fin        TIMESTAMPTZ NOT NULL,
                                 disponible BOOLEAN     NOT NULL DEFAULT TRUE,
                                 version    BIGINT      NOT NULL DEFAULT 0,
                                 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                 created_by VARCHAR(120),
                                 updated_by VARCHAR(120),
                                 deleted_at TIMESTAMPTZ,
                                 deleted_by VARCHAR(120),
                                 CONSTRAINT uq_creneau_reference UNIQUE (reference),
                                 CONSTRAINT uq_creneau_debut     UNIQUE (debut),
                                 CONSTRAINT ck_creneau_bornes    CHECK (fin > debut)
);
COMMENT ON COLUMN creneau_horaire.version IS 'Verrouillage optimiste JPA : empeche la double reservation du meme creneau.';
CREATE INDEX ix_creneau_disponibilite ON creneau_horaire (debut, disponible) WHERE deleted_at IS NULL;

CREATE TABLE rdv (
                     id              BIGSERIAL   PRIMARY KEY,
                     reference       UUID        NOT NULL DEFAULT gen_random_uuid(),
                     numero          VARCHAR(20) NOT NULL,
                     membre_id       BIGINT      NOT NULL,
                     vehicule_id     BIGINT      NOT NULL,
                     creneau_id      BIGINT      NOT NULL,
                     statut          VARCHAR(25) NOT NULL DEFAULT 'EN_ATTENTE',
                     commentaire     TEXT,
                     motif_refus     TEXT,
                     date_annulation TIMESTAMPTZ,
                     version         BIGINT      NOT NULL DEFAULT 0,
                     created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                     updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                     created_by      VARCHAR(120),
                     updated_by      VARCHAR(120),
                     deleted_at      TIMESTAMPTZ,
                     deleted_by      VARCHAR(120),
                     CONSTRAINT uq_rdv_reference UNIQUE (reference),
                     CONSTRAINT uq_rdv_numero    UNIQUE (numero),
                     CONSTRAINT uq_rdv_creneau   UNIQUE (creneau_id),
                     CONSTRAINT fk_rdv_membre FOREIGN KEY (membre_id)
                         REFERENCES utilisateur (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                     CONSTRAINT fk_rdv_vehicule FOREIGN KEY (vehicule_id)
                         REFERENCES vehicule (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                     CONSTRAINT fk_rdv_creneau FOREIGN KEY (creneau_id)
                         REFERENCES creneau_horaire (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                     CONSTRAINT ck_rdv_statut CHECK (statut IN ('EN_ATTENTE', 'CONFIRME', 'REFUSE', 'ANNULE', 'HONORE', 'ABSENT'))
);
COMMENT ON CONSTRAINT uq_rdv_creneau ON rdv IS 'Un creneau ne porte qu un seul rendez-vous : regle RM-08.';
CREATE INDEX ix_rdv_membre ON rdv (membre_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_rdv_statut ON rdv (statut)    WHERE deleted_at IS NULL;

CREATE TABLE rdv_service (
                             id                 BIGSERIAL     PRIMARY KEY,
                             rdv_id             BIGINT        NOT NULL,
                             service_id         BIGINT        NOT NULL,
                             quantite           SMALLINT      NOT NULL DEFAULT 1,
                             prix_unitaire_htva NUMERIC(10,2) NOT NULL,
                             taux_tva           NUMERIC(5,2)  NOT NULL,
                             created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
                             updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
                             created_by         VARCHAR(120),
                             updated_by         VARCHAR(120),
                             CONSTRAINT uq_rdv_service UNIQUE (rdv_id, service_id),
                             CONSTRAINT fk_rdv_service_rdv FOREIGN KEY (rdv_id)
                                 REFERENCES rdv (id) ON DELETE CASCADE ON UPDATE CASCADE,
                             CONSTRAINT fk_rdv_service_svc FOREIGN KEY (service_id)
                                 REFERENCES service (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                             CONSTRAINT ck_rdv_service_qte  CHECK (quantite > 0),
                             CONSTRAINT ck_rdv_service_prix CHECK (prix_unitaire_htva >= 0)
);
COMMENT ON COLUMN rdv_service.prix_unitaire_htva IS 'Prix fige a la reservation : le catalogue peut evoluer ensuite.';