package be.autoservplus.intervention.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.service.dto.HistoriqueVehiculeAdmin;
import be.autoservplus.intervention.service.dto.HistoriqueVehiculeMembre;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Historique d'entretien par véhicule (F21), pour le membre et pour le garage.
 *
 * <p>Le rattachement passe par le véhicule et non par le rendez-vous : une intervention
 * née d'une commande de services n'a pas de rendez-vous, et elle fait partie de
 * l'historique au même titre. C'est le même choix que l'export F22.</p>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class HistoriqueEntretienService {

    /** Plus récente d'abord, le numéro départageant deux interventions du même instant. */
    private static final Comparator<Intervention> ANTICHRONOLOGIQUE =
            Comparator.comparing(Intervention::dateDeReference)
                    .thenComparing(Intervention::getNumero)
                    .reversed();

    private final VehiculeRepository vehicules;
    private final InterventionRepository interventions;
    private final ParametreAtelierRepository parametres;

    public HistoriqueEntretienService(VehiculeRepository vehicules,
                                      InterventionRepository interventions,
                                      ParametreAtelierRepository parametres) {
        this.vehicules = vehicules;
        this.interventions = interventions;
        this.parametres = parametres;
    }

    /**
     * Historique d'un véhicule de son propriétaire. Un véhicule d'autrui, inconnu ou
     * retiré du parc répond comme introuvable (404) : un refus (403) confirmerait à un
     * tiers que la référence existe.
     */
    @PreAuthorize("isAuthenticated()")
    public HistoriqueVehiculeMembre pourMembre(UUID vehiculeReference, String email) {
        Vehicule vehicule = vehicules.findByReference(vehiculeReference)
                .filter(v -> v.getMembre().getEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new RessourceIntrouvableException("Véhicule", vehiculeReference));
        return HistoriqueVehiculeMembre.de(vehicule.getReference(), designation(vehicule),
                interventionsDe(vehicule), parametres.courants().zone());
    }

    /** Historique complet pour le garage, véhicule retiré du parc compris. */
    public HistoriqueVehiculeAdmin pourGarage(UUID vehiculeReference) {
        Vehicule vehicule = vehicules.parReferenceRetiresCompris(vehiculeReference)
                .orElseThrow(() -> new RessourceIntrouvableException("Véhicule", vehiculeReference));
        return HistoriqueVehiculeAdmin.de(vehicule.getReference(), designation(vehicule),
                vehicule.getMembre().nomComplet(), vehicule.estSupprime(),
                interventionsDe(vehicule), parametres.courants().zone());
    }

    private List<Intervention> interventionsDe(Vehicule vehicule) {
        return interventions.historiqueDuVehicule(vehicule.getId()).stream()
                .sorted(ANTICHRONOLOGIQUE)
                .toList();
    }

    private static String designation(Vehicule v) {
        return v.getMarque() + " " + v.getModele() + " (" + v.getPlaque() + ")";
    }
}
