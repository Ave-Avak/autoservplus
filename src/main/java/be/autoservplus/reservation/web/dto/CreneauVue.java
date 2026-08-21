package be.autoservplus.reservation.web.dto;

/**
 * Heure de depart presentee au membre. La valeur est l instant ISO renvoye par le
 * formulaire ; le libelle est en heure locale du garage.
 */
public record CreneauVue(String valeur, String libelle, int postesLibres) {
}