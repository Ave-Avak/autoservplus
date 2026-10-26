package be.autoservplus.accessibilite;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fous sur {@code css/autoservplus.css}.
 *
 * <p>{@code :invalid} colorait en rouge tout champ requis des l affichage, avant toute
 * saisie : un formulaire vierge se presentait comme une erreur. {@code :user-invalid}
 * n agit qu apres interaction ; le retour de {@code :invalid}, raccourci tentant, est
 * refuse ici.</p>
 *
 * <p><b>Contrastes calcules depuis la feuille.</b> Les liens de langue (1.13:1) et
 * l indicateur de focus (2.40:1) etaient illisibles sur le bleu de l en-tete, alors que
 * le commentaire de tete de la feuille annoncait des contrastes conformes. Les couleurs
 * sont relues dans la feuille, variables resolues, et le ratio est celui de WCAG 2.1.</p>
 */
@DisplayName("Feuille de style")
class FeuilleDeStyleTest {

    private static final Path FEUILLE = Path.of("src/main/resources/static/css/autoservplus.css");
    private static final Pattern INVALID_NU = Pattern.compile("(?<!user-):invalid\\b");
    private static final Pattern VARIABLE = Pattern.compile("(--[\\w-]+):\\s*(#[0-9A-Fa-f]{6})");
    private static final Pattern COULEUR = Pattern.compile("#[0-9A-Fa-f]{6}");

    private static String feuille;
    private static final Map<String, String> variables = new HashMap<>();

    @BeforeAll
    static void lire() throws Exception {
        feuille = Files.readString(FEUILLE, StandardCharsets.UTF_8).replaceAll("(?s)/\\*.*?\\*/", "");
        Matcher m = VARIABLE.matcher(feuille);
        while (m.find()) {
            variables.putIfAbsent(m.group(1), m.group(2));
        }
    }

    @Test
    @DisplayName("aucun champ n'est marqué en faute avant interaction")
    void pasDeInvalidNu() {
        assertThat(INVALID_NU.matcher(feuille).find())
                .as(":invalid sans user- dans la feuille de style")
                .isFalse();
    }

    /** Technique C40 : l un des deux anneaux tient 3:1 sur chacun des fonds du site. */
    @Test
    @DisplayName("focus à deux couleurs : au moins 3:1 sur fond blanc comme sur le bleu de l'en-tête")
    void focusLisibleSurLesDeuxFonds() {
        String bloc = bloc(":focus-visible");
        List<String> anneaux = List.of(couleur(propriete(bloc, "outline")), couleur(propriete(bloc, "box-shadow")));
        for (String fond : List.of("#FFFFFF", variables.get("--bleu-marine"))) {
            assertThat(anneaux.stream().mapToDouble(a -> contraste(a, fond)).max().orElseThrow())
                    .as("meilleur anneau sur " + fond).isGreaterThanOrEqualTo(3.0);
        }
    }

    @Test
    @DisplayName("liens de langue : au moins 4,5:1 sur le bleu de l'en-tête, langue active comprise")
    void liensDeLangueLisibles() {
        String marine = variables.get("--bleu-marine");
        assertThat(contraste(couleur(propriete(bloc(".selecteur-langue a"), "color")), marine))
                .isGreaterThanOrEqualTo(4.5);
        String active = bloc(".selecteur-langue a.est-active");
        assertThat(contraste(couleur(propriete(active, "color")), couleur(propriete(active, "background"))))
                .isGreaterThanOrEqualTo(4.5);
    }

    private static String bloc(String selecteur) {
        Matcher m = Pattern.compile("(?m)^" + Pattern.quote(selecteur) + "\\s*\\{([^}]*)}").matcher(feuille);
        assertThat(m.find()).as("règle " + selecteur).isTrue();
        return m.group(1);
    }

    private static String propriete(String bloc, String nom) {
        Matcher m = Pattern.compile("(?m)^\\s*" + Pattern.quote(nom) + ":\\s*([^;]+);").matcher(bloc);
        assertThat(m.find()).as("propriété " + nom).isTrue();
        return m.group(1);
    }

    private static String couleur(String valeur) {
        Matcher var = Pattern.compile("var\\((--[\\w-]+)\\)").matcher(valeur);
        String resolue = var.find() ? variables.get(var.group(1)) : valeur;
        Matcher m = COULEUR.matcher(resolue);
        assertThat(m.find()).as("couleur dans « " + valeur + " »").isTrue();
        return m.group();
    }

    /** Ratio de contraste WCAG 2.1, sur la luminance relative. */
    static double contraste(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            c[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }
}
