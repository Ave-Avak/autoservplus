package be.autoservplus.intervention.web.dto;

import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.service.FormatageQuantite;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Ce que le membre doit voir pour se prononcer sur un depassement de devis (RM-15) :
 * le devis qu il a accepte, ce que le garage propose en plus, et le total qui en
 * resulterait.
 *
 * <p>C est la seule vue membre qui expose des prix a la ligne. Le suivi
 * ({@link InterventionVueMembre}) s en tient au libelle et a la quantite, aligne sur
 * {@code RdvVue}. Ici l information tarifaire est l objet meme de la question posee.
 * Un accord donne sans voir le prix de chaque poste ne serait pas un accord expres.</p>
 *
 * <p>Les montants sont hors TVA, comme le seuil qu'ils servent à expliquer, sauf
 * {@code totalProposeTvac} : le membre accepte un montant à payer, il le voit taxe comprise
 * (RM-30).</p>
 */
public record DemandeValidationVue(
        UUID reference,
        String numero,
        String vehicule,
        String devisInitial,
        String totalPropose,
        String totalProposeTvac,
        String ecart,
        String commentaireAdmin,
        List<LigneProposeeVue> lignesEnAttente) {

    public static DemandeValidationVue de(Intervention it) {
        BigDecimal initial = it.devisReferenceHtva();
        BigDecimal propose = it.totalProposeHtva();
        var vehicule = it.getVehicule();
        return new DemandeValidationVue(
                it.getReference(),
                it.getNumero(),
                vehicule.getMarque() + " " + vehicule.getModele() + " (" + vehicule.getPlaque() + ")",
                FormatageRdv.eurosEcran(initial),
                FormatageRdv.eurosEcran(propose),
                FormatageRdv.eurosEcran(it.totalProposeTvac()),
                FormatageRdv.eurosEcran(propose.subtract(initial)),
                it.getCommentaireAdmin(),
                it.lignesEnAttente().stream().map(LigneProposeeVue::de).toList());
    }

    /** Une ligne soumise a l accord du membre, avec son prix : designation, quantite, total HTVA. */
    public record LigneProposeeVue(String libelle, String quantite, String totalHtva) {
        public static LigneProposeeVue de(LigneIntervention l) {
            return new LigneProposeeVue(l.getLibelleFige(),
                    FormatageQuantite.lisible(l.getQuantite(), l.getUnite(), LanguesSupportees.deLEcran()),
                    FormatageRdv.eurosEcran(l.totalHtva()));
        }
    }
}
