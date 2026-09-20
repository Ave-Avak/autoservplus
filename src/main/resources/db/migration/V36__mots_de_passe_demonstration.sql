-- Remplace le mot de passe du compte administrateur seme par V10 et corrige par V15.
--
-- Motif : "ChangezMoi2026!" fait quinze caracteres, soit le minimum admis par la
-- nouvelle politique (NIST SP 800-63B-4) — conforme de justesse, et publie en clair
-- dans le depot depuis le socle. Une phrase de passe plus longue coute le meme effort
-- a retenir et ne se devine pas.
--
-- V10 et V15 ne sont PAS modifiees : une migration appliquee ne se reecrit pas, son
-- empreinte changerait et Flyway refuserait toute base existante.
--
-- Le mot de passe reste publie et reste a bannir en production : ce compte est un
-- compte de demonstration, pas un compte d exploitation.
--
--   admin@autoservplus.be / garage-bruxelles-atelier-2026
--
-- Empreinte BCrypt de cout 12, produite par l encodeur du projet et verifiee par
-- matches() avant d etre inscrite ici.
UPDATE utilisateur
   SET mot_de_passe_hache = '$2a$12$7LU.4SdM7BI/rAVReYlmO.43NuY3BfGjWMnB4zeDJmIQKGDfywbsq',
       updated_at         = now(),
       updated_by         = 'migration-v36'
 WHERE email = 'admin@autoservplus.be';
