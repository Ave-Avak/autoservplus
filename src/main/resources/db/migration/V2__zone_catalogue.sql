-- =====================================================================================
-- Zone 2 : catalogue
-- Prestations et pieces detachees proposees par le garage, avec leurs categories
-- et leurs illustrations.
-- Reference : livrable 08, livrable 09
-- =====================================================================================

CREATE TABLE categorie (
                           id          BIGSERIAL     PRIMARY KEY,
                           code        VARCHAR(40)   NOT NULL,
                           libelle     VARCHAR(120)  NOT NULL,
                           type        VARCHAR(20)   NOT NULL,
                           description TEXT,
                           ordre       SMALLINT      NOT NULL DEFAULT 0,
                           actif       BOOLEAN       NOT NULL DEFAULT TRUE,
                           created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
                           updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
                           created_by  VARCHAR(120),
                           updated_by  VARCHAR(120),
                           deleted_at  TIMESTAMPTZ,
                           deleted_by  VARCHAR(120),
                           CONSTRAINT uq_categorie_code UNIQUE (code),
                           CONSTRAINT ck_categorie_type CHECK (type IN ('SERVICE', 'PIECE'))
);
CREATE INDEX ix_categorie_type ON categorie (type) WHERE deleted_at IS NULL;

CREATE TABLE service (
                         id            BIGSERIAL     PRIMARY KEY,
                         reference     UUID          NOT NULL DEFAULT gen_random_uuid(),
                         categorie_id  BIGINT        NOT NULL,
                         code          VARCHAR(40)   NOT NULL,
                         libelle       VARCHAR(150)  NOT NULL,
                         description   TEXT,
                         prix_htva     NUMERIC(10,2) NOT NULL,
                         taux_tva      NUMERIC(5,2)  NOT NULL DEFAULT 21.00,
                         duree_minutes INTEGER       NOT NULL,
                         actif         BOOLEAN       NOT NULL DEFAULT TRUE,
                         created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
                         updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
                         created_by    VARCHAR(120),
                         updated_by    VARCHAR(120),
                         deleted_at    TIMESTAMPTZ,
                         deleted_by    VARCHAR(120),
                         CONSTRAINT uq_service_reference UNIQUE (reference),
                         CONSTRAINT uq_service_code      UNIQUE (code),
                         CONSTRAINT fk_service_categorie FOREIGN KEY (categorie_id)
                             REFERENCES categorie (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                         CONSTRAINT ck_service_prix  CHECK (prix_htva >= 0),
                         CONSTRAINT ck_service_tva   CHECK (taux_tva >= 0 AND taux_tva <= 100),
                         CONSTRAINT ck_service_duree CHECK (duree_minutes > 0)
);
COMMENT ON COLUMN service.duree_minutes IS 'Duree standard de la prestation, utilisee pour calculer les creneaux.';
CREATE INDEX ix_service_categorie ON service (categorie_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_service_actif     ON service (actif)        WHERE deleted_at IS NULL;

CREATE TABLE piece (
                       id                  BIGSERIAL     PRIMARY KEY,
                       reference           UUID          NOT NULL DEFAULT gen_random_uuid(),
                       categorie_id        BIGINT        NOT NULL,
                       reference_fabricant VARCHAR(60)   NOT NULL,
                       libelle             VARCHAR(150)  NOT NULL,
                       description         TEXT,
                       marque              VARCHAR(80),
                       prix_htva           NUMERIC(10,2) NOT NULL,
                       taux_tva            NUMERIC(5,2)  NOT NULL DEFAULT 21.00,
                       quantite_stock      INTEGER       NOT NULL DEFAULT 0,
                       seuil_alerte        INTEGER       NOT NULL DEFAULT 0,
                       actif               BOOLEAN       NOT NULL DEFAULT TRUE,
                       created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       created_by          VARCHAR(120),
                       updated_by          VARCHAR(120),
                       deleted_at          TIMESTAMPTZ,
                       deleted_by          VARCHAR(120),
                       CONSTRAINT uq_piece_reference UNIQUE (reference),
                       CONSTRAINT uq_piece_fabricant UNIQUE (reference_fabricant),
                       CONSTRAINT fk_piece_categorie FOREIGN KEY (categorie_id)
                           REFERENCES categorie (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                       CONSTRAINT ck_piece_prix  CHECK (prix_htva >= 0),
                       CONSTRAINT ck_piece_stock CHECK (quantite_stock >= 0),
                       CONSTRAINT ck_piece_seuil CHECK (seuil_alerte >= 0)
);
CREATE INDEX ix_piece_categorie ON piece (categorie_id) WHERE deleted_at IS NULL;

CREATE TABLE photo (
                       id         BIGSERIAL     PRIMARY KEY,
                       service_id BIGINT,
                       piece_id   BIGINT,
                       chemin     VARCHAR(255)  NOT NULL,
                       texte_alt  VARCHAR(200)  NOT NULL,
                       ordre      SMALLINT      NOT NULL DEFAULT 0,
                       created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       created_by VARCHAR(120),
                       updated_by VARCHAR(120),
                       deleted_at TIMESTAMPTZ,
                       deleted_by VARCHAR(120),
                       CONSTRAINT fk_photo_service FOREIGN KEY (service_id)
                           REFERENCES service (id) ON DELETE CASCADE ON UPDATE CASCADE,
                       CONSTRAINT fk_photo_piece FOREIGN KEY (piece_id)
                           REFERENCES piece (id) ON DELETE CASCADE ON UPDATE CASCADE,
                       CONSTRAINT ck_photo_origine_unique CHECK (
                           (service_id IS NOT NULL AND piece_id IS NULL)
                               OR (service_id IS NULL     AND piece_id IS NOT NULL)
                           )
);
COMMENT ON COLUMN photo.texte_alt IS 'Obligatoire : exigence WCAG 2.1 niveau AA, critere 1.1.1.';
CREATE INDEX ix_photo_service ON photo (service_id) WHERE service_id IS NOT NULL;
CREATE INDEX ix_photo_piece   ON photo (piece_id)   WHERE piece_id   IS NOT NULL;