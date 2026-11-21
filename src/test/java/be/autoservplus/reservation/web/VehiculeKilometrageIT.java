package be.autoservplus.reservation.web;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Kilométrage en baisse sur le formulaire de véhicule : l'aide du champ énonce la règle,
 * l'erreur dit le fait, avec le dernier relevé. La même phrase s'affichait deux fois
 * sous le champ, une fois comme aide et une fois comme erreur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Formulaire de véhicule, kilométrage en baisse (intégration)")
class VehiculeKilometrageIT extends SocleIntegration {

    private static final String MARIE = "marie.kilometrage@exemple.be";
    private static final String AIDE = "ne peut pas diminuer";
    private static final String ERREUR = "inférieur au dernier relevé";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;

    @Test
    @DisplayName("l'erreur cite le dernier relevé et ne répète pas l'aide")
    void erreurDistincteDeLAide() throws Exception {
        Utilisateur marie = new Utilisateur(MARIE, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        marie.confirmerAdresseEmail();
        marie = utilisateurs.save(marie);
        Vehicule golf = new Vehicule(marie, "1-KLM-001", "VW", "Golf", Motorisation.DIESEL);
        golf.mettreAJourKilometrage(85000);
        golf = vehicules.saveAndFlush(golf);

        String page = mvc.perform(post("/mes-vehicules/{ref}/modifier", golf.getReference())
                        .with(user(MARIE).roles("MEMBRE")).with(csrf()).locale(Locale.FRENCH)
                        .param("plaque", "1-KLM-001").param("marque", "VW").param("modele", "Golf")
                        .param("motorisation", "DIESEL").param("kilometrage", "80000"))
                .andReturn().getResponse().getContentAsString();

        assertThat(compter(page, AIDE)).as("aide du champ").isEqualTo(1);
        assertThat(compter(page, ERREUR)).as("erreur du champ").isEqualTo(1);
        // Le dernier relevé, quel que soit le séparateur de milliers de la langue.
        assertThat(page.replaceAll("[\\u00A0\\u202F ]", "")).contains(ERREUR.replace(" ", "") + ",85000km.");
        assertThat(vehicules.findById(golf.getId()).orElseThrow().getKilometrage()).isEqualTo(85000);
    }

    private static int compter(String texte, String motif) {
        return texte.split(java.util.regex.Pattern.quote(motif), -1).length - 1;
    }
}
