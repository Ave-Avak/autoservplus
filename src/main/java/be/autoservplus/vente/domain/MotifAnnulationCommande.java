package be.autoservplus.vente.domain;

/**
 * Motifs d'annulation d'une commande, alignés sur le dictionnaire et le CHECK
 * {@code ck_commande_motif_annulation} (V24). Ce bloc n'en produit qu'un :
 * TIMEOUT_PAIEMENT (RM-21). Les autres sont autorisés en base dès maintenant
 * et attendront leurs blocs respectifs.
 */
public enum MotifAnnulationCommande {
    TIMEOUT_PAIEMENT,
    ABANDON_PAIEMENT,
    ECHEC_DEFINITIF,
    ANNULATION_MEMBRE,
    RETRACTATION_F30,
    EXCEPTION_ADMIN
}
