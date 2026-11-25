package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.service.dto.ArticleVue;
import org.springframework.data.domain.Page;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Une section des résultats de la recherche, dans l'ordre des onglets du catalogue.
 *
 * @param ancre      identifiant de la section, cible de ses liens de pagination
 * @param precedente adresse de la page précédente de cette section, les autres sections
 *                   gardant la leur, ou {@code null} sur la première page
 * @param suivante   adresse de la page suivante, ou {@code null} sur la dernière
 */
public record SectionRecherche(String ancre, String cleTitre, String clePagination,
                               Page<ArticleVue> resultats, String precedente, String suivante) {

    private static final String[] ANCRES = {"prestations", "pieces", "accessoires", "entretien"};
    private static final String[] PARAMETRES = {"pagePrestations", "pagePieces", "pageAccessoires", "pageEntretien"};

    /** Les quatre sections, dans l'ordre de {@code pages} : prestations, puis les trois rayons. */
    static List<SectionRecherche> toutes(String terme, List<Page<ArticleVue>> pages) {
        List<SectionRecherche> sections = new ArrayList<>();
        for (int i = 0; i < ANCRES.length; i++) {
            Page<ArticleVue> page = pages.get(i);
            sections.add(new SectionRecherche(ANCRES[i],
                    "recherche.section." + ANCRES[i], "recherche.pagination." + ANCRES[i], page,
                    page.hasPrevious() ? adresse(terme, pages, i, page.getNumber()) : null,
                    page.hasNext() ? adresse(terme, pages, i, page.getNumber() + 2) : null));
        }
        return List.copyOf(sections);
    }

    /** Adresse où la section {@code cible} passe à la page {@code numero}, comptée depuis 1. */
    private static String adresse(String terme, List<Page<ArticleVue>> pages, int cible, int numero) {
        UriComponentsBuilder adresse = UriComponentsBuilder.fromPath("/recherche").queryParam("q", terme);
        for (int i = 0; i < PARAMETRES.length; i++) {
            adresse.queryParam(PARAMETRES[i], i == cible ? numero : pages.get(i).getNumber() + 1);
        }
        return adresse.fragment(ANCRES[cible]).encode().build().toUriString();
    }
}
