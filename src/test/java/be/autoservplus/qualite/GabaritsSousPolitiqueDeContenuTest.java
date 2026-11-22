package be.autoservplus.qualite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que la politique de sécurité du contenu bloque sans rien dire, cherché dans le texte
 * des gabarits.
 *
 * <p>La politique n'admet que les feuilles de style et les scripts servis par le site
 * ({@code style-src 'self'}, {@code script-src 'self'}). Un attribut {@code style}, une
 * balise {@code <style>} ou un gestionnaire {@code onclick} écrit dans un gabarit est
 * ignoré par le navigateur, avec une ligne à la console et aucune erreur à l'écran :
 * deux confirmations n'apparaissaient jamais, et un formulaire perdait sa mise en ligne.
 * {@code EcransDansLeNavigateurIT} le voit dans un vrai navigateur, mais seulement sur
 * les écrans que ses données font rendre. Ce balayage lit tous les gabarits.</p>
 */
@DisplayName("Gabarits : rien que la politique de sécurité du contenu bloquerait")
class GabaritsSousPolitiqueDeContenuTest {

    private static final Path GABARITS = Path.of("src", "main", "resources", "templates");

    private static final Pattern COMMENTAIRE = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    /** Attribut style ou gestionnaire d'événement, dans une balise, en HTML ou par Thymeleaf. */
    private static final Pattern EN_LIGNE = Pattern.compile(
            "<[a-zA-Z][^>]*?\\s((?:th:)?(?:style|on[a-z]+))\\s*=", Pattern.DOTALL);
    private static final Pattern BALISE_STYLE = Pattern.compile("<style\\b");
    /** Un bloc de données JSON-LD n'est pas exécuté : la politique ne le concerne pas. */
    private static final Pattern SCRIPT_SANS_SOURCE = Pattern.compile(
            "<script\\b(?![^>]*\\bsrc=)(?![^>]*type=\"application/ld\\+json\")[^>]*>");

    @Test
    @DisplayName("ni attribut style, ni balise de style, ni gestionnaire d'événement, ni script en ligne")
    void rienEnLigne() {
        List<String> fautifs = new ArrayList<>();
        for (Path gabarit : gabarits()) {
            String texte = sansCommentaires(gabarit);
            Matcher attribut = EN_LIGNE.matcher(texte);
            while (attribut.find()) {
                fautifs.add(GABARITS.relativize(gabarit) + " : attribut " + attribut.group(1));
            }
            if (BALISE_STYLE.matcher(texte).find()) {
                fautifs.add(GABARITS.relativize(gabarit) + " : balise <style>");
            }
            if (SCRIPT_SANS_SOURCE.matcher(texte).find()) {
                fautifs.add(GABARITS.relativize(gabarit) + " : script en ligne");
            }
        }
        assertThat(fautifs)
                .as("Bloqué par la politique de sécurité du contenu, sans erreur visible. "
                        + "Une classe de la feuille de style, ou un script de /js avec des attributs data-*.")
                .isEmpty();
    }

    @Test
    @DisplayName("un gabarit qui charge HTMX lui interdit d'insérer sa balise de style")
    void htmxSansStyleInsere() {
        List<String> fautifs = new ArrayList<>();
        int avecHtmx = 0;
        for (Path gabarit : gabarits()) {
            String texte = sansCommentaires(gabarit);
            if (texte.contains("htmx.min.js")) {
                avecHtmx++;
                if (!texte.contains("\"includeIndicatorStyles\": false")) {
                    fautifs.add(GABARITS.relativize(gabarit).toString());
                }
            }
        }
        assertThat(avecHtmx).as("gabarits qui chargent HTMX").isPositive();
        assertThat(fautifs)
                .as("Sans <meta name=\"htmx-config\" content='{\"includeIndicatorStyles\": false}'>, HTMX "
                        + "insère une balise de style que style-src 'self' bloque.")
                .isEmpty();
    }

    private static String sansCommentaires(Path gabarit) {
        try {
            return COMMENTAIRE.matcher(Files.readString(gabarit, StandardCharsets.UTF_8)).replaceAll("");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> gabarits() {
        try (Stream<Path> arbre = Files.walk(GABARITS)) {
            List<Path> trouves = arbre.filter(chemin -> chemin.toString().endsWith(".html")).toList();
            assertThat(trouves).as("gabarits lus").isNotEmpty();
            return trouves;
        } catch (IOException e) {
            throw new UncheckedIOException("Dossier des gabarits illisible : " + GABARITS, e);
        }
    }
}
