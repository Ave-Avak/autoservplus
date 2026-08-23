package be.autoservplus.stockage.service;

/**
 * Levee quand un fichier depose n est pas d un type admis (fondation upload).
 *
 * <p>Le message ne reprend <b>jamais</b> le nom du fichier ni son en-tete
 * {@code Content-Type} : ces deux valeurs viennent du client et seraient reaffichees
 * telles quelles dans un message d erreur. Il annonce ce qui est accepte, ce qui est
 * la seule information utile a qui s est trompe.</p>
 */
public class TypeFichierRefuseException extends RuntimeException {

    public TypeFichierRefuseException(String typesAdmis) {
        super("Format de fichier non accepté. Formats admis : " + typesAdmis + ".");
    }
}
