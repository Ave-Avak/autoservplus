-- =====================================================================================
-- Encaissement d'une facture d'atelier au comptoir (RM-20 révisée, H4-2, Q-H-43 à Q-H-46).
--
-- 1) UN ENCAISSEMENT PAR FACTURE, ÉGAL À SON TOTAL
--    Pas de paiement partiel ni mixte en V1 : un encaissement règle la facture entière,
--    par un seul moyen. L'égalité au total TVAC est contrôlée par le domaine
--    (Encaissement.enregistrer), qui lit la facture. Une facture à 0 € n'a rien à régler
--    et n'en reçoit aucun (Q-H-44), d'où le montant strictement positif.
--
-- 2) ANNULATION ET NON SUPPRESSION
--    Une erreur de saisie se corrige par l'annulation, datée, signée et motivée, puis par un
--    nouvel encaissement. L'index unique partiel n'admet qu'un encaissement non annulé par
--    facture, et deux enregistrements simultanés n'en laissent passer qu'un.
--
-- 3) IMMUABLE UNE FOIS ENREGISTRÉ
--    Comme la facture et la note de crédit : seules les colonnes de l'annulation se posent,
--    une fois, et rien ne les retire. Un règlement s'annule, il ne s'efface pas : la
--    suppression d'une ligne est refusée elle aussi.
-- =====================================================================================

CREATE TABLE encaissement
(
    id                BIGSERIAL PRIMARY KEY,
    reference         UUID          NOT NULL DEFAULT gen_random_uuid(),
    facture_id        BIGINT        NOT NULL,
    moyen             VARCHAR(20)   NOT NULL,
    montant           NUMERIC(10,2) NOT NULL,
    date_encaissement DATE          NOT NULL,
    annule_le         TIMESTAMPTZ,
    annule_par        VARCHAR(120),
    motif_annulation  VARCHAR(500),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),
    updated_by        VARCHAR(120),

    CONSTRAINT uq_encaissement_reference UNIQUE (reference),
    CONSTRAINT fk_encaissement_facture FOREIGN KEY (facture_id)
        REFERENCES facture (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_encaissement_moyen CHECK (moyen IN ('TERMINAL', 'ESPECES', 'VIREMENT')),
    CONSTRAINT ck_encaissement_montant CHECK (montant > 0),
    -- Une annulation a sa date, son auteur et son motif, ou rien de tout cela.
    CONSTRAINT ck_encaissement_annulation CHECK (
        (annule_le IS NULL AND annule_par IS NULL AND motif_annulation IS NULL)
     OR (annule_le IS NOT NULL AND annule_par IS NOT NULL
         AND motif_annulation IS NOT NULL AND motif_annulation ~ '[^[:space:]]'))
);

CREATE UNIQUE INDEX uq_encaissement_facture_actif ON encaissement (facture_id) WHERE annule_le IS NULL;
CREATE INDEX ix_encaissement_facture ON encaissement (facture_id);

COMMENT ON TABLE encaissement IS
    'Règlement d''une facture d''atelier au comptoir : terminal, espèces ou virement. Un seul non annulé par facture.';
COMMENT ON COLUMN encaissement.moyen IS
    'TERMINAL, ESPECES ou VIREMENT. ESPECES refusé par le domaine au-delà du plafond légal (art. 67 de la loi du 18 septembre 2017).';
COMMENT ON COLUMN encaissement.montant IS
    'Total TVAC de la facture, contrôlé par le domaine. Pas de paiement partiel en V1.';
COMMENT ON COLUMN encaissement.date_encaissement IS
    'Jour du règlement au fuseau du garage, ni futur ni antérieur à l''émission de la facture.';
COMMENT ON COLUMN encaissement.annule_par IS
    'Administrateur qui a annulé l''encaissement, avec un motif obligatoire.';

CREATE OR REPLACE FUNCTION fn_encaissement_immuable() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Un encaissement enregistré est immuable, seule son annulation se pose.';
    END IF;
    IF OLD.facture_id        IS DISTINCT FROM NEW.facture_id
       OR OLD.moyen             IS DISTINCT FROM NEW.moyen
       OR OLD.montant           IS DISTINCT FROM NEW.montant
       OR OLD.date_encaissement IS DISTINCT FROM NEW.date_encaissement
       OR (OLD.annule_le IS NOT NULL AND (OLD.annule_le IS DISTINCT FROM NEW.annule_le
           OR OLD.annule_par IS DISTINCT FROM NEW.annule_par
           OR OLD.motif_annulation IS DISTINCT FROM NEW.motif_annulation)) THEN
        RAISE EXCEPTION 'Un encaissement enregistré est immuable, seule son annulation se pose.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_encaissement_immuable
    BEFORE UPDATE OR DELETE ON encaissement
    FOR EACH ROW EXECUTE FUNCTION fn_encaissement_immuable();

-- =====================================================================================
-- Enregistrement au balayage d'anonymisation (F23, V28), liste réécrite en entier comme
-- V69. SchemaIT.listeDesTracesExhaustive casse la build sans cette redéclaration.
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
