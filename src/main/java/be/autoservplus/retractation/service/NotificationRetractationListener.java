package be.autoservplus.retractation.service;

import be.autoservplus.communication.service.DetailsRetractationCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;
import be.autoservplus.retractation.repository.DemandeAnnulationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Previent le membre de la decision prise sur sa demande de retractation (F30).
 *
 * <p>{@code AFTER_COMMIT}, comme {@code NotificationPaiementListener} et
 * {@code FacturationPaiementListener} : un courriel n annonce jamais un remboursement
 * qui pourrait encore etre annule par un rollback. C est d autant plus vrai ici que
 * la validation appelle un prestataire externe — si ce dernier refuse le Refund, la
 * transaction entiere tombe, et l evenement meurt avec elle plutot que d annoncer au
 * membre un argent qu il ne recevra pas.</p>
 *
 * <p>Ce patron, et non l envoi inline d {@code AdminRdvService} : l administrateur
 * doit voir sa validation reussir meme si le fournisseur de courriel est en panne,
 * et cette transaction-ci ecrit beaucoup (avoir, commande, demande, paiement) — un
 * envoi inline qui echouerait dans la transaction risquerait de l emporter avec lui.
 * La divergence entre les deux patrons est documentee au projet ; c est le cas
 * ecrivant qui justifie AFTER_COMMIT.</p>
 *
 * <p>Exceptions du fournisseur avalees apres journal : le remboursement est fait, la
 * note de credit est emise, et le membre retrouve les deux dans « Mes commandes ».
 * Un courriel manquant ne doit pas laisser croire que la decision a echoue.</p>
 */
@Component
public class NotificationRetractationListener {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationRetractationListener.class);

    private final DemandeAnnulationRepository demandes;
    private final ServiceCourriel courriel;

    public NotificationRetractationListener(DemandeAnnulationRepository demandes,
                                            ServiceCourriel courriel) {
        this.demandes = demandes;
        this.courriel = courriel;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void surDecision(DecisionRetractationEvent evenement) {
        try {
            DemandeAnnulation demande = demandes.findByReference(evenement.referenceDemande())
                    .orElse(null);
            if (demande == null) {
                log.warn("Demande {} introuvable apres commit : decision non notifiee.",
                        evenement.referenceDemande());
                return;
            }
            Utilisateur membre = demande.getCommande().getMembre();
            boolean acceptee = demande.getStatut() == StatutDemandeAnnulation.VALIDEE;
            courriel.envoyerDecisionRetractation(new DetailsRetractationCourriel(
                    membre.getEmail(),
                    membre.getPrenom(),
                    demande.getCommande().getNumero(),
                    acceptee ? FormatageRdv.euros(demande.getCommande().getMontantTvac()) : null,
                    acceptee,
                    acceptee ? demande.getAvoir().getNumero() : null,
                    demande.getMotifDecision()));
        } catch (RuntimeException e) {
            log.warn("Notification de la decision de retractation {} impossible : {}",
                    evenement.referenceDemande(), e.getMessage());
        }
    }
}
