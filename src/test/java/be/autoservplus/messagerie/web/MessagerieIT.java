package be.autoservplus.messagerie.web;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.messagerie.domain.RoleExpediteur;
import be.autoservplus.messagerie.repository.ConversationRepository;
import be.autoservplus.messagerie.service.AdminMessagerieService;
import be.autoservplus.messagerie.service.MessagerieService;
import be.autoservplus.notification.domain.StatutNotification;
import be.autoservplus.notification.repository.NotificationRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
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
@DisplayName("Messagerie (integration)")
class MessagerieIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private MessagerieService messagerie;
    @Autowired private AdminMessagerieService adminMessagerie;
    @Autowired private ConversationRepository conversations;
    @Autowired private NotificationRepository notifications;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock horloge;

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

    /** Entrée directe au garage (V32) : ni rendez-vous ni commande, véhicule du membre. */
    private UUID interventionDirecte(String email) {
        Long membre = utilisateurs.findByEmail(email).orElseThrow().getId();
        Long vehicule = jdbc.queryForObject("INSERT INTO vehicule (reference, membre_id, plaque, marque, "
                        + "modele, motorisation) VALUES (gen_random_uuid(), ?, ?, 'VW', 'Golf', 'DIESEL') "
                        + "RETURNING id", Long.class, membre, "MSG" + COMPTEUR.getAndIncrement());
        return jdbc.queryForObject("INSERT INTO intervention (numero, vehicule_id, statut, "
                        + "montant_devis_htva) VALUES (?, ?, 'PLANIFIEE', 0) RETURNING reference",
                UUID.class, "INT-MSG-" + vehicule, vehicule);
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
        @DisplayName("un fil se rattache à une intervention sans rendez-vous dont le membre est titulaire")
        void interventionSansRendezVous() {
            UUID intervention = interventionDirecte(marie);

            UUID fil = messagerie.ouvrir(marie, "Ma révision", "Quand puis-je venir ?", intervention);

            assertThat(jdbc.queryForObject("SELECT intervention_id IS NOT NULL FROM conversation "
                    + "WHERE reference = ?", Boolean.class, fil)).isTrue();
            assertThatThrownBy(() -> messagerie.ouvrir(paul, "Pas la mienne", "Bonjour", intervention))
                    .isInstanceOf(RessourceIntrouvableException.class);
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
            var membre = utilisateurs.findByEmail(marie).orElseThrow();
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
    @DisplayName("Rafraichissement du fil et du compteur")
    class Rafraichissement {

        private long dernierId(UUID fil) {
            return jdbc.queryForObject("SELECT max(m.id) FROM message m JOIN conversation c "
                    + "ON c.id = m.conversation_id WHERE c.reference = ?", Long.class, fil);
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("le fil ne rend que les messages suivants, lus et datés à leur affichage")
        void suiteDuFil() throws Exception {
            UUID fil = filDe(marie, "Suite");
            long curseur = dernierId(fil);
            adminMessagerie.repondre("admin@autoservplus.be", fil, "Réponse du garage.");

            mvc.perform(get("/mes-messages/{ref}/messages", fil).param("apres", String.valueOf(curseur))
                            .with(user(marie)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Réponse du garage.")))
                    .andExpect(content().string(not(containsString("Bonjour, une question."))))
                    .andExpect(content().string(containsString("apres=" + dernierId(fil))));

            assertThat(jdbc.queryForObject("SELECT lu_le IS NOT NULL FROM message WHERE id = ?",
                    Boolean.class, dernierId(fil))).isTrue();
            mvc.perform(get("/mes-messages/{ref}/messages", fil).param("apres", String.valueOf(dernierId(fil)))
                            .with(user(marie)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser
        @DisplayName("le curseur ne sert qu'après le contrôle d'appartenance : le fil d'autrui répond 404")
        void curseurSurLeFilDAutrui() throws Exception {
            UUID fil = filDe(paul, "Fil de Paul");

            mvc.perform(get("/mes-messages/{ref}/messages", fil).param("apres", "0").with(user(marie)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser
        @DisplayName("quinze minutes après l'affichage, le fil et le compteur répondent 286")
        void arretApresInactivite() throws Exception {
            UUID fil = filDe(marie, "Ancien");
            String ancien = String.valueOf(horloge.millis() - Duration.ofMinutes(16).toMillis());

            mvc.perform(get("/mes-messages/{ref}/messages", fil).param("apres", "0")
                            .param("depuis", ancien).with(user(marie)))
                    .andExpect(status().is(286));
            mvc.perform(get("/mes-messages/compteur").param("actuel", "0")
                            .param("depuis", ancien).with(user(marie)))
                    .andExpect(status().is(286));
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("le compteur répond 204 tant qu'il ne change pas, puis rend le nouveau nombre")
        void compteur() throws Exception {
            UUID fil = filDe(marie, "Compteur de l'en-tête");
            adminMessagerie.repondre("admin@autoservplus.be", fil, "Une réponse.");

            mvc.perform(get("/mes-messages/compteur").param("actuel", "1").with(user(marie)))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/mes-messages/compteur").param("actuel", "0").with(user(marie))
                            .locale(java.util.Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"compteur-messages\"")))
                    .andExpect(content().string(containsString("actuel=1")));
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("le fil ouvert au garage se complète des messages du membre")
        void suiteCoteGarage() throws Exception {
            UUID fil = filDe(marie, "Côté garage");
            long curseur = dernierId(fil);
            messagerie.repondre(marie, fil, "Une précision du membre.");

            mvc.perform(get("/admin/messages/{ref}/messages", fil).param("apres", String.valueOf(curseur))
                            .with(user("admin@autoservplus.be").roles("ADMINISTRATEUR")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Une précision du membre.")));
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
