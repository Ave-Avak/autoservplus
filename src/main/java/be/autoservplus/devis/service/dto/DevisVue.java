package be.autoservplus.devis.service.dto;

import be.autoservplus.devis.domain.Devis;
import be.autoservplus.reservation.service.support.FormatageRdv;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Ligne de la liste « Mes devis » : date d'émission au fuseau du garage, formatée par le
 * gabarit dans la langue de l'écran, total dans la même langue.
 */
public record DevisVue(
        UUID reference,
        String numero,
        LocalDateTime dateEmission,
        LocalDate valableJusquAu,
        boolean expire,
        String vehicule,
        String totalTvac) {

    public static DevisVue de(Devis devis, ZoneId zone, LocalDate aujourdhui) {
        return new DevisVue(
                devis.getReference(),
                devis.getNumero(),
                LocalDateTime.ofInstant(devis.getDateEmission(), zone),
                devis.getValableJusquAu(),
                devis.estExpireLe(aujourdhui),
                devis.vehiculeDesignation(),
                FormatageRdv.eurosEcran(devis.getMontantTvac()));
    }
}
