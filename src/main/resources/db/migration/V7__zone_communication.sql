-- =====================================================================================
-- Zone 7 : communication et avis
-- Notifications adressees aux membres, messagerie entre le membre et le garage,
-- et avis deposes apres intervention.
-- Reference : livrable 08, livrable 09, F27
-- =====================================================================================

CREATE TABLE notification (
                              id           BIGSERIAL    PRIMARY KEY,
                              membre_id    BIGINT       NOT NULL,
                              type         VARCHAR(40)  NOT NULL,
                              titre        VARCHAR(150) NOT NULL,
                              corps        TEXT         NOT NULL,
                              statut       VARCHAR(20)  NOT NULL DEFAULT 'NON_LUE',
                              canal        VARCHAR(20)  NOT NULL DEFAULT 'APPLICATION',
                              date_envoi   TIMESTAMPTZ,
                              date_lecture TIMESTAMPTZ,
                              created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
                              updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
                              created_by   VARCHAR(120),
                              updated_by   VARCHAR(120),
                              deleted_at   TIMESTAMPTZ,
                              deleted_by   VARCHAR(120),
                              CONSTRAINT fk_notification_membre FOREIGN KEY (membre_id)
                                  REFERENCES utilisateur (id) ON DELETE CASCADE ON UPDATE CASCADE,
                              CONSTRAINT ck_notification_statut CHECK (statut IN ('NON_LUE', 'LUE', 'ARCHIVEE')),
                              CONSTRAINT ck_notification_canal  CHECK (canal IN ('APPLICATION', 'EMAIL', 'LES_DEUX'))
);
CREATE INDEX ix_notification_membre ON notification (membre_id, statut) WHERE deleted_at IS NULL;

CREATE TABLE conversation (
                              id              BIGSERIAL    PRIMARY KEY,
                              reference       UUID         NOT NULL DEFAULT gen_random_uuid(),
                              membre_id       BIGINT       NOT NULL,
                              intervention_id BIGINT,
                              sujet           VARCHAR(150) NOT NULL,
                              cloturee        BOOLEAN      NOT NULL DEFAULT FALSE,
                              created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                              updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
                              created_by      VARCHAR(120),
                              updated_by      VARCHAR(120),
                              deleted_at      TIMESTAMPTZ,
                              deleted_by      VARCHAR(120),
                              CONSTRAINT uq_conversation_reference UNIQUE (reference),
                              CONSTRAINT fk_conversation_membre FOREIGN KEY (membre_id)
                                  REFERENCES utilisateur (id) ON DELETE CASCADE ON UPDATE CASCADE,
                              CONSTRAINT fk_conversation_interv FOREIGN KEY (intervention_id)
                                  REFERENCES intervention (id) ON DELETE SET NULL ON UPDATE CASCADE
);
COMMENT ON COLUMN conversation.intervention_id IS 'Nullable : une conversation peut porter sur un sujet general.';
CREATE INDEX ix_conversation_membre ON conversation (membre_id) WHERE deleted_at IS NULL;

CREATE TABLE message (
                         id              BIGSERIAL   PRIMARY KEY,
                         conversation_id BIGINT      NOT NULL,
                         expediteur_id   BIGINT      NOT NULL,
                         role_expediteur VARCHAR(20) NOT NULL,
                         corps           TEXT        NOT NULL,
                         lu              BOOLEAN     NOT NULL DEFAULT FALSE,
                         date_envoi      TIMESTAMPTZ NOT NULL DEFAULT now(),
                         created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                         updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                         created_by      VARCHAR(120),
                         updated_by      VARCHAR(120),
                         deleted_at      TIMESTAMPTZ,
                         deleted_by      VARCHAR(120),
                         CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id)
                             REFERENCES conversation (id) ON DELETE CASCADE ON UPDATE CASCADE,
                         CONSTRAINT fk_message_expediteur FOREIGN KEY (expediteur_id)
                             REFERENCES utilisateur (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                         CONSTRAINT ck_message_role CHECK (role_expediteur IN ('MEMBRE', 'ADMINISTRATEUR'))
);
CREATE INDEX ix_message_conversation ON message (conversation_id, date_envoi) WHERE deleted_at IS NULL;

CREATE TABLE avis (
                      id              BIGSERIAL   PRIMARY KEY,
                      reference       UUID        NOT NULL DEFAULT gen_random_uuid(),
                      membre_id       BIGINT      NOT NULL,
                      intervention_id BIGINT      NOT NULL,
                      note            SMALLINT    NOT NULL,
                      commentaire     TEXT,
                      publie          BOOLEAN     NOT NULL DEFAULT TRUE,
                      signale         BOOLEAN     NOT NULL DEFAULT FALSE,
                      date_depot      TIMESTAMPTZ NOT NULL DEFAULT now(),
                      created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                      updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                      created_by      VARCHAR(120),
                      updated_by      VARCHAR(120),
                      deleted_at      TIMESTAMPTZ,
                      deleted_by      VARCHAR(120),
                      CONSTRAINT uq_avis_reference    UNIQUE (reference),
                      CONSTRAINT uq_avis_intervention UNIQUE (intervention_id),
                      CONSTRAINT fk_avis_membre FOREIGN KEY (membre_id)
                          REFERENCES utilisateur (id) ON DELETE CASCADE ON UPDATE CASCADE,
                      CONSTRAINT fk_avis_intervention FOREIGN KEY (intervention_id)
                          REFERENCES intervention (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                      CONSTRAINT ck_avis_note CHECK (note BETWEEN 1 AND 5)
);
COMMENT ON CONSTRAINT uq_avis_intervention ON avis IS 'Un seul avis par intervention : garantit l authenticite des avis publies.';