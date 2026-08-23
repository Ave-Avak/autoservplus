package be.autoservplus.notification.service;

import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.InterventionTermineeEvent;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.reservation.domain.StatutRdv;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.service.RdvStatutModifieEvent;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;
import be.autoservplus.retractation.repository.DemandeAnnulationRepository;
import be.autoservplus.retractation.service.DecisionRetractationEvent;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.service.CommandePayeeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Transforme les evenements metier en notifications in-app (BL-6).
 *
 * <p><b>AFTER_COMMIT, comme tous les listeners du projet.</b> Une notification annonce
 * un fait acquis : un rollback doit l emporter avec lui, sans quoi le membre verrait
 * « commande payee » pour un encaissement annule.</p>
 *
 * <p><b>Toute exception est avalee apres journal.</b> Le patron est celui de
 * {@code NotificationPaiementListener} : la transaction metier est deja committee et
 * ne peut plus etre defaite, faire remonter l echec ne rendrait service a personne. Une
 * notification manquante est un desagrement ; une transition de rendez-vous perdue est
 * un incident. Le courriel, lui, part par son propre chemin et n est pas affecte.</p>
 *
 * <p><b>Journalisation sans donnee personnelle</b> : seules les references techniques
 * et les numeros metier sont traces, jamais l adresse ni le nom du membre.</p>
 */
@Component
public class NotificationEvenementListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEvenementListener.class);

    private final NotificationService notifications;
    private final RdvRepository rdvs;
    private final CommandeRepository commandes;
    private final InterventionRepository interventions;
    private final DemandeAnnulationRepository demandes;

    public NotificationEvenementListener(NotificationService notifications,
                                         RdvRepository rdvs,
                                         CommandeRepository commandes,
                                         InterventionRepository interventions,
                                         DemandeAnnulationRepository demandes) {
        this.notifications = notifications;
        this.rdvs = rdvs;
        this.commandes = commandes;
        this.interventions = interventions;
        this.demandes = demandes;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void surStatutRdv(RdvStatutModifieEvent evenement) {
        deposer("rendez-vous", evenement.referenceRdv(), () -> {
            var rdv = rdvs.findByReference(evenement.referenceRdv()).orElse(null);
            if (rdv == null) {
                return false;
            }
            TypeNotification type = typePour(rdv.getStatut());
            // EN_ATTENTE n est pas une transition decidee par le garage mais l etat de
            // depart d une demande : le membre vient de la deposer, lui notifier son
            // propre geste n apprend rien.
            if (type == null) {
                return false;
            }
            notifications.deposer(rdv.getMembre(), type, rdv.getNumero());
            return true;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void surCommandePayee(CommandePayeeEvent evenement) {
        deposer("commande", evenement.referenceCommande(), () -> {
            var commande = commandes.findByReference(evenement.referenceCommande()).orElse(null);
            if (commande == null) {
                return false;
            }
            notifications.deposer(commande.getMembre(), TypeNotification.COMMANDE_PAYEE,
                    commande.getNumero());
            return true;
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void surInterventionTerminee(InterventionTermineeEvent evenement) {
        deposer("intervention", evenement.referenceIntervention(), () -> {
            var intervention = interventions.findByReference(evenement.referenceIntervention())
                    .orElse(null);
            if (intervention == null) {
                return false;
            }
            notifications.deposer(intervention.getRdv().getMembre(),
                    TypeNotification.INTERVENTION_TERMINEE, intervention.getNumero());
            return true;
        });
    }

    /**
     * Decision de retractation (F30). L evenement couvre les deux issues ; le libelle
     * se choisit sur le statut committe. Une validation porte le <b>numero d avoir</b>,
     * qui est ce que le membre doit retrouver ; un refus porte le numero de commande,
     * puisqu aucun avoir n a ete emis. Le <b>motif du refus n est pas notifie</b> : il
     * est redige librement par l administrateur et reste sur l ecran authentifie.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void surDecisionRetractation(DecisionRetractationEvent evenement) {
        deposer("retractation", evenement.referenceDemande(), () -> {
            var demande = demandes.findByReference(evenement.referenceDemande()).orElse(null);
            if (demande == null) {
                return false;
            }
            var membre = demande.getCommande().getMembre();
            if (demande.getStatut() == StatutDemandeAnnulation.VALIDEE && demande.getAvoir() != null) {
                notifications.deposer(membre, TypeNotification.AVOIR_EMIS,
                        demande.getAvoir().getNumero());
                return true;
            }
            if (demande.getStatut() == StatutDemandeAnnulation.REFUSEE) {
                notifications.deposer(membre, TypeNotification.RETRACTATION_REFUSEE,
                        demande.getCommande().getNumero());
                return true;
            }
            return false;
        });
    }

    private static TypeNotification typePour(StatutRdv statut) {
        return switch (statut) {
            case CONFIRME -> TypeNotification.RDV_CONFIRME;
            case REFUSE -> TypeNotification.RDV_REFUSE;
            case ANNULE -> TypeNotification.RDV_ANNULE;
            case HONORE -> TypeNotification.RDV_HONORE;
            case ABSENT -> TypeNotification.RDV_ABSENT;
            case EN_ATTENTE -> null;
        };
    }

    /**
     * Enveloppe commune : journalise l absence de cible et absorbe toute exception. Le
     * {@link Supplier} rend {@code false} quand il n y avait rien a notifier, ce qui se
     * distingue d un echec.
     */
    private void deposer(String objet, UUID reference, Supplier<Boolean> depot) {
        try {
            if (Boolean.FALSE.equals(depot.get())) {
                log.debug("Aucune notification a deposer pour {} {}.", objet, reference);
            }
        } catch (RuntimeException e) {
            log.warn("Notification in-app impossible pour {} {} : {}",
                    objet, reference, e.getMessage());
        }
    }
}
