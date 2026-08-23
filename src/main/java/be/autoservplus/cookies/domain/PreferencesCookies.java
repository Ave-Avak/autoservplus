package be.autoservplus.cookies.domain;

import java.util.Optional;

/**
 * Etat courant du choix d un visiteur sur les finalites optionnelles de cookies
 * (F25), et format sous lequel ce choix voyage dans le cookie de preference.
 *
 * <p>Les cookies strictement necessaires n apparaissent pas ici : ils ne se
 * refusent pas, donc il n y a rien a memoriser a leur sujet. Ce type ne porte que
 * ce sur quoi l utilisateur a reellement la main.</p>
 *
 * <p><b>Aucune donnee personnelle</b> n y figure : deux booleens, rien qui designe
 * une personne. C est ce qui permet au cookie qui la transporte d etre lui-meme
 * qualifie de strictement necessaire — il n existe que pour ne pas redemander a
 * chaque page un choix deja exprime, ce qui est le service demande par
 * l utilisateur lui-meme.</p>
 */
public record PreferencesCookies(boolean analytique, boolean marketing) {

    /**
     * Nom du cookie de preference. Sans prefixe {@code __Host-} : celui-ci imposerait
     * {@code Secure}, que le poste de developpement en HTTP simple ne fournit pas.
     */
    public static final String NOM_COOKIE = "preferences_cookies";

    /**
     * Version du <i>format</i> de la valeur, distincte de la version de la politique
     * cookies figee sur la preuve. Une valeur ecrite dans un format inconnu est
     * traitee comme une absence de choix : le bandeau reapparait et la question est
     * reposee, plutot que d interpreter au jugé une valeur qu on ne sait pas lire.
     */
    private static final String PREFIXE_FORMAT = "v1-";

    private static final int LONGUEUR_ATTENDUE = PREFIXE_FORMAT.length() + 2;
    private static final char OUI = '1';
    private static final char NON = '0';

    /** Refus de toutes les finalites optionnelles — l etat par defaut, avant tout choix. */
    public static PreferencesCookies refusTotal() {
        return new PreferencesCookies(false, false);
    }

    /** Acceptation de toutes les finalites optionnelles. */
    public static PreferencesCookies acceptationTotale() {
        return new PreferencesCookies(true, true);
    }

    /**
     * Valeur a deposer dans le cookie, par exemple {@code v1-10} pour « mesure
     * d audience acceptee, marketing refuse ».
     */
    public String versValeurCookie() {
        return PREFIXE_FORMAT + drapeau(analytique) + drapeau(marketing);
    }

    /**
     * Relit la valeur d un cookie de preference.
     *
     * <p>Renvoie un resultat vide des que la valeur ne correspond pas exactement au
     * format attendu — absente, tronquee, ecrite dans un format anterieur ou
     * bricolee a la main. Le vide signifie « choix inconnu », ce que l appelant
     * traduit par l affichage du bandeau : en cas de doute on redemande, on ne
     * suppose jamais un consentement.</p>
     */
    public static Optional<PreferencesCookies> depuisValeurCookie(String valeur) {
        if (valeur == null || valeur.length() != LONGUEUR_ATTENDUE
                || !valeur.startsWith(PREFIXE_FORMAT)) {
            return Optional.empty();
        }
        Optional<Boolean> analytiqueLu = drapeauLu(valeur.charAt(PREFIXE_FORMAT.length()));
        Optional<Boolean> marketingLu = drapeauLu(valeur.charAt(PREFIXE_FORMAT.length() + 1));
        if (analytiqueLu.isEmpty() || marketingLu.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PreferencesCookies(analytiqueLu.get(), marketingLu.get()));
    }

    private static char drapeau(boolean accorde) {
        return accorde ? OUI : NON;
    }

    private static Optional<Boolean> drapeauLu(char caractere) {
        return switch (caractere) {
            case OUI -> Optional.of(true);
            case NON -> Optional.of(false);
            default -> Optional.empty();
        };
    }
}
