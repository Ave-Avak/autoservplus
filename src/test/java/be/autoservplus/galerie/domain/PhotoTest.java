package be.autoservplus.galerie.domain;

import be.autoservplus.catalogue.domain.Categorie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Photo : visuel de catégorie, dimensions et texte alternatif (V79)")
class PhotoTest {

    private final Categorie categorie = Mockito.mock(Categorie.class);

    @Test
    @DisplayName("un visuel de catégorie n'a pas d'autre origine")
    void visuelDeCategorie() {
        Photo photo = Photo.pourCategorie(categorie, "demonstration/x.webp", "Visuel", (short) 0);

        assertThat(photo.getCategorie()).isSameAs(categorie);
        assertThat(photo.getPrestation()).isNull();
        assertThat(photo.getPiece()).isNull();
        assertThat(photo.getIntervention()).isNull();
        assertThat(photo.getCheminVignette()).isNull();
    }

    @Test
    @DisplayName("les dimensions se posent ensemble et strictement positives")
    void dimensions() {
        Photo photo = Photo.pourCategorie(categorie, "x.webp", "Visuel", (short) 0);

        photo.poserDimensions(1024, 768);

        assertThat(photo.getLargeur()).isEqualTo(1024);
        assertThat(photo.getHauteur()).isEqualTo(768);
        assertThatThrownBy(() -> photo.poserDimensions(0, 768)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> photo.poserDimensions(1024, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sans traduction, le texte alternatif est rendu en français avec sa langue")
    void texteAlternatifEnRepli() {
        Photo photo = Photo.pourCategorie(categorie, "x.webp", "Visuel de la catégorie", (short) 0);

        var neerlandais = photo.texteAlt(Locale.forLanguageTag("nl"));

        assertThat(neerlandais.texte()).isEqualTo("Visuel de la catégorie");
        assertThat(neerlandais.langue()).isEqualTo("fr");
    }

    @Test
    @DisplayName("une catégorie absente est refusée")
    void categorieObligatoire() {
        assertThatThrownBy(() -> Photo.pourCategorie(null, "x.webp", "Visuel", (short) 0))
                .isInstanceOf(NullPointerException.class);
    }
}
