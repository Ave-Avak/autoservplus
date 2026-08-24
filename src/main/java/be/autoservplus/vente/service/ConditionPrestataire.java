package be.autoservplus.vente.service;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Decide quelle passerelle de paiement s active : la reelle si un identifiant de
 * prestataire est fourni, le bouchon sinon.
 *
 * <p><b>Ce qui decide est la CLE, plus le profil Spring.</b> Le projet choisissait
 * jusqu ici par {@code @Profile("prod")}, ce qui liait deux questions
 * independantes. Deployer en {@code prod} sans cle levait une
 * {@code UnsupportedOperationException} au clic sur « payer » — l exception au
 * milieu d un parcours d achat, soit le pire point de rupture possible ; et
 * demarrer en {@code demo} avec une cle valide l ignorait. La presence d un
 * identifiant est la seule question qui compte, et elle se pose telle quelle.</p>
 *
 * <p><b>Une {@link Condition} plutot qu un {@code @ConditionalOnExpression}.</b>
 * L expression SpEL aurait interpole la valeur de la propriete dans une chaine
 * evaluee : un identifiant contenant un caractere de syntaxe aurait produit une
 * erreur d analyse SpEL <b>citant l identifiant</b> dans la trace. Un secret ne
 * doit jamais pouvoir se retrouver dans un journal par accident, fut-ce celui d une
 * erreur de demarrage. Lire l environnement en Java ne court pas ce risque.</p>
 */
public final class ConditionPrestataire {

    /** Propriete unique dont depend le choix, referencee ici seulement. */
    static final String CLE = "autoservplus.paiement.mollie.cle-api";

    private ConditionPrestataire() {
    }

    private static boolean identifiantFourni(ConditionContext contexte) {
        String valeur = contexte.getEnvironment().getProperty(CLE);
        return valeur != null && !valeur.isBlank();
    }

    /** Un identifiant est fourni : la passerelle reelle prend la main. */
    public static class PrestataireConfigure implements Condition {
        @Override
        public boolean matches(ConditionContext contexte, AnnotatedTypeMetadata metadonnees) {
            return identifiantFourni(contexte);
        }
    }

    /** Aucun identifiant : repli sur le bouchon et sa page de paiement simulee. */
    public static class AucunPrestataireConfigure implements Condition {
        @Override
        public boolean matches(ConditionContext contexte, AnnotatedTypeMetadata metadonnees) {
            return !identifiantFourni(contexte);
        }
    }
}
