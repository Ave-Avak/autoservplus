-- =====================================================================================
-- Journal des changements de statut d un compte (CdC 5.2.3).
--
-- 1) POURQUOI UNE TABLE, ALORS QUE utilisateur PORTE DEJA updated_by
--
--    Les colonnes d audit gardent le DERNIER geste, jamais la suite. Suspendre puis
--    reactiver puis resuspendre ne laisse qu une ligne, celle du dernier acte, et la
--    question qu on pose a un journal — « qui a suspendu ce compte, quand, et
--    pourquoi ? » — porte precisement sur ce qui a ete ecrase.
--
--    C est le meme raisonnement que pour historique_statut_intervention (V23), dont
--    cette table reprend la forme : append-only, une ligne par transition, ecrite
--    dans la transaction de la transition.
--
-- 2) L AUTEUR EST NULLABLE, ET CE N EST PAS UN RELACHEMENT
--
--    ON DELETE SET NULL, comme fk_histo_statut_auteur. Un compte auteur peut
--    disparaitre ; la trace, elle, doit survivre — sans quoi supprimer un compte
--    effacerait l historique des decisions qu il a prises sur les autres. La ligne
--    garde alors le fait sans l auteur, ce qui vaut mieux que l inverse.
--
--    Les comptes administrateurs ne s anonymisent pas (garde de Utilisateur), donc le
--    cas reste theorique ; la contrainte est neanmoins posee, parce qu une FK en
--    RESTRICT ferait echouer une suppression legitime des annees plus tard.
--
-- 3) LE MOTIF EST OBLIGATOIRE A LA SUSPENSION, PAS EN BASE
--
--    Aucun CHECK ne l impose : la meme table sert la reactivation, ou un motif n a
--    pas de sens, et un CHECK conditionnel au statut_apres se lirait mal pour ce
--    qu il apporte. L obligation est portee par le service, la ou la distinction
--    entre suspendre et reactiver existe.
--
-- 4) PAS DE CHECK ENUMERANT LES STATUTS
--
--    Ecart assume avec V23, et motive : ck_utilisateur_statut enumere deja les quatre
--    valeurs sur la table utilisateur, et la dupliquer ici creerait deux listes a
--    tenir d accord. Le registre note d ailleurs le defaut inverse — notification.type
--    sans CHECK la ou statut et canal en ont un. Ici la valeur vient toujours d une
--    transition ecrite par le service, jamais d une saisie.
-- =====================================================================================

CREATE TABLE historique_statut_utilisateur
(
    id              BIGSERIAL PRIMARY KEY,
    utilisateur_id  BIGINT       NOT NULL,
    statut_avant    VARCHAR(30)  NOT NULL,
    statut_apres    VARCHAR(30)  NOT NULL,
    horodatage      TIMESTAMPTZ  NOT NULL,
    auteur_id       BIGINT,
    motif           VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_by      VARCHAR(120),

    CONSTRAINT fk_histo_utilisateur_compte
        FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_histo_utilisateur_auteur
        FOREIGN KEY (auteur_id) REFERENCES utilisateur (id)
            ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_histo_utilisateur_transition
        CHECK (statut_avant <> statut_apres)
);

-- Lecture par compte, du plus recent au plus ancien : c est l unique acces prevu.
CREATE INDEX ix_histo_utilisateur_compte
    ON historique_statut_utilisateur (utilisateur_id, horodatage DESC);

COMMENT ON TABLE historique_statut_utilisateur IS
    'Journal append-only des suspensions et reactivations de comptes (CdC 5.2.3). '
    'Les colonnes updated_at / updated_by y sont inertes, comme sur les autres tables '
    'append-only du schema : uniformite assumee, notee au registre.';
COMMENT ON CONSTRAINT ck_histo_utilisateur_transition ON historique_statut_utilisateur IS
    'Une transition change le statut. Une ligne ou les deux valeurs coincident '
    'signalerait une ecriture parasite, pas un evenement.';

-- =====================================================================================
-- Enregistrement au balayage d anonymisation (F23, V28).
--
-- La liste de fn_tables_traces_audit() est ENUMEREE et non derivee du catalogue :
-- SchemaIT.listeDesTracesExhaustive casse la build sur toute colonne d audit non
-- declaree. C est exactement ce qui doit arriver ici, et c est ce prix qui garantit
-- qu aucune trace ne fuit en silence. La fonction est donc reecrite en entier, avec
-- les deux entrees de la nouvelle table — comme V33 l avait fait pour version_document.
--
-- Le balayage remplace l adresse par le jeton dans created_by / updated_by. Il ne
-- touche PAS auteur_id, qui designe la ligne d un administrateur : celle-ci n est
-- jamais anonymisee, la garde de Utilisateur.anonymiser s y oppose.
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
        ('facture', 'created_by'),
        ('facture', 'updated_by'),
        ('historique_modification_catalogue', 'created_by'),
        ('historique_modification_catalogue', 'updated_by'),
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
