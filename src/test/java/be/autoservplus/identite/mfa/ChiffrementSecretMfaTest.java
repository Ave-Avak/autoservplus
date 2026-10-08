package be.autoservplus.identite.mfa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Chiffrement des secrets TOTP (AES-256-GCM)")
class ChiffrementSecretMfaTest {

    private static final byte[] SECRET = "vingt octets secrets".getBytes();
    private static final SecretKey CLE_1 = cle((byte) 1);
    private static final SecretKey CLE_2 = cle((byte) 2);

    private static SecretKey cle(byte remplissage) {
        byte[] octets = new byte[32];
        Arrays.fill(octets, remplissage);
        return new SecretKeySpec(octets, "AES");
    }

    private final ChiffrementSecretMfa chiffrement =
            new ChiffrementSecretMfa(Map.of((short) 1, CLE_1), (short) 1);

    @Test
    @DisplayName("le secret se relit à l'identique, et n'apparaît pas en clair")
    void allerRetour() {
        ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(SECRET, 42L);

        assertThat(chiffre.version()).isEqualTo((short) 1);
        assertThat(new String(chiffre.donnees())).doesNotContain("secrets");
        assertThat(chiffrement.dechiffrer(chiffre.donnees(), chiffre.version(), 42L)).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("deux chiffrements du même secret diffèrent : le nonce est neuf à chaque fois")
    void nonceNeuf() {
        assertThat(chiffrement.chiffrer(SECRET, 42L).donnees())
                .isNotEqualTo(chiffrement.chiffrer(SECRET, 42L).donnees());
    }

    /**
     * La donnee authentifiee lie le chiffre a son compte : recopie sur une autre ligne, il
     * doit echouer au lieu de donner a un second compte le secret du premier.
     */
    @Test
    @DisplayName("un chiffré recopié sur un autre compte ne se déchiffre pas")
    void lieAuCompte() {
        ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(SECRET, 42L);

        assertThatThrownBy(() -> chiffrement.dechiffrer(chiffre.donnees(), chiffre.version(), 43L))
                .isInstanceOf(ChiffrementSecretMfa.SecretIndechiffrableException.class);
    }

    @Test
    @DisplayName("un octet altéré fait échouer le tag")
    void alteration() {
        byte[] donnees = chiffrement.chiffrer(SECRET, 42L).donnees();
        donnees[donnees.length - 1] ^= 1;

        assertThatThrownBy(() -> chiffrement.dechiffrer(donnees, (short) 1, 42L))
                .isInstanceOf(ChiffrementSecretMfa.SecretIndechiffrableException.class);
    }

    @Test
    @DisplayName("rotation : l'ancienne clé déchiffre encore, la nouvelle seule chiffre")
    void rotation() {
        byte[] sousAncienne = chiffrement.chiffrer(SECRET, 42L).donnees();
        ChiffrementSecretMfa apresRotation =
                new ChiffrementSecretMfa(Map.of((short) 1, CLE_1, (short) 2, CLE_2), (short) 2);

        assertThat(apresRotation.dechiffrer(sousAncienne, (short) 1, 42L)).isEqualTo(SECRET);
        assertThat(apresRotation.chiffrer(SECRET, 42L).version()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("une version inconnue est refusée, pas devinée")
    void versionInconnue() {
        byte[] donnees = chiffrement.chiffrer(SECRET, 42L).donnees();

        assertThatThrownBy(() -> chiffrement.dechiffrer(donnees, (short) 9, 42L))
                .isInstanceOf(ChiffrementSecretMfa.SecretIndechiffrableException.class)
                .hasMessageContaining("9");
    }
}
