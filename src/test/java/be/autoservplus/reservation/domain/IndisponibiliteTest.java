package be.autoservplus.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Indisponibilite")
class IndisponibiliteTest {

    private static final Instant H10 = Instant.parse("2026-09-15T10:00:00Z");
    private static final Instant H12 = Instant.parse("2026-09-15T12:00:00Z");

    @Test
    @DisplayName("sans poste, concerne tout l atelier")
    void toutLAtelier() {
        Indisponibilite fermeture = new Indisponibilite(null, H10, H12, "Formation");
        assertThat(fermeture.concerneToutLAtelier()).isTrue();
    }

    @Test
    @DisplayName("avec poste, ne concerne que lui")
    void unSeulPoste() {
        Indisponibilite panne = new Indisponibilite(new PosteAtelier("Pont 1"), H10, H12, "Pont en panne");
        assertThat(panne.concerneToutLAtelier()).isFalse();
    }

    @Test
    @DisplayName("refuse une fin anterieure ou egale au debut")
    void refuseIntervalleInverse() {
        assertThatThrownBy(() -> new Indisponibilite(null, H12, H10, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Indisponibilite(null, H10, H10, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{0}h, {1}h) contre [10h, 12h) -> {2}")
    @CsvSource({
            "08, 09, false",   // entierement avant
            "08, 10, false",   // se termine au debut : intervalles semi-ouverts, pas de contact
            "08, 11, true",    // deborde sur le debut
            "10, 12, true",    // identique
            "11, 13, true",    // deborde sur la fin
            "12, 13, false",   // commence a la fin
            "09, 13, true",    // englobe
            "10, 11, true"     // inclus
    })
    @DisplayName("detecte le chevauchement d intervalles semi-ouverts")
    void chevauche(String debut, String fin, boolean attendu) {
        Indisponibilite indispo = new Indisponibilite(null, H10, H12, "x");
        Instant d = Instant.parse("2026-09-15T" + debut + ":00:00Z");
        Instant f = Instant.parse("2026-09-15T" + fin + ":00:00Z");

        assertThat(indispo.chevauche(d, f)).isEqualTo(attendu);
    }

    @Test
    @DisplayName("refuse un motif vide ou trop long")
    void refuseMotifInvalide() {
        assertThatThrownBy(() -> new Indisponibilite(null, H10, H12, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Indisponibilite(null, H10, H12, "x".repeat(Indisponibilite.LONGUEUR_MOTIF + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la modification change poste, bornes et motif sous les mêmes gardes")
    void modification() {
        PosteAtelier pont = new PosteAtelier("Pont 1");
        Indisponibilite indispo = new Indisponibilite(null, H10, H12, "Formation");

        indispo.modifier(pont, H10, H12.plusSeconds(3600), "  Pont en panne ");

        assertThat(indispo.getPoste()).isSameAs(pont);
        assertThat(indispo.getFin()).isEqualTo(H12.plusSeconds(3600));
        assertThat(indispo.getMotif()).isEqualTo("Pont en panne");
    }

    @Test
    @DisplayName("une modification refusée laisse l'indisponibilité intacte")
    void modificationRefuseeSansEffet() {
        Indisponibilite indispo = new Indisponibilite(null, H10, H12, "Formation");

        assertThatThrownBy(() -> indispo.modifier(new PosteAtelier("Pont 1"), H12, H10, "Autre"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(indispo.concerneToutLAtelier()).isTrue();
        assertThat(indispo.getDebut()).isEqualTo(H10);
        assertThat(indispo.getMotif()).isEqualTo("Formation");
    }
}
