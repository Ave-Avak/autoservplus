package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.Base32;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Second facteur de l'administrateur de démonstration")
class SecondFacteurDemonstrationTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-08T08:00:00Z");
    private static final byte[] SECRET_PUBLIE = Base32.decoder(SecondFacteurDemonstration.SECRET_PUBLIE);

    @Mock private UtilisateurRepository utilisateurs;

    private final ChiffrementSecretMfa chiffrement = chiffrement((byte) 1);
    private Utilisateur admin;

    private static ChiffrementSecretMfa chiffrement(byte remplissage) {
        byte[] cle = new byte[32];
        Arrays.fill(cle, remplissage);
        return new ChiffrementSecretMfa(Map.of((short) 1, new SecretKeySpec(cle, "AES")), (short) 1);
    }

    @BeforeEach
    void setUp() {
        admin = new Utilisateur(SecondFacteurDemonstration.ADRESSE_ADMIN_DEMO, "x", "Admin", "Demo",
                TypeUtilisateur.ADMINISTRATEUR);
        ReflectionTestUtils.setField(admin, "id", 7L);
        when(utilisateurs.findByEmailIgnoreCase(SecondFacteurDemonstration.ADRESSE_ADMIN_DEMO))
                .thenReturn(Optional.of(admin));
    }

    private void demarrer() {
        new SecondFacteurDemonstration(utilisateurs, chiffrement, Clock.fixed(MAINTENANT, ZoneOffset.UTC))
                .run(null);
    }

    private byte[] secretEnPlace() {
        return chiffrement.dechiffrer(admin.getMfaSecretChiffre(), admin.getMfaCleVersion(), admin.getId());
    }

    /** Le cas de la base restauree depuis le dump : l admin existe, sans aucun secret. */
    @Test
    @DisplayName("admin existant sans secret : le secret publié est posé et actif")
    void poseSiManquant() {
        demarrer();

        assertThat(admin.secondFacteurActif()).isTrue();
        assertThat(secretEnPlace()).isEqualTo(SECRET_PUBLIE);
    }

    @Test
    @DisplayName("admin déjà enrôlé avec un secret lisible : rien n'est touché")
    void rienSiPresent() {
        byte[] autre = "un autre secret vingt".getBytes();
        ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(autre, 7L);
        admin.commencerEnrolement(chiffre.donnees(), chiffre.version());
        admin.confirmerEnrolement(MAINTENANT.minusSeconds(3600), 123L);
        byte[] avant = admin.getMfaSecretChiffre();

        demarrer();

        assertThat(admin.getMfaSecretChiffre()).isSameAs(avant);
        assertThat(admin.getMfaDernierPas()).isEqualTo(123L);
        assertThat(secretEnPlace()).isEqualTo(autre);
    }

    /**
     * Cle ephemere de {@code dev} : le secret pose au demarrage precedent ne s ouvre plus.
     * Le laisser en place enfermerait l admin de demonstration hors du back-office.
     */
    @Test
    @DisplayName("secret chiffré sous une clé disparue : compté comme manquant, reposé")
    void reposeSiIndechiffrable() {
        ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement((byte) 2).chiffrer(SECRET_PUBLIE, 7L);
        admin.commencerEnrolement(chiffre.donnees(), chiffre.version());
        admin.confirmerEnrolement(MAINTENANT, 1L);

        demarrer();

        assertThat(secretEnPlace()).isEqualTo(SECRET_PUBLIE);
    }
}
