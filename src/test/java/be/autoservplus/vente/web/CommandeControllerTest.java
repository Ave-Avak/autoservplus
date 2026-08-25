package be.autoservplus.vente.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.vente.service.CgvNonAccepteesException;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.service.PaiementImpossibleException;
import be.autoservplus.vente.service.PaiementService;
import be.autoservplus.vente.service.PrestataireIndisponibleException;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.StockInsuffisantException;
import be.autoservplus.vente.web.dto.ConfirmationCommandeVue;
import be.autoservplus.vente.web.dto.PanierVue;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.ViewResolver;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * WebMvcTest du controleur de commande : delegation au service (identite du
 * contexte, IP de la requete), PRG vers la confirmation, reaffichage du
 * recapitulatif avec le message sous la bonne zone, CSRF, 404 non catche.
 * Le rendu reel des templates est couvert par {@code CommandeTemplatesIT}.
 */
@WebMvcTest(controllers = CommandeController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import({CommandeControllerTest.SecuriteTest.class, CommandeControllerTest.StubViewResolver.class})
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("CommandeController")
class CommandeControllerTest {

    private static final UUID REF = UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Autowired private MockMvc mvc;
    @MockitoBean private CommandeService service;
    @MockitoBean private PanierService paniers;
    @MockitoBean private PaiementService paiements;

    @TestConfiguration
    static class SecuriteTest {
        @Bean
        SecurityFilterChain filtresTest(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .csrf(Customizer.withDefaults())
                    .build();
        }
    }

    @TestConfiguration
    static class StubViewResolver {
        @Bean
        ViewResolver stubViewResolver() {
            return (viewName, locale) -> (model, request, response) -> { /* no-op */ };
        }
    }

    private PanierVue panierNonVide;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(service, paniers, paiements);
        panierNonVide = new PanierVue(List.of(), 5, "70,01 €", "10,20 €", "80,21 €",
                false, false, false);
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("GET /commande rend le recapitulatif quand le panier est rempli")
    void recapitulatif() throws Exception {
        doReturn(panierNonVide).when(paniers).panierDuMembre("marie@exemple.be");

        mvc.perform(get("/commande"))
                .andExpect(status().isOk())
                .andExpect(view().name("vente/recapitulatif"))
                .andExpect(model().attributeExists("panier"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("GET /commande avec panier vide : retour au panier avec message")
    void recapitulatifPanierVide() throws Exception {
        doReturn(PanierVue.vide()).when(paniers).panierDuMembre("marie@exemple.be");

        mvc.perform(get("/commande"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/panier"))
                .andExpect(flash().attributeExists("erreur"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST avec CGV cochees : delegation (identite, case, IP) puis PRG vers la confirmation")
    void validationNominale() throws Exception {
        doReturn(new ConfirmationCommandeVue(REF, "CMD-2026-0001", "80,21 €", true))
                .when(service).passerCommande("marie@exemple.be", true, false, "127.0.0.1");

        mvc.perform(post("/commande").with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commande/" + REF + "/confirmation"));

        verify(service).passerCommande("marie@exemple.be", true, false, "127.0.0.1");
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST sans case CGV : le serveur refuse, recapitulatif reaffiche avec l'erreur CGV")
    void validationSansCgv() throws Exception {
        doThrow(new CgvNonAccepteesException())
                .when(service).passerCommande("marie@exemple.be", false, false, "127.0.0.1");
        doReturn(panierNonVide).when(paniers).panierDuMembre("marie@exemple.be");

        mvc.perform(post("/commande").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("vente/recapitulatif"))
                .andExpect(model().attributeExists("erreurCgv"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("stock insuffisant a la conversion : recapitulatif reaffiche, message cote lignes")
    void validationStockInsuffisant() throws Exception {
        doThrow(new StockInsuffisantException("Plaquettes avant", 2, 1))
                .when(service).passerCommande("marie@exemple.be", true, false, "127.0.0.1");
        doReturn(panierNonVide).when(paniers).panierDuMembre("marie@exemple.be");

        mvc.perform(post("/commande").with(csrf()).param("cgv", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("vente/recapitulatif"))
                .andExpect(model().attributeExists("erreurLignes"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("prestataire injoignable a l initiation : message lisible, jamais un 500")
    void initiationPrestataireInjoignable(CapturedOutput journal) throws Exception {
        // Une page d erreur au moment de payer est le pire point de rupture d un
        // parcours d achat. La commande reste valide et payable.
        doThrow(new PrestataireIndisponibleException("detail technique du prestataire"))
                .when(paiements).initierPaiement(REF, "marie@exemple.be");

        mvc.perform(post("/commande/{ref}/payer", REF).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commande/" + REF + "/confirmation"))
                .andExpect(flash().attributeExists("erreur"))
                // Le detail technique ne doit pas atterrir a l ecran.
                .andExpect(flash().attribute("erreur",
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("detail technique"))));

        // ... mais il doit atterrir dans le journal. Les deux moities comptent
        // ensemble : ecran muet SANS trace, c est le defaut constate en repetition,
        // ou un paiement echouait sans que rien ne dise pourquoi.
        assertThat(journal).contains(
                "Prestataire de paiement indisponible pour la commande " + REF);
        assertThat(journal).contains("detail technique du prestataire");
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("prestataire injoignable au retour : ni succes ni echec affirme")
    void retourPrestataireInjoignable(CapturedOutput journal) throws Exception {
        // On ne sait pas si le paiement a abouti : affirmer un echec pousserait a
        // payer une seconde fois un encaissement peut-etre deja passe.
        doThrow(new PrestataireIndisponibleException("injoignable"))
                .when(paiements).constaterRetour(REF, "marie@exemple.be");

        mvc.perform(get("/commande/{ref}/retour", REF))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commande/" + REF + "/confirmation"))
                .andExpect(flash().attributeExists("erreur"))
                .andExpect(flash().attributeCount(1));

        // L ecran est volontairement muet sur la cause : le journal est alors le seul
        // endroit ou elle existe.
        assertThat(journal).contains(
                "Prestataire de paiement indisponible pour la commande " + REF);
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("GET retour : commande payee, message de succes")
    void retourPaye() throws Exception {
        doReturn(true).when(paiements).constaterRetour(REF, "marie@exemple.be");

        mvc.perform(get("/commande/{ref}/retour", REF))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commande/" + REF + "/confirmation"))
                .andExpect(flash().attributeExists("succes"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("GET retour : paiement non abouti, message d attente et NON d echec")
    void retourNonAbouti() throws Exception {
        // Le retour survient aussi apres un abandon, et un paiement peut encore
        // aboutir plus tard (virement). Annoncer un echec pousserait a payer deux fois.
        doReturn(false).when(paiements).constaterRetour(REF, "marie@exemple.be");

        mvc.perform(get("/commande/{ref}/retour", REF))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erreur"))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("GET confirmation rend la vue du proprietaire")
    void confirmation() throws Exception {
        doReturn(new ConfirmationCommandeVue(REF, "CMD-2026-0001", "80,21 €", true))
                .when(service).confirmation(REF, "marie@exemple.be");

        mvc.perform(get("/commande/{ref}/confirmation", REF))
                .andExpect(status().isOk())
                .andExpect(view().name("vente/commande-confirmee"))
                .andExpect(model().attributeExists("commande"));
    }

    @Test
    @WithMockUser(username = "intrus@exemple.be")
    @DisplayName("confirmation d'une commande d'autrui : 404")
    void confirmationOwnership() throws Exception {
        doThrow(new RessourceIntrouvableException("Commande", REF))
                .when(service).confirmation(REF, "intrus@exemple.be");

        mvc.perform(get("/commande/{ref}/confirmation", REF))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST /payer redirige vers l'URL du prestataire rendue par le service")
    void payerRedirigeVersLePrestataire() throws Exception {
        doReturn("/paiement-fictif/tr_fictif_0001")
                .when(paiements).initierPaiement(REF, "marie@exemple.be");

        mvc.perform(post("/commande/{ref}/payer", REF).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/paiement-fictif/tr_fictif_0001"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST /payer sur une commande qui n'attend plus : retour confirmation avec message")
    void payerImpossible() throws Exception {
        doThrow(new PaiementImpossibleException())
                .when(paiements).initierPaiement(REF, "marie@exemple.be");

        mvc.perform(post("/commande/{ref}/payer", REF).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commande/" + REF + "/confirmation"))
                .andExpect(flash().attributeExists("erreur"));
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("POST sans jeton CSRF est rejete")
    void postSansCsrfRejete() throws Exception {
        mvc.perform(post("/commande").param("cgv", "true"))
                .andExpect(status().isForbidden());
    }
}
