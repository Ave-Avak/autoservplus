-- =====================================================================================
-- Rend le devis initial structurellement obligatoire (RM-15).
--
-- V20 a retro-rempli montant_devis_htva pour les interventions creees avant que la
-- colonne ne soit mappee (correction du constructeur Intervention(numero, rdv)), en
-- reconstituant la somme HTVA de leurs lignes et en retombant sur 0 pour une
-- intervention sans ligne — aucun NULL n'a donc ete laisse derriere.
--
-- Ce lot transforme ce constat en invariant. Tant que la colonne reste nullable,
-- RM-15 peut se desactiver en silence : devisReferenceHtva() retombe sur un recalcul,
-- mais une intervention arrivee en base sans devis ne declencherait plus jamais de
-- seuil correctement, et rien ne le signalerait. SET NOT NULL fait echouer l'INSERT
-- au lieu de laisser la regle s'eteindre sans bruit.
--
-- Conditions verifiees avant d'appliquer :
--   1. Apres V20, aucune ligne de la table ne porte NULL (COALESCE(..., 0) couvre le
--      cas de l'intervention sans ligne).
--   2. Le seul constructeur existant renseigne systematiquement le champ ; le calcul
--      retourne 0.00 et jamais null lorsqu'il n'y a aucune ligne.
--
-- Le filet de securite est conserve malgre tout : un UPDATE prealable rattrape
-- d'eventuelles lignes creees entre V20 et V21 par une instance encore en execution
-- sur l'ancien code. Sans lui, l'ALTER echouerait et bloquerait le demarrage.
--
-- Note : le dictionnaire (V5) prevoyait la colonne nullable, pour une entree directe
-- au garage hors V1. Ce cas reste couvert — une entree directe sans devis chiffre
-- s'enregistre a 0,00 €, ce qui est un devis, pas une absence de devis.
-- =====================================================================================

UPDATE intervention i
SET montant_devis_htva = COALESCE((
        SELECT SUM(l.prix_unitaire_htva * l.quantite)
        FROM ligne_intervention l
        WHERE l.intervention_id = i.id
          AND NOT l.refusee
    ), 0)
WHERE i.montant_devis_htva IS NULL;

ALTER TABLE intervention ALTER COLUMN montant_devis_htva SET NOT NULL;

COMMENT ON COLUMN intervention.montant_devis_htva IS
    'Devis initial HTVA fige a la creation depuis les lignes du rendez-vous. Reference de comparaison de RM-15 : le seuil vaut ce montant majore de dix pour cent. Obligatoire depuis V21 — une intervention sans devis rendrait la regle incalculable.';
