package be.autoservplus.identite.service;

import org.hibernate.exception.ConstraintViolationException;

import java.util.Locale;
import java.util.Set;

/**
 * Forme enregistrée d'une adresse de courriel : sans espaces autour, en minuscules.
 *
 * <p>V55 l'impose en base ({@code ck_utilisateur_email_minuscules} et
 * {@code ck_utilisateur_email_en_attente_minuscules}). Toute adresse saisie passe par
 * {@link #normaliser} avant d'être cherchée ou écrite : connexion, inscription, mot de
 * passe oublié et renvoi, changement d'adresse, création d'un administrateur et du compte
 * exploitant. {@code Locale.ROOT} et non la langue du serveur : en turc, « I » deviendrait
 * « ı » sans point, et la même adresse aurait deux formes selon la machine.</p>
 *
 * <p>Java et PostgreSQL peuvent ne pas mettre une lettre rare en minuscules de la même
 * façon (le « İ » turc pointé, par exemple). La contrainte refuse alors l'écriture : les
 * services traduisent ce refus en erreur sur le champ ({@link #violeLaCasse}), jamais en
 * erreur technique.</p>
 */
public final class AdresseCourriel {

    /** Clé du message affiché sous le champ quand la base refuse la forme en minuscules. */
    public static final String CLE_REFUS_CASSE = "validation.email.casse";

    private static final Set<String> CONTRAINTES = Set.of("ck_utilisateur_email_minuscules",
            "ck_utilisateur_email_en_attente_minuscules");

    private AdresseCourriel() {
    }

    public static String normaliser(String saisie) {
        return saisie == null ? "" : saisie.strip().toLowerCase(Locale.ROOT);
    }

    /** Vrai si l'erreur vient de l'une des deux contraintes de casse de V55. */
    public static boolean violeLaCasse(Throwable erreur) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException contrainte
                    && contrainte.getConstraintName() != null
                    && CONTRAINTES.contains(contrainte.getConstraintName().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
