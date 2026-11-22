package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Réservations et transitions simultanées d'un rendez-vous, sur deux fils réels.
 *
 * <p>Les autres tests prouvent les gardes en séquence : un second appel voit le premier
 * déjà validé. Ici, chaque fil a sa propre transaction et les deux partent au même
 * signal. Deux mécanismes sont en jeu, et chacun a son test. La création concurrente
 * est arrêtée par la contrainte d'exclusion {@code ex_rdv_poste_intervalle} : un
 * verrou de version ne protège pas une insertion, faute de ligne à comparer. Les
 * transitions concurrentes sont départagées par {@code @Version} sur {@link Rdv}.</p>
 *
 * <p>Un départ commun ne garantit pas que deux transactions se croisent. Chaque
 * mécanisme a donc aussi un test dont l'ordre est forcé par des loquets, et les tests
 * au départ commun admettent l'issue où le perdant arrive après la validation.</p>
 *
 * <p>La classe n'est pas transactionnelle : les écritures sont validées pour de bon,
 * dans la base propre à la classe. Chaque test prend un jour différent pour ne pas
 * dépendre des autres.</p>
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(RdvConcurrenceIT.HorlogeFixe.class)
@DisplayName("Rendez-vous : réservations et transitions simultanées (intégration)")
class RdvConcurrenceIT extends SocleIntegration {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    // Samedi 12 septembre 2026, 09:00. Premier créneau réservable : dimanche 09:00.
    private static final Instant INSTANT_FIGE =
            LocalDate.of(2026, 9, 12).atTime(9, 0).atZone(BRUXELLES).toInstant();

    @TestConfiguration
    static class HorlogeFixe {
        @Bean
        @Primary
        Clock horlogeFigee() {
            return Clock.fixed(INSTANT_FIGE, BRUXELLES);
        }
    }

    @Autowired private RdvService reservations;
    @Autowired private AdminRdvService adminRdvs;
    @Autowired private RdvRepository rdvs;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private PlageOuvertureRepository plages;
    @Autowired private ParametreAtelierRepository parametres;
    @Autowired private TransactionTemplate transaction;
    @Autowired private JdbcTemplate jdbc;

    private Utilisateur marie;
    private Utilisateur paul;
    private Vehicule golf;
    private Vehicule clio;
    private PosteAtelier pont;
    private Prestation vidange;

