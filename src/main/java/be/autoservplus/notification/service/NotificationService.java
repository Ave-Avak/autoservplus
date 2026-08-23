package be.autoservplus.notification.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.notification.domain.Notification;
import be.autoservplus.notification.domain.StatutNotification;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.repository.NotificationRepository;
import be.autoservplus.notification.service.dto.NotificationVue;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Notifications in-app du membre (BL-6) : depot par les evenements metier, lecture et
 * marquage par le titulaire.
 *
 * <p><b>Pas de {@code @PreAuthorize} de classe, contrairement au reste du projet.</b>
 * {@link #deposer} est appele par {@code NotificationEvenementListener} <b>apres
 * commit</b>, dans une transaction neuve ou aucun contexte de securite n est etabli —
 * une transition de rendez-vous decidee par l administrateur produit une notification
 * destinee au membre, et l evenement se rejoue hors requete (tache planifiee, webhook
 * du prestataire de paiement). Une garde de classe y leverait
 * {@code AuthenticationCredentialsNotFoundException} et ferait perdre la notification.
 * Les quatre methodes exposees au membre portent donc leur garde individuellement,
 * comme le fait deja {@code InterventionService} pour sa vue membre.</p>
 *
 * <p><b>Ownership.</b> Toutes les lectures et le marquage passent par le couple
 * (identifiant, membre) : la table {@code notification} n a pas de reference UUID, son
 * identifiant est un entier sequentiel devinable. Une notification d autrui remonte en
 * {@link RessourceIntrouvableException}, donc 404 et non 403 — meme code qu un
 * identifiant inconnu, pour ne pas confirmer l existence de la ligne.</p>
 *
 * <p><b>Rendu a la lecture.</b> Le texte est resolu ici, dans la locale courante du
 * lecteur, depuis {@link TypeNotification} et l argument conserve. Meme patron que
 * {@code CatalogueTraitements}, qui resout deja ses libelles en couche service.</p>
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notifications;
    private final UtilisateurRepository membres;
    private final ParametreAtelierRepository parametres;
    private final MessageSource messages;
    private final Clock horloge;

    public NotificationService(NotificationRepository notifications,
                               UtilisateurRepository membres,
                               ParametreAtelierRepository parametres,
                               MessageSource messages,
                               Clock horloge) {
        this.notifications = notifications;
        this.membres = membres;
        this.parametres = parametres;
        this.messages = messages;
        this.horloge = horloge;
    }

    // --- depot (appele hors contexte de securite, apres commit) -----------------------

    /**
     * Depose une notification applicative pour un membre.
     *
     * <p>Sans garde de securite : voir le Javadoc de classe. L appelant est un listener
     * {@code AFTER_COMMIT}, jamais un controleur.</p>
     *
     * @param argument numero metier affiche au membre ; jamais de donnee sensible
     */
    @Transactional
    public void deposer(Utilisateur membre, TypeNotification type, String argument) {
        String traceFr = messages.getMessage(type.cleTitre(), null, Locale.FRENCH);
        notifications.save(new Notification(membre, type, traceFr, argument, horloge.instant()));
    }

    // --- lecture et marquage par le titulaire -----------------------------------------

    /** Notifications du membre connecte, la plus recente d abord. */
    @PreAuthorize("isAuthenticated()")
    public List<NotificationVue> mesNotifications(String email) {
        Utilisateur membre = membre(email);
        Locale langue = LocaleContextHolder.getLocale();
        ZoneId zone = parametres.courants().zone();
        return notifications.findByMembreOrderByDateEnvoiDescIdDesc(membre).stream()
                .map(notification -> vue(notification, langue, zone))
                .toList();
    }

    /** Compteur affiche dans la navigation. */
    @PreAuthorize("isAuthenticated()")
    public long nombreNonLues(String email) {
        return membres.findByEmailIgnoreCase(email)
                .map(membre -> notifications.countByMembreAndStatut(membre, StatutNotification.NON_LUE))
                .orElse(0L);
    }

    /**
     * Marque une notification du membre comme lue.
     *
     * @throws RessourceIntrouvableException identifiant inconnu, ou notification d autrui
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void marquerLue(String email, Long id) {
        Notification notification = notifications.findByIdAndMembre(id, membre(email))
                .orElseThrow(() -> new RessourceIntrouvableException("Notification", id));
        notification.marquerLue(horloge.instant());
    }

    /** Marque toutes les notifications non lues du membre comme lues. */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void marquerToutesLues(String email) {
        var maintenant = horloge.instant();
        notifications.findByMembreAndStatut(membre(email), StatutNotification.NON_LUE)
                .forEach(notification -> notification.marquerLue(maintenant));
    }

    // --- helpers privees ---------------------------------------------------------------

    private Utilisateur membre(String email) {
        return membres.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
    }

    private NotificationVue vue(Notification notification, Locale langue, ZoneId zone) {
        TypeNotification type = notification.getType();
        return new NotificationVue(
                notification.getId(),
                messages.getMessage(type.cleTitre(), null, langue),
                messages.getMessage(type.cleCorps(), new Object[]{notification.getCorps()}, langue),
                !notification.estNonLue(),
                FormatageRdv.jourLisible(notification.getDateEnvoi(), zone));
    }
}
