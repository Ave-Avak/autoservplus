package be.autoservplus.intervention.web.dto;

import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Vue detaillee d une intervention pour l ecran admin. Les flags de transitions
 * sont derives directement de {@link StatutIntervention#peutPasserA} : le DTO
 * ne peut pas proposer une transition impossible.
 */
public record InterventionVueAdmin(
        UUID reference,
        String numero,
        String statut,
        String statutLisible,
        String rdvNumero,
        String vehicule,
        String membreNom,
        String membreEmail,
        String commentaireAdmin,
        String debutReel,
        String finReelle,
        List<LigneInterventionVue> lignes,
        String totalHtva,
        String totalTvac,
        boolean peutDemarrer,
        boolean peutMettreEnPause,
        boolean peutReprendre,
        boolean peutTerminer,
        boolean estEditable) {

    public static InterventionVueAdmin de(Intervention it, ZoneId zone) {
        StatutIntervention s = it.getStatut();
        var rdv = it.getRdv();
        var vehicule = it.getVehicule();
        return new InterventionVueAdmin(
                it.getReference(),
                it.getNumero(),
                s.name(),
                statutLisible(s),
                rdv != null ? rdv.getNumero() : null,
                vehicule.getMarque() + " " + vehicule.getModele() + " (" + vehicule.getPlaque() + ")",
                rdv != null ? rdv.getMembre().nomComplet() : null,
                rdv != null ? rdv.getMembre().getEmail() : null,
                it.getCommentaireAdmin(),
                it.getDebutReel() != null
                        ? FormatageRdv.jourLisible(it.getDebutReel(), zone) + " " + FormatageRdv.heureLisible(it.getDebutReel(), zone)
                        : null,
                it.getFinReelle() != null
                        ? FormatageRdv.jourLisible(it.getFinReelle(), zone) + " " + FormatageRdv.heureLisible(it.getFinReelle(), zone)
                        : null,
                it.getLignes().stream().map(LigneInterventionVue::de).toList(),
                FormatageRdv.euros(it.totalHtva()),
                FormatageRdv.euros(it.totalTvac()),
                s.peutPasserA(StatutIntervention.EN_COURS) && s == StatutIntervention.PLANIFIEE,
                s.peutPasserA(StatutIntervention.EN_PAUSE),
                s.peutPasserA(StatutIntervention.EN_COURS) && s == StatutIntervention.EN_PAUSE,
                s.peutPasserA(StatutIntervention.TERMINEE),
                s.estEditable());
    }

    private static String statutLisible(StatutIntervention s) {
        return switch (s) {
            case PLANIFIEE -> "Planifiée";
            case EN_COURS -> "En cours";
            case EN_PAUSE -> "En pause";
            case TERMINEE -> "Terminée";
            case FACTUREE -> "Facturée";
        };
    }

    public record LigneInterventionVue(
            Long id, String type, String libelle, short quantite,
            String prixUnitaireHtva, String totalHtva) {

        public static LigneInterventionVue de(LigneIntervention l) {
            return new LigneInterventionVue(
                    l.getId(),
                    l.getType().name(),
                    l.getLibelleFige(),
                    l.getQuantite(),
                    FormatageRdv.euros(l.getPrixUnitaireHtva()),
                    FormatageRdv.euros(l.totalHtva()));
        }
    }
}
