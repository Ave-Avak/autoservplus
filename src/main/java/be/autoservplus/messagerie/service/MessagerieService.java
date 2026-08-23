package be.autoservplus.messagerie.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.messagerie.domain.Conversation;
import be.autoservplus.messagerie.domain.RoleExpediteur;
import be.autoservplus.messagerie.repository.ConversationRepository;
import be.autoservplus.messagerie.service.dto.ConversationVue;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.service.NotificationService;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Messagerie entre un membre et le garage (BL-5), cote membre.
 *
 * <p><b>Ownership systematique.</b> Chaque acces passe par le couple (reference,
 * membre) ; le fil d autrui remonte en {@link RessourceIntrouvableException}, donc 404
 * et non 403 — repondre « interdit » confirmerait que la reference existe.</p>
 *
 * <p><b>Rattachement facultatif a une intervention seulement.</b> Le socle V7 ne
 * prevoit que {@code conversation.intervention_id} ; il n existe ni {@code rdv_id} ni
 * {@code commande_id}. Un fil peut donc porter sur des travaux, ou rester libre. Le
 * membre qui veut parler d une commande ouvre un fil libre et cite sa reference dans
 * le message — arbitrage retenu contre l ajout d une migration et d un ecran de choix
 * de contexte.</p>
 *
 * <p><b>Notification dans la transaction</b>, contrairement aux listeners d evenement :
 * il n y a pas d evenement ici, l envoi est l action elle-meme. Un echec doit annuler
 * l ensemble plutot que laisser un message que personne ne verra jamais.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class MessagerieService {

    private static final Logger log = LoggerFactory.getLogger(MessagerieService.class);

    private final ConversationRepository conversations;
    private final UtilisateurRepository membres;
    private final InterventionRepository interventions;
    private final ParametreAtelierRepository parametres;
    private final NotificationService notifications;
    private final ConversationMapper mapper;
    private final Clock horloge;

    public MessagerieService(ConversationRepository conversations,
                             UtilisateurRepository membres,
                             InterventionRepository interventions,
                             ParametreAtelierRepository parametres,
                             NotificationService notifications,
                             ConversationMapper mapper,
                             Clock horloge) {
        this.conversations = conversations;
        this.membres = membres;
        this.interventions = interventions;
        this.parametres = parametres;
        this.notifications = notifications;
        this.mapper = mapper;
        this.horloge = horloge;
    }

    // --- lecture ------------------------------------------------------------------------

    public List<ConversationVue> mesFils(String email) {
        ZoneId zone = parametres.courants().zone();
        return conversations.duMembre(membre(email)).stream()
                .map(fil -> mapper.resume(fil, RoleExpediteur.MEMBRE, zone))
                .toList();
    }

    public long nombreFilsNonLus(String email) {
        return membres.findByEmailIgnoreCase(email)
                .map(conversations::nombreFilsNonLusParLeMembre)
                .orElse(0L);
    }

    /**
     * Detail d un fil du membre. <b>Marque au passage les messages du garage comme
     * lus</b> : ouvrir le fil, c est le lire. Une action distincte « marquer comme lu »
     * obligerait a un second geste pour un effet que la consultation vient de produire.
     */
    @Transactional
    public ConversationVue lire(String email, UUID reference) {
        Conversation fil = filDuMembre(email, reference);
        fil.marquerLuPar(RoleExpediteur.MEMBRE);
        return mapper.detail(fil, RoleExpediteur.MEMBRE, parametres.courants().zone());
    }

    // --- ecriture -----------------------------------------------------------------------

    /**
     * Ouvre un fil et y depose le premier message.
     *
     * @param referenceIntervention travaux concernes, ou {@code null} pour un fil libre
     * @throws RessourceIntrouvableException intervention inconnue ou d autrui
     */
    @Transactional
    public UUID ouvrir(String email, String sujet, String corps, UUID referenceIntervention) {
        Utilisateur membre = membre(email);
        Intervention intervention = referenceIntervention == null
                ? null : interventionDuMembre(email, referenceIntervention);

        Conversation fil = new Conversation(membre, intervention, sujet);
        fil.ajouter(membre, RoleExpediteur.MEMBRE, corps, horloge.instant());
        conversations.save(fil);
        prevenirLeGarage(fil.getSujet());
        return fil.getReference();
    }

    /** Repond dans un fil du membre. */
    @Transactional
    public void repondre(String email, UUID reference, String corps) {
        Conversation fil = filDuMembre(email, reference);
        fil.ajouter(membre(email), RoleExpediteur.MEMBRE, corps, horloge.instant());
        prevenirLeGarage(fil.getSujet());
    }

    // --- helpers privees -----------------------------------------------------------------

    private Conversation filDuMembre(String email, UUID reference) {
        Conversation fil = conversations.findByReferenceAvecMessages(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Conversation", reference));
        if (!fil.appartientA(email)) {
            throw new RessourceIntrouvableException("Conversation", reference);
        }
        return fil;
    }

    private Intervention interventionDuMembre(String email, UUID reference) {
        Intervention intervention = interventions.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Intervention", reference));
        boolean sienne = intervention.getRdv() != null
                && intervention.getRdv().getMembre().getEmail().equalsIgnoreCase(email);
        if (!sienne) {
            throw new RessourceIntrouvableException("Intervention", reference);
        }
        return intervention;
    }

    private Utilisateur membre(String email) {
        return membres.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
    }

    private void prevenirLeGarage(String sujet) {
        List<Utilisateur> administrateurs = membres.findByTypeUtilisateurAndStatut(
                TypeUtilisateur.ADMINISTRATEUR, StatutUtilisateur.ACTIF);
        if (administrateurs.isEmpty()) {
            log.warn("Message recu sur un fil sans administrateur actif a prevenir.");
            return;
        }
        administrateurs.forEach(administrateur ->
                notifications.deposer(administrateur, TypeNotification.MESSAGE_RECU, sujet));
    }
}
