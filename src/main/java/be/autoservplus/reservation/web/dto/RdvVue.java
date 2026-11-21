package be.autoservplus.reservation.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Rendez-vous vu par son membre. Le jour reste une date et le statut un code : le
 * gabarit les rend dans la langue de l'écran, ce qu'une chaîne formatée par le service
 * empêchait.
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
        String montantTvac,
        String commentaire,
        String motifRefus,
        boolean annulable,
        boolean modifiable) {

    public boolean estActif() {
        return "EN_ATTENTE".equals(statut) || "CONFIRME".equals(statut);
    }
}