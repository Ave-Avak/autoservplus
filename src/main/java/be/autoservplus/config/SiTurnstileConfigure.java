package be.autoservplus.config;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Pose sur ce qui n a de sens qu avec une cle Turnstile non vide. Voir
 * {@link ConditionTurnstile} pour ce que cette annotation evite.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionTurnstile.TurnstileConfigure.class)
public @interface SiTurnstileConfigure {
}
