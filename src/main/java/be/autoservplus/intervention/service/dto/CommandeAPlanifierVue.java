package be.autoservplus.intervention.service.dto;

import java.util.List;
import java.util.UUID;

/**
 * Une commande de services payee en attente d ouverture de dossier d atelier (F12-b).
 *
 * @param vehicules parc du client au moment de l affichage : le garage doit dire sur
 *                  quel vehicule il travaille, la colonne {@code vehicule_id} de
 *                  l intervention etant {@code NOT NULL} et une prestation achetee en
 *                  ligne n etant rattachee a aucun vehicule
 */
public record CommandeAPlanifierVue(
        UUID reference,
        String numero,
        String date,
        String client,
        List<String> prestations,
        List<VehiculeChoisissable> vehicules,
        boolean dejaPlanifiee,
        String numeroIntervention) {

    /** Le client n a aucun vehicule enregistre : rien a proposer au garage. */
    public boolean sansVehicule() {
        return vehicules.isEmpty();
    }

    public record VehiculeChoisissable(UUID reference, String libelle) {
    }
}
