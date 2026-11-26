-- Taux de TVA du catalogue limités à 6, 12 et 21 %.
--
-- Aucune prestation ni pièce n'est vendue à 0 % en V1. Une ligne à 0 % exigerait sur la
-- facture la mention de la disposition qui justifie l'exemption (AR n° 1 du 29/12/1992,
-- art. 5), que l'application n'imprime pas. Le taux est contrôlé par la base sur les deux
-- tables où il se choisit. Les tables de lignes (panier, devis, rendez-vous, intervention)
-- recopient le taux au moment de la vente et ne reçoivent pas de contrainte : une ligne
-- déjà vendue ne se réécrit pas.

ALTER TABLE service DROP CONSTRAINT ck_service_tva;
ALTER TABLE service ADD CONSTRAINT ck_service_tva CHECK (taux_tva IN (6.00, 12.00, 21.00));
ALTER TABLE piece ADD CONSTRAINT ck_piece_tva CHECK (taux_tva IN (6.00, 12.00, 21.00));
