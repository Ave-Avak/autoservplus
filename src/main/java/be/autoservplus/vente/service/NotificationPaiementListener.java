package be.autoservplus.vente.service;

import be.autoservplus.communication.service.DetailsCommandeGarage;
import be.autoservplus.communication.service.DetailsPaiementCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Previent le membre (recu par courriel) et le garage quand une commande passe a
 * PAYEE. Meme patron que {@code NotificationInterventionListener} : AFTER_COMMIT :
 * un rollback emporte l evenement, un courriel n annonce jamais un encaissement
 * non acquis, et exceptions du fournisseur avalees apres journal.
 *
 * <p>Côté garage, un courriel à l'adresse de l'atelier dit ce que la commande porte et
 * si une rupture est à honorer. La notification aux administrateurs part de
 * {@code NotificationEvenementListener}. Les deux envois sont indépendants : un échec
 * de l'un n'empêche pas l'autre.</p>
 */
@Component
public class NotificationPaiementListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationPaiementListener.class);

    private final CommandeRepository commandes;
    private final ServiceCourriel courriel;

    public NotificationPaiementListener(CommandeRepository commandes, ServiceCourriel courriel) {
        this.commandes = commandes;
        this.courriel = courriel;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void surCommandePayee(CommandePayeeEvent evenement) {
        Commande commande = commandes.findByReference(evenement.referenceCommande()).orElse(null);
        if (commande == null) {
            log.warn("Commande {} introuvable apres commit : confirmation non envoyee.",
                    evenement.referenceCommande());
            return;
        }
        Utilisateur membre = commande.getMembre();
        String montant = FormatageRdv.euros(commande.getMontantTvac());
        try {
            courriel.envoyerConfirmationPaiement(new DetailsPaiementCourriel(
                    membre.getEmail(), membre.getPrenom(), membre.getLangue(), commande.getNumero(),
                    montant));
        } catch (RuntimeException e) {
            log.warn("Confirmation de paiement impossible pour la commande {} : {}",
                    commande.getNumero(), e.getMessage());
        }
        try {
            courriel.envoyerAvisCommandeAuGarage(new DetailsCommandeGarage(
                    commande.getNumero(), montant, membre.nomComplet(),
                    !commandes.lignesServiceDe(commande).isEmpty(), commande.isRuptureAHonorer()));
        } catch (RuntimeException e) {
            log.warn("Avis au garage impossible pour la commande {} : {}",
                    commande.getNumero(), e.getMessage());
        }
        log.info("Commande {} payee ({}) : a preparer par le garage{}.",
                commande.getNumero(), montant,
                commande.isRuptureAHonorer() ? ", RUPTURE A HONORER" : "");
    }
}
