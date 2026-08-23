package be.autoservplus.i18n;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Changement de langue de l interface, de bout en bout (F6).
 *
 * <p>Chaque cas verifie <b>deux choses ensemble</b> : que les libelles changent, et
 * que l attribut {@code lang} du document change avec eux. Les dissocier laisserait
 * passer exactement le defaut d origine — un site qui sert du neerlandais en
 * annoncant {@code lang="fr"}, ce qui fait prononcer un texte neerlandais avec la
 * phonetique francaise par un lecteur d ecran (<b>WCAG 3.1.1</b>).</p>
 *
 * <p>L en-tete {@code Accept-Language} est fixe a {@code fr} partout ou il n est pas
 * l objet du test : sans cela, ces cas dependraient de la langue de la machine de
 * build, qui n est pas le francais.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Selecteur de langue FR/NL/EN (integration)")
class SelecteurLangueIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    @Nested
    @DisplayName("Bascule par le selecteur")
    class Bascule {

        @Test
        @DisplayName("lang=nl rend les libelles en neerlandais ET annonce lang=\"nl\"")
        void basculeVersLeNeerlandais() throws Exception {
            mvc.perform(get("/cgv").param("lang", "nl")
                            .with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Voorlopig document")))
                    .andExpect(content().string(containsString("<html lang=\"nl\"")))
                    .andExpect(content().string(not(containsString("Document provisoire"))));
        }

        @Test
        @DisplayName("lang=en rend les libelles en anglais ET annonce lang=\"en\"")
        void basculeVersLAnglais() throws Exception {
            mvc.perform(get("/cgv").param("lang", "en")
                            .with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("Draft document")))
                    .andExpect(content().string(containsString("<html lang=\"en\"")));
        }

        /**
         * Le choix survit a la page suivante SANS le parametre. C est ce qui distingue
         * un vrai selecteur d un simple parametre d URL : sans persistance, le visiteur
         * repasserait en francais au premier lien clique.
         */
        @Test
        @DisplayName("le choix persiste en session, sans le parametre")
        void choixPersistantEnSession() throws Exception {
            HttpSession session = mvc.perform(get("/cgv").param("lang", "nl")
                            .with(anonymous()).header("Accept-Language", "fr"))
                    .andReturn().getRequest().getSession();

            mvc.perform(get("/mentions-legales").session(
                                    (org.springframework.mock.web.MockHttpSession) session)
                            .with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("Wettelijke vermeldingen")))
                    .andExpect(content().string(containsString("<html lang=\"nl\"")));
        }

        /**
         * Une langue hors perimetre est ramenee au francais plutot que posee telle
         * quelle. Sans ce filtre, {@code ?lang=de} donnerait un document annoncant
         * {@code lang="de"} tout en servant du francais : la non-conformite WCAG que
         * F6 corrige, sous une autre forme.
         */
        @Test
        @DisplayName("une langue non supportee retombe sur le francais, pas sur elle-meme")
        void langueInconnueRamenerAuFrancais() throws Exception {
            mvc.perform(get("/cgv").param("lang", "de")
                            .with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Document provisoire")))
                    .andExpect(content().string(containsString("<html lang=\"fr\"")));
        }

        /** Le selecteur doit etre atteignable depuis la page, sinon il n existe pas. */
        @Test
        @DisplayName("les trois langues sont proposees sur la page")
        void troisLanguesProposees() throws Exception {
            mvc.perform(get("/cgv").with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("selecteur-langue")))
                    .andExpect(content().string(containsString("Nederlands")))
                    .andExpect(content().string(containsString("English")))
                    .andExpect(content().string(containsString("lang=fr")));
        }

        /**
         * Le lien conserve la chaine de requete de la page courante. Un lien ecrit
         * « ?lang=nl » sans elle ferait perdre au visiteur sa page de resultats.
         */
        @Test
        @DisplayName("le lien de bascule conserve les autres parametres de l adresse")
        void parametresConserves() throws Exception {
            mvc.perform(get("/cgv?apercu=1")
                            .with(anonymous()).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("apercu=1&amp;lang=nl")));
        }
    }

    @Nested
    @DisplayName("Preference enregistree au profil")
    class PreferenceDuMembre {

        private static final String MOT_DE_PASSE = "MotDePasseTest2026!";

        private String creerMembreConnectable(Langue langue) {
            String email = "f6-" + COMPTEUR.getAndIncrement() + "@exemple.be";
            Utilisateur membre = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                    "Test", "Alex", TypeUtilisateur.MEMBRE);
            membre.setLangue(langue);
            membre.setStatut(StatutUtilisateur.ACTIF);
            utilisateurs.save(membre);
            return email;
        }

        /** Connexion par le VRAI formulaire : c est l evenement qui declenche la regle. */
        private MockHttpSession seConnecter(String email, MockHttpSession session) throws Exception {
            var requete = post("/connexion").param("email", email)
                    .param("password", MOT_DE_PASSE)
                    .with(csrf()).header("Accept-Language", "fr");
            if (session != null) {
                requete = requete.session(session);
            }
            return (MockHttpSession) mvc.perform(requete)
                    .andExpect(redirectedUrl("/mon-compte"))
                    .andReturn().getRequest().getSession();
        }

        /**
         * La colonne {@code utilisateur.langue} existait et etait deja lue pour choisir
         * la langue d une facture PDF, mais rien ne la reliait a l interface web. Ce cas
         * verrouille le branchement : le navigateur reclame du francais, et pourtant le
         * site s affiche en neerlandais parce que le profil le dit.
         */
        @Test
        @DisplayName("apres connexion, un profil nl donne le site en neerlandais")
        void profilAppliqueALaConnexion() throws Exception {
            MockHttpSession session = seConnecter(creerMembreConnectable(Langue.nl), null);

            mvc.perform(get("/cgv").session(session).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("Voorlopig document")))
                    .andExpect(content().string(containsString("<html lang=\"nl\"")));
        }

        /**
         * Le choix exprime avant la connexion survit a la connexion. La protection contre
         * la fixation de session recopie les attributs, donc l attribut de langue passe la
         * migration : quelqu un qui a clique sur « EN » puis s est connecte ne doit pas
         * voir son clic annule par une preference qu il n a jamais renseignee.
         */
        @Test
        @DisplayName("un choix exprime avant la connexion n est pas ecrase par le profil")
        void choixManuelPrimeSurLeProfil() throws Exception {
            String email = creerMembreConnectable(Langue.nl);

            MockHttpSession session = (MockHttpSession) mvc.perform(get("/cgv")
                            .param("lang", "en").with(anonymous()).header("Accept-Language", "fr"))
                    .andReturn().getRequest().getSession();
            seConnecter(email, session);

            mvc.perform(get("/cgv").session(session).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("Draft document")))
                    .andExpect(content().string(containsString("<html lang=\"en\"")));
        }

        /**
         * NON-REGRESSION, et la raison d etre du resserrement au seul evenement de
         * connexion. {@code utilisateur.langue} vaut {@code fr} par defaut pour tout le
         * monde et aucun ecran ne l ecrit : appliquer la preference a chaque requete
         * authentifiee revenait a servir du francais a tout membre connecte, y compris
         * celui dont le navigateur reclame du neerlandais et qui n a jamais rien choisi.
         * Quatre tests deja en place l ont montre ; celui-ci le dit a l endroit ou la
         * regle est ecrite.
         */
        @Test
        @DisplayName("hors connexion, l en-tete du navigateur decide encore pour un membre")
        void enTeteRespecteeHorsConnexion() throws Exception {
            String email = creerMembreConnectable(Langue.fr);

            mvc.perform(get("/cgv").with(user(email)).header("Accept-Language", "nl"))
                    .andExpect(content().string(containsString("Voorlopig document")))
                    .andExpect(content().string(containsString("<html lang=\"nl\"")));
        }
    }
}
