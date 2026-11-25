-- =====================================================================================
-- Unité figée et quantité décimale des lignes d'intervention (Q-H-36, Q-H-37, groupe H,
-- H4-1b).
--
-- 1) UNITÉ FIGÉE SUR LA LIGNE
--
--    Comme le libellé et le prix, l'unité est recopiée à la création de la ligne : PIECE
--    pour une pièce, FORFAIT ou HEURE selon la prestation (service.unite, V72). Pas de
--    valeur par défaut : une ligne écrite sans unité échoue ici, au lieu d'être complétée
--    en silence. La graine de démonstration, écrite avant cette migration, reçoit la sienne
--    d'un déclencheur de db/demo (V897), sur le procédé de V898.
--
-- 2) QUANTITÉ DÉCIMALE POUR LES HEURES SEULEMENT
--
--    NUMERIC(7,2) strictement positive. Une pièce et une prestation au forfait se comptent
--    en unités entières, une prestation à l'heure en heures, au centième, sans pas imposé.
--
-- 3) REPRISE
--
--    Les lignes existantes prennent PIECE si elles portent une pièce, FORFAIT sinon : aucune
--    prestation à l'heure n'existait avant V72. Les quantités entières passent telles quelles
--    en NUMERIC, sans perte.
-- =====================================================================================

ALTER TABLE ligne_intervention ADD COLUMN unite VARCHAR(10);

UPDATE ligne_intervention
SET unite = CASE WHEN piece_id IS NOT NULL THEN 'PIECE' ELSE 'FORFAIT' END;

ALTER TABLE ligne_intervention
    ALTER COLUMN unite SET NOT NULL,
    ALTER COLUMN quantite TYPE NUMERIC(7,2) USING quantite::NUMERIC(7,2),
    ADD CONSTRAINT ck_ligne_interv_unite CHECK (
        unite IN ('FORFAIT', 'HEURE', 'PIECE')
        AND (unite = 'PIECE') = (piece_id IS NOT NULL)),
    ADD CONSTRAINT ck_ligne_interv_quantite_unite CHECK (
        unite = 'HEURE' OR quantite = trunc(quantite));

COMMENT ON COLUMN ligne_intervention.unite IS
    'PIECE, FORFAIT ou HEURE, figée à la création de la ligne. Sans valeur par défaut.';
COMMENT ON COLUMN ligne_intervention.quantite IS
    'Strictement positive. Entière pour une pièce ou un forfait, en heures au centième pour HEURE.';
