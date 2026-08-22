package be.autoservplus.facturation.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.facturation.service.PdfFactureService;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvcTest du telechargement : en-tetes de reponse, identite prise du contexte
 * de securite, et 404 sur la facture d autrui. La politique de cache du PDF est
 * couverte par {@code PdfFactureServiceTest}, la chaine complete par
 * {@code TelechargementFactureIT}.
 */
@WebMvcTest(controllers = FactureController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import(FactureControllerTest.SecuriteTest.class)
@WithMockUser(username = "marie@exemple.be")
@DisplayName("FactureController")
class FactureControllerTest {

    private static final UUID REF = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final byte[] PDF = "%PDF-1.4 document".getBytes(StandardCharsets.UTF_8);

    @Autowired private MockMvc mvc;
    @MockitoBean private FactureService factures;
    @MockitoBean private PdfFactureService pdf;

    private static Facture facture() {
        Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        Commande commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                Instant.parse("2026-08-22T14:00:00Z"));
        return Facture.pourCommande("2026-0042", (short) 2026, 42, commande,
                new BigDecimal("21.00"), Instant.parse("2026-08-22T14:30:00Z"));
    }

    @Test
    @DisplayName("sert le PDF en piece jointe, nommee d'apres le numero de facture")
    void sertLePdf() throws Exception {
        Facture facture = facture();
        doReturn(facture).when(factures).pourMembre(REF, "marie@exemple.be");
        doReturn(PDF).when(pdf).pdfDe(facture);
        doReturn("facture-2026-0042.pdf").when(pdf).nomDeFichier(facture);

        mvc.perform(get("/factures/{ref}/pdf", REF))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("facture-2026-0042.pdf")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                // Document nominatif : aucun cache partage ne doit le garder.
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().bytes(PDF));
    }

    @Test
    @DisplayName("l'identite vient du contexte de securite, jamais de l'URL")
    void identiteDuContexte() throws Exception {
        Facture facture = facture();
        doReturn(facture).when(factures).pourMembre(REF, "marie@exemple.be");
        doReturn(PDF).when(pdf).pdfDe(facture);
        doReturn("facture-2026-0042.pdf").when(pdf).nomDeFichier(facture);

        mvc.perform(get("/factures/{ref}/pdf", REF)).andExpect(status().isOk());

        verify(factures).pourMembre(REF, "marie@exemple.be");
    }

    @Test
    @DisplayName("la facture d'autrui repond 404, et aucun PDF n'est fabrique")
    void factureDAutrui() throws Exception {
        // 404 et non 403 : un 403 confirmerait a un tiers que ce numero existe.
        doThrow(new RessourceIntrouvableException("Facture", REF))
                .when(factures).pourMembre(REF, "marie@exemple.be");

        mvc.perform(get("/factures/{ref}/pdf", REF)).andExpect(status().isNotFound());

        verify(pdf, never()).pdfDe(any());
    }

    @TestConfiguration
    static class SecuriteTest {
        @Bean
        SecurityFilterChain chaine(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(acces -> acces.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }
    }
}
