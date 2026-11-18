package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * La connexion ne dit pas si une adresse a un compte, ni par sa réponse ni par le travail
 * qu'elle fait.
 *
 * <p>Avant ce correctif, une adresse connue passait à {@code ?bloque} à la sixième
 * tentative, et un compte verrouillé ou désactivé répondait sans BCrypt. La durée n'est
 * pas mesurée ici, elle dépend du poste : le test prouve sa cause, un seul appel à
 * l'encodeur pour chaque tentative, quelle que soit l'adresse.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Connexion sans oracle d'énumération (integration)")
class ConnexionSansOracleIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test sans oracle";
    private static final String REFUS = "/connexion?erreur";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private Clock horloge;
    @MockitoSpyBean private PasswordEncoder encodeur;

    private Utilisateur actif;

    @BeforeEach
    void preparer() {
        actif = creer(StatutUtilisateur.ACTIF);
    }

    private Utilisateur creer(StatutUtilisateur statut) {
        Utilisateur u = new Utilisateur("oracle-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        u.confirmerAdresseEmail();
        u.setStatut(statut);
        return utilisateurs.saveAndFlush(u);
    }

    private MvcResult connecter(String email, String motDePasse) throws Exception {
        return mvc.perform(post("/connexion").with(csrf())
                .param("email", email).param("password", motDePasse)).andReturn();
    }

    private void verrouiller(Utilisateur u) throws Exception {
        for (int i = 0; i < 5; i++) {
            connecter(u.getEmail(), "mauvais " + i);
        }
        assertThat(utilisateurs.findById(u.getId()).orElseThrow()
                .estVerrouille(horloge.instant())).isTrue();
    }

    /** La page de refus suivie avec la session de la tentative, sans son jeton CSRF. */
    private String pageSuivie(MvcResult tentative) throws Exception {
        MockHttpSession session = (MockHttpSession) tentative.getRequest().getSession(false);
        // MockMvc lit le paramètre sans valeur de « ?erreur » comme absent, un vrai
        // conteneur comme une chaîne vide : il est donc posé explicitement.
        assertThat(tentative.getResponse().getRedirectedUrl()).isEqualTo(REFUS);
        var requete = get("/connexion").param("erreur", "").locale(Locale.FRENCH);
        if (session != null) {
            requete.session(session);
        }
        String page = mvc.perform(requete).andReturn().getResponse().getContentAsString();
        return page.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\"");
    }

    private static boolean sessionOuverte(MvcResult tentative) {
        HttpSession session = tentative.getRequest().getSession(false);
        return session != null && session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) != null;
    }

    @Test
    @DisplayName("six mots de passe faux : même redirection pour une adresse inconnue et pour un compte")
    void sixTentativesIdentiques() throws Exception {
        List<String> inconnue = new ArrayList<>();
        List<String> connue = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            inconnue.add(connecter("personne-" + i + "@exemple.be", "mauvais " + i)
                    .getResponse().getRedirectedUrl());
            connue.add(connecter(actif.getEmail(), "mauvais " + i).getResponse().getRedirectedUrl());
        }

        assertThat(inconnue).containsOnly(REFUS).hasSize(6);
        assertThat(connue).containsOnly(REFUS).hasSize(6);
        assertThat(utilisateurs.findById(actif.getId()).orElseThrow()
                .estVerrouille(horloge.instant())).isTrue();
    }

    @Test
    @DisplayName("pendant un verrou, bon et mauvais mot de passe donnent la même redirection et la même page")
    void pendantUnVerrou() throws Exception {
        verrouiller(actif);

        MvcResult juste = connecter(actif.getEmail(), MOT_DE_PASSE);
        MvcResult faux = connecter(actif.getEmail(), "encore faux");

        assertThat(juste.getResponse().getRedirectedUrl()).isEqualTo(REFUS);
        assertThat(faux.getResponse().getRedirectedUrl()).isEqualTo(REFUS);
        assertThat(sessionOuverte(juste)).isFalse();
        String pageJuste = pageSuivie(juste);
        assertThat(pageJuste).isEqualTo(pageSuivie(faux))
                .contains("Identifiants incorrects ou connexion temporairement bloquée.");
    }

    @Test
    @DisplayName("un compte suspendu avec son mot de passe reçoit le refus commun")
    void compteSuspendu() throws Exception {
        Utilisateur suspendu = creer(StatutUtilisateur.SUSPENDU);

        MvcResult juste = connecter(suspendu.getEmail(), MOT_DE_PASSE);

        assertThat(juste.getResponse().getRedirectedUrl()).isEqualTo(REFUS);
        assertThat(sessionOuverte(juste)).isFalse();
    }

    @Test
    @DisplayName("le mot de passe est haché une fois, compte verrouillé, suspendu ou adresse inconnue")
    void unHachageParTentative() throws Exception {
        verrouiller(actif);
        Utilisateur suspendu = creer(StatutUtilisateur.SUSPENDU);
        List<String> adresses = List.of(actif.getEmail(), suspendu.getEmail(),
                "personne-hachage@exemple.be");

        for (String adresse : adresses) {
            clearInvocations(encodeur);
            connecter(adresse, MOT_DE_PASSE);
            verify(encodeur, times(1)).matches(any(CharSequence.class), anyString());
        }
    }
}
