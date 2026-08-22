-- =====================================================================================
-- Fondations de RM-15 (validation du dépassement de devis par le membre).
--
-- Le dictionnaire (Livrable 09) porte RM-15 au seul niveau `intervention`, via
-- montant_devis_htva / depassement_notifie / accord_client / date_accord_client,
-- posées en V5 et jamais écrites. Ce modèle exprime « le client a-t-il donné son
-- accord », mais pas « SUR QUOI ». Or la règle exige de conserver une ligne refusée
-- dans le dossier (trace du défaut constaté) tout en l'excluant du total facturé :
-- c'est une information par ligne, que le modèle CdC ne sait pas représenter.
--
-- D'où l'écart assumé : trois marqueurs sur ligne_intervention.
--   ajoutee_en_cours : la ligne vient-elle du RDV (devis initial accepté à la
--                      réservation) ou a-t-elle été ajoutée par le garage pendant
--                      l'intervention ? C'est ce qui définit le devis de référence.
--   validee          : la ligne entre-t-elle dans le total facturable ?
--   refusee          : le membre l'a-t-il écartée ? Conservée en base, hors total,
--                      non exécutée.
--
-- Deux booléens plutôt qu'un statut de ligne : choix du porteur. Le CHECK
-- ck_ligne_interv_validation interdit l'état absurde (validée ET refusée) au
-- niveau base, là où un enum l'aurait rendu inexprimable par construction.
--
-- Les DEFAULT ne servent qu'au remplissage des lignes déjà présentes : JPA écrit
-- toujours les trois colonnes explicitement. DEFAULT TRUE sur `validee` est le
-- backfill demandé — toute ligne existante est réputée acceptée, puisque aucune
-- n'a jamais pu être refusée faute de mécanisme.
--
-- Backfill montant_devis_htva : la colonne existe depuis V5 mais n'a jamais été
-- renseignée (aucun code Java ne la mappait). RM-15 compare le total courant à
-- devis × 1,10 ; une valeur NULL désactiverait silencieusement la règle sur les
-- interventions antérieures. On la reconstitue depuis les lignes existantes, qui
-- sont toutes issues du RDV à ce stade (aucun ajout en cours n'était possible
-- avant ce lot).
-- =====================================================================================

ALTER TABLE ligne_intervention
    ADD COLUMN ajoutee_en_cours BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN validee          BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN refusee          BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ligne_intervention
    ADD CONSTRAINT ck_ligne_interv_validation CHECK (NOT (validee AND refusee));

COMMENT ON COLUMN ligne_intervention.ajoutee_en_cours IS
    'FALSE : ligne issue du rendez-vous, elle compose le devis initial accepte par le membre a la reservation. TRUE : ajoutee par le garage pendant l intervention (RM-15).';
COMMENT ON COLUMN ligne_intervention.validee IS
    'La ligne entre dans le total facturable. Les lignes du RDV naissent validees ; une ligne ajoutee en cours ne l est qu apres accord du membre si le seuil RM-15 est franchi.';
COMMENT ON COLUMN ligne_intervention.refusee IS
    'Le membre a refuse cette ligne (RM-15). Conservee comme trace du defaut constate, exclue du total facturable, non executee.';

UPDATE intervention i
SET montant_devis_htva = COALESCE((
        SELECT SUM(l.prix_unitaire_htva * l.quantite)
        FROM ligne_intervention l
        WHERE l.intervention_id = i.id
    ), 0)
WHERE i.montant_devis_htva IS NULL;
