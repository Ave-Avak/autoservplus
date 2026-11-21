package be.autoservplus.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque la méthode de contrôleur d'une page ouverte à tous qui porte un formulaire.
 *
 * <p>Le jeton CSRF d'un formulaire n'est calculé qu'à sa première lecture, et cette
 * lecture crée la session d'un visiteur qui n'en a pas. Le conteneur refuse de créer une
 * session une fois la réponse partie : si le jeton est lu pendant le rendu, après les
 * premiers octets envoyés, la page sort tronquée, en 200, sans fin de document. Le
 * visiteur concerné est le plus courant, celui qui revient après l'expiration de sa
 * session. Sur une méthode ainsi marquée, {@code BandeauCookiesAdvice} lit le jeton avant
 * le rendu, tant que la session peut encore naître.</p>
 *
 * <p>L'annotation ne se pose que sur une page qui a un formulaire pour un visiteur
 * anonyme : la poser partout donnerait une session à chaque visiteur de chaque page, ce
 * que les pages de simple lecture évitent. Une page réservée aux membres n'en a pas
 * besoin, sa session existe déjà.</p>
 *
 * <p>Elle n'est pas facultative. {@code PremiereVisiteSansSessionIT} ouvre chaque écran
 * public sur un vrai serveur, avec une réponse qui part dès son premier octet : une page
 * publique à formulaire sans cette annotation y échoue, quelle que soit sa longueur.</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FormulairePublic {
}
