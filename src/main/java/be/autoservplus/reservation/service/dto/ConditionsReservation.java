package be.autoservplus.reservation.service.dto;

/** Conditions annoncées par le formulaire de réservation. */
public record ConditionsReservation(boolean confirmationAutomatique, int delaiReponseHeures,
                                    String telephoneGarage) {
}
