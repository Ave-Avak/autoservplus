package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.web.dto.SimulationPaiementVue;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Page de paiement bouchonnée : ce que {@link PrestatairePaiementFictif} ne peut
 * pas fournir, faute d'être un site web.
 *
 * <p><b>Pourquoi cette classe existe.</b> Le bouchon rendait depuis toujours une
 * URL {@code /paiement-fictif/{reference}} vers laquelle le contrôleur de commande
 * redirige, mais rien ne servait cette adresse. Le membre qui cliquait
 * « Procéder au paiement » tombait sur un 404, et la chaîne marchande n'était
 * achevable que par un appel HTTP direct au webhook, ce que seuls les tests
 * faisaient. Le parcours était donc vert en intégration et impraticable à
 * l'écran.</p>
 *
 * <p><b>Le chemin emprunté est le VRAI chemin.</b> La simulation ne prend aucun
 * raccourci : elle pose le statut chez le prestataire, exactement comme un
 * encaissement réel le ferait, puis laisse {@link PaiementService#traiterNotification}
 * relire ce statut et décider. Rien ici n'écrit dans la commande, ne décrémente le
 * stock ni ne publie d'événement. Une simulation qui appellerait directement les
 * transitions du domaine validerait un chemin que la production n'emprunte pas,
 * et c'est précisément le défaut qu'elle répare.</p>
 *
 * <p>Active sous la condition exacte du bouchon dont elle est la face visible
 * ({@link SiAucunPrestataireConfigure}) : les deux vivent et meurent ensemble.</p>
 */
@Service
@SiAucunPrestataireConfigure
public class SimulationPaiementService {

    private final PaiementRepository paiements;
    private final PrestatairePaiementFictif prestataire;
    private final PaiementService paiementService;

    public SimulationPaiementService(PaiementRepository paiements,
                                     PrestatairePaiementFictif prestataire,
                                     PaiementService paiementService) {
        this.paiements = paiements;
        this.prestataire = prestataire;
        this.paiementService = paiementService;
    }

    /** Ce que le membre doit pouvoir vérifier avant de payer : numéro et montant. */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SimulationPaiementVue aRegler(String referencePrestataire, String email) {
        return SimulationPaiementVue.de(paiementDuMembre(referencePrestataire, email));
    }

    /**
     * Simule l'issue choisie et retourne la référence de la commande, pour y ramener
     * le membre.
     *
     * <p><b>Volontairement NON transactionnelle.</b> Elle doit laisser
     * {@code traiterNotification} ouvrir la sienne : ouverte ici en lecture seule
     * (convention du projet), la transaction englobante empêcherait l'écriture.
     * Ouverte en écriture, elle placerait le verrou pessimiste sur la commande hors
     * du périmètre que le service a conçu. Les deux lectures qui l'encadrent se
     * suffisent d'une transaction chacune.</p>
     *
     * @param issue issue à simuler : encaissement accepté, refusé par la banque, ou en
     *              attente de confirmation
     */
    @PreAuthorize("isAuthenticated()")
    public UUID simuler(String referencePrestataire, IssueSimulee issue, String email) {
        Paiement paiement = paiementDuMembre(referencePrestataire, email);
        UUID referenceCommande = paiement.getCommande().getReference();
        // L'ordre est celui du monde réel : le statut change CHEZ le prestataire,
        // puis la notification survient. L'inverse ferait relire un statut qui n'a
        // pas encore bougé, et la notification ne constaterait rien.
        if (issue == IssueSimulee.OUVERT_NON_ANNULABLE) {
            // Rien ne change chez le prestataire, donc aucune notification : le
            // paiement reste ouvert, et le job RM-21 ne pourra pas l'annuler.
            prestataire.programmerAnnulable(referencePrestataire, false);
            return referenceCommande;
        }
        prestataire.programmerStatut(referencePrestataire, issue.statut());
        paiementService.traiterNotification(referencePrestataire);
        return referenceCommande;
    }

    /**
     * Issues offertes par la page de paiement simulé. L'attente reproduit un virement
     * ou une autorisation de carte : le prestataire a reçu l'ordre, sans page de
     * paiement à rouvrir, et le membre ne doit pas pouvoir payer une seconde fois.
     */
    public enum IssueSimulee {
        REUSSITE(StatutPaiement.REUSSI),
        ECHEC(StatutPaiement.ECHOUE),
        ATTENTE(StatutPaiement.EN_COURS),
        /**
         * Le client est parti chez sa banque sans avoir fini : le paiement reste ouvert
         * et le prestataire refuse de l'annuler. À l'échéance RM-21, la commande attend
         * alors le statut définitif au lieu d'être annulée.
         */
        OUVERT_NON_ANNULABLE(StatutPaiement.INITIE);

        private final StatutPaiement statut;

        IssueSimulee(StatutPaiement statut) {
            this.statut = statut;
        }

        StatutPaiement statut() {
            return statut;
        }
    }

    /**
     * Un membre ne pilote que ses propres paiements. Référence inconnue et paiement
     * d'autrui répondent la même chose, 404, jamais 403, comme les autres
     * ressources nominatives du projet : un 403 confirmerait l'existence de la
     * ressource à qui n'y a pas droit.
     *
     * <p>Sans {@code @Transactional} : appelée en interne, elle ne passerait pas par
     * le proxy et l'annotation serait un mensonge. Elle n'en a pas besoin :
     * {@code findByReferenceMollie} charge la commande et le membre par JOIN FETCH,
     * donc les deux déréférencements ci-dessous tiennent hors session.</p>
     */
    private Paiement paiementDuMembre(String referencePrestataire, String email) {
        Paiement paiement = paiements.findByReferenceMollie(referencePrestataire)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Paiement", referencePrestataire));
        if (!paiement.getCommande().getMembre().getEmail().equals(email)) {
            throw new RessourceIntrouvableException("Paiement", referencePrestataire);
        }
        return paiement;
    }
}
