package be.autoservplus.catalogue.domain;

import java.util.Locale;

/**
 * Un texte du catalogue, avec la langue dans laquelle il est réellement écrit.
 *
 * <p>Le français est la langue d'origine du catalogue, toujours renseignée. Le
 * néerlandais et l'anglais sont facultatifs : sans traduction, le texte français est
 * rendu à leur place. La langue voyage avec le texte parce que la page, elle, reste dans
 * la langue demandée : un libellé français sur une page néerlandaise doit porter
 * {@code lang="fr"}, sans quoi un lecteur d'écran le prononce comme du néerlandais
 * (WCAG 2.1, critère 3.1.2).</p>
 *
 * @param texte  le texte à afficher, {@code null} si le français lui-même est absent
 * @param langue code de la langue du texte : {@code fr}, {@code nl} ou {@code en}
 */
public record TexteLocalise(String texte, String langue) {

    /** Langue d'origine du catalogue, et langue de repli. */
    public static final String FRANCAIS = "fr";

    /**
     * Le texte dans la langue demandée, ou le français quand la traduction manque.
     *
     * @param demandee  langue de l'écran, du destinataire ou du document
     * @param francais  texte d'origine
     * @param neerlandais traduction néerlandaise, éventuellement absente
     * @param anglais   traduction anglaise, éventuellement absente
     */
    public static TexteLocalise choisir(Locale demandee, String francais, String neerlandais, String anglais) {
        String langue = demandee == null ? FRANCAIS : demandee.getLanguage();
        if ("nl".equals(langue) && present(neerlandais)) {
            return new TexteLocalise(neerlandais, "nl");
        }
        if ("en".equals(langue) && present(anglais)) {
            return new TexteLocalise(anglais, "en");
        }
        return new TexteLocalise(francais, FRANCAIS);
    }

    /**
     * La langue à déclarer sur l'élément qui porte ce texte, ou {@code null} quand il est
     * déjà dans la langue de la page : un attribut {@code lang} qui répète celui du
     * document n'apprend rien. Un texte absent ne déclare rien non plus.
     */
    public String langueSiRepli(Locale page) {
        if (texte == null || page == null || langue.equals(page.getLanguage())) {
            return null;
        }
        return langue;
    }

    /** Vrai pour une traduction saisie : ni nulle ni faite d'espaces. */
    public static boolean present(String texte) {
        return texte != null && !texte.isBlank();
    }
}
