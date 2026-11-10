-- Réactive l'administrateur de démonstration, suspendu par V45 sur toute base.
--
-- Sous le profil demo seulement : les données sont fictives et le mot de passe est
-- publié exprès, pour qu'un évaluateur se connecte. Un serveur de démonstration reste
-- donc prenable par quiconque lit le dépôt, limite assumée.
--
-- La condition porte sur l'empreinte de V37. Un administrateur de démonstration dont le
-- mot de passe a été changé n'a pas été suspendu par V45 et reste tel quel. La graine
-- s'applique une seule fois, après V45, au premier démarrage sous demo.

WITH cibles AS (
    SELECT u.id
      FROM utilisateur u
     WHERE u.email = 'admin@autoservplus.be'
       AND u.statut = 'SUSPENDU'
       AND u.mot_de_passe_hache = '$2a$12$QbVngVgp7OUz5NYwVFKPvev2l2h8oVs6oYwoeAgDFmoIbicTdUZHK'
       FOR UPDATE
),
reactives AS (
    UPDATE utilisateur u
       SET statut     = 'ACTIF',
           updated_at = now(),
           updated_by = 'demo-v902'
      FROM cibles c
     WHERE u.id = c.id
 RETURNING u.id
)
INSERT INTO historique_statut_utilisateur
       (utilisateur_id, statut_avant, statut_apres, horodatage, auteur_id, motif,
        created_by, updated_by)
SELECT r.id, 'SUSPENDU', 'ACTIF', now(), NULL,
       'Compte de démonstration réactivé sous le profil demo. Données fictives.',
       'demo-v902', 'demo-v902'
  FROM reactives r;
