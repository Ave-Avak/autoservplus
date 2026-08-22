package be.autoservplus.vente.service;

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
 * PAYEE. Meme patron que {@code NotificationInterventionListener} : AFTER_COMMIT
 * — un rollback emporte l evenement, un courriel n annonce jamais un encaissement
 * non acquis — et exceptions du fournisseur avalees apres journal.
 *
 * <p>Cote garage : pas d infrastructure d adresse admin ni d ecran commandes a ce
 * stade — la notification est un journal structure, que le tableau de bord admin
 * a venir relaiera (le drapeau {@code rupture_a_honorer} y est deja persiste).</p>
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
        try {
            Commande commande = commandes.findByReference(evenement.referenceCommande())
                    .orElse(null);
            if (commande == null) {
                log.warn("Commande {} introuvable apres commit : confirmation non envoyee.",
                        evenement.referenceCommande());
                return;
            }
            Utilisateur membre = commande.getMembre();
            String montant = FormatageRdv.euros(commande.getMontantTvac());
            courriel.envoyerConfirmationPaiement(new DetailsPaiementCourriel(
                    membre.getEmail(), membre.getPrenom(), commande.getNumero(), montant));
            log.info("Commande {} payee ({}) : a preparer par le garage{}.",
                    commande.getNumero(), montant,
                    commande.isRuptureAHonorer() ? " — RUPTURE A HONORER" : "");
        } catch (RuntimeException e) {
            log.warn("Notification de paiement impossible pour la commande {} : {}",
                    evenement.referenceCommande(), e.getMessage());
        }
    }
}
