package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une page d erreur rendue pendant une connexion partielle ne montre ni le lien
 * « Mon compte » ni les compteurs de l en-tete.
 *
 * <p><b>Pourquoi un vrai serveur.</b> Une erreur que le controleur ne rattrape pas
 * repasse par le conteneur ({@code sendError}, puis dispatch {@code ERROR} vers
 * {@code /error}) ; MockMvc ne fait jamais ce second passage. Deux chemins, donc : la
 * page d erreur du projet, rendue par le controleur sous le contexte anonyme de
 * {@code ConnexionPartielleHorsEtapes}, et la page du conteneur, que l autorisation de
 * {@code /error} refuse a une connexion partielle avant tout rendu.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Page d'erreur pendant une connexion partielle (serveur réel)")
class ConnexionPartiellePageErreurIT extends SocleIntegration {

    private static final Pattern JETON_CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final String MOT_DE_PASSE = "phrase de passe du test des erreurs";
    private static final byte[] SECRET = "secret de vingt octs".getBytes();

    @LocalServerPort private int port;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ChiffrementSecretMfa chiffrement;
    @Autowired private Clock horloge;

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private HttpResponse<String> lire(String chemin) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
                        .header("Accept", "text/html").header("Accept-Language", "fr").GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> envoyer(String chemin, String jetonCsrf, String... paires) throws Exception {
        StringBuilder corps = new StringBuilder("_csrf=").append(encoder(jetonCsrf));
        for (int i = 0; i < paires.length; i += 2) {
            corps.append('&').append(paires[i]).append('=').append(encoder(paires[i + 1]));
        }
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
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

    /** Ni lien vers l espace membre, ni compteur du panier : ce qu affiche un en-tete connecte. */
    private static void sansTraceDeConnexion(HttpResponse<String> page) {
        assertThat(page.body())
                .doesNotContain("href=\"/mon-compte\"")
                .doesNotContain("href=\"/panier\"")
                .doesNotContain("href=\"/mes-notifications\"");
    }

    @Test
    @DisplayName("404 et erreur du conteneur, en connexion partielle : aucun élément d'une connexion")
    void pageDErreurSansConnexion() throws Exception {
        Utilisateur admin = new Utilisateur("erreur-partielle@exemple.be", encodeur.encode(MOT_DE_PASSE),
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

        // Rattrapee par le controleur : la page d erreur du projet est rendue.
        HttpResponse<String> introuvable = lire("/pieces/" + UUID.randomUUID());
        assertThat(introuvable.statusCode()).isEqualTo(404);
        assertThat(introuvable.body()).contains("<main id=\"contenu\">");
        sansTraceDeConnexion(introuvable);

        // Non rattrapee : le dispatch d erreur vers /error n est pas une route publique,
        // il est donc refuse a la connexion partielle et renvoie a l etape. Aucune page
        // n est rendue, et la session partielle survit.
        HttpResponse<String> conteneur = lire("/pieces/pas-un-identifiant");
        assertThat(conteneur.statusCode()).isEqualTo(302);
        assertThat(conteneur.headers().firstValue("Location").orElseThrow()).endsWith(EtapeConnexion.PREFIXE);
        assertThat(conteneur.body()).isEmpty();

        // Contre-epreuve : la connexion aboutie, l en-tete montre bien ces liens —
        // l assertion sait donc les voir.
        HttpResponse<String> aboutie = envoyer("/connexion/etape/second-facteur",
                csrf(lire("/connexion/etape/second-facteur")),
                "code", Totp.code(SECRET, Totp.pasDe(horloge.instant())));
        assertThat(aboutie.headers().firstValue("Location").orElseThrow()).endsWith("/admin");
        assertThat(lire("/pieces").body()).contains("href=\"/mon-compte\"", "href=\"/panier\"");
    }
}
