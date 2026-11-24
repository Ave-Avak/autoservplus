package be.autoservplus.common.numerotation;

import be.autoservplus.support.FuseauxDeTest;
import be.autoservplus.support.SocleIntegration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suites annuelles des devis et des fils de discussion (V69, V71).
 *
 * <p>Sans transaction de classe, comme {@code NumerotationFactureIT} : la concurrence se
 * prouve sur deux transactions réellement validées. Les horloges sont placées en 2031 et
 * 2032, années qu'aucun autre test n'atteint, et seules ces lignes de compteur sont
 * effacées : les fils et les devis des autres classes gardent leurs numéros.</p>
 */
@SpringBootTest
@DisplayName("Compteur annuel des devis et des fils (intégration)")
class CompteurAnnuelIT extends SocleIntegration {

    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;
    /** Le bean réel, seul porteur du proxy transactionnel. */
    @Autowired private CompteurAnnuel compteurInjecte;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactions);
        for (TypeCompteur type : TypeCompteur.values()) {
            jdbc.update("DELETE FROM " + type.table() + " WHERE exercice IN (2031, 2032)");
        }
    }

    /** Compteur à l'heure choisie, l'horloge en UTC comme celle de l'application. */
    private CompteurAnnuel compteurAu(String instant) {
        return new CompteurAnnuel(entityManager, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                FuseauxDeTest.bruxelles());
    }

    private String numeroValide(CompteurAnnuel compteur, TypeCompteur type) {
        return transaction.execute(statut -> compteur.prochainNumero(type));
    }

    @Test
    @DisplayName("les numéros d'une année se suivent, préfixe du type compris")
    void numerosConsecutifs() {
        CompteurAnnuel compteur = compteurAu("2031-06-01T10:00:00Z");

        assertThat(numeroValide(compteur, TypeCompteur.FIL)).isEqualTo("FIL-2031-0001");
        assertThat(numeroValide(compteur, TypeCompteur.FIL)).isEqualTo("FIL-2031-0002");
        assertThat(numeroValide(compteur, TypeCompteur.FIL)).isEqualTo("FIL-2031-0003");
    }

    @Test
    @DisplayName("devis et fils ont chacun leur suite, dans leur propre table")
    void suitesIndependantes() {
        CompteurAnnuel compteur = compteurAu("2031-06-01T10:00:00Z");

        assertThat(numeroValide(compteur, TypeCompteur.DEVIS)).isEqualTo("DEV-2031-0001");
        assertThat(numeroValide(compteur, TypeCompteur.FIL)).isEqualTo("FIL-2031-0001");
        assertThat(numeroValide(compteur, TypeCompteur.DEVIS)).isEqualTo("DEV-2031-0002");
        assertThat(jdbc.queryForObject(
                "SELECT dernier_numero FROM compteur_fil WHERE exercice = 2031", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("l'année est celle du garage : le 1er janvier à 0 h 30 ouvre une nouvelle suite")
    void anneeAuGarage() {
        assertThat(numeroValide(compteurAu("2031-12-31T10:00:00Z"), TypeCompteur.FIL))
                .isEqualTo("FIL-2031-0001");

        // 23 h 30 en UTC, 0 h 30 à Bruxelles : l'année d'une horloge en UTC serait 2031.
        assertThat(numeroValide(compteurAu("2031-12-31T23:30:00Z"), TypeCompteur.FIL))
                .isEqualTo("FIL-2032-0001");
    }

    @Test
    @DisplayName("une création annulée ne consomme pas de numéro")
    void annulationSansNumeroPerdu() {
        CompteurAnnuel compteur = compteurAu("2031-06-01T10:00:00Z");
        assertThat(numeroValide(compteur, TypeCompteur.FIL)).isEqualTo("FIL-2031-0001");

        assertThatThrownBy(() -> transaction.execute(statut -> {
            compteur.prochainNumero(TypeCompteur.FIL);
            throw new IllegalStateException("échec simulé de l'ouverture du fil");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(numeroValide(compteur, TypeCompteur.FIL)).isEqualTo("FIL-2031-0002");
    }

    @Test
    @DisplayName("un numéro ne se tire pas hors d'une transaction")
    void refuseHorsTransaction() {
        assertThatThrownBy(() -> compteurInjecte.prochainNumero(TypeCompteur.FIL))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("deux premières attributions simultanées de l'année obtiennent deux numéros")
    void premiereAttributionConcurrente() throws Exception {
        CompteurAnnuel compteur = compteurAu("2031-06-01T10:00:00Z");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        Future<String> premier = pool.submit(() -> {
            depart.await(5, TimeUnit.SECONDS);
            return numeroValide(compteur, TypeCompteur.FIL);
        });
        Future<String> second = pool.submit(() -> {
            depart.await(5, TimeUnit.SECONDS);
            return numeroValide(compteur, TypeCompteur.FIL);
        });
        depart.countDown();

        assertThat(List.of(premier.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("FIL-2031-0001", "FIL-2031-0002");
        pool.shutdown();
    }
}
