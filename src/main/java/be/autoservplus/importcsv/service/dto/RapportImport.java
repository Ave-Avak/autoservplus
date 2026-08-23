package be.autoservplus.importcsv.service.dto;

import java.util.List;

/**
 * Bilan d un import de catalogue (BL-2).
 *
 * <p><b>Les trois compteurs sont rendus separement</b>, et non fondus en un « n lignes
 * traitees » : le garage doit distinguer ce qui a ete cree de ce qui a ete mis a jour
 * — un import qui ne cree rien alors qu on l attendait signale une colonne de code mal
 * remplie, ce qu un total unique masquerait.</p>
 *
 * @param erreurs une ligne par probleme, numero de ligne du fichier compris
 */
public record RapportImport(int crees, int misAJour, List<LigneEnErreur> erreurs) {

    public int total() {
        return crees + misAJour + erreurs.size();
    }

    public boolean sansErreur() {
        return erreurs.isEmpty();
    }

    /**
     * @param ligne numero de la ligne dans le FICHIER, en-tete comprise, pour que le
     *              garage la retrouve dans son tableur sans avoir a recompter
     */
    public record LigneEnErreur(int ligne, String motif) {
    }
}
