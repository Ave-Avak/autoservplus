-- =====================================================================================
-- Date de vérification de l'adresse de courriel d'un compte (A25).
--
-- Le tableau de bord compte les nouveaux clients d'une période : les comptes de membre
-- dont l'adresse a été vérifiée pendant cette période. email_verifie dit qu'elle l'a
-- été, pas quand. created_at date l'inscription, or une inscription jamais confirmée
-- n'est pas un client, et une confirmation peut suivre l'inscription de plusieurs jours.
--
-- La colonne reste nulle pour les comptes déjà vérifiés et pour ceux qu'une graine
-- insère vérifiés : aucune source ne donne leur date. Le tableau de bord retombe alors
-- sur created_at, seule date connue. Rien n'est réécrit dans les lignes existantes.
-- =====================================================================================

ALTER TABLE utilisateur ADD COLUMN email_verifie_le TIMESTAMPTZ;

COMMENT ON COLUMN utilisateur.email_verifie_le IS
    'Instant de la vérification de l''adresse par le lien reçu. Nul pour un compte vérifié avant V63 ou inséré vérifié.';
