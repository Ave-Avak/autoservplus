-- Choix du client sur les lignes d'un forfait, et pièces incluses d'une intervention.
--
-- 1) CHOIX FIGÉ SUR LA LIGNE (pieces_client)
--
--    Quand un forfait propose un prix pose seule (V76), le client choisit entre les pièces
--    fournies par le garage et ses propres pièces. Le choix se fige sur la ligne, comme le
--    prix, sur les quatre tables qui portent une prestation : rdv_service, ligne_panier
--    (panier et commande), ligne_devis et ligne_intervention.
--
--    Trois états, sur le patron de accord_membre (V22) :
--      NULL   aucun choix proposé (pièce, prestation à l'heure, forfait sans prix pose seule)
--      false  pièces fournies par le garage
--      true   pièces apportées par le client
--
--    Pas de valeur par défaut : nul est la valeur juste de toutes les lignes existantes,
--    écrites avant que le choix existe, et une ligne écrite sans choix reste « non proposé ».
--    Le choix est figé ici plutôt que relu au catalogue : ligne_devis n'a aucune clé vers la
--    prestation, une facture dont l'archive a disparu se recompose depuis ses lignes, et un
--    prix pose seule retiré après coup ne doit changer aucun document déjà émis.

ALTER TABLE rdv_service ADD COLUMN pieces_client BOOLEAN;
ALTER TABLE ligne_panier ADD COLUMN pieces_client BOOLEAN;
ALTER TABLE ligne_devis ADD COLUMN pieces_client BOOLEAN;
ALTER TABLE ligne_intervention ADD COLUMN pieces_client BOOLEAN;

ALTER TABLE ligne_panier ADD CONSTRAINT ck_ligne_pieces_client
    CHECK (pieces_client IS NULL OR service_id IS NOT NULL);

ALTER TABLE ligne_intervention ADD CONSTRAINT ck_ligne_interv_pieces_client
    CHECK (pieces_client IS NULL OR unite = 'FORFAIT');

COMMENT ON COLUMN rdv_service.pieces_client IS
    'Choix figé à la réservation. NULL : aucun choix proposé. false : pièces fournies par le garage. true : pièces apportées par le client.';
COMMENT ON COLUMN ligne_panier.pieces_client IS
    'Choix figé à l''ajout. NULL : aucun choix proposé (toujours pour une pièce). false : pièces fournies par le garage. true : pièces apportées par le client.';
COMMENT ON COLUMN ligne_devis.pieces_client IS
    'Choix repris de la ligne du panier. NULL : aucun choix proposé. false : pièces fournies par le garage. true : pièces apportées par le client.';
COMMENT ON COLUMN ligne_intervention.pieces_client IS
    'Choix repris de la ligne d''origine ou fixé à l''ajout. NULL : aucun choix proposé (toujours hors FORFAIT). false : pièces fournies par le garage. true : pièces apportées par le client.';

-- 2) PIÈCES INCLUSES D'UNE LIGNE DE FORFAIT
--
--    La composition de la prestation (V76) est copiée sur la ligne de l'intervention quand
--    le garage fournit les pièces, à la naissance de l'intervention et à l'ajout d'un forfait.
--    Ces pièces ne sont pas facturées et n'apparaissent sur aucune facture : leur prix est
--    compris dans celui du forfait. Elles se modifient jusqu'à la clôture, où elles sortent
--    du stock avec les lignes de pièce.
--
--    Pas de suppression logique : une pièce incluse n'existe que par sa ligne, et chaque
--    changement s'écrit dans historique_piece_incluse. La clé vers la ligne suit son retrait,
--    la clé vers la pièce le refuse : une pièce incluse se désactive (RM-29).

CREATE TABLE piece_incluse (
    id                    BIGSERIAL PRIMARY KEY,
    ligne_intervention_id BIGINT NOT NULL,
    piece_id              BIGINT NOT NULL,
    quantite              INTEGER NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            VARCHAR(120),
    updated_by            VARCHAR(120),
    CONSTRAINT fk_piece_incluse_ligne FOREIGN KEY (ligne_intervention_id)
        REFERENCES ligne_intervention (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_piece_incluse_piece FOREIGN KEY (piece_id)
        REFERENCES piece (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_piece_incluse_quantite CHECK (quantite > 0),
    CONSTRAINT uq_piece_incluse UNIQUE (ligne_intervention_id, piece_id)
);

CREATE INDEX ix_piece_incluse_piece ON piece_incluse (piece_id);

COMMENT ON TABLE piece_incluse IS
    'Pièces comprises dans le prix d''une ligne de forfait, copiées de la composition de la prestation. Jamais facturées, décomptées du stock à la clôture.';

-- Journal append-only des pièces incluses, cinquième source du journal d'audit. La copie
-- initiale y est écrite aussi : la trace se lit sans relire l'historique du catalogue.
-- La ligne peut disparaître avec son retrait, la trace reste (SET NULL), rattachée à
-- l'intervention, qui la garde tant qu'elle existe.

CREATE TABLE historique_piece_incluse (
    id                    BIGSERIAL PRIMARY KEY,
    intervention_id       BIGINT NOT NULL,
    ligne_intervention_id BIGINT,
    piece_id              BIGINT NOT NULL,
    evenement             VARCHAR(12) NOT NULL,
    quantite_avant        INTEGER,
    quantite_apres        INTEGER,
    horodatage            TIMESTAMPTZ NOT NULL,
    auteur_id             BIGINT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by            VARCHAR(120),
    updated_by            VARCHAR(120),
    CONSTRAINT fk_histo_incluse_interv FOREIGN KEY (intervention_id)
        REFERENCES intervention (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_histo_incluse_ligne FOREIGN KEY (ligne_intervention_id)
        REFERENCES ligne_intervention (id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_histo_incluse_piece FOREIGN KEY (piece_id)
        REFERENCES piece (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_histo_incluse_auteur FOREIGN KEY (auteur_id)
        REFERENCES utilisateur (id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_histo_incluse_evenement CHECK (evenement IN ('COPIE', 'AJOUT', 'MODIFICATION', 'RETRAIT')),
    CONSTRAINT ck_histo_incluse_quantites CHECK (
        (evenement IN ('COPIE', 'AJOUT') AND quantite_avant IS NULL AND quantite_apres > 0)
        OR (evenement = 'MODIFICATION' AND quantite_avant > 0 AND quantite_apres > 0)
        OR (evenement = 'RETRAIT' AND quantite_avant > 0 AND quantite_apres IS NULL))
);

CREATE INDEX ix_histo_incluse_interv ON historique_piece_incluse (intervention_id);
CREATE INDEX ix_histo_incluse_piece ON historique_piece_incluse (piece_id);

COMMENT ON TABLE historique_piece_incluse IS
    'Journal append-only des pièces incluses : copie, ajout, modification et retrait, quantités avant et après, auteur et instant métier.';

-- =====================================================================================
-- Enregistrement au balayage d'anonymisation (F23, V28), liste réécrite en entier comme
-- V76 : les colonnes d'audit des deux tables portent l'adresse de l'administrateur qui
-- les a écrites. SchemaIT.listeDesTracesExhaustive casse la build sans ces lignes.
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
        ('historique_piece_incluse', 'created_by'),
        ('historique_piece_incluse', 'updated_by'),
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
        ('piece_incluse', 'created_by'),
        ('piece_incluse', 'updated_by'),
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
