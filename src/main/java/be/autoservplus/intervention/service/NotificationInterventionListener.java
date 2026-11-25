package be.autoservplus.intervention.service;

import be.autoservplus.communication.service.DetailsInterventionTerminee;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.service.support.FormatageRdv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie le courriel de cloture (F17) quand une intervention passe a TERMINEE.
 *
 * <p>{@code AFTER_COMMIT} : le courriel n annonce jamais un etat non acquis, si la
 * transaction de {@code terminer} est rollbackee, l evenement meurt avec elle. C est
 * la lecon tiree de {@code AdminRdvService.notifierSansEchouer} (envoi inline, protege
 * seulement tant que l envoi ne touche pas la base) : ici le decouplage est structurel,
 * pas seulement defensif. {@code REQUIRES_NEW} ouvre la transaction de lecture : la
 * transaction d origine est terminee, on ne peut pas s y joindre.</p>
 *
 * <p>Le listener vit dans le module {@code intervention}, pas {@code communication} :
 * ce dernier ne depend d aucun module metier (principe des records Details*), alors
 * que l intervention depend deja du courriel.</p>
 */
@Component
public class NotificationInterventionListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationInterventionListener.class);

    private final InterventionRepository interventions;
    private final FactureRepository factures;
    private final ServiceCourriel courriel;

    public NotificationInterventionListener(InterventionRepository interventions,
                                            FactureRepository factures,
                                            ServiceCourriel courriel) {
        this.interventions = interventions;
        this.factures = factures;
        this.courriel = courriel;
    }

    /**
     * Recharge l intervention par reference (l evenement ne transporte pas l entite)
     * et previent le membre. Toute {@link RuntimeException} est avalee apres journal :
     * la cloture est committee, un fournisseur mail en panne ne doit ni la faire
     * echouer ni faire retomber une 500 sur l ecran de l admin.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void surInterventionTerminee(InterventionTermineeEvent evenement) {
        try {
            Intervention it = interventions.findByReference(evenement.referenceIntervention())
                    .orElse(null);
            if (it == null) {
                log.warn("Intervention {} introuvable apres commit : courriel de cloture non envoye.",
                        evenement.referenceIntervention());
                return;
            }
            // titulaire() couvre les deux origines depuis F12-b, un service acheté en ligne
            // ayant un titulaire même sans rendez-vous, et l'entrée directe au garage, dont
            // le titulaire est le propriétaire du véhicule.
            Utilisateur membre = it.titulaire();
            if (membre == null) {
                log.warn("Intervention {} terminee sans titulaire : aucun membre a notifier.",
                        it.getNumero());
                return;
            }
            Vehicule vehicule = it.getVehicule();
            // Facture d'atelier émise à la clôture (A15, RM-17), s'il y en a une.
            Facture facture = factures.findByIntervention(it).orElse(null);
            courriel.envoyerInterventionTerminee(new DetailsInterventionTerminee(
                    membre.getEmail(),
                    membre.getPrenom(),
                    membre.getLangue(),
                    it.getNumero(),
                    vehicule.getMarque() + " " + vehicule.getModele(),
                    vehicule.getPlaque(),
                    // BL-4 : le courriel invitait a deposer un avis alors qu aucun
                    // ecran ne le permettait. Il porte desormais le chemin reel.
                    "/mes-avis/" + it.getReference() + "/nouveau",
                    // Montant rédigé dans la langue du compte, comme les autres courriels au membre.
                    facture == null ? null : FormatageRdv.euros(facture.getMontantTvac(),
                            LanguesSupportees.duCompte(membre.getLangue())),
                    facture == null ? null : "/factures/" + facture.getReference() + "/pdf"));
        } catch (RuntimeException e) {
            log.warn("Envoi du courriel de cloture impossible pour l intervention {} : {}",
                    evenement.referenceIntervention(), e.getMessage());
        }
    }
}
