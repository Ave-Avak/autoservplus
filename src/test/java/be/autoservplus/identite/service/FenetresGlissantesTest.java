package be.autoservplus.identite.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FenetresGlissantes")
class FenetresGlissantesTest {

    private static final Duration FENETRE = Duration.ofMinutes(15);
    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    @Test
    @DisplayName("refuse au-delà de la limite, puis accepte quand le plus ancien sort de la fenêtre")
    void fenetreGlissante() {
        FenetresGlissantes carte = new FenetresGlissantes(FENETRE, 10);
        assertThat(carte.ajouterSousLimite("a", 2, T0)).isTrue();
        assertThat(carte.ajouterSousLimite("a", 2, T0.plusSeconds(60))).isTrue();

        assertThat(carte.ajouterSousLimite("a", 2, T0.plusSeconds(120))).isFalse();
        assertThat(carte.ajouterSousLimite("a", 2, T0.plus(FENETRE).plusNanos(1))).isTrue();
        assertThat(carte.compter("a", T0.plus(FENETRE).plusNanos(1))).isEqualTo(2);
    }

    @Test
    @DisplayName("compter purge la clé et la retire quand elle se vide")
    void compterPurge() {
        FenetresGlissantes carte = new FenetresGlissantes(FENETRE, 10);
        carte.ajouter("a", T0);

        assertThat(carte.compter("a", T0.plus(FENETRE).plusSeconds(1))).isZero();
        assertThat(carte.nombreDeCles()).isZero();
    }

    @Test
    @DisplayName("pleine, la carte purge d'abord les clés expirées de toute la carte")
    void saturationApresPurge() {
        FenetresGlissantes carte = new FenetresGlissantes(FENETRE, 2);
        carte.ajouter("a", T0);
        carte.ajouter("b", T0.plusSeconds(600));

        assertThat(carte.saturee("c", T0.plusSeconds(600))).isTrue();
        assertThat(carte.saturee("a", T0.plusSeconds(600))).isFalse();

        Instant apresA = T0.plus(FENETRE).plusSeconds(1);
        assertThat(carte.saturee("c", apresA)).isFalse();
        assertThat(carte.nombreDeCles()).isEqualTo(1);
    }

    @Test
    @DisplayName("saturée, elle n'accepte aucune clé nouvelle et garde les existantes")
    void saturee() {
        FenetresGlissantes carte = new FenetresGlissantes(FENETRE, 1);
        carte.ajouter("a", T0);

        carte.ajouter("b", T0);
        carte.ajouter("a", T0.plusSeconds(1));

        assertThat(carte.compter("b", T0.plusSeconds(1))).isZero();
        assertThat(carte.compter("a", T0.plusSeconds(1))).isEqualTo(2);
    }

    @Test
    @DisplayName("retirer efface la clé")
    void retirer() {
        FenetresGlissantes carte = new FenetresGlissantes(FENETRE, 10);
        carte.ajouter("a", T0);

        carte.retirer("a");

        assertThat(carte.compter("a", T0)).isZero();
    }
}
