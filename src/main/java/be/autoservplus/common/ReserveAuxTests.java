package be.autoservplus.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Surcharge gardée pour les tests seulement : elle applique un choix par défaut que le code
 * de l'application doit toujours poser lui-même. La garder évite de réécrire les tests qui
 * ne portent pas sur ce choix.
 *
 * <p>Aucun appel depuis {@code src/main} : {@code SurchargesReserveesAuxTestsTest} lit le
 * bytecode compilé et casse la build au premier appel trouvé. Rétention {@code CLASS} : le
 * test lit l'annotation dans le fichier {@code .class}, l'application n'en a pas besoin.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
public @interface ReserveAuxTests {
}
