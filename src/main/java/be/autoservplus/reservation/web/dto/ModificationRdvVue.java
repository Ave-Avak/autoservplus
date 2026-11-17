package be.autoservplus.reservation.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ce que l'écran de déplacement montre du rendez-vous : son créneau actuel, la version
 * lue, renvoyée par le formulaire pour refuser un envoi fait sur une page périmée, et le
 * mode de confirmation, qui dit au membre ce que le déplacement va produire. Le jour
 * est {@code jourActuel}, formaté par l'écran dans la langue de l'interface.
 */
public record ModificationRdvVue(
        UUID reference,
        String numero,
        String heureDebut,
        String heureFin,
        long version,
        boolean confirmationAutomatique,
        LocalDate jourActuel) {
}
