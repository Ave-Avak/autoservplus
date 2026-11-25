-- =====================================================================================
-- Référence publique d'une photo.
--
-- Une photo était adressée par sa clé séquentielle (/images/galerie/{id}), sous une
-- route ouverte à tous et mise en cache public. Compter de un en un suffisait donc à
-- lire les photos d'intervention de tous les membres. La photo reçoit une référence
-- aléatoire, la seule qui sort de la base, comme les autres ressources du site. La clé
-- séquentielle reste la clé primaire et ne s'affiche plus.
--
-- La valeur par défaut est évaluée ligne par ligne : chaque photo existante reçoit sa
-- propre référence.
-- =====================================================================================

ALTER TABLE photo ADD COLUMN reference UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE photo ADD CONSTRAINT uq_photo_reference UNIQUE (reference);

COMMENT ON COLUMN photo.reference IS
    'Référence publique de la photo, seule adresse exposée. Une photo d''intervention '
    'ne se sert qu''au titulaire de l''intervention et au back-office.';
