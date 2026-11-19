package be.autoservplus.identite.web;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Migration à la connexion des empreintes écrites avant la normalisation NFKC.
 *
 * <p>Le compte porte l'empreinte BCrypt de la forme brute d'un mot de passe saisi avec un
 * « é » décomposé, comme avant la normalisation. La même saisie ouvre la session et fait
 * réécrire l'empreinte de la forme normalisée, après quoi la saisie précomposée, refusée
 * jusque-là, est acceptée.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Mots de passe normalisés, migration à la connexion (intégration)")
class MotDePasseNormaliseIT extends SocleIntegration {

    private static final String PRECOMPOSE = "phrase de passe déjà ancienne";
    private static final String DECOMPOSE = "phrase de passe déjà ancienne";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;

    private String connecter(String email, String motDePasse) throws Exception {
        return mvc.perform(post("/connexion").with(csrf())
                        .param("email", email).param("password", motDePasse))
                .andReturn().getResponse().getRedirectedUrl();
    }

    @Test
    @DisplayName("empreinte de la forme brute : la même saisie passe et migre l'empreinte, l'autre forme passe ensuite")
    void migrationALaConnexion() throws Exception {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
        Utilisateur membre = new Utilisateur("normalise@exemple.be", bcrypt.encode(DECOMPOSE),
                "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        membre = utilisateurs.saveAndFlush(membre);
        String ancienne = membre.getMotDePasseHache();

        assertThat(connecter(membre.getEmail(), PRECOMPOSE)).isEqualTo("/connexion?erreur");
        assertThat(connecter(membre.getEmail(), DECOMPOSE)).isEqualTo("/mon-compte");

        String nouvelle = utilisateurs.findById(membre.getId()).orElseThrow().getMotDePasseHache();
        assertThat(nouvelle).isNotEqualTo(ancienne);
        assertThat(bcrypt.matches(PRECOMPOSE, nouvelle)).as("empreinte de la forme NFKC").isTrue();
        assertThat(connecter(membre.getEmail(), PRECOMPOSE)).isEqualTo("/mon-compte");
    }

    @Test
    @DisplayName("forme normalisée au-delà de 72 octets : la connexion passe, l'empreinte brute reste")
    void formeNormaliseeTropLongue() throws Exception {
        // « ½ » occupe deux octets, sa forme NFKC « 1⁄2 » en occupe cinq : 60 octets bruts,
        // 150 normalisés, que BCryptPasswordEncoder.encode refuse de hacher.
        String brut = "½".repeat(30);
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
        Utilisateur membre = new Utilisateur("normalise-long@exemple.be", bcrypt.encode(brut),
                "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        membre = utilisateurs.saveAndFlush(membre);
        String ancienne = membre.getMotDePasseHache();

        assertThat(connecter(membre.getEmail(), brut)).isEqualTo("/mon-compte");

        assertThat(utilisateurs.findById(membre.getId()).orElseThrow().getMotDePasseHache()).isEqualTo(ancienne);
    }

    @Test
    @DisplayName("un mot de passe faux ne migre rien")
    void echecSansMigration() throws Exception {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
        Utilisateur membre = new Utilisateur("normalise-echec@exemple.be", bcrypt.encode(DECOMPOSE),
                "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        membre = utilisateurs.saveAndFlush(membre);
        String ancienne = membre.getMotDePasseHache();

        assertThat(connecter(membre.getEmail(), "phrase de passe déjà fausse")).isEqualTo("/connexion?erreur");

        assertThat(utilisateurs.findById(membre.getId()).orElseThrow().getMotDePasseHache()).isEqualTo(ancienne);
    }
}
