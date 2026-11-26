package be.autoservplus.stockage.service;

/**
 * Levee quand un fichier depasse le plafond applicatif (fondation upload).
 *
 * <p>Ce plafond est le seul que l'administrateur voit : celui du conteneur
 * ({@code spring.servlet.multipart}) est porté à la taille de la requête entière, parce
 * qu'une requête qu'il coupe perd son jeton CSRF et reçoit un 403 sans rapport avec la
 * taille. Le conteneur reste la protection contre la saturation, ce contrôle-ci donne le
 * message et se rejoue par un test sans requête HTTP.</p>
 */
public class FichierTropVolumineuxException extends RuntimeException {

    public FichierTropVolumineuxException(long tailleMaximaleOctets) {
        super("Fichier trop volumineux. Taille maximale : "
                + (tailleMaximaleOctets / 1024 / 1024) + " Mo.");
    }
}
