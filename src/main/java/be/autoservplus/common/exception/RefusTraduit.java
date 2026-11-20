package be.autoservplus.common.exception;

/**
 * Refus metier qui porte lui-meme la cle i18n de son message d ecran.
 *
 * <p><b>Le besoin auquel il repond.</b> {@link RegleMetierException} exige que
 * {@link #getMessage()} soit une <b>phrase affichable</b> : c est elle qui part aux
 * journaux et que lit tout appelant non web. Mais l ecran, lui, doit suivre la langue
 * de session (F6), donc afficher une cle traduite. Deux besoins, deux porteurs : la
 * phrase dans le message, la cle ici.</p>
 *
 * <p><b>Ce qu il remplace.</b> Le projet a un temps detourne le <i>code de regle</i> en
 * cle de traduction, ce qui a produit des pseudo-codes {@code RM-} qu aucune exigence
 * du cahier des charges ne definissait. Le Javadoc de {@link RegleMetierException}
 * l interdit explicitement. Un code de tracabilite et une cle de traduction se
 * ressemblent, ce sont deux chaines courtes qui servent a router, et c est exactement
 * pourquoi ils se confondaient. Les separer par le <b>type</b> plutot que par la
 * discipline est ce qui empeche la confusion de revenir.</p>
 *
 * <p>Corollaire pratique : le controleur traduit la cle que le refus porte, au lieu de
 * tenir une table de correspondance qu un refus ajoute plus tard obligerait a
 * completer, oubli qu aucun test ne signalerait, le repli etant silencieux.</p>
 */
public class RefusTraduit extends RegleMetierException {

    private final transient String cleMessage;

    private final transient Object[] arguments;

    /**
     * @param cleMessage cle i18n du texte a afficher
     * @param message    phrase francaise affichable sans retouche, pour les journaux
     *                   et les appelants non web
     */
    public RefusTraduit(String cleMessage, String message) {
        super(message);
        this.cleMessage = cleMessage;
        this.arguments = null;
    }

    /**
     * Refus qui porte AUSSI le code d une regle du cahier des charges : la cle sert
     * l ecran, le code la tracabilite, deux roles, deux champs, comme ci-dessus.
     */
    public RefusTraduit(String codeRegle, String cleMessage, String message) {
        this(codeRegle, cleMessage, null, message);
    }

    /**
     * Refus dont le message d'écran attend des valeurs, un délai ou une limite. Le
     * tableau est explicite, et non variable : avec trois chaînes, un constructeur à
     * arguments variables se confondrait avec celui qui porte un code de règle.
     *
     * @param codeRegle code d'une règle du cahier des charges, ou {@code null}
     * @param arguments valeurs du message, dans l'ordre de ses {@code {0}}, {@code {1}}
     */
    public RefusTraduit(String codeRegle, String cleMessage, Object[] arguments, String message) {
        super(codeRegle, message);
        this.cleMessage = cleMessage;
        this.arguments = arguments == null ? null : arguments.clone();
    }

    public String getCleMessage() {
        return cleMessage;
    }

    /** Valeurs du message, ou {@code null} quand il n'en attend aucune. */
    public Object[] getArguments() {
        return arguments == null ? null : arguments.clone();
    }
}
