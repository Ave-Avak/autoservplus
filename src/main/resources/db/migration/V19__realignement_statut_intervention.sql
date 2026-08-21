-- =====================================================================================
-- Réalignement du CHECK intervention.statut sur le cahier des charges
-- (Livrable 09 « Dictionnaire de données », table 3.8).
--
-- V18 avait posé un CHECK sur (PLANIFIEE, EN_COURS, EN_PAUSE, TERMINEE, FACTUREE) qui
-- reflétait la machine à états codée à l'époque (raccourci express, réouverture).
-- Le CdC modélise en fait six statuts :
--     PLANIFIEE, EN_COURS, SUSPENDUE, ATTENTE_VALIDATION_MEMBRE, TERMINEE, ANNULEE
-- et considère la facturation comme une action déclenchée AU passage à TERMINEE
-- (RM-17), pas comme un état. On retire donc FACTUREE ; le module facturation
-- (post-V1) branchera sa logique sur le hook TERMINEE.
--
-- Mapping des données existantes :
--   EN_PAUSE  → SUSPENDUE  (renommage, aucune perte sémantique)
--   FACTUREE  → TERMINEE   (peu probable en V1 : aucun code n'appelle
--                           marquerFacturee côté service, il n'existait qu'en
--                           test. Retomber sur TERMINEE est le mapping le plus
--                           conservateur ; si le module facturation V2 avait
--                           besoin de distinguer, il ajoutera sa propre table.)
--
-- Ordre des opérations : on DROP la contrainte d'abord (sinon l'UPDATE vers
-- 'SUSPENDUE' échouerait, 'SUSPENDUE' n'étant pas dans le CHECK actuel), on
-- réécrit les données, puis on remonte le CHECK élargi.
--
-- Colonne statut : VARCHAR(25) hérité de V5, 'ATTENTE_VALIDATION_MEMBRE' fait
-- exactement 25 caractères. On élargit à VARCHAR(30) pour absorber toute
-- variante future sans re-migration.
-- =====================================================================================

ALTER TABLE intervention DROP CONSTRAINT ck_intervention_statut;

UPDATE intervention SET statut = 'SUSPENDUE' WHERE statut = 'EN_PAUSE';
UPDATE intervention SET statut = 'TERMINEE'  WHERE statut = 'FACTUREE';

ALTER TABLE intervention ALTER COLUMN statut TYPE VARCHAR(30);

ALTER TABLE intervention
    ADD CONSTRAINT ck_intervention_statut
    CHECK (statut IN ('PLANIFIEE', 'EN_COURS', 'SUSPENDUE',
                      'ATTENTE_VALIDATION_MEMBRE', 'TERMINEE', 'ANNULEE'));
