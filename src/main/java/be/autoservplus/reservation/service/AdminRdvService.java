package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.DetailsRdvCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.reservation.web.dto.RdvVueAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Clock horloge;

    public AdminRdvService(RdvRepository rdvs, ParametreAtelierRepository parametres,
                           ServiceCourriel courriel, Clock horloge) {
        this.rdvs = rdvs;
        this.parametres = parametres;
        this.courriel = courriel;
        this.horloge = horloge;
    }

    // --- tableau de bord --------------------------------------------------------------

    /** Demandes en attente de decision par le garage, du plus ancien au plus recent. */
    public List<RdvVueAdmin> demandesEnAttente() {
        ZoneId zone = parametres.courants().zone();
        return rdvs.findByStatutOrderByDebut(StatutRdv.EN_ATTENTE).stream()
                .map(r -> RdvVueAdmin.de(r, zone))
                .toList();
    }

    /** Rendez-vous confirmes dont l heure de fin est passee, a cloturer honore/absent. */
    public List<RdvVueAdmin> aTraiterApresRdv() {
        ZoneId zone = parametres.courants().zone();
        return rdvs.findByStatutAndFinBeforeOrderByDebut(StatutRdv.CONFIRME, horloge.instant()).stream()
                .map(r -> RdvVueAdmin.de(r, zone))
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
        return RdvVueAdmin.de(rdv, parametres.courants().zone());
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
        rdv.marquerHonore();
        return ecrire(rdv, ignore -> { /* cloture interne, pas de notification */ });
    }

    @Transactional
    public Rdv marquerAbsent(UUID reference) {
        Rdv rdv = charger(reference);
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
     */
    private Rdv ecrire(Rdv rdv, Consumer<Rdv> apresEcriture) {
        try {
            Rdv enregistre = rdvs.saveAndFlush(rdv);
            apresEcriture.accept(enregistre);
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
