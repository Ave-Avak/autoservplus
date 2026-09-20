package be.autoservplus.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Journalise un avertissement au demarrage lorsque la planification est desactivee.
 *
 * <p><b>Pourquoi une classe a part.</b> {@link PlanificationConfig} n existe que si la
 * planification est active : quand elle ne l est pas, aucun de ses beans n est cree,
 * donc elle ne peut rien dire. Le silence est precisement le probleme — une
 * planification eteinte par erreur en production ne se verrait qu au moment ou
 * quelqu un s etonnerait qu une commande impayee ne soit jamais annulee, c est-a-dire
 * trop tard. Les deux configurations portent donc la meme propriete avec des valeurs
 * opposees : l une des deux est toujours active.</p>
 *
 * <p>Le message <b>nomme la consequence</b> et pas seulement le reglage. « Planification
 * desactivee » n apprend rien a qui lit un journal sans connaitre le code ; ce qui
 * compte est que le job RM-21 ne tournera pas et qu une commande non payee restera en
 * attente indefiniment.</p>
 *
 * <p>En test d integration l avertissement apparait une fois par contexte, et c est
 * voulu : le socle d integration eteint la planification deliberement, et le journal
 * doit le refleter plutot que de laisser croire a une build ordinaire.</p>
 */
@Configuration
@ConditionalOnProperty(name = "autoservplus.planification.activee", havingValue = "false")
public class PlanificationDesactiveeAvertissement {

    private static final Logger JOURNAL =
            LoggerFactory.getLogger(PlanificationDesactiveeAvertissement.class);

    @PostConstruct
    void avertir() {
        JOURNAL.warn("Planification DESACTIVEE (autoservplus.planification.activee=false) :"
                + " le job d expiration des commandes impayees (RM-21) ne s executera pas."
                + " Une commande non payee restera EN_ATTENTE_PAIEMENT indefiniment, et son"
                + " stock ne sera jamais rendu. Attendu en test, anormal en production.");
    }
}
