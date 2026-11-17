-- Début de l'attente d'une réponse du garage (F16).
--
-- La relance au garage part à mi-chemin entre ce début et l'échéance de réponse. Elle
-- se mesurait depuis created_at : un rendez-vous déplacé par le membre repasse en
-- attente avec une échéance neuve, et une demande ancienne aurait été relancée dès le
-- passage suivant de la tâche. updated_at est écarté : toute écriture future sur une
-- demande en attente le déplacerait, et la relance avec lui, sans que rien ne le dise.
-- La colonne est posée par l'entité, à la création et quand un déplacement remet le
-- rendez-vous en attente.
ALTER TABLE rdv ADD COLUMN en_attente_depuis TIMESTAMPTZ;

UPDATE rdv SET en_attente_depuis = created_at;

-- Le défaut sert les insertions écrites hors de l'entité (graine de démonstration,
-- jeux de tests), qui ne connaissent pas la colonne : il vaut alors created_at, posé
-- au même instant par son propre défaut.
ALTER TABLE rdv ALTER COLUMN en_attente_depuis SET DEFAULT now();
ALTER TABLE rdv ALTER COLUMN en_attente_depuis SET NOT NULL;

COMMENT ON COLUMN rdv.en_attente_depuis IS
    'Début de l''attente d''une réponse du garage : création, ou dernier déplacement par le membre qui a remis le rendez-vous en attente.';
COMMENT ON COLUMN rdv.echeance_reponse IS
    'Instant où une demande sans réponse expire. Calculé à la création, et de nouveau quand le membre déplace le rendez-vous en mode manuel.';
