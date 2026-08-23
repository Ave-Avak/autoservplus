package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.DetailsRdvCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.reservation.web.dto.RdvVueAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Actions administratives sur les rendez-vous : confirmation, refus, annulation par
 * le garage, cloture (honore/absent), plus les deux vues du tableau de bord.
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

    private final RdvRepository rdvs;
    private final ParametreAtelierRepository parametres;
    private final ServiceCourriel courriel;
    private final InterventionService interventions;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    public AdminRdvService(RdvRepository rdvs, ParametreAtelierRepository parametres,
                           ServiceCourriel courriel, InterventionService interventions,
                           ApplicationEventPublisher evenements,
                           Clock horloge) {
        this.rdvs = rdvs;
        this.parametres = parametres;
        this.courriel = courriel;
        this.interventions = interventions;
        this.evenements = evenements;
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
     * Rendez-vous confirmes deja commences (debut &lt;= maintenant), a traiter
     * par le garage : marquer honore des l accueil du client, ou absent une fois
     * le creneau ecoule (le flag correspondant sur {@link RdvVueAdmin} exige
     * respectivement debut atteint et fin passee).
     */
    public List<RdvVueAdmin> rendezVousATraiter() {
        ZoneId zone = parametres.courants().zone();
        var maintenant = horloge.instant();
        return rdvs.findATraiter(StatutRdv.CONFIRME, maintenant).stream()
                .map(r -> RdvVueAdmin.de(r, zone, maintenant))
                .toList();
    }

    /**
     * Vue detaillee d un rendez-vous, pour le contexte des ecrans de refus et
     * d annulation par le garage. {@link RessourceIntrouvableException} si absent.
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
        rdv.confirmer();
        return ecrire(rdv, enregistre -> notifierSansEchouer(
                () -> courriel.envoyerConfirmationRdv(enregistre.getMembre(), detailsPour(enregistre))));
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
        // Garde temporelle avant transition : on n'accueille pas un client avant
        // l'heure de debut du RDV. Symetrique du filtre d'affichage
        // (findATraiter WHERE debut <= maintenant) : les deux seuils se
        // correspondent, un POST direct hors-delai est rejete au meme point.
        if (rdv.getDebut().isAfter(horloge.instant())) {
            throw new RegleMetierException(
                    "Un rendez-vous ne peut être marqué honoré avant l'heure de début.");
        }
        rdv.marquerHonore();
        // Cree l intervention correspondante DANS la meme transaction : le passage
        // HONORE et l existence de l intervention sont atomiques. La methode
        // creerDepuisRdv est idempotente : si une intervention existe deja, elle
        // est reutilisee sans doublon.
        return ecrire(rdv, enregistre -> interventions.creerDepuisRdv(enregistre));
    }

    @Transactional
    public Rdv marquerAbsent(UUID reference) {
        Rdv rdv = charger(reference);
        // Garde temporelle : on ne declare pas absent tant que le creneau n'est
        // pas ecoule, le client peut encore arriver en retard. Refus si
        // fin >= maintenant. Symetrique du flag d'affichage peutMarquerAbsent
        // (fin < maintenant).
        if (!rdv.getFin().isBefore(horloge.instant())) {
            throw new RegleMetierException(
                    "Un rendez-vous ne peut être marqué absent avant la fin de son créneau.");
        }
        rdv.marquerAbsent();
        return ecrire(rdv, ignore -> { /* cloture interne, pas de notification */ });
    }

    // --- helpers privees --------------------------------------------------------------

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
     * penser — l oubli n est pas possible. L evenement part meme si le courriel a
     * echoue : les deux canaux sont independants, et {@code notifierSansEchouer} a deja
     * absorbe l echec avant ce point.</p>
     */
    private Rdv ecrire(Rdv rdv, Consumer<Rdv> apresEcriture) {
        try {
            Rdv enregistre = rdvs.saveAndFlush(rdv);
            apresEcriture.accept(enregistre);
            evenements.publishEvent(new RdvStatutModifieEvent(enregistre.getReference()));
            return enregistre;
        } catch (OptimisticLockingFailureException e) {
            throw new ConflitConcurrenceException(
                    "Ce rendez-vous a été mis à jour par un autre administrateur, rechargez la page.");
        }
    }

    private DetailsRdvCourriel detailsPour(Rdv rdv) {
        ZoneId zone = parametres.courants().zone();
        return new DetailsRdvCourriel(
                rdv.getNumero(),
                FormatageRdv.jourLisible(rdv.getDebut(), zone),
                FormatageRdv.heureLisible(rdv.getDebut(), zone));
    }

    /**
     * Envoie le courriel dans la transaction courante mais absorbe toute exception
     * du fournisseur : la transition RDV, deja persistee et flushee, doit rester en
     * base meme si Brevo est indisponible. La divergence vs {@code InscriptionService}
     * (qui rollback si le mail jette) est assumee : ici le mail est informationnel.
     */
    private void notifierSansEchouer(Runnable envoi) {
        try {
            envoi.run();
        } catch (RuntimeException e) {
            log.warn("Notification courriel echouee, la transition RDV commit malgre tout", e);
        }
    }
}
