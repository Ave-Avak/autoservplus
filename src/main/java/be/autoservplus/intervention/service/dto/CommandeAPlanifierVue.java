package be.autoservplus.intervention.service.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Une commande de services payée en attente d'ouverture de dossier d'atelier (F12-b).
 *
 * @param vehicules parc du client au moment de l'affichage : le garage doit dire sur
 *                  quel véhicule il travaille, la colonne {@code vehicule_id} de
 *                  l'intervention étant {@code NOT NULL} et une prestation achetée en
 *                  ligne n'étant rattachée à aucun véhicule
 */
public record CommandeAPlanifierVue(
        UUID reference,
        String numero,
        LocalDateTime date,
        String client,
        List<String> prestations,
        List<VehiculeChoisissable> vehicules,
        boolean dejaPlanifiee,
        String numeroIntervention) {

    /** Le client n'a aucun véhicule enregistré : rien à proposer au garage. */
    public boolean sansVehicule() {
        return vehicules.isEmpty();
    }

    public record VehiculeChoisissable(UUID reference, String libelle) {
    }
}
