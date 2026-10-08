package be.autoservplus.identite.mfa;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Chiffre et dechiffre les secrets TOTP en AES-256-GCM.
 *
 * <p><b>Chiffre, et non hache</b> : un code TOTP se recalcule a partir du secret, qu il
 * faut donc pouvoir relire. Une fuite de la base seule ne doit pas suffire a fabriquer
 * des codes — d ou une cle maitre hors base, tenue par l environnement.</p>
 *
 * <p><b>Donnee authentifiee : identifiant du compte et version de cle.</b> GCM authentifie
 * ces octets sans les chiffrer. Un chiffre recopie sur la ligne d un autre compte, ou
 * relu sous une autre version, echoue a la verification du tag au lieu de produire un
 * secret valide pour la mauvaise personne.</p>
 *
 * <p><b>Rotation.</b> La version active chiffre ; les versions precedentes, si elles sont
 * fournies, ne servent plus qu a dechiffrer. Le service rechiffre sous la version active
 * a chaque verification reussie : une rotation se resorbe a l usage, et la colonne
 * {@code mfa_cle_version} dit ce qu il reste a migrer.</p>
 */
public class ChiffrementSecretMfa {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAILLE_NONCE = 12;
    private static final int TAILLE_TAG_BITS = 128;

    private final Map<Short, SecretKey> cles;
    private final short versionActive;
    private final SecureRandom aleatoire = new SecureRandom();

    /**
     * @param cles          toutes les cles connues, active comprise, par version
     * @param versionActive celle qui chiffre ; doit figurer dans {@code cles}
     */
    public ChiffrementSecretMfa(Map<Short, SecretKey> cles, short versionActive) {
        this.cles = Map.copyOf(cles);
        this.versionActive = versionActive;
        if (!this.cles.containsKey(versionActive)) {
            throw new IllegalArgumentException("Cle active absente : version " + versionActive);
        }
    }

    public record SecretChiffre(byte[] donnees, short version) {
    }

    public short versionActive() {
        return versionActive;
    }

    public SecretChiffre chiffrer(byte[] secret, long idCompte) {
        byte[] nonce = new byte[TAILLE_NONCE];
        aleatoire.nextBytes(nonce);
        try {
            Cipher chiffreur = Cipher.getInstance(TRANSFORMATION);
            chiffreur.init(Cipher.ENCRYPT_MODE, cles.get(versionActive),
                    new GCMParameterSpec(TAILLE_TAG_BITS, nonce));
            chiffreur.updateAAD(donneeAuthentifiee(idCompte, versionActive));
            byte[] chiffre = chiffreur.doFinal(secret);
            return new SecretChiffre(ByteBuffer.allocate(nonce.length + chiffre.length)
                    .put(nonce).put(chiffre).array(), versionActive);
        } catch (GeneralSecurityException echec) {
            throw new IllegalStateException("Chiffrement du secret TOTP impossible", echec);
        }
    }

    /**
     * @throws SecretIndechiffrableException si la version est inconnue ou si le tag ne
     *         verifie pas — donnees alterees, mauvais compte, ou cle remplacee sans que
     *         l ancienne ait ete conservee
     */
    public byte[] dechiffrer(byte[] donnees, short version, long idCompte) {
        SecretKey cle = cles.get(version);
        if (cle == null) {
            throw new SecretIndechiffrableException(
                    "Aucune cle maitre connue pour la version " + version);
        }
        Objects.requireNonNull(donnees, "donnees");
        try {
            Cipher dechiffreur = Cipher.getInstance(TRANSFORMATION);
            dechiffreur.init(Cipher.DECRYPT_MODE, cle,
                    new GCMParameterSpec(TAILLE_TAG_BITS, donnees, 0, TAILLE_NONCE));
            dechiffreur.updateAAD(donneeAuthentifiee(idCompte, version));
            return dechiffreur.doFinal(Arrays.copyOfRange(donnees, TAILLE_NONCE, donnees.length));
        } catch (AEADBadTagException alteration) {
            throw new SecretIndechiffrableException("Tag GCM invalide", alteration);
        } catch (GeneralSecurityException echec) {
            throw new SecretIndechiffrableException("Dechiffrement impossible", echec);
        }
    }

    private static byte[] donneeAuthentifiee(long idCompte, short version) {
        return ("autoservplus-totp|" + idCompte + "|" + version).getBytes(StandardCharsets.US_ASCII);
    }

    /** Un secret qu aucune cle connue n ouvre : configuration ou donnee defaillante. */
    public static class SecretIndechiffrableException extends RuntimeException {
        public SecretIndechiffrableException(String message) {
            super(message);
        }

        public SecretIndechiffrableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
