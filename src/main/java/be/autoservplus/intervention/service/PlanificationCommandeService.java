package be.autoservplus.intervention.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.dto.CommandeAPlanifierVue;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Lecture des commandes de services a planifier (F12-b).
 *
 * <p>Separee de {@link InterventionService}, qui porte le cycle de vie d une
 * intervention : ici on ne fait que preparer l ecran de creation. L ecriture, elle,
 * reste dans {@code InterventionService.creerDepuisCommande} avec le reste des
 * invariants F17.</p>
 *
 * <p>{@code @PreAuthorize} de classe : ces ecrans montrent le nom des clients et le
 * detail de leurs achats.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class PlanificationCommandeService {

    private final CommandeRepository commandes;
    private final InterventionRepository interventions;
    private final VehiculeRepository vehicules;
    private final ParametreAtelierRepository parametres;

    public PlanificationCommandeService(CommandeRepository commandes,
                                        InterventionRepository interventions,
                                        VehiculeRepository vehicules,
                                        ParametreAtelierRepository parametres) {
        this.commandes = commandes;
        this.interventions = interventions;
        this.vehicules = vehicules;
        this.parametres = parametres;
    }

    /**
     * Commandes de services payees, dossier ouvert ou non.
     *
     * <p>Celles deja planifiees restent affichees, marquees comme telles : les retirer
     * ferait disparaitre l information au moment ou le garage cherche a verifier qu il
     * n a rien oublie.</p>
     */
    public List<CommandeAPlanifierVue> commandesAPlanifier() {
        ZoneId zone = parametres.courants().zone();
        return commandes.payeesAvecService().stream()
                .map(commande -> vue(commande, zone))
                .toList();
    }

    public CommandeAPlanifierVue detail(UUID reference) {
        Commande commande = commandes.findByReference(reference)
                .orElseThrow(() -> new RessourceIntrouvableException("Commande", reference));
        if (commande.getStatut() != StatutCommande.PAYEE) {
            throw new RessourceIntrouvableException("Commande", reference);
        }
        return vue(commande, parametres.courants().zone());
    }

    private CommandeAPlanifierVue vue(Commande commande, ZoneId zone) {
        var existante = interventions.findByCommandeId(commande.getId());
        return new CommandeAPlanifierVue(
                commande.getReference(),
                commande.getNumero(),
                FormatageRdv.jourLisible(commande.getDateCommande(), zone),
                commande.getMembre().getPrenom() + " " + commande.getMembre().getNom(),
                commandes.lignesServiceDe(commande).stream()
                        .map(LignePanier::getLibelleFige)
                        .toList(),
                vehicules.findByMembre(commande.getMembre().getEmail()).stream()
                        .map(v -> new CommandeAPlanifierVue.VehiculeChoisissable(
                                v.getReference(),
                                "%s %s (%s)".formatted(v.getMarque(), v.getModele(), v.getPlaque())))
                        .toList(),
                existante.isPresent(),
                existante.map(be.autoservplus.intervention.domain.Intervention::getNumero)
                        .orElse(null));
    }
}