    /**
     * Un seul poste et des plages de 08:00 à 18:00 tous les jours : deux demandes du
     * même créneau visent forcément le même poste. Les graines de plages et de postes
     * sont retirées pour de bon, la base n'appartient qu'à cette classe.
     */
    @BeforeAll
    void atelierAUnPoste() {
        plages.deleteAllInBatch();
        postes.deleteAllInBatch();
        pont = postes.save(new PosteAtelier("Pont unique"));
        for (DayOfWeek jour : DayOfWeek.values()) {
            plages.save(new PlageOuverture(jour, LocalTime.of(8, 0), LocalTime.of(18, 0)));
        }
        marie = utilisateurs.save(new Utilisateur("marie.concurrence@exemple.be", "$2a$12$h",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        paul = utilisateurs.save(new Utilisateur("paul.concurrence@exemple.be", "$2a$12$h",
                "Martin", "Paul", TypeUtilisateur.MEMBRE));
        golf = vehicules.save(new Vehicule(marie, "1-CON-001", "VW", "Golf", Motorisation.DIESEL));
        clio = vehicules.save(new Vehicule(paul, "1-CON-002", "Renault", "Clio", Motorisation.ESSENCE));
        Categorie entretien = categories.save(new Categorie("IT-CON", "Entretien", TypeCategorie.SERVICE));
        vidange = prestations.save(new Prestation(entretien, "IT-CON-VID", "Vidange", new BigDecimal("49.00"), 60));
        // Mode manuel : les transitions testées partent d'une demande EN_ATTENTE.
        ParametreAtelier p = parametres.courants();
        p.modifier(p.getFuseauHoraire(), p.getPasMinutes(), p.getTamponMinutes(), p.getDelaiMinimalHeures(),
                p.getHorizonJours(), p.getDelaiAnnulationHeures(), false, p.getMaxRdvEnAttenteParMembre());
        parametres.saveAndFlush(p);
    }

    @AfterEach
    void sansIdentite() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("deux membres réservent le même créneau du seul poste : une demande passe, l'autre est refusée")
    void deuxReservationsDuMemeCreneau() throws Exception {
        Instant dimanche10h = LocalDate.of(2026, 9, 13).atTime(10, 0).atZone(BRUXELLES).toInstant();
        List<UUID> prestationsChoisies = List.of(vidange.getReference());

        List<Object> issues = enMemeTemps(
                () -> reservations.reserver(marie.getEmail(), golf.getReference(), prestationsChoisies,
                        dimanche10h, null),
                () -> reservations.reserver(paul.getEmail(), clio.getReference(), prestationsChoisies,
                        dimanche10h, null));

        // PostgreSQL écarte le perdant par une violation d'exclusion (23P01) ou, si les deux
        // insertions se sont vues avant validation, par un interblocage (40P01) : constaté
        // en CI. Les deux issues sont traduites en créneau pris. Troisième issue, constatée
        // en CI elle aussi : le départ commun ne garantit pas que les deux transactions se
        // croisent. Si le perdant ne commence qu'après la validation du gagnant, il lit le
        // poste occupé et le service refuse avant toute insertion, sans état SQL. C'est le
        // refus ordinaire d'un créneau pris, et non un défaut. La contrainte, elle, est
        // prouvée sans hasard par insertionsCroiseesSurLeMemePoste.
        assertThat(issues).filteredOn(Rdv.class::isInstance).hasSize(1);
        assertThat(issues).filteredOn(CreneauIndisponibleException.class::isInstance).singleElement()
                .extracting(perdant -> EtatSql.de((Throwable) perdant))
                .isIn(Arrays.asList(EtatSql.EXCLUSION, EtatSql.INTERBLOCAGE, null));
        assertThat(rdvs.actifsChevauchant(dimanche10h, dimanche10h.plusSeconds(3600),
                List.of(StatutRdv.EN_ATTENTE, StatutRdv.CONFIRME)))
                .as("un seul rendez-vous occupe le poste à cette heure")
                .hasSize(1);
    }

    @Test
    @DisplayName("deux transactions insèrent sur le même poste : la seconde attend la première, puis la base la refuse")
    void insertionsCroiseesSurLeMemePoste() throws Exception {
        Instant mercredi10h = LocalDate.of(2026, 9, 16).atTime(10, 0).atZone(BRUXELLES).toInstant();

        List<Object> issues = insertionsCroisees(mercredi10h, pont);

        assertThat(issues.get(0)).isInstanceOf(Rdv.class);
        assertThat(issues.get(1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(EtatSql.de((Throwable) issues.get(1))).isIn(EtatSql.EXCLUSION, EtatSql.INTERBLOCAGE);
        assertThat(rdvs.actifsChevauchant(mercredi10h, mercredi10h.plusSeconds(3600),
                List.of(StatutRdv.EN_ATTENTE, StatutRdv.CONFIRME)))
                .as("un seul rendez-vous occupe le poste à cette heure")
                .hasSize(1);
    }

    @Test
    @DisplayName("deux transactions insèrent à la même heure sur deux postes : les deux passent")
    void insertionsCroiseesSurDeuxPostes() throws Exception {
        Instant jeudi10h = LocalDate.of(2026, 9, 17).atTime(10, 0).atZone(BRUXELLES).toInstant();
        // Poste inactif : le service ne le propose pas, les autres tests gardent leur poste
        // unique. La contrainte ne lit pas ce drapeau.
        PosteAtelier temoin = new PosteAtelier("Pont témoin");
        temoin.desactiver();
        postes.save(temoin);

        List<Object> issues = insertionsCroisees(jeudi10h, temoin);

        // Témoin du test précédent : le même scénario passe dès que le poste diffère. Le
        // refus vient donc de la contrainte, et non du montage à deux transactions.
        assertThat(issues).allSatisfy(issue -> assertThat(issue).isInstanceOf(Rdv.class));
    }

    @Test
    @DisplayName("une transition écrite sur une version périmée est refusée, la première reste")
    void transitionSurVersionPerimee() throws Exception {
        UUID reference = demande(LocalDate.of(2026, 9, 14).atTime(10, 0));
        CountDownLatch lu = new CountDownLatch(1);
        CountDownLatch ecrit = new CountDownLatch(1);

        // Le premier fil lit le rendez-vous EN_ATTENTE, puis attend que le second l'ait
        // confirmé et validé avant de le refuser à son tour, depuis sa lecture ancienne.
        // L'ordre est forcé : c'est bien le verrou de version qui tranche, et non la
        // machine à états, qui aurait refusé la transition sur une lecture fraîche.
        List<Object> issues = enParallele(
                () -> transaction.execute(statut -> {
                    rdvs.findByReference(reference).orElseThrow();
                    lu.countDown();
                    attendre(ecrit);
                    return adminRdvs.refuser(reference, "Atelier complet");
                }),
                () -> {
                    attendre(lu);
                    try {
                        return adminRdvs.confirmer(reference);
                    } finally {
                        ecrit.countDown();
                    }
                });

        assertThat(issues.get(0)).isInstanceOf(ConflitConcurrenceException.class);
        assertThat(issues.get(1)).isInstanceOf(Rdv.class);
        Rdv relu = rdvs.findByReference(reference).orElseThrow();
        assertThat(relu.getStatut()).isEqualTo(StatutRdv.CONFIRME);
        assertThat(relu.getMotifRefus()).isNull();
    }

    @Test
    @DisplayName("confirmer et refuser le même rendez-vous en même temps : une seule décision s'applique")
    void confirmationEtRefusSimultanes() throws Exception {
        UUID reference = demande(LocalDate.of(2026, 9, 15).atTime(10, 0));

        List<Object> issues = enMemeTemps(
                () -> adminRdvs.confirmer(reference),
                () -> adminRdvs.refuser(reference, "Atelier complet"));

        // Le perdant échoue au verrou de version s'il a lu avant l'écriture du gagnant,
        // sur la machine à états s'il a lu après : dans les deux cas, rien n'est écrit.
        List<Object> echecs = issues.stream().filter(issue -> !(issue instanceof Rdv)).toList();
        assertThat(issues).filteredOn(Rdv.class::isInstance).hasSize(1);
        assertThat(echecs).hasSize(1);
        assertThat(echecs.get(0)).isInstanceOfAny(ConflitConcurrenceException.class, IllegalStateException.class);

        Rdv gagnant = (Rdv) issues.stream().filter(Rdv.class::isInstance).findFirst().orElseThrow();
        Rdv relu = rdvs.findByReference(reference).orElseThrow();
        assertThat(relu.getStatut()).isEqualTo(gagnant.getStatut());
        assertThat(relu.getVersion()).as("une seule écriture").isEqualTo(1L);
    }

    // --- outillage ---------------------------------------------------------------------

    private UUID demande(java.time.LocalDateTime debutLocal) {
        Instant debut = debutLocal.atZone(BRUXELLES).toInstant();
        return reservations.reserver(marie.getEmail(), golf.getReference(),
                List.of(vidange.getReference()), debut, null).getReference();
    }

    /**
     * Deux insertions de rendez-vous qui se chevauchent, écrites sans passer par le
     * service, dont le calcul des disponibilités aurait écarté la seconde. La première
     * transaction insère sur le pont puis retient sa validation. La seconde insère alors
     * sur le poste donné : sur le même poste, PostgreSQL la fait attendre le sort de la
     * première. La première n'est libérée qu'une fois cette attente vue dans
     * {@code pg_locks}, ou la seconde terminée : l'ordre ne dépend d'aucun délai.
     *
     * @return l'issue de la première transaction, puis celle de la seconde
     */
    private List<Object> insertionsCroisees(Instant debut, PosteAtelier posteDuSecond) throws Exception {
        Duration pas = Duration.ofMinutes(30);
        CountDownLatch insere = new CountDownLatch(1);
        CountDownLatch valider = new CountDownLatch(1);
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        ExecutorService fils = Executors.newFixedThreadPool(2);
        try {
            Future<Object> premiere = fils.submit(() -> transaction.execute(statut -> {
                Rdv rdv = rdvs.saveAndFlush(new Rdv("RDV-CON-A-" + suffixe, marie, golf, pont, debut, pas,
                        List.of(vidange), null));
                insere.countDown();
                attendre(valider);
                return rdv;
            }));
            Future<Object> seconde = fils.submit(() -> {
                attendre(insere);
                return transaction.execute(statut -> rdvs.saveAndFlush(new Rdv("RDV-CON-B-" + suffixe, paul, clio,
                        posteDuSecond, debut.plus(pas), pas, List.of(vidange), null)));
            });
            try {
                attendre(insere);
                Instant limite = Instant.now().plusSeconds(30);
                while (!seconde.isDone() && !uneRequeteAttendUnVerrou()) {
                    if (Instant.now().isAfter(limite)) {
                        throw new IllegalStateException("La seconde insertion n'a ni fini ni attendu en trente secondes");
                    }
                    Thread.sleep(20);
                }
            } finally {
                valider.countDown();
            }
            return List.of(issue(premiere), issue(seconde));
        } finally {
            fils.shutdownNow();
        }
    }

    /** La base n'appartient qu'à cette classe : une attente de verrou ne peut venir que du test. */
    private boolean uneRequeteAttendUnVerrou() {
        Integer enAttente = jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Integer.class);
        return enAttente != null && enAttente > 0;
    }

    private static Object issue(Future<?> futur) throws Exception {
        try {
            return futur.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    /** Les deux actions partent au même signal. */
    @SafeVarargs
    private List<Object> enMemeTemps(Callable<?>... actions) throws Exception {
        CountDownLatch depart = new CountDownLatch(1);
        List<Callable<?>> retenues = new ArrayList<>();
        for (Callable<?> action : actions) {
            retenues.add(() -> {
                attendre(depart);
                return action.call();
            });
        }
        return executer(retenues, depart);
    }

    /** Les actions ordonnent elles-mêmes leurs étapes par des loquets. */
    @SafeVarargs
    private List<Object> enParallele(Callable<?>... actions) throws Exception {
        return executer(List.of(actions), null);
    }

    /**
     * Chaque fil reçoit l'identité d'un administrateur : {@link AdminRdvService} est
     * gardé par {@code @PreAuthorize}, et le contexte de sécurité ne passe pas d'un fil
     * à l'autre. Rend, dans l'ordre des actions, le résultat ou l'exception levée.
     */
    private List<Object> executer(List<Callable<?>> actions, CountDownLatch depart) throws Exception {
        ExecutorService fils = Executors.newFixedThreadPool(actions.size());
        try {
            List<Future<?>> futurs = new ArrayList<>();
            for (Callable<?> action : actions) {
                futurs.add(fils.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated("admin@garage.be", null,
                                    List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATEUR"))));
                    try {
                        return action.call();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            if (depart != null) {
                depart.countDown();
            }
            List<Object> issues = new ArrayList<>();
            for (Future<?> futur : futurs) {
                try {
                    issues.add(futur.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    issues.add(e.getCause());
                }
            }
            return issues;
        } finally {
            fils.shutdownNow();
        }
    }

    private static void attendre(CountDownLatch loquet) {
        try {
            if (!loquet.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Loquet non libéré en trente secondes");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
