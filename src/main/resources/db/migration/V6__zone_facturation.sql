-- =====================================================================================
-- Zone 6 : facturation
-- Une facture nait soit d une commande en ligne, soit d une intervention en atelier,
-- jamais des deux. Elle porte ses propres montants et devient immuable des son
-- emission : une modification ulterieure de la commande ne doit jamais reecrire un
-- document deja declare a la TVA. La seule correction legale est la note de credit.
-- Reference : livrable 08, livrable 18, F31
-- =====================================================================================

CREATE TABLE facture (
                         id                BIGSERIAL     PRIMARY KEY,
                         reference         UUID          NOT NULL DEFAULT gen_random_uuid(),
                         numero            VARCHAR(20)   NOT NULL,
                         exercice          SMALLINT      NOT NULL,
                         sequence_annuelle INTEGER       NOT NULL,
                         commande_id       BIGINT,
                         intervention_id   BIGINT,
                         membre_id         BIGINT        NOT NULL,
                         montant_htva      NUMERIC(10,2) NOT NULL,
                         montant_tva       NUMERIC(10,2) NOT NULL,
                         montant_tvac      NUMERIC(10,2) NOT NULL,
                         taux_tva_applique NUMERIC(5,2)  NOT NULL,
                         date_emission     TIMESTAMPTZ   NOT NULL DEFAULT now(),
                         date_echeance     DATE,
                         chemin_pdf        VARCHAR(255),
                         created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
                         updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
                         created_by        VARCHAR(120),
                         updated_by        VARCHAR(120),
                         CONSTRAINT uq_facture_reference UNIQUE (reference),
                         CONSTRAINT uq_facture_numero    UNIQUE (numero),
                         CONSTRAINT uq_facture_sequence  UNIQUE (exercice, sequence_annuelle),
                         CONSTRAINT fk_facture_commande FOREIGN KEY (commande_id)
                             REFERENCES commande (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                         CONSTRAINT fk_facture_intervention FOREIGN KEY (intervention_id)
                             REFERENCES intervention (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                         CONSTRAINT fk_facture_membre FOREIGN KEY (membre_id)
                             REFERENCES utilisateur (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                         CONSTRAINT ck_facture_origine_unique CHECK (
                             (commande_id IS NOT NULL AND intervention_id IS NULL)
                                 OR (commande_id IS NULL     AND intervention_id IS NOT NULL)
                             ),
                         CONSTRAINT ck_facture_montants  CHECK (montant_htva >= 0 AND montant_tva >= 0 AND montant_tvac >= 0),
                         CONSTRAINT ck_facture_coherence CHECK (montant_tvac = montant_htva + montant_tva),
                         CONSTRAINT ck_facture_sequence  CHECK (sequence_annuelle > 0)
);
COMMENT ON TABLE facture IS 'Document comptable immuable. Numerotation continue par exercice, sans trou.';
COMMENT ON CONSTRAINT uq_facture_sequence ON facture IS 'Garantit une numerotation continue par exercice comptable.';
CREATE INDEX ix_facture_membre   ON facture (membre_id);
CREATE INDEX ix_facture_emission ON facture (date_emission);

-- Interdiction de modifier une facture emise, au niveau de la base et non du seul code.
CREATE OR REPLACE FUNCTION fn_facture_immuable() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.numero            IS DISTINCT FROM NEW.numero
       OR OLD.montant_htva      IS DISTINCT FROM NEW.montant_htva
       OR OLD.montant_tva       IS DISTINCT FROM NEW.montant_tva
       OR OLD.montant_tvac      IS DISTINCT FROM NEW.montant_tvac
       OR OLD.taux_tva_applique IS DISTINCT FROM NEW.taux_tva_applique
       OR OLD.date_emission     IS DISTINCT FROM NEW.date_emission THEN
        RAISE EXCEPTION 'Une facture emise est immuable. Emettez une note de credit.';
END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_facture_immuable
    BEFORE UPDATE ON facture
    FOR EACH ROW EXECUTE FUNCTION fn_facture_immuable();

CREATE TABLE avoir (
                       id            BIGSERIAL     PRIMARY KEY,
                       reference     UUID          NOT NULL DEFAULT gen_random_uuid(),
                       numero        VARCHAR(20)   NOT NULL,
                       facture_id    BIGINT        NOT NULL,
                       montant_htva  NUMERIC(10,2) NOT NULL,
                       montant_tva   NUMERIC(10,2) NOT NULL,
                       montant_tvac  NUMERIC(10,2) NOT NULL,
                       motif         TEXT          NOT NULL,
                       date_emission TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       chemin_pdf    VARCHAR(255),
                       created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       created_by    VARCHAR(120),
                       updated_by    VARCHAR(120),
                       CONSTRAINT uq_avoir_reference UNIQUE (reference),
                       CONSTRAINT uq_avoir_numero    UNIQUE (numero),
                       CONSTRAINT fk_avoir_facture FOREIGN KEY (facture_id)
                           REFERENCES facture (id) ON DELETE RESTRICT ON UPDATE CASCADE,
                       CONSTRAINT ck_avoir_montants  CHECK (montant_htva >= 0 AND montant_tva >= 0 AND montant_tvac >= 0),
                       CONSTRAINT ck_avoir_coherence CHECK (montant_tvac = montant_htva + montant_tva)
);
COMMENT ON TABLE avoir IS 'Note de credit : seul moyen legal de corriger une facture deja emise.';
CREATE INDEX ix_avoir_facture ON avoir (facture_id);