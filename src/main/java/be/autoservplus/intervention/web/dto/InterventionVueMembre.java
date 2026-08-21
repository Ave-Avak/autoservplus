package be.autoservplus.intervention.web.dto;

import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Vue d une intervention destinee au membre proprietaire. Ne contient que ce
 * que le client doit voir : etat d avancement, ligne de facturation prevue,
 * commentaire visible du garage. Pas de champs administratifs (diagnostic
 * interne, transitions autorisees, etc.).
 */
public record InterventionVueMembre(
        UUID reference,
        String numero,
        String statut,
        String statutLisible,
        String vehicule,
        String commentaireAdmin,
        String debutReel,
        String finReelle,
        List<LigneVue> lignes,
        String totalTvac,
        boolean estTerminale) {

    public static InterventionVueMembre de(Intervention it, ZoneId zone) {
        StatutIntervention s = it.getStatut();
        var vehicule = it.getVehicule();
        return new InterventionVueMembre(
                it.getReference(),
                it.getNumero(),
                s.name(),
                statutLisible(s),
                vehicule.getMarque() + " " + vehicule.getModele() + " (" + vehicule.getPlaque() + ")",
                it.getCommentaireAdmin(),
                it.getDebutReel() != null
                        ? FormatageRdv.jourLisible(it.getDebutReel(), zone) + " " + FormatageRdv.heureLisible(it.getDebutReel(), zone)
                        : null,
                it.getFinReelle() != null
                        ? FormatageRdv.jourLisible(it.getFinReelle(), zone) + " " + FormatageRdv.heureLisible(it.getFinReelle(), zone)
                        : null,
                it.getLignes().stream().map(LigneVue::de).toList(),
                FormatageRdv.euros(it.totalTvac()),
                s == StatutIntervention.TERMINEE || s == StatutIntervention.ANNULEE);
    }

    private static String statutLisible(StatutIntervention s) {
        return switch (s) {
            case PLANIFIEE -> "Planifiée, en attente de démarrage";
            case EN_COURS -> "En cours au garage";
            case SUSPENDUE -> "Travaux momentanément suspendus";
            case ATTENTE_VALIDATION_MEMBRE -> "En attente de votre accord sur un dépassement";
            case TERMINEE -> "Terminée, votre véhicule est prêt";
            case ANNULEE -> "Annulée";
        };
    }

    /**
     * Lignes vues par le membre : libelle et quantite seulement, pas de prix
     * par ligne. Alignement avec {@code RdvVue.prestations} qui expose la meme
     * granularite (List<String> de libelles + montant total global uniquement).
     * Le membre voit le meme niveau de detail sur ses RDV et sur ses
     * interventions, aucune transparence tarifaire perdue puisque {@code totalTvac}
     * reste expose au niveau intervention.
     */
    public record LigneVue(String libelle, short quantite) {
        public static LigneVue de(LigneIntervention l) {
            return new LigneVue(l.getLibelleFige(), l.getQuantite());
        }
    }
}
