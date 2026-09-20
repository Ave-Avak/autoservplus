package be.autoservplus.identite.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verification par k-anonymat, sans reseau.
 *
 * <p>Le serveur simule est indispensable ici : ce qui doit etre verifie n est pas la
 * reponse du service mais <b>ce que la requete emporte</b>, et le comportement quand
 * elle echoue.</p>
 */
@DisplayName("Verification Have I Been Pwned")
class VerificateurHaveIBeenPwnedTest {

    private static final String MOT_DE_PASSE = "phrase-de-passe-connue";

    private MockRestServiceServer serveur;
    private VerificateurHaveIBeenPwned verificateur;

    private void avecServeur() {
        RestClient.Builder constructeur = RestClient.builder()
                .baseUrl("https://api.pwnedpasswords.com");
        serveur = MockRestServiceServer.bindTo(constructeur).build();
        verificateur = new VerificateurHaveIBeenPwned(constructeur.build());
    }

    private static String empreinte(String valeur) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(valeur.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * <b>Le cas qui justifie le k-anonymat.</b> L URL ne doit porter que les cinq
     * premiers caracteres de l empreinte — ni le mot de passe, ni son empreinte
     * complete, ni rien qui identifie le compte.
     */
    @Test
    @DisplayName("seuls cinq caractères d'empreinte quittent le serveur")
    void seulLePrefixePart() {
        avecServeur();
        String complete = empreinte(MOT_DE_PASSE);
        serveur.expect(requestTo("https://api.pwnedpasswords.com/range/"
                        + complete.substring(0, 5)))
                .andRespond(withSuccess("0000000000000000000000000000000000:3",
                        MediaType.TEXT_PLAIN));

        verificateur.estCompromis(MOT_DE_PASSE);

        serveur.verify();
        // Et la preuve en creux : le reste de l empreinte n a pas voyage.
        assertThat(complete.substring(5)).isNotEmpty();
    }

    @Test
    @DisplayName("suffixe présent dans la réponse : mot de passe compromis")
    void suffixePresent() {
        avecServeur();
        String complete = empreinte(MOT_DE_PASSE);
        serveur.expect(requestTo("https://api.pwnedpasswords.com/range/"
                        + complete.substring(0, 5)))
                .andRespond(withSuccess("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:1\r\n"
                        + complete.substring(5) + ":42", MediaType.TEXT_PLAIN));

        assertThat(verificateur.estCompromis(MOT_DE_PASSE)).isTrue();
    }

    @Test
    @DisplayName("suffixe absent : mot de passe accepté")
    void suffixeAbsent() {
        avecServeur();
        serveur.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.pwnedpasswords.com/range/")))
                .andRespond(withSuccess("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:1",
                        MediaType.TEXT_PLAIN));

        assertThat(verificateur.estCompromis(MOT_DE_PASSE)).isFalse();
    }

    /**
     * Le contrat de la frontiere : un service en panne ne bloque pas une inscription.
     * Refuser un mot de passe correct parce qu un tiers est indisponible serait un
     * deni de service inflige a l utilisateur.
     */
    @Test
    @DisplayName("service en erreur : le mot de passe passe, sans exception")
    void serviceEnPanne() {
        avecServeur();
        serveur.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.pwnedpasswords.com/range/")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(verificateur.estCompromis(MOT_DE_PASSE)).isFalse();
    }

    @Test
    @DisplayName("mot de passe vide : aucun appel")
    void videSansAppel() {
        avecServeur();

        assertThat(verificateur.estCompromis("")).isFalse();
        assertThat(verificateur.estCompromis(null)).isFalse();

        serveur.verify(); // aucune attente posee : tout appel ferait echouer
    }
}
