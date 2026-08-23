package be.autoservplus.notification.web;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.notification.domain.Notification;
import be.autoservplus.notification.domain.StatutNotification;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.repository.NotificationRepository;
import be.autoservplus.notification.service.NotificationService;
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

import java.time.Instant;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notifications in-app de bout en bout (BL-6), sur un PostgreSQL reel.
 *
 * <p>Verifie les deux niveaux de garde exiges par le projet : la <b>protection d URL</b>
 * de {@code SecuriteConfig} (un anonyme est renvoye vers la connexion) et le
 * <b>{@code @PreAuthorize} de methode</b> du service (invoque hors requete, il refuse
 * sans authentification). Verifie surtout que la notification d autrui produit un
 * <b>404 et non un 403</b> : confirmer l existence de la ligne renseignerait deja.</p>
 *
 * <p>Sans {@code @Transactional} de classe : les depots passent par le service, et une
 * transaction de test rollbackee masquerait ce que la base a reellement accepte (les
 * CHECK {@code ck_notification_statut} et {@code ck_notification_canal} du socle V7,
 * jamais exerces jusqu ici).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Notifications in-app (integration)")
class NotificationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private NotificationRepository notifications;
    @Autowired private NotificationService service;

    private String marie;
    private String paul;

    @BeforeEach
    void setUp() {
        int n = COMPTEUR.getAndIncrement();
        marie = "marie" + n + "@exemple.be";
        paul = "paul" + n + "@exemple.be";
        creer(marie, "Marie");
        creer(paul, "Paul");
    }

    private void creer(String email, String prenom) {
        utilisateurs.save(new Utilisateur(email, "$2a$12$abcdefghijklmnopqrstuv",
                "Test", prenom, TypeUtilisateur.MEMBRE));
    }

    private Utilisateur charger(String email) {
        return utilisateurs.findByEmailIgnoreCase(email).orElseThrow();
    }

    private Notification deposerPour(String email, String numero) {
        service.deposer(charger(email), TypeNotification.RDV_CONFIRME, numero);
        return notifications.findByMembreOrderByDateEnvoiDescIdDesc(charger(email)).getFirst();
    }

    private long nonLuesDe(String email) {
        return notifications.countByMembreAndStatut(charger(email), StatutNotification.NON_LUE);
    }

    @Nested
    @DisplayName("Depot et lecture")
    class DepotEtLecture {

        @Test
        @DisplayName("la base accepte les CHECK du socle V7, restes jusqu ici sans emploi")
        void depotAccepteParLaBase() {
            Notification deposee = deposerPour(marie, "RDV-2026-0100");

            assertThat(deposee.getId()).isNotNull();
            assertThat(deposee.getStatut()).isEqualTo(StatutNotification.NON_LUE);
            assertThat(deposee.getDateEnvoi()).isNotNull();
        }

        @Test
        @DisplayName("l ecran rend le libelle traduit, pas le texte stocke en base")
        void ecranRendLeLibelle() throws Exception {
            deposerPour(marie, "RDV-2026-0101");

            mvc.perform(get("/mes-notifications").with(user(marie)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("RDV-2026-0101")))
                    .andExpect(content().string(containsString("confirm")));
        }

        @Test
        @DisplayName("le membre ne voit que ses propres notifications")
        void cloisonnementDeLaListe() throws Exception {
            deposerPour(paul, "RDV-2026-0102");

            mvc.perform(get("/mes-notifications").with(user(marie)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("RDV-2026-0102"))));
        }
    }

    @Nested
    @DisplayName("Marquage")
    class Marquage {

        @Test
        @DisplayName("le titulaire marque sa notification, le compteur retombe")
        void marquageParLeTitulaire() throws Exception {
            Notification sienne = deposerPour(marie, "RDV-2026-0103");
            // Etat verifie par le repository et non par le service : les methodes de
            // lecture portent @PreAuthorize, et le test ne s execute pas sous
            // authentification. C est precisement ce que garantit preAuthorizeDeMethode.
            assertThat(nonLuesDe(marie)).isPositive();

            mvc.perform(post("/mes-notifications/{id}/lue", sienne.getId())
                            .with(user(marie)).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/mes-notifications"));

            assertThat(notifications.findById(sienne.getId()).orElseThrow().estNonLue()).isFalse();
        }

        @Test
        @DisplayName("marquer la notification d autrui donne 404, jamais 403")
        void notificationDAutrui() throws Exception {
            Notification celleDePaul = deposerPour(paul, "RDV-2026-0104");

            mvc.perform(post("/mes-notifications/{id}/lue", celleDePaul.getId())
                            .with(user(marie)).with(csrf()))
                    .andExpect(status().isNotFound());

            assertThat(notifications.findById(celleDePaul.getId()).orElseThrow().estNonLue())
                    .as("la notification de Paul reste intacte")
                    .isTrue();
        }

        @Test
        @DisplayName("tout marquer ne touche que les siennes")
        void toutMarquerCloisonne() throws Exception {
            deposerPour(marie, "RDV-2026-0105");
            Notification celleDePaul = deposerPour(paul, "RDV-2026-0106");

            mvc.perform(post("/mes-notifications/tout-lu").with(user(marie)).with(csrf()))
                    .andExpect(status().is3xxRedirection());

            assertThat(nonLuesDe(marie)).isZero();
            assertThat(notifications.findById(celleDePaul.getId()).orElseThrow().estNonLue())
                    .isTrue();
        }

        @Test
        @DisplayName("sans jeton CSRF, le marquage est refuse")
        void csrfExige() throws Exception {
            Notification sienne = deposerPour(marie, "RDV-2026-0107");

            mvc.perform(post("/mes-notifications/{id}/lue", sienne.getId()).with(user(marie)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Gardes de securite")
    class Gardes {

        @Test
        @WithAnonymousUser
        @DisplayName("niveau URL : l anonyme est renvoye vers la connexion")
        void urlProtegee() throws Exception {
            mvc.perform(get("/mes-notifications"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("niveau methode : le service refuse hors authentification")
        void preAuthorizeDeMethode() {
            assertThatThrownBy(() -> service.mesNotifications(marie))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @WithMockUser(username = "intrus@exemple.be")
        @DisplayName("le depot reste ouvert au listener, qui s execute sans contexte de securite")
        void depotSansGarde() {
            // Regression : poser un @PreAuthorize de classe sur NotificationService
            // ferait perdre toute notification, les listeners AFTER_COMMIT s executant
            // hors requete. Ce test casse si la garde remonte au niveau classe.
            assertThat(charger(marie)).isNotNull();
            service.deposer(charger(marie), TypeNotification.COMMANDE_PAYEE, "CMD-2026-0001");

            assertThat(notifications.countByMembreAndStatut(charger(marie),
                    StatutNotification.NON_LUE)).isPositive();
        }
    }
}
