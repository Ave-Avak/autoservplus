package be.autoservplus.qualite;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toute classe écrite dans un gabarit du site existe dans la feuille compilée.
 *
 * <p>La feuille n'embarque que les modules de Bootstrap employés (C14). Une page migrée
 * plus tard qui emploie une classe d'un module retiré s'afficherait sans style, sans
 * aucune erreur : ce test le dit à la build. Il lit les pages qui chargent {@code site.css}
 * (fragment {@code ressources} de {@code fragments/site}), puis les fragments qu'elles
 * insèrent, de proche en proche, fragment par fragment : un fragment partagé avec les
 * écrans pas encore migrés n'est lu que pour la partie qu'une page migrée emploie.</p>
 *
 * <p>Les classes viennent de l'attribut {@code class} et des littéraux entre apostrophes de
 * {@code th:class}, {@code th:classappend} et {@code th:attr}. Une classe calculée en entier
 * par une expression échappe au test.</p>
 */
@DisplayName("Classes des gabarits du site présentes dans la feuille compilée")
class ClassesDuSiteTest {

    private static final Path GABARITS = Path.of("src/main/resources/templates");

    /** Fragment qui charge la feuille du site : une page qui l'insère est une page migrée. */
    private static final String RESSOURCES_DU_SITE = "fragments/site :: ressources";

    private static final Pattern INSERTION = Pattern.compile("~\\{\\s*([\\w/-]+)\\s*::\\s*([\\w-]+)");
    private static final Pattern LITTERAL = Pattern.compile("'([^'$#@{}|]*)'");
    private static final Pattern NOM_DE_CLASSE = Pattern.compile("[A-Za-z][\\w-]*");

    /**
     * Classes posées pour un script, un test ou un lecteur, sans style à elles. Chacune dit
     * pourquoi : une classe qui manque à la feuille sans y être citée est un défaut.
     */
    private static final Map<String, String> SANS_STYLE = Map.of(
            "fil-ariane", "repère de FilArianeIT et de RayonsCatalogueIT, la mise en forme est sur fil-ariane-page",
            "liens-legaux", "repère de LiensLegauxIT, commun au pied du site et à l'ancien pied",
            "formulaire-suppression", "repère de confirmation.js, qui demande l'accord avant l'envoi, et de EcransDansLeNavigateurIT");

    @Test
    @DisplayName("chaque classe d'une page migrée ou d'un fragment qu'elle insère est dans site.css")
    void classesPresentesDansLaFeuille() throws IOException {
        String feuille = feuille();
        Map<String, Set<String>> manquantes = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entree : classesDesGabaritsDuSite().entrySet()) {
            String classe = entree.getKey();
            if (SANS_STYLE.containsKey(classe)) {
                continue;
            }
            Pattern selecteur = Pattern.compile("\\." + Pattern.quote(classe) + "(?![\\w-])");
            if (!selecteur.matcher(feuille).find()) {
                manquantes.put(classe, entree.getValue());
            }
        }
        assertThat(manquantes).as("classes sans règle dans site.css, avec les gabarits qui les emploient")
                .isEmpty();
    }

    @Test
    @DisplayName("le test lit bien les pages migrées et leurs fragments")
    void pagesMigreesLues() throws IOException {
        Map<String, Set<String>> classes = classesDesGabaritsDuSite();

        assertThat(classes).containsKeys("carte-article", "navbar", "offcanvas-lg", "btn");
        assertThat(classes.get("carte-article")).contains("catalogue/liste");
        assertThat(classes.values().stream().flatMap(Set::stream))
                .contains("accueil", "fragments/site :: entete", "fragments/cookies :: bandeau");
    }

    /** Classe vers les gabarits ou fragments qui l'emploient. */
    private static Map<String, Set<String>> classesDesGabaritsDuSite() throws IOException {
        Map<String, Set<String>> classes = new TreeMap<>();
        Deque<String> aLire = new ArrayDeque<>();
        Set<String> lus = new HashSet<>();
        for (String page : pagesMigrees()) {
            Document document = Jsoup.parse(Files.readString(GABARITS.resolve(page + ".html"), StandardCharsets.UTF_8));
            relever(document, page, classes, aLire);
        }
        while (!aLire.isEmpty()) {
            String insertion = aLire.pop();
            if (!lus.add(insertion)) {
                continue;
            }
            String[] parties = insertion.split(" :: ");
            Document document = Jsoup.parse(
                    Files.readString(GABARITS.resolve(parties[0] + ".html"), StandardCharsets.UTF_8));
            Element fragment = document.getAllElements().stream()
                    .filter(e -> nomDuFragment(e).equals(parties[1]))
                    .findFirst().orElseThrow(() -> new AssertionError("fragment introuvable : " + insertion));
            relever(fragment, insertion, classes, aLire);
        }
        return classes;
    }

    private static List<String> pagesMigrees() throws IOException {
        try (Stream<Path> fichiers = Files.walk(GABARITS)) {
            List<String> pages = fichiers.filter(f -> f.toString().endsWith(".html"))
                    .filter(f -> !f.startsWith(GABARITS.resolve("fragments")))
                    .filter(f -> contient(f, RESSOURCES_DU_SITE))
                    .map(f -> GABARITS.relativize(f).toString().replace('\\', '/').replace(".html", ""))
                    .sorted().toList();
            assertThat(pages).as("pages qui chargent site.css").isNotEmpty();
            return pages;
        }
    }

    private static void relever(Element racine, String origine, Map<String, Set<String>> classes,
                                Deque<String> aLire) {
        for (Element element : racine.getAllElements()) {
            for (Attribute attribut : element.attributes()) {
                String nom = attribut.getKey();
                String valeur = attribut.getValue();
                if (nom.equals("class")) {
                    ajouter(valeur, origine, classes);
                } else if (nom.equals("th:class") || nom.equals("th:classappend") || nom.equals("th:attr")) {
                    Matcher litteraux = LITTERAL.matcher(nom.equals("th:attr") ? valeurDeClasse(valeur) : valeur);
                    while (litteraux.find()) {
                        ajouter(litteraux.group(1), origine, classes);
                    }
                }
                Matcher insertions = INSERTION.matcher(valeur);
                while (insertions.find()) {
                    aLire.push(insertions.group(1) + " :: " + insertions.group(2));
                }
            }
        }
    }

    /** Dans un {@code th:attr}, la seule affectation de {@code class}, jusqu'à la virgule suivante. */
    private static String valeurDeClasse(String attributs) {
        Matcher affectation = Pattern.compile("(?:^|,)\\s*class\\s*=([^,]*)").matcher(attributs);
        return affectation.find() ? affectation.group(1) : "";
    }

    private static void ajouter(String valeur, String origine, Map<String, Set<String>> classes) {
        for (String jeton : valeur.trim().split("\\s+")) {
            if (NOM_DE_CLASSE.matcher(jeton).matches()) {
                classes.computeIfAbsent(jeton, c -> new TreeSet<>()).add(origine);
            }
        }
    }

    private static String nomDuFragment(Element element) {
        String declaration = element.attr("th:fragment");
        int parenthese = declaration.indexOf('(');
        return (parenthese < 0 ? declaration : declaration.substring(0, parenthese)).trim();
    }

    private static boolean contient(Path fichier, String texte) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8).contains(texte);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String feuille() throws IOException {
        try (InputStream flux = ClassesDuSiteTest.class.getResourceAsStream("/static/css/site.css")) {
            assertThat(flux).as("site.css absente : la phase generate-resources n'a pas tourné").isNotNull();
            return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
