package be.autoservplus.retractation.web.dto;

import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Une demande de retractation telle que l ecran du garage en a besoin (F30).
 *
 * <p>Montants et dates pre-formates, convention du module vente ; {@code statut}
 * expose brut, le libelle affiche etant une cle i18n construite dans le gabarit.</p>
 *
 * <p>{@code joursDepuisLaDemande} n est pas decoratif. Le professionnel doit
 * rembourser dans les quatorze jours de la demande (CDE, art. VI.50) : sans ce
 * compteur sous les yeux, l administrateur n a aucun moyen de voir qu un dossier
 * approche de l echeance, et {@code urgent} le lui signale avant qu il ne soit trop
 * tard. Il est calcule a l affichage plutot que stocke — un delai qui court se
 * recalcule, il ne se persiste pas.</p>
 */
public record DemandeAnnulationVueAdmin(
        UUID reference,
        StatutDemandeAnnulation statut,
        String numeroCommande,
        String montantTvac,
        String dateCommande,
        String dateDemande,
        long joursDepuisLaDemande,
        boolean urgent,
        String membreNom,
        String membreEmail,
        String motifMembre,
        String motifDecision,
        boolean peutDecider) {

    /** Au-dela de ce seuil, le remboursement legal approche : le dossier passe en tete. */
    private static final long SEUIL_URGENCE_JOURS = 10;

    public static DemandeAnnulationVueAdmin de(DemandeAnnulation demande, ZoneId zone,
                                               Instant maintenant) {
        long jours = Duration.between(demande.getDateDemande(), maintenant).toDays();
        return new DemandeAnnulationVueAdmin(
                demande.getReference(),
                demande.getStatut(),
                demande.getCommande().getNumero(),
                FormatageRdv.euros(demande.getCommande().getMontantTvac()),
                FormatageRdv.jourLisible(demande.getCommande().getDateCommande(), zone),
                FormatageRdv.jourLisible(demande.getDateDemande(), zone),
                jours,
                demande.estEnAttente() && jours >= SEUIL_URGENCE_JOURS,
                demande.getCommande().getMembre().nomComplet(),
                demande.getCommande().getMembre().getEmail(),
                demande.getMotifMembre(),
                demande.getMotifDecision(),
                demande.estEnAttente());
    }

    /** Le membre n a pas a se justifier : l absence de motif est le cas normal. */
    public boolean aUnMotifMembre() {
        return motifMembre != null && !motifMembre.isBlank();
    }
}
