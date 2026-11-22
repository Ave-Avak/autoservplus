package be.autoservplus.catalogue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Texte du catalogue : langue demandée, repli sur le français")
class TexteLocaliseTest {

    private static final Locale FR = Locale.FRENCH;
    private static final Locale NL = Locale.forLanguageTag("nl");
    private static final Locale EN = Locale.ENGLISH;

    @Test
    @DisplayName("la traduction demandée est rendue quand elle existe")
    void traductionPresente() {
        assertThat(TexteLocalise.choisir(NL, "Filtre à huile", "Oliefilter", "Oil filter"))
                .isEqualTo(new TexteLocalise("Oliefilter", "nl"));
        assertThat(TexteLocalise.choisir(EN, "Filtre à huile", "Oliefilter", "Oil filter"))
                .isEqualTo(new TexteLocalise("Oil filter", "en"));
        assertThat(TexteLocalise.choisir(FR, "Filtre à huile", "Oliefilter", "Oil filter"))
                .isEqualTo(new TexteLocalise("Filtre à huile", "fr"));
    }

    @Test
    @DisplayName("sans traduction, nulle ou faite d'espaces, le français est rendu et se déclare")
    void repliSurLeFrancais() {
        TexteLocalise absent = TexteLocalise.choisir(NL, "Filtre à huile", null, "Oil filter");
        TexteLocalise blanc = TexteLocalise.choisir(EN, "Filtre à huile", "Oliefilter", "   ");

        assertThat(absent).isEqualTo(new TexteLocalise("Filtre à huile", "fr"));
        assertThat(blanc).isEqualTo(new TexteLocalise("Filtre à huile", "fr"));
        assertThat(absent.langueSiRepli(NL)).isEqualTo("fr");
        assertThat(blanc.langueSiRepli(EN)).isEqualTo("fr");
    }

    @Test
    @DisplayName("un texte dans la langue de la page ne déclare aucune langue, un texte absent non plus")
    void aucuneLangueADeclarer() {
        assertThat(TexteLocalise.choisir(NL, "Filtre à huile", "Oliefilter", null).langueSiRepli(NL)).isNull();
        assertThat(TexteLocalise.choisir(FR, "Filtre à huile", null, null).langueSiRepli(FR)).isNull();
        assertThat(TexteLocalise.choisir(NL, null, null, null).langueSiRepli(NL)).isNull();
    }

    @Test
    @DisplayName("une langue hors des trois du site reçoit le français")
    void langueInconnue() {
        assertThat(TexteLocalise.choisir(Locale.GERMAN, "Filtre à huile", "Oliefilter", "Oil filter").texte())
                .isEqualTo("Filtre à huile");
        assertThat(TexteLocalise.choisir(null, "Filtre à huile", "Oliefilter", "Oil filter").texte())
                .isEqualTo("Filtre à huile");
    }

    @Test
    @DisplayName("une saisie vide est rangée nulle, et un article sans traduction n'en rend aucune")
    void saisieVide() {
        Traductions saisies = new Traductions("  Oliefilter ", "", "   ", null);

        assertThat(saisies.getLibelleNl()).isEqualTo("Oliefilter");
        assertThat(saisies.getLibelleEn()).isNull();
        assertThat(saisies.getDescriptionNl()).isNull();
        assertThat(saisies.libelleComplet()).isFalse();
        assertThat(new Traductions("Oliefilter", "Oil filter", null, null).libelleComplet()).isTrue();

        Piece piece = new Piece(new Categorie("FILTRES", "Filtres", TypeCategorie.PIECE), "FH-1", "Filtre à huile",
                new BigDecimal("12.40"));
        assertThat(piece.libelle(NL)).isEqualTo(new TexteLocalise("Filtre à huile", "fr"));
        piece.traduire(saisies);
        assertThat(piece.libelle(NL).texte()).isEqualTo("Oliefilter");
        assertThat(piece.libelle(EN).texte()).isEqualTo("Filtre à huile");
    }
}
