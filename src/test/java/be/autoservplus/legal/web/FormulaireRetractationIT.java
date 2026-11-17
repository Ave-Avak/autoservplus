package be.autoservplus.legal.web;

import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le modèle de formulaire de rétractation, page et PDF, sans connexion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Formulaire de rétractation : page publique et PDF (intégration)")
class FormulaireRetractationIT extends SocleIntegration {

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("la page se rend sans connexion, dans les trois langues, préremplie du garage")
    void pagePublique() throws Exception {
        mvc.perform(get("/retractation/formulaire").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Modèle de formulaire de rétractation")))
                .andExpect(content().string(containsString("Biffez la mention inutile")))
                .andExpect(content().string(containsString("/retractation/formulaire/pdf")))
                .andExpect(content().string(not(containsString("[l&#39;entreprise"))));
        mvc.perform(get("/retractation/formulaire").locale(Locale.of("nl")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Modelformulier voor herroeping")))
                .andExpect(content().string(containsString("Doorhalen wat niet van toepassing is")));
        mvc.perform(get("/retractation/formulaire").locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Model withdrawal form")))
                .andExpect(content().string(containsString("Delete as appropriate")));
    }

    @Test
    @DisplayName("le PDF se télécharge sans connexion, nommé selon la langue")
    void pdfPublic() throws Exception {
        mvc.perform(get("/retractation/formulaire/pdf").locale(Locale.of("nl")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        containsString("formulaire-retractation-nl.pdf")));
    }

    @Test
    @DisplayName("l'accueil et les pages légales mènent au formulaire")
    void liensPermanents() throws Exception {
        mvc.perform(get("/").locale(Locale.FRENCH))
                .andExpect(content().string(containsString("/retractation/formulaire")));
        mvc.perform(get("/cgv").locale(Locale.FRENCH))
                .andExpect(content().string(containsString("/retractation/formulaire")));
    }
}
