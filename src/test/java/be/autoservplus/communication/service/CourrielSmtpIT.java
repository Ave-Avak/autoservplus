package be.autoservplus.communication.service;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.InscriptionService;
import be.autoservplus.rgpd.service.SuppressionCompteService;
import be.autoservplus.support.SocleIntegration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

/**
 * Envoi réel par SMTP, reçu par un serveur Mailpit lancé pour la classe et lu par son
 * API.
 *
 * <p>Mailpit plutôt que GreenMail : Testcontainers est déjà au projet, Mailpit n'ajoute
 * donc aucune dépendance, et c'est le serveur de la recette. Le courriel traverse un vrai
 * dialogue SMTP, encodage et pièce jointe compris, puis se relit comme un client de
 * messagerie le lirait.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Courriels remis à un relais SMTP (intégration)")
class CourrielSmtpIT extends SocleIntegration {

    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.21.8")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    static {
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void relais(DynamicPropertyRegistry registre) {
        registre.add("autoservplus.courriel.smtp.hote", MAILPIT::getHost);
        registre.add("autoservplus.courriel.smtp.port", () -> MAILPIT.getMappedPort(1025));
        registre.add("autoservplus.courriel.smtp.starttls", () -> "false");
        registre.add("autoservplus.courriel.expediteur", () -> "ne-pas-repondre@autoservplus.test");
    }

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private MockMvc mvc;
    @Autowired private InscriptionService inscription;
    @Autowired private SuppressionCompteService suppression;
    @Autowired private ServiceCourriel courriel;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private IdentiteGarage garage;

    private static String adresse() {
        return "smtp-" + COMPTEUR.getAndIncrement() + "@exemple.be";
    }

    private static String api(String chemin) {
        return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + chemin;
    }

    private static JsonNode lire(String chemin) throws Exception {
        HttpResponse<String> reponse = HTTP.send(HttpRequest.newBuilder(URI.create(api(chemin)))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(reponse.body());
    }

    /** Messages reçus pour une adresse, relus en entier (texte et pièces jointes). */
    private static List<JsonNode> recus(String destinataire) throws Exception {
        List<JsonNode> messages = new ArrayList<>();
        for (JsonNode resume : lire("/api/v1/messages").path("messages")) {
            if (resume.path("To").get(0).path("Address").asText().equals(destinataire)) {
                messages.add(lire("/api/v1/message/" + resume.path("ID").asText()));
            }
        }
        return messages;
    }

    @BeforeEach
    void viderLaBoite() throws Exception {
        HTTP.send(HttpRequest.newBuilder(URI.create(api("/api/v1/messages"))).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    @Test
    @DisplayName("l'inscription remet au relais un lien de vérification absolu et valable")
    void inscription() throws Exception {
        String email = adresse();
        inscription.inscrire(email, MOT_DE_PASSE, "Dupont", "Marie", Langue.fr);

        List<JsonNode> messages = recus(email);
        assertThat(messages).hasSize(1);
        JsonNode message = messages.get(0);
        assertThat(message.path("Subject").asText()).isEqualTo("Confirmez votre adresse de courriel");
        assertThat(message.path("From").path("Address").asText())
                .isEqualTo("ne-pas-repondre@autoservplus.test");
        // Le membre lit le nom de son garage, pas une adresse technique.
        assertThat(message.path("From").path("Name").asText())
                .isEqualTo(garage.enseigne())
                .isNotBlank();
        String texte = message.path("Text").asText();
        assertThat(texte).startsWith("Bonjour Marie,").contains("valable 24 heures");

        String lien = texte.lines().filter(l -> l.startsWith("http")).findFirst().orElseThrow();
        assertThat(lien).startsWith("http://localhost:8080/inscription/verification?jeton=");
        mvc.perform(get("/inscription/verification")
                        .param("jeton", lien.substring(lien.indexOf("jeton=") + 6)))
                .andExpect(model().attribute("succes", true));
    }

    @Test
    @DisplayName("une transaction annulée ne remet rien au relais")
    void annulation() throws Exception {
        String email = adresse();
        new TransactionTemplate(transactions).executeWithoutResult(statut -> {
            inscription.inscrire(email, MOT_DE_PASSE, "Dupont", "Marie", Langue.fr);
            statut.setRollbackOnly();
        });

        assertThat(utilisateurs.findByEmailIgnoreCase(email)).isEmpty();
        assertThat(recus(email)).isEmpty();
    }

    /**
     * L'écouteur de la suppression n'a pas de transaction propre : il demande son
     * courriel pendant la phase {@code afterCommit} de la suppression. C'est le cas que
     * {@link EnvoiApresCommit} sert par {@code afterCompletion}.
     */
    @Test
    @WithMockUser
    @DisplayName("la confirmation de suppression part, dans la langue du compte")
    void suppressionDeCompte() throws Exception {
        String email = adresse();
        Utilisateur membre = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setLangue(Langue.nl);
        utilisateurs.saveAndFlush(membre);

        suppression.supprimer(email, MOT_DE_PASSE, SuppressionCompteService.MOT_DE_CONFIRMATION);

        List<JsonNode> messages = recus(email);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).path("Subject").asText()).isEqualTo("Uw account is verwijderd");
        assertThat(messages.get(0).path("Text").asText()).startsWith("Beste Marie,");
    }

    @Test
    @DisplayName("la pièce jointe d'agenda arrive jointe au message")
    void pieceJointe() throws Exception {
        String email = adresse();
        Utilisateur membre = new Utilisateur(email, "x", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        ZonedDateTime debut = ZonedDateTime.of(2026, 9, 13, 10, 0, 0, 0, ZoneId.of("Europe/Brussels"));

        courriel.envoyerConfirmationRdv(membre, new DetailsRdvCourriel("RDV-2026-0001", debut),
                new PieceJointeCourriel("rendez-vous-RDV-2026-0001.ics", "text/calendar",
                        "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n".getBytes()));

        JsonNode message = recus(email).get(0);
        assertThat(message.path("Attachments")).hasSize(1);
        assertThat(message.path("Attachments").get(0).path("FileName").asText())
                .isEqualTo("rendez-vous-RDV-2026-0001.ics");
        assertThat(message.path("Text").asText()).contains("dimanche 13 septembre 2026");
    }
}
