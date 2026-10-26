package be.autoservplus.catalogue.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le bouton « Prendre rendez-vous » d une fiche de prestation mene a la reservation.
 *
 * <p>Il pointait {@code /rendez-vous/nouveau}, une route qui n a jamais existe : un
 * membre connecte recevait un 404. Le cas suit le lien tel que la page le rend, et non
 * une adresse recopiee dans le test, qui aurait pu diverger du gabarit.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Fiche de prestation (integration)")
class FichePrestationIT extends SocleIntegration {

    private static final Pattern BOUTON_RENDEZ_VOUS =
            Pattern.compile("<a class=\"btn-cta\" href=\"([^\"]+)\"\\s*>Prendre rendez-vous</a>");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UtilisateurRepository utilisateurs;

    @Test
    @DisplayName("le bouton « Prendre rendez-vous » ouvre le formulaire de réservation")
    void boutonRendezVousOuvreLaReservation() throws Exception {
        UUID prestation = jdbc.queryForObject(
                "SELECT reference FROM service WHERE actif AND deleted_at IS NULL LIMIT 1", UUID.class);
        Utilisateur membre = new Utilisateur("fiche-prestation@exemple.be", "{noop}inutilise",
                "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        utilisateurs.saveAndFlush(membre);

        String fiche = mvc.perform(get("/services/{ref}", prestation)
                        .with(user(membre.getEmail()).roles("MEMBRE")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher bouton = BOUTON_RENDEZ_VOUS.matcher(fiche);
        assertThat(bouton.find()).as("bouton « Prendre rendez-vous » rendu").isTrue();

        mvc.perform(get(bouton.group(1)).with(user(membre.getEmail()).roles("MEMBRE")))
                .andExpect(status().isOk());
    }
}
