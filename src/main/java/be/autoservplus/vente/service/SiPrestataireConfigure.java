package be.autoservplus.vente.service;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composant actif uniquement lorsqu'un identifiant de prestataire de paiement est
 * fourni. Contraire exact de {@link SiAucunPrestataireConfigure} : les deux
 * partitionnent les démarrages, il y a toujours une passerelle et jamais deux.
 *
 * <p>Annotation nommée plutôt que {@code @Conditional} recopié : la condition
 * s'énonce alors sur le composant, et le jour où elle changera elle ne changera
 * qu'ici.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionPrestataire.PrestataireConfigure.class)
public @interface SiPrestataireConfigure {
}
