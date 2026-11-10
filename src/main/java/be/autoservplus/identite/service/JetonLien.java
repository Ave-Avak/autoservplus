package be.autoservplus.identite.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Jeton d'un lien envoyé par courriel : 256 bits tirés au hasard, dont seule l'empreinte
 * SHA-256 est stockée. Il sert aux quatre liens du compte : vérification d'adresse,
 * réinitialisation et définition du mot de passe, changement d'adresse.
 *
 * <p>Lire la base ne suffit donc pas à suivre un lien. SHA-256 et non BCrypt : un jeton
 * de 256 bits ne se devine pas, le hachage lent n'y ajoute rien, et l'empreinte
 * déterministe permet la recherche par index. V46 convertit les jetons déjà en base par
 * la même fonction, SHA-256 du texte en UTF-8 rendu en hexadécimal minuscule.</p>
 *
 * @param clair     ce qui part dans le lien, et nulle part ailleurs
 * @param empreinte ce qui est stocke
 */
public record JetonLien(String clair, String empreinte) {

    private static final SecureRandom ALEATOIRE = new SecureRandom();
    private static final int OCTETS = 32;

    public static JetonLien tirer() {
        byte[] octets = new byte[OCTETS];
        ALEATOIRE.nextBytes(octets);
        String clair = HexFormat.of().formatHex(octets);
        return new JetonLien(clair, empreinte(clair));
    }

    public static String empreinte(String clair) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((clair == null ? "" : clair).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
