package be.autoservplus.importcsv.service;

import be.autoservplus.importcsv.service.dto.RapportImport;

/**
 * Levee quand un import comporte au moins une ligne invalide (BL-2).
 *
 * <p><b>Elle porte le rapport complet</b>, et c est tout son interet : elle annule la
 * transaction — donc l import entier — tout en remontant a l ecran la liste de ce qui
 * doit etre corrige. Sans cela, il faudrait choisir entre annuler et informer.</p>
 */
public class ImportRefuseException extends RuntimeException {

    private final transient RapportImport rapport;

    public ImportRefuseException(RapportImport rapport) {
        super("Import refusé : " + rapport.erreurs().size() + " ligne(s) en erreur.");
        this.rapport = rapport;
    }

    public RapportImport rapport() {
        return rapport;
    }
}
