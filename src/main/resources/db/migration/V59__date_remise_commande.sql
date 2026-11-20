-- =====================================================================================
-- Date de remise des pièces au membre (CDE, art. VI.47, § 2, 2°).
--
-- Le délai de rétractation d'un bien court de sa prise de possession, et non de la
-- conclusion du contrat. Faute de date, une demande sur une commande de pièces était
-- toujours enregistrée et le garage la refusait au besoin, à la main. Le garage saisit
-- désormais la date au back-office. Le délai en découle, prolongé au jour ouvrable
-- suivant quand son dernier jour tombe un samedi, un dimanche ou un jour férié
-- (règlement 1182/71, considérant 41 de la directive 2011/83/UE). Sans date saisie, la
-- règle reste celle d'avant.
--
-- Colonne DATE : la remise est un jour, celui où le membre repart avec la pièce. Les
-- contrôles qui dépendent du jour courant (pas de date future, pas de date antérieure au
-- paiement) vivent dans l'entité. La base garde le seul invariant qui ne dépend pas de
-- l'heure : pas de remise sans paiement.
-- =====================================================================================

ALTER TABLE commande ADD COLUMN date_remise DATE;

ALTER TABLE commande ADD CONSTRAINT ck_commande_remise_apres_paiement
    CHECK (date_remise IS NULL OR date_paiement IS NOT NULL);

COMMENT ON COLUMN commande.date_remise IS
    'Jour de remise des pièces au membre, saisi par le garage. Point de départ du délai '
    'de rétractation d''un bien (CDE, art. VI.47, § 2, 2°). NULL tant qu''il n''est pas saisi.';
