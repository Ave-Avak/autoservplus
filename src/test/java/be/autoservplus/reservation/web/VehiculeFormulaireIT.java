package be.autoservplus.reservation.web;

import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Numéro de châssis saisi au formulaire du véhicule (M68, Q-H-54, Q-H-55). */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Formulaire du véhicule, numéro de châssis (intégration)")
class VehiculeFormulaireIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private JdbcTemplate jdbc;

    private String membre;
    private String plaque;

    @BeforeEach
    void setUp() {
        int n = COMPTEUR.getAndIncrement();
        membre = "chassis-" + n + "-" + UUID.randomUUID().toString().substring(0, 6) + "@exemple.be";
        plaque = "9-CH" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        utilisateurs.save(MembresDeTest.membre(membre, "$2a$12$h", "Dupont", "Marie"));
    }

    private MockHttpServletRequestBuilder formulaire(String action, String chassis) {
        return post(action).with(user(membre)).with(csrf()).locale(Locale.FRENCH)
                .param("plaque", plaque).param("marque", "Volkswagen").param("modele", "Golf")
                .param("motorisation", "DIESEL").param("numeroChassis", chassis);
    }

    private List<String> chassisEnBase() {
        return jdbc.queryForList("SELECT numero_chassis FROM vehicule WHERE plaque = ?", String.class, plaque);
    }

    @Test
    @DisplayName("l'ajout enregistre le châssis, sans espace et en majuscules")
    void ajoutConserveLeChassis() throws Exception {
        mvc.perform(formulaire("/mes-vehicules/nouveau", " wvw zzz1kzaw000003 "))
                .andExpect(redirectedUrl("/mes-vehicules"));

        assertThat(chassisEnBase()).containsExactly("WVWZZZ1KZAW000003");
    }

    @Test
    @DisplayName("un champ vidé à la modification enregistre NULL, pas une chaîne vide")
    void modificationVideEnregistreNull() throws Exception {
        mvc.perform(formulaire("/mes-vehicules/nouveau", "VF1RJA00000000002"));
        UUID reference = jdbc.queryForObject("SELECT reference FROM vehicule WHERE plaque = ?", UUID.class, plaque);

        mvc.perform(formulaire("/mes-vehicules/" + reference + "/modifier", ""))
                .andExpect(status().is3xxRedirection());

        assertThat(chassisEnBase()).hasSize(1).containsNull();
    }

    @Test
    @DisplayName("plus de 20 caractères : refusé avec un message traduit, rien d'enregistré")
    void longueurRefusee() throws Exception {
        mvc.perform(formulaire("/mes-vehicules/nouveau", "X".repeat(21)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Le numéro de châssis compte 20 caractères au plus.")));

        assertThat(chassisEnBase()).isEmpty();
    }
}
