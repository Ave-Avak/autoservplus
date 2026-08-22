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
        /** Total reellement facturable : hors lignes refusees et hors lignes en attente. */
        String totalHtva,
        String totalTvac,
        boolean peutDemarrer,
        boolean peutSuspendre,
        boolean peutReprendre,
        boolean peutTerminer,
        boolean peutAnnuler,
        /** Commentaire admin et retrait de ligne : tant que l intervention n est pas terminale. */
        boolean estEditable,
        /**
         * RM-14 : le formulaire d ajout de ligne n a de sens qu en EN_COURS. Le
         * domaine refuse deja l ajout ailleurs ; ce flag evite d afficher un
         * formulaire condamne, il ne le remplace pas.
         */
        boolean peutAjouterLigne,
        /** RM-15 : le garage attend la reponse du membre, il ne peut pas reprendre. */
        boolean enAttenteValidationMembre,
        String devisInitialHtva,
        String totalProposeHtva) {

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
                FormatageRdv.euros(it.totalFacturableHtva()),
                FormatageRdv.euros(it.totalFacturableTvac()),
                s == StatutIntervention.PLANIFIEE,
                s.peutPasserA(StatutIntervention.SUSPENDUE),
                // Reprendre reste possible depuis SUSPENDUE, jamais tant qu une ligne
                // attend la reponse du membre (RM-15) : le domaine refuse de toute
                // facon la transition, le DTO evite d afficher un bouton condamne.
                s == StatutIntervention.SUSPENDUE && !it.aDesLignesEnAttente(),
                s.peutPasserA(StatutIntervention.TERMINEE),
                s.peutPasserA(StatutIntervention.ANNULEE),
                s.estEditable(),
                s.accepteAjoutDeLigne(),
                s == StatutIntervention.ATTENTE_VALIDATION_MEMBRE,
                FormatageRdv.euros(it.devisReferenceHtva()),
                FormatageRdv.euros(it.totalProposeHtva()));
    }

    private static String statutLisible(StatutIntervention s) {
        return switch (s) {
            case PLANIFIEE -> "Planifiée";
            case EN_COURS -> "En cours";
            case SUSPENDUE -> "Suspendue";
            case ATTENTE_VALIDATION_MEMBRE -> "En attente de validation du membre";
            case TERMINEE -> "Terminée";
            case ANNULEE -> "Annulée";
        };
    }

    /**
     * Le garage voit TOUTES les lignes, y compris celles en attente et celles que le
     * membre a refusees : c est son dossier de travail. {@code etat} porte la mention
     * a afficher, {@code compteDansLeTotal} permet de griser celles qui n y entrent pas.
     */
    public record LigneInterventionVue(
            Long id, String type, String libelle, short quantite,
            String prixUnitaireHtva, String totalHtva,
            String etat, boolean compteDansLeTotal) {

        public static LigneInterventionVue de(LigneIntervention l) {
            return new LigneInterventionVue(
                    l.getId(),
                    l.getType().name(),
                    l.getLibelleFige(),
                    l.getQuantite(),
                    FormatageRdv.euros(l.getPrixUnitaireHtva()),
                    FormatageRdv.euros(l.totalHtva()),
                    etatLisible(l),
                    l.estFacturable());
        }

        private static String etatLisible(LigneIntervention l) {
            if (l.estRefusee()) return "Refusée par le membre";
            if (l.estEnAttenteValidation()) return "En attente d'accord";
            return l.estDuDevisInitial() ? "Devis initial" : "Ajoutée en cours";
        }
    }
}
