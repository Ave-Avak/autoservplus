package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.SituationPaiement;
import be.autoservplus.vente.domain.StatutCommande;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Une ligne de l'historique des commandes du membre (F32). Montant et date
 * pré-formatés, convention du module. Le détail complet d'une commande est porté
 * par {@link CommandeDetailVue} : cette vue-ci ne montre que ce qu'une liste doit
 * montrer, elle ne charge pas les lignes commandées.
 *
 * <p>Les champs de facture sont nuls à la sortie du module vente, qui ne connaît pas
 * la facturation : c'est le contrôleur qui les complète depuis le module
 * {@code facturation}, via {@link #avecFacture}. Faire dépendre la vente de la
 * facturation pour un simple lien de téléchargement inverserait la dépendance.
 * C'est la facture qui naît de la commande, pas l'inverse.</p>
 *
 * <p>{@code statut} est exposé brut : le libellé affiché est une clé i18n construite
 * dans le gabarit, aucune chaîne visible n'est fabriquée ici.</p>
 */
public record CommandeHistoriqueVue(
        UUID reference,
        String numero,
        LocalDateTime date,
        String totalTvac,
        StatutCommande statut,
        SituationPaiement situation,
        UUID referenceFacture,
        String numeroFacture,
        /** Un paiement est arrivé après l'annulation et attend son remboursement. */
        boolean remboursementEnCours,
        /** Ce paiement est remboursé. */
        boolean rembourseApresAnnulation) {

    /** Ligne sans paiement reçu après annulation. */
    public CommandeHistoriqueVue(UUID reference, String numero, LocalDateTime date,
                                 String totalTvac,
                                 StatutCommande statut, SituationPaiement situation,
                                 UUID referenceFacture, String numeroFacture) {
        this(reference, numero, date, totalTvac, statut, situation, referenceFacture, numeroFacture,
                false, false);
    }

    /** @param tardif paiement reçu après l'annulation de la commande, ou {@code null} */
    public static CommandeHistoriqueVue de(Commande commande, SituationPaiement situation,
                                           Paiement tardif, ZoneId zone) {
        return new CommandeHistoriqueVue(
                commande.getReference(),
                commande.getNumero(),
                LocalDateTime.ofInstant(commande.getDateCommande(), zone),
                FormatageRdv.eurosEcran(commande.getMontantTvac()),
                commande.getStatut(),
                situation,
                null,
                null,
                tardif != null && tardif.getRembourseLe() == null,
                tardif != null && tardif.getRembourseLe() != null);
    }

    /** La même ligne, augmentée du lien vers sa facture. */
    public CommandeHistoriqueVue avecFacture(UUID referenceFacture, String numeroFacture) {
        return new CommandeHistoriqueVue(reference, numero, date, totalTvac, statut, situation,
                referenceFacture, numeroFacture, remboursementEnCours, rembourseApresAnnulation);
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

    /** Le bouton de téléchargement n'apparaît que si la facture existe. */
    public boolean estFacturee() {
        return referenceFacture != null;
    }

    /**
     * La commande attend encore son paiement et aucune tentative n'est en cours de
     * traitement : la liste peut proposer de payer, ou de réessayer après un échec.
     *
     * <p>Même règle que {@code ConfirmationCommandeVue}, et que la garde de
     * {@code PaiementService.initierPaiement}, celle-ci reste seule à décider : le
     * gabarit affiche, il n'autorise pas.</p>
     *
     * <p>Aucune notion de délai ici. L'expiration RM-21 n'est pas une durée à
     * comparer, c'est {@code ExpirationCommandesJob} qui fait passer la commande en
     * ANNULEE au bout de trente minutes. Le statut porte donc déjà le verdict.</p>
     */
    public boolean peutEtrePayee() {
        return situation.peutEtrePayee();
    }
}
