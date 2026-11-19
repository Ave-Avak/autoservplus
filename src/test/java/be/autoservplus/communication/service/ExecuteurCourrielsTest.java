package be.autoservplus.communication.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("ExecuteurCourriels")
class ExecuteurCourrielsTest {

    private static final Duration ATTENTE = Duration.ofSeconds(5);

    private final List<String> partis = new CopyOnWriteArrayList<>();
    private final List<String> fils = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> contextes = new CopyOnWriteArrayList<>();
    private ExecuteurCourriels executeur;

    @AfterEach
    void arreter() {
        MDC.clear();
        if (executeur != null) {
            executeur.destroy();
        }
    }

    private static Courriel courriel(String type) {
        return new Courriel(type, "a@exemple.be", "A", "Sujet", "Corps", List.of());
    }

    private TransportCourriel enregistreur() {
        return c -> {
            fils.add(Thread.currentThread().getName());
            contextes.add(MDC.getCopyOfContextMap() == null ? Map.of() : MDC.getCopyOfContextMap());
            partis.add(c.type());
        };
    }

    @Test
    @DisplayName("le courriel part d'un autre fil, avec le MDC de l'appelant, vidé après la tâche")
    void autreFilEtMdc() {
        executeur = new ExecuteurCourriels(enregistreur(), 1, 10);
        MDC.put("requete", "0badcafe");

        executeur.soumettre(courriel("reinitialisation"));
        MDC.clear();
        executeur.soumettre(courriel("sans-contexte"));

        await().atMost(ATTENTE).until(() -> partis.size() == 2);
        assertThat(fils).allMatch(nom -> nom.startsWith("courriel-"))
                .doesNotContain(Thread.currentThread().getName());
        assertThat(contextes.get(0)).containsEntry("requete", "0badcafe");
        assertThat(contextes.get(1)).doesNotContainKey("requete");
    }

    @Test
    @DisplayName("file pleine : le courriel est abandonné sans lever, ni partir dans le fil appelant")
    void filePleine() throws Exception {
        CountDownLatch bloque = new CountDownLatch(1);
        CountDownLatch commence = new CountDownLatch(1);
        executeur = new ExecuteurCourriels(c -> {
            commence.countDown();
            try {
                bloque.await(ATTENTE.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException interrompu) {
                Thread.currentThread().interrupt();
            }
            fils.add(Thread.currentThread().getName());
            partis.add(c.type());
        }, 1, 1);

        executeur.soumettre(courriel("en-cours"));
        assertThat(commence.await(ATTENTE.toSeconds(), TimeUnit.SECONDS)).isTrue();
        executeur.soumettre(courriel("en-file"));
        executeur.soumettre(courriel("abandonne"));
        bloque.countDown();

        await().atMost(ATTENTE).until(() -> partis.size() == 2);
        assertThat(partis).containsExactly("en-cours", "en-file");
        assertThat(fils).doesNotContain(Thread.currentThread().getName());
    }

    @Test
    @DisplayName("une panne du transport n'arrête pas l'exécuteur")
    void panneDuTransport() {
        executeur = new ExecuteurCourriels(c -> {
            if (c.type().equals("en-panne")) {
                throw new IllegalStateException("relais injoignable");
            }
            partis.add(c.type());
        }, 1, 10);

        executeur.soumettre(courriel("en-panne"));
        executeur.soumettre(courriel("suivant"));

        await().atMost(ATTENTE).until(() -> partis.contains("suivant"));
    }

    @Test
    @DisplayName("l'arrêt attend les courriels en cours et en file")
    void arretPropre() throws Exception {
        CountDownLatch commence = new CountDownLatch(1);
        executeur = new ExecuteurCourriels(c -> {
            commence.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException interrompu) {
                Thread.currentThread().interrupt();
            }
            partis.add(c.type());
        }, 1, 10);
        executeur.soumettre(courriel("premier"));
        executeur.soumettre(courriel("second"));
        assertThat(commence.await(ATTENTE.toSeconds(), TimeUnit.SECONDS)).isTrue();

        executeur.destroy();
        executeur = null;

        assertThat(partis).containsExactly("premier", "second");
    }
}
