package be.autoservplus.messagerie.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.messagerie.domain.Conversation;
import be.autoservplus.messagerie.domain.RoleExpediteur;
import be.autoservplus.messagerie.repository.ConversationRepository;
import be.autoservplus.messagerie.service.dto.ConversationVue;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.service.NotificationService;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Cote garage de la messagerie (BL-5).
 *
 * <p>Aucun controle de propriete ici, et c est voulu : le garage est le second
 * interlocuteur de <b>tous</b> les fils. Le cloisonnement porte sur le membre, pas sur
 * l administration.</p>
 *
 * <p>{@code @PreAuthorize} de classe en defense en profondeur : la protection d URL
 * {@code /admin/**} filtre deja le role, le service refuse en second.</p>
 *
 * <p><b>La cloture est reversible</b> : elle ferme le fil sans effacer la trace, et le
 * garage peut rouvrir si le sujet revient. Repondre ne rouvre pas automatiquement —
 * la reouverture est un geste explicite, sans quoi une reponse malencontreuse
 * relancerait un dossier que le garage venait de clore.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminMessagerieService {

    private final ConversationRepository conversations;
    private final UtilisateurRepository membres;
    private final ConversationMapper mapper;
    private final ParametreAtelierRepository parametres;
    private final NotificationService notifications;
    private final Clock horloge;

    public AdminMessagerieService(ConversationRepository conversations,
                                  UtilisateurRepository membres,
                                  ConversationMapper mapper,
                                  ParametreAtelierRepository parametres,
                                  NotificationService notifications,
                                  Clock horloge) {
        this.conversations = conversations;
        this.membres = membres;
        this.mapper = mapper;
        this.parametres = parametres;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    /** Tous les fils ; les ouverts avant les clos. */
    public List<ConversationVue> tous() {
        ZoneId zone = parametres.courants().zone();
        return conversations.tousPourLeGarage().stream()
                .map(fil -> mapper.resume(fil, RoleExpediteur.ADMINISTRATEUR, zone))
                .toList();
    }

    /** Detail d un fil ; ouvrir vaut lecture, comme cote membre. */
    @Transactional
    public ConversationVue lire(UUID reference) {
        Conversation fil = charger(reference);
        fil.marquerLuPar(RoleExpediteur.ADMINISTRATEUR);
        return mapper.detail(fil, RoleExpediteur.ADMINISTRATEUR, parametres.courants().zone());
    }

    @Transactional
    public void repondre(String emailAdministrateur, UUID reference, String corps) {
        Conversation fil = charger(reference);
        Utilisateur auteur = membres.findByEmailIgnoreCase(emailAdministrateur)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Utilisateur", emailAdministrateur));
        fil.ajouter(auteur, RoleExpediteur.ADMINISTRATEUR, corps, horloge.instant());
        notifications.deposer(fil.getMembre(), TypeNotification.MESSAGE_RECU, fil.getSujet());
    }

    @Transactional
    public void cloturer(UUID reference) {
        charger(reference).cloturer();
    }

    @Transactional
    public void rouvrir(UUID reference) {
        charger(reference).rouvrir();
    }

    private Conversation charger(UUID reference) {
        return conversations.findByReferenceAvecMessages(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Conversation", reference));
    }
}
