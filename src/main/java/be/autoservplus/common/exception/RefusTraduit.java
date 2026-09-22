package be.autoservplus.common.exception;

/**
 * Refus metier qui porte lui-meme la cle i18n de son message d ecran.
 *
 * <p><b>Le besoin auquel il repond.</b> {@link RegleMetierException} exige que
 * {@link #getMessage()} soit une <b>phrase affichable</b> — c est elle qui part aux
 * journaux et que lit tout appelant non web. Mais l ecran, lui, doit suivre la langue
 * de session (F6), donc afficher une cle traduite. Deux besoins, deux porteurs : la
 * phrase dans le message, la cle ici.</p>
 *
 * <p><b>Ce qu il remplace.</b> Le projet a un temps detourne le <i>code de regle</i> en
 * cle de traduction, ce qui a produit des pseudo-codes {@code RM-} qu aucune exigence
 * du cahier des charges ne definissait — le Javadoc de {@link RegleMetierException}
 * l interdit explicitement. Un code de tracabilite et une cle de traduction se
 * ressemblent, ce sont deux chaines courtes qui servent a router, et c est exactement
 * pourquoi ils se confondaient. Les separer par le <b>type</b> plutot que par la
 * discipline est ce qui empeche la confusion de revenir.</p>
 *
 * <p>Corollaire pratique : le controleur traduit la cle que le refus porte, au lieu de
 * tenir une table de correspondance qu un refus ajoute plus tard obligerait a
 * completer — oubli qu aucun test ne signalerait, le repli etant silencieux.</p>
 */
public class RefusTraduit extends RegleMetierException {

    private final transient String cleMessage;

    /**
     * @param cleMessage cle i18n du texte a afficher
     * @param message    phrase francaise affichable sans retouche, pour les journaux
     *                   et les appelants non web
     */
    public RefusTraduit(String cleMessage, String message) {
        super(message);
        this.cleMessage = cleMessage;
    }

    public String getCleMessage() {
        return cleMessage;
    }
}
