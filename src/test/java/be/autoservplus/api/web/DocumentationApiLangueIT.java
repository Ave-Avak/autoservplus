package be.autoservplus.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.autoservplus.support.SocleIntegration;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * La documentation de l'API ne voit que trois langues, quel que soit l'en-tête reçu.
 *
 * <p>springdoc garde un document par langue demandée. Avant la 2.9.1, ce cache n'avait pas
 * de borne et un client qui variait {@code Accept-Language} le remplissait sans fin
 * (GHSA-rhhx-6j8h-8cvw). La langue que springdoc reçoit est résolue par le
 * {@code LocaleResolver} de l'application, comme tout paramètre {@link Locale} d'un
 * contrôleur : le test vérifie donc la langue que ce résolveur donne à chaque requête de la
 * documentation, plutôt que de lire l'état interne de springdoc.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithAnonymousUser
@DisplayName("Documentation de l'API, langue bornée (intégration)")
class DocumentationApiLangueIT extends SocleIntegration {

    private static final Set<String> LANGUES_SERVIES = Set.of("fr", "nl", "en");

    @Autowired private MockMvc mvc;

    @ParameterizedTest(name = "Accept-Language: {0}")
    @ValueSource(strings = {"ja", "de-DE", "zh-Hant-TW", "x-inconnue", "fr-BE", "nl-BE",
            "en-GB;q=0.9, de;q=0.8", "*", "tlh", "a-b-c-d-e-f-g-h"})
    @DisplayName("ramène toute langue demandée à fr, nl ou en")
    void langueBornee(String langue) throws Exception {
        MvcResult reponse = mvc.perform(get("/v3/api-docs").header("Accept-Language", langue))
                .andExpect(status().isOk())
                .andReturn();

        Locale resolue = RequestContextUtils.getLocale(reponse.getRequest());
        assertThat(LANGUES_SERVIES).contains(resolue.getLanguage());
        assertThat(resolue.getCountry()).isEmpty();
    }
}
