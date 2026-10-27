package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Page d arrivee selon le role, a la fin de chaque parcours de connexion.
 *
 * <p>Le back-office arrive sur {@code /admin}, le membre sur {@code /mon-compte}. Le
 * super-administrateur est le cas qui discrimine : il ne porte que
 * {@code ROLE_SUPER_ADMINISTRATEUR}, et seule la hierarchie des roles l envoie au
 * back-office. Les parcours avec second facteur et changement impose sont tenus par
 * {@code ConnexionSecondFacteurIT}. Ici, les fins qui n y figuraient pas.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Page d'arrivée par rôle (integration)")
class PageArriveeParRoleIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test d integration";
    private static final byte[] SECRET = "secret de vingt octs".getBytes();

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ChiffrementSecretMfa chiffrement;
    @Autowired private Clock horloge;

    private Utilisateur creer(TypeUtilisateur type, boolean enrole) {
        Utilisateur u = new Utilisateur("arrivee-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom", type);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        u = utilisateurs.saveAndFlush(u);
        if (enrole) {
            ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(SECRET, u.getId());
            u.commencerEnrolement(chiffre.donnees(), chiffre.version());
            u.confirmerEnrolement(horloge.instant(), Totp.pasDe(horloge.instant()) - 10);
            u = utilisateurs.saveAndFlush(u);
        }
        return u;
    }

    private MvcResult connecter(Utilisateur u) throws Exception {
        return mvc.perform(post("/connexion").with(csrf())
                .param("email", u.getEmail()).param("password", MOT_DE_PASSE)).andReturn();
    }

    private static MockHttpSession session(MvcResult resultat) {
        return (MockHttpSession) resultat.getRequest().getSession(false);
    }

    private String codeCourant(byte[] secret) {
        return Totp.code(secret, Totp.pasDe(horloge.instant()));
    }

    @Test
    @DisplayName("membre sans étape : « Mon compte » dès le mot de passe")
    void membre() throws Exception {
        assertThat(connecter(creer(TypeUtilisateur.MEMBRE, false)).getResponse().getRedirectedUrl())
                .isEqualTo(PageArrivee.MEMBRE);
    }

    @Test
    @DisplayName("super-administrateur : le back-office à la fin du second facteur")
    void superAdministrateurApresSecondFacteur() throws Exception {
        MockHttpSession session = session(connecter(creer(TypeUtilisateur.SUPER_ADMINISTRATEUR, true)));

        mvc.perform(post("/connexion/etape/second-facteur").session(session).with(csrf())
                        .param("code", codeCourant(SECRET)))
                .andExpect(redirectedUrl(PageArrivee.BACK_OFFICE));
    }

    @Test
    @DisplayName("étape rouverte une fois connecté : retour à la page d'arrivée du rôle")
    void etapeRouverteUneFoisConnecte() throws Exception {
        MockHttpSession admin = session(connecter(creer(TypeUtilisateur.ADMINISTRATEUR, true)));
        mvc.perform(post("/connexion/etape/second-facteur").session(admin).with(csrf())
                .param("code", codeCourant(SECRET)));
        mvc.perform(get("/connexion/etape").session(admin))
                .andExpect(redirectedUrl(PageArrivee.BACK_OFFICE));

        MockHttpSession membre = session(connecter(creer(TypeUtilisateur.MEMBRE, false)));
        mvc.perform(get("/connexion/etape/second-facteur").session(membre))
                .andExpect(redirectedUrl(PageArrivee.MEMBRE));
    }

    /**
     * L enrolement aboutit sur les codes de secours, qui ne s affichent qu une fois.
     * C est leur bouton de sortie qui doit alors mener au back-office.
     */
    @Test
    @DisplayName("enrôlement d'un administrateur : les codes de secours mènent au back-office")
    void enrolementPuisCodes() throws Exception {
        Utilisateur admin = creer(TypeUtilisateur.ADMINISTRATEUR, false);
        MockHttpSession session = session(connecter(admin));
        mvc.perform(get("/connexion/etape/enrolement").session(session));
        Utilisateur enAttente = utilisateurs.findById(admin.getId()).orElseThrow();
        byte[] secret = chiffrement.dechiffrer(enAttente.getMfaSecretChiffre(),
                enAttente.getMfaCleVersion(), enAttente.getId());

        MvcResult confirmation = mvc.perform(post("/connexion/etape/enrolement").session(session)
                .with(csrf()).param("code", codeCourant(secret))).andReturn();

        mvc.perform(get(confirmation.getResponse().getRedirectedUrl()).session(session)
                        .flashAttrs(confirmation.getFlashMap()).locale(Locale.FRENCH))
                .andExpect(content().string(allOf(
                        containsString("<a class=\"btn-cta\" href=\"/admin\">"),
                        not(containsString("<a class=\"btn-cta\" href=\"/mon-compte\">")))));
    }
}
