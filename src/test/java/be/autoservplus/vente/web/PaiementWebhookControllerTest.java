package be.autoservplus.vente.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.vente.service.IssueRelecture;
import be.autoservplus.vente.service.PaiementService;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.service.PrestataireIndisponibleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint webhook : accessible sans session ni jeton CSRF (configuration miroir
 * de SecuriteConfig), il delegue au service qui relit le statut aupres du
 * prestataire — le corps de la requete n est jamais cru.
 */
@WebMvcTest(controllers = PaiementWebhookController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import(PaiementWebhookControllerTest.SecuriteTest.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("PaiementWebhookController")
class PaiementWebhookControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private PaiementService service;

    @TestConfiguration
    static class SecuriteTest {
        @Bean
        SecurityFilterChain filtresTest(HttpSecurity http) throws Exception {
            // Miroir de la configuration reelle : /webhooks/** public et hors CSRF.
            return http
                    .authorizeHttpRequests(a -> a
                            .requestMatchers("/webhooks/**").permitAll()
                            .anyRequest().authenticated())
                    .csrf(c -> c.ignoringRequestMatchers("/webhooks/**"))
                    .build();
        }
    }

    @Test
    @DisplayName("prestataire injoignable : l erreur REMONTE, pour que le prestataire rejoue")
    void panneDuPrestataireNonAbsorbee() throws Exception {
        // Asymetrie deliberee avec les ecrans, qui traduisent cette meme exception en
        // message lisible. Ici le correspondant est une machine : un 200 signifierait
        // « c est traite » et le prestataire ne rappellerait jamais, laissant la
        // commande en attente apres un encaissement reel. Le traitement etant
        // idempotent, le rejeu provoque par l erreur est sans risque.
        doThrow(new PrestataireIndisponibleException("prestataire injoignable"))
                .when(service).traiterNotification("tr_panne");

        assertThatThrownBy(() -> mvc.perform(
                post("/webhooks/paiement").param("id", "tr_panne")))
                .hasRootCauseInstanceOf(PrestataireIndisponibleException.class);
    }

    @Test
    @DisplayName("POST anonyme sans CSRF : 200, l'identifiant est delegue au service")
    void notificationAnonymeAcceptee(CapturedOutput journal) throws Exception {
        doReturn(new IssueRelecture(StatutPaiement.REUSSI,
                IssueRelecture.Effet.FACTURE_EMISE))
                .when(service).traiterNotification("tr_fictif_0001");

        mvc.perform(post("/webhooks/paiement").param("id", "tr_fictif_0001"))
                .andExpect(status().isOk());

        verify(service).traiterNotification("tr_fictif_0001");
        // Formulation verrouillee ici parce que docs/deploiement.md la cite mot pour
        // mot : une reformulation silencieuse rendrait la documentation fausse.
        assertThat(journal).contains("Notification du prestataire pour le paiement "
                + "tr_fictif_0001 : statut relu = REUSSI, commande passee PAYEE, "
                + "facture emise.");
    }

    @Test
    @DisplayName("prestataire injoignable : aucune ligne n annonce un statut non relu")
    void pasDeTraceQuandLeStatutNAPuEtreRelu(CapturedOutput journal) {
        // Le 500 provoque un rejeu ; ecrire « statut relu = ... » alors que la
        // relecture a echoue rendrait la trace mensongere au moment exact ou
        // l exploitant s y fie.
        doThrow(new PrestataireIndisponibleException("injoignable"))
                .when(service).traiterNotification("tr_muet");

        assertThatThrownBy(() -> mvc.perform(
                post("/webhooks/paiement").param("id", "tr_muet")))
                .hasRootCauseInstanceOf(PrestataireIndisponibleException.class);

        assertThat(journal).doesNotContain("Notification du prestataire pour le paiement tr_muet");
    }

    @Test
    @DisplayName("reference inconnue : 404 — un identifiant forge ne revele rien")
    void referenceInconnue() throws Exception {
        doThrow(new RessourceIntrouvableException("Paiement", "tr_forge"))
                .when(service).traiterNotification("tr_forge");

        mvc.perform(post("/webhooks/paiement").param("id", "tr_forge"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("parametre id manquant : 400")
    void parametreManquant() throws Exception {
        mvc.perform(post("/webhooks/paiement"))
                .andExpect(status().isBadRequest());
    }
}
