package be.autoservplus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active l execution des taches planifiees ({@code @Scheduled}). Premier
 * consommateur : le job d expiration des commandes non payees (RM-21).
 * Tout job planifie du projet lit le temps via l horloge injectee
 * ({@code java.time.Clock}), jamais {@code Instant.now()} — la planification
 * declenche, l horloge date.
 */
@Configuration
@EnableScheduling
public class PlanificationConfig {
}
