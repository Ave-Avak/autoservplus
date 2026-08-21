package be.autoservplus.reservation.service.support;

import be.autoservplus.reservation.domain.StatutRdv;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatage d affichage des rendez-vous, partage entre les couches UI et courriel.
 *
 * <p>Centralise les regles de presentation (fuseau applique par l appelant, locale
 * belge pour les montants, francais pour les jours) : ainsi l ecran admin, l ecran
 * membre et le courriel produisent la meme chaine pour la meme donnee, sans risque
 * de divergence subtile.</p>
 */
public final class FormatageRdv {

    // DateTimeFormatter est immuable et thread-safe, contrairement a NumberFormat.
    private static final DateTimeFormatter FORMAT_JOUR =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter FORMAT_HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private FormatageRdv() {
        // utilitaire non instanciable
    }

    public static String jourLisible(Instant instant, ZoneId zone) {
        return FORMAT_JOUR.format(instant.atZone(zone));
    }

    public static String heureLisible(Instant instant, ZoneId zone) {
        return FORMAT_HEURE.format(instant.atZone(zone));
    }

    /**
     * Formate un montant en euros au format belge (« 49,00 € »). Une nouvelle
     * instance de {@link NumberFormat} est creee a chaque appel car le type n est
     * pas thread-safe : le partager en constante statique introduirait un bug
     * latent en concurrence.
     */
    public static String euros(BigDecimal montant) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("fr-BE")).format(montant);
    }

    public static String statutLisible(StatutRdv statut) {
        return switch (statut) {
            case EN_ATTENTE -> "En attente de confirmation";
            case CONFIRME -> "Confirmé";
            case REFUSE -> "Refusé par le garage";
            case ANNULE -> "Annulé";
            case HONORE -> "Effectué";
            case ABSENT -> "Non présenté";
        };
    }
}
