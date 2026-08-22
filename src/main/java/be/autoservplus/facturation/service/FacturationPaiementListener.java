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
 * Emet la facture des qu une commande passe a PAYEE (F31).
 *
 * <p>{@code AFTER_COMMIT}, comme {@code NotificationInterventionListener} et
 * {@code NotificationPaiementListener} : une facture ne peut pas exister pour un
 * encaissement qui n a pas ete committe. Si la transaction de paiement est annulee,
 * l evenement meurt avec elle et aucun numero n est consomme.</p>
 *
 * <p>Difference avec les deux precedents : ce listener <b>ecrit</b>. Sa transaction
 * {@code REQUIRES_NEW} n est donc pas en lecture seule, et son echec a des
 * consequences comptables — d ou le journal en ERROR et non en WARN. Le paiement,
 * lui, reste acquis : on ne rembourse pas un client parce qu un PDF a manque. La
 * reprise (rejeu de l emission pour une commande payee sans facture) releve d un
 * traitement de reconciliation, hors perimetre de ce bloc.</p>
 *
 * <p>Le listener vit dans {@code facturation}, module qui depend deja de
 * {@code vente} (la facture nait d une commande) : l inverse ferait dependre la
 * vente de la facturation pour un evenement qu elle publie deja.</p>
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
            // Course entre deux emissions simultanees : l index partiel
            // uq_facture_commande a refuse la seconde. La facture existe, ecrite par
            // l autre transaction ; celle-ci est annulee, son numero rendu au
            // compteur. Rien a reprendre.
            log.info("Facture de la commande {} deja emise par un traitement concurrent.",
                    evenement.referenceCommande());
        } catch (RuntimeException e) {
            log.error("Emission de la facture impossible pour la commande {} : {}. "
                            + "L encaissement reste acquis, la facture est a emettre.",
                    evenement.referenceCommande(), e.getMessage(), e);
        }
    }
}
