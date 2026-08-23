package be.autoservplus.cookies.web;

import be.autoservplus.cookies.domain.PreferencesCookies;
import be.autoservplus.cookies.service.PreferencesCookiesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.regex.Pattern;

/**
 * Bandeau de consentement aux cookies et gestion permanente des preferences (F25).
 *
 * <p><b>Aucun JavaScript.</b> Le bandeau est un formulaire, l ecran « Personnaliser »
 * un element {@code <details>} natif. Ce choix repond a trois contraintes a la fois :
 * la politique de securite du contenu n autorise que {@code 'self'} pour les scripts
 * et interdit tout script en ligne ; un bandeau pilote par JavaScript disparait pour
 * qui le desactive, alors que le consentement doit pouvoir etre exprime par tous ; et
 * {@code <details>}/{@code <summary>} est nativement navigable au clavier, la ou une
 * boite de dialogue maison demanderait de reimplementer la gestion du focus. La
 * politique de securite n a donc pas eu a etre assouplie.</p>
 *
 * <p><b>Le cookie est pose par le serveur</b>, jamais par le navigateur : c est ce qui
 * garantit que ses attributs — duree, portee, {@code SameSite} — sont ceux decrits
 * ici et pas ceux d un script qu on aurait oublie de mettre a jour.</p>
 */
@Controller
public class PreferencesCookiesController {

    private static final String ACTION_ACCEPTER = "accepter";
    private static final String ACTION_REFUSER = "refuser";

    /**
     * Adresses de retour acceptees : un chemin local, rien d autre. Sans ce filtre,
     * le champ cache {@code retour} offrirait une redirection ouverte — un lien
     * fabrique renverrait le visiteur vers un site tiers depuis une adresse
     * AutoServ+, ce qui est le point de depart classique d un hameconnage.
     */
    private static final Pattern RETOUR_LOCAL = Pattern.compile("/[A-Za-z0-9._~!$&'()*+,;=:@%/?-]*");

    private static final String DESTINATION_PAR_DEFAUT = "/";

    private final PreferencesCookiesService service;
    private final MessageSource messages;

    public PreferencesCookiesController(PreferencesCookiesService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    /**
     * Ecran « Gerer mes cookies », atteignable en permanence depuis le pied de page.
     * Une page plutot qu une boite reouverte par script : elle reste accessible sans
     * JavaScript, se met en favori et se cite dans la politique de confidentialite.
     */
    @GetMapping("/cookies")
    public String preferences(@RequestParam(name = "enregistre", required = false) String enregistre,
                              Model modele) {
        modele.addAttribute("titre", msg("cookies.page.titre"));
        modele.addAttribute("choixEnregistre", enregistre != null);
        return "cookies/preferences";
    }

    /**
     * Enregistre le choix : cookie de preference dans tous les cas, preuve en base
     * si le visiteur est connecte.
     *
     * <p>Les trois actions du bandeau arrivent ici. « Tout accepter » et « Tout
     * refuser » sont symetriques — meme chemin, meme cout, une seule pression —
     * parce que refuser doit etre aussi simple qu accepter.</p>
     */
    @PostMapping("/cookies/preferences")
    public String enregistrer(@RequestParam(name = "action", required = false) String action,
                              @RequestParam(name = "analytique", defaultValue = "false") boolean analytique,
                              @RequestParam(name = "marketing", defaultValue = "false") boolean marketing,
                              @RequestParam(name = "retour", required = false) String retour,
                              @AuthenticationPrincipal UserDetails membre,
                              HttpServletRequest requete,
                              HttpServletResponse reponse) {
        PreferencesCookies choix = choix(action, analytique, marketing);
        deposerCookie(choix, requete, reponse);
        service.enregistrer(membre == null ? null : membre.getUsername(), choix,
                requete.getRemoteAddr());
        return "redirect:" + destination(retour);
    }

    private PreferencesCookies choix(String action, boolean analytique, boolean marketing) {
        return switch (action == null ? "" : action) {
            case ACTION_ACCEPTER -> PreferencesCookies.acceptationTotale();
            case ACTION_REFUSER -> PreferencesCookies.refusTotal();
            // « Personnaliser », et tout envoi dont l action est absente ou inconnue :
            // seules les cases effectivement cochees sont retenues. Une action que le
            // serveur ne reconnait pas ne doit jamais accorder plus que ce qui a ete
            // demande explicitement.
            default -> new PreferencesCookies(analytique, marketing);
        };
    }

    /**
     * Depose le cookie de preference.
     *
     * <p>{@code HttpOnly} est actif : aucun script n a besoin de lire ce cookie,
     * puisque c est le serveur qui decide du rendu du bandeau — et, le jour ou des
     * traceurs existeront, qui decidera aussi de les servir ou non. Un cookie
     * inaccessible au JavaScript est hors de portee d une injection de script.</p>
     *
     * <p>{@code Secure} suit le protocole reel de la requete au lieu d etre force :
     * un cookie {@code Secure} emis en HTTP simple est ignore par le navigateur, le
     * choix ne serait jamais memorise et le bandeau reviendrait a chaque page sur le
     * poste de developpement. En production, derriere HTTPS, l attribut est pose.</p>
     *
     * <p><b>{@code SameSite=Lax} et non {@code Strict}.</b> {@code Strict} n envoie
     * pas le cookie lors d une navigation entrante depuis un autre site — un resultat
     * de moteur de recherche, un lien dans un courriel. Le serveur ne verrait alors
     * aucune preference et reafficherait le bandeau a quelqu un qui a deja repondu, y
     * compris a quelqu un qui a <b>refuse</b> : redemander sans fin a qui a dit non
     * est precisement le procede que l Autorite de Protection des Donnees qualifie de
     * pratique manipulatrice. {@code Lax} accompagne la navigation de premier niveau
     * et reste bloque en contexte tiers, ce qui est le reglage attendu d un cookie de
     * preference — {@code Strict} releve du cookie de session, dont l enjeu est
     * l usurpation, pas la memoire d un choix.</p>
     */
    private void deposerCookie(PreferencesCookies choix, HttpServletRequest requete,
                               HttpServletResponse reponse) {
        ResponseCookie cookie = ResponseCookie.from(PreferencesCookies.NOM_COOKIE,
                        choix.versValeurCookie())
                .path("/")
                .maxAge(service.dureeDeMemorisation())
                .httpOnly(true)
                .secure(requete.isSecure())
                .sameSite("Lax")
                .build();
        reponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String destination(String retour) {
        if (retour == null || retour.startsWith("//") || retour.startsWith("/\\")
                || !RETOUR_LOCAL.matcher(retour).matches()) {
            return DESTINATION_PAR_DEFAUT;
        }
        return retour;
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
