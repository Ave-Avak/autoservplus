-- =====================================================================================
-- Devis émis par le membre depuis son panier (CdC F15).
--
-- 1) UN INSTANTANÉ, PAS UNE VUE DU PANIER
--
--    Le devis reproduit le panier au moment où le membre le demande : lignes, prix et
--    taux, client et véhicule nommés. Il est recopié ici parce que le panier change
--    ensuite (articles retirés, panier converti en commande) et qu'un devis présenté au
--    garage doit pouvoir être reproduit à l'identique. Le PDF n'est pas archivé : il se
--    recompose à la demande depuis ces lignes, qui ne changent plus.
--
-- 2) NI SUPPRESSION LOGIQUE NI CONVERSION
--
--    Un devis ne se modifie pas, ne s'accepte pas et ne devient pas une commande en V1.
--    Le membre ne le supprime pas. La suppression du compte (F23) le supprime
--    physiquement, lignes comprises par la cascade : aucune obligation de conservation
--    ne le couvre, contrairement à une facture. deleted_at n'aurait donc aucun usage.
--
-- 3) NUMÉRO DEV-AAAA-NNNN PAR COMPTEUR ANNUEL
--
--    compteur_devis repart à 1 chaque année au garage, incrémenté dans la transaction
--    qui insère le devis. Un devis n'est pas une pièce comptable : un trou dans la
--    suite n'aurait aucune conséquence, mais le compteur en table évite d'en créer.
--
-- 4) LIGNES SANS COLONNES D'AUDIT
--
--    Une ligne naît avec son devis, dans la même transaction, et ne change plus. Les
--    colonnes d'audit du devis suffisent à dater et attribuer l'ensemble.
-- =====================================================================================

CREATE TABLE devis
(
    id                 BIGSERIAL     PRIMARY KEY,
    reference          UUID          NOT NULL DEFAULT gen_random_uuid(),
    numero             VARCHAR(20)   NOT NULL,
    membre_id          BIGINT        NOT NULL,
    date_emission      TIMESTAMPTZ   NOT NULL,
    valable_jusqu_au   DATE          NOT NULL,
    langue_document    VARCHAR(2)    NOT NULL,
    client_prenom      VARCHAR(80)   NOT NULL,
    client_nom         VARCHAR(80)   NOT NULL,
    client_rue         VARCHAR(150),
    client_numero_rue  VARCHAR(15),
    client_code_postal VARCHAR(10),
    client_localite    VARCHAR(100),
    client_pays        VARCHAR(60),
    vehicule_plaque    VARCHAR(15)   NOT NULL,
    vehicule_marque    VARCHAR(60)   NOT NULL,
    vehicule_modele    VARCHAR(80)   NOT NULL,
    montant_htva       NUMERIC(10,2) NOT NULL,
    montant_tva        NUMERIC(10,2) NOT NULL,
    montant_tvac       NUMERIC(10,2) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         VARCHAR(120),
    updated_by         VARCHAR(120),

    CONSTRAINT uq_devis_reference UNIQUE (reference),
    CONSTRAINT uq_devis_numero UNIQUE (numero),
    CONSTRAINT fk_devis_membre
        FOREIGN KEY (membre_id) REFERENCES utilisateur (id)
            ON UPDATE CASCADE ON DELETE RESTRICT,
    -- Langue des documents du garage (V66) : l'anglais n'est admis pour aucun document.
    CONSTRAINT ck_devis_langue_document CHECK (langue_document IN ('fr', 'nl')),
    CONSTRAINT ck_devis_montants CHECK (montant_htva >= 0 AND montant_tva >= 0 AND montant_tvac > 0),
    CONSTRAINT ck_devis_coherence CHECK (montant_tvac = montant_htva + montant_tva)
);

CREATE INDEX ix_devis_membre ON devis (membre_id, date_emission DESC);

COMMENT ON TABLE devis IS
    'Devis F15 émis par le membre depuis son panier : instantané des lignes, du client et '
    'du véhicule, valable jusqu''à valable_jusqu_au. Supprimé avec le compte (F23).';
COMMENT ON COLUMN devis.valable_jusqu_au IS
    'Dernier jour de validité, au fuseau du garage : jour d''émission plus 30 jours.';

CREATE TABLE ligne_devis
(
    id                 BIGSERIAL     PRIMARY KEY,
    devis_id           BIGINT        NOT NULL,
    rang               SMALLINT      NOT NULL,
    libelle            VARCHAR(150)  NOT NULL,
    quantite           SMALLINT      NOT NULL,
    prix_unitaire_htva NUMERIC(10,2) NOT NULL,
    taux_tva           NUMERIC(5,2)  NOT NULL,

    CONSTRAINT fk_ligne_devis_devis
        FOREIGN KEY (devis_id) REFERENCES devis (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT uq_ligne_devis_rang UNIQUE (devis_id, rang),
    CONSTRAINT ck_ligne_devis_quantite CHECK (quantite > 0),
    CONSTRAINT ck_ligne_devis_montants CHECK (prix_unitaire_htva >= 0 AND taux_tva >= 0)
);

COMMENT ON TABLE ligne_devis IS
    'Ligne d''un devis : libellé dans la langue du document, prix unitaire hors TVA et taux '
    'repris du panier à l''émission. Le total de ligne et la ventilation se recalculent (CalculTva).';

CREATE TABLE compteur_devis
(
    exercice       SMALLINT PRIMARY KEY,
    dernier_numero INTEGER  NOT NULL,

    CONSTRAINT ck_compteur_devis_numero CHECK (dernier_numero > 0)
);

COMMENT ON TABLE compteur_devis IS
    'Dernier numéro de devis attribué par année au garage (DEV-AAAA-NNNN).';

-- =====================================================================================
-- Enregistrement au balayage d'anonymisation (F23, V28), liste réécrite en entier comme
-- V60. SchemaIT.listeDesTracesExhaustive casse la build sans cette redéclaration. Le
-- devis est supprimé avant le balayage, l'entrée ne sert que si la suppression change.
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
        ('consentement', 'created_by'),
        ('consentement', 'updated_by'),
        ('conversation', 'created_by'),
        ('conversation', 'updated_by'),
        ('demande_annulation', 'created_by'),
        ('demande_annulation', 'updated_by'),
        ('devis', 'created_by'),
        ('devis', 'updated_by'),
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
