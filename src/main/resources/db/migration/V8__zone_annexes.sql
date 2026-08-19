-- =====================================================================================
-- Zone 8 : services annexes
-- Location de places de parking, service complementaire propose aux membres pendant
-- l immobilisation de leur vehicule ou pour du stationnement de longue duree.
-- Reference : livrable 08, livrable 09, A7 a A10
-- =====================================================================================

CREATE TABLE place_parking (
                               id              BIGSERIAL     PRIMARY KEY,
                               numero          VARCHAR(10)   NOT NULL,
                               type            VARCHAR(20)   NOT NULL DEFAULT 'STANDARD',
                               tarif_jour_htva NUMERIC(10,2) NOT NULL,
                               actif           BOOLEAN       NOT NULL DEFAULT TRUE,
                               created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
                               updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
                               created_by      VARCHAR(120),
                               updated_by      VARCHAR(120),
                               deleted_at      TIMESTAMPTZ,
                               deleted_by      VARCHAR(120),
                               CONSTRAINT uq_place_parking_numero UNIQUE (numero),
                               CONSTRAINT ck_place_parking_type   CHECK (type IN ('STANDARD', 'COUVERTE', 'PMR', 'GRANDE')),
                               CONSTRAINT ck_place_parking_tarif  CHECK (tarif_jour_htva >= 0)
);
COMMENT ON COLUMN place_parking.type IS 'PMR : place reservee aux personnes a mobilite reduite.';

CREATE TABLE reservation_parking (
                                     id           BIGSERIAL     PRIMARY KEY,
                                     reference    UUID          NOT NULL DEFAULT gen_random_uuid(),
                                     numero       VARCHAR(20)   NOT NULL,
                                     membre_id    BIGINT        NOT NULL,
                                     vehicule_id  BIGINT        NOT NULL,
                                     place_id     BIGINT        NOT NULL,
                                     paiement_id  BIGINT,
                                     date_debut   DATE          NOT NULL,
                                     date_fin     DATE          NOT NULL,
                                     montant_htva NUMERIC(10,2) NOT NULL,
                                     montant_tvac NUMERIC(10,2) NOT NULL,
                                     statut       VARCHAR(25)   NOT NULL DEFAULT 'EN_ATTENTE_PAIEMENT',
                                     created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                     updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                     created_by   VARCHAR(120),
                                     updated_by   VARCHAR(120),
                                     deleted_at   TIMESTAMPTZ,
                                     deleted_by   VARCHAR(120),
                                     CONSTRAINT uq_resa_parking_reference UNIQUE (reference),
                                     CONSTRAINT uq_resa_parking_numero    UNIQUE (numero),
                                     CONSTRAINT fk_resa_parking_membre FOREIGN KEY (membre_id)
                                         REFERENCES utilisateur (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                                     CONSTRAINT fk_resa_parking_vehicule FOREIGN KEY (vehicule_id)
                                         REFERENCES vehicule (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                                     CONSTRAINT fk_resa_parking_place FOREIGN KEY (place_id)
                                         REFERENCES place_parking (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                                     CONSTRAINT fk_resa_parking_paiement FOREIGN KEY (paiement_id)
                                         REFERENCES paiement (id) ON DELETE SET NULL ON UPDATE CASCADE,
                                     CONSTRAINT ck_resa_parking_statut  CHECK (statut IN ('EN_ATTENTE_PAIEMENT', 'CONFIRMEE', 'EN_COURS', 'TERMINEE', 'ANNULEE')),
                                     CONSTRAINT ck_resa_parking_dates   CHECK (date_fin >= date_debut),
                                     CONSTRAINT ck_resa_parking_montant CHECK (montant_htva >= 0 AND montant_tvac >= 0)
);
COMMENT ON COLUMN reservation_parking.paiement_id IS 'Nullable : la place est reservee avant que le paiement n aboutisse.';
CREATE INDEX ix_resa_parking_place  ON reservation_parking (place_id, date_debut, date_fin) WHERE deleted_at IS NULL;
CREATE INDEX ix_resa_parking_membre ON reservation_parking (membre_id) WHERE deleted_at IS NULL;