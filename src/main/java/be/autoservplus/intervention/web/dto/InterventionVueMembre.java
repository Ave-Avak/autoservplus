package be.autoservplus.intervention.web.dto;

import be.autoservplus.intervention.domain.HistoriqueStatutIntervention;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.ZoneId;
import java.util.ArrayList;
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
        /** Chronologie des changements de statut (F17), deja projetee sur les statuts
         *  percus (RM-16) : les transitions internes qui ne changent pas le percu
         *  (suspension, attente de validation, reprise) n y figurent pas. */
        List<EntreeChronologieVue> chronologie,
        boolean estTerminale,
        /** RM-15 : une reponse du membre est attendue sur un depassement de devis.
         *  Expose comme drapeau plutot que par lecture du statut technique, pour que
         *  le template n ait jamais a tester « ATTENTE_VALIDATION_MEMBRE » (RM-16). */
        boolean validationRequise) {

    public static InterventionVueMembre de(Intervention it,
                                           List<HistoriqueStatutIntervention> historique,
                                           ZoneId zone) {
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
                // Seules les lignes acquises figurent dans « Travaux prevus » : une ligne
                // en attente n est pas encore due (elle s affiche sur l ecran de
                // validation, avec son prix), une ligne refusee ne sera pas executee.
                it.getLignes().stream()
                        .filter(LigneIntervention::estFacturable)
                        .map(LigneVue::de).toList(),
                FormatageRdv.euros(it.totalFacturableTvac()),
                chronologiePercue(historique, zone),
                s == StatutIntervention.TERMINEE || s == StatutIntervention.ANNULEE,
                s == StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
    }

    /**
     * Projette la chronologie technique (F17) sur les statuts percus (RM-16) : une
     * entree n apparait que si son statut percu differe de la derniere entree
     * affichee. EN_COURS -&gt; SUSPENDUE -&gt; EN_COURS, tous percus « En cours »,
     * donnerait sinon trois lignes identiques — exposant au membre le rythme de la
     * mecanique interne que RM-16 masque justement. Le journal complet reste en
     * base, seul l affichage membre est filtre.
     */
    private static List<EntreeChronologieVue> chronologiePercue(
            List<HistoriqueStatutIntervention> historique, ZoneId zone) {
        List<EntreeChronologieVue> entrees = new ArrayList<>();
        String percuPrecedent = null;
        for (HistoriqueStatutIntervention h : historique) {
            String percu = h.getStatutApres().clePercue();
            if (percu.equals(percuPrecedent)) {
                continue;
            }
            entrees.add(EntreeChronologieVue.de(h, zone));
            percuPrecedent = percu;
        }
        return List.copyOf(entrees);
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

    /**
     * Une etape de la chronologie vue par le membre (F17). Les statuts restent les
     * enums techniques — le template affiche leur cle i18n percue via
     * {@link StatutIntervention#clePercue()}, jamais leur nom brut (RM-16). La date
     * arrive pre-formatee, comme {@code debutReel} et {@code finReelle} : le DTO
     * connait le fuseau, le template non. L auteur n est volontairement pas expose
     * cote membre.
     */
    public record EntreeChronologieVue(
            StatutIntervention statutAvant,
            StatutIntervention statutApres,
            String horodatage,
            String motif) {

        public static EntreeChronologieVue de(HistoriqueStatutIntervention h, ZoneId zone) {
            return new EntreeChronologieVue(
                    h.getStatutAvant(),
                    h.getStatutApres(),
                    FormatageRdv.jourLisible(h.getHorodatage(), zone)
                            + " " + FormatageRdv.heureLisible(h.getHorodatage(), zone),
                    h.getMotif());
        }
    }
}
