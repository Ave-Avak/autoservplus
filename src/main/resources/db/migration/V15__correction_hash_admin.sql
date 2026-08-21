-- Correction du hash BCrypt du compte administrateur de seed.
--
-- Le hash inscrit en V10 ne correspond pas au mot de passe documenté
-- ("ChangezMoi2026!") : la connexion échouait. Vérifié par un
-- BCryptPasswordEncoder(12).matches(...) qui retournait false.
--
-- Ce hash est régénéré avec le même algorithme et le même cost, sur la
-- même chaîne. V10 n'est pas modifiée (règle Flyway : jamais toucher à une
-- migration déjà committée) ; on corrige par une nouvelle migration.
--
-- Le mot de passe en clair reste "ChangezMoi2026!" et doit impérativement
-- être changé à la première connexion en production.

UPDATE utilisateur
   SET mot_de_passe_hache = '$2a$12$gnSV2iew72ylfGo24d4qB.gq7DPefhBG9efPcE2EBl8QfjAbQgoYG',
       updated_by         = 'migration'
 WHERE email = 'admin@autoservplus.be';
