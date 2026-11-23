package be.autoservplus.vente.domain;

/**
 * Cycle de vie d'une commande, aligné sur le CHECK {@code ck_commande_statut}
 * de la table (V4).
 *
 * <p>Naissance en EN_ATTENTE_PAIEMENT (RM-19). Le bloc paiement exerce
 * EN_ATTENTE_PAIEMENT vers PAYEE (webhook confirmé) et vers ANNULEE (timeout
 * RM-21). La rétractation (F30, RM-23) exerce PAYEE vers REMBOURSEE, à la
 * validation de la demande d'annulation par l'administrateur.</p>
 */
public enum StatutCommande {
    EN_ATTENTE_PAIEMENT,
    PAYEE,
    ANNULEE,
    REMBOURSEE;

    /**
     * Transitions autorisées. PAYEE ne redevient jamais ANNULEE (un paiement
     * encaissé ne s'efface pas) et ANNULEE ne devient jamais PAYEE (la garde
     * tranche la course entre le job d'expiration et un webhook tardif).
     */
    public boolean peutPasserA(StatutCommande cible) {
        return switch (this) {
            case EN_ATTENTE_PAIEMENT -> cible == PAYEE || cible == ANNULEE;
            case PAYEE               -> cible == REMBOURSEE;
            case ANNULEE, REMBOURSEE -> false;
        };
    }
}
