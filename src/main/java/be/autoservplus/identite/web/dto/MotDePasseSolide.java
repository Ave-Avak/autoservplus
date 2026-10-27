package be.autoservplus.identite.web.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Mot de passe conforme a la politique du projet.
 *
 * <p>La regle elle-meme (quinze caracteres au minimum, 72 octets UTF-8 au maximum,
 * liste de refus, service de compromission) vit dans
 * {@link be.autoservplus.identite.service.PolitiqueMotDePasse}, que les services
 * appliquent aussi. Cette annotation ne fait que la brancher sur le formulaire, pour
 * afficher le refus a cote du champ.</p>
 */
@Documented
@Constraint(validatedBy = ValidateurMotDePasseSolide.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface MotDePasseSolide {

    String message() default "{validation.motDePasse.politique}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
