package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.Langue;
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
 * Modification du profil par le membre (CdC §5.2.2) et solde de la dette F6.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Profil du membre (integration)")
class ProfilIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    private String email;

    @BeforeEach
    void setUp() {
        email = "profil-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        Utilisateur membre = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        utilisateurs.saveAndFlush(membre);
    }

    private Utilisateur relire() {
        return utilisateurs.findByEmailIgnoreCase(email).orElseThrow();
    }

    /**
     * Soumission complete. La locale est fixee explicitement : MockMvc est anglophone
     * par defaut, et un test qui attend un message francais sans le dire verifierait
     * surtout sa propre configuration.
     */
    private org.springframework.test.web.servlet.ResultActions enregistrer(
            MockHttpSession session, String langue, String rue, String cp, String localite)
            throws Exception {
        var requete = post("/mon-profil").with(user(email).roles("MEMBRE")).with(csrf())
                .locale(Locale.FRENCH)
                .param("prenom", "Marie").param("nom", "Dupont")
                .param("telephone", "+32 470 00 00 00")
                .param("rue", rue).param("numeroRue", "18")
                .param("codePostal", cp).param("localite", localite)
                .param("pays", "Belgique").param("langue", langue);
        if (session != null) {
            requete = requete.session(session);
        }
        return mvc.perform(requete);
    }

    @Nested
    @DisplayName("modification")
    class Modification {

        @Test
        @DisplayName("le formulaire est prérempli avec le profil existant")
        void formulairePrerempli() throws Exception {
            mvc.perform(get("/mon-profil").with(user(email).roles("MEMBRE"))
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("value=\"Marie\"")))
                    .andExpect(content().string(containsString("value=\"Dupont\"")))
                    .andExpect(content().string(containsString(email)));
        }

        @Test
        @DisplayName("nom, prénom, téléphone et adresse sont enregistrés")
        void champsEnregistres() throws Exception {
            enregistrer(null, "fr", "Rue des Ateliers", "1000", "Bruxelles");

            Utilisateur apres = relire();
            assertThat(apres.getPrenom()).isEqualTo("Marie");
            assertThat(apres.getTelephone()).isEqualTo("+32 470 00 00 00");
            assertThat(apres.getRue()).isEqualTo("Rue des Ateliers");
            assertThat(apres.getCodePostal()).isEqualTo("1000");
            assertThat(apres.getLocalite()).isEqualTo("Bruxelles");
        }

        /**
         * Vide devient {@code null} et non la chaine vide : {@code adresseLisible()} de
         * la facture teste {@code null}, et une adresse faite d espaces y passerait.
         */
        @Test
        @DisplayName("un champ vidé devient nul, pas une chaîne vide")
        void videDevientNul() throws Exception {
            enregistrer(null, "fr", "Rue des Ateliers", "1000", "Bruxelles");

            mvc.perform(post("/mon-profil").with(user(email).roles("MEMBRE")).with(csrf())
                            .param("prenom", "Marie").param("nom", "Dupont")
                            .param("telephone", "").param("rue", "").param("numeroRue", "")
                            .param("codePostal", "").param("localite", "")
                            .param("pays", "Belgique").param("langue", "fr"))
                    .andExpect(status().is3xxRedirection());

            assertThat(relire().getTelephone()).isNull();
            assertThat(relire().getRue()).isNull();
        }
    }

    @Nested
    @DisplayName("adresse complète ou vide")
    class Adresse {

        @Test
        @DisplayName("adresse partielle : refusée, rien n'est enregistré")
        void adressePartielleRefusee() throws Exception {
            enregistrer(null, "fr", "Rue des Ateliers", "", "Bruxelles")
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Renseignez la rue")));

            // Le refus porte sur l ensemble : le prenom non plus n est pas enregistre.
            assertThat(relire().getRue()).isNull();
        }

        @Test
        @DisplayName("adresse entièrement vide : acceptée")
        void adresseVideAcceptee() throws Exception {
            // Succes : redirection, et non un rendu direct (Post/Redirect/Get).
            enregistrer(null, "fr", "", "", "")
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/mon-profil"));

            assertThat(relire().getRue()).isNull();
        }
    }

    @Nested
    @DisplayName("langue (dette F6)")
    class LanguePreference {

        /**
         * <b>Le test qui solde F6.</b> Deux moities a distinguer : la langue doit
         * s appliquer <b>aussitot</b> — sans quoi le membre verrait sa confirmation
         * dans l ancienne langue et croirait son choix perdu — et elle doit <b>revenir
         * seule</b> a la connexion suivante. C est l aller-retour de session qui separe
         * « applique » de « memorise » : la colonne etait deja lue a la connexion, mais
         * aucun ecran ne l ecrivait, si bien qu elle valait {@code fr} pour tout le
         * monde.
         */
        @Test
        @DisplayName("enregistrée, appliquée aussitôt, et retrouvée à la session suivante")
        void langueMemoriseeEtAppliquee() throws Exception {
            MockHttpSession session = new MockHttpSession();

            enregistrer(session, "nl", "Rue des Ateliers", "1000", "Bruxelles")
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/mon-profil"));

            // 1. appliquee aussitot : la page qui SUIT la redirection est en
            //    neerlandais, alors que la requete precedente etait en francais.
            String page = mvc.perform(get("/mon-profil").with(user(email).roles("MEMBRE"))
                            .session(session))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(page).contains("Mijn profiel");
            assertThat(page).contains("lang=\"nl\"");

            // 2. memorisee : la colonne que F6 lisait sans que rien ne l ecrive
            assertThat(relire().getLangue()).isEqualTo(Langue.nl);

            // 3. retrouvee a la connexion SUIVANTE, sur une session neuve et avec un
            //    navigateur qui reclame du francais. C est la moitie que F6 n avait
            //    pas : la preference est relue sans que le membre redemande rien.
            //
            //    La connexion passe par le VRAI formulaire. Les post-processeurs
            //    d authentification de MockMvc ne publient aucun evenement de
            //    connexion, donc LangueApresConnexionHandler ne tournerait pas et le
            //    test passerait au vert sans rien prouver — piege deja rencontre au
            //    lot F6, et consigne au registre.
            MockHttpSession nouvelleSession = new MockHttpSession();
            mvc.perform(post("/connexion").with(csrf()).session(nouvelleSession)
                            .locale(Locale.FRENCH)
                            .param("email", email)
                            .param("password", MOT_DE_PASSE))
                    .andExpect(status().is3xxRedirection());

            mvc.perform(get("/mon-profil").session(nouvelleSession).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Mijn profiel")));
        }
    }
}
