package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.reservation.domain.HorairePlage;
import be.autoservplus.reservation.domain.Indisponibilite;
import be.autoservplus.reservation.domain.LigneRdv;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.repository.IndisponibiliteRepository;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.service.dto.ConflitRdv;
import be.autoservplus.reservation.service.dto.IndisponibiliteVue;
import be.autoservplus.reservation.service.dto.PlageVue;
import be.autoservplus.reservation.service.dto.PosteVue;
import be.autoservplus.reservation.service.dto.PropositionRetraitPoste;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Planning de l'atelier depuis le back-office (A24) : plages d'ouverture, postes et
 * indisponibilités. Avant ce service, le garage ne réglait son planning que par
 * migration.
 *
 * <p>Même accès que le catalogue : la protection d'URL {@code /admin/**} filtre le rôle
 * ADMINISTRATEUR, et {@code @PreAuthorize} redouble en défense en profondeur.</p>
 *
 * <p><b>Conflits.</b> Réduire une plage, poser une indisponibilité ou désactiver un
 * poste est accepté même si des rendez-vous EN_ATTENTE ou CONFIRME tombent dans la
 * période touchée. Ces rendez-vous restent intacts : chaque écriture rend la liste des
 * conflits, que l'écran affiche pour un traitement manuel. Une annulation automatique a
 * été écartée : elle priverait le garage du choix entre déplacer le client, l'appeler
 * ou maintenir le rendez-vous sur un autre poste. Aucun avis n'est envoyé au client en
 * V1, faute d'envoi réel de courriel.</p>
 *
 * <p>Les conflits ne portent que sur des rendez-vous à venir ou en cours : un
 * rendez-vous terminé ne peut plus être déplacé.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminPlanningService {

    private static final EnumSet<StatutRdv> STATUTS_ACTIFS = EnumSet.of(StatutRdv.EN_ATTENTE, StatutRdv.CONFIRME);

    private final PlageOuvertureRepository plages;
    private final PosteAtelierRepository postes;
    private final IndisponibiliteRepository indisponibilites;
    private final RdvRepository rdvs;
    private final ParametreAtelierRepository parametres;
    private final Clock horloge;

    public AdminPlanningService(PlageOuvertureRepository plages,
                                PosteAtelierRepository postes,
                                IndisponibiliteRepository indisponibilites,
                                RdvRepository rdvs,
                                ParametreAtelierRepository parametres,
                                Clock horloge) {
        this.plages = plages;
        this.postes = postes;
        this.indisponibilites = indisponibilites;
        this.rdvs = rdvs;
        this.parametres = parametres;
        this.horloge = horloge;
    }

    // --- plages d'ouverture -----------------------------------------------------------

    public List<PlageVue> plages() {
        return plages.findAllByOrderByJourSemaineAscHeureDebutAsc().stream().map(PlageVue::de).toList();
    }

    public PlageVue plage(Long id) {
        return PlageVue.de(chargerPlage(id));
    }

    /**
     * Ajoute une plage. Une ouverture supplémentaire ne crée aucun conflit.
     *
     * @throws RefusPlanning            si elle chevauche une autre plage du même jour
     * @throws IllegalArgumentException si la fin ne suit pas le début
     */
    @Transactional
    public void creerPlage(DayOfWeek jour, LocalTime heureDebut, LocalTime heureFin, boolean actif) {
        PlageOuverture plage = new PlageOuverture(jour, heureDebut, heureFin);
        if (!actif) {
            plage.desactiver();
        }
        enregistrerPlage(() -> plages.saveAndFlush(plage));
    }

    /**
     * Modifie une plage et rend les rendez-vous qu'elle couvrait et qu'aucune plage
     * active ne couvre plus.
     *
     * @throws RefusPlanning            si elle chevauche une autre plage du même jour
     * @throws IllegalArgumentException si la fin ne suit pas le début
     */
    @Transactional
    public List<ConflitRdv> modifierPlage(Long id, DayOfWeek jour, LocalTime heureDebut,
                                          LocalTime heureFin, boolean actif) {
        PlageOuverture plage = chargerPlage(id);
        HorairePlage avant = plage.horaire();
        plage.modifier(jour, heureDebut, heureFin);
        if (actif) {
            plage.activer();
        } else {
            plage.desactiver();
        }
        enregistrerPlage(plages::flush);
        return conflitsDePlage(avant);
    }

    /** Retire une plage (suppression logique) et rend les rendez-vous qu'elle laisse sans ouverture. */
    @Transactional
    public List<ConflitRdv> retirerPlage(Long id, String auteur) {
        PlageOuverture plage = chargerPlage(id);
        HorairePlage avant = plage.horaire();
        plage.marquerSupprime(auteur, horloge.instant());
        plages.flush();
        return conflitsDePlage(avant);
    }

    // --- postes d'atelier ---------------------------------------------------------------

    public List<PosteVue> postes() {
        return postes.findAllByOrderByOrdreAscLibelleAsc().stream().map(PosteVue::de).toList();
    }

    public PosteVue poste(UUID reference) {
        return PosteVue.de(chargerPoste(reference));
    }

    /**
     * Crée un poste actif : la capacité de l'atelier augmente, aucun conflit possible.
     *
     * @throws RefusPlanning si le libellé est déjà pris par un poste non supprimé
     */
    @Transactional
    public void creerPoste(String libelle, String description, int ordre) {
        if (postes.existsByLibelleIgnoreCase(libelle.trim())) {
            throw libelleDejaPris(libelle);
        }
        PosteAtelier poste = new PosteAtelier(libelle);
        poste.modifier(libelle, description, ordre);
        enregistrerPoste(libelle, () -> postes.saveAndFlush(poste));
    }

    /** @throws RefusPlanning si le libellé est déjà pris par un autre poste */
    @Transactional
    public void modifierPoste(UUID reference, String libelle, String description, int ordre) {
        if (postes.existsByLibelleIgnoreCaseAndReferenceNot(libelle.trim(), reference)) {
            throw libelleDejaPris(libelle);
        }
        PosteAtelier poste = chargerPoste(reference);
        poste.modifier(libelle, description, ordre);
        enregistrerPoste(libelle, postes::flush);
    }

    public PropositionRetraitPoste propositionRetraitPoste(UUID reference) {
        PosteAtelier poste = chargerPoste(reference);
        return new PropositionRetraitPoste(poste.getReference(), poste.getLibelle(),
                postes.nombreRendezVous(poste.getId()), poste.isActif());
    }

    /**
     * Supprime physiquement un poste qu'aucun rendez-vous ne référence (RM-29). Ses
     * indisponibilités partent avec lui ({@code ON DELETE CASCADE}).
     *
     * @throws RefusPlanning si un rendez-vous le référence, seule la désactivation est alors permise
     */
    @Transactional
    public void supprimerDefinitivementPoste(UUID reference) {
        PosteAtelier poste = chargerPoste(reference);
        if (postes.nombreRendezVous(poste.getId()) > 0) {
            throw retraitRefuse(poste);
        }
        try {
            postes.delete(poste);
            postes.flush();
        } catch (DataIntegrityViolationException e) {
            // Rendez-vous apparu entre le comptage et la suppression : la FK RESTRICT
            // refuse, seconde ligne de défense de RM-29, traduite en refus plutôt qu'en 500.
            throw retraitRefuse(poste);
        }
    }

    /** Désactive un poste et rend ses rendez-vous à venir, qui gardent leur poste. */
    @Transactional
    public List<ConflitRdv> desactiverPoste(UUID reference) {
        PosteAtelier poste = chargerPoste(reference);
        poste.desactiver();
        postes.flush();
        return conflits(nonTermines().stream().filter(r -> poste.equals(r.getPoste())).toList());
    }

    @Transactional
    public void activerPoste(UUID reference) {
        chargerPoste(reference).activer();
    }

    // --- indisponibilités ---------------------------------------------------------------

    /** Indisponibilités en cours et à venir. Les passées ne règlent plus rien. */
    public List<IndisponibiliteVue> indisponibilites() {
        ZoneId zone = zone();
        return indisponibilites.aVenir(horloge.instant()).stream().map(i -> vue(i, zone)).toList();
    }

    public IndisponibiliteVue indisponibilite(UUID reference) {
        return vue(chargerIndisponibilite(reference), zone());
    }

    /**
     * Pose une indisponibilité, bornes saisies en heure locale du garage.
     *
     * @param posteReference nul pour fermer tout l'atelier
     * @throws IllegalArgumentException si la fin ne suit pas le début ou si le motif est vide
     */
    @Transactional
    public List<ConflitRdv> creerIndisponibilite(UUID posteReference, LocalDateTime debut,
                                                 LocalDateTime fin, String motif) {
        ZoneId zone = zone();
        Indisponibilite indispo = new Indisponibilite(posteOuAtelier(posteReference),
                debut.atZone(zone).toInstant(), fin.atZone(zone).toInstant(), motif);
        indisponibilites.saveAndFlush(indispo);
        return conflitsDIndisponibilite(indispo);
    }

    @Transactional
    public List<ConflitRdv> modifierIndisponibilite(UUID reference, UUID posteReference,
                                                    LocalDateTime debut, LocalDateTime fin, String motif) {
        ZoneId zone = zone();
        Indisponibilite indispo = chargerIndisponibilite(reference);
        indispo.modifier(posteOuAtelier(posteReference),
                debut.atZone(zone).toInstant(), fin.atZone(zone).toInstant(), motif);
        indisponibilites.flush();
        return conflitsDIndisponibilite(indispo);
    }

    /** Retire une indisponibilité (suppression logique). Rouvrir ne crée aucun conflit. */
    @Transactional
    public void retirerIndisponibilite(UUID reference, String auteur) {
        chargerIndisponibilite(reference).marquerSupprime(auteur, horloge.instant());
    }

    // --- conflits -----------------------------------------------------------------------

    private List<ConflitRdv> conflitsDePlage(HorairePlage avant) {
        List<HorairePlage> actives = plages.findByActifTrue().stream().map(PlageOuverture::horaire).toList();
        return conflits(rendezVousDecouverts(avant, actives, nonTermines(), zone()));
    }

    private List<ConflitRdv> conflitsDIndisponibilite(Indisponibilite indispo) {
        return conflits(nonTermines().stream()
                .filter(r -> touchePar(indispo, r.getPoste(), r.getDebut(), r.getFin()))
                .toList());
    }

    /**
     * Rendez-vous que l'horaire d'avant couvrait et qu'aucune plage active ne couvre plus.
     *
     * <p>Le recoupement avec l'ancien horaire écarte les rendez-vous que la modification
     * ne concerne pas, sur un autre jour ou à une autre heure. La couverture exige une
     * seule plage contenant tout le rendez-vous, comme à la réservation
     * ({@code DisponibiliteService.estReservable}).</p>
     */
    static List<Rdv> rendezVousDecouverts(HorairePlage avant, Collection<HorairePlage> plagesActives,
                                          List<Rdv> candidats, ZoneId zone) {
        return candidats.stream().filter(r -> {
            ZonedDateTime debut = r.getDebut().atZone(zone);
            DayOfWeek jour = debut.getDayOfWeek();
            LocalTime heureDebut = debut.toLocalTime();
            LocalTime heureFin = r.getFin().atZone(zone).toLocalTime();
            return avant.recoupe(jour, heureDebut, heureFin)
                    && plagesActives.stream().noneMatch(p -> p.contient(jour, heureDebut, heureFin));
        }).toList();
    }

    /** Vrai si l'indisponibilité ferme le poste du rendez-vous, ou tout l'atelier, pendant celui-ci. */
    static boolean touchePar(Indisponibilite indispo, PosteAtelier poste, Instant debut, Instant fin) {
        return (indispo.concerneToutLAtelier() || indispo.getPoste().equals(poste))
                && indispo.chevauche(debut, fin);
    }

    private List<Rdv> nonTermines() {
        return rdvs.nonTerminesAvecDetails(horloge.instant(), STATUTS_ACTIFS);
    }

    private List<ConflitRdv> conflits(List<Rdv> rendezVous) {
        ZoneId zone = zone();
        return rendezVous.stream().map(r -> new ConflitRdv(
                r.getReference(),
                r.getNumero(),
                LocalDateTime.ofInstant(r.getDebut(), zone),
                LocalDateTime.ofInstant(r.getFin(), zone),
                r.getMembre().nomComplet(),
                r.getPoste().getLibelle(),
                r.getLignes().stream().map(LigneRdv::getPrestation)
                        .map(Prestation::getLibelle).collect(Collectors.joining(", ")),
                r.getStatut())).toList();
    }

    // --- aides --------------------------------------------------------------------------

    /**
     * Écrit la plage et traduit la violation de {@code ex_plage_ouverture_chevauchement}
     * (état SQL 23P01) en refus rattaché au champ. Le flush immédiat fait remonter la
     * violation ici, où elle se traduit, et non au commit où elle deviendrait une 500.
     * Un contrôle préalable en Java a été écarté : il ne protégerait pas de deux
     * écritures simultanées, que la contrainte arrête de toute façon.
     */
    private void enregistrerPlage(Runnable ecriture) {
        try {
            ecriture.run();
        } catch (DataIntegrityViolationException e) {
            if (EtatSql.estUneExclusion(e)) {
                throw new RefusPlanning("heureDebut", "admin.planning.plage.erreur.chevauchement",
                        "Cette plage chevauche une autre plage du même jour.");
            }
            throw e;
        }
    }

    /** Course entre le contrôle d'unicité et l'écriture : {@code uq_poste_libelle_actif} tranche. */
    private void enregistrerPoste(String libelle, Runnable ecriture) {
        try {
            ecriture.run();
        } catch (DataIntegrityViolationException e) {
            throw libelleDejaPris(libelle);
        }
    }

    private static RefusPlanning libelleDejaPris(String libelle) {
        return new RefusPlanning("libelle", "admin.planning.poste.erreur.doublon",
                "Le libellé « %s » est déjà pris par un autre poste.".formatted(libelle.trim()));
    }

    private static RefusPlanning retraitRefuse(PosteAtelier poste) {
        return new RefusPlanning(null, "admin.planning.poste.erreur.reference",
                "Le poste « %s » est référencé par des rendez-vous : seule la désactivation est permise."
                        .formatted(poste.getLibelle()));
    }

    private PosteAtelier posteOuAtelier(UUID posteReference) {
        return posteReference == null ? null : chargerPoste(posteReference);
    }

    private IndisponibiliteVue vue(Indisponibilite i, ZoneId zone) {
        PosteAtelier poste = i.getPoste();
        return new IndisponibiliteVue(i.getReference(),
                poste == null ? null : poste.getReference(),
                poste == null ? null : poste.getLibelle(),
                LocalDateTime.ofInstant(i.getDebut(), zone),
                LocalDateTime.ofInstant(i.getFin(), zone),
                i.getMotif());
    }

    private ZoneId zone() {
        return parametres.courants().zone();
    }

    private PlageOuverture chargerPlage(Long id) {
        return plages.parIdentifiant(id).orElseThrow(() -> new RessourceIntrouvableException("Plage d'ouverture", id));
    }

    private PosteAtelier chargerPoste(UUID reference) {
        return postes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Poste d'atelier", reference));
    }

    private Indisponibilite chargerIndisponibilite(UUID reference) {
        return indisponibilites.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Indisponibilité", reference));
    }
}
