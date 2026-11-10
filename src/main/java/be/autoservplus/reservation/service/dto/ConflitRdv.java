package be.autoservplus.reservation.service.dto;

import be.autoservplus.reservation.domain.StatutRdv;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rendez-vous actif qu'une modification du planning laisse hors des heures d'ouverture,
 * sur un poste désactivé ou dans une indisponibilité (A24).
 *
 * <p>Le rendez-vous reste intact : la liste sert au traitement manuel par le garage.
 * Sérialisable parce qu'elle voyage en attribut flash, dans la session, entre le POST
 * et la page affichée après la redirection. Heures en heure locale du garage.</p>
 */
public record ConflitRdv(
        UUID reference,
        String numero,
        LocalDateTime debut,
        LocalDateTime fin,
        String membre,
        String poste,
        String prestations,
        StatutRdv statut) implements Serializable {
}
