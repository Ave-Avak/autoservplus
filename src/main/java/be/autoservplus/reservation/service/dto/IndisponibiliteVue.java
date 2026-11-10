package be.autoservplus.reservation.service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Indisponibilité vue du back-office, bornes en heure locale du garage. Sans poste,
 * elle ferme tout l'atelier.
 */
public record IndisponibiliteVue(
        UUID reference,
        UUID posteReference,
        String posteLibelle,
        LocalDateTime debut,
        LocalDateTime fin,
        String motif) {

    public boolean concerneToutLAtelier() {
        return posteReference == null;
    }
}
