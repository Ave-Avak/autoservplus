package be.autoservplus.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Turnstile n est arme que si une cle secrete <b>non vide</b> est fournie.
 *
 * <h2>Pourquoi une Condition et non {@code @ConditionalOnProperty}</h2>
 *
 * <p>{@code @ConditionalOnProperty(name = "...")} sans {@code havingValue} considere
 * la propriete presente des qu elle est <b>declaree</b>, meme vide, et ne la rejette
 * que si elle vaut litteralement {@code false}. Or {@code application.yml} la declare
 * avec une valeur par defaut vide — {@code ${TURNSTILE_CLE_SECRETE:}} — de sorte que
 * la condition passait sur <b>tout</b> deploiement.</p>
 *
 * <p><b>L effet, constate en test avant livraison</b> : le verificateur naissait avec
 * une cle vide, aucun jeton ne lui parvenait puisque le widget n etait pas rendu, et
 * il refusait donc chaque soumission — <b>silencieusement, sur la page du cas
 * nominal</b>. Inscription, renvoi de verification et mot de passe oublie etaient
 * morts sur tout deploiement sans Turnstile, c est-a-dire par defaut, sans qu aucun
 * message ne le signale.</p>
 *
 * <p>Meme raisonnement et meme forme que {@code ConditionPrestataire} pour Mollie :
 * <b>la cle decide, et une cle vide n est pas une cle</b>.</p>
 */
public final class ConditionTurnstile {

    /** Propriete unique dont depend le choix, referencee ici seulement. */
    static final String CLE = "autoservplus.securite.turnstile.cle-secrete";

    private ConditionTurnstile() {
    }

    private static boolean cleFournie(ConditionContext contexte) {
        String valeur = contexte.getEnvironment().getProperty(CLE);
        return valeur != null && !valeur.isBlank();
    }

    /** Une cle secrete est fournie : la verification Turnstile prend la main. */
    public static class TurnstileConfigure implements Condition {
        @Override
        public boolean matches(ConditionContext contexte, AnnotatedTypeMetadata metadonnees) {
            return cleFournie(contexte);
        }
    }
}
