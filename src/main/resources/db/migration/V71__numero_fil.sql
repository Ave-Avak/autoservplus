-- =====================================================================================
-- Numéro d'un fil de discussion, FIL-AAAA-NNNN (Q-H-27).
--
-- 1) POURQUOI UN NUMÉRO
--
--    Un fil n'était désigné que par son sujet, texte libre écrit par le membre, et par son
--    UUID. La notification déposée chez chaque administrateur citait le sujet : à la
--    suppression du compte (F23), ce texte restait chez des tiers. Le numéro le remplace
--    dans cette notification, s'affiche à côté du sujet partout où le fil est désigné, et
--    sert au membre de référence à citer.
--    Écartés : l'identifiant technique, séquentiel, qui se devine et dit le nombre de fils,
--    et une forme courte de l'UUID, illisible au téléphone.
--
-- 2) COMPTEUR PAR ANNÉE AU GARAGE
--
--    Même procédé que compteur_devis (V69) : la suite repart à 1 chaque année au fuseau du
--    garage, et une seule instruction crée ou incrémente la ligne de l'année dans la
--    transaction qui ouvre le fil.
--
-- 3) REPRISE DES FILS EXISTANTS
--
--    Numérotés par année au fuseau de l'atelier, puis par date de création et identifiant,
--    fils supprimés compris. compteur_fil reçoit le dernier numéro de chaque année. Sur une
--    base de démonstration où la graine V900 est déjà jouée, son fil est numéroté ici. Sur
--    une base neuve, V900 passe après cette migration : db/demo/V898 numérote le fil
--    qu'elle insère sans numéro.
-- =====================================================================================

CREATE TABLE compteur_fil
(
    exercice       SMALLINT PRIMARY KEY,
    dernier_numero INTEGER  NOT NULL,

    CONSTRAINT ck_compteur_fil_numero CHECK (dernier_numero > 0)
);

COMMENT ON TABLE compteur_fil IS
    'Dernier numéro de fil de discussion attribué par année au garage (FIL-AAAA-NNNN).';

ALTER TABLE conversation ADD COLUMN numero VARCHAR(20);

WITH numerotes AS (
    SELECT c.id,
           EXTRACT(YEAR FROM c.created_at AT TIME ZONE p.fuseau_horaire)::INTEGER AS exercice,
           row_number() OVER (
               PARTITION BY EXTRACT(YEAR FROM c.created_at AT TIME ZONE p.fuseau_horaire)
               ORDER BY c.created_at, c.id) AS rang
    FROM conversation c
             JOIN parametre_atelier p ON p.id = 1
)
UPDATE conversation c
SET numero = 'FIL-' || n.exercice || '-' || lpad(n.rang::TEXT, greatest(4, length(n.rang::TEXT)), '0')
FROM numerotes n
WHERE n.id = c.id;

INSERT INTO compteur_fil (exercice, dernier_numero)
SELECT substring(numero FROM 5 FOR 4)::SMALLINT, max(substring(numero FROM 10)::INTEGER)
FROM conversation
GROUP BY 1;

ALTER TABLE conversation
    ALTER COLUMN numero SET NOT NULL,
    ADD CONSTRAINT uq_conversation_numero UNIQUE (numero);

COMMENT ON COLUMN conversation.numero IS
    'Numéro FIL-AAAA-NNNN, année au fuseau du garage. La notification d''un administrateur '
    'ne cite que lui, jamais le sujet écrit par le membre.';
