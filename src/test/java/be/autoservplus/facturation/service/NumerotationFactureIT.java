package be.autoservplus.facturation.service;

import be.autoservplus.facturation.repository.CompteurFactureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le coeur legal de F31 : la numerotation des factures est CONTINUE, sans trou.
 *
 * <p>Volontairement <b>sans</b> {@code @Transactional} de classe. Toute la propriete
 * a prouver tient dans le comportement transactionnel du compteur : un test qui
 * s executerait dans une transaction englobante rollbackee ne demontrerait rien, et
 * le test de concurrence exige deux transactions reellement committees, sur deux
 * connexions distinctes. Le nettoyage est donc explicite.</p>
 *
 * <p>Le generateur y est instancie a la main avec une horloge figee : l exercice
 * comptable est la donnee sous test, il doit etre choisi, pas subi. Le bean injecte
 * sert la ou c est son proxy transactionnel qui est verifie.</p>
 */
@SpringBootTest
@Testcontainers
@DisplayName("Numerotation continue des factures (integration)")
class NumerotationFactureIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    @Autowired private CompteurFactureRepository compteurs;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    /** Le bean reel, seul porteur du proxy transactionnel (voir refuseHorsTransaction). */
    @Autowired private GenerateurNumeroFacture generateurInjecte;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactions);
        // Aucune transaction englobante : les lignes committees par un test
        // survivraient au suivant.
        jdbc.update("DELETE FROM compteur_facture");
    }

    /**
     * Generateur a horloge choisie. Instancie a la main, donc <b>sans</b> le proxy
     * transactionnel de Spring : sans effet sur les tests qui suivent, tous executes
     * dans une transaction explicite. La garde {@code MANDATORY}, elle, se verifie
     * necessairement sur le bean injecte.
     */
    private GenerateurNumeroFacture generateurAu(String instant) {
        return new GenerateurNumeroFacture(compteurs,
                Clock.fixed(Instant.parse(instant), BRUXELLES));
    }

    /** Un numero tire dans sa propre transaction, committee. */
    private String numeroCommite(GenerateurNumeroFacture generateur) {
        return transaction.execute(statut -> generateur.prochain().valeur());
    }

    @Test
    @DisplayName("deux emissions successives portent des numeros consecutifs")
    void numerosConsecutifs() {
        GenerateurNumeroFacture generateur = generateurAu("2026-08-22T10:00:00Z");

        assertThat(numeroCommite(generateur)).isEqualTo("2026-0001");
        assertThat(numeroCommite(generateur)).isEqualTo("2026-0002");
        assertThat(numeroCommite(generateur)).isEqualTo("2026-0003");
    }

    @Test
    @DisplayName("une transaction annulee ne consomme PAS de numero : aucun trou dans la suite")
    void leRollbackNeCreusePasDeTrou() {
        GenerateurNumeroFacture generateur = generateurAu("2026-08-22T10:00:00Z");
        assertThat(numeroCommite(generateur)).isEqualTo("2026-0001");

        // Emission qui echoue apres l attribution du numero : exactement le
        // scenario qui, avec une sequence PostgreSQL, perdrait le 0002 a jamais.
        assertThatThrownBy(() -> transaction.execute(statut -> {
            generateur.prochain();
            throw new IllegalStateException("echec simule de l emission");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(numeroCommite(generateur))
                .as("Le numero libere par le rollback est reattribue : la suite reste continue")
                .isEqualTo("2026-0002");
        assertThat(jdbc.queryForObject(
                "SELECT dernier_numero FROM compteur_facture WHERE exercice = 2026", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("un numero ne peut pas etre tire hors transaction")
    void refuseHorsTransaction() {
        // Sans cette garde, l increment serait committe seul puis l emission
        // pourrait echouer : le trou reapparaitrait par la porte de derriere.
        assertThatThrownBy(() -> generateurInjecte.prochain())
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM compteur_facture", Integer.class))
                .as("Refus avant toute ecriture : aucune ligne de compteur creee")
                .isZero();
    }

    @Test
    @DisplayName("le passage d'annee civile repart a ANNEE-0001")
    void passageDAnnee() {
        GenerateurNumeroFacture en2026 = generateurAu("2026-12-31T10:00:00Z");
        assertThat(numeroCommite(en2026)).isEqualTo("2026-0001");
        assertThat(numeroCommite(en2026)).isEqualTo("2026-0002");

        GenerateurNumeroFacture en2027 = generateurAu("2027-01-01T09:00:00Z");
        assertThat(numeroCommite(en2027))
                .as("Nouvel exercice, nouvelle suite : la numerotation ne continue pas 2026")
                .isEqualTo("2027-0001");

        // Les deux compteurs coexistent : 2026 reste consultable, figee.
        assertThat(jdbc.queryForObject(
                "SELECT dernier_numero FROM compteur_facture WHERE exercice = 2026", Integer.class))
                .isEqualTo(2);
        assertThat(numeroCommite(en2027)).isEqualTo("2027-0002");
    }

    @Test
    @DisplayName("deux emissions simultanees n'obtiennent jamais le meme numero")
    void concurrenceSerialiseeParLeVerrou() throws Exception {
        GenerateurNumeroFacture generateur = generateurAu("2026-08-22T10:00:00Z");
        int concurrents = 6;
        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(concurrents);
        List<Callable<String>> emissions = new ArrayList<>();
        for (int i = 0; i < concurrents; i++) {
            emissions.add(() -> {
                depart.await(5, TimeUnit.SECONDS);
                return numeroCommite(generateur);
            });
        }

        List<Future<String>> resultats = new ArrayList<>();
        for (Callable<String> emission : emissions) {
            resultats.add(pool.submit(emission));
        }
        depart.countDown();

        List<String> numeros = new ArrayList<>();
        for (Future<String> resultat : resultats) {
            numeros.add(resultat.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(numeros)
                .as("Le verrou de ligne serialise les emissions : ni doublon, ni trou")
                .containsExactlyInAnyOrder("2026-0001", "2026-0002", "2026-0003",
                        "2026-0004", "2026-0005", "2026-0006");
    }

    @Test
    @DisplayName("la premiere facture d'un exercice cree sa ligne de compteur sans course perdue")
    void creationConcurrenteDuCompteur() throws Exception {
        GenerateurNumeroFacture generateur = generateurAu("2026-08-22T10:00:00Z");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM compteur_facture", Integer.class)).isZero();

        // Deux toutes premieres factures de l annee en meme temps : sans le
        // ON CONFLICT DO NOTHING, la perdante violerait la cle primaire et sa
        // transaction entiere serait condamnee.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        Future<String> premiere = pool.submit(() -> {
            depart.await(5, TimeUnit.SECONDS);
            return numeroCommite(generateur);
        });
        Future<String> seconde = pool.submit(() -> {
            depart.await(5, TimeUnit.SECONDS);
            return numeroCommite(generateur);
        });
        depart.countDown();

        assertThat(List.of(premiere.get(30, TimeUnit.SECONDS), seconde.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("2026-0001", "2026-0002");
        pool.shutdown();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM compteur_facture WHERE exercice = 2026", Integer.class))
                .isEqualTo(1);
    }
}
