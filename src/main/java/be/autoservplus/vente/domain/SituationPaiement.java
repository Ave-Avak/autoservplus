package be.autoservplus.vente.domain;

import java.util.Objects;

/**
 * Ce que les écrans du membre disent du paiement d'une commande : sa commande et sa
 * dernière tentative, lues ensemble.
 *
 * <p>Le statut de la commande ne suffit pas. Une commande EN_ATTENTE_PAIEMENT dont la
 * dernière tentative est en cours de traitement chez le prestataire ne doit plus
 * proposer de payer, et l'invitation à reprendre deviendrait une invitation à payer
 * deux fois. Une tentative échouée, elle, se dit comme telle avant de proposer de
 * réessayer.</p>
 */
public enum SituationPaiement {

    /** En attente, sans tentative ou avec une tentative ouverte ou expirée : payer. */
    A_PAYER,
    /** En attente, dernière tentative en cours de traitement : rien à faire. */
    EN_COURS,
    /** En attente, dernière tentative échouée : réessayer. */
    ECHOUE,
    PAYEE,
    /** Annulée ou remboursée : aucun paiement attendu. */
    CLOSE;

    public static SituationPaiement de(StatutCommande commande, StatutPaiement derniereTentative) {
        Objects.requireNonNull(commande, "commande");
        return switch (commande) {
            case PAYEE -> PAYEE;
            case ANNULEE, REMBOURSEE -> CLOSE;
            case EN_ATTENTE_PAIEMENT -> {
                if (derniereTentative == StatutPaiement.EN_COURS) {
                    yield EN_COURS;
                }
                yield derniereTentative == StatutPaiement.ECHOUE ? ECHOUE : A_PAYER;
            }
        };
    }

    /** Le membre peut lancer un paiement, premier ou nouvel essai. */
    public boolean peutEtrePayee() {
        return this == A_PAYER || this == ECHOUE;
    }
}
