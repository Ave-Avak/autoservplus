package be.autoservplus.identite.mfa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TOTP (RFC 6238)")
class TotpTest {

    /** Cle de l annexe B pour HMAC-SHA1 : « 12345678901234567890 » en ASCII. */
    private static final byte[] CLE_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    /**
     * Vecteurs de l annexe B, colonne SHA1. Ils sont donnes sur huit chiffres : le calcul
     * est le meme, seule la troncature finale change — six chiffres en sont le modulo.
     */
    @ParameterizedTest(name = "T = {0} s -> {1}")
    @CsvSource({
            "59, 94287082",
            "1111111109, 07081804",
            "1111111111, 14050471",
            "1234567890, 89005924",
            "2000000000, 69279037",
            "20000000000, 65353130"})
    @DisplayName("reproduit les vecteurs de l'annexe B")
    void vecteursDeLaRfc(long secondes, String attendu) {
        long pas = Totp.pasDe(Instant.ofEpochSecond(secondes));

        assertThat(Totp.code(CLE_RFC, pas, 8)).isEqualTo(attendu);
        assertThat(Totp.code(CLE_RFC, pas)).isEqualTo(attendu.substring(2));
    }

    @Test
    @DisplayName("accepte le pas précédent et le suivant, pas au-delà")
    void tolerance() {
        Instant maintenant = Instant.ofEpochSecond(1_234_567_890L);
        long pas = Totp.pasDe(maintenant);

        assertThat(Totp.verifier(CLE_RFC, Totp.code(CLE_RFC, pas - 1), maintenant, null))
                .hasValue(pas - 1);
        assertThat(Totp.verifier(CLE_RFC, Totp.code(CLE_RFC, pas + 1), maintenant, null))
                .hasValue(pas + 1);
        assertThat(Totp.verifier(CLE_RFC, Totp.code(CLE_RFC, pas - 2), maintenant, null)).isEmpty();
        assertThat(Totp.verifier(CLE_RFC, Totp.code(CLE_RFC, pas + 2), maintenant, null)).isEmpty();
    }

    @Test
    @DisplayName("refuse un pas déjà consommé, ou antérieur au dernier accepté")
    void antiRejeu() {
        Instant maintenant = Instant.ofEpochSecond(1_234_567_890L);
        long pas = Totp.pasDe(maintenant);
        String code = Totp.code(CLE_RFC, pas);

        assertThat(Totp.verifier(CLE_RFC, code, maintenant, pas)).isEmpty();
        assertThat(Totp.verifier(CLE_RFC, Totp.code(CLE_RFC, pas - 1), maintenant, pas)).isEmpty();
        assertThat(Totp.verifier(CLE_RFC, code, maintenant, pas - 1)).hasValue(pas);
    }

    @Test
    @DisplayName("tolère les espaces de l'affichage, refuse tout ce qui n'est pas six chiffres")
    void formatDeSaisie() {
        Instant maintenant = Instant.ofEpochSecond(59);
        String code = Totp.code(CLE_RFC, Totp.pasDe(maintenant));

        assertThat(Totp.verifier(CLE_RFC, code.substring(0, 3) + " " + code.substring(3),
                maintenant, null)).isPresent();
        assertThat(Totp.verifier(CLE_RFC, null, maintenant, null)).isEqualTo(OptionalLong.empty());
        assertThat(Totp.verifier(CLE_RFC, "12345", maintenant, null)).isEmpty();
        assertThat(Totp.verifier(CLE_RFC, "12a456", maintenant, null)).isEmpty();
    }
}
