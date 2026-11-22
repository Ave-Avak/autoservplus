-- Paiement reçu sur une commande déjà annulée, et son remboursement.
--
-- Le job RM-21 lit le statut chez le prestataire avant d'annuler une commande, mais une
-- course reste possible entre cette lecture et la notification d'un paiement, et des
-- tentatives closes avant ce changement peuvent encore aboutir. L'encaissement est alors
-- réel alors qu'aucune commande ne l'attend : il est enregistré, signalé au garage, et
-- remboursé par un administrateur depuis le back-office.
--
-- Aucune facture n'a été émise pour une commande annulée : le remboursement n'a donc ni
-- avoir ni autre pièce. Ces trois colonnes sont sa seule trace, avec la référence du
-- remboursement chez le prestataire (reference_remboursement, V27).

ALTER TABLE paiement
    ADD COLUMN recu_apres_annulation_le TIMESTAMPTZ,
    ADD COLUMN rembourse_le             TIMESTAMPTZ,
    ADD COLUMN rembourse_par            VARCHAR(120);

COMMENT ON COLUMN paiement.recu_apres_annulation_le IS
    'Instant où l''encaissement a été constaté sur une commande déjà annulée. NULL pour un paiement ordinaire.';
COMMENT ON COLUMN paiement.rembourse_le IS
    'Instant du remboursement d''un paiement reçu après annulation. Le remboursement d''une rétractation est daté par sa demande et par son avoir.';
COMMENT ON COLUMN paiement.rembourse_par IS
    'Administrateur qui a déclenché le remboursement d''un paiement reçu après annulation.';

-- Un remboursement hors rétractation a une date et un auteur, ou n'a ni l'un ni l'autre,
-- et ne concerne qu'un paiement reçu après annulation.
ALTER TABLE paiement
    ADD CONSTRAINT ck_paiement_remboursement_apres_annulation CHECK (
        (rembourse_le IS NULL AND rembourse_par IS NULL)
        OR (rembourse_le IS NOT NULL AND rembourse_par IS NOT NULL
            AND recu_apres_annulation_le IS NOT NULL AND statut = 'REMBOURSE'));
