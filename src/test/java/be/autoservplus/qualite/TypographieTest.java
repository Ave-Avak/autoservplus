package be.autoservplus.qualite;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regle de redaction du projet, verifiee sur les fichiers du depot.
 *
 * <p>Deux controles. Les caracteres proscrits (tiret long ou moyen, points de suspension
 * en un seul caractere, fleches et symboles, emojis, apostrophes courbes) ne figurent
 * nulle part, code et donnees compris. Le point-virgule ne figure pas dans la prose :
 * libelles i18n, texte des gabarits, Markdown, et commentaires des sources et des
 * fichiers de configuration. Dans un commentaire, seul le point-virgule precede d une
 * espace est vise, la forme de la prose francaise, pour ne pas confondre un extrait de
 * code avec une phrase.</p>
 *
 * <p>Les caracteres sont designes par leur point de code : ce fichier passe lui-meme le
 * controle. Une donnee de test qui exige l un d eux l ecrit en sequence d echappement.</p>
 *
 * <p>Les migrations deja committees restent hors controle : modifier une migration,
 * meme dans un commentaire, change son empreinte et Flyway refuse alors toute base ou
 * elle est appliquee. Chaque exclusion porte son motif, et une exclusion d attente qui
 * ne sert plus fait echouer le test, sur le patron de {@code EN_FRANCAIS_FIGE}.</p>
 */
@DisplayName("Typographie du depot")
class TypographieTest {

    private static final List<String> RACINES = List.of(
            "src", "docs", ".github", "README.md", "CONTRIBUTING.md", "THIRD-PARTY.md",
            "Dockerfile", "docker-compose.yml", "docker-compose.prod.yml", "pom.xml",
            ".dockerignore", ".env.example");

    private static final Set<String> EXTENSIONS = Set.of(
            "java", "js", "css", "html", "properties", "yml", "yaml", "xml", "md", "sql", "sh",
            "txt", "json", "factories");

    /** Fichiers ecartes pour de bon, avec leur motif. */
    private static final Map<String, String> EXCLUS = Map.of(
            "docs/dump_autoservplus.sql", "dump produit par pg_dump, regenere au gel du code",
            "src/main/resources/static/js/htmx.min.js", "bibliotheque tierce, servie telle quelle",
            "src/main/resources/securite/mots-de-passe-courants.txt",
            "liste tierce (SecLists) de mots de passe, des donnees et non de la prose");

    /** Fichiers ecartes en attendant leur correction. Le test echoue des qu ils sont propres. */
    private static final Map<String, String> EN_ATTENTE = Map.of(
            ".env.example", "relu et corrige par le developpeur seul");

    /** Derniere migration de schema committee avant la regle, et graines de demonstration. */
    private static final int DERNIERE_MIGRATION_FIGEE = 42;
    private static final Set<String> GRAINES_FIGEES = Set.of("V900", "V901");

    private static final Pattern MIGRATION = Pattern.compile("src/main/resources/db/migration/V(\\d+)__.*");
    private static final Pattern GRAINE = Pattern.compile("src/main/resources/db/demo/(V\\d+)__.*");
    private static final Pattern ENTITE = Pattern.compile("&#?\\w+;");

    private static final Map<String, String> fichiers = new TreeMap<>();

