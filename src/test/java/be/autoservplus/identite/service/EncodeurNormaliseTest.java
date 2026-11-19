package be.autoservplus.identite.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EncodeurNormalise")
class EncodeurNormaliseTest {

    /** « é » en un point de code, puis en « e » suivi de l'accent aigu combinant. */
    private static final String PRECOMPOSE = "phrase de passe déjà longue";
    private static final String DECOMPOSE = "phrase de passe déjà longue";

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
    private final EncodeurNormalise encodeur = new EncodeurNormalise(bcrypt);

    @Test
    @DisplayName("un « é » précomposé et un « é » décomposé sont le même mot de passe")
    void deuxSaisiesUnMotDePasse() {
        assertThat(PRECOMPOSE).isNotEqualTo(DECOMPOSE);

        String empreinte = encodeur.encode(DECOMPOSE);

        assertThat(encodeur.matches(PRECOMPOSE, empreinte)).isTrue();
        assertThat(encodeur.matches(DECOMPOSE, empreinte)).isTrue();
        assertThat(bcrypt.matches(PRECOMPOSE, empreinte)).as("forme hachée").isTrue();
    }

    @Test
    @DisplayName("NFKC ramène les formes de compatibilité : ligature et pleine chasse")
    void formesDeCompatibilite() {
        assertThat(EncodeurNormalise.normaliser("ﬁn de journée ＡＢ１"))
                .isEqualTo("fin de journée AB1");
    }

    @Test
    @DisplayName("une empreinte de la forme brute se vérifie encore, et demande sa migration")
    void empreinteAncienne() {
        String ancienne = bcrypt.encode(DECOMPOSE);

        assertThat(encodeur.matches(DECOMPOSE, ancienne)).isTrue();
        assertThat(encodeur.exigeMigration(DECOMPOSE, ancienne)).isTrue();
        assertThat(encodeur.matches(PRECOMPOSE, ancienne)).as("autre saisie, avant migration").isFalse();
    }

    @Test
    @DisplayName("aucune migration pour une empreinte déjà normalisée, ni pour une saisie déjà normale")
    void sansMigration() {
        assertThat(encodeur.exigeMigration(DECOMPOSE, encodeur.encode(DECOMPOSE))).isFalse();
        assertThat(encodeur.exigeMigration("phrase de passe ascii", bcrypt.encode("phrase de passe ascii")))
                .isFalse();
    }

    @Test
    @DisplayName("BCrypt refuse de hacher 73 octets, mais les vérifie par troncature")
    void soixanteTreizeOctets() {
        String soixanteTreize = "a".repeat(73);
        String empreinteDes72 = bcrypt.encode(soixanteTreize.substring(0, 72));

        assertThatThrownBy(() -> bcrypt.encode(soixanteTreize))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes");
        assertThatThrownBy(() -> encodeur.encode(soixanteTreize))
                .isInstanceOf(IllegalArgumentException.class);
        // Constat sur la version employée : la vérification lit les 72 premiers octets et
        // ignore la suite. Seule la politique tient la limite à l'écriture.
        assertThat(bcrypt.matches(soixanteTreize, empreinteDes72)).isTrue();
        assertThat(encodeur.matches(soixanteTreize, empreinteDes72)).isTrue();
    }

    @Test
    @DisplayName("aucune migration quand la forme normalisée dépasse 72 octets")
    void formeNormaliseeTropLongue() {
        String brut = "½".repeat(30);
        String ancienne = bcrypt.encode(brut);

        assertThat(EncodeurNormalise.normaliser(brut).getBytes(StandardCharsets.UTF_8)).hasSize(150);
        assertThat(encodeur.matches(brut, ancienne)).isTrue();
        assertThat(encodeur.exigeMigration(brut, ancienne)).isFalse();
    }

    @Test
    @DisplayName("un mauvais mot de passe reste refusé sous ses deux formes")
    void mauvaisMotDePasse() {
        String empreinte = encodeur.encode(PRECOMPOSE);

        assertThat(encodeur.matches("phrase de passe déjà courte", empreinte)).isFalse();
    }
}
