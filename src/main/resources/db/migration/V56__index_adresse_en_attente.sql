-- Index d'unicité de l'adresse en attente porté par la colonne elle-même.
--
-- V38 l'avait posé sur lower(email_en_attente), parce que la casse n'était garantie que
-- par le code. Depuis V55, ck_utilisateur_email_en_attente_minuscules l'impose : les deux
-- index refusent exactement les mêmes doublons. Seul celui-ci sert une recherche par
-- égalité stricte, celle de l'application, alors que l'index sur lower() ne serait
-- employé que si chaque requête réécrivait lower(). Garder lower() pour cette seule
-- colonne aurait fait une exception à la règle des recherches strictes, à retenir à
-- chaque nouvelle requête.
DROP INDEX uq_utilisateur_email_en_attente;

CREATE UNIQUE INDEX uq_utilisateur_email_en_attente
    ON utilisateur (email_en_attente)
    WHERE email_en_attente IS NOT NULL;
