package be.autoservplus.vente.service;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Décide quelle passerelle de paiement s'active : la réelle si un identifiant de
 * prestataire est fourni, le bouchon sinon.
 *
 * <p><b>Ce qui décide est la CLE, plus le profil Spring.</b> Le projet choisissait
 * jusqu'ici par {@code @Profile("prod")}, ce qui liait deux questions
 * indépendantes. Déployer en {@code prod} sans clé levait une
 * {@code UnsupportedOperationException} au clic sur « payer » : l'exception au
 * milieu d'un parcours d'achat, soit le pire point de rupture possible. Et
 * démarrer en {@code demo} avec une clé valide l'ignorait. La présence d'un
 * identifiant est la seule question qui compte, et elle se pose telle quelle.</p>
 *
 * <p><b>Une {@link Condition} plutôt qu'un {@code @ConditionalOnExpression}.</b>
 * L'expression SpEL aurait interpolé la valeur de la propriété dans une chaîne
 * évaluée : un identifiant contenant un caractère de syntaxe aurait produit une
 * erreur d'analyse SpEL <b>citant l'identifiant</b> dans la trace. Un secret ne
 * doit jamais pouvoir se retrouver dans un journal par accident, fût-ce celui d'une
 * erreur de démarrage. Lire l'environnement en Java ne court pas ce risque.</p>
 */
public final class ConditionPrestataire {

    /** Propriété unique dont dépend le choix, référencée ici seulement. */
    static final String CLE = "autoservplus.paiement.mollie.cle-api";

    private ConditionPrestataire() {
    }

    private static boolean identifiantFourni(ConditionContext contexte) {
        String valeur = contexte.getEnvironment().getProperty(CLE);
        return valeur != null && !valeur.isBlank();
    }

    /** Un identifiant est fourni : la passerelle réelle prend la main. */
    public static class PrestataireConfigure implements Condition {
        @Override
        public boolean matches(ConditionContext contexte, AnnotatedTypeMetadata metadonnees) {
            return identifiantFourni(contexte);
        }
    }

    /** Aucun identifiant : repli sur le bouchon et sa page de paiement simulée. */
    public static class AucunPrestataireConfigure implements Condition {
        @Override
        public boolean matches(ConditionContext contexte, AnnotatedTypeMetadata metadonnees) {
            return !identifiantFourni(contexte);
        }
    }
}
