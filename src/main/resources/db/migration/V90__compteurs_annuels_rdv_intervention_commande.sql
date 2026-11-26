-- =====================================================================================
-- Numéros de rendez-vous, d'intervention et de commande par année au garage.
--
-- 1) POURQUOI
--
--    Les trois suites étaient tirées de séquences globales (V9, V12), préfixées de l'année
--    mais jamais remises à 1 : la première commande d'une année reprenait après la dernière
--    de l'année précédente (CMD-2027-0412 et non CMD-2027-0001). Elles passent au procédé
--    des devis et des fils (V69, V71) : une table de compteurs par suite, une ligne par
--    année au fuseau du garage, incrémentée dans la transaction qui crée le document.
--
-- 2) NUMÉROS DÉJÀ ATTRIBUÉS
--
--    Aucun n'est réécrit : ils sont cités dans les courriels, les notifications et sur les
--    factures. Chaque compteur reprend au plus grand suffixe porté dans son année, lignes
--    supprimées comprises, puisque l'unicité porte sur toute la table. Un numéro hors du
--    format AAAA-NNNN n'est pas compté.
--
-- 3) SÉQUENCES GARDÉES
--
--    La graine de démonstration (V900, V904, V915) s'en sert encore. Leur commentaire dit
--    ce qui les remplace, pour qu'aucun code nouveau ne s'y raccroche.
-- =====================================================================================

CREATE TABLE compteur_rdv
(
    exercice       SMALLINT PRIMARY KEY,
    dernier_numero INTEGER  NOT NULL,

    CONSTRAINT ck_compteur_rdv_numero CHECK (dernier_numero > 0)
);

CREATE TABLE compteur_intervention
(
    exercice       SMALLINT PRIMARY KEY,
    dernier_numero INTEGER  NOT NULL,

    CONSTRAINT ck_compteur_intervention_numero CHECK (dernier_numero > 0)
);

CREATE TABLE compteur_commande
(
    exercice       SMALLINT PRIMARY KEY,
    dernier_numero INTEGER  NOT NULL,

    CONSTRAINT ck_compteur_commande_numero CHECK (dernier_numero > 0)
);

COMMENT ON TABLE compteur_rdv IS
    'Dernier numéro de rendez-vous attribué par année au garage (RDV-AAAA-NNNN).';
COMMENT ON TABLE compteur_intervention IS
    'Dernier numéro d''intervention attribué par année au garage (INT-AAAA-NNNN).';
COMMENT ON TABLE compteur_commande IS
    'Dernier numéro de commande attribué par année au garage (CMD-AAAA-NNNN).';

INSERT INTO compteur_rdv (exercice, dernier_numero)
SELECT substring(numero FROM 5 FOR 4)::SMALLINT, max(substring(numero FROM 10)::INTEGER)
FROM rdv
WHERE numero ~ '^RDV-[0-9]{4}-[0-9]+$'
GROUP BY 1;

INSERT INTO compteur_intervention (exercice, dernier_numero)
SELECT substring(numero FROM 5 FOR 4)::SMALLINT, max(substring(numero FROM 10)::INTEGER)
FROM intervention
WHERE numero ~ '^INT-[0-9]{4}-[0-9]+$'
GROUP BY 1;

INSERT INTO compteur_commande (exercice, dernier_numero)
SELECT substring(numero FROM 5 FOR 4)::SMALLINT, max(substring(numero FROM 10)::INTEGER)
FROM commande
WHERE numero ~ '^CMD-[0-9]{4}-[0-9]+$'
GROUP BY 1;

COMMENT ON SEQUENCE rdv_numero_seq IS
    'Inutilisée par l''application, qui numérote les rendez-vous par la table compteur_rdv (V90). Recalée seulement par la graine de démonstration V904.';
COMMENT ON SEQUENCE seq_numero_rdv IS
    'Inutilisée par l''application, qui numérote les rendez-vous par la table compteur_rdv (V90). Employée seulement par la graine de démonstration V900.';
COMMENT ON SEQUENCE seq_numero_intervention IS
    'Inutilisée par l''application, qui numérote les interventions par la table compteur_intervention (V90). Employée seulement par les graines de démonstration V900 et V915.';
COMMENT ON SEQUENCE seq_numero_commande IS
    'Inutilisée par l''application, qui numérote les commandes par la table compteur_commande (V90). Employée seulement par la graine de démonstration V900.';
