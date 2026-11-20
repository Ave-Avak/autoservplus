package be.autoservplus.vente.web.dto;

import be.autoservplus.vente.domain.ModificationCommande;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Une ligne de l'historique d'une commande, pour son détail au back-office. Les dates de
 * remise, stockées au format ISO, reviennent en {@link LocalDate} pour que le gabarit
 * les rende dans la langue de l'écran.
 */
public record ModificationCommandeVue(LocalDateTime horodatage, LocalDate avant,
                                      LocalDate apres, String auteur) {

    public static ModificationCommandeVue de(ModificationCommande modification, ZoneId zone) {
        Instant instant = modification.getHorodatage();
        return new ModificationCommandeVue(
                LocalDateTime.ofInstant(instant, zone),
                date(modification.getValeurAvant()),
                date(modification.getValeurApres()),
                modification.getAuteur() == null ? null : modification.getAuteur().nomComplet());
    }

    private static LocalDate date(String iso) {
        return iso == null ? null : LocalDate.parse(iso);
    }
}
