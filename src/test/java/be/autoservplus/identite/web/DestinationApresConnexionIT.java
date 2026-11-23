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

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La page demandée avant la connexion est celle où la connexion aboutit, par le vrai
 * formulaire et à travers ses étapes : la requête sauvegardée vit dans la session, que
 * chaque étape doit conserver.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Destination après connexion (intégration)")
class DestinationApresConnexionIT extends SocleIntegration {

    private static final String MOT_DE_PASSE = "phrase de passe du test d integration";
    private static final byte[] SECRET = "secret de vingt octs".getBytes();

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ChiffrementSecretMfa chiffrement;
    @Autowired private Clock horloge;

    private Utilisateur creer(boolean enrole, boolean doitChanger) {
        Utilisateur u = new Utilisateur("destination-" + UUID.randomUUID() + "@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        if (doitChanger) {
            u.exigerChangementMotDePasse();
        }
        u = utilisateurs.saveAndFlush(u);
        if (enrole) {
            ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(SECRET, u.getId());
            u.commencerEnrolement(chiffre.donnees(), chiffre.version());
            u.confirmerEnrolement(horloge.instant(), Totp.pasDe(horloge.instant()) - 10);
            u = utilisateurs.saveAndFlush(u);
        }
        return u;
    }

    /** Un visiteur anonyme demande une page protégée : il est renvoyé à la connexion. */
    private MockHttpSession demander(String adresse) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get(adresse).session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    private String connecter(Utilisateur u, MockHttpSession session) throws Exception {
        return mvc.perform(post("/connexion").session(session).with(csrf())
                        .param("email", u.getEmail()).param("password", MOT_DE_PASSE))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    @Test
    @DisplayName("la connexion aboutit sur la page demandée, paramètres compris, une seule fois")
    void pageDemandee() throws Exception {
        Utilisateur membre = creer(false, false);
        MockHttpSession session = demander("/commandes?page=2");

        assertThat(connecter(membre, session)).isEqualTo("/commandes?page=2");
        mvc.perform(get("/commandes?page=2").session(session)).andExpect(status().isOk());

        // La requête sauvegardée est consommée : une connexion suivante dans la même
        // session retrouve la page d'arrivée du rôle.
        assertThat(connecter(membre, session)).isEqualTo("/mon-compte");
    }

    @Test
    @DisplayName("sans page demandée, la page d'arrivée du rôle")
    void sansPageDemandee() throws Exception {
        assertThat(connecter(creer(false, false), new MockHttpSession())).isEqualTo("/mon-compte");
    }

    @Test
    @DisplayName("un hôte forgé dans la requête sauvegardée ne sort pas du site")
    void hoteForge() throws Exception {
        Utilisateur membre = creer(false, false);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/mes-rendez-vous").session(session)
                        .with(requete -> {
                            requete.setServerName("site-pirate.example");
                            requete.addHeader("Host", "site-pirate.example");
                            return requete;
                        }))
                .andExpect(status().is3xxRedirection());

        assertThat(connecter(membre, session)).isEqualTo("/mes-rendez-vous");
    }

    @Test
    @DisplayName("la page demandée survit au second facteur")
    void apresLeSecondFacteur() throws Exception {
        Utilisateur membre = creer(true, false);
        MockHttpSession session = demander("/commandes");

        assertThat(connecter(membre, session)).isEqualTo("/connexion/etape/second-facteur");
        mvc.perform(post("/connexion/etape/second-facteur").session(session).with(csrf())
                        .param("code", Totp.code(SECRET, Totp.pasDe(horloge.instant()))))
                .andExpect(redirectedUrl("/commandes"));
        mvc.perform(get("/commandes").session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("la page demandée survit au changement imposé du mot de passe")
    void apresLeChangementImpose() throws Exception {
        Utilisateur membre = creer(false, true);
        MockHttpSession session = demander("/mes-vehicules");

        assertThat(connecter(membre, session)).isEqualTo("/connexion/etape/mot-de-passe");
        String nouveau = "une toute nouvelle phrase choisie";
        mvc.perform(post("/connexion/etape/mot-de-passe").session(session).with(csrf())
                        .param("motDePasse", nouveau).param("confirmation", nouveau))
                .andExpect(redirectedUrl("/mes-vehicules"));
        mvc.perform(get("/mes-vehicules").session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("un membre renvoyé vers le back-office reçoit un 403, sans retour à la connexion")
    void pageInterditeAuRole() throws Exception {
        Utilisateur membre = creer(false, false);
        MockHttpSession session = demander("/admin/comptes");

        assertThat(connecter(membre, session)).isEqualTo("/admin/comptes");
        // Refus net : ni redirection vers la connexion, ni nouvelle requête sauvegardée.
        mvc.perform(get("/admin/comptes").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/comptes").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/mon-compte").session(session)).andExpect(status().isOk());
    }
}
