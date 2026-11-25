package be.autoservplus.intervention.web.dto;

import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.service.FormatageQuantite;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
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
        String rdvNumero,
        /** Commande de prestations d'origine (F12-b), ou {@code null}. Sans rendez-vous ni commande : entrée directe. */
        String commandeNumero,
        String vehicule,
        String membreNom,
        String membreEmail,
        String commentaireAdmin,
        LocalDateTime debutReel,
        LocalDateTime finReelle,
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
         * domaine refuse deja l ajout ailleurs. Ce flag evite d afficher un
         * formulaire condamne, il ne le remplace pas.
         */
        boolean peutAjouterLigne,
        /** RM-15 : le garage attend la reponse du membre, il ne peut pas reprendre. */
        boolean enAttenteValidationMembre,
        String devisInitialHtva,
        String totalProposeHtva,
        /** Lien vers l'historique d'entretien du véhicule (F21). */
        UUID vehiculeReference,
        /** Note de l'atelier (A16). Vue du garage seulement, comme ce record. */
        String noteInterne) {

    public static InterventionVueAdmin de(Intervention it, ZoneId zone) {
        return de(it, zone, Set.of());
    }

    /**
     * @param piecesACommander identifiants des pièces dont le stock ne couvre pas les
     *                         interventions ouvertes (Q-H-30) : leurs lignes portent la
     *                         mention « à commander »
     */
    public static InterventionVueAdmin de(Intervention it, ZoneId zone, Set<Long> piecesACommander) {
        StatutIntervention s = it.getStatut();
        var rdv = it.getRdv();
        var commande = it.getCommande();
        var vehicule = it.getVehicule();
        // Le membre est le titulaire, quelle que soit l'origine : celui du rendez-vous, de la
        // commande, ou le propriétaire du véhicule pour une entrée directe.
        var titulaire = it.titulaire();
        return new InterventionVueAdmin(
                it.getReference(),
                it.getNumero(),
                s.name(),
                rdv != null ? rdv.getNumero() : null,
                commande != null ? commande.getNumero() : null,
                vehicule.getMarque() + " " + vehicule.getModele() + " (" + vehicule.getPlaque() + ")",
                titulaire != null ? titulaire.nomComplet() : null,
                titulaire != null ? titulaire.getEmail() : null,
                it.getCommentaireAdmin(),
                it.getDebutReel() == null ? null : LocalDateTime.ofInstant(it.getDebutReel(), zone),
                it.getFinReelle() == null ? null : LocalDateTime.ofInstant(it.getFinReelle(), zone),
                it.getLignes().stream().map(l -> LigneInterventionVue.de(l, piecesACommander)).toList(),
                FormatageRdv.eurosEcran(it.totalFacturableHtva()),
                FormatageRdv.eurosEcran(it.totalFacturableTvac()),
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
                FormatageRdv.eurosEcran(it.devisReferenceHtva()),
                FormatageRdv.eurosEcran(it.totalProposeHtva()),
                vehicule.getReference(),
                it.getNoteInterne());
    }

    /**
     * Le garage voit TOUTES les lignes, y compris celles en attente et celles que le
     * membre a refusees : c est son dossier de travail. {@code cleEtat} porte la clé de
     * la mention à afficher, {@code compteDansLeTotal} permet de griser celles qui n'y
     * entrent pas. Le type reste le nom de l'énumération, traduit par le gabarit.
     * {@code aCommander} ne vaut que pour une pièce non refusée dont le stock ne couvre pas
     * les interventions ouvertes : une ligne refusée ne sera pas posée.
     * {@code mentionPieces} est la clé de la mention du choix des pièces, nulle sans choix.
     * {@code porteDesPiecesIncluses} ouvre le formulaire des pièces incluses d'un forfait dont le
     * garage fournit les pièces (V77), même quand la composition est vide.
     */
    public record LigneInterventionVue(
            Long id, String type, String libelle, String quantite,
            String prixUnitaireHtva, String totalHtva,
            String cleEtat, boolean compteDansLeTotal, boolean aCommander, String mentionPieces,
            boolean porteDesPiecesIncluses, List<PieceIncluseVue> piecesIncluses) {

        public static LigneInterventionVue de(LigneIntervention l, Set<Long> piecesACommander) {
            return new LigneInterventionVue(
                    l.getId(),
                    l.getType().name(),
                    l.getLibelleFige(),
                    FormatageQuantite.lisible(l.getQuantite(), l.getUnite(), LanguesSupportees.deLEcran()),
                    FormatageRdv.eurosEcran(l.getPrixUnitaireHtva()),
                    FormatageRdv.eurosEcran(l.totalHtva()),
                    cleEtat(l),
                    l.estFacturable(),
                    l.getPiece() != null && !l.estRefusee()
                            && piecesACommander.contains(l.getPiece().getId()),
                    ChoixPieces.cleMentionDe(l.getChoixPieces()),
                    l.porteDesPiecesIncluses(),
                    l.getPiecesIncluses().stream().map(i -> PieceIncluseVue.de(i, piecesACommander)).toList());
        }

        private static String cleEtat(LigneIntervention l) {
            if (l.estRefusee()) return "admin.historique.etat.refusee";
            if (l.estEnAttenteValidation()) return "admin.historique.etat.attente";
            return l.estDuDevisInitial()
                    ? "admin.historique.etat.initiale"
                    : "admin.historique.etat.ajoutee";
        }
    }
}
