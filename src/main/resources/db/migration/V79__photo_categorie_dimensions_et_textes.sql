-- =====================================================================================
-- Photos : visuel de catégorie, texte alternatif traduit, dimensions et vignette.
--
-- Visuel de catégorie. Un article sans photo propre montre la photo de sa catégorie.
-- La photo reçoit donc une quatrième origine possible, la catégorie, toujours exclusive
-- des trois autres. Écarté : une ligne de photo par article qui pointerait le même
-- fichier. Retirer l'une de ces lignes au back-office effacerait le fichier partagé, et
-- les autres articles afficheraient une image cassée.
--
-- Texte alternatif en néerlandais et en anglais, facultatif, avec repli sur le français
-- comme les libellés du catalogue (V65). Le texte français reste obligatoire
-- (WCAG 2.1, critère 1.1.1).
--
-- Dimensions en pixels, lues au dépôt. Une image dont la page connaît la largeur et la
-- hauteur réserve sa place avant d'être chargée, et la mise en page ne saute pas
-- (indicateur CLS). Nulles pour une photo dont l'en-tête n'a pas pu être lu.
--
-- Vignette : seconde version plus petite du même fichier, servie aux écrans étroits par
-- srcset. Seules les photos de démonstration en ont une. Une photo déposée au
-- back-office reste en une taille : le JDK n'écrit pas le WebP, et le serveur n'a aucun
-- outil de conversion.
-- =====================================================================================

ALTER TABLE photo
    ADD COLUMN categorie_id BIGINT;

ALTER TABLE photo
    ADD CONSTRAINT fk_photo_categorie
        FOREIGN KEY (categorie_id) REFERENCES categorie (id)
            ON UPDATE CASCADE ON DELETE CASCADE;

ALTER TABLE photo
    DROP CONSTRAINT ck_photo_origine_unique;

ALTER TABLE photo
    ADD CONSTRAINT ck_photo_origine_unique
        CHECK (num_nonnulls(service_id, piece_id, intervention_id, categorie_id) = 1);

CREATE INDEX ix_photo_categorie ON photo (categorie_id)
    WHERE categorie_id IS NOT NULL;

ALTER TABLE photo
    ADD COLUMN texte_alt_nl VARCHAR(200),
    ADD COLUMN texte_alt_en VARCHAR(200),
    ADD COLUMN largeur INTEGER,
    ADD COLUMN hauteur INTEGER,
    ADD COLUMN chemin_vignette VARCHAR(255);

-- Les deux dimensions vont ensemble : une largeur sans hauteur ne réserve aucune place.
-- IS NOT NULL écrit en toutes lettres : avec une hauteur nulle, « hauteur > 0 » est
-- inconnu, et une condition inconnue passe une contrainte CHECK.
ALTER TABLE photo
    ADD CONSTRAINT ck_photo_dimensions
        CHECK ((largeur IS NULL AND hauteur IS NULL)
            OR (largeur IS NOT NULL AND hauteur IS NOT NULL AND largeur > 0 AND hauteur > 0));

COMMENT ON COLUMN photo.categorie_id IS
    'Visuel commun aux articles de la catégorie qui n''ont pas de photo propre. Exclusif avec service_id, piece_id et intervention_id.';
COMMENT ON COLUMN photo.texte_alt_nl IS
    'Texte alternatif en néerlandais. Nul : le texte français est rendu à sa place.';
COMMENT ON COLUMN photo.texte_alt_en IS
    'Texte alternatif en anglais. Nul : le texte français est rendu à sa place.';
COMMENT ON COLUMN photo.largeur IS
    'Largeur en pixels, lue dans l''en-tête du fichier au dépôt. Nulle si l''en-tête n''a pas pu être lu.';
COMMENT ON COLUMN photo.hauteur IS
    'Hauteur en pixels, lue dans l''en-tête du fichier au dépôt. Nulle si l''en-tête n''a pas pu être lu.';
COMMENT ON COLUMN photo.chemin_vignette IS
    'Version de 480 pixels de large du même fichier, relative à la racine des médias. Nulle pour une photo déposée au back-office.';
