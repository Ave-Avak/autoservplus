package be.autoservplus.intervention.web.dto;

import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.intervention.domain.HistoriqueStatutIntervention;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.LigneIntervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.service.FormatageQuantite;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vue d une intervention destinee au membre proprietaire.
 *
 * <p><b>Regle metier RM-16</b> : le membre ne voit pas la mecanique interne du
 * garage. La clé {@link #cleStatutPercu()} projette les six statuts techniques
 * sur les quatre statuts perçus du CdC (« En attente », « En cours »,
 * « Terminée », « Annulée ») et c'est ce que le gabarit affiche, dans la langue de
 * l'écran. Les dates sont des {@code LocalDateTime} à l'heure de l'atelier : le
 * gabarit les formate, le DTO connaît le fuseau. Le champ
 * {@link #statut()} conserve la valeur technique brute (« SUSPENDUE »,
 * « ATTENTE_VALIDATION_MEMBRE »...) uniquement pour la logique interne du
 * template (branchement de messages sur TERMINEE vs ANNULEE). Il ne doit
 * JAMAIS etre rendu comme texte visible du membre.</p>
 */
public record InterventionVueMembre(
        UUID reference,
        String numero,
        /** Statut technique brut (SUSPENDUE, ATTENTE_VALIDATION_MEMBRE...).
         *  Usage interne du template UNIQUEMENT (branchement) : ne jamais afficher. */
        String statut,
        /** Clé i18n du statut perçu par le membre (RM-16). C'est ce qui s'affiche. */
        String cleStatutPercu,
        String vehicule,
        String commentaireAdmin,
        LocalDateTime debutReel,
        LocalDateTime finReelle,
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
                s.clePercue(),
                vehicule.getMarque() + " " + vehicule.getModele() + " (" + vehicule.getPlaque() + ")",
                it.getCommentaireAdmin(),
                heureLocale(it.getDebutReel(), zone),
                heureLocale(it.getFinReelle(), zone),
                // Seules les lignes acquises figurent dans « Travaux prevus » : une ligne
                // en attente n est pas encore due (elle s affiche sur l ecran de
                // validation, avec son prix), une ligne refusee ne sera pas executee.
                it.getLignes().stream()
                        .filter(LigneIntervention::estFacturable)
                        .map(LigneVue::de).toList(),
                FormatageRdv.eurosEcran(it.totalFacturableTvac()),
                chronologiePercue(historique, zone),
                s == StatutIntervention.TERMINEE || s == StatutIntervention.ANNULEE,
                s == StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
    }

    private static LocalDateTime heureLocale(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }

    /**
     * Projette la chronologie technique (F17) sur les statuts percus (RM-16) : une
     * entree n apparait que si son statut percu differe de la derniere entree
     * affichee. EN_COURS -&gt; SUSPENDUE -&gt; EN_COURS, tous percus « En cours »,
     * donnerait sinon trois lignes identiques : exposant au membre le rythme de la
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
    /**
     * @param piecesIncluses pièces comprises dans un forfait (V77), en lecture seule : le membre
     *                       voit ce qui est posé, jamais sur la facture
     */
    public record LigneVue(String libelle, String quantite, String mentionPieces,
                           List<PieceIncluseVue> piecesIncluses) {
        public static LigneVue de(LigneIntervention l) {
            return new LigneVue(l.getLibelleFige(),
                    FormatageQuantite.lisible(l.getQuantite(), l.getUnite(), LanguesSupportees.deLEcran()),
                    ChoixPieces.cleMentionDe(l.getChoixPieces()),
                    l.getPiecesIncluses().stream().map(i -> PieceIncluseVue.de(i, Set.of())).toList());
        }
    }

    /**
     * Une etape de la chronologie vue par le membre (F17). Les statuts restent les
     * enums techniques : le template affiche leur cle i18n percue via
     * {@link StatutIntervention#clePercue()}, jamais leur nom brut (RM-16). La date
     * est à l'heure de l'atelier, comme {@code debutReel} et {@code finReelle} : le DTO
     * connaît le fuseau, le gabarit la formate dans la langue de l'écran. L'auteur
     * n'est volontairement pas exposé côté membre.
     */
    public record EntreeChronologieVue(
            StatutIntervention statutAvant,
            StatutIntervention statutApres,
            LocalDateTime horodatage,
            String motif) {

        public static EntreeChronologieVue de(HistoriqueStatutIntervention h, ZoneId zone) {
            return new EntreeChronologieVue(
                    h.getStatutAvant(),
                    h.getStatutApres(),
                    LocalDateTime.ofInstant(h.getHorodatage(), zone),
                    h.getMotif());
        }
    }
}
