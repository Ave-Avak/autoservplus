package be.autoservplus.common.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Pages d erreur 403, 404 et 500 : traduites, sans detail technique, avec un retour vers
 * la page d arrivee du role, a la place de la page Whitelabel de Spring Boot.
 *
 * <p>Le test demarre un vrai serveur. Une erreur que le controleur ne rattrape pas
 * repasse par le conteneur ({@code sendError}, puis dispatch {@code ERROR} vers
 * {@code /error}) ; MockMvc ne fait jamais ce second passage. Seul le cas 500, qu aucune
 * requete ne sait provoquer proprement, est rendu en appelant {@code /error} avec le
 * statut que le conteneur y aurait pose.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Pages d'erreur (serveur réel)")
class PagesErreurIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final Pattern JETON_CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final String MOT_DE_PASSE = "phrase de passe du test des erreurs";
    private static final byte[] SECRET = "secret de vingt octs".getBytes();
    private static final String WHITELABEL = "Whitelabel";

    @LocalServerPort private int port;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ChiffrementSecretMfa chiffrement;
    @Autowired private Clock horloge;
    @Autowired private WebApplicationContext contexte;

    /** Un navigateur : cookies conserves, redirections non suivies. */
    private final class Navigateur {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        HttpResponse<String> lire(String chemin, String langue) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                            .header("Accept", "text/html").header("Accept-Language", langue).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> lire(String chemin) throws Exception {
            return lire(chemin, "fr");
        }

        HttpResponse<String> envoyer(String chemin, String... paires) throws Exception {
            Matcher jeton = JETON_CSRF.matcher(lire(chemin).body());
            assertThat(jeton.find()).as("jeton CSRF dans " + chemin).isTrue();
            StringBuilder corps = new StringBuilder("_csrf=").append(encoder(jeton.group(1)));
            for (int i = 0; i < paires.length; i += 2) {
                corps.append('&').append(paires[i]).append('=').append(encoder(paires[i + 1]));
            }
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(corps.toString())).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private static String encoder(String valeur) {
        return URLEncoder.encode(valeur, StandardCharsets.UTF_8);
    }

    private Utilisateur creer(TypeUtilisateur type) {
        Utilisateur u = new Utilisateur("erreurs-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom", type);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        u = utilisateurs.saveAndFlush(u);
        if (type != TypeUtilisateur.MEMBRE) {
            ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(SECRET, u.getId());
            u.commencerEnrolement(chiffre.donnees(), chiffre.version());
            u.confirmerEnrolement(horloge.instant(), Totp.pasDe(horloge.instant()) - 10);
            u = utilisateurs.saveAndFlush(u);
        }
        return u;
    }

    private Navigateur connecte(TypeUtilisateur type) throws Exception {
        Utilisateur compte = creer(type);
        Navigateur navigateur = new Navigateur();
        navigateur.envoyer("/connexion", "email", compte.getEmail(), "password", MOT_DE_PASSE);
        if (type != TypeUtilisateur.MEMBRE) {
            navigateur.envoyer("/connexion/etape/second-facteur",
                    "code", Totp.code(SECRET, Totp.pasDe(horloge.instant())));
        }
        return navigateur;
    }

    @Nested
    @DisplayName("404")
    class Introuvable {

        /** Avant l ouverture du dispatch ERROR, ce visiteur etait renvoye a la connexion. */
        @Test
        @DisplayName("visiteur anonyme, adresse inconnue d'une zone publique : page traduite, retour à l'accueil")
        void anonyme() throws Exception {
            Navigateur visiteur = new Navigateur();

            HttpResponse<String> fr = visiteur.lire("/pieces/inconnue/sous-page");
            assertThat(fr.statusCode()).isEqualTo(404);
            assertThat(fr.body()).contains("<h1>Page introuvable</h1>", "<a href=\"/\">Retour à l&#39;accueil</a>")
                    .doesNotContain(WHITELABEL);

            HttpResponse<String> nl = visiteur.lire("/pieces/inconnue/sous-page", "nl");
            assertThat(nl.body()).contains("<h1>Pagina niet gevonden</h1>").doesNotContain(WHITELABEL);
        }

        @Test
        @DisplayName("membre connecté : retour à « Mon compte »")
        void membre() throws Exception {
            HttpResponse<String> page = connecte(TypeUtilisateur.MEMBRE).lire("/adresse-inconnue");
            assertThat(page.statusCode()).isEqualTo(404);
            assertThat(page.body()).contains("<h1>Page introuvable</h1>", "<a href=\"/mon-compte\">")
                    .doesNotContain(WHITELABEL);
        }
    }

    @Nested
    @DisplayName("403")
    class Refus {

        @Test
        @DisplayName("membre sur le back-office : refus par la chaîne de filtres")
        void membreSurLeBackOffice() throws Exception {
            HttpResponse<String> page = connecte(TypeUtilisateur.MEMBRE).lire("/admin");
            assertThat(page.statusCode()).isEqualTo(403);
            assertThat(page.body()).contains("<h1>Accès refusé</h1>", "<a href=\"/mon-compte\">")
                    .doesNotContain(WHITELABEL);
        }

        @Test
        @DisplayName("administrateur sur un écran du super-administrateur : refus au service, retour au back-office")
        void administrateurSurUnEcranReserve() throws Exception {
            HttpResponse<String> page = connecte(TypeUtilisateur.ADMINISTRATEUR)
                    .lire("/admin/comptes/administrateurs");
            assertThat(page.statusCode()).isEqualTo(403);
            assertThat(page.body()).contains("<h1>Accès refusé</h1>", "<a href=\"/admin\">")
                    .doesNotContain(WHITELABEL);
        }
    }

    @Nested
    @DisplayName("500 et autres statuts")
    class AutresStatuts {

        private MockMvc mvc() {
            return MockMvcBuilders.webAppContextSetup(contexte).apply(springSecurity()).build();
        }

        private String rendre(int statut) throws Exception {
            return mvc().perform(get("/error").with(user("membre@exemple.be").roles("MEMBRE"))
                            .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, statut)
                            .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/quelque-part")
                            .accept(MediaType.TEXT_HTML).locale(java.util.Locale.FRENCH))
                    .andReturn().getResponse().getContentAsString();
        }

        @Test
        @DisplayName("500 : page traduite, sans trace ni message technique")
        void erreurInterne() throws Exception {
            assertThat(rendre(500)).contains("<h1>Erreur inattendue</h1>")
                    .doesNotContain(WHITELABEL, "Exception", "/quelque-part");
        }

        @Test
        @DisplayName("tout autre statut (405) : page de repli du projet")
        void repli() throws Exception {
            assertThat(rendre(405)).contains("<h1>Demande impossible</h1>").doesNotContain(WHITELABEL);
        }
    }
}
