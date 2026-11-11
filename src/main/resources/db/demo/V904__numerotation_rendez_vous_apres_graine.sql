-- Numérotation des rendez-vous alignée sur la graine.
--
-- V900 numérote ses rendez-vous par seq_numero_rdv (V9), alors que l'application tire
-- ses numéros de rdv_numero_seq (V12). Sur une base de démonstration, la première
-- réservation recevait donc RDV-AAAA-0001, déjà pris, et butait sur uq_rdv_numero.
-- La séquence de l'application reprend après le plus grand suffixe existant, toutes
-- années et lignes supprimées comprises, puisque uq_rdv_numero porte sur toute la table.
-- Elle n'est jamais reculée : sur une base où elle a déjà servi, elle garde sa valeur.
--
-- Les autres séquences de V9 n'ont pas ce défaut : la graine emploie celles du code
-- (seq_numero_commande, seq_numero_intervention), et factures comme avoirs sont
-- numérotés par compteur en table, que V900 tient à jour.
DO $$
DECLARE
    v_plus_grand BIGINT;
BEGIN
    SELECT max(substring(numero FROM '(\d+)$')::BIGINT) INTO v_plus_grand FROM rdv;
    IF v_plus_grand IS NOT NULL THEN
        PERFORM setval('rdv_numero_seq',
                       GREATEST(v_plus_grand, (SELECT last_value FROM rdv_numero_seq)), true);
    END IF;
END
$$;

-- seq_numero_rdv reste en place : V900 s'en sert sur une base neuve.
COMMENT ON SEQUENCE seq_numero_rdv IS
    'Inutilisée par l''application, qui numérote par rdv_numero_seq. Employée seulement par la graine de démonstration V900.';
