-- Administrateur de démonstration à une adresse du domaine réservé aux essais (RFC 2606).
--
-- Jusqu'ici la démonstration ouvrait le back-office avec l'administrateur de V10, à une
-- adresse en .be qu'un courriel de la démonstration pouvait atteindre. Le garage de
-- démonstration reçoit son propre administrateur, en .test, et celui de V10 est suspendu
-- de nouveau sous le profil demo, comme V45 le fait partout ailleurs. V10 et V902 ne
-- changent pas : la réactivation de V902 reste dans l'historique du compte, suivie de la
-- suspension écrite ici.
--
-- Mot de passe et clé TOTP publiés au README, comme ceux de V902. Le second facteur est
-- posé au démarrage par SecondFacteurDemonstration. Chaque instruction est rejouable sans
-- effet : un compte déjà présent n'est pas recréé, un compte déjà suspendu ne l'est pas
-- une seconde fois.

INSERT INTO utilisateur (type_utilisateur, email, mot_de_passe_hache, nom, prenom, langue, statut,
                         email_verifie, fonction, created_by, updated_by)
SELECT 'ADMINISTRATEUR', 'admin@bielle-bleue.test',
       '$2a$12$bXZVbdPkwmAjK8IfzhGjKuMfekhql7n8XdrgM6cY5Thdwq5OwrgbK',
       'Lambert', 'Claire', 'fr', 'ACTIF', TRUE, 'Responsable de l''atelier', 'demo', 'demo'
 WHERE NOT EXISTS (SELECT 1 FROM utilisateur WHERE email = 'admin@bielle-bleue.test');

-- Les transitions de statut des interventions de la graine (V900) portent l'administrateur
-- de V10 comme auteur. Elles passent au nouvel administrateur : le journal de la
-- démonstration nomme le compte qu'un évaluateur emploie. Seules les lignes de la graine
-- sont touchées.
UPDATE historique_statut_intervention h
   SET auteur_id = nouveau.id, updated_by = 'demo-v910'
  FROM utilisateur ancien, utilisateur nouveau
 WHERE ancien.email = 'admin@autoservplus.be'
   AND nouveau.email = 'admin@bielle-bleue.test'
   AND h.auteur_id = ancien.id
   AND h.created_by = 'demo';

WITH cibles AS (
    SELECT u.id
      FROM utilisateur u
     WHERE u.email = 'admin@autoservplus.be'
       AND u.statut = 'ACTIF'
       AND u.updated_by = 'demo-v902'
       FOR UPDATE
),
suspendus AS (
    UPDATE utilisateur u
       SET statut     = 'SUSPENDU',
           updated_at = now(),
           updated_by = 'demo-v910'
      FROM cibles c
     WHERE u.id = c.id
 RETURNING u.id
)
INSERT INTO historique_statut_utilisateur
       (utilisateur_id, statut_avant, statut_apres, horodatage, auteur_id, motif,
        created_by, updated_by)
SELECT s.id, 'ACTIF', 'SUSPENDU', now(), NULL,
       'Remplacé par l''administrateur de démonstration admin@bielle-bleue.test. Mot de passe publié.',
       'demo-v910', 'demo-v910'
  FROM suspendus s;
