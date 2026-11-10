-- =====================================================================================
-- Suspension de tout compte dont le mot de passe est publié dans le dépôt.
--
-- V10 crée admin@autoservplus.be sur toute base, vierge et production comprises, et
-- V15, V36 puis V37 lui posent des mots de passe écrits en clair dans les migrations et
-- au README. Ce compte n'a pas de second facteur enrôlé : à la première connexion, le
-- back-office demande l'enrôlement, et le premier venu muni du mot de passe publié
-- l'aurait réalisé à la place du garage.
--
-- V10, V15, V36 et V37 ne sont pas modifiées. Une migration appliquée ne se réécrit pas,
-- son empreinte changerait et Flyway refuserait toute base où elle l'est déjà. Supprimer
-- la ligne a été écarté : sur une base en service, ce compte peut être l'auteur de
-- décisions (avoirs, demandes d'annulation), en clé étrangère RESTRICT.
--
-- La cible est l'empreinte et non l'adresse. Un compte dont le mot de passe a été changé
-- depuis porte une autre empreinte, BCrypt salant chaque hachage : il n'est pas touché.
-- Une empreinte publiée, elle, ne protège plus rien, quelle que soit l'adresse.
--
-- Le compte suspendu ne se réactive pas en exploitation. Un administrateur se crée par
-- lien de définition, depuis l'écran des comptes du back-office. Sous le profil demo,
-- la graine V902 le réactive, ses données étant fictives.
--
-- Une ligne par compte au journal des statuts (V40), sans auteur : aucun compte n'a pris
-- la décision, et le motif dit d'où elle vient.
-- =====================================================================================

WITH cibles AS (
    SELECT id, statut
      FROM utilisateur
     WHERE statut IN ('ACTIF', 'EN_ATTENTE_VALIDATION')
       AND mot_de_passe_hache IN (
           '$2a$12$8K1p/a0dURXAm7QiTRqTOuKzKe7BJ9YlKz5CoFDoOB0jQZmLGxKPe',  -- V10
           '$2a$12$gnSV2iew72ylfGo24d4qB.gq7DPefhBG9efPcE2EBl8QfjAbQgoYG',  -- V15
           '$2a$12$7LU.4SdM7BI/rAVReYlmO.43NuY3BfGjWMnB4zeDJmIQKGDfywbsq',  -- V36
           '$2a$12$QbVngVgp7OUz5NYwVFKPvev2l2h8oVs6oYwoeAgDFmoIbicTdUZHK')  -- V37
       FOR UPDATE
),
suspendus AS (
    UPDATE utilisateur u
       SET statut     = 'SUSPENDU',
           updated_at = now(),
           updated_by = 'migration-v45'
      FROM cibles c
     WHERE u.id = c.id
 RETURNING u.id
)
INSERT INTO historique_statut_utilisateur
       (utilisateur_id, statut_avant, statut_apres, horodatage, auteur_id, motif,
        created_by, updated_by)
SELECT c.id, c.statut, 'SUSPENDU', now(), NULL,
       'Mot de passe publié dans le dépôt du projet. Compte suspendu à l''installation, '
       || 'à ne pas réactiver : créer un administrateur par lien de définition.',
       'migration-v45', 'migration-v45'
  FROM cibles c
  JOIN suspendus s ON s.id = c.id;
