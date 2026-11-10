package be.autoservplus.reservation.service.dto;

import java.util.UUID;

/**
 * Diagnostic RM-29 présenté avant de retirer un poste, sur le patron du catalogue :
 * suppression définitive si aucun rendez-vous ne le référence, désactivation sinon,
 * jamais les deux. Le service reste l'arbitre au POST.
 */
public record PropositionRetraitPoste(UUID reference, String libelle, long nombreRendezVous, boolean actif) {

    public boolean suppressionPossible() {
        return nombreRendezVous == 0;
    }
}
