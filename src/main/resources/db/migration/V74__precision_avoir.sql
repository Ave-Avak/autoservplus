-- Note de crédit d'une facture d'atelier (CdC A15, RM-17, Q-H-14, Q-H-50).
--
-- Le motif reste un code stable, traduit à l'impression (ANNULATION_ATELIER), comme
-- RETRACTATION_F30. La précision porte le texte libre de l'administrateur, imprimé tel
-- que saisi, dans sa langue : un texte libre rangé dans motif s'imprimerait en français
-- sur une note de crédit néerlandaise, et une liste fermée de motifs ne laisserait rien
-- expliquer.
ALTER TABLE avoir ADD COLUMN precision VARCHAR(500);

-- Obligatoire et non blanche pour une annulation d'atelier, absente sinon : une
-- rétractation n'a pas de texte libre, ses mots restent sur la demande du membre.
ALTER TABLE avoir ADD CONSTRAINT ck_avoir_precision CHECK (
    (motif = 'ANNULATION_ATELIER' AND precision IS NOT NULL AND precision ~ '[^[:space:]]')
 OR (motif <> 'ANNULATION_ATELIER' AND precision IS NULL));

COMMENT ON COLUMN avoir.precision IS
    'Texte libre de l''administrateur sur une annulation de facture d''atelier, imprimé tel '
    'que saisi. Pas de donnée personnelle : la note de crédit se conserve sept ans.';

-- V27 nomme les colonnes protégées une à une : la nouvelle colonne y entre, sans quoi
-- la précision d'une note de crédit émise resterait modifiable.
CREATE OR REPLACE FUNCTION fn_avoir_immuable() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.numero        IS DISTINCT FROM NEW.numero
       OR OLD.facture_id    IS DISTINCT FROM NEW.facture_id
       OR OLD.montant_htva  IS DISTINCT FROM NEW.montant_htva
       OR OLD.montant_tva   IS DISTINCT FROM NEW.montant_tva
       OR OLD.montant_tvac  IS DISTINCT FROM NEW.montant_tvac
       OR OLD.motif         IS DISTINCT FROM NEW.motif
       OR OLD.precision     IS DISTINCT FROM NEW.precision
       OR OLD.date_emission IS DISTINCT FROM NEW.date_emission THEN
        RAISE EXCEPTION 'Une note de credit emise est immuable.';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
