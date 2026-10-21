-- =====================================================================================
-- Second facteur TOTP, changement de mot de passe impose, definition du mot de passe
-- d un administrateur cree depuis le back-office (CdC 5.2.3, Strategie securite §17.4).
--
-- 1) LE SECRET TOTP EST CHIFFRE, PAS HACHE
--
--    Un mot de passe se verifie contre une empreinte ; un code TOTP se RECALCULE a
--    partir du secret, qu il faut donc pouvoir relire. D ou AES-256-GCM, cle maitre
--    hors base (variable d environnement). mfa_cle_version dit quelle cle a chiffre la
--    ligne : une rotation se suit par une requete, et le service rechiffre a la volee
--    sous la cle active a chaque verification reussie.
--
--    mfa_active_le NULL avec un secret present = enrolement commence, pas confirme. Le
--    secret n est actif qu apres un premier code valide : un QR code scanne de travers
--    ne doit pas enfermer le titulaire hors de son compte.
--
-- 2) ANTI-REJEU : mfa_dernier_pas
--
--    Numero du dernier pas de 30 s accepte. Tout code d un pas inferieur OU EGAL est
--    refuse : un code intercepte ne sert qu une fois, meme dans sa fenetre de validite.
--
-- 3) VERROUILLAGE PROGRESSIF : mfa_echecs, mfa_verrouillages
--
--    Cinq echecs posent verrouille_jusqu_a (colonne du socle, deja lue a la connexion),
--    pour une duree qui double a chaque verrouillage, plafonnee a 24 h. Les compteurs
--    sont EN BASE et non en memoire : un redemarrage ne doit pas rendre un budget neuf
--    a qui detient deja le mot de passe. Remis a zero au succes final de la connexion.
--
-- 4) JETON DE DEFINITION : HACHE, ET DISTINCT DES DEUX AUTRES
--
--    Troisieme jeton du compte, pour la meme raison que V38 : un usage, un jeton. Il est
--    stocke en SHA-256, et non en clair comme jeton_verification et
--    jeton_changement_email (dette nommee au registre). SHA-256 et non BCrypt : un jeton
--    de 256 bits ne se force pas, et l empreinte deterministe garde la recherche par
--    index — BCrypt imposerait de balayer la table.
--
-- 5) DEUX TABLES NOUVELLES, ET POURQUOI PAS historique_statut_utilisateur
--
--    Le journal de V40 enregistre des TRANSITIONS d etat : statut_avant et statut_apres
--    y sont NOT NULL, et ck_histo_utilisateur_transition exige qu ils different. Un
--    evenement de securite n a ni avant ni apres ; l y verser obligerait a rendre ces
--    colonnes nullables et a affaiblir le CHECK qui fait la valeur de cette table.
--
--    Le type d evenement porte un CHECK enumere : notification.type, qui n en a pas,
--    laisse passer une valeur inconnue et fait tomber l ecran a la lecture (registre).
--
-- 6) LES SUPER-ADMINISTRATEURS EXISTANTS DOIVENT CHANGER LEUR MOT DE PASSE
--
--    Le compte exploitant nait avec le secret du fichier .env. Ceux crees avant cette
--    migration ne seraient jamais contraints, l indicateur n etant pose qu a la
--    creation. Au pire, un exploitant ayant deja change le sien le change une fois de
--    trop. Le compte admin de demonstration est un ADMINISTRATEUR : il n est pas vise.
-- =====================================================================================

ALTER TABLE utilisateur
    ADD COLUMN doit_changer_mot_de_passe   BOOLEAN  NOT NULL DEFAULT false,
    ADD COLUMN mfa_secret_chiffre          BYTEA,
    ADD COLUMN mfa_cle_version             SMALLINT,
    ADD COLUMN mfa_active_le               TIMESTAMPTZ,
    ADD COLUMN mfa_dernier_pas             BIGINT,
    ADD COLUMN mfa_echecs                  SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN mfa_verrouillages           SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN jeton_definition_hache      VARCHAR(64),
    ADD COLUMN jeton_definition_expiration TIMESTAMPTZ;

