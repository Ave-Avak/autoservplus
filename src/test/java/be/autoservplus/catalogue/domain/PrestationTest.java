package be.autoservplus.catalogue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Prestation")
class PrestationTest {

    private final Categorie entretien =
            new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);

    @ParameterizedTest(name = "{0} EUR HTVA a {1} % donne {2} EUR TVAC")
    @CsvSource({
            "75.00,  21.00, 90.75",
            "100.00, 21.00, 121.00",
            "12.50,  21.00, 15.13",
            "49.99,  21.00, 60.49",
            "75.00,   6.00, 79.50",
            "0.00,   21.00,  0.00"
    })
    @DisplayName("calcule le prix taxe comprise sans erreur d arrondi")
    void calculeLePrixTvac(String htva, String taux, String tvacAttendu) {
        Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                new BigDecimal(htva), 60);
        prestation.setTauxTva(new BigDecimal(taux));

        assertThat(prestation.prixTvac()).isEqualByComparingTo(new BigDecimal(tvacAttendu));
    }

    @Test
    @DisplayName("deduit le montant de la taxe du prix affiche")
    void calculeLeMontantDeTva() {
        Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                new BigDecimal("100.00"), 60);

        assertThat(prestation.montantTva()).isEqualByComparingTo(new BigDecimal("21.00"));
    }

    @Test
    @DisplayName("refuse un prix negatif")
    void refuseUnPrixNegatif() {
        Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                new BigDecimal("75.00"), 60);

        assertThatThrownBy(() -> prestation.modifierPrix(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("desactive puis reactive la prestation")
    void basculeLActivation() {
        Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                new BigDecimal("75.00"), 60);

        prestation.desactiver();
        assertThat(prestation.isActif()).isFalse();

        prestation.activer();
        assertThat(prestation.isActif()).isTrue();
    }
}