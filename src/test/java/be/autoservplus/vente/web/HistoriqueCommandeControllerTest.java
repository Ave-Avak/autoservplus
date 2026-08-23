package be.autoservplus.vente.web;

import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.facturation.service.dto.FactureVue;
import be.autoservplus.retractation.service.RetractationService;
import be.autoservplus.retractation.service.dto.RetractationVue;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.web.dto.CommandeHistoriqueVue;
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
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Rapprochement commande / facture / retractation au niveau du controleur : chaque
 * module repond sur son domaine, l assemblage se fait ici. Le rendu reel du gabarit
 * est couvert par {@code HistoriqueCommandeTemplatesIT}.
 */
@WebMvcTest(controllers = HistoriqueCommandeController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import({HistoriqueCommandeControllerTest.SecuriteTest.class,
        HistoriqueCommandeControllerTest.StubViewResolver.class})
@WithMockUser(username = "marie@exemple.be")
@DisplayName("HistoriqueCommandeController")
class HistoriqueCommandeControllerTest {

    private static final UUID COMMANDE_PAYEE = UUID.randomUUID();
    private static final UUID COMMANDE_EN_ATTENTE = UUID.randomUUID();
    private static final UUID FACTURE = UUID.randomUUID();

    @Autowired private MockMvc mvc;
    @MockitoBean private CommandeService commandes;
    @MockitoBean private FactureService factures;
    @MockitoBean private PanierService paniers;
    @MockitoBean private RetractationService retractations;

    private static CommandeHistoriqueVue ligne(UUID reference, String numero, StatutCommande statut) {
        return new CommandeHistoriqueVue(reference, numero, "22 août 2026", "48,38 €",
                statut, null, null);
    }

    @Test
    @DisplayName("rattache sa facture a chaque commande facturee, et laisse les autres nues")
    void rapprochementDesFactures() throws Exception {
        doReturn(List.of(
                ligne(COMMANDE_PAYEE, "CMD-2026-0002", StatutCommande.PAYEE),
                ligne(COMMANDE_EN_ATTENTE, "CMD-2026-0001", StatutCommande.EN_ATTENTE_PAIEMENT)))
                .when(commandes).historiqueDuMembre("marie@exemple.be");
        doReturn(List.of(new FactureVue(FACTURE, "2026-0007", COMMANDE_PAYEE)))
                .when(factures).facturesDuMembre("marie@exemple.be");
        doReturn(Map.of(
                COMMANDE_PAYEE, RetractationVue.sansDemande(COMMANDE_PAYEE, true),
                COMMANDE_EN_ATTENTE, RetractationVue.sansDemande(COMMANDE_EN_ATTENTE, false)))
                .when(retractations).etatsDuMembre("marie@exemple.be");

        var resultat = mvc.perform(get("/commandes"))
                .andExpect(status().isOk())
                .andExpect(view().name("vente/commandes"))
                .andExpect(model().attributeExists("commandes"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<CommandeHistoriqueVue> lignes =
                (List<CommandeHistoriqueVue>) resultat.getModelAndView().getModel().get("commandes");
        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(0).estFacturee()).isTrue();
        assertThat(lignes.get(0).referenceFacture()).isEqualTo(FACTURE);
        assertThat(lignes.get(0).numeroFacture()).isEqualTo("2026-0007");
        // Une commande non payee n a pas de facture : aucun bouton ne doit apparaitre.
        assertThat(lignes.get(1).estFacturee()).isFalse();
    }

    @Test
    @DisplayName("l'identite vient du contexte de securite, pour les trois modules")
    void identiteDuContexte() throws Exception {
        doReturn(List.<CommandeHistoriqueVue>of())
                .when(commandes).historiqueDuMembre("marie@exemple.be");
        doReturn(List.<FactureVue>of()).when(factures).facturesDuMembre("marie@exemple.be");
        doReturn(Map.<UUID, RetractationVue>of())
                .when(retractations).etatsDuMembre("marie@exemple.be");

        mvc.perform(get("/commandes")).andExpect(status().isOk());

        // Jamais un identifiant de requete : sans cela, changer un parametre dans
        // l URL suffirait a lire l historique de n importe qui.
        org.mockito.Mockito.verify(commandes).historiqueDuMembre("marie@exemple.be");
        org.mockito.Mockito.verify(factures).facturesDuMembre("marie@exemple.be");
        org.mockito.Mockito.verify(retractations).etatsDuMembre("marie@exemple.be");
    }

    @Test
    @DisplayName("un historique vide s'affiche sans erreur")
    void historiqueVide() throws Exception {
        doReturn(List.<CommandeHistoriqueVue>of())
                .when(commandes).historiqueDuMembre("marie@exemple.be");
        doReturn(List.<FactureVue>of()).when(factures).facturesDuMembre("marie@exemple.be");
        doReturn(Map.<UUID, RetractationVue>of())
                .when(retractations).etatsDuMembre("marie@exemple.be");

        mvc.perform(get("/commandes"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("commandes", List.of()))
                .andExpect(model().attribute("retractations", Map.of()));
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

    /** Thymeleaf est exclu : le nom de vue est verifie, pas son rendu. */
    @TestConfiguration
    static class StubViewResolver {
        @Bean
        ViewResolver viewResolver() {
            InternalResourceViewResolver resolver = new InternalResourceViewResolver();
            resolver.setPrefix("/WEB-INF/");
            resolver.setSuffix(".jsp");
            return resolver;
        }
    }
}
