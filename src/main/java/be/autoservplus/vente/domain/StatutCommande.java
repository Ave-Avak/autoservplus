package be.autoservplus.vente.domain;

/**
 * Cycle de vie d une commande, aligne sur le CHECK {@code ck_commande_statut}
 * de la table (V4).
 *
 * <p>Naissance en EN_ATTENTE_PAIEMENT (RM-19). Le bloc paiement exerce
 * EN_ATTENTE_PAIEMENT vers PAYEE (webhook confirme) et vers ANNULEE (timeout
 * RM-21) ; PAYEE vers REMBOURSEE est admis par la machine mais aucun code ne
 * l exerce encore — bloc retractation a venir.</p>
 */
public enum StatutCommande {
    EN_ATTENTE_PAIEMENT,
    PAYEE,
    ANNULEE,
    REMBOURSEE;

    /**
     * Transitions autorisees. PAYEE ne redevient jamais ANNULEE (un paiement
     * encaisse ne s efface pas) et ANNULEE ne devient jamais PAYEE (la garde
     * tranche la course entre le job d expiration et un webhook tardif).
     */
    public boolean peutPasserA(StatutCommande cible) {
        return switch (this) {
            case EN_ATTENTE_PAIEMENT -> cible == PAYEE || cible == ANNULEE;
            case PAYEE               -> cible == REMBOURSEE;
            case ANNULEE, REMBOURSEE -> false;
        };
    }
}
