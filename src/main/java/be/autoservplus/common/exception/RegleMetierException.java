package be.autoservplus.common.exception;

/**
 * Levee lorsqu une regle metier du cahier des charges est violee.
 *
 * <p><b>Le code de regle ne fait pas partie du message.</b> Il l a longtemps fait —
 * le constructeur composait {@code "[RM-01] L adresse est obligatoire."} — et ce
 * prefixe ressortait tel quel a l ecran, partout ou un controleur remonte
 * {@code getMessage()} en message flash. Le membre lisait « [RM-02] » sans que cela
 * lui apprenne rien : un code de tracabilite interne s adresse a l equipe, pas au
 * client du garage.</p>
 *
 * <p>Les deux besoins sont donc separes plutot qu opposes :</p>
 * <ul>
 *   <li>{@link #getMessage()} rend la <b>phrase destinee a l utilisateur</b>, seule,
 *       affichable sans retouche ;</li>
 *   <li>{@link #getCodeRegle()} rend le <b>code de tracabilite</b>, pour le routage
 *       applicatif et les tests — c est deja ainsi que {@code RdvController} choisit
 *       le champ de formulaire a annoter ;</li>
 *   <li>{@link #toString()} accole le code, de sorte que journaux et traces de pile
 *       le portent encore. La tracabilite n est pas perdue, elle est deplacee la ou
 *       elle sert.</li>
 * </ul>
 */
public class RegleMetierException extends RuntimeException {

    private final String codeRegle;

    /**
     * @param codeRegle code CdC (RM-01, RM-30…), conserve pour la tracabilite
     * @param message   phrase destinee a l utilisateur, <b>sans</b> code ni prefixe
     */
    public RegleMetierException(String codeRegle, String message) {
        super(message);
        this.codeRegle = codeRegle;
    }

    /**
     * Pour une regle metier sans code RM dans le CdC (ex. gardes temporelles
     * de marquage). Le message doit se suffire a lui-meme. Ne pas fabriquer un
     * pseudo-code : la tracabilite RM = exigence CdC ne doit pas etre polluee.
     */
    public RegleMetierException(String message) {
        super(message);
        this.codeRegle = null;
    }

    public String getCodeRegle() {
        return codeRegle;
    }

    /**
     * Porte le code dans les journaux et les traces de pile, jamais a l ecran :
     * aucun gabarit n affiche {@code toString()}, tous passent par
     * {@code getMessage()}.
     */
    @Override
    public String toString() {
        return codeRegle == null
                ? super.toString()
                : "%s [%s]: %s".formatted(getClass().getName(), codeRegle, getMessage());
    }
}
