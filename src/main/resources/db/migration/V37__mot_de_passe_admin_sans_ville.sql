-- Remplace le mot de passe administrateur pose par V36.
--
-- Motif : "garage-bruxelles-atelier-2026" nommait une ville. Ce mot de passe est
-- publie au README, en tete du dump et dans le manuel ; le cas pilote est un garage
-- de Braine-le-Comte, et un identifiant de demonstration n a pas a situer une
-- entreprise, fut-ce a cote.
--
-- V36 n est pas modifiee : une migration appliquee ne se reecrit pas, son empreinte
-- changerait et Flyway refuserait toute base existante.
--
--   admin@autoservplus.be / atelier-demonstration-2026
--
-- Empreinte BCrypt de cout 12, produite par l encodeur du projet et verifiee par
-- matches() avant d etre inscrite ici.
UPDATE utilisateur
   SET mot_de_passe_hache = '$2a$12$QbVngVgp7OUz5NYwVFKPvev2l2h8oVs6oYwoeAgDFmoIbicTdUZHK',
       updated_at         = now(),
       updated_by         = 'migration-v37'
 WHERE email = 'admin@autoservplus.be';
