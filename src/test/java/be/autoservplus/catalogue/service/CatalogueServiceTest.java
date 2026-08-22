package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du service de catalogue.
 *
 * <p>Verifie notamment la coherence entre le type d une categorie et la nature de
 * l element qu on y rattache, ainsi que le comportement de la recherche.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogueService")
class CatalogueServiceTest {

    @Mock private CategorieRepository categories;
    @Mock private PrestationRepository prestations;
    @Mock private PieceRepository pieces;

    @InjectMocks private CatalogueService service;

    @Nested
    @DisplayName("recherche")
    class Recherche {

        @Test
        @DisplayName("renvoie une page vide pour un terme trop court")
        void refuseUnTermeTropCourt() {
            assertThat(service.rechercherPrestations("a", 0)).isEmpty();
            assertThat(service.rechercherPieces("a", 0)).isEmpty();

            verifyNoInteractions(prestations, pieces);
        }

        @Test
        @DisplayName("renvoie une page vide pour un terme nul ou blanc")
        void refuseUnTermeVide() {
            assertThat(service.rechercherPrestations(null, 0)).isEmpty();
            assertThat(service.rechercherPieces("   ", 0)).isEmpty();

            verifyNoInteractions(prestations, pieces);
        }

        @Test
        @DisplayName("interroge le repository avec le terme nettoye")
        void nettoieLeTerme() {
            when(prestations.rechercher(eq("vidange"), any())).thenReturn(org.springframework.data.domain.Page.empty());

            service.rechercherPrestations("  vidange  ", 0);

            verify(prestations).rechercher(eq("vidange"), any());
        }
    }

    @Nested
    @DisplayName("consultation")
    class Consultation {

        @Test
        @DisplayName("ne renvoie que les categories actives du type demande")
        void listeLesCategoriesActives() {
            Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
            when(categories.findByTypeAndActifTrueOrderByOrdreAsc(TypeCategorie.SERVICE))
                    .thenReturn(List.of(entretien));

            assertThat(service.categoriesDePrestations())
                    .hasSize(1)
                    .first()
                    .extracting(Categorie::getCode)
                    .isEqualTo("ENTRETIEN");
        }

        @Test
        @DisplayName("leve une exception sur une reference de prestation inconnue")
        void echoueSurUneReferenceInconnue() {
            UUID reference = UUID.randomUUID();
            when(prestations.findByReference(reference)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.prestationParReference(reference))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("suppression logique")
    class Suppression {

        @Test
        @DisplayName("supprime logiquement une prestation sans appeler delete")
        void supprimeUnePrestationLogiquement() {
            Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
            Prestation prestation = new Prestation(entretien, "VID", "Vidange",
                    new BigDecimal("75.00"), 60);
            when(prestations.findByReference(prestation.getReference()))
                    .thenReturn(Optional.of(prestation));

            service.supprimerPrestation(prestation.getReference(), "admin@autoservplus.be");

            assertThat(prestation.estSupprime()).isTrue();
            assertThat(prestation.getDeletedBy()).isEqualTo("admin@autoservplus.be");
            verify(prestations, never()).delete(any());
        }

        @Test
        @DisplayName("supprime logiquement une piece sans appeler delete")
        void supprimeUnePieceLogiquement() {
            Piece piece = pieceAvecStock(5);
            when(pieces.findByReference(piece.getReference())).thenReturn(Optional.of(piece));

            service.supprimerPiece(piece.getReference(), "admin@autoservplus.be");

            assertThat(piece.estSupprime()).isTrue();
            verify(pieces, never()).delete(any());
        }
    }

    private Piece pieceAvecStock(int quantite) {
        Categorie filtres = new Categorie("P_FILTRES", "Filtres", TypeCategorie.PIECE);
        Piece piece = new Piece(filtres, "F-001", "Filtre a huile", new BigDecimal("12.50"));
        piece.setQuantiteStock(quantite);
        piece.setSeuilAlerte(2);
        return piece;
    }
}