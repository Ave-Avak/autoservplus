-- =====================================================================================
-- Zone 5 : operations atelier
-- L intervention est le travail reellement effectue sur un vehicule. Elle peut naitre
-- d un rendez-vous en ligne ou d une entree directe au garage, d ou la nullabilite de
-- rdv_id.
-- Reference : livrable 08, livrable 09, F17, RM-15
-- =====================================================================================

CREATE TABLE intervention (
                              id                  BIGSERIAL     PRIMARY KEY,
                              reference           UUID          NOT NULL DEFAULT gen_random_uuid(),
                              numero              VARCHAR(20)   NOT NULL,
                              rdv_id              BIGINT,
                              vehicule_id         BIGINT        NOT NULL,
                              statut              VARCHAR(25)   NOT NULL DEFAULT 'PLANIFIEE',
                              diagnostic          TEXT,
                              montant_devis_htva  NUMERIC(10,2),
                              montant_reel_htva   NUMERIC(10,2),
                              depassement_notifie BOOLEAN       NOT NULL DEFAULT FALSE,
                              accord_client       BOOLEAN,
                              date_accord_client  TIMESTAMPTZ,
                              debut_reel          TIMESTAMPTZ,
                              fin_reelle          TIMESTAMPTZ,
                              kilometrage_releve  INTEGER,
                              version             BIGINT        NOT NULL DEFAULT 0,
                              created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
                              updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
                              created_by          VARCHAR(120),
                              updated_by          VARCHAR(120),
                              deleted_at          TIMESTAMPTZ,
                              deleted_by          VARCHAR(120),
                              CONSTRAINT uq_intervention_reference UNIQUE (reference),
                              CONSTRAINT uq_intervention_numero    UNIQUE (numero),
                              CONSTRAINT fk_intervention_rdv FOREIGN KEY (rdv_id)
                                  REFERENCES rdv (id) ON DELETE SET NULL ON UPDATE CASCADE,
                              CONSTRAINT fk_intervention_vehicule FOREIGN KEY (vehicule_id)
                                  REFERENCES vehicule (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                              CONSTRAINT ck_intervention_statut CHECK (statut IN ('PLANIFIEE', 'EN_COURS', 'EN_ATTENTE_ACCORD', 'TERMINEE', 'ANNULEE')),
                              CONSTRAINT ck_intervention_bornes CHECK (fin_reelle IS NULL OR debut_reel IS NULL OR fin_reelle >= debut_reel),
                              CONSTRAINT ck_intervention_montants CHECK (
                                  (montant_devis_htva IS NULL OR montant_devis_htva >= 0)
                                      AND (montant_reel_htva  IS NULL OR montant_reel_htva  >= 0)
                                  ),
                              CONSTRAINT ck_intervention_kilometrage CHECK (kilometrage_releve IS NULL OR kilometrage_releve >= 0)
);
COMMENT ON TABLE intervention IS 'Travail effectue sur un vehicule. Peut naitre d un rendez-vous ou d une entree directe.';
COMMENT ON COLUMN intervention.depassement_notifie IS 'Regle RM-15 : un depassement de plus de dix pour cent du devis exige un accord expres du client avant poursuite.';
COMMENT ON COLUMN intervention.version IS 'Verrouillage optimiste : deux mecaniciens ne peuvent pas modifier la meme intervention simultanement.';
CREATE INDEX ix_intervention_vehicule ON intervention (vehicule_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_intervention_statut   ON intervention (statut)      WHERE deleted_at IS NULL;
CREATE INDEX ix_intervention_rdv      ON intervention (rdv_id)      WHERE rdv_id IS NOT NULL;

CREATE TABLE ligne_intervention (
                                    id                 BIGSERIAL     PRIMARY KEY,
                                    intervention_id    BIGINT        NOT NULL,
                                    service_id         BIGINT,
                                    piece_id           BIGINT,
                                    libelle_fige       VARCHAR(150)  NOT NULL,
                                    quantite           SMALLINT      NOT NULL,
                                    prix_unitaire_htva NUMERIC(10,2) NOT NULL,
                                    taux_tva           NUMERIC(5,2)  NOT NULL,
                                    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                    created_by         VARCHAR(120),
                                    updated_by         VARCHAR(120),
                                    CONSTRAINT fk_ligne_interv_interv FOREIGN KEY (intervention_id)
                                        REFERENCES intervention (id) ON DELETE CASCADE ON UPDATE CASCADE,
                                    CONSTRAINT fk_ligne_interv_service FOREIGN KEY (service_id)
                                        REFERENCES service (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                                    CONSTRAINT fk_ligne_interv_piece FOREIGN KEY (piece_id)
                                        REFERENCES piece (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                                    CONSTRAINT ck_ligne_interv_article CHECK (
                                        (service_id IS NOT NULL AND piece_id IS NULL)
                                            OR (service_id IS NULL     AND piece_id IS NOT NULL)
                                        ),
                                    CONSTRAINT ck_ligne_interv_quantite CHECK (quantite > 0),
                                    CONSTRAINT ck_ligne_interv_prix     CHECK (prix_unitaire_htva >= 0)
);
CREATE INDEX ix_ligne_intervention ON ligne_intervention (intervention_id);