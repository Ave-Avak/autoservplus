package be.autoservplus.reservation.web.dto;

import be.autoservplus.reservation.domain.Vehicule;

import java.util.UUID;

/** Vue d un vehicule destinee a l affichage. */
public record VehiculeVue(
        UUID reference,
        String plaque,
        String marque,
        String modele,
        String motorisation,
        Short annee,
        Integer kilometrage,
        String numeroChassis,
        String designation) {

    public static VehiculeVue de(Vehicule vehicule) {
        return new VehiculeVue(
                vehicule.getReference(),
                vehicule.getPlaque(),
                vehicule.getMarque(),
                vehicule.getModele(),
                vehicule.getMotorisation().name(),
                vehicule.getAnnee(),
                vehicule.getKilometrage(),
                vehicule.getNumeroChassis(),
                vehicule.designation());
    }

    /** Motorisation en toutes lettres pour l affichage. */
    public String motorisationLisible() {
        return switch (motorisation) {
            case "ESSENCE" -> "Essence";
            case "DIESEL" -> "Diesel";
            case "HYBRIDE" -> "Hybride";
            case "ELECTRIQUE" -> "Électrique";
            case "GPL" -> "GPL";
            default -> "Autre";
        };
    }

    public String kilometrageLisible() {
        return kilometrage == null ? "Non renseigné" : "%,d km".formatted(kilometrage).replace(',', ' ');
    }
}