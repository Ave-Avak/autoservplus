package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renvoi du courriel de verification, de bout en bout.
 *
 * <p>Test d integration et non {@code @WebMvcTest} : le defaut a corriger etait qu une
 * methode de service existait sans qu aucun ecran ne l atteigne. Un test a doublures
 * aurait verifie la methode — celle-la etait deja verte — sans jamais dire qu elle
 * etait inatteignable. C est le rendu HTTP complet qui fait foi.</p>
 *
 * <p>{@code Accept-Language: fr} est fixe partout : sans cela ces cas dependraient de
 * la langue de la machine de build.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Renvoi du courriel de verification (integration)")
class RenvoiVerificationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "MotDePasseSolide2026!";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    @Nested
    @DisplayName("Ecrans reellement servis")
    class Servis {

        /**
         * Anonyme, et pas seulement « non authentifie » : le membre vise ici est
         * precisement celui qui ne PEUT pas se connecter, faute d avoir active son
         * compte. Un ecran protege serait inaccessible a son unique destinataire.
         */
        @Test
        @DisplayName("le formulaire est ouvert sans compte")
        void formulaireOuvert() throws Exception {
            mvc.perform(get("/inscription/renvoyer-verification")
                            .with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andExpect(content().string(containsString(
                            "action=\"/inscription/renvoyer-verification\"")));
        }

        /**
         * Le lien depuis la connexion est la moitie utile de la fonctionnalite : sans
         * lui l ecran existe mais reste introuvable — exactement le defaut que ce lot
         * corrige, deplace d un cran.
         */
        @Test
        @DisplayName("la page de connexion y mene")
        void connexionPorteLeLien() throws Exception {
            mvc.perform(get("/connexion").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("/inscription/renvoyer-verification")));
        }

        /**
         * Second point d entree, et le plus naturel : un jeton expire (24 h) atterrit
         * sur cet ecran, et c est exactement la que le renvoi doit etre propose.
         */
        @Test
        @DisplayName("l echec de verification y mene aussi")
        void echecDeVerificationPorteLeLien() throws Exception {
            mvc.perform(get("/inscription/verification").param("jeton", "jeton-qui-n-existe-pas")
                            .with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("/inscription/renvoyer-verification")));
        }
    }

    @Nested
    @DisplayName("Neutralite de la reponse")
    class Neutralite {

        /**
         * LE cas qui compte. Les trois situations — adresse inconnue, adresse deja
         * verifiee, renvoi reellement effectue — doivent produire la MEME page. Si
         * elles differaient, ce formulaire public deviendrait un oracle d existence de
         * compte : il suffirait de le soumettre en boucle pour enumerer les membres.
         *
         * <p>La comparaison porte sur le corps ENTIER, jetons CSRF neutralises — eux
         * seuls varient legitimement d une requete a l autre. Comparer trois fragments
         * choisis a la main laisserait passer une difference ailleurs dans la page.</p>
         */
        @Test
        @DisplayName("inconnue, deja verifiee et renvoi effectif rendent la meme page")
        void troisSituationsIndiscernables() throws Exception {
            String inconnue = corpsApresDemande("inconnu-" + COMPTEUR.getAndIncrement() + "@exemple.be");
            String dejaVerifiee = corpsApresDemande(creerMembre(true));
            String renvoiEffectif = corpsApresDemande(creerMembre(false));

            assertThat(dejaVerifiee).isEqualTo(inconnue);
            assertThat(renvoiEffectif).isEqualTo(inconnue);
        }

        /**
         * L adresse soumise n est meme pas reaffichee. La reafficher ne dirait rien de
         * plus a son titulaire — il vient de la taper — mais elle ferait de la page un
         * echo exploitable, et rendrait la comparaison ci-dessus impossible a tenir.
         */
        @Test
        @DisplayName("la page n echo pas l adresse soumise")
        void adresseNonReaffichee() throws Exception {
            String email = "echo-" + COMPTEUR.getAndIncrement() + "@exemple.be";

            assertThat(corpsApresDemande(email)).doesNotContain(email);
        }

        @Test
        @DisplayName("une adresse vide ne provoque aucune erreur visible")
        void adresseVideAcceptee() throws Exception {
            mvc.perform(post("/inscription/renvoyer-verification").param("email", "")
                            .with(anonymous()).with(csrf()).header("Accept-Language", "fr"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Effet reel sur le compte")
    class Effet {

        /**
         * La neutralite de l ecran ne doit pas se payer d une inaction : le compte non
         * verifie recoit bien un jeton NEUF. Verifie en base et non sur la page — la
         * page, elle, a justement pour consigne de n en rien dire.
         */
        @Test
        @DisplayName("un compte non verifie recoit un nouveau jeton")
        void jetonRegenere() throws Exception {
            String email = creerMembre(false);
            String jetonInitial = utilisateurs.findByEmailIgnoreCase(email).orElseThrow()
                    .getJetonVerification();

            corpsApresDemande(email);

            Utilisateur apres = utilisateurs.findByEmailIgnoreCase(email).orElseThrow();
            assertThat(apres.getJetonVerification()).isNotNull().isNotEqualTo(jetonInitial);
            assertThat(apres.isEmailVerifie()).isFalse();
        }

        /**
         * Le pendant du cas precedent : un compte deja verifie ne doit pas voir son etat
         * bouger. Sans ce cas, une implementation qui regenererait un jeton pour tout le
         * monde passerait le test de neutralite sans que rien ne le signale.
         */
        @Test
        @DisplayName("un compte deja verifie n est pas modifie")
        void compteVerifieIntact() throws Exception {
            String email = creerMembre(true);

            corpsApresDemande(email);

            Utilisateur apres = utilisateurs.findByEmailIgnoreCase(email).orElseThrow();
            assertThat(apres.isEmailVerifie()).isTrue();
        }
    }

    /** Corps de la reponse, jetons CSRF neutralises : eux seuls varient legitimement. */
    private String corpsApresDemande(String email) throws Exception {
        String corps = mvc.perform(post("/inscription/renvoyer-verification").param("email", email)
                        .with(anonymous()).with(csrf()).header("Accept-Language", "fr"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return corps.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\" value=\"X\"");
    }

    private String creerMembre(boolean verifie) {
        String email = "renvoi-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        Utilisateur membre = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                "Test", "Alex", TypeUtilisateur.MEMBRE);
        if (verifie) {
            membre.confirmerAdresseEmail();
            membre.setStatut(StatutUtilisateur.ACTIF);
        } else {
            membre.enregistrerJetonVerification("jeton-initial-" + COMPTEUR.getAndIncrement(),
                    Instant.now().plusSeconds(3600));
        }
        return utilisateurs.save(membre).getEmail();
    }
}
