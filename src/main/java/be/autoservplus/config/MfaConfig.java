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
 * Clé maître des secrets TOTP.
 *
 * <p><b>Démarrage refusé sans clé, sous tout profil.</b> Une application qui démarrerait
 * sans clé devrait soit refuser tout enrôlement (et les administrateurs, pour qui le
 * second facteur est obligatoire, ne pourraient plus se connecter), soit en inventer une
 * à chaque démarrage, et chaque redémarrage rendrait indéchiffrables tous les secrets déjà
 * enrôlés. Les deux échouent au pire moment. Un refus au démarrage échoue au bon, devant
 * l'exploitant.</p>
 *
 * <p><b>Aucune exception de profil, développement compris.</b> Une clé tirée au hasard
 * sous {@code dev} se perdait à chaque redémarrage (devtools compris), et un poste qui
 * tourne sous une règle différente de la production ne révèle pas les défauts de
 * celle-ci. Les tests reçoivent une clé fixe par leur propre configuration
 * ({@code src/test/resources/config/application.properties}) : le code de production ne
 * contient aucune clé, et n'en invente aucune.</p>
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

    /** Format {@code version:base64}, séparé par des virgules. */
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
            // Une clé plus courte ferait silencieusement de l'AES-128 ou échouerait au
            // premier chiffrement : le défaut se signale ici, pas au premier enrôlement.
            throw new IllegalStateException(variable + " doit faire 32 octets (AES-256), "
                    + "et non " + octets.length + ".");
        }
        return new SecretKeySpec(octets, "AES");
    }
}
