package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.web.dto.PaiementApresAnnulationVue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Paiements encaissés sur une commande déjà annulée : leur liste, et leur
 * remboursement par un administrateur.
 *
 * <p>Le remboursement n'est pas automatique. Rendre de l'argent reste le geste d'une
 * personne, qui peut d'abord joindre le client : il a payé une commande que le garage
 * peut encore vouloir honorer autrement. Le geste est tracé sur le paiement, avec son
 * auteur et sa date, et le journal d'audit du back-office le lit de là.</p>
 *
 * <p>Aucune pièce comptable n'accompagne ce remboursement : une commande annulée n'a
 * jamais été facturée, il n'y a donc rien à contre-passer par une note de crédit.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class RemboursementApresAnnulationService {

    private static final Logger log =
            LoggerFactory.getLogger(RemboursementApresAnnulationService.class);

    private final PaiementRepository paiements;
    private final PrestatairePaiement prestataire;
    private final ParametreAtelierRepository parametres;
    private final ApplicationEventPublisher evenements;
    private final Clock horloge;

    public RemboursementApresAnnulationService(PaiementRepository paiements,
                                               PrestatairePaiement prestataire,
                                               ParametreAtelierRepository parametres,
                                               ApplicationEventPublisher evenements,
                                               Clock horloge) {
        this.paiements = paiements;
        this.prestataire = prestataire;
        this.parametres = parametres;
        this.evenements = evenements;
        this.horloge = horloge;
    }

    /** Les paiements à rembourser d'abord, puis ceux qui l'ont été, du plus récent au plus ancien. */
    public List<PaiementApresAnnulationVue> tous() {
        ZoneId zone = parametres.courants().zone();
        return paiements.recusApresAnnulation().stream()
                .map(paiement -> PaiementApresAnnulationVue.de(paiement, zone))
                .toList();
    }

    /**
     * Le paiement à rembourser, pour l'écran qui demande confirmation.
     *
     * @throws RefusTraduit si le paiement est déjà remboursé
     */
    public PaiementApresAnnulationVue aConfirmer(UUID referencePaiement) {
        Paiement paiement = paiements.findByReference(referencePaiement)
                .filter(Paiement::estRecuApresAnnulation)
                .orElseThrow(() -> new RessourceIntrouvableException("Paiement", referencePaiement));
        exigerARembourser(paiement);
        return PaiementApresAnnulationVue.de(paiement, parametres.courants().zone());
    }

    /**
     * Rembourse le paiement chez le prestataire et le trace.
     *
     * <p>Un seul remboursement par paiement, par trois gardes. Le paiement est
     * verrouillé puis relu : deux demandes simultanées se suivent, et la seconde le
     * trouve remboursé avant tout appel. Son statut est vérifié avant l'appel au
     * prestataire. La clé d'idempotence dérivée du paiement, enfin, fait rendre au
     * prestataire le même remboursement si la réponse se perd et que l'administrateur
     * recommence.</p>
     *
     * <p>L'appel au prestataire se fait dans la transaction, comme pour une
     * rétractation : un refus annule l'écriture, et le paiement reste à rembourser.
     * Le verrou est donc tenu pendant l'appel, borné par le délai de la passerelle.</p>
     *
     * @param auteur adresse de l'administrateur connecté, lue du contexte de sécurité
     * @throws RefusTraduit si le paiement est déjà remboursé
     */
    @Transactional
    public void rembourser(UUID referencePaiement, String auteur) {
        Paiement paiement = paiements.verrouillerParReference(referencePaiement)
                .filter(Paiement::estRecuApresAnnulation)
                .orElseThrow(() -> new RessourceIntrouvableException("Paiement", referencePaiement));
        exigerARembourser(paiement);
        RemboursementCree remboursement = prestataire.rembourser(new DemandeRemboursement(
                paiement.getReferenceMollie(), paiement.getMontant(), paiement.getDevise(),
                paiement.cleIdempotenceRemboursement()));
        paiement.rembourserApresAnnulation(remboursement.referenceRemboursement(),
                horloge.instant(), auteur);
        paiements.saveAndFlush(paiement);
        // Le numéro de commande et la référence du remboursement, pas l'adresse de
        // l'auteur : elle est sur la ligne du paiement, pas dans les journaux.
        log.info("Paiement {} de la commande {} remboursé après annulation (remboursement {}).",
                paiement.getReference(), paiement.getCommande().getNumero(),
                remboursement.referenceRemboursement());
        evenements.publishEvent(new RemboursementApresAnnulationEvent(paiement.getReference()));
    }

    private static void exigerARembourser(Paiement paiement) {
        if (!paiement.estARembourser()) {
            throw new RefusTraduit("admin.paiements-tardifs.erreur.deja-rembourse",
                    "Ce paiement est déjà remboursé.");
        }
    }
}
