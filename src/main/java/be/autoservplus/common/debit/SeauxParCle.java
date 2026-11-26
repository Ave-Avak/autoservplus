package be.autoservplus.common.debit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.local.LocalBucketBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

/**
 * Un seau à jetons Bucket4j par clé (compte, IP, adresse hachée), en mémoire et en nombre
 * de clés borné.
 *
 * <p>Chaque limite remplit son seau en continu ({@code refillGreedy}) : cinq demandes par
 * quart d'heure rendent un jeton toutes les trois minutes. Sur un quart d'heure pris à
 * cheval, un client patient obtient au plus deux fois la limite, comme avec une fenêtre
 * fixe. Une fenêtre glissante exacte l'éviterait, au prix d'un horodatage gardé par demande :
 * elle reste réservée aux échecs de connexion ({@code GardeConnexion}), qui comptent des
 * échecs et non des demandes.</p>
 *
 * <p>Le nombre de clés est plafonné, sinon le compteur devient le déni de service qu'il
 * combat. Une clé sans demande depuis la plus longue période de ses limites a un seau plein,
 * donc équivalent à une clé absente : elle est retirée quand la carte atteint son plafond.
 * Si la carte reste pleine, la décision le dit ({@link Decision#saturee()}) et l'appelant
 * choisit ce qu'il protège. Le balayage parcourt toute la carte : il a lieu au plus une fois
 * par seconde, sinon une carte pleine coûterait un parcours complet à chaque requête.</p>
 *
 * <p>L'horloge est celle de l'application, pour que les tests avancent le temps sans
 * attendre. Compteurs par instance, perdus au redémarrage : le déploiement ne lance qu'une
 * instance.</p>
 */
public final class SeauxParCle {

    private final List<Limite> limites;
    private final Duration retention;
    private final int clesMax;
    private final Clock horloge;
    private final TimeMeter mesure;
    private final Map<String, Entree> seaux = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> prochainBalayage = new AtomicReference<>(Instant.MIN);

    static final Duration INTERVALLE_BALAYAGE = Duration.ofSeconds(1);

    public SeauxParCle(List<Limite> limites, int clesMax, Clock horloge) {
        if (limites.isEmpty()) {
            throw new IllegalArgumentException("Au moins une limite est requise");
        }
        this.limites = List.copyOf(limites);
        this.retention = limites.stream().map(Limite::periode)
                .max(Comparator.naturalOrder()).orElseThrow();
        this.clesMax = clesMax;
        this.horloge = Objects.requireNonNull(horloge);
        this.mesure = new MesureHorloge(horloge);
    }

    /** Consomme un jeton de la clé s'il en reste un. */
    public Decision consommer(String cle) {
        Instant maintenant = horloge.instant();
        if (!seaux.containsKey(cle) && seaux.size() >= clesMax) {
            balayerSiPermis(maintenant);
            if (seaux.size() >= clesMax) {
                return Decision.SATUREE;
            }
        }
        Entree entree = seaux.computeIfAbsent(cle, ignore -> new Entree(nouveauSeau()));
        entree.dernierUsage = maintenant;
        ConsumptionProbe sonde = entree.seau.tryConsumeAndReturnRemaining(1);
        return sonde.isConsumed()
                ? Decision.ACCEPTEE
                : Decision.refusee(Duration.ofNanos(sonde.getNanosToWaitForRefill()));
    }

    private void balayerSiPermis(Instant maintenant) {
        Instant prevu = prochainBalayage.get();
        if (maintenant.isBefore(prevu)
                || !prochainBalayage.compareAndSet(prevu, maintenant.plus(INTERVALLE_BALAYAGE))) {
            return;
        }
        seaux.values().removeIf(entree -> entree.inactiveDepuis(maintenant, retention));
    }

    int nombreDeCles() {
        return seaux.size();
    }

    private Bucket nouveauSeau() {
        LocalBucketBuilder constructeur = Bucket.builder().withCustomTimePrecision(mesure);
        for (Limite limite : limites) {
            constructeur.addLimit(bande -> bande.capacity(limite.nombre())
                    .refillGreedy(limite.nombre(), limite.periode()));
        }
        return constructeur.build();
    }

    /** Au plus {@code nombre} demandes par {@code periode}. */
    public record Limite(int nombre, Duration periode) {
        public Limite {
            if (nombre <= 0 || periode == null || periode.isZero() || periode.isNegative()) {
                throw new IllegalArgumentException("Limite invalide : " + nombre + " par " + periode);
            }
        }
    }

    /**
     * Issue d'une demande. {@code attente} n'a de sens que pour un refus : c'est le délai
     * annoncé par {@code Retry-After}.
     */
    public record Decision(boolean acceptee, boolean saturee, Duration attente) {
        static final Decision ACCEPTEE = new Decision(true, false, Duration.ZERO);
        static final Decision SATUREE = new Decision(false, true, Duration.ZERO);

        static Decision refusee(Duration attente) {
            return new Decision(false, false, attente);
        }
    }

    private static final class Entree {
        private final Bucket seau;
        private volatile Instant dernierUsage;

        private Entree(Bucket seau) {
            this.seau = seau;
        }

        private boolean inactiveDepuis(Instant maintenant, Duration retention) {
            return dernierUsage == null || !dernierUsage.plus(retention).isAfter(maintenant);
        }
    }

    /** Temps de Bucket4j lu sur l'horloge injectée, en nanosecondes depuis l'époque. */
    private record MesureHorloge(Clock horloge) implements TimeMeter {
        @Override
        public long currentTimeNanos() {
            Instant instant = horloge.instant();
            return TimeUnit.SECONDS.toNanos(instant.getEpochSecond()) + instant.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
