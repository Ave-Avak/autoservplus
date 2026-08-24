package be.autoservplus.vente.service;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composant actif uniquement lorsqu un identifiant de prestataire de paiement est
 * fourni. Contraire exact de {@link SiAucunPrestataireConfigure} : les deux
 * partitionnent les demarrages, il y a toujours une passerelle et jamais deux.
 *
 * <p>Annotation nommee plutot que {@code @Conditional} recopie : la condition
 * s enonce alors sur le composant, et le jour ou elle changera elle ne changera
 * qu ici.</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionPrestataire.PrestataireConfigure.class)
public @interface SiPrestataireConfigure {
}
