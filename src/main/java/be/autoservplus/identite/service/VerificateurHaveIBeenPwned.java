package be.autoservplus.identite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Seconde couche de refus : interroge Have I Been Pwned par <b>k-anonymat</b>.
 *
 * <h2>Ce qui sort, et ce qui ne sort pas</h2>
 *
 * <p>Le mot de passe est hache en SHA-1 localement ; <b>seuls les cinq premiers
 * caracteres hexadecimaux de l empreinte</b> quittent le serveur. Le service repond
 * la liste des suffixes qu il connait pour ce prefixe — plusieurs centaines — et la
 * comparaison se fait ici. Le service ne peut donc pas savoir quel mot de passe a ete
 * verifie, et <b>aucune adresse ni aucun identifiant ne l accompagne</b> : la requete
 * ne porte que cinq caracteres.</p>
 *
 * <p>SHA-1 n est pas un choix de securite mais le format impose par l interface du
 * service. Il ne sert pas a proteger le mot de passe — c est BCrypt qui le fait — mais
 * a l indexer.</p>
 *
 * <h2>Panne et lenteur</h2>
 *
 * <p>Deux secondes au plus, et <b>toute erreur rend {@code false}</b> : un service
 * injoignable ne bloque pas une inscription. La liste embarquee a deja tranche, et
 * refuser un mot de passe correct parce qu un tiers est en panne serait un deni de
 * service inflige a l utilisateur. L echec est journalise — silencieux pour le
 * visiteur, pas pour l exploitant.</p>
 *
 * <p>Le delai est plus court que les cinq secondes de la passerelle de paiement : ici
 * le visiteur attend devant un formulaire, et la verification est un supplement, non
 * une etape du parcours.</p>
 */
@Service
@ConditionalOnProperty(name = "autoservplus.securite.compromission.activee",
        havingValue = "true", matchIfMissing = true)
public class VerificateurHaveIBeenPwned implements VerificateurCompromission {

    private static final Logger JOURNAL =
            LoggerFactory.getLogger(VerificateurHaveIBeenPwned.class);

    /** Longueur du prefixe d empreinte transmis, imposee par l interface du service. */
    private static final int LONGUEUR_PREFIXE = 5;

    private final RestClient client;

    public VerificateurHaveIBeenPwned(@Qualifier("clientCompromission") RestClient client) {
        this.client = client;
    }

    @Override
    public boolean estCompromis(String motDePasse) {
        if (motDePasse == null || motDePasse.isBlank()) {
            return false;
        }
        String empreinte = sha1(motDePasse);
        String prefixe = empreinte.substring(0, LONGUEUR_PREFIXE);
        String suffixeCherche = empreinte.substring(LONGUEUR_PREFIXE);
        try {
            String reponse = client.get().uri("/range/{prefixe}", prefixe)
                    .retrieve().body(String.class);
            return contient(reponse, suffixeCherche);
        } catch (RuntimeException echec) {
            // Ni le mot de passe, ni son empreinte, ni meme le prefixe : une trace
            // d exploitation n a pas besoin d en porter pour etre utile.
            JOURNAL.warn("Verification des mots de passe compromis indisponible :"
                    + " l inscription se poursuit sur la seule liste embarquee. ({})",
                    echec.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * La reponse liste un suffixe et un compte par ligne, separes par deux-points.
     * Comparaison insensible a la casse : le service repond en majuscules, mais rien
     * dans son contrat ne l y engage.
     */
    private static boolean contient(String reponse, String suffixeCherche) {
        if (reponse == null) {
            return false;
        }
        String cherche = suffixeCherche.toUpperCase(Locale.ROOT);
        for (String ligne : reponse.split("\\R")) {
            int separateur = ligne.indexOf(':');
            String suffixe = separateur < 0 ? ligne : ligne.substring(0, separateur);
            if (cherche.equalsIgnoreCase(suffixe.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String sha1(String valeur) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(valeur.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-1 indisponible", impossible);
        }
    }
}
