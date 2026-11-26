package be.autoservplus.reservation.service.dto;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Créneaux libres des prochains jours, tels que l'API publique les expose (F43).
 *
 * <p>Ni poste ni nombre de postes libres : un tiers apprend qu'une heure est réservable,
 * pas comment l'atelier est occupé.</p>
 *
 * @param duree durée réservée, arrondie au pas de l'atelier
 * @param zone  fuseau du garage, dans lequel les heures s'affichent
 */
public record DisponibilitesPubliques(
        Duration duree,
        ZoneId zone,
        LocalDate premierJour,
        LocalDate dernierJour,
        List<Creneau> creneaux) {

    public record Creneau(Instant debut, Instant fin) {
    }
}
