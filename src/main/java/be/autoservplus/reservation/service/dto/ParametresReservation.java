package be.autoservplus.reservation.service.dto;

/** Paramètres de réservation réglables au back-office, en heures. */
public record ParametresReservation(boolean confirmationAutomatique, int delaiReponseHeures,
                                    int delaiMinimalHeures, int delaiAnnulationHeures,
                                    int delaiRappelHeures) {
}
