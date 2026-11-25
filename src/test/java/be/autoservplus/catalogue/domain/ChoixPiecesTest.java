package be.autoservplus.catalogue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Choix des pièces (V77)")
class ChoixPiecesTest {

    private final Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);

    private Prestation vidange() {
        return new Prestation(entretien, "VID", "Vidange", new BigDecimal("69.00"), 30);
    }

    private Prestation vidangeAvecPoseSeule() {
        Prestation vidange = vidange();
        vidange.fixerPrix(new BigDecimal("69.00"), new BigDecimal("39.00"));
        return vidange;
    }

    @Nested
    @DisplayName("Prix figé selon le choix")
    class Prix {

        @Test
        @DisplayName("pièces fournies : prix pièces comprises")
        void fournies() {
            assertThat(vidangeAvecPoseSeule().prixHtvaPour(ChoixPieces.FOURNIES_PAR_GARAGE))
                    .isEqualByComparingTo("69.00");
        }

        @Test
        @DisplayName("pièces apportées : prix pose seule")
        void apportees() {
            assertThat(vidangeAvecPoseSeule().prixHtvaPour(ChoixPieces.APPORTEES_PAR_CLIENT))
                    .isEqualByComparingTo("39.00");
        }

        @Test
        @DisplayName("sans choix proposé, aucun choix et le seul prix")
        void sansChoixPropose() {
            assertThat(vidange().prixHtvaPour(null)).isEqualByComparingTo("69.00");
        }

        @Test
        @DisplayName("choix proposé mais absent : refusé, avec la référence de la prestation")
        void absentRefuse() {
            Prestation vidange = vidangeAvecPoseSeule();

            assertThatThrownBy(() -> vidange.prixHtvaPour(null))
                    .isInstanceOfSatisfying(ChoixPiecesRefuseException.class, refus -> {
                        assertThat(refus.getCleMessage()).isEqualTo("choix-pieces.erreur.manquant");
                        assertThat(refus.getPrestation()).isEqualTo(vidange.getReference());
                        assertThat(refus.getArguments()).containsExactly("Vidange");
                    });
        }

        @Test
        @DisplayName("choix donné alors qu'il n'est pas proposé : refusé")
        void donneSansEtrePropose() {
            assertThatThrownBy(() -> vidange().prixHtvaPour(ChoixPieces.APPORTEES_PAR_CLIENT))
                    .isInstanceOfSatisfying(ChoixPiecesRefuseException.class,
                            refus -> assertThat(refus.getCleMessage()).isEqualTo("choix-pieces.erreur.non-propose"));
        }

        @Test
        @DisplayName("le choix par défaut du back-office : pièces fournies, ou aucun sans choix proposé")
        void choixParDefaut() {
            assertThat(vidangeAvecPoseSeule().choixParDefaut()).isEqualTo(ChoixPieces.FOURNIES_PAR_GARAGE);
            assertThat(vidange().choixParDefaut()).isNull();
        }
    }

    @Nested
    @DisplayName("Trois états en base")
    class TroisEtats {

        private final ConvertisseurChoixPieces convertisseur = new ConvertisseurChoixPieces();

        @Test
        @DisplayName("nul pour aucun choix, faux pour les pièces fournies, vrai pour les pièces apportées")
        void versLaBase() {
            assertThat(convertisseur.convertToDatabaseColumn(null)).isNull();
            assertThat(convertisseur.convertToDatabaseColumn(ChoixPieces.FOURNIES_PAR_GARAGE)).isFalse();
            assertThat(convertisseur.convertToDatabaseColumn(ChoixPieces.APPORTEES_PAR_CLIENT)).isTrue();
        }

        @Test
        @DisplayName("relu sans perte")
        void depuisLaBase() {
            assertThat(convertisseur.convertToEntityAttribute(null)).isNull();
            assertThat(convertisseur.convertToEntityAttribute(false)).isEqualTo(ChoixPieces.FOURNIES_PAR_GARAGE);
            assertThat(convertisseur.convertToEntityAttribute(true)).isEqualTo(ChoixPieces.APPORTEES_PAR_CLIENT);
        }

        @Test
        @DisplayName("une ligne sans choix ne porte aucune mention")
        void mention() {
            assertThat(ChoixPieces.cleMentionDe(Optional.empty())).isNull();
            assertThat(ChoixPieces.cleMentionDe(Optional.of(ChoixPieces.APPORTEES_PAR_CLIENT)))
                    .isEqualTo("choix-pieces.mention.apportees");
        }
    }
}
