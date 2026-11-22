package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.MotifAnnulationCommande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.SituationPaiement;
import be.autoservplus.vente.domain.StatutCommande;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
        boolean contientDesPieces,
        /** Commande annulée : la page ne dit plus « enregistrée » ni de montant à régler. */
        boolean annulee,
        /** Annulée parce que son délai de paiement est passé (RM-21). */
        boolean annuleePourDelai,
        /** Paiement reçu après l'annulation (V64), ou {@code null}. */
        LocalDateTime recuApresAnnulationLe,
        /** Remboursement de ce paiement, ou {@code null} tant qu'il est à venir. */
        LocalDateTime rembourseApresAnnulationLe) {

    /** Commande qui n'est pas annulée. */
    public ConfirmationCommandeVue(UUID reference, String numero, String totalTvac,
                                   SituationPaiement situation, boolean contientDesPieces) {
        this(reference, numero, totalTvac, situation, contientDesPieces, false, false, null, null);
    }

    public static ConfirmationCommandeVue de(Commande commande, SituationPaiement situation,
                                             boolean contientDesPieces) {
        return new ConfirmationCommandeVue(
                commande.getReference(),
                commande.getNumero(),
                FormatageRdv.eurosEcran(commande.getMontantTvac()),
                situation,
                contientDesPieces);
    }

    /**
     * @param tardif paiement reçu après l'annulation de la commande, ou {@code null}.
     *               Cette page est le point de chute du retour de paiement : le membre
     *               qui a payé une commande annulée entre-temps y arrive, et doit y lire
     *               ce que dit le détail de sa commande.
     */
    public static ConfirmationCommandeVue de(Commande commande, SituationPaiement situation,
                                             boolean contientDesPieces, Paiement tardif, ZoneId zone) {
        return new ConfirmationCommandeVue(
                commande.getReference(),
                commande.getNumero(),
                FormatageRdv.eurosEcran(commande.getMontantTvac()),
                situation,
                contientDesPieces,
                commande.getStatut() == StatutCommande.ANNULEE,
                commande.getMotifAnnulation() == MotifAnnulationCommande.TIMEOUT_PAIEMENT,
                tardif == null ? null : LocalDateTime.ofInstant(tardif.getRecuApresAnnulationLe(), zone),
                tardif == null || tardif.getRembourseLe() == null
                        ? null : LocalDateTime.ofInstant(tardif.getRembourseLe(), zone));
    }

    public boolean paiementRecuApresAnnulation() {
        return recuApresAnnulationLe != null;
    }

    public boolean paiementApresAnnulationRembourse() {
        return rembourseApresAnnulationLe != null;
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

    /** Échéance de paiement passée, plus rien à payer : un message remplace le bouton. */
    public boolean delaiDepasse() {
        return situation == SituationPaiement.DELAI_DEPASSE;
    }

    public boolean peutEtrePayee() {
        return situation.peutEtrePayee();
    }
}
