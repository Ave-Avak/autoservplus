package be.autoservplus.vente.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.web.dto.SimulationPaiementVue;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Page de paiement bouchonnee : ce que {@link PrestatairePaiementFictif} ne peut
 * pas fournir, faute d etre un site web.
 *
 * <p><b>Pourquoi cette classe existe.</b> Le bouchon rendait depuis toujours une
 * URL {@code /paiement-fictif/{reference}} vers laquelle le controleur de commande
 * redirige — mais rien ne servait cette adresse. Le membre qui cliquait
 * « Procéder au paiement » tombait sur un 404, et la chaine marchande n etait
 * achevable que par un appel HTTP direct au webhook, ce que seuls les tests
 * faisaient. Le parcours etait donc vert en integration et impraticable a
 * l ecran.</p>
 *
 * <p><b>Le chemin emprunte est le VRAI chemin.</b> La simulation ne prend aucun
 * raccourci : elle pose le statut chez le prestataire, exactement comme un
 * encaissement reel le ferait, puis laisse {@link PaiementService#traiterNotification}
 * relire ce statut et decider. Rien ici n ecrit dans la commande, ne decremente le
 * stock ni ne publie d evenement. Une simulation qui appellerait directement les
 * transitions du domaine validerait un chemin que la production n emprunte pas —
 * et c est precisement le defaut qu elle repare.</p>
 *
 * <p>Active partout sauf en production, comme le bouchon dont elle est la face
 * visible : les deux vivent et meurent ensemble.</p>
 */
@Service
@Profile("!prod")
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

    /** Ce que le membre doit pouvoir verifier avant de payer : numero et montant. */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SimulationPaiementVue aRegler(String referencePrestataire, String email) {
        return SimulationPaiementVue.de(paiementDuMembre(referencePrestataire, email));
    }

    /**
     * Simule l issue choisie et retourne la reference de la commande, pour y ramener
     * le membre.
     *
     * <p><b>Volontairement NON transactionnelle.</b> Elle doit laisser
     * {@code traiterNotification} ouvrir la sienne : ouverte ici en lecture seule
     * (convention du projet), la transaction englobante empecherait l ecriture ;
     * ouverte en ecriture, elle placerait le verrou pessimiste sur la commande hors
     * du perimetre que le service a concu. Les deux lectures qui l encadrent se
     * suffisent d une transaction chacune.</p>
     *
     * @param reussite issue a simuler : encaissement accepte, ou refuse par la banque
     */
    @PreAuthorize("isAuthenticated()")
    public UUID simuler(String referencePrestataire, boolean reussite, String email) {
        Paiement paiement = paiementDuMembre(referencePrestataire, email);
        UUID referenceCommande = paiement.getCommande().getReference();
        // L ordre est celui du monde reel : le statut change CHEZ le prestataire,
        // puis la notification survient. L inverse ferait relire un statut qui n a
        // pas encore bouge, et la notification ne constaterait rien.
        prestataire.programmerStatut(referencePrestataire,
                reussite ? StatutPaiement.REUSSI : StatutPaiement.ECHOUE);
        paiementService.traiterNotification(referencePrestataire);
        return referenceCommande;
    }

    /**
     * Un membre ne pilote que ses propres paiements. Reference inconnue et paiement
     * d autrui repondent la meme chose — 404, jamais 403, comme les autres
     * ressources nominatives du projet : un 403 confirmerait l existence de la
     * ressource a qui n y a pas droit.
     *
     * <p>Sans {@code @Transactional} — appelee en interne, elle ne passerait pas par
     * le proxy et l annotation serait un mensonge. Elle n en a pas besoin :
     * {@code findByReferenceMollie} charge la commande et le membre par JOIN FETCH,
     * donc les deux dereferencements ci-dessous tiennent hors session.</p>
     */
    private Paiement paiementDuMembre(String referencePrestataire, String email) {
        Paiement paiement = paiements.findByReferenceMollie(referencePrestataire)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Paiement", referencePrestataire));
        if (!paiement.getCommande().getMembre().getEmail().equalsIgnoreCase(email)) {
            throw new RessourceIntrouvableException("Paiement", referencePrestataire);
        }
        return paiement;
    }
}
