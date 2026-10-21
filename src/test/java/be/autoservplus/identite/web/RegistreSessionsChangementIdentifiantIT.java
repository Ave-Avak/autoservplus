package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.RevocationSessions;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le registre de sessions suit-il le changement d identifiant qui fait aboutir la
 * connexion ?
 *
 * <p><b>Pourquoi un vrai serveur.</b> Le registre apprend le changement par
 * {@code HttpSessionIdListener}, que le conteneur de servlets appelle et que MockMvc
 * n appelle jamais : sous MockMvc, ce test passerait ou echouerait pour une raison sans
 * rapport avec la production. S il echouait en production, l identifiant de la session
 * complete serait inconnu du registre, et la suspension ou la reinitialisation ne la
 * fermerait pas — en silence, puisque le filtre de sessions laisse passer ce qu il ne
 * connait pas.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Registre de sessions et changement d'identifiant (serveur réel)")
class RegistreSessionsChangementIdentifiantIT extends SocleIntegration {

    private static final Pattern JETON_CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final String MOT_DE_PASSE = "phrase de passe du test serveur";
    private static final byte[] SECRET = "secret de vingt octs".getBytes();

    @LocalServerPort private int port;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ChiffrementSecretMfa chiffrement;
    @Autowired private SessionRegistry registre;
    @Autowired private RevocationSessions revocation;
    @Autowired private Clock horloge;

    private final CookieManager cookies = new CookieManager();
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private URI uri(String chemin) {
        return URI.create("http://localhost:" + port + chemin);
    }

    private HttpResponse<String> lire(String chemin) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(chemin)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> envoyer(String chemin, String jetonCsrf, String... paires) throws Exception {
        StringBuilder corps = new StringBuilder("_csrf=").append(encoder(jetonCsrf));
        for (int i = 0; i < paires.length; i += 2) {
            corps.append('&').append(paires[i]).append('=').append(encoder(paires[i + 1]));
        }
        return client.send(HttpRequest.newBuilder(uri(chemin))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(corps.toString()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String encoder(String valeur) {
        return URLEncoder.encode(valeur, StandardCharsets.UTF_8);
    }

    private static String csrf(HttpResponse<String> page) {
        Matcher jeton = JETON_CSRF.matcher(page.body());
        assertThat(jeton.find()).as("jeton CSRF dans la page").isTrue();
        return jeton.group(1);
    }

    private String identifiantDeSession() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("JSESSIONID"))
                .map(HttpCookie::getValue)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("après le second facteur, le registre connaît le nouvel identifiant et oublie l'ancien")
    void registreSuitLeChangementDIdentifiant() throws Exception {
        Utilisateur admin = new Utilisateur("serveur-reel@exemple.be", encodeur.encode(MOT_DE_PASSE),
                "Nom", "Prenom", TypeUtilisateur.ADMINISTRATEUR);
        admin.confirmerAdresseEmail();
        admin.setStatut(StatutUtilisateur.ACTIF);
        admin = utilisateurs.saveAndFlush(admin);
        ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(SECRET, admin.getId());
        admin.commencerEnrolement(chiffre.donnees(), chiffre.version());
        admin.confirmerEnrolement(horloge.instant(), Totp.pasDe(horloge.instant()) - 10);
        utilisateurs.saveAndFlush(admin);

        HttpResponse<String> connexion = envoyer("/connexion", csrf(lire("/connexion")),
                "email", admin.getEmail(), "password", MOT_DE_PASSE);
        assertThat(connexion.headers().firstValue("Location").orElseThrow())
                .endsWith("/connexion/etape/second-facteur");
        String identifiantPartiel = identifiantDeSession();
        assertThat(registre.getSessionInformation(identifiantPartiel)).isNotNull();

        HttpResponse<String> aboutie = envoyer("/connexion/etape/second-facteur",
                csrf(lire("/connexion/etape/second-facteur")),
                "code", Totp.code(SECRET, Totp.pasDe(horloge.instant())));
        assertThat(aboutie.headers().firstValue("Location").orElseThrow()).endsWith("/mon-compte");
        String identifiantComplet = identifiantDeSession();

        assertThat(identifiantComplet).isNotEqualTo(identifiantPartiel);
        assertThat(registre.getSessionInformation(identifiantPartiel)).isNull();
        assertThat(registre.getSessionInformation(identifiantComplet)).isNotNull();
        assertThat(lire("/mon-compte").statusCode()).isEqualTo(200);

        // La preuve qui compte : une revocation atteint bien la session complete.
        assertThat(revocation.revoquer(admin.getEmail())).isPositive();
        HttpResponse<String> apresRevocation = lire("/mon-compte");
        assertThat(apresRevocation.statusCode()).isEqualTo(302);
        assertThat(apresRevocation.headers().firstValue("Location").orElseThrow()).endsWith("/connexion");
    }
}
