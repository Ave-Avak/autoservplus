package be.autoservplus.identite.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Mot de passe a usage unique fonde sur le temps (TOTP, RFC 6238) : HMAC-SHA1, pas de
 * 30 secondes, six chiffres, les parametres que toutes les applications
 * d authentification acceptent sans configuration.
 *
 * <p><b>Implementation maison plutot qu une bibliotheque.</b> Les candidates
 * ({@code dev.samstevens.totp}, {@code googleauth}) ne sont plus publiees depuis 2020,
 * et aucune ne gere ce qui demande reellement du soin ici : la persistance du dernier pas
 * accepte, donc l anti-rejeu. Le calcul lui-meme tient en quelques lignes de
 * {@code javax.crypto} et se verifie contre les vecteurs de l annexe B de la RFC :
 * c est ce qui rend ce choix defendable, pas sa brievete.</p>
 */
public final class Totp {

    public static final int PERIODE_SECONDES = 30;
    public static final int CHIFFRES = 6;

    /**
     * Un pas de part et d autre : la derive d horloge d un telephone et le temps de
     * saisie ne doivent pas faire echouer un code juste. Au-dela, la fenetre ouverte a
     * qui devine croit sans servir personne.
     */
    public static final int TOLERANCE_PAS = 1;

    private static final String ALGORITHME = "HmacSHA1";

    private Totp() {
    }

    public static long pasDe(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), PERIODE_SECONDES);
    }

    public static String code(byte[] secret, long pas) {
        return code(secret, pas, CHIFFRES);
    }

    /** Huit chiffres ne servent qu aux vecteurs de la RFC, qui sont donnes ainsi. */
    static String code(byte[] secret, long pas, int chiffres) {
        byte[] empreinte = hmac(secret, ByteBuffer.allocate(Long.BYTES).putLong(pas).array());
        // Troncature dynamique (RFC 4226 §5.3) : le dernier quartet choisit ou lire.
        int decalage = empreinte[empreinte.length - 1] & 0x0f;
        int binaire = ((empreinte[decalage] & 0x7f) << 24)
                | ((empreinte[decalage + 1] & 0xff) << 16)
                | ((empreinte[decalage + 2] & 0xff) << 8)
                | (empreinte[decalage + 3] & 0xff);
        int modulo = (int) Math.pow(10, chiffres);
        return String.format("%0" + chiffres + "d", binaire % modulo);
    }

    /**
     * Cherche le pas auquel correspond la saisie, dans la tolerance, en refusant tout pas
     * deja consomme.
     *
     * <p>Les trois candidats sont toujours calcules et compares en temps constant : une
     * sortie anticipee laisserait le temps de reponse dire lequel a correspondu.</p>
     *
     * @param dernierPasAccepte {@code null} si aucun code n a encore ete accepte
     * @return le pas accepte, a memoriser par l appelant. Vide si la saisie est refusee
     */
    public static OptionalLong verifier(byte[] secret, String saisie, Instant maintenant,
                                        Long dernierPasAccepte) {
        String normalisee = normaliser(saisie);
        if (normalisee.length() != CHIFFRES || !normalisee.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        byte[] attendu = normalisee.getBytes(StandardCharsets.US_ASCII);
        long courant = pasDe(maintenant);
        long retenu = Long.MIN_VALUE;
        for (long pas = courant - TOLERANCE_PAS; pas <= courant + TOLERANCE_PAS; pas++) {
            boolean correspond = MessageDigest.isEqual(attendu,
                    code(secret, pas).getBytes(StandardCharsets.US_ASCII));
            boolean neuf = dernierPasAccepte == null || pas > dernierPasAccepte;
            if (correspond && neuf && retenu == Long.MIN_VALUE) {
                retenu = pas;
            }
        }
        return retenu == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(retenu);
    }

    /** Les applications affichent souvent « 123 456 » : l espace n est pas une faute. */
    static String normaliser(String saisie) {
        return saisie == null ? "" : saisie.replaceAll("\\s", "");
    }

    private static byte[] hmac(byte[] cle, byte[] message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHME);
            mac.init(new SecretKeySpec(cle, ALGORITHME));
            return mac.doFinal(message);
        } catch (GeneralSecurityException impossible) {
            // HmacSHA1 est exige de toute implementation Java.
            throw new IllegalStateException("HmacSHA1 indisponible", impossible);
        }
    }
}
