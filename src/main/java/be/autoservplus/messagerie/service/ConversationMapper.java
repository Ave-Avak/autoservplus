package be.autoservplus.messagerie.service;

import be.autoservplus.messagerie.domain.Conversation;
import be.autoservplus.messagerie.domain.RoleExpediteur;
import be.autoservplus.messagerie.service.dto.ConversationVue;
import be.autoservplus.messagerie.service.dto.MessageVue;
import be.autoservplus.reservation.service.support.FormatageRdv;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

/**
 * Projection des fils de discussion vers leurs DTO (BL-5).
 *
 * <p><b>Composant separe et non methodes partagees entre les deux services.</b> Les
 * deux services portent un {@code @PreAuthorize} de classe — l un
 * {@code isAuthenticated()}, l autre {@code hasRole('ADMINISTRATEUR')} — et se
 * passer une methode de projection de l un a l autre la ferait traverser un proxy de
 * securite dont ce n est pas le role. La conversion n a aucune autorisation a
 * verifier : elle recoit une entite deja chargee par un appelant qui, lui, a fait le
 * controle.</p>
 */
@Component
public class ConversationMapper {

    private final MessageSource messages;

    public ConversationMapper(MessageSource messages) {
        this.messages = messages;
    }

    /** Ligne de liste : les messages ne sont pas charges, seul leur compte importe. */
    public ConversationVue resume(Conversation fil, RoleExpediteur lecteur, ZoneId zone) {
        return vue(fil, lecteur, zone, List.of());
    }

    /** Vue de detail, messages compris. */
    public ConversationVue detail(Conversation fil, RoleExpediteur lecteur, ZoneId zone) {
        String libelleGarage = messages.getMessage("messagerie.garage", null,
                LocaleContextHolder.getLocale());
        List<MessageVue> vues = fil.getMessages().stream()
                .map(m -> MessageVue.de(m, FormatageRdv.jourLisible(m.getDateEnvoi(), zone),
                        libelleGarage))
                .toList();
        return vue(fil, lecteur, zone, vues);
    }

    private ConversationVue vue(Conversation fil, RoleExpediteur lecteur, ZoneId zone,
                                List<MessageVue> messagesVus) {
        return new ConversationVue(
                fil.getReference(),
                fil.getSujet(),
                fil.getMembre().getPrenom(),
                fil.getIntervention() == null ? null : fil.getIntervention().getNumero(),
                fil.isCloturee(),
                fil.nombreNonLusPar(lecteur),
                FormatageRdv.jourLisible(fil.getUpdatedAt(), zone),
                messagesVus);
    }
}
