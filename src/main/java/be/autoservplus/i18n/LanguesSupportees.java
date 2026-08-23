package be.autoservplus.i18n;

import be.autoservplus.identite.domain.Langue;

import java.util.List;
import java.util.Locale;

/**
 * Ensemble ferme des langues de l interface (F6) : francais, neerlandais, anglais.
 *
 * <p>La liste n est pas redeclaree ici : elle est <b>derivee de l enumeration
 * {@link Langue}</b>, qui est deja la source de verite de la colonne
 * {@code utilisateur.langue} et de la langue des documents PDF. Une quatrieme
 * langue s ajoutera donc a un seul endroit, et il sera impossible qu on puisse
 * choisir au site une langue que le profil ne sait pas stocker.</p>
 *
 * <p>L ensemble est ferme, et c est le point important pour l accessibilite. Sans
 * cette contrainte, un navigateur annoncant {@code Accept-Language: de} obtiendrait
 * une locale allemande : les messages retomberaient sur le fichier par defaut, donc
 * en francais, mais l attribut {@code lang} du document annoncerait
 * {@code de}. Un lecteur d ecran prononcerait alors du francais avec une phonetique
 * allemande — exactement la non-conformite <b>WCAG 3.1.1</b> que F6 corrige, sous
 * une autre forme. Toute locale inconnue est donc ramenee au francais.</p>
 */
public final class LanguesSupportees {

    /** Langue de repli, et langue du fichier {@code messages.properties} lui-meme. */
    public static final Locale DEFAUT = Locale.forLanguageTag(Langue.fr.name());

    private static final List<Locale> ADMISES =
            List.of(Langue.values()).stream()
                    .map(langue -> Locale.forLanguageTag(langue.name()))
                    .toList();

    private LanguesSupportees() {
        // classe utilitaire
    }

    /** Les trois locales proposees au selecteur, dans l ordre de l enumeration. */
    public static List<Locale> admises() {
        return ADMISES;
    }

    /**
     * Ramene une locale quelconque a l une des langues admises.
     *
     * <p>La comparaison porte sur la <b>langue seule</b> et non sur la locale
     * complete : {@code nl-BE} et {@code nl-NL} designent le meme fichier de
     * messages, et un visiteur flamand ne doit pas basculer en francais pour une
     * question de region.</p>
     *
     * @return la locale admise correspondante, ou {@link #DEFAUT} si aucune ne
     *         correspond
     */
    public static Locale plusProche(Locale demandee) {
        if (demandee == null) {
            return DEFAUT;
        }
        return ADMISES.stream()
                .filter(admise -> admise.getLanguage().equals(demandee.getLanguage()))
                .findFirst()
                .orElse(DEFAUT);
    }

    /** Vraie si la locale active correspond a la langue donnee. */
    public static boolean estActive(Locale active, Locale candidate) {
        return active != null && candidate != null
                && active.getLanguage().equals(candidate.getLanguage());
    }
}
