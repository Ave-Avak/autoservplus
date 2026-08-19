-- =====================================================================================
-- Zone 9 : sequences de numerotation documentaire
-- La numerotation des factures doit etre continue et sans trou par exercice comptable.
-- Chaque type de document dispose de son propre compteur.
-- Reference : livrable 18
-- =====================================================================================

CREATE SEQUENCE seq_numero_rdv          START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_numero_commande     START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_numero_intervention START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_numero_facture      START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_numero_avoir        START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_numero_parking      START WITH 1 INCREMENT BY 1;

COMMENT ON SEQUENCE seq_numero_facture IS
    'Compteur des factures. Remis a zero au changement d exercice par la couche service, '
    'la colonne sequence_annuelle assurant l unicite par annee.';