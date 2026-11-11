-- Trois séquences de V9 ne servent plus l'application. Elles restent en place : une
-- séquence supprimée casserait la graine de démonstration V900, qui emploie encore
-- seq_numero_rdv, et rien ne gagne à les retirer. Le commentaire dit ce qui les remplace,
-- pour qu'aucun code nouveau ne s'y raccroche. seq_numero_rdv n'en portait aucun hors
-- de la graine V904, ceux de seq_numero_facture (V26) et seq_numero_avoir (V27) sont
-- réécrits dans la même forme.

COMMENT ON SEQUENCE seq_numero_rdv IS
    'Inutilisée par l''application, qui numérote les rendez-vous par rdv_numero_seq (V12). Employée seulement par la graine de démonstration V900.';

COMMENT ON SEQUENCE seq_numero_facture IS
    'Inutilisée par l''application. Les factures sont numérotées par la table compteur_facture (V26), sous verrou dans la transaction d''émission : une séquence laisse des trous au rollback.';

COMMENT ON SEQUENCE seq_numero_avoir IS
    'Inutilisée par l''application. Les avoirs sont numérotés par la table compteur_avoir (V27), sur le patron des factures.';
