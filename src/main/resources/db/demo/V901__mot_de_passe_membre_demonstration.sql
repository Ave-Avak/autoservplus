-- Remplace le mot de passe du membre de demonstration seme par V900.
--
-- Meme motif que V36 pour l administrateur : "DemoMembre2026!" faisait quinze
-- caracteres, soit le minimum admis par la nouvelle politique. V900 n est pas
-- modifiee — une migration appliquee ne se reecrit pas.
--
-- Cette migration vit dans db/demo et ne s applique donc que sous le profil demo,
-- comme la graine qu elle corrige.
--
--   marie.dupont@demo.test / marie-conduit-une-golf-bleue
--
-- Empreinte BCrypt de cout 12, produite par l encodeur du projet.
UPDATE utilisateur
   SET mot_de_passe_hache = '$2a$12$042ZHjFm8KH3Rsu/asfX6uR0ZgVG/ol7TgXqGeeN096Hwuru.Fhsu',
       updated_at         = now(),
       updated_by         = 'migration-v901'
 WHERE email = 'marie.dupont@demo.test';
