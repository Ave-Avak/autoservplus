-- =====================================================================================
-- Zone 1 : identites et acces
-- Reference : livrable 08 (schema de base de donnees), livrable 09 (dictionnaire)
--
-- Conventions valables pour tout le schema :
--   * cles primaires BIGSERIAL, jamais exposees dans les URL publiques
--   * colonne reference UUID sur les entites exposees, contre l enumeration
--   * horodatages TIMESTAMPTZ stockes en UTC
--   * montants NUMERIC(10,2), jamais en virgule flottante
--   * quatre colonnes d audit sur toutes les tables metier
--   * suppression logique par deleted_at / deleted_by, jamais de DELETE physique
-- =====================================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Heritage a table unique : Utilisateur / Membre / Administrateur
CREATE TABLE utilisateur (
    id                   BIGSERIAL     PRIMARY KEY,
    reference            UUID          NOT NULL DEFAULT gen_random_uuid(),
    type_utilisateur     VARCHAR(20)   NOT NULL,
    email                VARCHAR(180)  NOT NULL,
    mot_de_passe_hache   VARCHAR(60)   NOT NULL,
    nom                  VARCHAR(80)   NOT NULL,
    prenom               VARCHAR(80)   NOT NULL,
    telephone            VARCHAR(30),
    rue                  VARCHAR(150),
    numero_rue           VARCHAR(15),
    code_postal          VARCHAR(10),
    localite             VARCHAR(100),
    pays                 VARCHAR(60)   NOT NULL DEFAULT ''Belgique'',
    langue               VARCHAR(2)    NOT NULL DEFAULT ''fr'',
    statut               VARCHAR(30)   NOT NULL DEFAULT ''EN_ATTENTE_VALIDATION'',
    email_verifie        BOOLEAN       NOT NULL DEFAULT FALSE,
    jeton_verification   VARCHAR(64),
    jeton_expiration     TIMESTAMPTZ,
    derniere_connexion   TIMESTAMPTZ,
    tentatives_echouees  SMALLINT      NOT NULL DEFAULT 0,
    verrouille_jusqu_a   TIMESTAMPTZ,
    fonction             VARCHAR(80),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by           VARCHAR(120),
    updated_by           VARCHAR(120),
    deleted_at           TIMESTAMPTZ,
    deleted_by           VARCHAR(120),
    CONSTRAINT uq_utilisateur_reference UNIQUE (reference),
    CONSTRAINT uq_utilisateur_email     UNIQUE (email),
    CONSTRAINT ck_utilisateur_type      CHECK (type_utilisateur IN (''MEMBRE'', ''ADMINISTRATEUR'')),
    CONSTRAINT ck_utilisateur_statut    CHECK (statut IN (''EN_ATTENTE_VALIDATION'', ''ACTIF'', ''SUSPENDU'', ''SUPPRIME'')),
    CONSTRAINT ck_utilisateur_langue    CHECK (langue IN (''fr'', ''nl'', ''en'')),
    CONSTRAINT ck_utilisateur_tentatives CHECK (tentatives_echouees >= 0)
);
COMMENT ON TABLE  utilisateur IS ''Comptes de la plateforme. Heritage a table unique.'';
COMMENT ON COLUMN utilisateur.mot_de_passe_hache IS ''Empreinte BCrypt facteur 12, longueur fixe de 60 caracteres.'';

CREATE INDEX ix_utilisateur_email  ON utilisateur (email)  WHERE deleted_at IS NULL;
CREATE INDEX ix_utilisateur_statut ON utilisateur (statut) WHERE deleted_at IS NULL;
CREATE INDEX ix_utilisateur_jeton  ON utilisateur (jeton_verification) WHERE jeton_verification IS NOT NULL;

CREATE TABLE consentement (
    id                BIGSERIAL     PRIMARY KEY,
    utilisateur_id    BIGINT        NOT NULL,
    type_document     VARCHAR(30)   NOT NULL,
    version_acceptee  VARCHAR(20)   NOT NULL,
    accorde           BOOLEAN       NOT NULL,
    date_consentement TIMESTAMPTZ   NOT NULL DEFAULT now(),
    adresse_ip        VARCHAR(45),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        VARCHAR(120),
    updated_by        VARCHAR(120),
    CONSTRAINT fk_consentement_utilisateur FOREIGN KEY (utilisateur_id)
        REFERENCES utilisateur (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_consentement_type CHECK (type_document IN (''CGV'', ''POLITIQUE_CONFIDENTIALITE'', ''COOKIES'', ''NEWSLETTER''))
);
COMMENT ON TABLE consentement IS ''Preuve horodatee des consentements RGPD. Jamais supprimee : sert de preuve.'';
CREATE INDEX ix_consentement_utilisateur ON consentement (utilisateur_id);

CREATE TABLE clef_api (
    id              BIGSERIAL     PRIMARY KEY,
    reference       UUID          NOT NULL DEFAULT gen_random_uuid(),
    libelle         VARCHAR(120)  NOT NULL,
    clef_hachee     VARCHAR(64)   NOT NULL,
    quota_minute    INTEGER       NOT NULL DEFAULT 60,
    actif           BOOLEAN       NOT NULL DEFAULT TRUE,
    date_expiration TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      VARCHAR(120),
    updated_by      VARCHAR(120),
    deleted_at      TIMESTAMPTZ,
    deleted_by      VARCHAR(120),
    CONSTRAINT uq_clef_api_reference UNIQUE (reference),
    CONSTRAINT uq_clef_api_hachee    UNIQUE (clef_hachee),
    CONSTRAINT ck_clef_api_quota     CHECK (quota_minute > 0)
);
COMMENT ON COLUMN clef_api.clef_hachee IS ''Empreinte SHA-256. La valeur en clair n est jamais stockee.'';
