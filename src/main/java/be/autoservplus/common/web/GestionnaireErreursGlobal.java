package be.autoservplus.common.web;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.TransitionRefusee;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;

/**
 * Statut des erreurs que Spring MVC ne classe pas comme le projet le veut.
 *
 * <p>Aucune méthode ne rend de vue. Chacune pose le statut par {@code sendError}, et le
 * conteneur repasse par {@code /error}, où {@link PagesErreurResolver} rend
 * {@code erreur.html}. C'est déjà le chemin d'une {@code RessourceIntrouvableException},
 * que son {@code @ResponseStatus} envoie en 404 sans gestionnaire ici. Une vue rendue
 * depuis un {@code @ExceptionHandler} ne recevait pas les attributs des
 * {@code @ModelAttribute} de {@code SelecteurLangueAdvice} et de
 * {@code BandeauCookiesAdvice} : le sélecteur de langue sortait vide et le bandeau
 * manquait. Par {@code /error}, la page est la même pour toutes les erreurs, et une
 * requête JSON reçoit une réponse JSON au lieu d'une page.</p>
 *
 * <p>Pas d'{@code @ExceptionHandler(Exception.class)}. Il capturerait aussi les
 * exceptions de Spring MVC (paramètre absent, verbe non supporté) auxquelles le framework
 * associe déjà le bon statut, et les ferait passer en 500. Essayé puis retiré :
 * {@code PaiementWebhookControllerTest.parametreManquant} passait de 400 à 500. Le
 * détail technique d'une erreur imprévue reste caché par {@code server.error.*}.</p>
 *
 * <p>Un {@code catch} local qui redirige avec un message reste prioritaire : il connaît
 * l'écran où ramener l'utilisateur.</p>
 *
 * <p>Les refus métier qu'aucun contrôleur n'attrape sortent en 422 avec leur message
 * traduit. Sans ce filet, un refus prévu par une règle (panier mixte, prestation retirée,
 * annulation hors délai) s'affichait comme une panne du site et se journalisait en
 * ERROR, comme un incident.</p>
 */
@ControllerAdvice
public class GestionnaireErreursGlobal {

    private static final Logger log = LoggerFactory.getLogger(GestionnaireErreursGlobal.class);

    /**
     * Écriture concurrente perdue. 409 et non 500 : la demande était recevable, c'est
     * l'état qui a changé entre l'affichage et l'envoi.
     */
    @ExceptionHandler(ConflitConcurrenceException.class)
    public void conflit(ConflitConcurrenceException e, HttpServletResponse reponse) throws IOException {
        log.info("Conflit de concurrence : {}", e.getMessage());
        reponse.sendError(HttpStatus.CONFLICT.value());
    }

    /**
     * Valeur impossible à convertir, le plus souvent un UUID mal formé.
     *
     * <p>Dans le chemin, c'est une référence qui ne désigne rien : 404, comme une
     * référence bien formée mais inconnue. Un 400 dirait au visiteur que la forme de
     * l'identifiant compte, et donnerait deux réponses là où la règle n'en veut qu'une.
     * Dans un paramètre de requête, c'est une demande mal formée : 400, la réponse par
     * défaut de Spring MVC.</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public void conversionImpossible(MethodArgumentTypeMismatchException e, HttpServletRequest requete,
                                     HttpServletResponse reponse) throws IOException {
        boolean dansLeChemin = e.getParameter().hasParameterAnnotation(PathVariable.class);
        log.debug("Valeur non convertible pour {} sur {}", e.getName(), requete.getRequestURI());
        reponse.sendError(dansLeChemin ? HttpStatus.NOT_FOUND.value() : HttpStatus.BAD_REQUEST.value());
    }

    /**
     * Refus qui porte sa clé de message. 422 et non 409 : le 409 du projet dit
     * « modification simultanée », alors qu'ici la demande est lue en entier et refusée
     * par une règle. La clé passe à {@link PagesErreurResolver} par un attribut de la
     * requête, que la redistribution vers {@code /error} conserve.
     */
    @ExceptionHandler(RefusTraduit.class)
    public void refusTraduit(RefusTraduit e, HttpServletRequest requete, HttpServletResponse reponse)
            throws IOException {
        refuser(requete, reponse, e, new PagesErreurResolver.RefusAffiche(e.getCleMessage(), e.getArguments()));
    }

    /** Transition interdite par la machine à états d'une entité, même rendu qu'un refus traduit. */
    @ExceptionHandler(TransitionRefusee.class)
    public void transitionRefusee(TransitionRefusee e, HttpServletRequest requete, HttpServletResponse reponse)
            throws IOException {
        refuser(requete, reponse, e, new PagesErreurResolver.RefusAffiche(e.getCleMessage(), null));
    }

    /**
     * Refus sans clé. Son message est une phrase française écrite pour les journaux : la
     * page affiche le texte générique du refus, traduit, plutôt qu'une phrase dans une
     * autre langue que celle du visiteur.
     */
    @ExceptionHandler(RegleMetierException.class)
    public void regleMetier(RegleMetierException e, HttpServletRequest requete, HttpServletResponse reponse)
            throws IOException {
        refuser(requete, reponse, e, null);
    }

    private static void refuser(HttpServletRequest requete, HttpServletResponse reponse, RuntimeException e,
                                PagesErreurResolver.RefusAffiche refus) throws IOException {
        log.warn("Refus métier non rattrapé sur {} : {}", requete.getRequestURI(), e.toString());
        if (refus != null) {
            requete.setAttribute(PagesErreurResolver.ATTRIBUT_REFUS, refus);
        }
        reponse.sendError(HttpStatus.UNPROCESSABLE_ENTITY.value());
    }
}
