package be.autoservplus.communication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fait partir hors du fil de la requête les courriels dont la durée d'envoi trahirait
 * l'existence d'un compte : ceux des formulaires anonymes (inscription, renvoi de
 * vérification, mot de passe oublié) et l'avis de verrouillage d'un mot de passe faux.
 *
 * <p>Une adresse inconnue ne déclenche aucun envoi : quand l'envoi se faisait dans la
 * requête, sa durée, celle d'un échange SMTP, disait à un tiers que l'adresse saisie a un
 * compte. Une attente minimale fixe sur ces formulaires a été écartée : elle bloquerait
 * un fil par requête et ne couvrirait pas un relais plus lent qu'elle.</p>
 *
 * <p><b>File bornée, abandon journalisé.</b> Pleine, la file refuse la tâche, et le
 * courriel est abandonné avec un avertissement. Exécuter la tâche dans le fil appelant
 * (CallerRunsPolicy) recréerait sous charge exactement l'écart de durée que cet
 * exécuteur supprime.</p>
 *
 * <p>Le MDC de la requête, dont son identifiant, est recopié dans le fil de la tâche et
 * vidé à la fin de celle-ci. À l'arrêt, les tâches en cours et en file sont attendues
 * avant que le transport ne soit détruit.</p>
 *
 * <p>Volontairement pas exposé comme bean {@code Executor} : Spring Boot retirerait alors
 * son exécuteur d'application, et tout autre emploi asynchrone viendrait ici.</p>
 */
@Component
public class ExecuteurCourriels implements DisposableBean {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ExecuteurCourriels.class);
    private static final int ATTENTE_ARRET_SECONDES = 30;

    private final TransportCourriel transport;
    private final ThreadPoolTaskExecutor executeur;

    public ExecuteurCourriels(
            TransportCourriel transport,
            @Value("${autoservplus.courriel.executeur.fils:2}") int fils,
            @Value("${autoservplus.courriel.executeur.file:200}") int capaciteFile) {
        this.transport = transport;
        this.executeur = new ThreadPoolTaskExecutor();
        executeur.setCorePoolSize(fils);
        executeur.setMaxPoolSize(fils);
        executeur.setQueueCapacity(capaciteFile);
        executeur.setThreadNamePrefix("courriel-");
        executeur.setTaskDecorator(new RecopieMdc());
        executeur.setWaitForTasksToCompleteOnShutdown(true);
        executeur.setAwaitTerminationSeconds(ATTENTE_ARRET_SECONDES);
        executeur.initialize();
    }

    /** Confie le courriel à un fil de l'exécuteur, ou l'abandonne si la file est pleine. */
    public void soumettre(Courriel courriel) {
        try {
            executeur.execute(() -> transmettre(courriel));
        } catch (TaskRejectedException filePleine) {
            // Ni l'adresse ni le contenu : le type suffit à l'exploitant.
            JOURNAL.warn("Courriel {} abandonne : file de l executeur pleine.", courriel.type());
        }
    }

    private void transmettre(Courriel courriel) {
        try {
            transport.transmettre(courriel);
        } catch (RuntimeException panne) {
            JOURNAL.warn("Courriel {} non remis par l executeur : {}", courriel.type(),
                    panne.getClass().getSimpleName());
        }
    }

    /** Attend les tâches en cours et en file, dans la limite de trente secondes. */
    @Override
    public void destroy() {
        executeur.shutdown();
    }

    /** Recopie le MDC de l'appelant dans le fil de la tâche, et le vide après elle. */
    static final class RecopieMdc implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable tache) {
            Map<String, String> contexte = MDC.getCopyOfContextMap();
            return () -> {
                if (contexte != null) {
                    MDC.setContextMap(contexte);
                }
                try {
                    tache.run();
                } finally {
                    MDC.clear();
                }
            };
        }
    }
}
