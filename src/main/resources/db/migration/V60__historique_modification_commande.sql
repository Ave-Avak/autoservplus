-- =====================================================================================
-- Historique des modifications d'une commande par le garage.
--
-- La date de remise des pièces (V59) fixe le point de départ du délai de rétractation.
-- Chaque saisie ou correction garde l'auteur, l'instant et l'ancienne valeur : c'est ce
-- qu'il faudra montrer si le membre conteste le délai qu'on lui oppose. Les colonnes
-- d'audit de commande ne gardent que le dernier geste.
--
-- Forme de historique_modification_compte (V57) : une ligne par champ modifié, valeur
-- avant et après en texte, append-only. ck_histo_commande_champ n'admet que DATE_REMISE,
-- seul champ que le garage modifie aujourd'hui. Un champ de plus s'ajoute à la
-- contrainte, sans nouvelle table.
--
-- Clés étrangères :
--   commande_id : ON DELETE RESTRICT, comme demande_annulation et facture. Une commande
--   ne se supprime pas physiquement, et la trace ne doit pas partir avec elle.
--   auteur_id   : ON DELETE SET NULL, comme les autres journaux. La trace survit à la
--   disparition du compte de son auteur.
--
-- Aucune donnée personnelle du membre : une date et la référence d'un compte du garage.
-- Rien n'est donc à vider à l'anonymisation (F23).
-- =====================================================================================

CREATE TABLE historique_modification_commande
(
    id           BIGSERIAL PRIMARY KEY,
    commande_id  BIGINT       NOT NULL,
    champ        VARCHAR(20)  NOT NULL,
    valeur_avant TEXT,
    valeur_apres TEXT,
    horodatage   TIMESTAMPTZ  NOT NULL,
    auteur_id    BIGINT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(120),
    updated_by   VARCHAR(120),

    CONSTRAINT fk_histo_commande_commande
        FOREIGN KEY (commande_id) REFERENCES commande (id)
            ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_histo_commande_auteur
        FOREIGN KEY (auteur_id) REFERENCES utilisateur (id)
            ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_histo_commande_champ
        CHECK (champ IN ('DATE_REMISE')),
    CONSTRAINT ck_histo_commande_changement
        CHECK (valeur_avant IS DISTINCT FROM valeur_apres)
);

CREATE INDEX ix_histo_commande_commande
    ON historique_modification_commande (commande_id, horodatage DESC);

COMMENT ON TABLE historique_modification_commande IS
    'Journal append-only des modifications d''une commande par le garage : saisie et '
    'correction de la date de remise des pièces (V59), avec auteur et ancienne valeur.';

-- =====================================================================================
-- Enregistrement au balayage d'anonymisation (F23, V28), liste réécrite en entier comme
-- V57. SchemaIT.listeDesTracesExhaustive casse la build sans cette redéclaration.
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
