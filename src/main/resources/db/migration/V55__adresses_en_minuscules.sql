-- Adresses de courriel en minuscules, garanties par la base.
--
-- L'unicité de l'adresse (uq_utilisateur_email) est sensible à la casse : elle ne tenait
-- que parce que chaque chemin d'écriture mettait l'adresse en minuscules et cherchait
-- sans tenir compte de la casse. Un seul chemin qui l'oublierait créerait un doublon que
-- la base accepterait. La contrainte rend la règle indépendante du code, et les
-- recherches peuvent passer à l'égalité stricte, servie par l'index de l'adresse.
--
-- La migration refuse de s'appliquer si deux comptes ne diffèrent que par la casse de
-- leur adresse : les mettre en minuscules violerait l'unicité, et choisir lequel garder
-- relève de l'exploitant. Le message nomme les comptes par leur identifiant, sans
-- adresse. L'adresse en attente de confirmation n'a pas besoin de ce contrôle : son index
-- d'unicité (uq_utilisateur_email_en_attente, V38) porte déjà sur lower(email_en_attente),
-- et deux demandes ne peuvent pas ne différer que par la casse.
DO $$
DECLARE
    doublons TEXT;
BEGIN
    SELECT string_agg(ids, ' / ') INTO doublons
      FROM (SELECT string_agg(id::TEXT, ', ' ORDER BY id) AS ids
              FROM utilisateur
             GROUP BY lower(email)
            HAVING count(*) > 1) AS groupes;
    IF doublons IS NOT NULL THEN
        RAISE EXCEPTION 'V55 : adresses de courriel qui ne diffèrent que par la casse, comptes %. Fusionner ou corriger ces comptes avant de relancer la migration.', doublons;
    END IF;
END
$$;

UPDATE utilisateur SET email = lower(email) WHERE email <> lower(email);
UPDATE utilisateur SET email_en_attente = lower(email_en_attente)
 WHERE email_en_attente <> lower(email_en_attente);

ALTER TABLE utilisateur ADD CONSTRAINT ck_utilisateur_email_minuscules
    CHECK (email = lower(email));
ALTER TABLE utilisateur ADD CONSTRAINT ck_utilisateur_email_en_attente_minuscules
    CHECK (email_en_attente = lower(email_en_attente));
