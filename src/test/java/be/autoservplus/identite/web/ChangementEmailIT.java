package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Changement d adresse de courriel en deux temps (CdC 5.2.2, option A1).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Changement d'adresse de courriel (integration)")
class ChangementEmailIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    private String email;
    private String cible;

    @BeforeEach
    void setUp() {
        int n = COMPTEUR.getAndIncrement();
        email = "adresse-" + n + "@exemple.be";
        cible = "nouvelle-" + n + "@exemple.be";
        utilisateurs.saveAndFlush(membre(email, true));
    }

    private Utilisateur membre(String adresse, boolean verifie) {
        Utilisateur compte = new Utilisateur(adresse, encodeur.encode(MOT_DE_PASSE),
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        if (verifie) {
            compte.confirmerAdresseEmail();
        }
        compte.setStatut(StatutUtilisateur.ACTIF);
        return compte;
    }

    private Utilisateur relire(String adresse) {
        return utilisateurs.findByEmailIgnoreCase(adresse).orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions demander(String nouvelle,
                                                                       String motDePasse)
            throws Exception {
        return mvc.perform(post("/mon-profil/adresse")
                .with(user(email).roles("MEMBRE")).with(csrf())
                .locale(Locale.FRENCH)
                .param("nouvelleAdresse", nouvelle)
                .param("motDePasse", motDePasse));
    }

    private String jetonDe(String adresse) {
        return relire(adresse).getJetonChangementEmail();
    }

    @Nested
    @DisplayName("demande")
    class Demande {

        @Test
        @DisplayName("enregistre l'adresse en attente et redirige (Post/Redirect/Get)")
        void demandeEnregistree() throws Exception {
            demander(cible, MOT_DE_PASSE)
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/mon-profil"));

            Utilisateur apres = relire(email);
            assertThat(apres.getEmail()).isEqualTo(email);
            assertThat(apres.getEmailEnAttente()).isEqualTo(cible);
        }

        @Test
        @DisplayName("mot de passe faux : la page est réaffichée et rien n'est enregistré")
        void motDePasseFaux() throws Exception {
            demander(cible, "mauvaise phrase de passe")
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Mot de passe incorrect")));

            assertThat(relire(email).changementEmailEnCours()).isFalse();
        }

        /**
         * Le mot de passe ne doit jamais repartir dans la page : {@code th:field} le
         * repeuplerait, et il se retrouverait en clair dans le cache du navigateur et
         * dans toute copie d ecran.
         */
        @Test
        @DisplayName("le mot de passe soumis n'est jamais réaffiché")
        void motDePasseNonReaffiche() throws Exception {
            demander(cible, MOT_DE_PASSE.replace("test", "faux"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("phrase de passe du faux"))));
        }
    }

    @Nested
    @DisplayName("neutralité")
    class Neutralite {

        /**
         * <b>Le cas central du lot.</b> Comparer les deux PAGES, et non verifier
         * seulement que l une ne cite pas l adresse : une page qui annoncerait
         * « adresse deja utilisee » sans la citer resterait un oracle parfait.
         */
        @Test
        @DisplayName("adresse libre et adresse déjà prise rendent exactement la même réponse")
        void memeReponseQueLAdresseSoitPriseOuNon() throws Exception {
            String occupee = "occupee-" + COMPTEUR.getAndIncrement() + "@exemple.be";
            utilisateurs.saveAndFlush(membre(occupee, true));

            var versOccupee = demander(occupee, MOT_DE_PASSE).andReturn().getResponse();
            var versLibre = demander(cible, MOT_DE_PASSE).andReturn().getResponse();

            assertThat(versOccupee.getStatus()).isEqualTo(versLibre.getStatus());
            assertThat(versOccupee.getRedirectedUrl()).isEqualTo(versLibre.getRedirectedUrl());

            // Et en base, l effet DIFFERE bien : c est ce que l ecran ne dit pas.
            assertThat(relire(email).getEmailEnAttente()).isEqualTo(cible);
        }
    }

    @Nested
    @DisplayName("confirmation")
    class Confirmation {

        @Test
        @DisplayName("le lien bascule l'adresse et reste accessible sans être connecté")
        void basculeAnonyme() throws Exception {
            demander(cible, MOT_DE_PASSE);
            String jeton = jetonDe(email);

            // Aucune authentification : le lien arrive dans la NOUVELLE boite, souvent
            // ouverte dans un autre navigateur.
            mvc.perform(get("/changement-adresse/confirmation").param("jeton", jeton)
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Adresse confirmée")));

            assertThat(utilisateurs.findByEmailIgnoreCase(email)).isEmpty();
            Utilisateur apres = relire(cible);
            assertThat(apres.isEmailVerifie()).isTrue();
            assertThat(apres.changementEmailEnCours()).isFalse();
        }

        /**
         * Le nom d utilisateur EST l adresse : apres la bascule, le principal en
         * session designe une adresse que plus aucune ligne ne porte.
         */
        @Test
        @DisplayName("la session en cours est coupée par la bascule")
        void sessionCoupee() throws Exception {
            MockHttpSession session = new MockHttpSession();
            mvc.perform(post("/connexion").with(csrf()).session(session)
                            .param("email", email).param("password", MOT_DE_PASSE))
                    .andExpect(status().is3xxRedirection());
            mvc.perform(get("/mon-profil").session(session)).andExpect(status().isOk());

            demander(cible, MOT_DE_PASSE);
            mvc.perform(get("/changement-adresse/confirmation")
                            .param("jeton", jetonDe(email)).session(session))
                    .andExpect(status().isOk());

            mvc.perform(get("/mon-profil").session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("http://localhost/connexion"));
        }

        /**
         * <b>La propriete de securite qui justifie la migration V38.</b> Le jeton de
         * changement part vers une adresse NON ENCORE PROUVEE ; s il etait accepte par
         * la reinitialisation de mot de passe — qui partage {@code jeton_verification}
         * avec l activation de compte — son porteur fixerait le mot de passe du compte.
         * Le flux destine a verifier une adresse deviendrait un flux de prise de
         * controle.
         */
        @Test
        @DisplayName("le jeton de changement n'ouvre PAS la réinitialisation de mot de passe")
        void jetonsNonInterchangeables() throws Exception {
            demander(cible, MOT_DE_PASSE);
            String jeton = jetonDe(email);

            // Le formulaire de nouveau mot de passe n est meme pas propose.
            mvc.perform(get("/mot-de-passe/nouveau").param("jeton", jeton))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Ce lien n'est plus valable")));

            // Et le forcage direct ne change rien : l empreinte est intacte. C est
            // l effet qui repond, pas le code HTTP — la page de refus est rendue en 200.
            String empreinteAvant = relire(email).getMotDePasseHache();
            mvc.perform(post("/mot-de-passe/nouveau").with(csrf())
                            .param("jeton", jeton)
                            .param("motDePasse", "phrase de passe de l attaquant")
                            .param("confirmation", "phrase de passe de l attaquant"))
                    .andExpect(content().string(containsString("Ce lien n'est plus valable")));

            assertThat(relire(email).getMotDePasseHache()).isEqualTo(empreinteAvant);
            assertThat(relire(email).getEmail()).isEqualTo(email);
        }

        @Test
        @DisplayName("jeton inconnu : page d'erreur, aucune bascule")
        void jetonInconnu() throws Exception {
            mvc.perform(get("/changement-adresse/confirmation").param("jeton", "inexistant"))
                    .andExpect(status().isNotFound());

            assertThat(relire(email).getEmail()).isEqualTo(email);
        }
    }
}
