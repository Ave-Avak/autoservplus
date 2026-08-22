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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/**
 * <b>RM-21</b> : une commande non payee sous {@link #DELAI_PAIEMENT} est annulee
 * automatiquement (motif TIMEOUT_PAIEMENT) et ses paiements non aboutis passent
 * EXPIRE. Rien n est decremente — rien n etait reserve.
 *
 * <p>Course contre un webhook « paid » tardif : chaque candidate est reverifiee
 * SOUS VERROU pessimiste avant d etre annulee — si le webhook a gagne, le statut
 * relu n est plus EN_ATTENTE_PAIEMENT et la commande est passee. Dans l autre
 * sens, un webhook qui arrive apres l annulation se heurte a la garde d entite
 * (une ANNULEE ne devient pas PAYEE) et acte l encaissement a rembourser.</p>
 *
 * <p>Le declenchement est planifie ; la DATE, elle, vient de l horloge injectee
 * — le job se teste en appelant directement la methode avec une horloge figee.
 * Le premier passage attend une minute (initialDelay), pour ne pas balayer la
 * base au milieu du demarrage.</p>
 */
@Component
public class ExpirationCommandesJob {

    private static final Logger log = LoggerFactory.getLogger(ExpirationCommandesJob.class);

    /**
     * Delai de paiement d une commande (RM-21). Constante unique du projet —
     * la rendre parametrable (parametre d atelier) est une evolution prevue.
     */
    public static final Duration DELAI_PAIEMENT = Duration.ofMinutes(30);

    private final CommandeRepository commandes;
    private final PaiementRepository paiements;
    private final Clock horloge;

    public ExpirationCommandesJob(CommandeRepository commandes,
                                  PaiementRepository paiements,
                                  Clock horloge) {
        this.commandes = commandes;
        this.paiements = paiements;
        this.horloge = horloge;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void annulerLesCommandesExpirees() {
        Instant limite = horloge.instant().minus(DELAI_PAIEMENT);
        for (Commande candidate : commandes.parStatutAnterieuresA(
                StatutCommande.EN_ATTENTE_PAIEMENT, limite)) {
            Commande commande = commandes.verrouillerParId(candidate.getId()).orElse(null);
            if (commande == null || commande.getStatut() != StatutCommande.EN_ATTENTE_PAIEMENT) {
                // Payee (ou disparue) entre le scan et le verrou : le webhook a gagne.
                continue;
            }
            commande.annuler(MotifAnnulationCommande.TIMEOUT_PAIEMENT, horloge.instant());
            for (Paiement paiement : paiements.findByCommandeAndStatutIn(commande,
                    EnumSet.of(StatutPaiement.INITIE, StatutPaiement.EN_COURS))) {
                paiement.expirer(horloge.instant());
                paiements.saveAndFlush(paiement);
            }
            commandes.saveAndFlush(commande);
            log.info("Commande {} annulee : non payee sous {} minutes (RM-21).",
                    commande.getNumero(), DELAI_PAIEMENT.toMinutes());
        }
    }
}
