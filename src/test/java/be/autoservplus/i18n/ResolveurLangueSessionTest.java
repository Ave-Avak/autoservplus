package be.autoservplus.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Langue d'une session sans choix exprimé : celle du navigateur, ramenée aux trois
 * langues du site.
 */
@DisplayName("Résolveur de langue de session")
class ResolveurLangueSessionTest {

    private final ResolveurLangueSession resolveur = new ResolveurLangueSession();

    private static MockHttpServletRequest navigateurEn(String langues) {
        MockHttpServletRequest requete = new MockHttpServletRequest();
        requete.addHeader("Accept-Language", langues);
        return requete;
    }

    @Test
    @DisplayName("un navigateur en allemand reçoit le français, jamais une langue que le site ne sert pas")
    void langueHorsPerimetre() {
        assertThat(resolveur.resolveLocale(navigateurEn("de-DE"))).isEqualTo(Locale.forLanguageTag("fr"));
        assertThat(resolveur.resolveLocaleContext(navigateurEn("de-DE")).getLocale())
                .isEqualTo(Locale.forLanguageTag("fr"));
    }

    @Test
    @DisplayName("un navigateur en néerlandais de Belgique reçoit le néerlandais")
    void langueAdmise() {
        assertThat(resolveur.resolveLocale(navigateurEn("nl-BE"))).isEqualTo(Locale.forLanguageTag("nl"));
    }

    @Test
    @DisplayName("un choix hors périmètre enregistré en session est ramené au français")
    void choixHorsPerimetre() {
        MockHttpServletRequest requete = navigateurEn("en");
        resolveur.setLocale(requete, new MockHttpServletResponse(), Locale.forLanguageTag("de"));

        assertThat(resolveur.resolveLocale(requete)).isEqualTo(Locale.forLanguageTag("fr"));
    }
}
