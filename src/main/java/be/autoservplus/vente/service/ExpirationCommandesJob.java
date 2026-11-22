package be.autoservplus.vente.service;

import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.MotifAnnulationCommande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>RM-21</b> : une commande non payée sous {@link #DELAI_PAIEMENT} est annulée
 * (motif TIMEOUT_PAIEMENT). Rien n'est décrémenté : rien n'était réservé.
 *
 * <p><b>Écart à la règle du cahier des charges : l'annulation n'est plus prononcée à
 * trente minutes fixes.</b> Un paiement Bancontact reste valable une heure chez Mollie,
 * une carte quinze minutes, comptées depuis la création du paiement (documentation
 * Mollie, « Handling payment status », qui conseille d'attendre le statut plutôt que de
 * prédire l'expiration). Annuler à trente minutes devançait donc le prestataire, et un
 * paiement pouvait aboutir sur une commande déjà annulée. À l'échéance, le job lit
 * chaque tentative encore ouverte chez le prestataire, et décide sur cette lecture :</p>
 * <ul>
 *   <li>payée : même traitement que la notification, la commande passe PAYEE.</li>
 *   <li>ouverte et annulable : annulée chez le prestataire, puis la commande.</li>
 *   <li>ouverte non annulable, ou en cours de traitement : la commande attend le statut
 *       définitif, que la notification ou un passage suivant constatera.</li>
 *   <li>échouée, annulée ou expirée : la commande est annulée.</li>
 *   <li>prestataire injoignable : rien, le passage suivant relit.</li>
 * </ul>
 *
 * <p>Passé l'échéance, le membre peut encore finir la tentative ouverte mais plus en
 * créer une ({@code PaiementService.initierPaiement}) : l'attente est bornée par
 * l'expiration de cette tentative. Au-delà de {@link #ATTENTE_MAXIMALE} sans statut
 * définitif, le job alerte au journal et ne fait rien d'autre.</p>
 *
 * <p>La lecture chez le prestataire se fait hors transaction. La commande est ensuite
 * verrouillée et relue avant toute écriture : payée ou reprise entre-temps, elle est
 * laissée. Un paiement qui aboutirait malgré tout sur une commande annulée est
 * enregistré et signalé au garage par {@code PaiementService}.</p>
 *
 * <p>Le déclenchement est planifié. La date vient de l'horloge injectée : le job se
 * teste en appelant la méthode avec une horloge figée. Le premier passage attend une
 * minute, pour ne pas balayer la base au milieu du démarrage.</p>
 */
@Component
public class ExpirationCommandesJob {

    private static final Logger log = LoggerFactory.getLogger(ExpirationCommandesJob.class);

    /**
     * Délai de paiement d'une commande (RM-21). Constante unique du projet :
     * la rendre paramétrable (paramètre d'atelier) est une évolution prévue.
     */
    public static final Duration DELAI_PAIEMENT = Duration.ofMinutes(30);

    /**
     * Attente au-delà de laquelle une tentative sans statut définitif est signalée :
     * deux fois l'expiration la plus longue des moyens proposés, une heure pour
     * Bancontact ({@code MollieGateway.MOYENS_PROPOSES}).
     */
    public static final Duration ATTENTE_MAXIMALE = Duration.ofHours(2);

    private static final Set<StatutPaiement> OUVERTS =
            EnumSet.of(StatutPaiement.INITIE, StatutPaiement.EN_COURS);

    private final CommandeRepository commandes;
    private final PaiementRepository paiements;
    private final PrestatairePaiement prestataire;
    private final PaiementService paiementService;
    private final TransactionOperations transaction;
    private final Clock horloge;

    /**
     * Tentatives déjà signalées, pour qu'une attente anormale s'écrive une fois et non
     * à chaque minute. En mémoire : un redémarrage la signale de nouveau, ce qui ne
     * nuit pas.
     */
    private final Set<String> attentesSignalees = ConcurrentHashMap.newKeySet();

    public ExpirationCommandesJob(CommandeRepository commandes,
                                  PaiementRepository paiements,
                                  PrestatairePaiement prestataire,
                                  PaiementService paiementService,
                                  TransactionOperations transaction,
                                  Clock horloge) {
        this.commandes = commandes;
        this.paiements = paiements;
        this.prestataire = prestataire;
        this.paiementService = paiementService;
        this.transaction = transaction;
        this.horloge = horloge;
    }

    /** Une tentative ouverte, telle que la base la connaît avant la lecture. */
    private record TentativeOuverte(String reference, Instant dateInitiation) {
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void annulerLesCommandesExpirees() {
        Instant limite = horloge.instant().minus(DELAI_PAIEMENT);
        List<Long> candidates = transaction.execute(statut -> commandes
                .parStatutAnterieuresA(StatutCommande.EN_ATTENTE_PAIEMENT, limite).stream()
                .map(Commande::getId)
                .toList());
        for (Long id : candidates) {
            try {
                traiter(id);
            } catch (PrestataireIndisponibleException panne) {
                // Ni annulation ni attente décidée sur une lecture manquée : annuler
                // sans savoir est ce que ce job ne fait plus.
                log.warn("Commande {} : prestataire de paiement injoignable, échéance RM-21 "
                        + "revue au passage suivant ({}).", id, panne.getMessage());
            }
        }
    }

    private void traiter(Long commandeId) {
        List<TentativeOuverte> ouvertes = transaction.execute(statut -> commandes
                .findById(commandeId)
                .filter(commande -> commande.getStatut() == StatutCommande.EN_ATTENTE_PAIEMENT)
                .map(commande -> paiements.findByCommandeAndStatutIn(commande, OUVERTS).stream()
                        .filter(paiement -> aQuitteLeSite(paiement.getReferenceMollie()))
                        .map(paiement -> new TentativeOuverte(paiement.getReferenceMollie(),
                                paiement.getDateInitiation()))
                        .toList())
                .orElse(List.of()));

        boolean enAttente = false;
        for (TentativeOuverte tentative : ouvertes) {
            EtatPaiement etat = prestataire.lireEtat(tentative.reference());
            if (etat.statut() == StatutPaiement.INITIE && etat.annulable()) {
                // Fermée chez le prestataire avant la commande : plus aucun règlement
                // ne peut arriver sur cette page de paiement.
                etat = prestataire.annuler(tentative.reference());
            }
            paiementService.constater(tentative.reference(), etat);
            if (etat.statut() == StatutPaiement.REUSSI) {
                // La commande vient de passer PAYEE, ou l'était déjà.
                return;
            }
            if (etat.statut() == StatutPaiement.INITIE || etat.statut() == StatutPaiement.EN_COURS) {
                enAttente = true;
                signalerSiTropLongue(commandeId, tentative);
            }
        }
        if (!enAttente) {
            transaction.executeWithoutResult(statut -> annuler(commandeId));
        }
    }

    /** Une tentative sans référence du prestataire n'a jamais quitté le site. */
    private static boolean aQuitteLeSite(String reference) {
        return reference != null && !reference.isBlank();
    }

    private void signalerSiTropLongue(Long commandeId, TentativeOuverte tentative) {
        Instant plafond = tentative.dateInitiation().plus(ATTENTE_MAXIMALE);
        if (horloge.instant().isAfter(plafond) && attentesSignalees.add(tentative.reference())) {
            log.warn("Commande {} : le paiement {} n'a aucun statut définitif {} heures après "
                            + "sa création. La commande reste en attente, à vérifier chez le "
                            + "prestataire.",
                    commandeId, tentative.reference(), ATTENTE_MAXIMALE.toHours());
        }
    }

    /**
     * Annule la commande sous verrou, si elle attend toujours et qu'aucune tentative
     * partie chez le prestataire n'est restée ouverte : une tentative née entre la
     * lecture et le verrou n'a pas été lue, elle le sera au passage suivant.
     */
    private void annuler(Long commandeId) {
        Commande commande = commandes.verrouillerParId(commandeId).orElse(null);
        if (commande == null || commande.getStatut() != StatutCommande.EN_ATTENTE_PAIEMENT) {
            // Payée (ou disparue) entre la lecture et le verrou : le webhook a gagné.
            return;
        }
        List<Paiement> restantes = paiements.findByCommandeAndStatutIn(commande, OUVERTS);
        if (restantes.stream().anyMatch(paiement -> aQuitteLeSite(paiement.getReferenceMollie()))) {
            return;
        }
        Instant maintenant = horloge.instant();
        for (Paiement paiement : restantes) {
            paiement.expirer(maintenant);
            paiements.saveAndFlush(paiement);
        }
        commande.annuler(MotifAnnulationCommande.TIMEOUT_PAIEMENT, maintenant);
        commandes.saveAndFlush(commande);
        log.info("Commande {} annulée : non payée à l'échéance de {} minutes (RM-21).",
                commande.getNumero(), DELAI_PAIEMENT.toMinutes());
    }
}
