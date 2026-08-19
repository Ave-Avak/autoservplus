package be.autoservplus.common.exception;

/**
 * Levee lorsqu une regle metier du cahier des charges est violee.
 *
 * <p>Le code de la regle est conserve afin de pouvoir tracer l exigence dans les
 * journaux et dans les tests.</p>
 */
public class RegleMetierException extends RuntimeException {

    private final String codeRegle;

    public RegleMetierException(String codeRegle, String message) {
        super("[%s] %s".formatted(codeRegle, message));
        this.codeRegle = codeRegle;
    }

    public String getCodeRegle() {
        return codeRegle;
    }
}