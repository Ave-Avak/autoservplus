package be.autoservplus.common.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Rafraîchissement périodique d'un fragment par HTMX ({@code hx-trigger="every ..."}) :
 * fil de discussion, compteur des messages, statut d'une intervention.
 *
 * <p>Une page oubliée dans un onglet interrogerait le serveur indéfiniment. Chaque page
 * porte donc l'instant où elle a été rendue ({@code instantPage}, en millisecondes),
 * recopié dans l'adresse du fragment. Quinze minutes plus tard, sans nouvelle action qui
 * rende la page à nouveau, le serveur répond {@link #ARRET}, le statut par lequel HTMX
 * cesse d'interroger. Recharger la page relance le rafraîchissement.</p>
 *
 * <p>L'instant vient du client et peut être falsifié : il ne commande que l'arrêt des
 * interrogations, jamais un accès.</p>
 */
@ControllerAdvice
public class Rafraichissement {

    /** Statut qui arrête un déclencheur {@code every} d'HTMX (documentation HTMX, « Polling »). */
    public static final int ARRET = 286;

    /** Délai sans nouvel affichage de la page au-delà duquel le rafraîchissement s'arrête. */
    public static final Duration INACTIVITE = Duration.ofMinutes(15);

    private final ObjectProvider<Clock> horloge;

    public Rafraichissement(ObjectProvider<Clock> horloge) {
        this.horloge = horloge;
    }

    /**
     * Instant de rendu de la page, posé pour tous les contrôleurs. Un fragment rafraîchi le
     * remplace par celui qu'il a reçu, pour que l'arrêt se compte depuis le dernier rendu
     * complet et non depuis la dernière interrogation.
     */
    @ModelAttribute
    public void exposerLInstantDeLaPage(Model modele) {
        modele.addAttribute("instantPage", horloge.getIfAvailable(Clock::systemUTC).millis());
    }

    /**
     * @param depuis instant de rendu de la page, en millisecondes, ou {@code null} pour une
     *               page rendue avant ce mécanisme, qui n'est jamais arrêtée
     */
    public static boolean expire(Long depuis, Instant maintenant) {
        return depuis != null && maintenant.toEpochMilli() - depuis > INACTIVITE.toMillis();
    }
}
