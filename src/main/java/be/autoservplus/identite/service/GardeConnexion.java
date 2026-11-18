package be.autoservplus.identite.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Échecs de connexion comptés par adresse IP et par adresse saisie, en mémoire.
 *
 * <h2>Plafond par IP (Stratégie A07, contrôle 4)</h2>
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
 * <h2>Vérification humaine après trois échecs</h2>
 *
 * <p>Trois échecs sur l'IP ou sur l'adresse saisie exigent un jeton Turnstile valide
 * avant l'authentification. Le compte par adresse ne dépend pas de l'existence du
 * compte : compté en base, il ne viserait que les adresses connues, et l'apparition du
 * widget dirait lesquelles le sont. L'adresse n'est gardée que sous forme d'empreinte,
 * comme dans {@link LimiteurDemandesCourriel}. Une connexion aboutie remet à zéro le
 * compte de l'adresse, jamais celui de l'IP.</p>
 *
 * <p><b>Carte saturée.</b> Une IP ou une adresse que la carte ne peut plus retenir exige
 * la vérification humaine : oublier des entrées laisserait passer sans jeton qui sature
 * la carte. Le plafond par IP, lui, ne compte plus les IP nouvelles dans ce cas.</p>
 *
 * <p><b>IPv6 par /64.</b> Un fournisseur attribue au moins un /64 à chaque abonné : compter
 * par adresse complète donnerait à chacun des milliards d'adresses, donc autant de
 * budgets. L'adresse vient de {@code getRemoteAddr()}, que la vanne RemoteIp de Tomcat ne
 * réécrit d'après X-Forwarded-For que pour un proxy de confiance
 * ({@code server.forward-headers-strategy: native}).</p>
 *
 * <p>Compteurs en mémoire et par instance, comme ceux de {@link LimiteurDemandesCourriel}
 * et pour les mêmes raisons.</p>
 */
@Service
public class GardeConnexion {

    private static final int CLES_MAX = 100_000;
    private static final Pattern LITTERAL_IPV6 = Pattern.compile("[0-9A-Fa-f:.]+");

    private final Clock horloge;
    private final int echecsMaxParIp;
    private final int echecsAvantVerification;
    private final FenetresGlissantes echecsParIp;
    private final FenetresGlissantes echecsParAdresse;

    @Autowired
    public GardeConnexion(
            Clock horloge,
            @Value("${autoservplus.securite.connexion.echecs-max-par-ip:50}") int echecsMaxParIp,
            @Value("${autoservplus.securite.connexion.echecs-avant-verification:3}")
            int echecsAvantVerification,
            @Value("${autoservplus.securite.fenetre-echecs-minutes:15}") long fenetreMinutes) {
        this(horloge, echecsMaxParIp, echecsAvantVerification, fenetreMinutes, CLES_MAX);
    }

    GardeConnexion(Clock horloge, int echecsMaxParIp, int echecsAvantVerification,
                   long fenetreMinutes, int clesMax) {
        this.horloge = horloge;
        this.echecsMaxParIp = echecsMaxParIp;
        this.echecsAvantVerification = echecsAvantVerification;
        Duration fenetre = Duration.ofMinutes(fenetreMinutes);
        this.echecsParIp = new FenetresGlissantes(fenetre, clesMax);
        this.echecsParAdresse = new FenetresGlissantes(fenetre, clesMax);
    }

    /** Vrai si l'IP a atteint son plafond d'échecs : la tentative est refusée d'emblée. */
    public boolean plafondAtteint(String ip) {
        return echecsParIp.compter(cleIp(ip), horloge.instant()) >= echecsMaxParIp;
    }

    /** Vrai si la tentative doit porter un jeton Turnstile valide. */
    public boolean exigeVerificationHumaine(String adresse, String ip) {
        Instant maintenant = horloge.instant();
        String cleAdresse = LimiteurDemandesCourriel.empreinte(adresse);
        return exigeVerificationHumaine(ip)
                || echecsParAdresse.saturee(cleAdresse, maintenant)
                || echecsParAdresse.compter(cleAdresse, maintenant) >= echecsAvantVerification;
    }

    /**
     * Vrai si l'IP seule exige la vérification humaine. Sert à la page de connexion, qui
     * ne connaît pas encore l'adresse que le visiteur va saisir.
     */
    public boolean exigeVerificationHumaine(String ip) {
        Instant maintenant = horloge.instant();
        String cle = cleIp(ip);
        return echecsParIp.saturee(cle, maintenant)
                || echecsParIp.compter(cle, maintenant) >= echecsAvantVerification;
    }

    /** Compte un échec de connexion, quelle qu'en soit la cause, adresse inconnue comprise. */
    public void echec(String adresse, String ip) {
        Instant maintenant = horloge.instant();
        echecsParIp.ajouter(cleIp(ip), maintenant);
        echecsParAdresse.ajouter(LimiteurDemandesCourriel.empreinte(adresse), maintenant);
    }

    /** Connexion aboutie : l'adresse repart de zéro, l'IP garde ses échecs. */
    public void succes(String adresse) {
        echecsParAdresse.retirer(LimiteurDemandesCourriel.empreinte(adresse));
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
