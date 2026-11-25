package be.autoservplus.reservation.web.dto;

import be.autoservplus.catalogue.service.dto.PrestationRetenueVue;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Rendez-vous vu par son membre. Le jour reste une date et le statut un code : le
 * gabarit les rend dans la langue de l'écran, ce qu'une chaîne formatée par le service
 * empêchait. {@code lignes} reprend les prestations avec la mention du choix des pièces,
 * pour le détail, les listes s'en tenant aux libellés.
 */
public record RdvVue(
        UUID reference,
        String numero,
        String statut,
        LocalDate jour,
        String heureDebut,
        String heureFin,
        String vehicule,
        List<String> prestations,
        List<PrestationRetenueVue> lignes,
        String montantTvac,
        String commentaire,
        String motifRefus,
        boolean annulable,
        boolean modifiable) {

    public boolean estActif() {
        return "EN_ATTENTE".equals(statut) || "CONFIRME".equals(statut);
    }
}