package be.autoservplus.intervention.service;

import be.autoservplus.catalogue.service.dto.PrestationRetenueVue;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.dto.CommandeAPlanifierVue;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Lecture des commandes de services à planifier (F12-b).
 *
 * <p>Séparée de {@link InterventionService}, qui porte le cycle de vie d'une
 * intervention : ici on ne fait que préparer l'écran de création. L'écriture, elle,
 * reste dans {@code InterventionService.creerDepuisCommande} avec le reste des
 * invariants F17.</p>
 *
 * <p>{@code @PreAuthorize} de classe : ces écrans montrent le nom des clients et le
 * détail de leurs achats.</p>
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
     * Commandes de services payées, dossier ouvert ou non.
     *
     * <p>Celles déjà planifiées restent affichées, marquées comme telles : les retirer
     * ferait disparaître l'information au moment où le garage cherche à vérifier qu'il
     * n'a rien oublié.</p>
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
                LocalDateTime.ofInstant(commande.getDateCommande(), zone),
                commande.getMembre().getPrenom() + " " + commande.getMembre().getNom(),
                commandes.lignesServiceDe(commande).stream()
                        .map(LignePanier::getLibelleFige)
                        .toList(),
                commandes.lignesServiceDe(commande).stream()
                        .map(l -> PrestationRetenueVue.de(l.getLibelleFige(), l.getChoixPieces()))
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
