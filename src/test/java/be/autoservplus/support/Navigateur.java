package be.autoservplus.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.function.Consumer;

/**
 * Un Chromium sans interface, piloté par Playwright, pour les tests d'intégration qui
 * ont besoin d'un vrai navigateur : mise en page, clavier, scripts, politique de
 * sécurité du contenu. MockMvc ne rend pas une page, il en rend le texte.
 *
 * <p>Playwright télécharge Chromium à son premier lancement, dans le cache de
 * l'utilisateur et non dans le dépôt. Un seul moteur : le débordement et le clavier ne
 * dépendent pas assez du moteur pour doubler la durée de la build.</p>
 *
 * <p>Un échec laisse de quoi le lire sans rejouer le test : une capture d'écran et une
 * trace Playwright dans {@code target/navigateur}, que l'intégration continue joint à ses
 * rapports. La trace s'ouvre par {@code npx playwright show-trace} ou sur
 * trace.playwright.dev.</p>
 */
public final class Navigateur implements AutoCloseable {

    /** Dossier des captures, des traces et du rapport d'accessibilité. */
    public static final Path SORTIE = Path.of("target", "navigateur");

    private static final int HAUTEUR = 900;

    private final Playwright playwright;
    private final Browser chromium;
    private final String racine;

    public Navigateur(int port) {
        this.playwright = Playwright.create();
        this.chromium = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        this.racine = "http://localhost:" + port;
        try {
            Files.createDirectories(SORTIE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Ouvre une fenêtre de la largeur donnée, en français, et y déroule le parcours. La
     * trace n'est gardée que si le parcours échoue, avec une capture de chaque page
     * encore ouverte.
     *
     * @param nom          nom du parcours, repris dans le nom des fichiers d'un échec
     * @param etatSession  cookies d'une session déjà ouverte, rendus par
     *                     {@link BrowserContext#storageState()}, ou {@code null} pour un
     *                     visiteur sans session
     */
    public void parcourir(String nom, int largeur, String etatSession, Consumer<BrowserContext> parcours) {
        parcourir(nom, largeur, etatSession, true, parcours);
    }

    /**
     * Le même parcours, script désactivé ou non : l'amélioration progressive se vérifie en
     * ouvrant la page sans script, comme le fait un navigateur qui l'a coupé.
     */
    public void parcourir(String nom, int largeur, String etatSession, boolean script,
                          Consumer<BrowserContext> parcours) {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setBaseURL(racine)
                .setLocale("fr-BE")
                .setJavaScriptEnabled(script)
                .setViewportSize(largeur, HAUTEUR);
        if (etatSession != null) {
            options.setStorageState(etatSession);
        }
        try (BrowserContext contexte = chromium.newContext(options)) {
            // Instantanés du document sans film de captures : il doublait la durée d'un balayage.
            contexte.tracing().start(new Tracing.StartOptions().setScreenshots(false).setSnapshots(true));
            try {
                parcours.accept(contexte);
            } catch (RuntimeException | Error echec) {
                String fichier = nomDeFichier(nom);
                int numero = 0;
                for (Page page : contexte.pages()) {
                    capturer(page, fichier + (numero++ == 0 ? "" : "-" + numero));
                }
                contexte.tracing().stop(new Tracing.StopOptions().setPath(SORTIE.resolve(fichier + "-trace.zip")));
                throw echec;
            }
            contexte.tracing().stop();
        }
    }

    /** Capture de la page entière, pour un défaut constaté sans arrêter le parcours. */
    public static void capturer(Page page, String nom) {
        try {
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(SORTIE.resolve(nomDeFichier(nom) + ".png"))
                    .setFullPage(true));
        } catch (RuntimeException e) {
            // Une capture manquée ne doit pas masquer l'échec qu'elle illustre.
        }
    }

    /** Un nom de fichier sans accent, sans espace ni barre oblique. */
    public static String nomDeFichier(String nom) {
        String sansAccent = Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccent.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "").toLowerCase();
    }

    @Override
    public void close() {
        chromium.close();
        playwright.close();
    }
}
