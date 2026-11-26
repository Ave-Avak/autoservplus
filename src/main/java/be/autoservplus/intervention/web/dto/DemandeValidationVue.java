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
 * <p>Chaque montant est donné taxe comprise, le prix qu'un consommateur paie (RM-30), avec
 * son équivalent hors TVA à côté. Le seuil de 10 % de RM-15 se mesure hors TVA, mais c'est
 * un prix à payer que le membre accepte ou refuse : l'annoncer hors taxe le lui cacherait.</p>
 */
public record DemandeValidationVue(
        UUID reference,
        String numero,
        String vehicule,
        String devisInitialTvac,
        String devisInitialHtva,
        String ecartTvac,
        String ecartHtva,
        String totalProposeTvac,
        String totalProposeHtva,
        String commentaireAdmin,
        List<LigneProposeeVue> lignesEnAttente) {

    public static DemandeValidationVue de(Intervention it) {
        BigDecimal initialHtva = it.totalDevisInitialHtva();
        BigDecimal initialTvac = it.totalDevisInitialTvac();
        BigDecimal proposeHtva = it.totalProposeHtva();
        BigDecimal proposeTvac = it.totalProposeTvac();
        var vehicule = it.getVehicule();
        return new DemandeValidationVue(
                it.getReference(),
                it.getNumero(),
                vehicule.getMarque() + " " + vehicule.getModele() + " (" + vehicule.getPlaque() + ")",
                FormatageRdv.eurosEcran(initialTvac),
                FormatageRdv.eurosEcran(initialHtva),
                FormatageRdv.eurosEcran(proposeTvac.subtract(initialTvac)),
                FormatageRdv.eurosEcran(proposeHtva.subtract(initialHtva)),
                FormatageRdv.eurosEcran(proposeTvac),
                FormatageRdv.eurosEcran(proposeHtva),
                it.getCommentaireAdmin(),
                it.lignesEnAttente().stream().map(LigneProposeeVue::de).toList());
    }

    /** Une ligne soumise à l'accord du membre, avec son prix taxe comprise et hors TVA. */
    public record LigneProposeeVue(String libelle, String quantite, String totalTvac, String totalHtva) {
        public static LigneProposeeVue de(LigneIntervention l) {
            return new LigneProposeeVue(l.getLibelleFige(),
                    FormatageQuantite.lisible(l.getQuantite(), l.getUnite(), LanguesSupportees.deLEcran()),
                    FormatageRdv.eurosEcran(l.totalTvac()), FormatageRdv.eurosEcran(l.totalHtva()));
        }
    }
}
