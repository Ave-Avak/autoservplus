-- =====================================================================================
-- Historisation des modifications du catalogue (A2, A5 du CdC : « les modifications
-- sont historisees — qui, quand, quoi »).
--
-- Une ligne par CHAMP reellement modifie, et non par enregistrement : c'est ce qui
-- rend le « quoi » requetable (« qui a touche au prix de cette piece ce mois-ci ? »)
-- sans avoir a diffuser deux photos completes de l'entite. Une modification qui ne
-- change aucune valeur n'ecrit rien.
--
-- Meme patron que historique_statut_intervention (V23) : journal append-only, ecrit
-- dans la MEME transaction que la modification qu'il trace — un rollback emporte les
-- deux, le journal ne peut donc ni manquer un changement ni en inventer un. Pas de
-- suppression logique : rien ne se « supprime » dans un journal.
--
-- `horodatage` est la donnee METIER (instant fourni par l'horloge applicative
-- injectee, donc deterministe en test) ; `created_at` reste l'audit TECHNIQUE de la
-- base. Les deux coincident en pratique, ils n'ont ni la meme source ni le meme role.
--
-- `auteur_id` est nullable en ON DELETE SET NULL : la trace doit survivre a la
-- suppression du compte (l'auteur s'efface, le changement reste) et un futur
-- traitement systeme n'aura aucun utilisateur authentifie a declarer.
--
-- PAS de cle etrangere sur entite_id, deliberement, pour deux raisons :
--   1. la colonne est polymorphe (service OU piece selon type_entite) ; une FK ne
--      peut pas viser deux tables, et un couple de colonnes nullables avec CHECK
--      d'exclusivite (le patron de ligne_intervention) n'apporterait rien ici — ce
--      journal n'a aucune jointure metier a faire, il se lit par (type, id) ;
--   2. A3/A6 autorisent la suppression PHYSIQUE d'un element jamais reference
--      (RM-29) ; une FK RESTRICT rendrait ce journal bloquant, une FK CASCADE
--      effacerait l'historique de ce qui a ete supprime — exactement ce qu'un
--      journal doit conserver. Les ids BIGSERIAL n'etant jamais reutilises, la
--      trace reste sans ambiguite meme apres disparition de sa cible.
--
-- champ_modifie porte le nom TECHNIQUE du champ (libelle, prixHtva, tauxTva...) :
-- une valeur stable dans le temps, donc requetable, que la couche de presentation
-- traduira via l'i18n le jour ou l'historique sera affiche.
--
-- valeur_avant / valeur_apres sont du TEXT et non un type metier : le journal est
-- generique par nature (montants, entiers, libelles, codes de categorie). Elles sont
-- nullables des deux cotes — une description passe de NULL a une valeur et inversement.
-- =====================================================================================

CREATE TABLE historique_modification_catalogue (
    id            BIGSERIAL   PRIMARY KEY,
    type_entite   VARCHAR(20) NOT NULL,
    entite_id     BIGINT      NOT NULL,
    champ_modifie VARCHAR(60) NOT NULL,
    valeur_avant  TEXT,
    valeur_apres  TEXT,
    horodatage    TIMESTAMPTZ NOT NULL,
    auteur_id     BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    VARCHAR(120),
    updated_by    VARCHAR(120),
    CONSTRAINT fk_histo_catalogue_auteur FOREIGN KEY (auteur_id)
        REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_histo_catalogue_type CHECK (type_entite IN ('PRESTATION', 'PIECE'))
);

COMMENT ON TABLE historique_modification_catalogue IS
    'Journal append-only des modifications du catalogue (A2, A5). '
    'Une ligne par champ reellement modifie, ecrite dans la transaction de la modification.';
COMMENT ON COLUMN historique_modification_catalogue.entite_id IS
    'Identifiant de la prestation (service.id) ou de la piece (piece.id) selon type_entite. '
    'Volontairement sans FK : colonne polymorphe, et la trace survit a la suppression A3/A6.';
COMMENT ON COLUMN historique_modification_catalogue.champ_modifie IS
    'Nom technique du champ modifie, stable dans le temps ; la traduction est affaire de presentation.';
COMMENT ON COLUMN historique_modification_catalogue.horodatage IS
    'Instant metier de la modification (horloge applicative injectee), distinct de l audit created_at.';

-- Lecture unique du journal : l'historique d'un element du catalogue, du plus recent
-- au plus ancien. L'index couvre aussi bien le filtre (type_entite, entite_id) que le tri.
CREATE INDEX ix_historique_modification_catalogue
    ON historique_modification_catalogue (type_entite, entite_id, horodatage);
