package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.EvenementSecuriteCompte;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.EvenementSecuriteCompteRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * « Changer mon mot de passe » depuis le profil : mot de passe actuel exigé, politique
 * commune, autres sessions fermées, identifiant de session renouvelé, et session fermée
 * au cinquième mot de passe actuel incorrect.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Changement du mot de passe depuis le profil (intégration)")
class ChangementMotDePasseIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String ACTUEL = "phrase de passe actuelle du test";
    private static final String NOUVEAU = "autre phrase choisie pour le test";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private EvenementSecuriteCompteRepository evenements;
    @Autowired private SessionRegistry registre;
    @Autowired private PasswordEncoder encodeur;

    private Utilisateur membre;

    @BeforeEach
    void setUp() {
        membre = new Utilisateur("changement-mdp-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode(ACTUEL), "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        membre = utilisateurs.saveAndFlush(membre);
    }

    private ResultActions changer(MockHttpSession session, String actuel, String nouveau,
                                  String confirmation) throws Exception {
        return mvc.perform(post("/mon-profil/mot-de-passe").session(session)
                .with(user(membre.getEmail()).roles("MEMBRE")).with(csrf()).locale(Locale.FRENCH)
                .param("actuel", actuel).param("motDePasse", nouveau)
                .param("confirmation", confirmation));
    }

    private Utilisateur relire() {
        return utilisateurs.findById(membre.getId()).orElseThrow();
    }

    private long evenementsDeType(TypeEvenementSecurite type) {
        return evenements.evenementsDe(membre.getId()).stream()
                .map(EvenementSecuriteCompte::getType).filter(type::equals).count();
    }

    @Test
    @DisplayName("le formulaire s'ouvre depuis le profil")
    void formulaire() throws Exception {
        mvc.perform(get("/mon-profil").with(user(membre.getEmail()).roles("MEMBRE"))
                        .locale(Locale.FRENCH))
                .andExpect(content().string(containsString("href=\"/mon-profil/mot-de-passe\"")));
        mvc.perform(get("/mon-profil/mot-de-passe").with(user(membre.getEmail()).roles("MEMBRE"))
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("autocomplete=\"current-password\"")));
    }

    @Test
    @DisplayName("succès : nouveau secret, journal, autre session fermée, identifiant renouvelé")
    void succes() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String idAvant = session.getId();
        User principal = new User(membre.getEmail(), "x", java.util.List.of());
        registre.registerNewSession("autre-navigateur-" + membre.getId(), principal);

        changer(session, ACTUEL, NOUVEAU, NOUVEAU).andExpect(redirectedUrl("/mon-profil"));

        assertThat(encodeur.matches(NOUVEAU, relire().getMotDePasseHache())).isTrue();
        assertThat(evenementsDeType(TypeEvenementSecurite.MOT_DE_PASSE_CHANGE)).isEqualTo(1);
        assertThat(registre.getSessionInformation("autre-navigateur-" + membre.getId()).isExpired())
                .isTrue();
        assertThat(session.getId()).isNotEqualTo(idAvant);
    }

    @Test
    @DisplayName("mot de passe actuel faux : refus sous le champ, secret inchangé")
    void actuelFaux() throws Exception {
        changer(new MockHttpSession(), "pas le bon mot de passe", NOUVEAU, NOUVEAU)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Le mot de passe actuel est incorrect")));

        assertThat(encodeur.matches(ACTUEL, relire().getMotDePasseHache())).isTrue();
    }

    @Test
    @DisplayName("nouveau mot de passe trop court : refusé par la politique commune")
    void politique() throws Exception {
        changer(new MockHttpSession(), ACTUEL, "court", "court").andExpect(status().isOk());

        assertThat(encodeur.matches(ACTUEL, relire().getMotDePasseHache())).isTrue();
        assertThat(evenementsDeType(TypeEvenementSecurite.MOT_DE_PASSE_CHANGE)).isZero();
    }

    @Test
    @DisplayName("cinquième mot de passe actuel faux : session fermée, journal, compte non verrouillé")
    void cinqEchecs() throws Exception {
        MockHttpSession session = new MockHttpSession();
        for (int i = 1; i < 5; i++) {
            changer(session, "faux " + i, NOUVEAU, NOUVEAU).andExpect(status().isOk());
        }
        assertThat(evenementsDeType(TypeEvenementSecurite.CHANGEMENT_MOT_DE_PASSE_BLOQUE)).isZero();

        changer(session, "faux 5", NOUVEAU, NOUVEAU)
                .andExpect(redirectedUrl("/connexion?session-fermee"));

        assertThat(session.isInvalid()).isTrue();
        assertThat(evenementsDeType(TypeEvenementSecurite.CHANGEMENT_MOT_DE_PASSE_BLOQUE)).isEqualTo(1);
        assertThat(evenements.evenementsDe(membre.getId()).stream()
                .filter(e -> e.getType() == TypeEvenementSecurite.CHANGEMENT_MOT_DE_PASSE_BLOQUE)
                .findFirst().orElseThrow().getAuteur()).as("constat du système, sans auteur").isNull();
        Utilisateur apres = relire();
        assertThat(apres.getVerrouilleJusquA()).isNull();
        assertThat(encodeur.matches(ACTUEL, apres.getMotDePasseHache())).isTrue();

        mvc.perform(get("/connexion").param("session-fermee", "").locale(Locale.FRENCH))
                .andExpect(content().string(containsString("cinq mots de passe actuels incorrects")));
        mvc.perform(get("/connexion").param("session-fermee", "").param("lang", "nl"))
                .andExpect(content().string(containsString("Uw sessie werd gesloten")));
    }

    @Test
    @DisplayName("confirmation différente : refus sans compter d'échec du mot de passe actuel")
    void confirmationDifferente() throws Exception {
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < 5; i++) {
            changer(session, "faux", NOUVEAU, "autre chose").andExpect(status().isOk());
        }

        assertThat(session.isInvalid()).isFalse();
        assertThat(evenementsDeType(TypeEvenementSecurite.CHANGEMENT_MOT_DE_PASSE_BLOQUE)).isZero();
    }
}
