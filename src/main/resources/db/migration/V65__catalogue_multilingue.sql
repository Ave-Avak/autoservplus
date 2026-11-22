-- =====================================================================================
-- Libellés du catalogue en néerlandais et en anglais.
--
-- Le site est servi en trois langues, mais le libellé et la description d'une
-- prestation ou d'une pièce, comme le libellé d'une catégorie, n'existaient qu'en une :
-- un visiteur néerlandophone lisait une page traduite autour d'un catalogue en français.
--
-- Une colonne par langue, et non une table de traductions. Les trois langues sont fixées
-- par le cahier des charges : une colonne se lit sans jointure, se trie et se cherche
-- directement, et le repli sur le français n'est qu'une valeur nulle. Une quatrième
-- langue s'ajouterait par une migration, comme celle-ci.
--
-- Les colonnes existantes (libelle, description) restent le français, langue d'origine
-- et de repli : elles gardent leur contrainte NOT NULL et leur unicité. Les nouvelles
-- sont facultatives. Une valeur nulle signifie « non traduit », jamais une chaîne vide.
--
-- La description d'une catégorie n'est pas traduite : aucun écran ne l'affiche.
-- =====================================================================================

ALTER TABLE service
    ADD COLUMN libelle_nl     VARCHAR(150),
    ADD COLUMN libelle_en     VARCHAR(150),
    ADD COLUMN description_nl TEXT,
    ADD COLUMN description_en TEXT;

ALTER TABLE piece
    ADD COLUMN libelle_nl     VARCHAR(150),
    ADD COLUMN libelle_en     VARCHAR(150),
    ADD COLUMN description_nl TEXT,
    ADD COLUMN description_en TEXT;

ALTER TABLE categorie
    ADD COLUMN libelle_nl VARCHAR(120),
    ADD COLUMN libelle_en VARCHAR(120);

COMMENT ON COLUMN service.libelle IS 'Libellé en français, langue d''origine et de repli.';
COMMENT ON COLUMN service.libelle_nl IS 'Libellé en néerlandais. NULL : non traduit, le français s''affiche.';
COMMENT ON COLUMN service.libelle_en IS 'Libellé en anglais. NULL : non traduit, le français s''affiche.';
COMMENT ON COLUMN service.description_nl IS 'Description en néerlandais. NULL : non traduite.';
COMMENT ON COLUMN service.description_en IS 'Description en anglais. NULL : non traduite.';

COMMENT ON COLUMN piece.libelle IS 'Libellé en français, langue d''origine et de repli.';
COMMENT ON COLUMN piece.libelle_nl IS 'Libellé en néerlandais. NULL : non traduit, le français s''affiche.';
COMMENT ON COLUMN piece.libelle_en IS 'Libellé en anglais. NULL : non traduit, le français s''affiche.';
COMMENT ON COLUMN piece.description_nl IS 'Description en néerlandais. NULL : non traduite.';
COMMENT ON COLUMN piece.description_en IS 'Description en anglais. NULL : non traduite.';

COMMENT ON COLUMN categorie.libelle IS 'Libellé en français, langue d''origine et de repli.';
COMMENT ON COLUMN categorie.libelle_nl IS 'Libellé en néerlandais. NULL : non traduit, le français s''affiche.';
COMMENT ON COLUMN categorie.libelle_en IS 'Libellé en anglais. NULL : non traduit, le français s''affiche.';