ALTER TABLE utilisateur
    ADD CONSTRAINT ck_utilisateur_mfa_secret
        CHECK (num_nonnulls(mfa_secret_chiffre, mfa_cle_version) IN (0, 2)),
    ADD CONSTRAINT ck_utilisateur_mfa_active
        CHECK (mfa_active_le IS NULL OR mfa_secret_chiffre IS NOT NULL),
    ADD CONSTRAINT ck_utilisateur_mfa_compteurs
        CHECK (mfa_echecs >= 0 AND mfa_verrouillages >= 0),
    ADD CONSTRAINT ck_utilisateur_jeton_definition
        CHECK (num_nonnulls(jeton_definition_hache, jeton_definition_expiration) IN (0, 2));

CREATE UNIQUE INDEX uq_utilisateur_jeton_definition
    ON utilisateur (jeton_definition_hache)
    WHERE jeton_definition_hache IS NOT NULL;

UPDATE utilisateur
SET doit_changer_mot_de_passe = true
WHERE type_utilisateur = 'SUPER_ADMINISTRATEUR';

COMMENT ON COLUMN utilisateur.mfa_secret_chiffre IS
    'Secret TOTP de 160 bits, chiffre en AES-256-GCM (nonce de 12 octets, puis chiffre '
    'et tag). Donnee authentifiee : id du compte et version de cle — un chiffre recopie '
    'sur une autre ligne ne se dechiffre pas.';
COMMENT ON COLUMN utilisateur.mfa_dernier_pas IS
    'Dernier pas TOTP (30 s) accepte. Tout code d un pas inferieur ou egal est refuse.';
COMMENT ON COLUMN utilisateur.jeton_definition_hache IS
    'SHA-256 du jeton du lien de definition du mot de passe d un administrateur cree '
    'depuis le back-office. Usage unique, 24 h. Le jeton en clair ne vit que dans le lien.';

CREATE TABLE code_secours
(
    id             BIGSERIAL PRIMARY KEY,
    utilisateur_id BIGINT       NOT NULL,
    empreinte      VARCHAR(60)  NOT NULL,
    consomme_le    TIMESTAMPTZ,
    invalide_le    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(120),
    updated_by     VARCHAR(120),

    CONSTRAINT fk_code_secours_utilisateur
        FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    -- Un code est consomme OU remplace par une regeneration, jamais les deux : c est
    -- la date qui dit pourquoi il ne sert plus.
    CONSTRAINT ck_code_secours_un_seul_motif
        CHECK (num_nonnulls(consomme_le, invalide_le) <= 1)
);

CREATE INDEX ix_code_secours_utilisables
    ON code_secours (utilisateur_id)
    WHERE consomme_le IS NULL AND invalide_le IS NULL;

COMMENT ON TABLE code_secours IS
    'Codes de secours du second facteur, en BCrypt de cout 10. Jamais supprimes : un code '
    'consomme ou remplace garde sa date, qui est la trace de son usage.';

CREATE TABLE evenement_securite_compte
(
    id             BIGSERIAL PRIMARY KEY,
    utilisateur_id BIGINT       NOT NULL,
    type           VARCHAR(40)  NOT NULL,
    horodatage     TIMESTAMPTZ  NOT NULL,
    auteur_id      BIGINT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(120),
    updated_by     VARCHAR(120),

    CONSTRAINT fk_evenement_securite_compte
        FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_evenement_securite_auteur
        FOREIGN KEY (auteur_id) REFERENCES utilisateur (id)
            ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_evenement_securite_type
        CHECK (type IN ('MFA_ACTIVE', 'MFA_DESACTIVE', 'SECOND_FACTEUR_ECHEC',
                        'SECOND_FACTEUR_VERROUILLAGE', 'CODE_SECOURS_CONSOMME',
                        'CODES_SECOURS_REGENERES', 'MFA_REINITIALISE',
                        'MOT_DE_PASSE_DEFINI', 'MOT_DE_PASSE_CHANGE',
                        'ADMINISTRATEUR_CREE', 'LIEN_DEFINITION_ENVOYE'))
);

CREATE INDEX ix_evenement_securite_compte
    ON evenement_securite_compte (utilisateur_id, horodatage DESC);

COMMENT ON TABLE evenement_securite_compte IS
    'Journal append-only des evenements de securite d un compte. Ne porte jamais de '
    'secret, de code ni d adresse IP : le fait et sa date suffisent a l enquete.';

-- =====================================================================================
-- Deux tables de plus portent created_by / updated_by : la liste enumeree du balayage
-- d anonymisation (V28) est reecrite en entier, comme V33 et V40 l ont fait.
-- SchemaIT.listeDesTracesExhaustive casserait la build sans cette redeclaration.
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
