package be.autoservplus.identite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Verification <b>cote serveur</b> du jeton Turnstile.
 *
 * <h2>Pourquoi elle est indispensable</h2>
 *
 * <p>Le widget depose un jeton dans le formulaire. Ce jeton ne vaut rien tant qu il n
 * a pas ete presente a Cloudflare : sans cette verification, il suffirait de poster le
 * formulaire sans passer par le widget, ou d y mettre n importe quelle chaine. C est
 * la meme regle que pour le webhook de paiement — <b>le payload n est jamais cru, le
 * statut est relu chez le prestataire</b>.</p>
 *
 * <h2>Panne</h2>
 *
 * <p><b>Une panne de Cloudflare ne bloque pas l inscription.</b> Les trois autres
 * couches — champ piege, delai minimal, plafond par IP — restent en vigueur, et
 * refuser tout le monde parce qu un tiers est indisponible serait lui confier la
 * disponibilite du site. Choix inverse de celui du paiement, ou un prestataire
 * injoignable interrompt le parcours : la, il n y a rien a encaisser sans lui.</p>
 *
 * <p>Un jeton <b>absent</b> est en revanche refuse sans appel : il signale un
 * formulaire poste hors du navigateur, pas une panne.</p>
 */
@Service
@ConditionalOnProperty(name = "autoservplus.securite.turnstile.cle-secrete")
public class VerificateurTurnstile {

    private static final Logger JOURNAL = LoggerFactory.getLogger(VerificateurTurnstile.class);

    /** Nom du champ que le widget ajoute au formulaire. */
    public static final String CHAMP_JETON = "cf-turnstile-response";

    private final RestClient client;
    private final String cleSecrete;

    public VerificateurTurnstile(@Qualifier("clientTurnstile") RestClient client,
                                 @Value("${autoservplus.securite.turnstile.cle-secrete}")
                                 String cleSecrete) {
        this.client = client;
        this.cleSecrete = cleSecrete;
    }

    /**
     * Dit si le jeton est valide.
     *
     * @param jeton valeur du champ {@value #CHAMP_JETON}
     * @param ip    adresse du visiteur, transmise a Cloudflare pour affiner son
     *              analyse ; facultative de son cote
     */
    public boolean jetonValide(String jeton, String ip) {
        if (jeton == null || jeton.isBlank()) {
            JOURNAL.info("Soumission sans jeton Turnstile : ecartee.");
            return false;
        }
        MultiValueMap<String, String> corps = new LinkedMultiValueMap<>();
        corps.add("secret", cleSecrete);
        corps.add("response", jeton);
        if (ip != null && !ip.isBlank()) {
            corps.add("remoteip", ip);
        }
        try {
            Map<?, ?> reponse = client.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(corps)
                    .retrieve().body(Map.class);
            boolean valide = reponse != null && Boolean.TRUE.equals(reponse.get("success"));
            if (!valide) {
                JOURNAL.info("Jeton Turnstile refuse par Cloudflare.");
            }
            return valide;
        } catch (RuntimeException echec) {
            // Ni le jeton ni la cle : la trace sert a diagnostiquer une panne, pas a
            // rejouer une soumission.
            JOURNAL.warn("Verification Turnstile indisponible : la soumission est acceptee,"
                    + " les autres couches restent en vigueur. ({})",
                    echec.getClass().getSimpleName());
            return true;
        }
    }
}
