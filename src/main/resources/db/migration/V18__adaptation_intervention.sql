-- =====================================================================================
-- Adaptations du modèle intervention pour aligner le CHECK statut sur la machine
-- à états portée par le domaine Java, et ajouter le champ commentaire_admin.
--
-- 1) Statuts : V1 traite le cas nominal PLANIFIEE → EN_COURS → (EN_PAUSE) →
--    TERMINEE → FACTUREE. EN_PAUSE était prévu par l'analyse UML V3 mais absent
--    du CHECK initial de V5. FACTUREE est un hook du module facturation, pas
--    déclenché en V1. Les statuts EN_ATTENTE_ACCORD (RM-15 dépassement de devis)
--    et ANNULEE sont retirés du CHECK : la V1 ne les couvre pas, une évolution
--    ultérieure pourra les rétablir avec la logique métier correspondante.
--    Les colonnes depassement_notifie, accord_client, date_accord_client de V5
--    restent en base (nullables, non écrites en V1) : leur suppression relève
--    d'une passe de nettoyage ultérieure.
--
-- 2) commentaire_admin : note visible du client dans le suivi de l'intervention
--    (F17). Distinct de la colonne diagnostic (interne). Ex. le mécanicien
--    écrit « Plaquettes commandées, livraison mardi » et le membre le lit sur
--    /mes-interventions/{ref}.
-- =====================================================================================

ALTER TABLE intervention DROP CONSTRAINT ck_intervention_statut;
ALTER TABLE intervention
    ADD CONSTRAINT ck_intervention_statut
    CHECK (statut IN ('PLANIFIEE', 'EN_COURS', 'EN_PAUSE', 'TERMINEE', 'FACTUREE'));

ALTER TABLE intervention ADD COLUMN commentaire_admin TEXT;
COMMENT ON COLUMN intervention.commentaire_admin IS
    'Note du garage visible par le client dans le suivi de l intervention (F17). '
    'Distinct de diagnostic (interne au garage).';
