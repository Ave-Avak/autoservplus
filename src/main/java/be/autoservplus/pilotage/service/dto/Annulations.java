package be.autoservplus.pilotage.service.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rendez-vous de la période qui n'ont pas eu lieu, et le taux d'annulation (A25).
 *
 * <p>Le taux rapporte les rendez-vous annulés, par le membre ou par le garage, au total
 * des rendez-vous de la période, tous statuts confondus. Les absences et les demandes
 * refusées par le garage sont comptées à part : un client qui ne vient pas et une
 * demande déclinée ne sont pas des annulations, et les mêler au taux empêcherait de
 * lire ce que chacun dit de l'activité.</p>
 *
 * @param total       rendez-vous de la période, tous statuts confondus
 * @param parLeMembre annulations demandées par le membre
 * @param parLeGarage annulations décidées par le garage, toujours motivées
 */
public record Annulations(long total, long parLeMembre, long parLeGarage, long absents, long refuses) {

    public long annules() {
        return parLeMembre + parLeGarage;
    }

    /**
     * Taux d'annulation en pourcentage entier, nul sans rendez-vous sur la période :
     * « 0 % » annoncerait une période sans annulation alors qu'elle est sans rendez-vous.
     */
    public Integer taux() {
        if (total <= 0) {
            return null;
        }
        return BigDecimal.valueOf(annules()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP).intValue();
    }
}
