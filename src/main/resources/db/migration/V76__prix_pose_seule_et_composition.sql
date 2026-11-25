-- Prix « pose seule » d'une prestation et composition d'une prestation en pièces.
--
-- Un forfait peut proposer deux prix : pièces fournies par le garage (prix_htva, prix
-- pièces comprises) ou pièces apportées par le client (prix_pose_seule_htva). La colonne
-- est nulle quand le garage ne propose pas ce choix. Une prestation à l'heure n'en a
-- jamais : elle se facture au temps passé, sans pièces comprises.
--
-- Un prix pose seule égal ou supérieur au prix pièces comprises ne correspond à aucun cas
-- réel : la contrainte le refuse, comme le domaine.

ALTER TABLE service ADD COLUMN prix_pose_seule_htva NUMERIC(10, 2);

ALTER TABLE service ADD CONSTRAINT ck_service_pose_seule
    CHECK (prix_pose_seule_htva IS NULL
           OR (prix_pose_seule_htva >= 0 AND prix_pose_seule_htva < prix_htva));

ALTER TABLE service ADD CONSTRAINT ck_service_pose_seule_forfait
    CHECK (unite <> 'HEURE' OR prix_pose_seule_htva IS NULL);

COMMENT ON COLUMN service.prix_pose_seule_htva IS
    'Prix hors TVA quand le client apporte ses pièces. Nul : choix non proposé. Toujours nul pour une prestation à l''heure.';

-- Pièces qu'une prestation consomme quand le garage les fournit. Une ligne par pièce.
--
-- Pas de suppression logique : la composition est une propriété de la prestation, et
-- chaque changement s'écrit dans historique_modification_catalogue (champ composition,
-- avant et après). La clé vers la prestation suit sa suppression définitive (RM-29), la
-- clé vers la pièce la refuse : une pièce présente dans une composition se désactive.

CREATE TABLE composition_prestation (
    id          BIGSERIAL PRIMARY KEY,
    service_id  BIGINT NOT NULL,
    piece_id    BIGINT NOT NULL,
    quantite    INTEGER NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  VARCHAR(120),
    updated_by  VARCHAR(120),
    CONSTRAINT fk_composition_service FOREIGN KEY (service_id) REFERENCES service (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_composition_piece FOREIGN KEY (piece_id) REFERENCES piece (id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_composition_quantite CHECK (quantite > 0),
    CONSTRAINT uq_composition_prestation UNIQUE (service_id, piece_id)
);

CREATE INDEX ix_composition_piece ON composition_prestation (piece_id);

COMMENT ON TABLE composition_prestation IS
    'Pièces consommées par une prestation quand le garage les fournit. Changements tracés dans historique_modification_catalogue.';

-- =====================================================================================
-- Enregistrement au balayage d'anonymisation (F23, V28), liste réécrite en entier comme
-- V75 : les colonnes d'audit de la composition portent l'adresse de l'administrateur
-- qui l'a écrite. SchemaIT.listeDesTracesExhaustive casse la build sans cette ligne.
-- =====================================================================================

CREATE OR REPLACE FUNCTION fn_tables_traces_audit()
    RETURNS TABLE (nom_table TEXT, nom_colonne TEXT) AS $$
    VALUES
        ('avis'::TEXT, 'created_by'::TEXT),
        ('avis', 'updated_by'),
        ('avoir', 'created_by'),
        ('avoir', 'updated_by'),
        ('categorie', 'created_by'),
        ('categorie', 'updated_by'),
        ('clef_api', 'created_by'),
        ('clef_api', 'updated_by'),
        ('code_secours', 'created_by'),
        ('code_secours', 'updated_by'),
        ('commande', 'created_by'),
        ('commande', 'updated_by'),
        ('compteur_avoir', 'updated_by'),
        ('compteur_facture', 'updated_by'),
        ('composition_prestation', 'created_by'),
        ('composition_prestation', 'updated_by'),
        ('consentement', 'created_by'),
        ('consentement', 'updated_by'),
        ('conversation', 'created_by'),
        ('conversation', 'updated_by'),
        ('demande_annulation', 'created_by'),
        ('demande_annulation', 'updated_by'),
        ('devis', 'created_by'),
        ('devis', 'updated_by'),
        ('encaissement', 'created_by'),
        ('encaissement', 'updated_by'),
        ('evenement_securite_compte', 'created_by'),
        ('evenement_securite_compte', 'updated_by'),
        ('facture', 'created_by'),
        ('facture', 'updated_by'),
        ('historique_modification_catalogue', 'created_by'),
        ('historique_modification_catalogue', 'updated_by'),
        ('historique_modification_commande', 'created_by'),
        ('historique_modification_commande', 'updated_by'),
        ('historique_modification_compte', 'created_by'),
        ('historique_modification_compte', 'updated_by'),
        ('historique_statut_intervention', 'created_by'),
        ('historique_statut_intervention', 'updated_by'),
        ('historique_statut_utilisateur', 'created_by'),
        ('historique_statut_utilisateur', 'updated_by'),
        ('indisponibilite', 'created_by'),
        ('indisponibilite', 'updated_by'),
        ('intervention', 'created_by'),
        ('intervention', 'updated_by'),
        ('ligne_intervention', 'created_by'),
        ('ligne_intervention', 'updated_by'),
        ('ligne_panier', 'created_by'),
        ('ligne_panier', 'updated_by'),
        ('message', 'created_by'),
        ('message', 'updated_by'),
        ('notification', 'created_by'),
        ('notification', 'updated_by'),
        ('paiement', 'created_by'),
        ('paiement', 'updated_by'),
        ('panier', 'created_by'),
        ('panier', 'updated_by'),
        ('parametre_atelier', 'updated_by'),
        ('photo', 'created_by'),
        ('photo', 'updated_by'),
        ('piece', 'created_by'),
        ('piece', 'updated_by'),
        ('place_parking', 'created_by'),
        ('place_parking', 'updated_by'),
        ('plage_ouverture', 'created_by'),
        ('plage_ouverture', 'updated_by'),
        ('poste_atelier', 'created_by'),
        ('poste_atelier', 'updated_by'),
        ('rdv', 'created_by'),
        ('rdv', 'updated_by'),
        ('rdv_service', 'created_by'),
        ('rdv_service', 'updated_by'),
        ('reservation_parking', 'created_by'),
        ('reservation_parking', 'updated_by'),
        ('service', 'created_by'),
        ('service', 'updated_by'),
        ('utilisateur', 'created_by'),
        ('utilisateur', 'updated_by'),
        ('vehicule', 'created_by'),
        ('vehicule', 'updated_by'),
        ('version_document', 'created_by'),
        ('version_document', 'updated_by');
$$ LANGUAGE sql IMMUTABLE;
