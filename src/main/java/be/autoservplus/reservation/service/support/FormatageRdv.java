package be.autoservplus.reservation.service.support;

import org.springframework.context.i18n.LocaleContextHolder;

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
     * Montant au format belge francophone (« 49,00 € »), pour ce qui ne dépend pas de la
     * langue d'un écran : courriels, export comptable. Une nouvelle instance de
     * {@link NumberFormat} est créée à chaque appel : le type n'est pas thread-safe, et
     * le partager en constante introduirait un défaut latent en concurrence.
     */
    public static String euros(BigDecimal montant) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("fr-BE")).format(montant);
    }

    /**
     * Montant dans la langue donnée, deux décimales et le symbole après une espace
     * insécable, comme les prix du catalogue : « 49,00 € » en français et en néerlandais,
     * « 49.00 € » en anglais.
     */
    public static String euros(BigDecimal montant, Locale langue) {
        NumberFormat format = NumberFormat.getNumberInstance(langue);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(montant) + "\u00A0€";
    }

    /**
     * Montant dans la langue de l'écran qui se rend, lue dans le contexte de la requête.
     * Réservé aux vues construites pendant la requête : un courriel part dans la langue
     * de son destinataire, et non dans celle de l'écran qui l'a déclenché.
     */
    public static String eurosEcran(BigDecimal montant) {
        return euros(montant, LocaleContextHolder.getLocale());
    }
}
