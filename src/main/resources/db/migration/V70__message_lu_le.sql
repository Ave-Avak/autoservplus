-- =====================================================================================
-- Date de lecture d'un message (messagerie, groupe H).
--
-- lu dit qu'un message a été lu par son destinataire, lu_le dit quand. Le fil affiché
-- au membre se rafraîchit seul : un message du garage arrivé pendant que le fil est
-- ouvert est lu à son affichage, et l'export des données (F22) restitue cette date.
--
-- Les messages déjà lus reçoivent leur date de mise à jour : un message ne change plus
-- après son envoi, hormis son drapeau de lecture, donc updated_at est l'instant où il a
-- été marqué lu.
--
-- La contrainte admet un message lu sans date, et non l'inverse. La graine de
-- démonstration (db/demo/V900), jouée après cette migration sur une base vierge, insère
-- des messages lus sans connaître cette colonne : une contrainte d'équivalence la ferait
-- échouer.
-- =====================================================================================

ALTER TABLE message ADD COLUMN lu_le TIMESTAMPTZ;

UPDATE message SET lu_le = updated_at WHERE lu;

ALTER TABLE message
    ADD CONSTRAINT ck_message_lecture CHECK (lu_le IS NULL OR lu);

COMMENT ON COLUMN message.lu_le IS
    'Instant où le destinataire a lu le message. NULL pour un message non lu, et pour un message lu de la graine de démonstration.';

-- Le rafraîchissement d'un fil et le compteur de l'en-tête ne cherchent que les
-- messages non lus.
CREATE INDEX ix_message_non_lus ON message (conversation_id) WHERE NOT lu;
