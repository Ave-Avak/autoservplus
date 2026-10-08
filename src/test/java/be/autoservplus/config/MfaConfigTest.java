package be.autoservplus.config;

import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Clé maître TOTP au démarrage")
class MfaConfigTest {

    private static final String CLE_32_OCTETS = Base64.getEncoder().encodeToString(new byte[32]);

    private static MockEnvironment profils(String... actifs) {
        MockEnvironment environnement = new MockEnvironment();
        environnement.setActiveProfiles(actifs);
        return environnement;
    }

    @Test
    @DisplayName("sans clé, le démarrage est refusé sous le profil prod")
    void refusEnProduction() {
        assertThatThrownBy(() -> MfaConfig.construire(profils("prod"), "", (short) 1, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MFA_CLE_MAITRE");
    }

    @Test
    @DisplayName("sans clé, le démarrage est refusé sous tout profil autre que dev et test")
    void refusHorsListeBlanche() {
        assertThatThrownBy(() -> MfaConfig.construire(profils("demo"), null, (short) 1, ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sans clé, dev et test tirent une clé éphémère")
    void cleEphemereEnDeveloppement() {
        ChiffrementSecretMfa dev = MfaConfig.construire(profils("dev"), "", (short) 1, "");
        ChiffrementSecretMfa test = MfaConfig.construire(profils("test"), "", (short) 3, "");

        byte[] secret = {1, 2, 3};
        assertThat(dev.dechiffrer(dev.chiffrer(secret, 1L).donnees(), (short) 1, 1L)).isEqualTo(secret);
        assertThat(test.versionActive()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("une clé qui ne fait pas 32 octets est refusée, et non tronquée en AES-128")
    void longueurExigee() {
        String seize = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> MfaConfig.construire(profils("prod"), seize, (short) 1, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 octets");
    }

    @Test
    @DisplayName("les clés précédentes déchiffrent ce qu'elles ont chiffré")
    void clesPrecedentes() {
        ChiffrementSecretMfa avant = MfaConfig.construire(profils("prod"), CLE_32_OCTETS, (short) 1, "");
        byte[] chiffre = avant.chiffrer(new byte[]{7}, 5L).donnees();
        String nouvelle = Base64.getEncoder().encodeToString(new byte[32]).replace('A', 'B');

        ChiffrementSecretMfa apres = MfaConfig.construire(profils("prod"), nouvelle, (short) 2,
                "1:" + CLE_32_OCTETS);

        assertThat(apres.dechiffrer(chiffre, (short) 1, 5L)).containsExactly(7);
        assertThat(apres.versionActive()).isEqualTo((short) 2);
    }
}
