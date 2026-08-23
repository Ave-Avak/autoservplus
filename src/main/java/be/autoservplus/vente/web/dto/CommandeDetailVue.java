package be.autoservplus.vente.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Paiement;
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
 * lignes. Ce sont eux qui ont ete factures et encaisses ; les recalculer ferait de
 * cet ecran une seconde source de verite, susceptible de diverger de la facture au
 * moindre arrondi.</p>
 *
 * <p>Les champs de facture sont nuls a la sortie du module vente, qui ignore la
 * facturation : le controleur les complete via {@link #avecFacture}, exactement
 * comme pour {@link CommandeHistoriqueVue}. L etat de retractation, lui, n entre pas
 * du tout dans cette vue — il voyage a part, pour la meme raison.</p>
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
        UUID referenceFacture,
        String numeroFacture) {

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
                    FormatageRdv.euros(ligne.getPrixUnitaireHtva()),
                    pourcentage(ligne.getTauxTva()),
                    FormatageRdv.euros(ligne.totalHtva()),
                    FormatageRdv.euros(ligne.totalTvac()));
        }

        /** 21.00 s affiche « 21 % » : les decimales nulles d un taux belge sont du bruit. */
        private static String pourcentage(BigDecimal taux) {
            return taux.stripTrailingZeros().toPlainString() + " %";
        }
    }

    /**
     * @param paiement paiement abouti de la commande, ou {@code null} si elle n a
     *                 jamais ete payee — l ecran affiche alors son statut, pas un
     *                 moyen de paiement fabrique
     */
    public static CommandeDetailVue de(Commande commande, List<LignePanier> lignes,
                                       Paiement paiement, ZoneId zone) {
        return new CommandeDetailVue(
                commande.getReference(),
                commande.getNumero(),
                FormatageRdv.jourLisible(commande.getDateCommande(), zone),
                commande.getStatut(),
                lignes.stream().map(LigneVue::de).toList(),
                FormatageRdv.euros(commande.getMontantHtva()),
                FormatageRdv.euros(commande.getMontantTva()),
                FormatageRdv.euros(commande.getMontantTvac()),
                paiement == null ? null : paiement.getMethode(),
                paiement == null || paiement.getDateFinalisation() == null
                        ? null
                        : FormatageRdv.jourLisible(paiement.getDateFinalisation(), zone),
                paiement == null ? null : paiement.getStatut());
    }

    private CommandeDetailVue(UUID reference, String numero, String date, StatutCommande statut,
                              List<LigneVue> lignes, String totalHtva, String totalTva,
                              String totalTvac, String methodePaiement, String datePaiement,
                              StatutPaiement statutPaiement) {
        this(reference, numero, date, statut, lignes, totalHtva, totalTva, totalTvac,
                methodePaiement, datePaiement, statutPaiement, null, null);
    }

    /** Le meme detail, augmente du lien vers sa facture. */
    public CommandeDetailVue avecFacture(UUID referenceFacture, String numeroFacture) {
        return new CommandeDetailVue(reference, numero, date, statut, lignes,
                totalHtva, totalTva, totalTvac, methodePaiement, datePaiement,
                statutPaiement, referenceFacture, numeroFacture);
    }

    /** Le lien de telechargement n apparait que si la facture existe — condition de la liste. */
    public boolean estFacturee() {
        return referenceFacture != null;
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
