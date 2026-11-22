package be.autoservplus.vente.web.dto;

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
 * Detail d une commande passee (F32, CdC P384) : date, lignes commandees avec
 * quantites et prix, les trois totaux, le paiement, le statut courant.
 *
 * <p><b>Les prix sont ceux figes a la commande</b> (RM-30), relus des lignes et
 * jamais du catalogue courant. Un tarif revise depuis l achat ne doit pas reecrire
 * ce que le membre a paye : l ecran doit continuer de concorder avec la facture
 * archivee, qui, elle, est immuable.</p>
 *
 * <p>Les totaux viennent de la <b>commande</b> et non d une somme recalculee sur les
 * lignes. Ce sont eux qui ont ete factures et encaisses. Les recalculer ferait de
 * cet ecran une seconde source de verite, susceptible de diverger de la facture au
 * moindre arrondi.</p>
 *
 * <p>Les champs de facture sont nuls a la sortie du module vente, qui ignore la
 * facturation : le controleur les complete via {@link #avecFacture}, exactement
 * comme pour {@link CommandeHistoriqueVue}. L etat de retractation, lui, n entre pas
 * du tout dans cette vue : il voyage a part, pour la meme raison.</p>
 */
public record CommandeDetailVue(
        UUID reference,
        String numero,
        String date,
        StatutCommande statut,
        List<LigneVue> lignes,
        String totalHtva,
        String totalTva,
        String totalTvac,
        String methodePaiement,
        String datePaiement,
        StatutPaiement statutPaiement,
        SituationPaiement situation,
        UUID referenceFacture,
        String numeroFacture,
        /** Paiement reçu après l'annulation de la commande (V64), ou {@code null}. */
        java.time.LocalDateTime recuApresAnnulationLe,
        /** Remboursement de ce paiement, ou {@code null} tant qu'il est à venir. */
        java.time.LocalDateTime rembourseApresAnnulationLe) {

    /**
     * Une ligne commandee. {@code quantite} est un {@code int} et non le {@code short}
     * de l entite : le gabarit n a que faire de la contrainte de stockage.
     */
    public record LigneVue(
            String libelle,
            int quantite,
            String prixUnitaireHtva,
            String tauxTva,
            String totalHtva,
            String totalTvac) {

        static LigneVue de(LignePanier ligne) {
            return new LigneVue(
                    ligne.getLibelleFige(),
                    ligne.getQuantite(),
                    FormatageRdv.eurosEcran(ligne.getPrixUnitaireHtva()),
                    pourcentage(ligne.getTauxTva()),
                    FormatageRdv.eurosEcran(ligne.totalHtva()),
                    FormatageRdv.eurosEcran(ligne.totalTvac()));
        }

        /** 21.00 s affiche « 21 % » : les decimales nulles d un taux belge sont du bruit. */
        private static String pourcentage(BigDecimal taux) {
            return taux.stripTrailingZeros().toPlainString() + " %";
        }
    }

    /**
     * @param paiement paiement abouti de la commande, ou {@code null} si elle n a
     *                 jamais ete payee : l ecran affiche alors son statut, pas un
     *                 moyen de paiement fabrique
     * @param situation situation du paiement, qui decide de la demarche de paiement
     *                  proposee : payer, reessayer apres un echec, ou rien tant
     *                  qu une tentative est en cours de traitement
     */
    public static CommandeDetailVue de(Commande commande, List<LignePanier> lignes,
                                       Paiement paiement, SituationPaiement situation,
                                       ZoneId zone) {
        return new CommandeDetailVue(
                commande.getReference(),
                commande.getNumero(),
                FormatageRdv.jourLisible(commande.getDateCommande(), zone),
                commande.getStatut(),
                lignes.stream().map(LigneVue::de).toList(),
                FormatageRdv.eurosEcran(commande.getMontantHtva()),
                FormatageRdv.eurosEcran(commande.getMontantTva()),
                FormatageRdv.eurosEcran(commande.getMontantTvac()),
                paiement == null ? null : paiement.getMethode(),
                paiement == null || paiement.getDateFinalisation() == null
                        ? null
                        : FormatageRdv.jourLisible(paiement.getDateFinalisation(), zone),
                paiement == null ? null : paiement.getStatut(),
                situation, null, null,
                paiement == null ? null : local(paiement.getRecuApresAnnulationLe(), zone),
                paiement == null ? null : local(paiement.getRembourseLe(), zone));
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

    /** Le meme detail, augmente du lien vers sa facture. */
    public CommandeDetailVue avecFacture(UUID referenceFacture, String numeroFacture) {
        return new CommandeDetailVue(reference, numero, date, statut, lignes,
                totalHtva, totalTva, totalTvac, methodePaiement, datePaiement,
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

    /** Le lien de telechargement n apparait que si la facture existe : condition de la liste. */
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
     * Le moyen employe est connu. Faux en V1 tant que le prestataire reel n est pas
     * cable : l ecran l annonce plutot que de laisser une case vide inexpliquee.
     */
    public boolean methodePaiementConnue() {
        return methodePaiement != null && !methodePaiement.isBlank();
    }
}
