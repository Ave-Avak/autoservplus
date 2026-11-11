-- Rappel au membre avant un rendez-vous confirmé (F16).
--
-- rappel_envoye_le sert de réservation : la tâche planifiée l'écrit par une mise à jour
-- conditionnelle (rappel_envoye_le IS NULL) avant d'envoyer, si bien qu'un passage
-- concurrent ou repris après une panne ne renvoie pas le rappel. Un changement de
-- créneau la remettra à NULL.
ALTER TABLE rdv ADD COLUMN rappel_envoye_le TIMESTAMPTZ;

COMMENT ON COLUMN rdv.rappel_envoye_le IS
    'Instant de réservation du rappel avant le rendez-vous, NULL tant qu''il n''est pas parti.';

-- Rendez-vous confirmés dont le rappel reste à envoyer, parcourus chaque minute.
CREATE INDEX ix_rdv_rappel_a_envoyer ON rdv (debut)
    WHERE deleted_at IS NULL AND statut = 'CONFIRME' AND rappel_envoye_le IS NULL;

-- Délai du rappel avant le début, en heures. La valeur 0 désactive le rappel.
ALTER TABLE parametre_atelier
    ADD COLUMN delai_rappel_heures SMALLINT NOT NULL DEFAULT 24;

ALTER TABLE parametre_atelier
    ADD CONSTRAINT ck_param_delai_rappel CHECK (delai_rappel_heures >= 0 AND delai_rappel_heures <= 168);
