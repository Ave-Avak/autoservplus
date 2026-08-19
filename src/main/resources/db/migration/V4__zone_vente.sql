-- =====================================================================================
-- Zone 4 : processus de vente
-- Du panier au paiement. Le panier est volatil, la commande est definitive : ses lignes
-- sont recopiees et non partagees, afin qu une evolution du catalogue ne reecrive
-- jamais une commande passee.
-- Reference : livrable 08, livrable 09, F12 a F14
-- =====================================================================================

CREATE TABLE panier (
                        id              BIGSERIAL   PRIMARY KEY,
                        reference       UUID        NOT NULL DEFAULT gen_random_uuid(),
                        membre_id       BIGINT      NOT NULL,
                        date_expiration TIMESTAMPTZ,
                        created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                        created_by      VARCHAR(120),
                        updated_by      VARCHAR(120),
                        deleted_at      TIMESTAMPTZ,
                        deleted_by      VARCHAR(120),
                        CONSTRAINT uq_panier_reference UNIQUE (reference),
                        CONSTRAINT fk_panier_membre FOREIGN KEY (membre_id)
                            REFERENCES utilisateur (id) ON DELETE CASCADE ON UPDATE CASCADE
);
CREATE UNIQUE INDEX uq_panier_membre_actif ON panier (membre_id) WHERE deleted_at IS NULL;
COMMENT ON INDEX uq_panier_membre_actif IS 'Un seul panier ouvert par membre a la fois.';

CREATE TABLE commande (
                          id            BIGSERIAL     PRIMARY KEY,
                          reference     UUID          NOT NULL DEFAULT gen_random_uuid(),
                          numero        VARCHAR(20)   NOT NULL,
                          membre_id     BIGINT        NOT NULL,
                          statut        VARCHAR(25)   NOT NULL DEFAULT 'EN_ATTENTE_PAIEMENT',
                          montant_htva  NUMERIC(10,2) NOT NULL,
                          montant_tva   NUMERIC(10,2) NOT NULL,
                          montant_tvac  NUMERIC(10,2) NOT NULL,
                          date_commande TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          date_paiement TIMESTAMPTZ,
                          created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          created_by    VARCHAR(120),
                          updated_by    VARCHAR(120),
                          deleted_at    TIMESTAMPTZ,
                          deleted_by    VARCHAR(120),
                          CONSTRAINT uq_commande_reference UNIQUE (reference),
                          CONSTRAINT uq_commande_numero    UNIQUE (numero),
                          CONSTRAINT fk_commande_membre FOREIGN KEY (membre_id)
                              REFERENCES utilisateur (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                          CONSTRAINT ck_commande_statut   CHECK (statut IN ('EN_ATTENTE_PAIEMENT', 'PAYEE', 'ANNULEE', 'REMBOURSEE')),
                          CONSTRAINT ck_commande_montants CHECK (montant_htva >= 0 AND montant_tva >= 0 AND montant_tvac >= 0),
                          CONSTRAINT ck_commande_coherence CHECK (montant_tvac = montant_htva + montant_tva)
);
CREATE INDEX ix_commande_membre ON commande (membre_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_commande_statut ON commande (statut)    WHERE deleted_at IS NULL;

CREATE TABLE ligne_panier (
                              id                 BIGSERIAL     PRIMARY KEY,
                              panier_id          BIGINT,
                              commande_id        BIGINT,
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
                              CONSTRAINT fk_ligne_panier_panier FOREIGN KEY (panier_id)
                                  REFERENCES panier (id) ON DELETE CASCADE ON UPDATE CASCADE,
                              CONSTRAINT fk_ligne_panier_commande FOREIGN KEY (commande_id)
                                  REFERENCES commande (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                              CONSTRAINT fk_ligne_panier_service FOREIGN KEY (service_id)
                                  REFERENCES service (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                              CONSTRAINT fk_ligne_panier_piece FOREIGN KEY (piece_id)
                                  REFERENCES piece (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                              CONSTRAINT ck_ligne_rattachement_unique CHECK (
                                  (panier_id IS NOT NULL AND commande_id IS NULL)
                                      OR (panier_id IS NULL     AND commande_id IS NOT NULL)
                                  ),
                              CONSTRAINT ck_ligne_article_unique CHECK (
                                  (service_id IS NOT NULL AND piece_id IS NULL)
                                      OR (service_id IS NULL     AND piece_id IS NOT NULL)
                                  ),
                              CONSTRAINT ck_ligne_quantite CHECK (quantite > 0),
                              CONSTRAINT ck_ligne_prix     CHECK (prix_unitaire_htva >= 0)
);
COMMENT ON COLUMN ligne_panier.libelle_fige IS 'Libelle recopie a la commande : la facture ne change pas si le catalogue evolue.';
COMMENT ON CONSTRAINT ck_ligne_rattachement_unique ON ligne_panier IS 'Une ligne appartient soit a un panier, soit a une commande, jamais aux deux.';
CREATE INDEX ix_ligne_panier_panier   ON ligne_panier (panier_id)   WHERE panier_id   IS NOT NULL;
CREATE INDEX ix_ligne_panier_commande ON ligne_panier (commande_id) WHERE commande_id IS NOT NULL;

CREATE TABLE paiement (
                          id                BIGSERIAL     PRIMARY KEY,
                          reference         UUID          NOT NULL DEFAULT gen_random_uuid(),
                          commande_id       BIGINT,
                          reference_mollie  VARCHAR(64),
                          cle_idempotence   VARCHAR(64)   NOT NULL,
                          montant           NUMERIC(10,2) NOT NULL,
                          devise            VARCHAR(3)    NOT NULL DEFAULT 'EUR',
                          methode           VARCHAR(30),
                          statut            VARCHAR(25)   NOT NULL DEFAULT 'INITIE',
                          date_initiation   TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          date_finalisation TIMESTAMPTZ,
                          created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          created_by        VARCHAR(120),
                          updated_by        VARCHAR(120),
                          CONSTRAINT uq_paiement_reference   UNIQUE (reference),
                          CONSTRAINT uq_paiement_mollie      UNIQUE (reference_mollie),
                          CONSTRAINT uq_paiement_idempotence UNIQUE (cle_idempotence),
                          CONSTRAINT fk_paiement_commande FOREIGN KEY (commande_id)
                              REFERENCES commande (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                          CONSTRAINT ck_paiement_statut  CHECK (statut IN ('INITIE', 'EN_COURS', 'REUSSI', 'ECHOUE', 'EXPIRE', 'REMBOURSE')),
                          CONSTRAINT ck_paiement_montant CHECK (montant > 0)
);
COMMENT ON COLUMN paiement.commande_id IS 'Nullable : un paiement peut aussi couvrir une reservation de parking.';
COMMENT ON COLUMN paiement.cle_idempotence IS 'Empeche le double debit si la requete est rejouee.';
CREATE INDEX ix_paiement_commande ON paiement (commande_id) WHERE commande_id IS NOT NULL;
CREATE INDEX ix_paiement_statut   ON paiement (statut);