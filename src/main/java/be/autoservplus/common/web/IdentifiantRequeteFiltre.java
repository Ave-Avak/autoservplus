package be.autoservplus.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Donne à chaque requête un identifiant, porté par le MDC sous la clé {@value #CLE} et
 * repris par le motif des journaux.
 *
 * <p>Il relie entre elles les lignes d'une même requête, et à celles des tâches qu'elle
 * confie à un autre fil, comme l'envoi d'un courriel par l'exécuteur des formulaires
 * anonymes, à qui {@code TaskDecorator} recopie le MDC. Huit caractères hexadécimaux tirés
 * au hasard : aucune donnée personnelle, et assez pour distinguer les requêtes d'une
 * même minute.</p>
 *
 * <p><b>Dispatch d'erreur compris.</b> Une exception qui remonte hors de la chaîne fait
 * rejouer la requête vers la page d'erreur, après que le premier passage a vidé le MDC.
 * L'identifiant est donc aussi gardé en attribut de requête, et repris : les lignes d'un
 * 500 portent celui de la requête qui l'a causé. Un identifiant déjà présent dans le MDC
 * est repris de même, et n'est alors pas effacé à la sortie, puisqu'il appartient au
 * passage qui l'a posé.</p>
 */
public class IdentifiantRequeteFiltre extends OncePerRequestFilter {

    public static final String CLE = "requete";
    /** Attribut de requête qui garde l'identifiant pour le dispatch d'erreur. */
    public static final String ATTRIBUT = IdentifiantRequeteFiltre.class.getName() + ".identifiant";

    private static final SecureRandom HASARD = new SecureRandom();

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain chaine) throws ServletException, IOException {
        String dejaPose = MDC.get(CLE);
        String identifiant = dejaPose != null ? dejaPose : (String) requete.getAttribute(ATTRIBUT);
        if (identifiant == null) {
            identifiant = nouvelIdentifiant();
        }
        requete.setAttribute(ATTRIBUT, identifiant);
        MDC.put(CLE, identifiant);
        try {
            chaine.doFilter(requete, reponse);
        } finally {
            if (dejaPose == null) {
                MDC.remove(CLE);
            }
        }
    }

    static String nouvelIdentifiant() {
        byte[] octets = new byte[4];
        HASARD.nextBytes(octets);
        return HexFormat.of().formatHex(octets);
    }
}
