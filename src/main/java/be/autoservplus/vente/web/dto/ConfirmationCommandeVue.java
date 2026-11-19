package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.SituationPaiement;

import java.util.UUID;

/**
 * Vue de la page « commande enregistree » (F14). Le numero est la piece
 * d identite que le membre conserve. Le montant TVAC rappelle ce qui est du au
 * paiement. Montant pre-formate en euros, convention du module.
 *
 * <p>La situation du paiement existe parce que cette page est le point de chute du
 * RETOUR de paiement : y proposer « Proceder au paiement » a un membre qui vient de
 * payer, ou dont le paiement est en cours de traitement chez le prestataire, serait
 * une invitation a payer deux fois. Apres un echec, la page le dit avant de proposer
 * de reessayer.</p>
 */
public record ConfirmationCommandeVue(
        UUID reference,
        String numero,
        String totalTvac,
        SituationPaiement situation,
        /** Au moins une pièce : la notice sur la garantie légale ne vise que les biens. */
        boolean contientDesPieces) {

    public static ConfirmationCommandeVue de(Commande commande, SituationPaiement situation,
                                             boolean contientDesPieces) {
        return new ConfirmationCommandeVue(
                commande.getReference(),
                commande.getNumero(),
                FormatageRdv.euros(commande.getMontantTvac()),
                situation,
                contientDesPieces);
    }

    /** Premier paiement : rien n'a encore été tenté, ou la tentative reste ouverte. */
    public boolean aPayer() {
        return situation == SituationPaiement.A_PAYER;
    }

    public boolean paiementEnCours() {
        return situation == SituationPaiement.EN_COURS;
    }

    public boolean paiementEchoue() {
        return situation == SituationPaiement.ECHOUE;
    }

    public boolean peutEtrePayee() {
        return situation.peutEtrePayee();
    }
}
