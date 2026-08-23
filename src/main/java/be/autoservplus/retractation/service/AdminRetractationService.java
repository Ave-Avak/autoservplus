package be.autoservplus.retractation.service;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.AvoirService;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.repository.DemandeAnnulationRepository;
import be.autoservplus.retractation.web.dto.DemandeAnnulationVueAdmin;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.service.DemandeRemboursement;
import be.autoservplus.vente.service.PrestatairePaiement;
import be.autoservplus.vente.service.RemboursementCree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Decision du garage sur une demande de retractation (F30, RM-23) : validation —
 * remboursement, note de credit, changements d etat — ou refus motive.
 *
 * <p><b>Pourquoi ce service vit dans {@code retractation} et non dans {@code vente}
 * ou {@code facturation}.</b> Valider une retractation touche les deux : il faut
 * rembourser un paiement (vente) et contre-passer une facture (facturation). Le
 * projet a deja tranche que la vente ignore la facturation — c est la facture qui
 * nait de la commande, jamais l inverse. Loger l orchestration dans l un des deux
 * modules creerait donc la dependance que ce choix evitait. Un module dedie, qui
 * depend des deux sans qu aucun ne depende de lui, laisse la fleche dans le bon
 * sens.</p>
 *
 * <p><b>Tout dans une transaction, l appel externe en dernier.</b> Avoir emis,
 * commande basculee, demande tranchee, puis seulement l appel au prestataire, puis
 * l ecriture de la reference de remboursement. Si le prestataire refuse, tout est
 * annule : aucun avoir, aucun numero consomme dans la suite legale, aucune commande
 * remboursee sans argent rendu. Reste la fenetre inverse — le prestataire accepte
 * mais le commit echoue : le remboursement a eu lieu, la base n en sait rien. C est
 * la que sert la cle d idempotence <b>derivee du paiement</b> : rejouer la validation
 * envoie la meme cle, le prestataire reconnait le mouvement et ne rend pas l argent
 * deux fois. Une cle tiree au hasard aurait double le remboursement.</p>
 *
 * <p><b>Idempotence a trois etages</b> contre le double-clic et les deux
 * administrateurs simultanes : {@code @Version} sur la demande fait perdre le
 * second, l index unique {@code uq_avoir_facture} refuse un second avoir si le
 * verrou est passe, et la cle d idempotence protege le prestataire si tout le reste
 * a echoue. Chaque etage rattrape ce que le precedent ne peut pas voir.</p>
 *
 * <p>{@code @PreAuthorize} de classe en defense en profondeur : la protection d URL
 * {@code /admin/**} filtre deja le role, mais elle ne protege que les appels qui
 * passent par une URL. Meme patron que {@code AdminRdvService} et
 * {@code AdminCatalogueService}.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminRetractationService {

    private static final Logger log = LoggerFactory.getLogger(AdminRetractationService.class);

    private final DemandeAnnulationRepository demandes;
    private final CommandeRepository commandes;
    private final PaiementRepository paiements;
    private final FactureRepository factures;
    private final AvoirService avoirs;
    private final PrestatairePaiement prestataire;
    private final UtilisateurRepository utilisateurs;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    public AdminRetractationService(DemandeAnnulationRepository demandes,
                                    CommandeRepository commandes,
                                    PaiementRepository paiements,
                                    FactureRepository factures,
                                    AvoirService avoirs,
                                    PrestatairePaiement prestataire,
                                    UtilisateurRepository utilisateurs,
                                    ApplicationEventPublisher evenements,
                                    Clock horloge) {
        this.demandes = demandes;
        this.commandes = commandes;
        this.paiements = paiements;
        this.factures = factures;
        this.avoirs = avoirs;
        this.prestataire = prestataire;
        this.utilisateurs = utilisateurs;
        this.evenements = evenements;
        this.horloge = horloge;
    }

    // --- file de traitement ------------------------------------------------------------

    /** Demandes en attente de decision, de la plus ancienne a la plus recente. */
    public List<DemandeAnnulationVueAdmin> demandesEnAttente() {
        ZoneId zone = horloge.getZone();
        Instant maintenant = horloge.instant();
        return demandes.enAttente().stream()
                .map(demande -> DemandeAnnulationVueAdmin.de(demande, zone, maintenant))
                .toList();
    }

    /** Une demande precise, pour l ecran de refus motive. */
    public DemandeAnnulationVueAdmin vue(UUID reference) {
        return DemandeAnnulationVueAdmin.de(charger(reference), horloge.getZone(), horloge.instant());
    }

    // --- decisions ---------------------------------------------------------------------

    /**
     * Accepte la retractation : la facture est contre-passee par une note de credit,
     * le paiement est rembourse chez le prestataire, la commande passe REMBOURSEE.
     *
     * @throws RessourceIntrouvableException reference de demande inconnue
     * @throws RegleMetierException          la commande n a pas de facture ou pas de
     *                                       paiement encaisse a rembourser
     * @throws ConflitConcurrenceException   un autre traitement a tranche cette demande
     * @throws IllegalStateException         la demande n est plus en attente
     */
    @Transactional
    public DemandeAnnulation valider(UUID reference, String emailAdministrateur) {
        DemandeAnnulation demande = charger(reference);
        // Fail-fast avant tout effet de bord : sans lui, un second clic irait
        // jusqu au remboursement pour echouer sur « aucun paiement encaisse ».
        demande.exigerEnAttente();
        Utilisateur administrateur = administrateur(emailAdministrateur);
        Instant maintenant = horloge.instant();

        // Verrou pessimiste sur la commande : la table n a pas de colonne version, et
        // ce verrou serialise cette validation avec un eventuel webhook ou job
        // d expiration qui viserait la meme ligne. Le second lecteur voit alors l etat
        // deja change et la garde de l entite tranche, au lieu d ecraser l autre.
        Commande commande = commandes.verrouillerParId(demande.getCommande().getId())
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Commande", demande.getCommande().getReference()));

        Facture facture = factures.findByCommande(commande)
                .orElseThrow(() -> new RegleMetierException(
                        "RM-23", ("La commande %s n a pas de facture : rien a contre-passer. "
                                + "L emission a echoue ou n a pas encore eu lieu.")
                                .formatted(commande.getNumero())));
        Paiement paiement = paiementEncaisse(commande);

        // Avoir d abord : il consomme un numero de la suite legale, et c est
        // l operation la plus susceptible d echouer sur une garde (idempotence,
        // coherence des montants). Mieux vaut echouer avant d avoir rendu l argent.
        Avoir avoir = avoirs.contrePasser(facture, Avoir.MOTIF_RETRACTATION);

        commande.rembourser(maintenant);
        demande.valider(avoir, administrateur, maintenant);
        try {
            commandes.saveAndFlush(commande);
            demandes.saveAndFlush(demande);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflitConcurrenceException(
                    "Cette demande vient d etre traitee par un autre administrateur.");
        }

        // Appel externe en DERNIER : un refus du prestataire annule tout ce qui
        // precede, y compris le numero d avoir, rendu au compteur.
        RemboursementCree remboursement = prestataire.rembourser(new DemandeRemboursement(
                paiement.getReferenceMollie(), paiement.getMontant(), paiement.getDevise(),
                paiement.cleIdempotenceRemboursement()));
        paiement.rembourser(remboursement.referenceRemboursement());
        paiements.saveAndFlush(paiement);

        log.info("Retractation {} validee : commande {} remboursee ({} EUR), avoir {}, refund {}.",
                demande.getReference(), commande.getNumero(), paiement.getMontant(),
                avoir.getNumero(), remboursement.referenceRemboursement());
        evenements.publishEvent(new DecisionRetractationEvent(demande.getReference()));
        return demande;
    }

    /**
     * Oppose une exception au droit de retractation (piece montee, deballee, abimee).
     *
     * <p>Rien n est rembourse, aucun avoir n est emis, la facture reste seule au
     * dossier — un refus ne produit aucun mouvement comptable. Le motif est
     * obligatoire, l entite le refuse s il est vide : c est le professionnel qui doit
     * se justifier quand il refuse, pas le consommateur quand il demande.</p>
     */
    @Transactional
    public DemandeAnnulation refuser(UUID reference, String motif, String emailAdministrateur) {
        DemandeAnnulation demande = charger(reference);
        demande.exigerEnAttente();
        demande.refuser(motif, administrateur(emailAdministrateur), horloge.instant());
        try {
            demandes.saveAndFlush(demande);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflitConcurrenceException(
                    "Cette demande vient d etre traitee par un autre administrateur.");
        }
        log.info("Retractation {} refusee sur la commande {}.",
                demande.getReference(), demande.getCommande().getNumero());
        evenements.publishEvent(new DecisionRetractationEvent(demande.getReference()));
        return demande;
    }

    // --- helpers ------------------------------------------------------------------------

    /**
     * Le paiement encaisse de la commande. Une commande peut porter plusieurs
     * paiements — chaque nouvelle tentative en cree un — mais un seul a abouti :
     * c est celui-la qu on rembourse. Son absence signale une incoherence (commande
     * PAYEE sans paiement REUSSI) qui doit remonter, pas etre contournee.
     */
    private Paiement paiementEncaisse(Commande commande) {
        List<Paiement> encaisses =
                paiements.findByCommandeAndStatutIn(commande, List.of(StatutPaiement.REUSSI));
        if (encaisses.isEmpty()) {
            throw new RegleMetierException("RM-23",
                    "Aucun paiement encaisse sur la commande %s : rien a rembourser."
                            .formatted(commande.getNumero()));
        }
        return encaisses.get(0);
    }

    private DemandeAnnulation charger(UUID reference) {
        return demandes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Demande d annulation", reference));
    }

    private Utilisateur administrateur(String email) {
        return utilisateurs.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RessourceIntrouvableException("Utilisateur", email));
    }
}
