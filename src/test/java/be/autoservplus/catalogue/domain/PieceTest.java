package be.autoservplus.catalogue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Piece")
class PieceTest {

    private Piece filtre() {
        Categorie filtres = new Categorie("P_FILTRES", "Filtres", TypeCategorie.PIECE);
        return new Piece(filtres, "F-001", "Filtre a huile", new BigDecimal("12.50"));
    }

    @Test
    @DisplayName("retire la quantite demandee du stock")
    void retireDuStock() {
        Piece piece = filtre();
        piece.setQuantiteStock(10);

        piece.retirerDuStock(3);

        assertThat(piece.getQuantiteStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("refuse de descendre sous zero")
    void refuseUnStockNegatif() {
        Piece piece = filtre();
        piece.setQuantiteStock(2);

        assertThatThrownBy(() -> piece.retirerDuStock(5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stock insuffisant");

        assertThat(piece.getQuantiteStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("refuse une quantite nulle ou negative")
    void refuseUneQuantiteInvalide() {
        Piece piece = filtre();
        piece.setQuantiteStock(10);

        assertThatThrownBy(() -> piece.retirerDuStock(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> piece.ajouterAuStock(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("n est disponible que si elle est active et en stock")
    void determineLaDisponibilite() {
        Piece piece = filtre();

        piece.setQuantiteStock(0);
        assertThat(piece.estDisponible()).isFalse();

        piece.setQuantiteStock(5);
        assertThat(piece.estDisponible()).isTrue();

        piece.desactiver();
        assertThat(piece.estDisponible()).isFalse();
    }

    @Test
    @DisplayName("signale le franchissement du seuil d alerte")
    void signaleLeSeuilDAlerte() {
        Piece piece = filtre();
        piece.setSeuilAlerte(3);

        piece.setQuantiteStock(5);
        assertThat(piece.stockSousLeSeuil()).isFalse();

        piece.setQuantiteStock(3);
        assertThat(piece.stockSousLeSeuil()).isTrue();
    }

    @Test
    @DisplayName("calcule le prix taxe comprise")
    void calculeLePrixTvac() {
        assertThat(filtre().prixTvac()).isEqualByComparingTo(new BigDecimal("15.13"));
    }

    @Test
    @DisplayName("accepte un taux de TVA belge et refuse un taux hors liste")
    void verrouilleLeTauxDeTva() {
        Piece piece = filtre();

        piece.setTauxTva(new BigDecimal("6.00"));
        assertThat(piece.getTauxTva()).isEqualByComparingTo(new BigDecimal("6.00"));

        assertThatThrownBy(() -> piece.setTauxTva(new BigDecimal("19.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Taux de TVA");

        // Le dernier taux valide reste en place : le refus n a rien modifie.
        assertThat(piece.getTauxTva()).isEqualByComparingTo(new BigDecimal("6.00"));
    }

    @Test
    @DisplayName("renomme la piece")
    void renommeLaPiece() {
        Piece piece = filtre();

        piece.renommer("Filtre à huile longue durée");

        assertThat(piece.getLibelle()).isEqualTo("Filtre à huile longue durée");
    }

    @Test
    @DisplayName("change de categorie mais refuse une categorie de prestations")
    void changeDeCategorieAvecGardeDeType() {
        Piece piece = filtre();
        Categorie eclairage = new Categorie("P_ECLAIRAGE", "Éclairage", TypeCategorie.PIECE);
        Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);

        piece.changerCategorie(eclairage);
        assertThat(piece.getCategorie()).isEqualTo(eclairage);

        assertThatThrownBy(() -> piece.changerCategorie(entretien))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destinee aux prestations");

        assertThat(piece.getCategorie()).isEqualTo(eclairage);
    }
}