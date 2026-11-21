package be.autoservplus.common.exception;

/**
 * Transition refusée par la machine à états d'une entité, avec la clé i18n de son
 * message d'écran.
 *
 * <p>Pendant de {@link RefusTraduit} pour les invariants que l'entité défend elle-même.
 * Elle reste une {@link IllegalStateException} : un appelant qui attrape déjà ce type
 * n'a rien à changer, et seul l'écran qui affiche le refus lit la clé. Le message reste
 * la phrase française des journaux.</p>
 */
public class TransitionRefusee extends IllegalStateException {

    private final transient String cleMessage;

    public TransitionRefusee(String cleMessage, String message) {
        super(message);
        this.cleMessage = cleMessage;
    }

    public String getCleMessage() {
        return cleMessage;
    }
}
