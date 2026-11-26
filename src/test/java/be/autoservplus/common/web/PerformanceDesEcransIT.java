package be.autoservplus.common.web;

import be.autoservplus.support.Navigateur;
import be.autoservplus.support.SocleIntegration;
import com.google.gson.JsonObject;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Budget de performance des écrans du gabarit du site (Q-I-11 (b)) : la page se charge
 * sur un téléphone ralenti et la build échoue au-delà du budget, au lieu d'une mesure faite
 * une fois à la main.
 *
 * <p>Conditions : processeur divisé par quatre et réseau 4G lente (150 ms d'aller-retour,
 * 1,6 Mbit/s en réception), celles du profil mobile de Lighthouse, appliquées par le
 * protocole de Chrome. Fenêtre de 412 px. Budget : LCP 2,5 s et CLS 0,1, les seuils
 * « bons » des Core Web Vitals.</p>
 *
 * <p>La compression gzip est activée dans ce seul contexte : en production, Caddy compresse
 * les réponses ({@code encode gzip}, {@code deploy/Caddyfile}) et l'application ne le fait
 * pas. Mesurer des réponses non compressées jugerait une chaîne qui n'existe pas.</p>
 *
 * <p>Les écrans sont mesurés sur la graine de démonstration, sous le profil {@code demo}
 * posé comme dans {@code GraineDemonstrationIT}. Sans elle, les listes n'ont ni catalogue
 * ni photos, et le plus grand élément peint est un titre au lieu de la première vignette :
 * le budget jugerait une page que personne ne verra. L'élément mesuré est nommé dans le
 * message d'échec. Les photos et les PDF éventuels sont écrits dans un dossier temporaire,
 * supprimé après la classe.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=demo",
        "server.compression.enabled=true",
        "server.compression.mime-types=text/html,text/css,application/javascript,image/svg+xml",
        "server.compression.min-response-size=1024"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Performance des écrans du site sur un téléphone ralenti")
class PerformanceDesEcransIT extends SocleIntegration {

    private static final int TELEPHONE = 412;
    private static final double BUDGET_LCP_MS = 2500;
    private static final double BUDGET_CLS = 0.1;

    private static final Path DOSSIER = dossierTemporaire();

    /** LCP et CLS lus par PerformanceObserver, trois secondes après le chargement. */
    private static final String MESURE = """
            () => new Promise(resolve => {
                let lcp = 0;
                let cls = 0;
                let element = '';
                new PerformanceObserver(liste => {
                    for (const entree of liste.getEntries()) {
                        lcp = entree.startTime;
                        const cible = entree.element;
                        element = cible ? cible.tagName.toLowerCase() + '.' + cible.className
                                + (cible.currentSrc ? ' ' + new URL(cible.currentSrc).pathname
                                        : ' « ' + cible.textContent.trim().slice(0, 40) + ' »') : 'aucun';
                    }
                }).observe({type: 'largest-contentful-paint', buffered: true});
                new PerformanceObserver(liste => {
                    for (const entree of liste.getEntries()) { if (!entree.hadRecentInput) { cls += entree.value; } }
                }).observe({type: 'layout-shift', buffered: true});
                const navigation = performance.getEntriesByType('navigation')[0];
                setTimeout(() => resolve({lcp, cls, element, reponse: navigation.responseStart}), 3000);
            })
            """;

    @LocalServerPort private int port;

    private Navigateur navigateur;

    @BeforeAll
    void lancer() {
        navigateur = new Navigateur(port);
    }

    @AfterAll
    void fermer() throws IOException {
        if (navigateur != null) {
            navigateur.close();
        }
        FileSystemUtils.deleteRecursively(DOSSIER);
    }

    @DynamicPropertySource
    static void dossiersTemporaires(DynamicPropertyRegistry registre) {
        registre.add("autoservplus.facturation.archive", () -> DOSSIER.resolve("factures").toString());
        registre.add("autoservplus.stockage.racine", () -> DOSSIER.resolve("medias").toString());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/", "/services", "/pieces"})
    @DisplayName("LCP sous 2,5 s et CLS sous 0,1 en 4G lente, processeur divisé par quatre")
    void budget(String ecran) {
        navigateur.parcourir("performance" + ecran.replace('/', '-'), TELEPHONE, null, contexte -> {
            Page page = contexte.newPage();
            // Un premier chargement, sans ralentissement, sur une autre page : sans lui, la
            // première mesure d'un serveur qui vient de démarrer comptait la compilation des
            // gabarits (premier octet à 1,8 s sur l'accueil). Le cache du navigateur est vidé
            // ensuite, la page mesurée repart de zéro.
            Page echauffement = contexte.newPage();
            echauffement.navigate(ecran);
            echauffement.close();
            contexte.clearCookies();
            CDPSession protocole = contexte.newCDPSession(page);
            protocole.send("Network.clearBrowserCache");
            JsonObject reseau = new JsonObject();
            reseau.addProperty("offline", false);
            reseau.addProperty("latency", 150);
            reseau.addProperty("downloadThroughput", 1.6 * 1024 * 1024 / 8);
            reseau.addProperty("uploadThroughput", 750 * 1024 / 8);
            protocole.send("Network.enable");
            protocole.send("Network.emulateNetworkConditions", reseau);
            JsonObject processeur = new JsonObject();
            processeur.addProperty("rate", 4);
            protocole.send("Emulation.setCPUThrottlingRate", processeur);

            page.navigate(ecran, new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
            @SuppressWarnings("unchecked")
            Map<String, Object> mesure = (Map<String, Object>) page.evaluate(MESURE);
            double lcp = ((Number) mesure.get("lcp")).doubleValue();
            double cls = ((Number) mesure.get("cls")).doubleValue();

            String detail = " (élément " + mesure.get("element") + ", premier octet à "
                    + Math.round(((Number) mesure.get("reponse")).doubleValue()) + " ms)";
            assertThat(lcp).as("LCP de " + ecran + " en millisecondes" + detail)
                    .isPositive().isLessThanOrEqualTo(BUDGET_LCP_MS);
            assertThat(cls).as("CLS de " + ecran + detail).isLessThanOrEqualTo(BUDGET_CLS);
        });
    }

    private static Path dossierTemporaire() {
        try {
            return Files.createTempDirectory("performance-des-ecrans");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
