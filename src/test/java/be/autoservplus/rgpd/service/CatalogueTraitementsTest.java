package be.autoservplus.rgpd.service;

import be.autoservplus.rgpd.service.dto.ExportDonnees;
import be.autoservplus.rgpd.service.dto.InformationsTraitement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rappel legal de l article 15 : structure, completude et disponibilite dans les
 * trois langues.
 *
 * <p>Le test monte un vrai {@code MessageSource} sur les fichiers du projet plutot
 * qu un bouchon : ce qui doit etre verifie ici n est pas que le composant sait
 * concatener des cles, c est qu <b>aucune mention obligatoire ne manque</b> dans le
 * fichier livre. Un bouchon rendrait le test vert avec des fichiers vides.
 */
@DisplayName("CatalogueTraitements")
class CatalogueTraitementsTest {

    private final CatalogueTraitements catalogue = new CatalogueTraitements(messageSource());

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        // Meme reglage que application.yml : la langue du systeme de build ne doit
        // pas decider du contenu d un document juridique.
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Nested
    @DisplayName("Informations de traitement")
    class Informations {

        @Test
        @DisplayName("expose les cinq rubriques exigees par l'article 15, toutes non vides")
        void rubriquesCompletes() {
            InformationsTraitement infos = catalogue.informationsTraitement(Locale.FRENCH);

            assertThat(infos.responsableTraitement()).isNotBlank();
            assertThat(infos.finalites()).isNotEmpty();
            assertThat(infos.categoriesDonnees()).isNotEmpty();
            assertThat(infos.destinataires()).isNotEmpty();
            assertThat(infos.dureesConservation()).isNotEmpty();
            assertThat(infos.droits()).isNotEmpty();
            assertThat(infos.exerciceDesDroits()).isNotBlank();
            assertThat(infos.note()).isNotBlank();
        }

        @Test
        @DisplayName("associe une base legale a chaque finalite (article 6)")
        void chaqueFinaliteAUneBaseLegale() {
            InformationsTraitement infos = catalogue.informationsTraitement(Locale.FRENCH);

            assertThat(infos.finalites()).allSatisfy(finalite -> {
                assertThat(finalite.code()).isNotBlank();
                assertThat(finalite.libelle()).isNotBlank();
                assertThat(finalite.baseLegale()).isNotBlank();
            });
        }

        @Test
        @DisplayName("declare les sous-traitants reellement mobilises : paiement, courriel, hebergement")
        void declareLesSousTraitants() {
            InformationsTraitement infos = catalogue.informationsTraitement(Locale.FRENCH);

            assertThat(infos.destinataires())
                    .extracting(InformationsTraitement.Destinataire::nom)
                    .contains("Mollie B.V.", "Brevo");
            assertThat(infos.destinataires()).allSatisfy(destinataire -> {
                assertThat(destinataire.role()).isNotBlank();
                assertThat(destinataire.pays()).isNotBlank();
            });
        }

        @Test
        @DisplayName("cite l'article du RGPD qui fonde chaque droit rappele")
        void chaqueDroitCiteSonArticle() {
            InformationsTraitement infos = catalogue.informationsTraitement(Locale.FRENCH);

            assertThat(infos.droits()).allSatisfy(droit -> {
                assertThat(droit.libelle()).isNotBlank();
                assertThat(droit.article()).isNotBlank();
            });
            assertThat(infos.droits())
                    .extracting(InformationsTraitement.Droit::article)
                    .contains("Article 15 du RGPD", "Article 20 du RGPD");
        }

        @Test
        @DisplayName("mentionne la conservation comptable de 7 ans")
        void mentionneLaConservationComptable() {
            InformationsTraitement infos = catalogue.informationsTraitement(Locale.FRENCH);

            assertThat(infos.dureesConservation())
                    .extracting(InformationsTraitement.DureeConservation::duree)
                    .anySatisfy(duree -> assertThat(duree).contains("7 ans"));
        }

        @ParameterizedTest(name = "langue {0}")
        @ValueSource(strings = {"fr", "nl", "en"})
        @DisplayName("se resout dans les trois langues sans cle manquante")
        void disponibleDansLesTroisLangues(String langue) {
            InformationsTraitement infos =
                    catalogue.informationsTraitement(Locale.forLanguageTag(langue));

            assertThat(infos.responsableTraitement()).isNotBlank();
            assertThat(infos.finalites()).hasSize(8);
            assertThat(infos.categoriesDonnees()).hasSize(7).allSatisfy(
                    categorie -> assertThat(categorie).isNotBlank());
            assertThat(infos.destinataires()).hasSize(3);
            assertThat(infos.dureesConservation()).hasSize(5);
            assertThat(infos.droits()).hasSize(8);
        }
    }

    @Nested
    @DisplayName("Exclusions")
    class NotesExclusion {

        @ParameterizedTest(name = "langue {0}")
        @ValueSource(strings = {"fr", "nl", "en"})
        @DisplayName("porte les trois notes d'exclusion dans les trois langues")
        void troisNotes(String langue) {
            ExportDonnees.Exclusions exclusions =
                    catalogue.exclusions(Locale.forLanguageTag(langue));

            assertThat(exclusions.motDePasse()).isNotBlank();
            assertThat(exclusions.donneesBancaires()).isNotBlank();
            assertThat(exclusions.secretsTechniques()).isNotBlank();
        }
    }

    /**
     * Garde-fou du chantier i18n : {@code ResourceBundle} retombe silencieusement
     * sur le fichier par defaut quand une cle manque en NL ou en EN. Un export
     * neerlandophone pourrait donc partir avec des mentions legales francaises sans
     * qu aucune exception ne soit levee. La comparaison porte sur les fichiers
     * eux-memes, seul endroit ou le trou est visible.
     */
    @Test
    @DisplayName("les cles rgpd.* existent a l'identique en FR, NL et EN")
    void aucuneCleManquanteEnNlOuEn() throws IOException {
        Set<String> francais = clesRgpd("/i18n/messages.properties");
        Set<String> neerlandais = clesRgpd("/i18n/messages_nl.properties");
        Set<String> anglais = clesRgpd("/i18n/messages_en.properties");

        assertThat(francais).isNotEmpty();
        assertThat(neerlandais).containsExactlyInAnyOrderElementsOf(francais);
        assertThat(anglais).containsExactlyInAnyOrderElementsOf(francais);
    }

    private static Set<String> clesRgpd(String ressource) throws IOException {
        Properties proprietes = new Properties();
        try (InputStream flux = CatalogueTraitementsTest.class.getResourceAsStream(ressource)) {
            assertThat(flux).as("fichier %s absent du classpath", ressource).isNotNull();
            proprietes.load(new InputStreamReader(flux, StandardCharsets.UTF_8));
        }
        return proprietes.stringPropertyNames().stream()
                .filter(cle -> cle.startsWith("rgpd."))
                .collect(Collectors.toSet());
    }
}
