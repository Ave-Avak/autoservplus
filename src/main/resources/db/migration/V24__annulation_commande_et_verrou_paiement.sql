-- =====================================================================================
-- Annulation de commande (RM-21) et verrouillage du paiement, pour le module paiement.
--
-- 1) commande : les colonnes motif_annulation et date_annulation du dictionnaire,
--    laissées de côté par le bloc conversion faute de consommateur — le voici. Le
--    CHECK porte d'emblée les SIX motifs du dictionnaire pour aligner la base sur
--    le livrable ; ce bloc n'en produit qu'un (TIMEOUT_PAIEMENT), les autres sont
--    autorisés mais attendront leurs blocs (abandon, échec définitif, annulation
--    membre, rétractation F30, exception admin).
--
-- 2) commande.rupture_a_honorer : règle (a) du décrément au paiement — si le stock
--    est devenu insuffisant entre conversion et paiement, la commande passe QUAND
--    MÊME PAYEE (on n'annule pas un paiement encaissé), le stock plancher à 0, et ce
--    drapeau lève l'alerte « rupture à honorer » pour le garage. Le détail des
--    lignes en rupture part en journal applicatif WARN (pas de table dédiée).
--
-- 3) paiement.version : verrou optimiste — un webhook et le job d'expiration
--    peuvent viser le même paiement (même patron que rdv et intervention).
--
-- NOTE paiement.statut : AUCUNE modification. Vérification faite : le seul CHECK
-- existant est ck_paiement_statut, posé par V4, qui couvre déjà INITIE, EN_COURS,
-- REUSSI, ECHOUE, EXPIRE (et REMBOURSE pour le bloc rétractation futur). Aucune
-- contrainte V10 n'existe sur cette colonne — il n'y a donc rien à supprimer, et
-- une seule contrainte CHECK reste en place sur paiement.statut.
-- =====================================================================================

ALTER TABLE commande
    ADD COLUMN motif_annulation  VARCHAR(30),
    ADD COLUMN date_annulation   TIMESTAMPTZ,
    ADD COLUMN rupture_a_honorer BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE commande
    ADD CONSTRAINT ck_commande_motif_annulation
    CHECK (motif_annulation IS NULL OR motif_annulation IN
        ('TIMEOUT_PAIEMENT', 'ABANDON_PAIEMENT', 'ECHEC_DEFINITIF',
         'ANNULATION_MEMBRE', 'RETRACTATION_F30', 'EXCEPTION_ADMIN'));

-- Une ANNULEE porte toujours son motif et sa date ; les autres statuts n'en ont pas
-- l'obligation (REMBOURSEE, bloc futur, pourra conserver ceux d'une annulation).
ALTER TABLE commande
    ADD CONSTRAINT ck_commande_annulation
    CHECK (statut <> 'ANNULEE' OR (motif_annulation IS NOT NULL AND date_annulation IS NOT NULL));

COMMENT ON COLUMN commande.rupture_a_honorer IS
    'Regle (a) du paiement : stock devenu insuffisant au moment du paiement confirme. '
    'La commande est payee, le garage doit honorer la rupture hors ligne. '
    'Le detail des lignes concernees est journalise a la detection.';

ALTER TABLE paiement ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN paiement.version IS
    'Verrouillage optimiste : un webhook et le job d expiration peuvent viser le meme paiement.';
