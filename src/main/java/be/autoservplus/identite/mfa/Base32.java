package be.autoservplus.identite.mfa;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/**
 * Base 32 de la RFC 4648, sans remplissage : le format que les applications
 * d authentification attendent dans l URI {@code otpauth://} et a la saisie manuelle.
 *
 * <p>Le JDK n en fournit pas ; {@code commons-codec} le ferait, mais une dependance pour
 * trente lignes verifiees par les vecteurs de la RFC n apporte rien.</p>
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
    }

    public static String encoder(byte[] octets) {
        StringBuilder resultat = new StringBuilder((octets.length * 8 + 4) / 5);
        int tampon = 0;
        int bits = 0;
        for (byte octet : octets) {
            tampon = (tampon << 8) | (octet & 0xff);
            bits += 8;
            while (bits >= 5) {
                resultat.append(ALPHABET.charAt((tampon >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            resultat.append(ALPHABET.charAt((tampon << (5 - bits)) & 0x1f));
        }
        return resultat.toString();
    }

    /** Insensible a la casse et aux espaces ; le remplissage eventuel est ignore. */
    public static byte[] decoder(String texte) {
        String propre = texte.replaceAll("[\\s=]", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream resultat = new ByteArrayOutputStream(propre.length() * 5 / 8);
        int tampon = 0;
        int bits = 0;
        for (char caractere : propre.toCharArray()) {
            int valeur = ALPHABET.indexOf(caractere);
            if (valeur < 0) {
                throw new IllegalArgumentException("Caractere hors base 32 : " + caractere);
            }
            tampon = (tampon << 5) | valeur;
            bits += 5;
            if (bits >= 8) {
                resultat.write((tampon >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return resultat.toByteArray();
    }
}
