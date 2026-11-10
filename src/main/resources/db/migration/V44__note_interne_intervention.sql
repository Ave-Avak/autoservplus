-- =====================================================================================
-- Note interne de l'atelier sur une intervention (A16).
--
-- Texte libre réservé au garage, jamais affiché au membre. Il reste une donnée
-- personnelle du membre dont le véhicule est concerné : l'export (art. 15) le
-- restitue et la suppression de compte le vide.
--
-- La colonne diagnostic, posée par le socle et jamais employée, n'est pas reprise :
-- elle reste réservée à un diagnostic visible du membre. La note a donc sa propre
-- colonne, plutôt que de détourner un nom qui promettait l'inverse.
-- =====================================================================================

ALTER TABLE intervention ADD COLUMN note_interne varchar(2000);

COMMENT ON COLUMN intervention.note_interne IS
    'Note de l''atelier, invisible du membre. Exportée sur demande d''accès, vidée à la suppression du compte.';
