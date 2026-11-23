package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.vente.service.CommandePayeeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Émet la facture dès qu'une commande passe à PAYEE (F31).
 *
 * <p>{@code AFTER_COMMIT}, comme {@code NotificationInterventionListener} et
 * {@code NotificationPaiementListener} : une facture ne peut pas exister pour un
 * encaissement qui n'a pas été committé. Si la transaction de paiement est annulée,
 * l'événement meurt avec elle et aucun numéro n'est consommé.</p>
 *
 * <p>Différence avec les deux précédents : ce listener <b>écrit</b>. Sa transaction
 * {@code REQUIRES_NEW} n'est donc pas en lecture seule, et son échec a des
 * conséquences comptables : d'où le journal en ERROR et non en WARN. Le paiement,
 * lui, reste acquis : on ne rembourse pas un client parce qu'un PDF a manqué. La
 * reprise (rejeu de l'émission pour une commande payée sans facture) relève d'un
 * traitement de réconciliation, hors périmètre de ce bloc.</p>
 *
 * <p>Le listener vit dans {@code facturation}, module qui dépend déjà de
 * {@code vente} (la facture naît d'une commande) : l'inverse ferait dépendre la
 * vente de la facturation pour un événement qu'elle publie déjà.</p>
 */
@Component
public class FacturationPaiementListener {

    private static final Logger log = LoggerFactory.getLogger(FacturationPaiementListener.class);

    private final FactureService factures;

    public FacturationPaiementListener(FactureService factures) {
        this.factures = factures;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void surCommandePayee(CommandePayeeEvent evenement) {
        try {
            Facture facture = factures.emettrePourCommande(evenement.referenceCommande());
            log.info("Facture {} disponible pour la commande {}.",
                    facture.getNumero(), evenement.referenceCommande());
        } catch (DataIntegrityViolationException e) {
            // Course entre deux émissions simultanées : l'index partiel
            // uq_facture_commande a refusé la seconde. La facture existe, écrite par
            // l'autre transaction. Celle-ci est annulée, son numéro rendu au
            // compteur. Rien à reprendre.
            log.info("Facture de la commande {} deja emise par un traitement concurrent.",
                    evenement.referenceCommande());
        } catch (RuntimeException e) {
            log.error("Emission de la facture impossible pour la commande {} : {}. "
                            + "L encaissement reste acquis, la facture est a emettre.",
                    evenement.referenceCommande(), e.getMessage(), e);
        }
    }
}
