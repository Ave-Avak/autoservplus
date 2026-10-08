package be.autoservplus.identite.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Jeton du lien de definition du mot de passe : 256 bits tires au hasard, dont seule
 * l empreinte SHA-256 est stockee.
 *
 * <p>Lire la base ne suffit donc plus a suivre le lien. SHA-256 et non BCrypt : un jeton
 * de 256 bits ne se devine pas, le hachage lent n y ajoute rien, et l empreinte
 * deterministe permet la recherche par index.</p>
 *
 * @param clair     ce qui part dans le lien, et nulle part ailleurs
 * @param empreinte ce qui est stocke
 */
public record JetonDefinition(String clair, String empreinte) {

    private static final SecureRandom ALEATOIRE = new SecureRandom();
    private static final int OCTETS = 32;

    public static JetonDefinition tirer() {
        byte[] octets = new byte[OCTETS];
        ALEATOIRE.nextBytes(octets);
        String clair = HexFormat.of().formatHex(octets);
        return new JetonDefinition(clair, empreinte(clair));
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
