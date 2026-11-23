package be.autoservplus.vente.service;

import be.autoservplus.communication.service.DetailsCommandeGarage;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.i18n.LanguesSupportees;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Prévient le membre (confirmation du contrat par courriel, art. VI.46, § 7, CDE) et
 * le garage quand une commande passe à PAYEE. Même patron que {@code NotificationInterventionListener} : AFTER_COMMIT :
 * un rollback emporte l'événement, un courriel n'annonce jamais un encaissement
 * non acquis, et exceptions du fournisseur avalées après journal.
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
    private final ConfirmationCommande confirmation;
    private final PaiementRepository paiements;

    public NotificationPaiementListener(CommandeRepository commandes, ServiceCourriel courriel,
                                        ConfirmationCommande confirmation,
                                        PaiementRepository paiements) {
        this.paiements = paiements;
        this.commandes = commandes;
        this.courriel = courriel;
        this.confirmation = confirmation;
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
            courriel.envoyerConfirmationCommande(confirmation.composer(commande));
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

    /**
     * Paiement encaissé sur une commande déjà annulée : le membre apprend que sa
     * commande reste annulée et qu'il sera remboursé, l'atelier qu'un remboursement
     * l'attend. Deux envois indépendants, dans la langue du membre pour le premier.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void surPaiementApresAnnulation(PaiementApresAnnulationEvent evenement) {
        Paiement paiement = paiements.findByReference(evenement.referencePaiement()).orElse(null);
        if (paiement == null) {
            log.warn("Paiement {} introuvable après commit : avis de paiement reçu après "
                    + "annulation non envoyés.", evenement.referencePaiement());
            return;
        }
        Commande commande = paiement.getCommande();
        // L'avis au garage reste au format du site, celui du membre suit sa langue.
        String montant = FormatageRdv.euros(paiement.getMontant());
        try {
            courriel.envoyerAvisPaiementApresAnnulation(commande.getMembre(),
                    commande.getNumero(), FormatageRdv.euros(paiement.getMontant(),
                            LanguesSupportees.duCompte(commande.getMembre().getLangue())));
        } catch (RuntimeException e) {
            log.warn("Avis au membre impossible pour le paiement reçu après l'annulation de "
                    + "la commande {} : {}", commande.getNumero(), e.getMessage());
        }
        try {
            courriel.envoyerAvisPaiementARembourserAuGarage(commande.getNumero(), montant,
                    commande.getMembre().nomComplet());
        } catch (RuntimeException e) {
            log.warn("Avis au garage impossible pour le paiement reçu après l'annulation de "
                    + "la commande {} : {}", commande.getNumero(), e.getMessage());
        }
    }

    /** Le remboursement est demandé au prestataire : le membre en est prévenu. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void surRemboursementApresAnnulation(RemboursementApresAnnulationEvent evenement) {
        Paiement paiement = paiements.findByReference(evenement.referencePaiement()).orElse(null);
        if (paiement == null) {
            log.warn("Paiement {} introuvable après commit : avis de remboursement non envoyé.",
                    evenement.referencePaiement());
            return;
        }
        Commande commande = paiement.getCommande();
        try {
            courriel.envoyerAvisRemboursementApresAnnulation(commande.getMembre(),
                    commande.getNumero(), FormatageRdv.euros(paiement.getMontant(),
                            LanguesSupportees.duCompte(commande.getMembre().getLangue())));
        } catch (RuntimeException e) {
            log.warn("Avis de remboursement impossible pour la commande {} : {}",
                    commande.getNumero(), e.getMessage());
        }
    }
}
