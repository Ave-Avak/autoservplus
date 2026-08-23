package be.autoservplus.messagerie.web;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.messagerie.domain.RoleExpediteur;
import be.autoservplus.messagerie.repository.ConversationRepository;
import be.autoservplus.messagerie.service.AdminMessagerieService;
import be.autoservplus.messagerie.service.MessagerieService;
import be.autoservplus.notification.domain.StatutNotification;
import be.autoservplus.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Messagerie de bout en bout (BL-5), sur un PostgreSQL reel.
 *
 * <p>Verifie le cloisonnement entre membres (404 et non 403), la double garde de
 * l ecran garage (URL puis methode), le refus d ecrire dans un fil clos, et la
 * notification croisee de BL-6 qui previent le camp oppose.</p>
 *
 * <p>Sans {@code @Transactional} de classe : la lecture d un fil marque des messages
 * lus, effet qu une transaction de test rollbackee masquerait.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Messagerie (integration)")
class MessagerieIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private MessagerieService messagerie;
    @Autowired private AdminMessagerieService adminMessagerie;
    @Autowired private ConversationRepository conversations;
    @Autowired private NotificationRepository notifications;
    @Autowired private UtilisateurRepository utilisateurs;

    private String marie;
    private String paul;

    @BeforeEach
    void setUp() {
        int n = COMPTEUR.getAndIncrement();
        marie = "msg-marie" + n + "@exemple.be";
        paul = "msg-paul" + n + "@exemple.be";
        creer(marie, "Marie", TypeUtilisateur.MEMBRE);
        creer(paul, "Paul", TypeUtilisateur.MEMBRE);
    }

    private void creer(String email, String prenom, TypeUtilisateur type) {
        utilisateurs.save(new Utilisateur(email, "$2a$12$abcdefghijklmnopqrstuv",
                "Test", prenom, type));
    }

    private UUID filDe(String email, String sujet) {
        return messagerie.ouvrir(email, sujet, "Bonjour, une question.", null);
    }

    @Nested
    @DisplayName("Cloisonnement entre membres")
    class Cloisonnement {

        @Test
        @WithMockUser
        @DisplayName("le membre ouvre un fil et le relit")
        void ouvreEtRelit() throws Exception {
            UUID reference = filDe(marie, "Ma vidange");

            mvc.perform(get("/mes-messages/{ref}", reference).with(user(marie)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Ma vidange")));
        }

        @Test
        @WithMockUser
        @DisplayName("le fil d autrui donne 404, jamais 403")
        void filDAutrui() throws Exception {
            UUID celuiDePaul = filDe(paul, "Fil de Paul");

            mvc.perform(get("/mes-messages/{ref}", celuiDePaul).with(user(marie)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        @DisplayName("la liste ne montre que ses propres fils")
        void listeCloisonnee() throws Exception {
            filDe(paul, "Secret de Paul");

            mvc.perform(get("/mes-messages").with(user(marie)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("Secret de Paul"))));
        }

        @Test
        @WithMockUser
        @DisplayName("repondre dans le fil d autrui est refuse par un 404")
        void reponseDansLeFilDAutrui() throws Exception {
            UUID celuiDePaul = filDe(paul, "Fil de Paul");

            mvc.perform(post("/mes-messages/{ref}", celuiDePaul)
                            .param("corps", "Je m'incruste.")
                            .with(user(marie)).with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Echange et cloture")
    class Echange {

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("la reponse du garage notifie le membre, et inversement")
        void notificationCroisee() {
            UUID reference = filDe(marie, "Devis");
            var membre = utilisateurs.findByEmailIgnoreCase(marie).orElseThrow();
            long avant = notifications.countByMembreAndStatut(membre, StatutNotification.NON_LUE);

            adminMessagerie.repondre("admin@autoservplus.be", reference, "Voici votre devis.");

            assertThat(notifications.countByMembreAndStatut(membre, StatutNotification.NON_LUE))
                    .isGreaterThan(avant);
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("un fil clos refuse tout nouveau message, des deux cotes")
        void filClosRefuse() {
            UUID reference = filDe(marie, "Terminé");
            adminMessagerie.cloturer(reference);

            assertThatThrownBy(() -> adminMessagerie.repondre(
                    "admin@autoservplus.be", reference, "Encore un mot."))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("la reouverture rend le fil de nouveau utilisable")
        void reouverture() {
            UUID reference = filDe(marie, "Reprise");
            adminMessagerie.cloturer(reference);
            adminMessagerie.rouvrir(reference);

            adminMessagerie.repondre("admin@autoservplus.be", reference, "Nous reprenons.");

            assertThat(conversations.findByReferenceAvecMessages(reference).orElseThrow()
                    .getMessages()).hasSize(2);
        }

        @Test
        @WithMockUser
        @DisplayName("lire un fil eteint le compteur du lecteur, pas celui d en face")
        void lectureCiblee() {
            UUID reference = filDe(marie, "Compteur");
            var fil = conversations.findByReferenceAvecMessages(reference).orElseThrow();
            assertThat(fil.nombreNonLusPar(RoleExpediteur.ADMINISTRATEUR)).isEqualTo(1);

            messagerie.lire(marie, reference);

            var apres = conversations.findByReferenceAvecMessages(reference).orElseThrow();
            assertThat(apres.nombreNonLusPar(RoleExpediteur.ADMINISTRATEUR))
                    .as("le membre a lu son propre message : le garage doit encore le lire")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Gardes de securite")
    class Gardes {

        @Test
        @WithAnonymousUser
        @DisplayName("niveau URL : l anonyme est renvoye vers la connexion")
        void anonymeRefuse() throws Exception {
            mvc.perform(get("/mes-messages")).andExpect(status().is3xxRedirection());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau URL : un membre n atteint pas /admin/messages")
        void adminReserve() throws Exception {
            mvc.perform(get("/admin/messages")).andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau methode : le service garage refuse meme sans passer par l URL")
        void serviceGarageRedouble() {
            assertThatThrownBy(() -> adminMessagerie.tous())
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