    @BeforeAll
    static void lire() throws IOException {
        for (String racine : RACINES) {
            Path chemin = Path.of(racine);
            if (!Files.exists(chemin)) {
                continue;
            }
            try (Stream<Path> parcours = Files.walk(chemin)) {
                for (Path p : parcours.filter(Files::isRegularFile).toList()) {
                    String nom = p.toString().replace('\\', '/');
                    if (estTexte(nom) && !estFige(nom) && !EXCLUS.containsKey(nom)) {
                        fichiers.put(nom, Files.readString(p, StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("aucun caractere proscrit, hors fichiers exclus avec leur motif")
    void aucunCaractereProscrit() {
        List<String> ecarts = new ArrayList<>();
        fichiers.forEach((nom, texte) -> {
            if (!EN_ATTENTE.containsKey(nom)) {
                ecarts.addAll(caracteresProscrits(nom, texte));
            }
        });
        assertThat(ecarts).as("Caracteres proscrits par la regle de redaction de CLAUDE.md").isEmpty();
    }

    @Test
    @DisplayName("aucun point-virgule dans la prose")
    void aucunPointVirguleDansLaProse() {
        List<String> ecarts = new ArrayList<>();
        fichiers.forEach((nom, texte) -> {
            if (!EN_ATTENTE.containsKey(nom)) {
                ecarts.addAll(pointsVirgules(nom, texte));
            }
        });
        assertThat(ecarts).as("Point-virgule dans la prose : le remplacer par un point").isEmpty();
    }

    @Test
    @DisplayName("chaque exclusion d attente sert encore, chaque exclusion definitive designe un fichier")
    void exclusionsAJour() {
        EN_ATTENTE.keySet().forEach(nom -> assertThat(
                caracteresProscrits(nom, fichiers.getOrDefault(nom, "")).size()
                        + pointsVirgules(nom, fichiers.getOrDefault(nom, "")).size())
                .as("%s est conforme : le retirer de EN_ATTENTE", nom)
                .isPositive());
        EXCLUS.keySet().forEach(nom -> assertThat(Path.of(nom)).as("exclusion sans fichier").exists());
    }

    @Test
    @DisplayName("le controle voit ce qu il doit voir")
    void discrimination() {
        String tiret = Character.toString(0x2014);
        String emoji = Character.toString(0x1F527);
        assertThat(caracteresProscrits("a.md", "un " + tiret + " deux")).hasSize(1);
        assertThat(caracteresProscrits("a.java", "String s = \"" + emoji + "\";")).hasSize(1);
        assertThat(pointsVirgules("i18n/messages.properties", "cle=un ; deux")).hasSize(1);
        assertThat(pointsVirgules("A.java", "int a = 1; // un ; deux")).hasSize(1);
        assertThat(pointsVirgules("A.java", "String s = \"a // b ; c\"; int b;")).isEmpty();
        assertThat(pointsVirgules("a.html", "<p>un ; deux</p><code>a;b</code>&nbsp;")).hasSize(1);
        assertThat(pointsVirgules("a.md", "un ; deux\n```\na;b\n```\n`c;d`")).hasSize(1);
    }

    private static boolean estTexte(String nom) {
        String fichier = nom.substring(nom.lastIndexOf('/') + 1);
        int point = fichier.lastIndexOf('.');
        return fichier.equals("Dockerfile") || fichier.equals(".dockerignore")
                || fichier.equals(".env.example")
                || point > 0 && EXTENSIONS.contains(fichier.substring(point + 1));
    }

    private static boolean estFige(String nom) {
        Matcher migration = MIGRATION.matcher(nom);
        if (migration.matches()) {
            return Integer.parseInt(migration.group(1)) <= DERNIERE_MIGRATION_FIGEE;
        }
        Matcher graine = GRAINE.matcher(nom);
        return graine.matches() && GRAINES_FIGEES.contains(graine.group(1));
    }

    static boolean proscrit(int c) {
        return c == 0x2014 || c == 0x2013 || c == 0x2026 || c == 0x2018 || c == 0x2019
                || c == 0x00D7
                || c >= 0x2190 && c <= 0x21FF
                || c >= 0x2600 && c <= 0x27BF
                || c >= 0x1F300 && c <= 0x1FAFF;
    }

    static List<String> caracteresProscrits(String nom, String texte) {
        List<String> ecarts = new ArrayList<>();
        String[] lignes = texte.split("\n", -1);
        for (int k = 0; k < lignes.length; k++) {
            if (lignes[k].codePoints().anyMatch(TypographieTest::proscrit)) {
                ecarts.add(nom + ":" + (k + 1) + ": " + lignes[k].strip());
            }
        }
        return ecarts;
    }

    static List<String> pointsVirgules(String nom, String texte) {
        List<String> ecarts = new ArrayList<>();
        if (nom.endsWith(".properties") && nom.contains("i18n/")) {
            String[] lignes = texte.split("\n", -1);
            for (int k = 0; k < lignes.length; k++) {
                String ligne = lignes[k];
                boolean commentaire = ligne.stripLeading().startsWith("#");
                String prose = commentaire ? ligne : ligne.substring(ligne.indexOf('=') + 1);
                if (commentaire ? prose.contains(" ;") : ENTITE.matcher(prose).replaceAll("").contains(";")) {
                    ecarts.add(nom + ":" + (k + 1) + ": " + ligne.strip());
                }
            }
        } else if (nom.endsWith(".java") || nom.endsWith(".js")) {
            commentairesC(texte).forEach(m -> signaler(nom, texte, m, " ;", ecarts));
        } else if (nom.endsWith(".css")) {
            trouver(texte, "(?s)/\\*.*?\\*/").forEach(m -> signaler(nom, texte, m, " ;", ecarts));
        } else if (nom.endsWith(".html") || nom.endsWith(".xml")) {
            String sansBlocs = effacer(texte, "(?is)<(script|style|code|pre)\\b.*?</\\1>");
            trouver(sansBlocs, "(?s)<!--.*?-->").forEach(m -> signaler(nom, texte, m, " ;", ecarts));
            String visible = effacer(effacer(effacer(sansBlocs, "(?s)<!--.*?-->"), "(?s)<[^>]*>"), "&#?\\w+;");
            trouver(visible, ";").forEach(m -> signaler(nom, texte, m, ";", ecarts));
        } else if (nom.endsWith(".md")) {
            String prose = effacer(effacer(effacer(texte, "(?ms)^[ \\t]*```.*?^[ \\t]*```"), "`[^`\\n]*`"), "&#?\\w+;");
            trouver(prose, ";").forEach(m -> signaler(nom, texte, m, ";", ecarts));
        } else if (nom.endsWith(".sql")) {
            trouver(texte, "--[^\\n]*").forEach(m -> signaler(nom, texte, m, " ;", ecarts));
        } else {
            trouver(texte, "(?m)^[ \\t]*#[^\\n]*").forEach(m -> signaler(nom, texte, m, " ;", ecarts));
        }
        return ecarts;
    }

    private record Morceau(int debut, String texte) {
    }

    /** Commentaires d un source Java ou JavaScript : chaines, blocs de texte et caracteres sautes. */
    private static List<Morceau> commentairesC(String t) {
        List<Morceau> morceaux = new ArrayList<>();
        int i = 0;
        int n = t.length();
        while (i < n) {
            char c = t.charAt(i);
            if (t.startsWith("\"\"\"", i)) {
                int fin = t.indexOf("\"\"\"", i + 3);
                i = fin < 0 ? n : fin + 3;
            } else if (c == '"' || c == '\'' || c == '`') {
                int j = i + 1;
                while (j < n && t.charAt(j) != c && t.charAt(j) != '\n') {
                    j += t.charAt(j) == '\\' ? 2 : 1;
                }
                i = j + 1;
            } else if (t.startsWith("//", i)) {
                int fin = t.indexOf('\n', i);
                fin = fin < 0 ? n : fin;
                morceaux.add(new Morceau(i, t.substring(i, fin)));
                i = fin;
            } else if (t.startsWith("/*", i)) {
                int fin = t.indexOf("*/", i + 2);
                fin = fin < 0 ? n : fin + 2;
                morceaux.add(new Morceau(i, t.substring(i, fin)));
                i = fin;
            } else {
                i++;
            }
        }
        return morceaux;
    }

    private static List<Morceau> trouver(String texte, String motif) {
        List<Morceau> morceaux = new ArrayList<>();
        Matcher m = Pattern.compile(motif).matcher(texte);
        while (m.find()) {
            morceaux.add(new Morceau(m.start(), m.group()));
        }
        return morceaux;
    }

    /** Remplace chaque correspondance par des espaces, en gardant les sauts de ligne. */
    private static String effacer(String texte, String motif) {
        return Pattern.compile(motif).matcher(texte)
                .replaceAll(m -> Matcher.quoteReplacement(m.group().replaceAll("[^\\n]", " ")));
    }

    private static void signaler(String nom, String texte, Morceau morceau, String cible, List<String> ecarts) {
        int position = morceau.texte().indexOf(cible);
        while (position >= 0) {
            int absolu = morceau.debut() + position;
            int ligne = (int) texte.substring(0, absolu).chars().filter(c -> c == '\n').count() + 1;
            int debutLigne = texte.lastIndexOf('\n', absolu) + 1;
            int finLigne = texte.indexOf('\n', absolu);
            ecarts.add(nom + ":" + ligne + ": "
                    + texte.substring(debutLigne, finLigne < 0 ? texte.length() : finLigne).strip());
            position = morceau.texte().indexOf(cible, position + 1);
        }
    }
}
