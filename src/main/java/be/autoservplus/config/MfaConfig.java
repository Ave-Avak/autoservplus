package be.autoservplus.config;

import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Cle maitre des secrets TOTP.
 *
 * <p><b>Demarrage refuse sans cle, hors developpement et tests.</b> Une application qui
 * demarrerait sans cle devrait soit refuser tout enrolement — et les administrateurs, pour
 * qui le second facteur est obligatoire, ne pourraient plus se connecter —, soit en
 * inventer une a chaque demarrage, et chaque redemarrage rendrait indechiffrables tous les
 * secrets deja enroles. Les deux echouent au pire moment ; un refus au demarrage echoue
 * au bon, devant l exploitant.</p>
 *
 * <p><b>Pourquoi une liste blanche de profils et non {@code @Profile("prod")}.</b> Le
 * profil par defaut est {@code dev} ; un deploiement qui oublierait de fixer
 * {@code SPRING_PROFILES_ACTIVE} tomberait sous lui, mais un deploiement qui en fixerait
 * un autre ({@code demo}, {@code recette}…) doit exiger la cle. C est l absence de cle qui
 * est l exception, et elle n est toleree que la ou aucun secret reel n existe.</p>
 *
 * <p>Sous {@code dev} et {@code test}, une cle aleatoire est tiree au demarrage. Les
 * secrets enroles ne survivent donc pas a un redemarrage du poste de developpement ; le
 * secret de demonstration, lui, est reseme a chaque demarrage par
 * {@code SecondFacteurDemonstration}.</p>
 */
@Configuration
public class MfaConfig {

    private static final Logger JOURNAL = LoggerFactory.getLogger(MfaConfig.class);
    private static final int OCTETS_AES_256 = 32;

    @Bean
    public ChiffrementSecretMfa chiffrementSecretMfa(
            Environment environnement,
            @Value("${autoservplus.mfa.cle-maitre:}") String cleMaitre,
            @Value("${autoservplus.mfa.cle-version:1}") short version,
            @Value("${autoservplus.mfa.cles-precedentes:}") String clesPrecedentes) {
        return construire(environnement, cleMaitre, version, clesPrecedentes);
    }

    static ChiffrementSecretMfa construire(Environment environnement, String cleMaitre,
                                           short version, String clesPrecedentes) {
        Map<Short, SecretKey> cles = new HashMap<>(lirePrecedentes(clesPrecedentes));
        if (cleMaitre == null || cleMaitre.isBlank()) {
            if (!environnement.acceptsProfiles(Profiles.of("dev", "test"))) {
                throw new IllegalStateException("MFA_CLE_MAITRE absente : le chiffrement des "
                        + "secrets TOTP l exige hors des profils dev et test. Generez-la par "
                        + "« openssl rand -base64 32 ».");
            }
            byte[] ephemere = new byte[OCTETS_AES_256];
            new SecureRandom().nextBytes(ephemere);
            JOURNAL.warn("Aucune MFA_CLE_MAITRE : cle TOTP ephemere tiree pour ce demarrage. "
                    + "Les secrets enroles ne survivront pas au redemarrage.");
            cles.put(version, new SecretKeySpec(ephemere, "AES"));
        } else {
            cles.put(version, cle(cleMaitre, "MFA_CLE_MAITRE"));
        }
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
