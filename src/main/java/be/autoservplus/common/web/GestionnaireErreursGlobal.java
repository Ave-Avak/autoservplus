package be.autoservplus.common.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Traitement global des erreurs non rattrapees par un controleur.
 *
 * <p><b>Ce n est pas ce qui rend le 404.</b>
 * {@link RessourceIntrouvableException} porte deja
 * {@code @ResponseStatus(NOT_FOUND)} : une reference inconnue produisait donc
 * <b>deja</b> un 404, jamais un 500 — la dette qui annoncait l inverse etait
 * perimee. Ce que cet advice apporte, c est la <b>page</b> : sans lui, ce 404
 * s affiche sur la page Whitelabel de Spring Boot, qui ne parle aucune des trois
 * langues du projet et ne ramene nulle part.</p>
 *
 * <p><b>Deux exceptions seulement, jamais un attrape-tout.</b> Un
 * {@code @ExceptionHandler(Exception.class)} capturerait aussi les exceptions de
 * Spring MVC — parametre absent, verbe non supporte — auxquelles le framework
 * associe deja le bon statut, et les degraderait en 500. Le detail de la tentative
 * et de son retrait est en commentaire plus bas.</p>
 *
 * <p><b>Ne recouvre pas les controleurs qui catchent deja.</b> Un {@code catch} local
 * qui redirige avec un message flash reste prioritaire : il connait le contexte et
 * ramene l utilisateur sur son ecran, la ou cet advice ne peut offrir qu une page
 * d erreur. L advice est le dernier recours, pas le premier.</p>
 *
 * <p><b>Le gabarit {@code erreur.html} est le seul du projet sans bandeau cookies</b>
 * (F25), et sans commentaire non plus. Deux raisons distinctes :</p>
 * <ul>
 *   <li>Les attributs de modele d un {@code @ControllerAdvice} ne sont pas repeuples
 *       quand un {@code @ExceptionHandler} rend une vue : le fragment recevrait
 *       {@code choixCookiesExprime} a {@code null} et casserait le rendu. Une page
 *       d erreur qui plante est le pire endroit ou planter. L omission est sans effet
 *       sur la conformite — cette page ne depose aucun cookie, ne charge aucun
 *       traceur, et le bandeau reparait des la page suivante. A revoir le jour ou un
 *       traceur optionnel sera cable.</li>
 *   <li>Un commentaire HTML est <b>servi au navigateur</b>. Y expliquer le
 *       fonctionnement interne expedierait des noms de classes du projet a chaque
 *       404 — c est ce qu a rattrape {@code aucuneFuiteTechnique}, sur ce fichier
 *       meme. Le pourquoi vit donc ici, dans du code qui ne part jamais sur le
 *       reseau.</li>
 * </ul>
 */
@ControllerAdvice
public class GestionnaireErreursGlobal {

    private static final Logger log = LoggerFactory.getLogger(GestionnaireErreursGlobal.class);

    private static final String VUE = "erreur";

    private final MessageSource messages;

    public GestionnaireErreursGlobal(MessageSource messages) {
        this.messages = messages;
    }

    /**
     * Ressource inexistante ou appartenant a autrui.
     *
     * <p>Le <b>meme</b> 404 dans les deux cas, volontairement : distinguer
     * « n existe pas » de « pas a vous » confirmerait l existence de la reference a
     * qui n y a pas droit. C est la regle suivie par tous les services du projet.</p>
     */
    @ExceptionHandler({RessourceIntrouvableException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String introuvable(Exception e, HttpServletRequest requete, Model modele) {
        log.debug("404 sur {} : {}", requete.getRequestURI(), e.getMessage());
        return page(modele, "erreur.404.titre", "erreur.404.message");
    }

    /**
     * Ecriture concurrente perdue. <b>409 et non 500</b> : la demande etait
     * recevable, c est l etat qui a bouge entre l affichage et l envoi. Le message
     * invite a recharger, seule action utile.
     */
    @ExceptionHandler(ConflitConcurrenceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String conflit(ConflitConcurrenceException e, Model modele) {
        log.info("Conflit de concurrence : {}", e.getMessage());
        return page(modele, "erreur.409.titre", "erreur.409.message");
    }

    // PAS de @ExceptionHandler(Exception.class) ici, et c est deliberе.
    //
    // Un attrape-tout parait etre un filet de securite ; c en est un piege. Il
    // capture aussi les exceptions de Spring MVC — parametre absent, verbe non
    // supporte, type de contenu refuse — auxquelles le framework associe deja le
    // bon statut, et les degrade toutes en 500. Essaye puis retire : il faisait
    // passer PaiementWebhookControllerTest.parametreManquant de 400 a 500, soit
    // exactement le contraire du but recherche.
    //
    // Le besoin qu il devait couvrir — ne pas divulguer de detail technique sur une
    // erreur imprevue — est deja tenu par Spring Boot, dont server.error
    // .include-stacktrace et include-message valent « never » par defaut : la page
    // d erreur ne montre que le statut et le chemin. Rien a ajouter, donc rien
    // ajoute.

    private String page(Model modele, String cleTitre, String cleMessage) {
        modele.addAttribute("titre", msg(cleTitre));
        modele.addAttribute("messageErreur", msg(cleMessage));
        return VUE;
    }

    private String msg(String cle) {
        return messages.getMessage(cle, null, LocaleContextHolder.getLocale());
    }
}
