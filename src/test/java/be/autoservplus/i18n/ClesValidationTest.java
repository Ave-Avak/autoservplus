package be.autoservplus.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toute cle citee par une annotation de validation existe dans les <b>trois</b>
 * langues.
 *
 * <h2>Le defaut que ce test aurait empeche</h2>
 *
 * <p>Huit cles {@code validation.*} etaient citees par {@code InscriptionForm} et
 * {@code Vehicule} sans figurer dans aucun fichier de messages. Une cle introuvable
 * n est pas une erreur au sens de Bean Validation : le message est rendu <b>tel
 * quel</b>. Le formulaire public d inscription affichait donc
 * {@code {validation.email.format}} a l utilisateur — constate en soumettant le
 * formulaire sur une application demarree, pas deduit.</p>
 *
 * <p>Le defaut avait survecu parce que <b>rien ne le regardait</b> : aucun test ne
 * couvrait ces messages, et une cle manquante ne casse ni la compilation, ni le
 * demarrage, ni le rendu. Elle ne se voit qu a l ecran, au moment ou un visiteur se
 * trompe de saisie.</p>
 *
 * <h2>Pourquoi balayer les annotations plutot que verifier une liste</h2>
 *
 * <p>Une liste de cles ecrite a la main dans un test vieillit comme le reste : elle
 * ne dit rien de la cle ajoutee demain. Ce test lit les <b>sources</b>, en extrait
 * les cles reellement citees, et echoue sur la premiere qui n est pas traduite. Meme
 * patron que {@code SchemaIT.listeDesTracesExhaustive} et
 * {@code GabaritsAccessiblesTest} : ce qui protege n est pas la declaration, c est le
 * test qui echoue quand elle cesse d etre vraie.</p>
 */
@DisplayName("Cles de validation traduites")
class ClesValidationTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final Path I18N = Path.of("src/main/resources/i18n");

    /**
     * Ne retient que l attribut {@code message} d une annotation, et seulement quand
     * sa valeur est <b>entierement</b> une cle entre accolades. Bean Validation ne
     * resout que cette forme-la ; un message litteral ne concerne pas ce test.
     */
    private static final Pattern MESSAGE = Pattern.compile("message\\s*=\\s*\"\\{([^}]+)}\"");

    @Test
    @DisplayName("chaque clé citée par une annotation existe en FR, NL et EN")
    void toutesLesClesSontTraduites() throws IOException {
        Set<String> citees = clesCitees();
        // Garde-fou du garde-fou : si l extraction ne trouve plus rien, le test
        // passerait au vert en ne verifiant rien. Le projet en cite une vingtaine.
        assertThat(citees)
                .as("Aucune clé extraite : l'extraction des annotations ne fonctionne plus")
                .hasSizeGreaterThan(10);

        List<String> manquantes = new ArrayList<>();
        for (String fichier : List.of("messages.properties", "messages_nl.properties",
                "messages_en.properties")) {
            Properties traductions = charger(I18N.resolve(fichier));
            citees.stream()
                    .filter(cle -> !traductions.containsKey(cle))
                    .forEach(cle -> manquantes.add(fichier + " : " + cle));
        }

        assertThat(manquantes)
                .as("Ces clés sont citées par une annotation de validation mais ne sont "
                        + "traduites nulle part. Bean Validation rendrait la clé telle "
                        + "quelle, accolades comprises, à l'utilisateur.")
                .isEmpty();
    }

    private static Set<String> clesCitees() throws IOException {
        Set<String> cles = new LinkedHashSet<>();
        try (Stream<Path> fichiers = Files.walk(SOURCES)) {
            fichiers.filter(chemin -> chemin.toString().endsWith(".java"))
                    .forEach(chemin -> {
                        Matcher trouve = MESSAGE.matcher(lire(chemin));
                        while (trouve.find()) {
                            cles.add(trouve.group(1));
                        }
                    });
        }
        return cles;
    }

    private static String lire(Path chemin) {
        try {
            return Files.readString(chemin, StandardCharsets.UTF_8);
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }

    /**
     * Lecture en <b>UTF-8</b> et non par {@code Properties.load(InputStream)}, qui
     * suppose ISO-8859-1 : les valeurs accentuees seraient illisibles. Seules les cles
     * comptent ici, mais lire faux pour n en regarder qu une moitie serait un piege
     * pose pour le prochain.
     */
    private static Properties charger(Path fichier) throws IOException {
        Properties proprietes = new Properties();
        try (var lecteur = Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
            proprietes.load(lecteur);
        }
        return proprietes;
    }
}
