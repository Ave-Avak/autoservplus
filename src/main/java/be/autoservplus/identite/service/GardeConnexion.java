package be.autoservplus.identite.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Plafond des echecs de connexion par adresse IP (Stratégie A07, contrôle 4).
 *
 * <p>Le verrouillage par compte arrête qui vise un compte, pas qui essaie un mot de passe
 * courant sur beaucoup d'adresses : chaque compte n'y perd qu'une tentative. Cinquante
 * échecs par IP sur quinze minutes dépassent de loin ce que produit le réseau partagé
 * d'un garage ou d'une famille, et arrêtent un tel balayage.</p>
 *
 * <p><b>Seuls les échecs comptent.</b> Un succès n'efface pas le compte de l'IP : sinon un
 * attaquant qui détient un compte remettrait son budget à zéro entre deux séries. Au
 * plafond, la tentative est refusée avant toute authentification, donc sans BCrypt et
 * sans toucher aux compteurs du compte visé, et elle ne compte pas davantage.</p>
 *
 * <p><b>IPv6 par /64.</b> Un fournisseur attribue au moins un /64 à chaque abonné : compter
 * par adresse complète donnerait à chacun des milliards d'adresses, donc autant de
 * budgets. L'adresse vient de {@code getRemoteAddr()}, que la vanne RemoteIp de Tomcat ne
 * réécrit d'après X-Forwarded-For que pour un proxy de confiance
 * ({@code server.forward-headers-strategy: native}).</p>
 *
 * <p>Compteurs en mémoire et par instance, comme ceux de {@link LimiteurDemandesCourriel}
 * et pour les memes raisons. Carte bornee : saturee, elle ne compte plus les IP nouvelles,
 * et la verification humaine prend le relais.</p>
 */
@Service
public class GardeConnexion {

    private static final int CLES_MAX = 100_000;
    private static final Pattern LITTERAL_IPV6 = Pattern.compile("[0-9A-Fa-f:.]+");

    private final Clock horloge;
    private final int echecsMaxParIp;
    private final FenetresGlissantes echecsParIp;

    public GardeConnexion(
            Clock horloge,
            @Value("${autoservplus.securite.connexion.echecs-max-par-ip:50}") int echecsMaxParIp,
            @Value("${autoservplus.securite.fenetre-echecs-minutes:15}") long fenetreMinutes) {
        this.horloge = horloge;
        this.echecsMaxParIp = echecsMaxParIp;
        this.echecsParIp = new FenetresGlissantes(Duration.ofMinutes(fenetreMinutes), CLES_MAX);
    }

    /** Vrai si l'IP a atteint son plafond d'échecs : la tentative est refusée d'emblée. */
    public boolean plafondAtteint(String ip) {
        return echecsParIp.compter(cleIp(ip), horloge.instant()) >= echecsMaxParIp;
    }

    /** Compte un échec de connexion, quelle qu'en soit la cause, adresse inconnue comprise. */
    public void echec(String ip) {
        echecsParIp.ajouter(cleIp(ip), horloge.instant());
    }

    /**
     * Clé de comptage d'une IP : l'adresse IPv4 telle quelle, le préfixe /64 d'une adresse
     * IPv6. Une IPv4 écrite en IPv6 ({@code ::ffff:192.0.2.1}) compte comme l'IPv4.
     *
     * <p>Le motif refuse tout ce qui n'est pas un littéral : {@code InetAddress.getByName}
     * ne fait alors aucune résolution de nom.</p>
     */
    static String cleIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "?";
        }
        String litteral = ip.strip();
        if (litteral.startsWith("[") && litteral.endsWith("]")) {
            litteral = litteral.substring(1, litteral.length() - 1);
        }
        int zone = litteral.indexOf('%');
        if (zone >= 0) {
            litteral = litteral.substring(0, zone);
        }
        if (litteral.indexOf(':') < 0 || !LITTERAL_IPV6.matcher(litteral).matches()) {
            return litteral;
        }
        try {
            InetAddress adresse = InetAddress.getByName(litteral);
            if (adresse instanceof Inet4Address) {
                return adresse.getHostAddress();
            }
            return HexFormat.of().formatHex(adresse.getAddress(), 0, 8) + "/64";
        } catch (UnknownHostException malformee) {
            return litteral;
        }
    }
}
