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

    @ParameterizedTest(name = "accepte le taux belge {0} %")
    @CsvSource({"0.00", "6.00", "12.00", "21.00", "21"})
    @DisplayName("accepte les quatre taux de TVA belges, quelle que soit l echelle")
    void accepteLesTauxBelges(String taux) {
        Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                new BigDecimal("75.00"), 60);

        prestation.setTauxTva(new BigDecimal(taux));

        assertThat(prestation.getTauxTva()).isEqualByComparingTo(new BigDecimal(taux));
    }

    @ParameterizedTest(name = "refuse le taux {0} %")
    @CsvSource({"19.00", "21.50", "-6.00", "100.00"})
    @DisplayName("refuse tout taux de TVA hors de la liste belge 0/6/12/21")
    void refuseUnTauxHorsListe(String taux) {
        Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                new BigDecimal("75.00"), 60);

        assertThatThrownBy(() -> prestation.setTauxTva(new BigDecimal(taux)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Taux de TVA");

        // Le taux d origine (21 %) reste en place : le refus n a rien modifie.
        assertThat(prestation.getTauxTva()).isEqualByComparingTo(new BigDecimal("21.00"));
    }

    @Test
    @DisplayName("renomme la prestation")
    void renommeLaPrestation() {
        Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                new BigDecimal("75.00"), 60);

        prestation.renommer("Vidange complète");

        assertThat(prestation.getLibelle()).isEqualTo("Vidange complète");
    }

    @Test
    @DisplayName("change de categorie vers une autre categorie de services")
    void changeDeCategorie() {
        Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                new BigDecimal("75.00"), 60);
        Categorie freinage = new Categorie("FREINAGE", "Freinage", TypeCategorie.SERVICE);

        prestation.changerCategorie(freinage);

        assertThat(prestation.getCategorie()).isEqualTo(freinage);
    }

    @Test
    @DisplayName("refuse le deplacement vers une categorie de pieces")
    void refuseUneCategorieDePieces() {
        Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                new BigDecimal("75.00"), 60);
        Categorie filtres = new Categorie("P_FILTRES", "Filtres", TypeCategorie.PIECE);

        assertThatThrownBy(() -> prestation.changerCategorie(filtres))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destinee aux pieces");

        assertThat(prestation.getCategorie()).isEqualTo(entretien);
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