package be.autoservplus.common.validation;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Numéro de compte bancaire international (ISO 13616).
 *
 * <p>Deux contrôles pour un compte belge, un seul pour les autres. La clé ISO, commune
 * à tous les pays : les quatre premiers caractères passent en fin de chaîne, chaque
 * lettre est remplacée par son rang plus neuf (A vaut 10), et le nombre obtenu doit
 * laisser un reste de 1 modulo 97. La clé nationale belge en plus : seize caractères,
 * et les deux derniers chiffres du compte égaux au reste des dix premiers modulo 97,
 * ou 97 si ce reste est nul.</p>
 *
 * <p>La clé nationale n'est pas redondante. La clé ISO est calculée sur un compte déjà
 * formé : un IBAN construit autour d'un numéro de compte belge faux passe la clé ISO et
 * reste inutilisable. Seule la clé nationale le révèle.</p>
 */
public final class Iban {

    private static final Pattern FORME = Pattern.compile("[A-Z]{2}\\d{2}[A-Z0-9]{11,30}");
    private static final Pattern BELGE = Pattern.compile("BE\\d{14}");
    private static final BigInteger QUATRE_VINGT_DIX_SEPT = BigInteger.valueOf(97);

    private Iban() {
    }

    /** Retire les espaces et passe en majuscules. Rend {@code null} pour {@code null}. */
    public static String normaliser(String iban) {
        return iban == null ? null : iban.replaceAll("\\s", "").toUpperCase();
    }

    public static boolean estValide(String iban) {
        String compact = normaliser(iban);
        if (compact == null || !FORME.matcher(compact).matches() || !cleIsoJuste(compact)) {
            return false;
        }
        return !compact.startsWith("BE") || cleBelgeJuste(compact);
    }

    private static boolean cleIsoJuste(String compact) {
        String deplace = compact.substring(4) + compact.substring(0, 4);
        StringBuilder chiffres = new StringBuilder();
        for (char c : deplace.toCharArray()) {
            chiffres.append(Character.getNumericValue(c));
        }
        return new BigInteger(chiffres.toString()).mod(QUATRE_VINGT_DIX_SEPT).intValue() == 1;
    }

    private static boolean cleBelgeJuste(String compact) {
        if (!BELGE.matcher(compact).matches()) {
            return false;
        }
        String compte = compact.substring(4);
        long reste = Long.parseLong(compte.substring(0, 10)) % 97;
        int cle = Integer.parseInt(compte.substring(10));
        return cle == (reste == 0 ? 97 : reste);
    }
}
