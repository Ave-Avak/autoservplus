-- =====================================================================================
-- Chronologie horodatee des changements de statut d'une intervention (F17).
--
-- Chaque transition de la machine a etats ecrit une ligne, dans la MEME transaction
-- que la transition elle-meme : la chronologie ne peut ni manquer un changement ni
-- en inventer un. La ligne de creation porte statut_avant NULL — il n'y a pas d'etat
-- anterieur a la naissance du dossier.
--
-- `horodatage` est la donnee METIER (l'instant de la transition, fourni par l'horloge
-- injectee de l'application, donc deterministe en test) ; `created_at` reste l'audit
-- TECHNIQUE (now() de la base). Les deux coincident en pratique, mais ils n'ont ni la
-- meme source ni le meme role, on ne les fusionne pas.
--
-- `auteur_id` est nullable : une transition peut venir d'un traitement systeme (aucun
-- utilisateur authentifie) et l'historique doit survivre a la suppression du compte
-- (ON DELETE SET NULL — la trace reste, l'auteur s'efface).
--
-- Pas de suppression logique : un journal est append-only, rien ne s'y "supprime"
-- (meme precedent que ligne_intervention, qui ne porte pas non plus deleted_at).
-- Les colonnes d'audit created/updated restent, comme sur toutes les tables ecrites
-- par l'application.
-- =====================================================================================

CREATE TABLE historique_statut_intervention (
                                                id              BIGSERIAL    PRIMARY KEY,
                                                intervention_id BIGINT       NOT NULL,
                                                statut_avant    VARCHAR(30),
                                                statut_apres    VARCHAR(30)  NOT NULL,
                                                horodatage      TIMESTAMPTZ  NOT NULL,
                                                auteur_id       BIGINT,
                                                motif           VARCHAR(500),
                                                created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                                updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                                                created_by      VARCHAR(120),
                                                updated_by      VARCHAR(120),
                                                CONSTRAINT fk_histo_statut_interv FOREIGN KEY (intervention_id)
                                                    REFERENCES intervention (id) ON DELETE CASCADE ON UPDATE CASCADE,
                                                CONSTRAINT fk_histo_statut_auteur FOREIGN KEY (auteur_id)
                                                    REFERENCES utilisateur (id) ON DELETE SET NULL ON UPDATE CASCADE,
                                                CONSTRAINT ck_histo_statut_avant CHECK (statut_avant IN
                                                    ('PLANIFIEE', 'EN_COURS', 'SUSPENDUE',
                                                     'ATTENTE_VALIDATION_MEMBRE', 'TERMINEE', 'ANNULEE')),
                                                CONSTRAINT ck_histo_statut_apres CHECK (statut_apres IN
                                                    ('PLANIFIEE', 'EN_COURS', 'SUSPENDUE',
                                                     'ATTENTE_VALIDATION_MEMBRE', 'TERMINEE', 'ANNULEE'))
);

COMMENT ON TABLE historique_statut_intervention IS
    'Journal append-only des transitions de statut d une intervention (F17). '
    'Une ligne par transition, ecrite dans la transaction de la transition.';
COMMENT ON COLUMN historique_statut_intervention.statut_avant IS
    'NULL pour la ligne de creation : le dossier n a pas d etat anterieur.';
COMMENT ON COLUMN historique_statut_intervention.horodatage IS
    'Instant metier de la transition (horloge applicative injectee), distinct de l audit created_at.';

-- Lecture unique du journal : la chronologie d une intervention, en ordre.
CREATE INDEX ix_historique_statut_intervention
    ON historique_statut_intervention (intervention_id, horodatage);
