package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.RegleMetierException;
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
    @DisplayName("creation de categorie")
    class CreationCategorie {

        @Test
        @DisplayName("enregistre la categorie lorsque le code est libre")
        void creeQuandCodeLibre() {
            when(categories.existsByCode("FREINAGE")).thenReturn(false);
            when(categories.save(any(Categorie.class))).thenAnswer(i -> i.getArgument(0));

            Categorie resultat = service.creerCategorie("FREINAGE", "Freinage", TypeCategorie.SERVICE);

            assertThat(resultat.getCode()).isEqualTo("FREINAGE");
            assertThat(resultat.getType()).isEqualTo(TypeCategorie.SERVICE);
            assertThat(resultat.isActif()).isTrue();
        }

        @Test
        @DisplayName("refuse un code deja utilise")
        void refuseUnCodeDuplique() {
            when(categories.existsByCode("FREINAGE")).thenReturn(true);

            assertThatThrownBy(() -> service.creerCategorie("FREINAGE", "Freinage", TypeCategorie.SERVICE))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("RM-28");

            verify(categories, never()).save(any());
        }
    }

    @Nested
    @DisplayName("creation de prestation")
    class CreationPrestation {

        @Test
        @DisplayName("rattache la prestation a une categorie de service")
        void creeUnePrestation() {
            Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
            when(prestations.existsByCode("VID")).thenReturn(false);
            when(categories.findByCode("ENTRETIEN")).thenReturn(Optional.of(entretien));
            when(prestations.save(any(Prestation.class))).thenAnswer(i -> i.getArgument(0));

            Prestation resultat = service.creerPrestation(
                    "ENTRETIEN", "VID", "Vidange", new BigDecimal("75.00"), 60);

            assertThat(resultat.getCode()).isEqualTo("VID");
            assertThat(resultat.getCategorie()).isEqualTo(entretien);
            assertThat(resultat.getDureeMinutes()).isEqualTo(60);
            assertThat(resultat.getReference()).isNotNull();
        }

        @Test
        @DisplayName("refuse une categorie destinee aux pieces")
        void refuseUneCategorieDePieces() {
            Categorie filtres = new Categorie("P_FILTRES", "Filtres", TypeCategorie.PIECE);
            when(prestations.existsByCode("VID")).thenReturn(false);
            when(categories.findByCode("P_FILTRES")).thenReturn(Optional.of(filtres));

            assertThatThrownBy(() -> service.creerPrestation(
                    "P_FILTRES", "VID", "Vidange", new BigDecimal("75.00"), 60))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("RM-29");

            verify(prestations, never()).save(any());
        }

        @Test
        @DisplayName("refuse un code de prestation deja utilise")
        void refuseUnCodeDuplique() {
            when(prestations.existsByCode("VID")).thenReturn(true);

            assertThatThrownBy(() -> service.creerPrestation(
                    "ENTRETIEN", "VID", "Vidange", new BigDecimal("75.00"), 60))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("RM-29");
        }

        @Test
        @DisplayName("refuse une categorie inexistante")
        void refuseUneCategorieInconnue() {
            when(prestations.existsByCode("VID")).thenReturn(false);
            when(categories.findByCode("INCONNUE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creerPrestation(
                    "INCONNUE", "VID", "Vidange", new BigDecimal("75.00"), 60))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("creation de piece")
    class CreationPiece {

        @Test
        @DisplayName("rattache la piece a une categorie de pieces")
        void creeUnePiece() {
            Categorie filtres = new Categorie("P_FILTRES", "Filtres", TypeCategorie.PIECE);
            when(pieces.existsByReferenceFabricant("F-001")).thenReturn(false);
            when(categories.findByCode("P_FILTRES")).thenReturn(Optional.of(filtres));
            when(pieces.save(any(Piece.class))).thenAnswer(i -> i.getArgument(0));

            Piece resultat = service.creerPiece("P_FILTRES", "F-001", "Filtre a huile",
                    new BigDecimal("12.50"));

            assertThat(resultat.getReferenceFabricant()).isEqualTo("F-001");
            assertThat(resultat.getQuantiteStock()).isZero();
        }

        @Test
        @DisplayName("refuse une categorie destinee aux prestations")
        void refuseUneCategorieDeServices() {
            Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
            when(pieces.existsByReferenceFabricant("F-001")).thenReturn(false);
            when(categories.findByCode("ENTRETIEN")).thenReturn(Optional.of(entretien));

            assertThatThrownBy(() -> service.creerPiece("ENTRETIEN", "F-001", "Filtre",
                    new BigDecimal("12.50")))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("RM-29");
        }

        @Test
        @DisplayName("refuse une reference fabricant deja enregistree")
        void refuseUneReferenceDupliquee() {
            when(pieces.existsByReferenceFabricant("F-001")).thenReturn(true);

            assertThatThrownBy(() -> service.creerPiece("P_FILTRES", "F-001", "Filtre",
                    new BigDecimal("12.50")))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("RM-29");
        }
    }

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
    @DisplayName("gestion du stock")
    class Stock {

        @Test
        @DisplayName("reapprovisionne une piece")
        void reapprovisionne() {
            Piece piece = pieceAvecStock(3);
            when(pieces.findByReference(piece.getReference())).thenReturn(Optional.of(piece));

            service.reapprovisionner(piece.getReference(), 10);

            assertThat(piece.getQuantiteStock()).isEqualTo(13);
        }

        @Test
        @DisplayName("signale les pieces sous le seuil d alerte")
        void signaleLesPiecesEnAlerte() {
            Piece piece = pieceAvecStock(1);
            when(pieces.enAlerteDeStock()).thenReturn(List.of(piece));

            assertThat(service.piecesEnAlerteDeStock()).containsExactly(piece);
        }
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