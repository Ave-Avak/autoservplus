package be.autoservplus.vente.service;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composant du repli : actif tant qu aucun identifiant de prestataire n est fourni.
 * Contraire exact de {@link SiPrestataireConfigure}.
 *
 * <p>Le repli vaut <b>y compris en production</b>, et c est delibere : une
 * plateforme deployee sans cle doit rester parcourable de bout en bout plutot que
 * de rompre au paiement. Il n est pas silencieux pour autant — le bouchon
 * l annonce au demarrage, et la page de paiement simulee le dit au membre.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionPrestataire.AucunPrestataireConfigure.class)
public @interface SiAucunPrestataireConfigure {
}
