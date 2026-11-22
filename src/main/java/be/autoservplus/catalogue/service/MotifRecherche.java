package be.autoservplus.catalogue.service;

import java.util.Locale;

/**
 * Motif {@code LIKE} construit depuis le terme saisi par le visiteur.
 *
 * <p>Le terme est une donnée, jamais un motif : « 100% » doit chercher le signe pour
 * cent, et « _ » le tiret bas. Leurs jokers sont donc neutralisés par « ! », caractère
 * d'échappement que les requêtes de recherche déclarent ({@code ESCAPE '!'}). La barre
 * oblique inverse, échappement par défaut de PostgreSQL, a été écartée : elle se double
 * une fois en Java et une fois en JPQL, et une erreur sur le nombre ne se voit pas.</p>
 *
 * <p>La mise en minuscules se fait ici, en {@code Locale.ROOT}, pour ne pas dépendre de
 * la langue de la machine. Les accents sont retirés par la base, des deux côtés.</p>
 */
final class MotifRecherche {

    private MotifRecherche() {
    }

    static String de(String terme) {
        String nettoye = terme.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + nettoye + "%";
    }
}
