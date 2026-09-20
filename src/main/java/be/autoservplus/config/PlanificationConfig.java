package be.autoservplus.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active l execution des taches planifiees ({@code @Scheduled}). Premier
 * consommateur : le job d expiration des commandes non payees (RM-21).
 * Tout job planifie du projet lit le temps via l horloge injectee
 * ({@code java.time.Clock}), jamais {@code Instant.now()} — la planification
 * declenche, l horloge date.
 *
 * <p><b>Desactivable</b> par {@code autoservplus.planification.activee=false}, et
 * active partout ailleurs ({@code matchIfMissing}) : aucun deploiement n a donc a
 * connaitre ce drapeau. Il existe pour les tests d integration, ou l ordonnanceur ne
 * rend aucun service et fait du mal. Chaque contexte de test garde son ordonnanceur
 * vivant jusqu a la fin de la JVM, longtemps apres la classe qui l a fait naitre :
 * passe la premiere minute, des dizaines d entre eux reveillent le job sur des bases
 * qui n ont plus de raison d etre interrogees, et retardent l arret de la JVM.</p>
 *
 * <p>Rien n est perdu cote couverture : {@code ExpirationCommandesJobTest} est un test
 * Mockito pur qui appelle {@code annulerLesCommandesExpirees} directement, avec une
 * horloge figee — c est la methode qui est verifiee, jamais le declenchement. Aucun
 * test d integration ne cite ce job.</p>
 */
@Configuration
@ConditionalOnProperty(name = "autoservplus.planification.activee",
        havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class PlanificationConfig {
}
