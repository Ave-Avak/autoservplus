package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.common.web.PeriodeFiltre;
import be.autoservplus.communication.service.DetailsRdvCourriel;
import be.autoservplus.communication.service.PieceJointeCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.reservation.domain.JoursFeries;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.service.dto.AgendaAdmin;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.reservation.web.dto.CreneauVue;
import be.autoservplus.reservation.service.dto.FichierAgenda;
import be.autoservplus.reservation.web.dto.RdvVueAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Actions administratives sur les rendez-vous : confirmation, refus, annulation par
 * le garage, clôture (honoré, absent), plus les vues de l'écran de traitement, de
 * l'agenda et du détail.
 *
 * <p>Chaque transition delegue au domaine ({@link Rdv} et sa machine a etats) : les
 * gardes RM-10 vivent dans l entite et remontent telles quelles. Le service se
 * contente de charger, appeler la transition, ecrire, et notifier le membre pour
 * les decisions visibles de l exterieur (confirmation, refus, annulation garage).
 * La cloture honore/absent n envoie pas de mail : ce sont des marqueurs internes,
 * pas des decisions dont le membre attend confirmation.</p>
 *
 * <p>Concurrence : {@code @Version} sur {@link Rdv} protege les transitions
 * simultanees. Un conflit remonte via {@link OptimisticLockingFailureException}
 * et est traduit en {@link ConflitConcurrenceException} pour un message
 * utilisateur clair.</p>
 *
 * <p>Notification : {@code notifierSansEchouer} avale les {@link RuntimeException}
 * du fournisseur mail pour que la transition, deja persistee, ne soit pas
 * annulee par un provider indisponible. Pattern divergent d {@code InscriptionService}
 * (documente).</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminRdvService {

    private static final Logger log = LoggerFactory.getLogger(AdminRdvService.class);

    /** Étendue par défaut de l'agenda, au-delà du jour courant. */
    static final int HORIZON_AGENDA_JOURS = 14;

    private final RdvRepository rdvs;
    private final ParametreAtelierRepository parametres;
    private final ServiceCourriel courriel;
    private final InterventionService interventions;
    private final ExportAgendaService exportAgenda;
    private final ApplicationEventPublisher evenements;
    private final DisponibiliteService disponibilites;
    private final IdentiteGarage garage;
    private final Clock horloge;

    public AdminRdvService(RdvRepository rdvs, ParametreAtelierRepository parametres,
                           ServiceCourriel courriel, InterventionService interventions,
                           ExportAgendaService exportAgenda,
                           ApplicationEventPublisher evenements,
                           DisponibiliteService disponibilites, IdentiteGarage garage,
                           Clock horloge) {
        this.rdvs = rdvs;
        this.parametres = parametres;
        this.courriel = courriel;
        this.interventions = interventions;
        this.exportAgenda = exportAgenda;
        this.evenements = evenements;
        this.disponibilites = disponibilites;
        this.garage = garage;
        this.horloge = horloge;
    }

    // --- tableau de bord --------------------------------------------------------------

    /** Demandes en attente de decision par le garage, du plus ancien au plus recent. */
    public List<RdvVueAdmin> demandesEnAttente() {
        ZoneId zone = parametres.courants().zone();
        var maintenant = horloge.instant();
        return rdvs.findByStatutOrderByDebut(StatutRdv.EN_ATTENTE).stream()
                .map(r -> RdvVueAdmin.de(r, zone, maintenant))
                .toList();
    }

    /**
     * Rendez-vous confirmés commencés aujourd'hui, de minuit (fuseau de l'atelier) à
     * maintenant : le client est attendu ou présent, le garage le marque honoré, ou
     * absent une fois le créneau écoulé (flags de {@link RdvVueAdmin}).
     */
    public List<RdvVueAdmin> rendezVousDuJour() {
        ZoneId zone = parametres.courants().zone();
        Instant maintenant = horloge.instant();
        return rdvs.commencesEntre(StatutRdv.CONFIRME, minuit(maintenant, zone), maintenant).stream()
                .map(r -> RdvVueAdmin.de(r, zone, maintenant))
                .toList();
    }

    /**
     * Rendez-vous confirmés d'un jour antérieur que personne n'a marqués honorés ni
     * absents. Séparés de ceux du jour pour qu'un oubli ancien ne se lise pas comme un
     * client attendu aujourd'hui.
     */
    public List<RdvVueAdmin> rendezVousPassesSansIssue() {
        ZoneId zone = parametres.courants().zone();
        Instant maintenant = horloge.instant();
        return rdvs.commencesAvant(StatutRdv.CONFIRME, minuit(maintenant, zone)).stream()
                .map(r -> RdvVueAdmin.de(r, zone, maintenant))
                .toList();
    }

    /**
     * Rendez-vous non terminés, d'aujourd'hui à n'importe quelle date, qui tombent un
     * jour férié légal dans le fuseau de l'atelier. Un jour férié n'est pas un geste du
     * garage : aucune action ne produit ce conflit, d'où une liste permanente plutôt
     * qu'un avertissement au moment d'une modification. Le garage déplace ou annule
     * depuis le détail. Filtrés en mémoire : les jours fériés sont calculés, pas stockés.
     */
    public List<RdvVueAdmin> rendezVousJoursFeries() {
        ZoneId zone = parametres.courants().zone();
        Instant maintenant = horloge.instant();
        return rdvs.nonTerminesDepuis(minuit(maintenant, zone)).stream()
                .filter(r -> JoursFeries.estFerie(LocalDate.ofInstant(r.getDebut(), zone)))
                .map(r -> RdvVueAdmin.de(r, zone, maintenant))
                .toList();
    }

    /**
     * Agenda du back-office : tous les statuts, du jour {@code du} au jour {@code au}
     * compris, dans le fuseau de l'atelier. Sans borne, la période va d'aujourd'hui à
     * {@value #HORIZON_AGENDA_JOURS} jours plus tard. Une fin antérieure au début ne
     * lève rien : la page le signale et n'affiche aucun rendez-vous.
     */
    public AgendaAdmin agenda(LocalDate du, LocalDate au) {
        ZoneId zone = parametres.courants().zone();
        Instant maintenant = horloge.instant();
        LocalDate debut = du != null ? du : LocalDate.ofInstant(maintenant, zone);
        LocalDate fin = au != null ? au : debut.plusDays(HORIZON_AGENDA_JOURS);
        if (fin.isBefore(debut)) {
            return new AgendaAdmin(debut, fin, true, List.of());
        }
        List<RdvVueAdmin> vues = rdvs.agenda(
                        debut.atStartOfDay(zone).toInstant(),
                        fin.plusDays(1).atStartOfDay(zone).toInstant()).stream()
                .map(r -> RdvVueAdmin.de(r, zone, maintenant))
                .toList();
        return new AgendaAdmin(debut, fin, false, vues);
    }

    /** Période de l'historique, dans les jours de l'atelier : les trente derniers jours sans date saisie. */
    public PeriodeFiltre periodeHistorique(LocalDate du, LocalDate au) {
        return PeriodeFiltre.de(du, au, aujourdhui());
    }

    /**
     * Historique des rendez-vous, tous statuts ou un seul, du plus récent au plus ancien,
     * par page. L'agenda montre une période à venir sans pagination, les listes de travail
     * ce qui attend une décision : l'historique est la seule liste qui grossit sans fin.
     * Une période inversée rend une page vide, que l'écran signale.
     */
    public Page<RdvVueAdmin> historique(StatutRdv statut, PeriodeFiltre periode, Pageable pagination) {
        if (periode.inversee()) {
            return Page.empty(pagination);
        }
        ZoneId zone = parametres.courants().zone();
        Instant maintenant = horloge.instant();
        Set<StatutRdv> statuts = statut == null ? EnumSet.allOf(StatutRdv.class) : EnumSet.of(statut);
        Page<Rdv> resultats = rdvs.historique(statuts, periode.debut(zone), periode.finExclue(zone), pagination);
        if (resultats.hasContent()) {
            rdvs.chargerLignes(resultats.getContent());
        }
        return resultats.map(r -> RdvVueAdmin.de(r, zone, maintenant));
    }

    /**
     * Vue détaillée d'un rendez-vous, pour l'écran de détail et le contexte des écrans
     * de refus et d'annulation par le garage. {@link RessourceIntrouvableException} si absent.
     * On charge le RDV avant de consulter les parametres : sur reference inconnue,
     * l exception remonte immediatement sans requete inutile en base.
     */
    public RdvVueAdmin vue(UUID reference) {
        Rdv rdv = charger(reference);
        return RdvVueAdmin.de(rdv, parametres.courants().zone(), horloge.instant());
    }

    // --- transitions ------------------------------------------------------------------

    @Transactional
    public Rdv confirmer(UUID reference) {
        Rdv rdv = charger(reference);
        Instant maintenant = horloge.instant();
        rdv.confirmer(maintenant);
        // La production du fichier d agenda est DANS le lambda absorbe par
        // notifierSansEchouer : une exception du redacteur iCalendar ne doit pas plus
        // annuler la confirmation qu une panne du fournisseur de courriel. Le
        // rendez-vous est deja confirme en base a ce point.
        return ecrire(rdv, enregistre -> {
            notifierSansEchouer(() -> courriel.envoyerConfirmationRdv(enregistre.getMembre(),
                    detailsPour(enregistre), agendaPour(enregistre)));
            if (enregistre.confirmationTientLieuDeRappel(maintenant, parametres.courants().delaiRappel())) {
                rdvs.reserverRappel(enregistre.getId(), maintenant);
            }
        });
    }

    @Transactional
    public Rdv refuser(UUID reference, String motif) {
        Rdv rdv = charger(reference);
        rdv.refuser(motif, horloge.instant());
        return ecrire(rdv, enregistre -> notifierSansEchouer(
                () -> courriel.envoyerRefusRdv(enregistre.getMembre(), detailsPour(enregistre), motif)));
    }

    @Transactional
    public Rdv annulerParLeGarage(UUID reference, String motif) {
        Rdv rdv = charger(reference);
        rdv.annulerParLeGarage(motif, horloge.instant());
        return ecrire(rdv, enregistre -> notifierSansEchouer(
                () -> courriel.envoyerAnnulationParLeGarage(enregistre.getMembre(), detailsPour(enregistre), motif)));
    }

    @Transactional
    public Rdv marquerHonore(UUID reference) {
        Rdv rdv = charger(reference);
        rdv.marquerHonore(horloge.instant());
        // Cree l intervention correspondante DANS la meme transaction : le passage
        // HONORE et l existence de l intervention sont atomiques. La methode
        // creerDepuisRdv est idempotente : si une intervention existe deja, elle
        // est reutilisee sans doublon.
        return ecrire(rdv, enregistre -> interventions.creerDepuisRdv(enregistre));
    }

    @Transactional
    public Rdv marquerAbsent(UUID reference) {
        Rdv rdv = charger(reference);
        rdv.marquerAbsent(horloge.instant());
        return ecrire(rdv, ignore -> { /* cloture interne, pas de notification */ });
    }

    // --- déplacement par le garage -------------------------------------------------

    /**
     * Le garage déplace un rendez-vous, motif obligatoire. Le nouveau créneau suit les
     * plages, les jours fériés, les indisponibilités et la contrainte d'exclusion, sans le
     * délai minimal, l'horizon ni le temps de réponse qui protègent l'atelier contre une
     * demande du membre. Le rendez-vous en sort confirmé, modifié en place.
     *
     * <p>{@code versionLue} est la version affichée à l'administrateur : un écart dit
     * qu'un autre geste a modifié le rendez-vous depuis l'ouverture de la page.</p>
     */
    @Transactional
    public Rdv deplacer(UUID reference, Instant nouveauDebut, String motif, long versionLue) {
        Rdv rdv = charger(reference);
        if (rdv.getVersion() != versionLue) {
            throw new ConflitConcurrenceException(
                    "Ce rendez-vous a été mis à jour entre-temps, rechargez la page.");
        }
        Instant maintenant = horloge.instant();
        Instant fin = nouveauDebut.plus(rdv.duree());
        if (!disponibilites.estReservable(nouveauDebut, fin, ReglesDeCreneau.GARAGE)) {
            throw new CreneauIndisponibleException(CreneauIndisponibleException.NON_OUVERT, "Ce créneau n'est pas ouvert.");
        }
        PosteAtelier poste = disponibilites.premierPosteLibre(nouveauDebut, fin, rdv)
                .orElseThrow(() -> new CreneauIndisponibleException(
                        CreneauIndisponibleException.AUCUN_POSTE, "Aucun poste libre sur ce créneau."));

        Instant ancienDebut = rdv.getDebut();
        rdv.modifierParLeGarage(nouveauDebut, poste, motif, maintenant);
        Rdv enregistre = ecrireDeplacement(rdv, enregistreRdv -> {
            // Le rappel et la relance portaient sur l'ancien créneau.
            rdvs.remettreRappelEtRelanceAZero(enregistreRdv.getId());
            if (enregistreRdv.confirmationTientLieuDeRappel(maintenant, parametres.courants().delaiRappel())) {
                rdvs.reserverRappel(enregistreRdv.getId(), maintenant);
            }
            ZoneId zone = parametres.courants().zone();
            DetailsRdvCourriel ancien = new DetailsRdvCourriel(enregistreRdv.getNumero(), ancienDebut.atZone(zone));
            notifierSansEchouer(() -> courriel.envoyerModificationParLeGarage(enregistreRdv.getMembre(),
                    ancien, detailsPour(enregistreRdv), motif.trim(), garage.telephone(),
                    agendaPour(enregistreRdv)));
        });
        log.info("Rendez-vous {} deplace par le garage de {} a {}, poste {}, motif : {}",
                enregistre.getNumero(), ancienDebut, nouveauDebut, poste.getLibelle(), motif.trim());
        return enregistre;
    }

    /**
     * Écrit le déplacement par le chemin commun des transitions, et traduit en créneau
     * pris les deux issues par lesquelles PostgreSQL écarte un recoupement concurrent.
     */
    private Rdv ecrireDeplacement(Rdv rdv, Consumer<Rdv> apresEcriture) {
        try {
            return ecrire(rdv, apresEcriture, new RdvDeplaceEvent(rdv.getReference()));
        } catch (DataIntegrityViolationException e) {
            if (!EtatSql.estUneExclusion(e)) {
                throw e;
            }
            throw new CreneauIndisponibleException(CreneauIndisponibleException.PRIS,
                    "Ce créneau vient d'être pris. Choisissez-en un autre.", e);
        } catch (DataAccessException e) {
            if (!EtatSql.estUnInterblocage(e)) {
                throw e;
            }
            throw new CreneauIndisponibleException(CreneauIndisponibleException.PRIS,
                    "Ce créneau vient d'être pris. Choisissez-en un autre.", e);
        }
    }

    /** Heures où le garage peut déplacer le rendez-vous un jour donné, sur sa durée exacte. */
    public List<CreneauVue> creneauxPourDeplacer(UUID reference, LocalDate jour) {
        Rdv rdv = charger(reference);
        if (jour == null) {
            return List.of();
        }
        ZoneId zone = parametres.courants().zone();
        return disponibilites.creneauxPourDeplacer(rdv, jour, ReglesDeCreneau.GARAGE).stream()
                .map(c -> new CreneauVue(
                        c.debut().toString(),
                        FormatageRdv.heureLisible(c.debut(), zone) + " - " + FormatageRdv.heureLisible(c.fin(), zone),
                        c.postesLibres()))
                .toList();
    }

    /** Version du rendez-vous, renvoyée par le formulaire de déplacement. */
    public long version(UUID reference) {
        return charger(reference).getVersion();
    }

    /** Jour proposé à l'ouverture de l'écran : celui du rendez-vous, s'il est à venir. */
    public LocalDate aujourdhui() {
        return LocalDate.ofInstant(horloge.instant(), parametres.courants().zone());
    }

    // --- helpers privees --------------------------------------------------------------

    private static Instant minuit(Instant maintenant, ZoneId zone) {
        return LocalDate.ofInstant(maintenant, zone).atStartOfDay(zone).toInstant();
    }

    private Rdv charger(UUID reference) {
        return rdvs.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Rdv", reference));
    }

    /**
     * Flush explicite pour observer l {@link OptimisticLockingFailureException} ici et
     * la traduire, plutot qu a la fermeture de transaction ou l on ne pourrait plus le
     * faire. La notification s execute apres ecriture reussie.
     *
     * <p>Point d ecriture <b>unique</b> des transitions : c est donc ici, et nulle part
     * ailleurs, qu est publie {@link RdvStatutModifieEvent} (BL-6). Toute transition
     * ajoutee plus tard a la machine a etats notifiera le membre sans qu on ait a y
     * penser : l oubli n est pas possible. L evenement part meme si le courriel a
     * echoue : les deux canaux sont independants, et {@code notifierSansEchouer} a deja
     * absorbe l echec avant ce point.</p>
     *
     * <p>Seul le déplacement publie un autre événement, {@link RdvDeplaceEvent} : il
     * laisse le rendez-vous confirmé, et l'événement de statut annoncerait au membre
     * une confirmation au lieu d'un déplacement.</p>
     */
    private Rdv ecrire(Rdv rdv, Consumer<Rdv> apresEcriture) {
        return ecrire(rdv, apresEcriture, new RdvStatutModifieEvent(rdv.getReference()));
    }

    private Rdv ecrire(Rdv rdv, Consumer<Rdv> apresEcriture, Object evenement) {
        try {
            Rdv enregistre = rdvs.saveAndFlush(rdv);
            apresEcriture.accept(enregistre);
            evenements.publishEvent(evenement);
            return enregistre;
        } catch (OptimisticLockingFailureException e) {
            throw new ConflitConcurrenceException(
                    "Ce rendez-vous a été mis à jour par un autre administrateur, rechargez la page.");
        }
    }

    /**
     * Fichier iCalendar joint a la confirmation (F38). Produit ici et non dans le
     * module {@code communication}, qui ignore tout des rendez-vous : il ne recoit
     * que des octets et un nom de fichier.
     *
     * <p>Un echec rend {@code null} au lieu de remonter. La hierarchie est explicite :
     * la confirmation est l information que le membre attend, le fichier d agenda en
     * est le confort. Laisser l exception remonter la ferait absorber un cran plus
     * haut par {@code notifierSansEchouer} : la transition survivrait, mais le
     * courriel ne partirait pas du tout, c est-a-dire qu on priverait le membre de
     * l essentiel faute d accessoire.</p>
     */
    private PieceJointeCourriel agendaPour(Rdv rdv) {
        try {
            FichierAgenda agenda = exportAgenda.pourLeCourriel(rdv);
            return new PieceJointeCourriel(agenda.nomFichier(), FichierAgenda.TYPE_MIME, agenda.octets());
        } catch (RuntimeException e) {
            log.warn("Fichier d agenda non produit pour {}, la confirmation part sans piece jointe",
                    rdv.getNumero(), e);
            return null;
        }
    }

    private DetailsRdvCourriel detailsPour(Rdv rdv) {
        ZoneId zone = parametres.courants().zone();
        return new DetailsRdvCourriel(rdv.getNumero(), rdv.getDebut().atZone(zone));
    }

    /**
     * Le courriel part apres la validation de la transaction ({@code EnvoiApresCommit}),
     * et un echec de remise y est journalise sans remonter. Reste ici la redaction, qui
     * se fait dans la transaction : une erreur a cette etape ne doit pas annuler une
     * transition deja decidee, le courriel etant informationnel.
     */
    private void notifierSansEchouer(Runnable envoi) {
        try {
            envoi.run();
        } catch (RuntimeException e) {
            log.warn("Notification courriel echouee, la transition RDV commit malgre tout", e);
        }
    }
}
