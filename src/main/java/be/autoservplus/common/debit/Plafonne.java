package be.autoservplus.common.debit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Plafonne une route par la surface nommée, dont les limites se lisent sous
 * {@code autoservplus.plafonds.surfaces} dans la configuration.
 *
 * <p>Une annotation sur la méthode plutôt qu'une liste d'adresses en configuration : la
 * limite se lit à côté de la route qu'elle protège, et une route renommée la garde.</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Plafonne {

    /** Nom de la surface, clé de {@code autoservplus.plafonds.surfaces}. */
    String value();
}
