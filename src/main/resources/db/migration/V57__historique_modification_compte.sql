-- =====================================================================================
-- Historique des modifications d'un compte (CdC 5.2.2 et 5.2.3, A12).
--
-- 1) POURQUOI UNE TABLE DE PLUS
--
--    Les colonnes d'audit d'utilisateur gardent le dernier geste, jamais la suite, et
--    historique_statut_utilisateur refuse toute ligne qui ne change pas le statut
--    (ck_histo_utilisateur_transition). Une adresse corrigée par l'administrateur à la
--    demande du membre, ou un nom changé par le membre, ne laissait donc aucune trace.
--    La forme reprend historique_modification_catalogue (V25) : une ligne par champ
--    modifié, avec la valeur avant et la valeur après, append-only.
--
-- 2) LES VALEURS SONT DES DONNÉES PERSONNELLES
--
--    Un ancien nom ou une ancienne adresse reste une donnée sur la personne. L'export
--    de l'article 15 (F22) restitue donc ces lignes, et l'anonymisation (F23) vide
--    valeur_avant et valeur_apres en gardant le champ, la date et l'auteur : sans
--    cela, l'historique rendrait ce que l'anonymisation vient d'effacer. Aucun secret
--    n'y entre jamais, ni empreinte de mot de passe ni secret TOTP : ck_histo_compte_champ
--    n'admet que les champs du profil et l'adresse de courriel.
--
-- 3) LE MOTIF EST EXIGÉ QUAND L'AUTEUR N'EST PAS LE TITULAIRE
--
--    Un administrateur ne modifie un compte qu'à la demande explicite du membre (A12),
--    et le motif note cette demande. Le titulaire qui modifie son propre profil n'a
--    pas à se justifier. Un auteur nul (compte auteur disparu, ON DELETE SET NULL)
--    laisse passer la ligne : la trace doit survivre à son auteur.
--
-- 4) PAS DE CHECK valeur_avant <> valeur_apres
--
--    L'anonymisation met les deux valeurs à NULL : un tel CHECK la ferait échouer.
--    L'entité refuse elle-même une ligne sans changement.
-- =====================================================================================

CREATE TABLE historique_modification_compte
(
    id             BIGSERIAL PRIMARY KEY,
    utilisateur_id BIGINT       NOT NULL,
    champ          VARCHAR(20)  NOT NULL,
    valeur_avant   TEXT,
    valeur_apres   TEXT,
    horodatage     TIMESTAMPTZ  NOT NULL,
    auteur_id      BIGINT,
    motif          VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(120),
    updated_by     VARCHAR(120),

    CONSTRAINT fk_histo_compte_utilisateur
        FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_histo_compte_auteur
        FOREIGN KEY (auteur_id) REFERENCES utilisateur (id)
            ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_histo_compte_champ
        CHECK (champ IN ('PRENOM', 'NOM', 'TELEPHONE', 'RUE', 'NUMERO_RUE', 'CODE_POSTAL',
                         'LOCALITE', 'PAYS', 'LANGUE', 'EMAIL')),
    CONSTRAINT ck_histo_compte_motif
        CHECK (auteur_id IS NULL OR auteur_id = utilisateur_id OR motif IS NOT NULL)
);

-- Lecture par compte, du plus récent au plus ancien : fiche du back-office et export.
CREATE INDEX ix_histo_compte_utilisateur
    ON historique_modification_compte (utilisateur_id, horodatage DESC);

COMMENT ON TABLE historique_modification_compte IS
    'Journal append-only des modifications du profil et de l''adresse de courriel, par '
    'le titulaire ou par un administrateur. Valeurs vidées à l''anonymisation (F23), '
    'restituées par l''export (F22).';
COMMENT ON CONSTRAINT ck_histo_compte_motif ON historique_modification_compte IS
    'Un administrateur modifie un compte à la demande du membre (A12) : le motif note '
    'cette demande. Le titulaire qui modifie son profil n''en donne pas.';

-- =====================================================================================
-- Enregistrement au balayage d'anonymisation (F23, V28). La liste est énumérée et non
-- dérivée du catalogue : elle est réécrite en entier, comme V33, V40 et V41 l'ont fait.
-- SchemaIT.listeDesTracesExhaustive casse la build sans cette redéclaration.
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
