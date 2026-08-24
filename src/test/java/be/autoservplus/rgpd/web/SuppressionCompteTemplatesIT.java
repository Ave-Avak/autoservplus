package be.autoservplus.rgpd.web;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendu reel des ecrans de suppression de compte (F23) contre le contexte Spring
 * complet : c est ici qu une cle i18n manquante se voit, pas dans un
 * {@code @WebMvcTest} qui court-circuite le gabarit.
 *
 * <p>Les requetes fixent la locale — MockMvc est anglophone par defaut, et un test
 * qui ne la fixerait pas ne prouverait rien sur les fichiers FR et NL.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Ecrans de suppression de compte (integration)")
class SuppressionCompteTemplatesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;

    @BeforeEach
    void setUp() {
        utilisateurs.save(new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE));
    }

    @Test
    @DisplayName("l'ecran annonce ce qui est efface ET ce qui est conserve")
    void ecranEnFrancais() throws Exception {
        mvc.perform(get("/supprimer-mon-compte").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Supprimer mon compte")))
                // Dire la conservation comptable AVANT l'action : une page qui
                // promettrait un effacement total serait fausse.
                .andExpect(content().string(containsString("dix ans")))
                .andExpect(content().string(containsString("irréversible")))
                // Le mot a recopier vient du service, pas d'une copie du gabarit.
                .andExpect(content().string(containsString("SUPPRIMER")))
                // Rappel de portabilite : exporter avant d'effacer.
                .andExpect(content().string(containsString("/mes-donnees")))
                .andExpect(content().string(not(containsString("??suppression"))));
    }

    @Test
    @DisplayName("l'ecran se rend aussi en neerlandais et en anglais")
    void ecranDansLesTroisLangues() throws Exception {
        mvc.perform(get("/supprimer-mon-compte").locale(new Locale("nl")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mijn account verwijderen")))
                .andExpect(content().string(containsString("SUPPRIMER")))
                .andExpect(content().string(not(containsString("??suppression"))));

        mvc.perform(get("/supprimer-mon-compte").locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Delete my account")))
                .andExpect(content().string(containsString("right to erasure")))
                .andExpect(content().string(not(containsString("??suppression"))));
    }

    @Test
    @DisplayName("un code d'erreur inconnu n'affiche rien plutot qu'une page en erreur")
    void codeErreurInconnu() throws Exception {
        // L'URL est modifiable a la main : un code invente ne doit pas casser l'ecran.
        mvc.perform(get("/supprimer-mon-compte").locale(Locale.FRENCH)
                        .param("erreur", "inconnu"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("message-erreur"))));
    }

    @Test
    @DisplayName("chaque code de refus a son message traduit")
    void messagesDeRefus() throws Exception {
        mvc.perform(get("/supprimer-mon-compte").locale(Locale.FRENCH)
                        .param("erreur", "motdepasse"))
                .andExpect(content().string(containsString("Mot de passe incorrect")));
        mvc.perform(get("/supprimer-mon-compte").locale(Locale.FRENCH)
                        .param("erreur", "confirmation"))
                .andExpect(content().string(containsString("Confirmation absente")));
        mvc.perform(get("/supprimer-mon-compte").locale(Locale.FRENCH)
                        .param("erreur", "impossible"))
                .andExpect(content().string(containsString("ne peut pas être supprimé")));
    }

    @Test
    @DisplayName("la page de confirmation est PUBLIQUE : la session vient d'etre invalidee")
    void confirmationAccessibleSansSession() throws Exception {
        // A l'instant ou elle s'affiche, le compte n'existe plus. Une page
        // authentifiee renverrait vers un formulaire de connexion que plus aucun
        // identifiant ne satisfait.
        mvc.perform(get("/compte-supprime").with(anonymous()).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Votre compte a été supprimé")))
                .andExpect(content().string(not(containsString("??suppression"))));

        mvc.perform(get("/compte-supprime").with(anonymous()).locale(new Locale("nl")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Uw account is verwijderd")));
    }
}
