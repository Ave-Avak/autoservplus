-- Confirmation automatique par défaut, et échéance de réponse des demandes en mode
-- manuel (F16).
--
-- Confirmation automatique : le planning modélisé (plages, postes, indisponibilités,
-- contrainte d'exclusion) ne propose qu'un créneau réellement libre, le garage n'a
-- donc rien à vérifier avant d'accepter. Il garde l'annulation motivée. Écart au cahier
-- des charges, qui prévoyait une confirmation manuelle.
ALTER TABLE parametre_atelier ALTER COLUMN confirmation_automatique SET DEFAULT TRUE;
UPDATE parametre_atelier SET confirmation_automatique = TRUE;

-- Délai de réponse du garage en mode manuel, en heures. Au moins deux heures : un
-- créneau n'est proposé que si le garage dispose de ce temps pour répondre.
ALTER TABLE parametre_atelier
    ADD COLUMN delai_reponse_heures SMALLINT NOT NULL DEFAULT 24;

ALTER TABLE parametre_atelier
    ADD CONSTRAINT ck_param_delai_reponse CHECK (delai_reponse_heures >= 2 AND delai_reponse_heures <= 168);

-- Une demande restée sans réponse à son échéance passe EXPIRE, sans motif à rédiger.
-- Comme REFUSE et ANNULE, le statut sort de la contrainte d'exclusion et libère le
-- créneau.
ALTER TABLE rdv DROP CONSTRAINT ck_rdv_statut;
ALTER TABLE rdv ADD CONSTRAINT ck_rdv_statut CHECK (statut IN
    ('EN_ATTENTE', 'CONFIRME', 'REFUSE', 'ANNULE', 'HONORE', 'ABSENT', 'EXPIRE'));

-- Échéance calculée à la création et figée : un changement ultérieur des plages, des
-- paramètres ou du mode ne la déplace pas. NULL pour une demande confirmée d'office et
-- pour les demandes antérieures à cette migration, qui n'expirent jamais.
ALTER TABLE rdv ADD COLUMN echeance_reponse TIMESTAMPTZ;

-- Réservation de la relance au garage à mi-délai, sur le patron de rappel_envoye_le.
ALTER TABLE rdv ADD COLUMN relance_envoyee_le TIMESTAMPTZ;

COMMENT ON COLUMN rdv.echeance_reponse IS
    'Instant où une demande sans réponse expire. Calculé à la création, jamais recalculé.';
COMMENT ON COLUMN rdv.relance_envoyee_le IS
    'Instant de réservation de la relance au garage, NULL tant qu''elle n''est pas partie.';

CREATE INDEX ix_rdv_echeance_reponse ON rdv (echeance_reponse)
    WHERE deleted_at IS NULL AND statut = 'EN_ATTENTE' AND echeance_reponse IS NOT NULL;
