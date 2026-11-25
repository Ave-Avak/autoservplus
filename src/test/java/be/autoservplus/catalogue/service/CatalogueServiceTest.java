package be.autoservplus.catalogue.service;

import be.autoservplus.catalogue.domain.Rayon;
import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.service.dto.CategorieVue;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;
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
            assertThat(service.rechercherProduits("a", Rayon.PIECES_AUTO, 0)).isEmpty();

            verifyNoInteractions(prestations, pieces);
        }

        @Test
        @DisplayName("renvoie une page vide pour un terme nul ou blanc")
        void refuseUnTermeVide() {
            assertThat(service.rechercherPrestations(null, 0)).isEmpty();
            assertThat(service.rechercherProduits("   ", Rayon.PIECES_AUTO, 0)).isEmpty();

            verifyNoInteractions(prestations, pieces);
        }

        @Test
        @DisplayName("interroge le dépôt avec un motif en minuscules, encadré de jokers")
        void nettoieLeTerme() {
            when(prestations.rechercher(eq("%vidange%"), eq("nl"), any())).thenReturn(org.springframework.data.domain.Page.empty());
            // Sans contexte Spring, la langue hors requête serait celle de la machine : le
            // test fixe celle de la requête, que le service transmet pour le tri.
            LocaleContextHolder.setLocale(Locale.forLanguageTag("nl-BE"));
            try {
                service.rechercherPrestations("  Vidange  ", 0);
            } finally {
                LocaleContextHolder.resetLocaleContext();
            }

            verify(prestations).rechercher(eq("%vidange%"), eq("nl"), any());
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
                    .extracting(CategorieVue::code)
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

}