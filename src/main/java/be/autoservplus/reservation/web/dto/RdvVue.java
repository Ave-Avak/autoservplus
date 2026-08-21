package be.autoservplus.reservation.web.dto;

import java.util.List;
import java.util.UUID;

public record RdvVue(
        UUID reference,
        String numero,
        String statut,
        String statutLisible,
        String jourLisible,
        String heureDebut,
        String heureFin,
        String vehicule,
        List<String> prestations,
        String montantTvac,
        String commentaire,
        String motifRefus,
        boolean annulable) {

    public boolean estActif() {
        return "EN_ATTENTE".equals(statut) || "CONFIRME".equals(statut);
    }
}