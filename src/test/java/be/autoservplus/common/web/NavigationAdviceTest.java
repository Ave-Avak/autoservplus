package be.autoservplus.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rubrique du menu marquée selon l'adresse")
class NavigationAdviceTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/catalogue", "/services", "/pieces", "/accessoires", "/entretien",
            "/services/6f1c8a6e-0000-4000-8000-000000000001", "/entretien/6f1c8a6e-0000-4000-8000-000000000002"})
    @DisplayName("les quatre listes du catalogue et leurs fiches marquent l'entrée Catalogue")
    void catalogue(String chemin) {
        assertThat(NavigationAdvice.rubrique(chemin)).isEqualTo(NavigationAdvice.CATALOGUE);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/", "/contact", "/recherche", "/piecesdetachees", "/mes-rendez-vous/nouveau", "/admin/pieces"})
    @DisplayName("aucune autre adresse, même de préfixe voisin")
    void horsCatalogue(String chemin) {
        assertThat(NavigationAdvice.rubrique(chemin)).isNull();
    }
}
