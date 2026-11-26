package be.autoservplus.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Liens de pagination calculés")
class PaginationVueTest {

    private static PageImpl<Integer> page(int numero, int taille, long total) {
        return new PageImpl<>(Collections.nCopies(taille, 0), PageRequest.of(numero, taille), total);
    }

    @Test
    @DisplayName("les filtres de l'adresse sont gardés, seul le numéro de page change")
    void filtresGardes() {
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/admin/comptes");
        requete.setParameter("recherche", "dupont");
        requete.setParameter("statut", "ACTIF");
        requete.setParameter("page", "2");

        PaginationVue pagination = PaginationVue.de(page(1, 10, 35), requete);

        assertThat(pagination.numero()).isEqualTo(2);
        assertThat(pagination.total()).isEqualTo(4);
        assertThat(pagination.precedente()).isEqualTo("/admin/comptes?recherche=dupont&statut=ACTIF&page=1");
        assertThat(pagination.suivante()).isEqualTo("/admin/comptes?recherche=dupont&statut=ACTIF&page=3");
    }

    @Test
    @DisplayName("une recherche avec espace, accent ou esperluette est encodée une seule fois")
    void encodageUnique() {
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/admin/comptes");
        requete.setParameter("recherche", "élise & co");

        PaginationVue pagination = PaginationVue.de(page(0, 10, 20), requete);

        assertThat(pagination.suivante()).isEqualTo("/admin/comptes?recherche=%C3%A9lise%20%26%20co&page=2");
    }

    @Test
    @DisplayName("ni précédente sur la première page, ni suivante sur la dernière, une page pour une liste vide")
    void bornes() {
        MockHttpServletRequest requete = new MockHttpServletRequest("GET", "/admin/avis");

        PaginationVue vide = PaginationVue.de(new PageImpl<>(Collections.<Integer>emptyList(), PageRequest.of(0, 10), 0),
                requete);

        assertThat(vide.total()).isEqualTo(1);
        assertThat(vide.precedente()).isNull();
        assertThat(vide.suivante()).isNull();
    }
}
