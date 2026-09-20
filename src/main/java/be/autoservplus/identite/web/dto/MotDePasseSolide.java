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
 * Politique de mot de passe du projet, alignee sur le NIST SP 800-63B-4.
 *
 * <p><b>Longueur, pas composition.</b> Quinze caracteres au minimum — le mot de passe
 * est l unique facteur d authentification des membres — et cent au maximum, espaces et
 * caracteres Unicode acceptes. <b>Aucune regle de composition</b> : exiger une
 * majuscule, un chiffre et un caractere special produit des mots de passe previsibles
 * ({@code Motdepasse1!}) et pousse a les noter. C est un ecart assume au cahier des
 * charges, qui demandait dix caracteres et quatre classes.</p>
 *
 * <p><b>Liste de refus</b> plutot que regles : le mot de passe est confronte a la
 * liste embarquee des mots de passe frequents, puis au service de compromission. Un
 * refus donne <b>le meme message</b> quelle que soit son origine — indiquer laquelle
 * apprendrait a un attaquant si la chaine figure dans une fuite connue.</p>
 */
@Documented
@Constraint(validatedBy = ValidateurMotDePasseSolide.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface MotDePasseSolide {

    /** Longueur minimale. Quinze, le mot de passe etant l unique facteur. */
    int MINIMUM = 15;

    /**
     * Longueur maximale. Le NIST demande d accepter au moins 64 caracteres ; cent
     * laisse de la marge a une phrase de passe sans exposer au deni de service par
     * hachage d une chaine demesuree.
     */
    int MAXIMUM = 100;

    String message() default "{validation.motDePasse.politique}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
