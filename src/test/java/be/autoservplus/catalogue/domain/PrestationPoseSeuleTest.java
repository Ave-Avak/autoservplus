package be.autoservplus.catalogue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Prestation : prix pose seule et composition (V76)")
class PrestationPoseSeuleTest {

    private final Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
    private final Categorie filtres = new Categorie("FILTRES", "Filtres", TypeCategorie.PIECE);

    private Prestation vidange() {
        return new Prestation(entretien, "VID", "Vidange", new BigDecimal("69.00"), 30);
    }

    private Prestation mainDOeuvre() {
        return new Prestation(entretien, "MO", "Main-d'œuvre", new BigDecimal("65.00"), 60, UnitePrestation.HEURE);
    }

    private Piece piece(String reference) {
        return new Piece(filtres, reference, "Pièce " + reference, new BigDecimal("10.00"));
    }

    @Nested
    @DisplayName("Prix pose seule")
    class PoseSeule {

        @Test
        @DisplayName("aucun par défaut : le choix n'est pas proposé")
        void aucunParDefaut() {
            Prestation vidange = vidange();

            assertThat(vidange.proposePoseSeule()).isFalse();
            assertThat(vidange.prixPoseSeuleTvac()).isNull();
        }

        @Test
        @DisplayName("un prix inférieur au prix pièces comprises est retenu, avec son prix TVA comprise")
        void inferieurRetenu() {
            Prestation vidange = vidange();

            vidange.fixerPrix(new BigDecimal("69.00"), new BigDecimal("39.00"));

            assertThat(vidange.proposePoseSeule()).isTrue();
            assertThat(vidange.getPrixPoseSeuleHtva()).isEqualByComparingTo("39.00");
            assertThat(vidange.prixPoseSeuleTvac()).isEqualByComparingTo("47.19");
        }

        @Test
        @DisplayName("zéro est admis, comme le CHECK de V76")
        void zeroAdmis() {
            Prestation vidange = vidange();

            vidange.fixerPrix(new BigDecimal("69.00"), BigDecimal.ZERO);

            assertThat(vidange.proposePoseSeule()).isTrue();
        }

        @ParameterizedTest(name = "pose seule {0} pour 69,00 refusée")
        @CsvSource({"69.00", "69.01", "120.00", "-0.01"})
        @DisplayName("égal, supérieur ou négatif : refusé, rien ne change")
        void pasInferieurRefuse(String poseSeule) {
            Prestation vidange = vidange();

            assertThatThrownBy(() -> vidange.fixerPrix(new BigDecimal("69.00"), new BigDecimal(poseSeule)))
                    .isInstanceOf(PrixPoseSeuleRefuseException.class)
                    .extracting("cleMessage").isEqualTo("admin.catalogue.erreur.pose-seule-prix");
            assertThat(vidange.proposePoseSeule()).isFalse();
        }

        @Test
        @DisplayName("refusé sur une prestation à l'heure")
        void refuseALHeure() {
            Prestation mainDOeuvre = mainDOeuvre();

            assertThatThrownBy(() -> mainDOeuvre.fixerPrix(new BigDecimal("65.00"), new BigDecimal("30.00")))
                    .isInstanceOf(PrixPoseSeuleRefuseException.class)
                    .extracting("cleMessage").isEqualTo("admin.catalogue.erreur.pose-seule-heure");
        }

        @Test
        @DisplayName("une baisse du seul prix pièces comprises sous le prix pose seule est refusée")
        void baisseSousLaPoseSeuleRefusee() {
            Prestation vidange = vidange();
            vidange.fixerPrix(new BigDecimal("69.00"), new BigDecimal("39.00"));

            assertThatThrownBy(() -> vidange.modifierPrix(new BigDecimal("35.00")))
                    .isInstanceOf(PrixPoseSeuleRefuseException.class);
            assertThat(vidange.getPrixHtva()).isEqualByComparingTo("69.00");
        }

        @Test
        @DisplayName("une baisse des deux prix ensemble est acceptée")
        void baisseSimultanee() {
            Prestation vidange = vidange();
            vidange.fixerPrix(new BigDecimal("69.00"), new BigDecimal("39.00"));

            vidange.fixerPrix(new BigDecimal("35.00"), new BigDecimal("20.00"));

            assertThat(vidange.getPrixHtva()).isEqualByComparingTo("35.00");
            assertThat(vidange.getPrixPoseSeuleHtva()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("un prix pose seule nul retire le choix")
        void retrait() {
            Prestation vidange = vidange();
            vidange.fixerPrix(new BigDecimal("69.00"), new BigDecimal("39.00"));

            vidange.fixerPrix(new BigDecimal("69.00"), null);

            assertThat(vidange.proposePoseSeule()).isFalse();
        }
    }

    @Nested
    @DisplayName("Composition")
    class Composition {

        @Test
        @DisplayName("une pièce s'ajoute, puis prend la nouvelle quantité au lieu d'être doublée")
        void ajoutPuisQuantite() {
            Prestation vidange = vidange();
            Piece huile = piece("HUILE-5L");

            vidange.fixerComposant(huile, 1);
            vidange.fixerComposant(huile, 2);

            assertThat(vidange.getComposition()).singleElement()
                    .satisfies(c -> {
                        assertThat(c.getPiece()).isSameAs(huile);
                        assertThat(c.getQuantite()).isEqualTo(2);
                    });
        }

        @Test
        @DisplayName("deux pièces, dans l'ordre de leur ajout, et le retrait de l'une")
        void retrait() {
            Prestation vidange = vidange();
            Piece huile = piece("HUILE-5L");
            Piece filtre = piece("FILTRE");
            vidange.fixerComposant(huile, 1);
            vidange.fixerComposant(filtre, 1);

            assertThat(vidange.retirerComposant(huile)).isTrue();
            assertThat(vidange.retirerComposant(huile)).isFalse();
            assertThat(vidange.getComposition()).extracting(ComposantPrestation::getPiece).containsExactly(filtre);
        }

        @Test
        @DisplayName("une quantité inférieure à un est refusée")
        void quantiteRefusee() {
            Prestation vidange = vidange();

            assertThatThrownBy(() -> vidange.fixerComposant(piece("FILTRE"), 0))
                    .isInstanceOf(CompositionRefuseeException.class)
                    .extracting("cleMessage").isEqualTo("admin.catalogue.erreur.composition-quantite");
            assertThat(vidange.getComposition()).isEmpty();
        }

        @Test
        @DisplayName("refusée sur une prestation à l'heure")
        void refuseeALHeure() {
            Prestation mainDOeuvre = mainDOeuvre();

            assertThatThrownBy(() -> mainDOeuvre.fixerComposant(piece("FILTRE"), 1))
                    .isInstanceOf(CompositionRefuseeException.class)
                    .extracting("cleMessage").isEqualTo("admin.catalogue.erreur.composition-heure");
        }

        @Test
        @DisplayName("la liste rendue ne se modifie pas de l'extérieur")
        void listeNonModifiable() {
            Prestation vidange = vidange();

            assertThatThrownBy(() -> vidange.getComposition().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
