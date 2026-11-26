package be.autoservplus.vente.service;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composant du repli : actif tant qu'aucun identifiant de prestataire n'est fourni.
 * Contraire exact de {@link SiPrestataireConfigure}.
 *
 * <p>Le repli sert à {@code dev} et {@code demo}, où la plateforme reste parcourable de
 * bout en bout sans clé. Sous {@code prod}, {@code ControlePaiementProduction} refuse
 * le démarrage sans identifiant réel : une facture émise sur un paiement simulé serait
 * une vente comptabilisée sans encaissement. Le bouchon s'annonce au démarrage, et la
 * page de paiement simulée le dit au membre.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionPrestataire.AucunPrestataireConfigure.class)
public @interface SiAucunPrestataireConfigure {
}
