package be.autoservplus.vente.domain;

/**
 * Cycle de vie d une commande, aligne sur le CHECK {@code ck_commande_statut}
 * de la table (V4).
 *
 * <p>En V1-bloc-conversion, seule la naissance en EN_ATTENTE_PAIEMENT est
 * implementee (RM-19) : les transitions vers PAYEE, ANNULEE et REMBOURSEE
 * viendront avec le bloc paiement et porteront leur machine a etats dans
 * l entite, comme {@code StatutIntervention}. Les valeurs existent des
 * maintenant pour que l enum colle au CHECK et ne bloque rien.</p>
 */
public enum StatutCommande {
    EN_ATTENTE_PAIEMENT,
    PAYEE,
    ANNULEE,
    REMBOURSEE
}
