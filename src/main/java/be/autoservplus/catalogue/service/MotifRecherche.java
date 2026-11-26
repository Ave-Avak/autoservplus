package be.autoservplus.catalogue.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    /** Mots gardés d'un terme, les suivants sont ignorés. */
    static final int MOTS_MAXIMUM = 5;

    /** Motif qui accepte tout texte : il occupe la place d'un mot absent. */
    static final String SANS_CONTRAINTE = "%";

    private MotifRecherche() {
    }

    /**
     * Un motif par mot du terme, complété par {@link #SANS_CONTRAINTE} jusqu'à
     * {@link #MOTS_MAXIMUM}. F5 cherche « un ou plusieurs mots-clés séparés par des
     * espaces » : chaque mot doit apparaître quelque part dans l'article, pas forcément à
     * la suite des autres. « plaquettes freinage » trouve ainsi la prestation dont le
     * libellé dit « plaquettes » et la catégorie « Freinage ». Le nombre de motifs est
     * fixe parce qu'une requête JPQL a un nombre fixe de paramètres.
     */
    static List<String> mots(String terme) {
        List<String> motifs = new ArrayList<>(Arrays.stream(terme.trim().split("\\s+"))
                .filter(mot -> !mot.isEmpty())
                .distinct()
                .limit(MOTS_MAXIMUM)
                .map(MotifRecherche::de)
                .toList());
        while (motifs.size() < MOTS_MAXIMUM) {
            motifs.add(SANS_CONTRAINTE);
        }
        return List.copyOf(motifs);
    }

    static String de(String terme) {
        String nettoye = terme.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + nettoye + "%";
    }
}
