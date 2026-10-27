package be.autoservplus.config;

import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Cle maitre des secrets TOTP.
 *
 * <p><b>Demarrage refuse sans cle, sous tout profil.</b> Une application qui demarrerait
 * sans cle devrait soit refuser tout enrolement (et les administrateurs, pour qui le
 * second facteur est obligatoire, ne pourraient plus se connecter), soit en inventer une
 * a chaque demarrage, et chaque redemarrage rendrait indechiffrables tous les secrets deja
 * enroles. Les deux echouent au pire moment. Un refus au demarrage echoue au bon, devant
 * l exploitant.</p>
 *
 * <p><b>Aucune exception de profil, developpement compris.</b> Une cle tiree au hasard
 * sous {@code dev} se perdait a chaque redemarrage (devtools compris), et un poste qui
 * tourne sous une regle differente de la production ne revele pas les defauts de
 * celle-ci. Les tests recoivent une cle fixe par leur propre configuration
 * ({@code src/test/resources/config/application.properties}) : le code de production ne
 * contient aucune cle, et n en invente aucune.</p>
 */
@Configuration
public class MfaConfig {

    private static final int OCTETS_AES_256 = 32;

    @Bean
    public ChiffrementSecretMfa chiffrementSecretMfa(
            @Value("${autoservplus.mfa.cle-maitre:}") String cleMaitre,
            @Value("${autoservplus.mfa.cle-version:1}") short version,
            @Value("${autoservplus.mfa.cles-precedentes:}") String clesPrecedentes) {
        return construire(cleMaitre, version, clesPrecedentes);
    }

    static ChiffrementSecretMfa construire(String cleMaitre, short version, String clesPrecedentes) {
        if (cleMaitre == null || cleMaitre.isBlank()) {
            throw new IllegalStateException("MFA_CLE_MAITRE absente : le chiffrement des secrets "
                    + "TOTP l exige, en local comme en production. Generez-la une fois par "
                    + "« openssl rand -base64 32 » et conservez-la.");
        }
        Map<Short, SecretKey> cles = new HashMap<>(lirePrecedentes(clesPrecedentes));
        cles.put(version, cle(cleMaitre, "MFA_CLE_MAITRE"));
        return new ChiffrementSecretMfa(cles, version);
    }

    /** Format {@code version:base64}, separe par des virgules. */
    private static Map<Short, SecretKey> lirePrecedentes(String texte) {
        Map<Short, SecretKey> cles = new HashMap<>();
        if (texte == null || texte.isBlank()) {
            return cles;
        }
        for (String entree : texte.split(",")) {
            String[] parties = entree.trim().split(":", 2);
            if (parties.length != 2) {
                throw new IllegalStateException(
                        "MFA_CLES_PRECEDENTES : entree attendue sous la forme version:base64.");
            }
            cles.put(Short.parseShort(parties[0].trim()),
                    cle(parties[1].trim(), "MFA_CLES_PRECEDENTES"));
        }
        return cles;
    }

    private static SecretKey cle(String base64, String variable) {
        byte[] octets;
        try {
            octets = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException illisible) {
            throw new IllegalStateException(variable + " n est pas en base 64.", illisible);
        }
        if (octets.length != OCTETS_AES_256) {
            // Une cle plus courte ferait silencieusement de l AES-128 ou echouerait au
            // premier chiffrement : le defaut se signale ici, pas au premier enrolement.
            throw new IllegalStateException(variable + " doit faire 32 octets (AES-256), "
                    + "et non " + octets.length + ".");
        }
        return new SecretKeySpec(octets, "AES");
    }
}
