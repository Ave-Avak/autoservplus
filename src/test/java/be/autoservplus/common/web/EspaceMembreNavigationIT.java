package be.autoservplus.common.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.notification.domain.Notification;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.repository.NotificationRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Commandes, messages et notifications atteints par clic depuis l espace membre.
 *
 * <p>Les trois ecrans n etaient lies que depuis eux-memes ou depuis la page de
 * confirmation d une commande. Les compteurs, eux, n existaient que sur deux ou trois
 * pages : le cas les lit sur un ecran sans rapport, les vehicules, pour etablir qu ils
 * sont devenus globaux.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Navigation de l'espace membre (integration)")
class EspaceMembreNavigationIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private NotificationRepository notifications;

    private Utilisateur membre;

    @BeforeEach
    void setUp() {
        membre = new Utilisateur("espace-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                "{noop}inutilise", "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        membre = utilisateurs.saveAndFlush(membre);
    }

    @Test
    @DisplayName("« Mon compte » porte les cartes commandes, messages et notifications")
    void cartesDeMonCompte() throws Exception {
        mvc.perform(get("/mon-compte").with(user(membre.getEmail()).roles("MEMBRE"))
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("class=\"carte carte-lien\" href=\"/commandes\""),
                        containsString("class=\"carte carte-lien\" href=\"/mes-messages\""),
                        containsString("class=\"carte carte-lien\" href=\"/mes-notifications\""))));
    }

    @Test
    @DisplayName("l'en-tête d'un écran quelconque porte les liens et leurs compteurs")
    void enTeteAvecCompteurs() throws Exception {
        notifications.saveAndFlush(new Notification(membre, TypeNotification.RDV_CONFIRME,
                "Rendez-vous confirme", "RDV-1", Instant.now()));

        mvc.perform(get("/mes-vehicules").with(user(membre.getEmail()).roles("MEMBRE"))
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Notifications (1)"),
                        containsString("Messages (0)"),
                        containsString("Panier (0)"),
                        containsString("href=\"/mes-rendez-vous/nouveau\""))));
    }
}
