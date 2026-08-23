package be.autoservplus.i18n;

import be.autoservplus.identite.domain.Langue;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

        private String creerMembre(Langue langue) {
            String email = "f6-" + COMPTEUR.getAndIncrement() + "@exemple.be";
            Utilisateur membre = new Utilisateur(email, "$2a$12$abcdefghijklmnopqrstuv",
                    "Test", "Alex", TypeUtilisateur.MEMBRE);
            membre.setLangue(langue);
            utilisateurs.save(membre);
            return email;
        }

        /**
         * La colonne {@code utilisateur.langue} existait et etait deja lue pour choisir
         * la langue d une facture PDF, mais rien ne la reliait a l interface web. Ce
         * cas verrouille le branchement : aucun parametre d URL, aucune en-tete
         * favorable, et pourtant du neerlandais.
         */
        @Test
        @DisplayName("un membre dont le profil dit nl obtient le site en neerlandais")
        void profilApplique() throws Exception {
            String email = creerMembre(Langue.nl);

            mvc.perform(get("/cgv").with(user(email)).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("Voorlopig document")))
                    .andExpect(content().string(containsString("<html lang=\"nl\"")));
        }

        /**
         * Le clic prime sur le profil pour la session courante. Autrement, un membre
         * enregistre en francais ne pourrait <b>jamais</b> consulter le site en
         * anglais : sa preference ecraserait son propre choix a chaque page.
         */
        @Test
        @DisplayName("le choix manuel prime sur la preference du profil")
        void choixManuelPrimeSurLeProfil() throws Exception {
            String email = creerMembre(Langue.fr);

            HttpSession session = mvc.perform(get("/cgv").param("lang", "en")
                            .with(user(email)).header("Accept-Language", "fr"))
                    .andReturn().getRequest().getSession();

            mvc.perform(get("/cgv").session(
                                    (org.springframework.mock.web.MockHttpSession) session)
                            .with(user(email)).header("Accept-Language", "fr"))
                    .andExpect(content().string(containsString("Draft document")))
                    .andExpect(content().string(containsString("<html lang=\"en\"")));
        }
    }
}
