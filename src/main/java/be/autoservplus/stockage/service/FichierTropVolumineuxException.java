package be.autoservplus.stockage.service;

/**
 * Levee quand un fichier depasse le plafond applicatif (fondation upload).
 *
 * <p>Doublon <b>voulu</b> du plafond de Spring ({@code spring.servlet.multipart}) : le
 * conteneur coupe la requete avant le controleur et rend une erreur technique, alors
 * que ce controle-ci se produit dans le service et peut etre rejoue par un test sans
 * requete HTTP. Les deux plafonds sont alignes, celui de Spring restant la vraie
 * protection contre la saturation.</p>
 */
public class FichierTropVolumineuxException extends RuntimeException {

    public FichierTropVolumineuxException(long tailleMaximaleOctets) {
        super("Fichier trop volumineux. Taille maximale : "
                + (tailleMaximaleOctets / 1024 / 1024) + " Mo.");
    }
}
