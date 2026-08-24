package be.autoservplus.accessibilite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Garde-fou sur les attributs d accessibilite portes par le CORPS des gabarits.
 *
 * <p>Poser ces attributs une fois ne suffit pas : le vrai risque est qu un ecran
 * ajoute plus tard reintroduise le defaut sans que rien ne le signale. Ces cas
 * balaient donc l ensemble des gabarits plutot que ceux corriges aujourd hui, et
 * echouent en NOMMANT le fichier et la balise fautive — meme parti que
 * {@code SchemaIT.listeDesTracesExhaustive}, qui casse la build lorsqu une table
 * echappe au balayage RGPD.</p>
 *
 * <p>Test <b>unitaire</b> et non d integration : il lit des fichiers, sans Spring ni
 * base. C est aussi ce qui le rend assez rapide pour tourner a chaque {@code test}.</p>
 *
 * <p><b>Hors perimetre, deliberement</b> : le lien d evitement et les attributs de
 * l en-tete, recopies dans une soixantaine de gabarits. Les verrouiller ici
 * figerait une duplication que la passe « fragment de layout commun » doit
 * supprimer.</p>
 */
@DisplayName("Accessibilite du corps des gabarits (WCAG 2.1 AA)")
class GabaritsAccessiblesTest {

    private static final Path GABARITS = Path.of("src", "main", "resources", "templates");

    private static final Pattern THEAD = Pattern.compile("<thead\\b.*?</thead>", Pattern.DOTALL);
    private static final Pattern CELLULE_ENTETE = Pattern.compile("<th\\b[^>]*>");
    private static final Pattern ERREUR_DE_CHAMP = Pattern.compile("<[a-z]+\\b[^>]*\\bth:errors=[^>]*>");
    private static final Pattern RETOUR_FLASH =
            Pattern.compile("<[a-z]+\\b[^>]*th:if=\"\\$\\{(?:erreur|message)\\}\"[^>]*>");

    @Test
    @DisplayName("le dossier des gabarits est bien lu (sans quoi tous les cas seraient vides)")
    void leBalayageTrouveDesGabarits() {
        assertThat(gabarits()).hasSizeGreaterThan(50);
    }

    @Nested
    @DisplayName("Tableaux de donnees (WCAG 1.3.1)")
    class Tableaux {

        /**
         * Sans {@code scope}, un lecteur d ecran ne rattache aucune cellule a sa
         * colonne : le tableau est lu comme une suite de valeurs sans etiquette, ce qui
         * le rend inexploitable des qu il depasse deux colonnes.
         */
        @Test
        @DisplayName("toute cellule d en-tete declare sa portee")
        void porteeDeclareePartout() {
            List<String> manquants = new ArrayList<>();
            for (Path gabarit : gabarits()) {
                Matcher enTete = THEAD.matcher(lire(gabarit));
                while (enTete.find()) {
                    Matcher cellule = CELLULE_ENTETE.matcher(enTete.group());
                    while (cellule.find()) {
                        if (!cellule.group().contains("scope=")) {
                            manquants.add(nom(gabarit) + " : " + cellule.group());
                        }
                    }
                }
            }
            assertThat(manquants)
                    .as("cellules <th> sans scope= (ajouter scope=\"col\")")
                    .isEmpty();
        }

        /**
         * Une cellule d en-tete vide n annonce rien : la colonne des actions devient
         * anonyme. Le patron du projet est un libelle porte par
         * {@code <span class="visuellement-cache">}, visible du seul lecteur d ecran.
         */
        @Test
        @DisplayName("aucune cellule d en-tete n est vide")
        void aucuneCelluleDEnteteVide() {
            List<String> vides = new ArrayList<>();
            for (Path gabarit : gabarits()) {
                Matcher enTete = THEAD.matcher(lire(gabarit));
                while (enTete.find()) {
                    Matcher vide = Pattern.compile("<th\\b[^>]*>\\s*</th>").matcher(enTete.group());
                    while (vide.find()) {
                        vides.add(nom(gabarit) + " : " + vide.group());
                    }
                }
            }
            assertThat(vides)
                    .as("cellules <th> sans nom accessible (poser un span.visuellement-cache)")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Retours de formulaire (WCAG 3.3.1 et 4.1.3)")
    class Retours {

        /**
         * Un message flash apparait apres une soumission. Sans {@code role}, il est
         * rendu a l ecran mais jamais annonce : l utilisateur de lecteur d ecran voit
         * son action aboutir ou echouer sans qu aucune information ne lui parvienne.
         *
         * <p>{@code alert} pour l erreur (assertif, interrompt), {@code status} pour le
         * succes (poli, attend une pause) : intervertir les deux ferait interrompre la
         * lecture pour une confirmation anodine.</p>
         */
        @Test
        @DisplayName("tout bloc de message flash porte un role")
        void messagesFlashAnnonces() {
            List<String> muets = new ArrayList<>();
            for (Path gabarit : gabarits()) {
                Matcher bloc = RETOUR_FLASH.matcher(lire(gabarit));
                while (bloc.find()) {
                    if (!bloc.group().contains("role=")) {
                        muets.add(nom(gabarit) + " : " + bloc.group());
                    }
                }
            }
            assertThat(muets)
                    .as("blocs th:if=\"${erreur}\" / th:if=\"${message}\" sans role "
                        + "(role=\"alert\" pour une erreur, role=\"status\" pour un succes)")
                    .isEmpty();
        }

        /**
         * Un {@code th:errors} n est rendu QUE lorsque la validation a echoue. Le
         * contenu est donc present des le chargement de la page de retour, ou
         * {@code role="alert"} le fait annoncer.
         */
        @Test
        @DisplayName("toute erreur de champ porte role=alert")
        void erreursDeChampAnnoncees() {
            List<String> muettes = new ArrayList<>();
            for (Path gabarit : gabarits()) {
                Matcher erreur = ERREUR_DE_CHAMP.matcher(lire(gabarit));
                while (erreur.find()) {
                    if (!erreur.group().contains("role=")) {
                        muettes.add(nom(gabarit) + " : " + erreur.group());
                    }
                }
            }
            assertThat(muettes)
                    .as("balises th:errors sans role=\"alert\"")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Langue du document (WCAG 3.1.1)")
    class Langue {

        /**
         * Non-regression de F6. Un {@code lang} fige annoncerait du francais sur une
         * page neerlandaise, que le lecteur d ecran prononcerait alors avec la
         * phonetique francaise. Le doublon {@code lang="fr"} est conserve a dessein :
         * il sert de repli au gabarit ouvert hors serveur.
         */
        @Test
        @DisplayName("tout gabarit lie son attribut lang a la locale active")
        void langueLieeALaLocale() {
            List<String> figes = new ArrayList<>();
            for (Path gabarit : gabarits()) {
                String contenu = lire(gabarit);
                if (contenu.contains("<html") && !contenu.contains("th:lang=")) {
                    figes.add(nom(gabarit));
                }
            }
            assertThat(figes).as("gabarits dont l attribut lang ne suit pas la locale").isEmpty();
        }
    }

    private static List<Path> gabarits() {
        try (Stream<Path> arbre = Files.walk(GABARITS)) {
            return arbre.filter(Files::isRegularFile)
                    .filter(chemin -> chemin.toString().endsWith(".html"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Dossier des gabarits illisible : " + GABARITS, e);
        }
    }

    private static String lire(Path gabarit) {
        try {
            return Files.readString(gabarit, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Gabarit illisible : " + gabarit, e);
        }
    }

    private static String nom(Path gabarit) {
        return GABARITS.relativize(gabarit).toString().replace('\\', '/');
    }
}
