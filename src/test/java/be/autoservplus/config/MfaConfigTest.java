package be.autoservplus.config;

import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Clé maître TOTP au démarrage")
class MfaConfigTest {

    private static final String CLE_32_OCTETS = Base64.getEncoder().encodeToString(new byte[32]);

    /**
     * Plus de profil d exception : le refus ne depend que de la presence de la cle. La
     * methode ne recoit d ailleurs plus l environnement — aucun profil ne peut l assouplir.
     */
    @ParameterizedTest(name = "cle « {0} »")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("sans clé, le démarrage est refusé, quel que soit le profil")
    void refusSansCle(String cle) {
        assertThatThrownBy(() -> MfaConfig.construire(cle, (short) 1, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MFA_CLE_MAITRE");
    }

    @Test
    @DisplayName("une clé qui ne fait pas 32 octets est refusée, et non tronquée en AES-128")
    void longueurExigee() {
        String seize = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> MfaConfig.construire(seize, (short) 1, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 octets");
    }

    @Test
    @DisplayName("une clé valide chiffre sous la version déclarée")
    void cleValide() {
        ChiffrementSecretMfa chiffrement = MfaConfig.construire(CLE_32_OCTETS, (short) 3, "");

        byte[] secret = {1, 2, 3};
        assertThat(chiffrement.versionActive()).isEqualTo((short) 3);
        assertThat(chiffrement.dechiffrer(chiffrement.chiffrer(secret, 1L).donnees(), (short) 3, 1L))
                .isEqualTo(secret);
    }

    @Test
    @DisplayName("les clés précédentes déchiffrent ce qu'elles ont chiffré")
    void clesPrecedentes() {
        ChiffrementSecretMfa avant = MfaConfig.construire(CLE_32_OCTETS, (short) 1, "");
        byte[] chiffre = avant.chiffrer(new byte[]{7}, 5L).donnees();
        String nouvelle = CLE_32_OCTETS.replace('A', 'B');

        ChiffrementSecretMfa apres = MfaConfig.construire(nouvelle, (short) 2, "1:" + CLE_32_OCTETS);

        assertThat(apres.dechiffrer(chiffre, (short) 1, 5L)).containsExactly(7);
        assertThat(apres.versionActive()).isEqualTo((short) 2);
    }
}
