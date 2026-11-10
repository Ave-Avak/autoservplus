package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le compte administrateur de V10, dont le mot de passe est publié, n'ouvre pas le
 * back-office d'une base installée sans graine de démonstration.
 *
 * <p>La connexion est rejouée par le vrai formulaire : une suspension écrite en base ne
 * protège que si {@code UtilisateurDetailsService} en tire bien un compte désactivé. La
 * graine V902 n'est jouée par aucun démarrage de test (elle vit dans {@code db/demo}),
 * elle est donc appliquée ici à la main, en dernier, sur la base propre à cette classe.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Compte administrateur de démonstration (intégration)")
class CompteDemonstrationIT extends SocleIntegration {

    private static final String ADRESSE = "admin@autoservplus.be";
    private static final String MOT_DE_PASSE_PUBLIE = "atelier-demonstration-2026";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource source;

    private String connexion() throws Exception {
        return mvc.perform(post("/connexion").with(csrf())
                        .param("email", ADRESSE).param("password", MOT_DE_PASSE_PUBLIE))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    private StatutUtilisateur statut() {
        return utilisateurs.findByEmailIgnoreCase(ADRESSE).orElseThrow().getStatut();
    }

    private int lignesDeJournal() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM historique_statut_utilisateur h "
                        + "JOIN utilisateur u ON u.id = h.utilisateur_id WHERE u.email = ?",
                Integer.class, ADRESSE);
        return n == null ? 0 : n;
    }

    private void appliquerGraineV902() {
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/demo/V902__reactivation_administrateur_demonstration.sql")).execute(source);
    }

    @Test
    @Order(1)
    @DisplayName("sans graine, le mot de passe publié ne connecte pas")
    void connexionRefuseeSansGraine() throws Exception {
        assertThat(statut()).isEqualTo(StatutUtilisateur.SUSPENDU);
        assertThat(connexion()).contains("erreur");
    }

    @Test
    @Order(2)
    @DisplayName("la graine de démonstration réactive le compte une seule fois")
    void graineReactive() throws Exception {
        appliquerGraineV902();

        assertThat(statut()).isEqualTo(StatutUtilisateur.ACTIF);
        assertThat(lignesDeJournal()).isEqualTo(2);
        assertThat(connexion())
                .as("le mot de passe passe, le second facteur reste à franchir")
                .doesNotContain("erreur");

        appliquerGraineV902();
        assertThat(lignesDeJournal())
                .as("un compte déjà actif n'est pas réactivé une seconde fois")
                .isEqualTo(2);
    }
}
