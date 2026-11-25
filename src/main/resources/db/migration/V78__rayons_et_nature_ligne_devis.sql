-- Rayon d'une catégorie de produits, et nature d'une ligne de devis.
--
-- 1) RAYON (categorie.rayon)
--
--    Le catalogue public range les produits en trois rayons : pièces auto, accessoires et
--    entretien. Le rayon appartient à la catégorie, pas à l'article : un article change de
--    rayon en changeant de catégorie. Une catégorie de prestations n'a pas de rayon, le type
--    dit déjà qu'elle se réserve. Une quatrième valeur PRESTATIONS a été écartée : elle
--    doublerait la colonne type.
--
--    Les catégories de pièces existantes vont dans le rayon des pièces auto, seul rayon
--    connu avant cette migration.

ALTER TABLE categorie ADD COLUMN rayon VARCHAR(20);

UPDATE categorie SET rayon = 'PIECES_AUTO' WHERE type = 'PIECE';

-- Le IS NOT NULL est voulu : sans lui, un rayon nul rendrait la condition inconnue, et une
-- contrainte CHECK laisse passer l'inconnu.
ALTER TABLE categorie ADD CONSTRAINT ck_categorie_rayon
    CHECK ((type = 'SERVICE' AND rayon IS NULL)
           OR (type = 'PIECE' AND rayon IS NOT NULL
               AND rayon IN ('PIECES_AUTO', 'ACCESSOIRES', 'ENTRETIEN')));

COMMENT ON COLUMN categorie.rayon IS
    'Rayon du catalogue public : PIECES_AUTO, ACCESSOIRES ou ENTRETIEN pour une catégorie de produits. Toujours nul pour une catégorie de prestations.';

-- 2) NATURE D'UNE LIGNE DE DEVIS (ligne_devis.nature)
--
--    Le devis est un instantané sans clé vers l'article. Sans la nature, rien ne dit si une
--    ligne porte une pièce ou une prestation, donc si le choix des pièces (V77) peut y
--    figurer. La contrainte ck_ligne_devis_pieces_client le tient en base, comme
--    ck_ligne_pieces_client pour le panier.
--
--    Une ligne qui porte un choix est une prestation. Pour les autres, rien dans la ligne ne
--    permet de trancher : la migration s'arrête plutôt que de deviner, une nature fausse
--    rendant le contrôle faux sans bruit. Aucune base ne porte de devis antérieur à V77 :
--    le devis date du même cycle de développement, et le dump de démonstration est régénéré
--    au gel.

ALTER TABLE ligne_devis ADD COLUMN nature VARCHAR(20);

UPDATE ligne_devis SET nature = 'PRESTATION' WHERE pieces_client IS NOT NULL;

DO $$
DECLARE
    sans_nature BIGINT;
BEGIN
    SELECT count(*) INTO sans_nature FROM ligne_devis WHERE nature IS NULL;
    IF sans_nature > 0 THEN
        RAISE EXCEPTION 'V78 : % ligne(s) de devis sans nature déductible. Renseigner ligne_devis.nature (PIECE ou PRESTATION) d''après le libellé, puis relancer la migration.', sans_nature;
    END IF;
END
$$;

ALTER TABLE ligne_devis ALTER COLUMN nature SET NOT NULL;

ALTER TABLE ligne_devis ADD CONSTRAINT ck_ligne_devis_nature
    CHECK (nature IN ('PIECE', 'PRESTATION'));

ALTER TABLE ligne_devis ADD CONSTRAINT ck_ligne_devis_pieces_client
    CHECK (pieces_client IS NULL OR nature = 'PRESTATION');

COMMENT ON COLUMN ligne_devis.nature IS
    'PIECE ou PRESTATION, reprise de la ligne du panier à l''émission. Le choix des pièces n''existe que sur une PRESTATION.';
