package be.autoservplus.vente.web.dto;

import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.SituationPaiement;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Détail d'une commande passée (F32, CdC P384) : date, lignes commandées avec
 * quantités et prix, les trois totaux, le paiement, le statut courant.
 *
 * <p><b>Les prix sont ceux figés à la commande</b> (RM-30), relus des lignes et
 * jamais du catalogue courant. Un tarif révisé depuis l'achat ne doit pas réécrire
 * ce que le membre a payé : l'écran doit continuer de concorder avec la facture
 * archivée, qui, elle, est immuable.</p>
 *
 * <p>Les totaux viennent de la <b>commande</b> et non d'une somme recalculée sur les
 * lignes. Ce sont eux qui ont été facturés et encaissés. Les recalculer ferait de
 * cet écran une seconde source de vérité, susceptible de diverger de la facture au
 * moindre arrondi.</p>
 *
 * <p>Les champs de facture sont nuls à la sortie du module vente, qui ignore la
 * facturation : le contrôleur les complète via {@link #avecFacture}, exactement
 * comme pour {@link CommandeHistoriqueVue}. L'état de rétractation, lui, n'entre pas
 * du tout dans cette vue : il voyage à part, pour la même raison.</p>
 */
public record CommandeDetailVue(
        UUID reference,
        String numero,
        java.time.LocalDateTime date,
        StatutCommande statut,
        List<LigneVue> lignes,
        String totalHtva,
        String totalTva,
        /**
         * TVA de chaque taux, recalculée des lignes affichées. Vide quand le détail ne porte
         * aucune ligne : l'écran montre alors la TVA totale de la commande.
         */
        List<TvaParTauxVue> tvaParTaux,
        String totalTvac,
        String methodePaiement,
        java.time.LocalDateTime datePaiement,
        StatutPaiement statutPaiement,
        SituationPaiement situation,
        UUID referenceFacture,
        String numeroFacture,
        /** Paiement reçu après l'annulation de la commande (V64), ou {@code null}. */
        java.time.LocalDateTime recuApresAnnulationLe,
        /** Remboursement de ce paiement, ou {@code null} tant qu'il est à venir. */
        java.time.LocalDateTime rembourseApresAnnulationLe) {

    /**
     * Une ligne commandée. {@code quantite} est un {@code int} et non le {@code short}
     * de l'entité : le gabarit n'a que faire de la contrainte de stockage.
     */
    public record LigneVue(
            String libelle,
            int quantite,
            String prixUnitaireHtva,
            String tauxTva,
            String prixUnitaireTvac,
            String totalTvac,
            /** Clé de la mention du choix des pièces, nulle sans choix. */
            String mentionPieces) {

        static LigneVue de(LignePanier ligne) {
            return new LigneVue(
                    ligne.getLibelleFige(),
                    ligne.getQuantite(),
                    FormatageRdv.eurosEcran(ligne.getPrixUnitaireHtva()),
                    pourcentage(ligne.getTauxTva()),
                    FormatageRdv.eurosEcran(ligne.prixUnitaireTvac()),
                    FormatageRdv.eurosEcran(ligne.totalTvac()),
                    ChoixPieces.cleMentionDe(ligne.getChoixPieces()));
        }

        /** 21.00 s'affiche « 21 % » : les décimales nulles d'un taux belge sont du bruit. */
        private static String pourcentage(BigDecimal taux) {
            return taux.stripTrailingZeros().toPlainString() + " %";
        }
    }

    /**
     * @param paiement paiement abouti de la commande, ou {@code null} si elle n'a
     *                 jamais été payée : l'écran affiche alors son statut, pas un
     *                 moyen de paiement fabriqué
     * @param situation situation du paiement, qui décide de la démarche de paiement
     *                  proposée : payer, réessayer après un échec, ou rien tant
     *                  qu'une tentative est en cours de traitement
     */
    public static CommandeDetailVue de(Commande commande, List<LignePanier> lignes,
                                       Paiement paiement, SituationPaiement situation,
                                       ZoneId zone) {
        return new CommandeDetailVue(
                commande.getReference(),
                commande.getNumero(),
                local(commande.getDateCommande(), zone),
                commande.getStatut(),
                lignes.stream().map(LigneVue::de).toList(),
                FormatageRdv.eurosEcran(commande.getMontantHtva()),
                FormatageRdv.eurosEcran(commande.getMontantTva()),
                tvaParTaux(lignes),
                FormatageRdv.eurosEcran(commande.getMontantTvac()),
                paiement == null ? null : paiement.getMethode(),
                paiement == null ? null : local(paiement.getDateFinalisation(), zone),
                paiement == null ? null : paiement.getStatut(),
                situation, null, null,
                paiement == null ? null : local(paiement.getRecuApresAnnulationLe(), zone),
                paiement == null ? null : local(paiement.getRembourseLe(), zone));
    }

    /** TVA par taux des lignes données, par le même calcul que la facture. */
    static List<TvaParTauxVue> tvaParTaux(List<LignePanier> lignes) {
        return TvaParTauxVue.de(CalculTva.ventiler(lignes.stream()
                .map(ligne -> new CalculTva.MontantTaxe(ligne.getTauxTva(), ligne.totalTvac()))
                .toList()));
    }

    private static java.time.LocalDateTime local(java.time.Instant instant, ZoneId zone) {
        return instant == null ? null : java.time.LocalDateTime.ofInstant(instant, zone);
    }

    /**
     * Un paiement est arrivé après l'annulation de la commande. Elle n'est pas payée
     * pour autant : l'écran le signale à part, et dit s'il est remboursé.
     */
    public boolean paiementRecuApresAnnulation() {
        return recuApresAnnulationLe != null;
    }

    public boolean paiementApresAnnulationRembourse() {
        return rembourseApresAnnulationLe != null;
    }

    /** Le même détail, augmenté du lien vers sa facture. */
    public CommandeDetailVue avecFacture(UUID referenceFacture, String numeroFacture) {
        return new CommandeDetailVue(reference, numero, date, statut, lignes,
                totalHtva, totalTva, tvaParTaux, totalTvac, methodePaiement, datePaiement,
                statutPaiement, situation, referenceFacture, numeroFacture,
                recuApresAnnulationLe, rembourseApresAnnulationLe);
    }

    /**
     * Au moins une pièce : la notice sur la garantie légale ne vise que les biens. Les
     * lignes de ce détail sont celles de {@code CommandeRepository.lignesDe}, qui ne rend
     * que les lignes de pièce.
     */
    public boolean contientDesPieces() {
        return !lignes.isEmpty();
    }

    /** Le lien de téléchargement n'apparaît que si la facture existe : condition de la liste. */
    public boolean estFacturee() {
        return referenceFacture != null;
    }

    /**
     * La commande attend encore son paiement et aucune tentative n'est en cours de
     * traitement : l'écran peut proposer de payer, ou de réessayer après un échec.
     * Condition de la liste, sur la même règle : voir
     * {@link CommandeHistoriqueVue#peutEtrePayee()}.
     */
    public boolean peutEtrePayee() {
        return situation.peutEtrePayee();
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

    /** Un paiement abouti existe : de quoi afficher une section « paiement ». */
    public boolean estPayee() {
        return statutPaiement != null;
    }

    /**
     * Le moyen employé est connu. Faux en V1 tant que le prestataire réel n'est pas
     * câblé : l'écran l'annonce plutôt que de laisser une case vide inexpliquée.
     */
    public boolean methodePaiementConnue() {
        return methodePaiement != null && !methodePaiement.isBlank();
    }
}
