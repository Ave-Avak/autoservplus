package be.autoservplus.intervention.web.dto;

import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Vue d une intervention destinee au membre proprietaire.
 *
 * <p><b>Regle metier RM-16</b> : le membre ne voit pas la mecanique interne du
 * garage. La chaine {@link #statutPercu()} projette les six statuts techniques
 * sur les quatre statuts percus du CdC (« En attente », « En cours »,
 * « Terminee », « Annulee ») et c est ce que le template affiche. Le champ
 * {@link #statut()} conserve la valeur technique brute (« SUSPENDUE »,
 * « ATTENTE_VALIDATION_MEMBRE »...) uniquement pour la logique interne du
 * template (branchement de messages sur TERMINEE vs ANNULEE) ; il ne doit
 * JAMAIS etre rendu comme texte visible du membre.</p>
 */
public record InterventionVueMembre(
        UUID reference,
        String numero,
        /** Statut technique brut (SUSPENDUE, ATTENTE_VALIDATION_MEMBRE...).
         *  Usage interne du template UNIQUEMENT (branchement) — ne jamais afficher. */
        String statut,
        /** Statut percu par le membre (RM-16) : « En attente », « En cours »,
         *  « Terminee », « Annulee ». C est ce qui doit s afficher a l ecran. */
        String statutPercu,
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
                s.percuLabel(),
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
