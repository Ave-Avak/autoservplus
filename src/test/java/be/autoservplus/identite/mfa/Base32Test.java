package be.autoservplus.identite.mfa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Base 32 (RFC 4648)")
class Base32Test {

    /** Vecteurs de la RFC 4648 §10, sans le remplissage que l URI otpauth n emploie pas. */
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({"f, MY", "fo, MZXQ", "foo, MZXW6", "foob, MZXW6YQ", "fooba, MZXW6YTB",
            "foobar, MZXW6YTBOI"})
    @DisplayName("encode et décode les vecteurs de la RFC")
    void vecteursDeLaRfc(String clair, String attendu) {
        byte[] octets = clair.getBytes(StandardCharsets.US_ASCII);

        assertThat(Base32.encoder(octets)).isEqualTo(attendu);
        assertThat(Base32.decoder(attendu)).isEqualTo(octets);
    }

    @Test
    @DisplayName("décode sans égard à la casse, aux espaces ni au remplissage")
    void decodageTolerant() {
        assertThat(Base32.decoder("mzxw 6ytb oi======"))
                .isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("refuse un caractère hors alphabet")
    void caractereInvalide() {
        assertThatThrownBy(() -> Base32.decoder("MZ1W")).isInstanceOf(IllegalArgumentException.class);
    }
}
