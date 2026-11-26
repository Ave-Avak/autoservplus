package be.autoservplus.catalogue.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Taux de TVA belges admis au catalogue : 6, 12 et 21 %.
 *
 * <p>Le taux nul existe en droit belge, mais une ligne exemptée oblige la facture à
 * citer la disposition qui justifie l'exemption (AR n° 1 du 29/12/1992, art. 5), et
 * l'application ne l'imprime pas. Il est donc refusé au catalogue. Les contraintes
 * {@code ck_service_tva} et {@code ck_piece_tva} (V130) tiennent la même liste en base.
 * La comparaison passe par {@code compareTo} : « 21 », « 21.0 » et « 21.00 »
 * désignent le même taux quelle que soit l'échelle du BigDecimal.</p>
 */
public final class TauxTvaBelge {

    /** Liste exposee aux formulaires d administration pour construire le choix. */
    public static final List<BigDecimal> TAUX_ADMIS = List.of(
            new BigDecimal("6.00"),
            new BigDecimal("12.00"),
            new BigDecimal("21.00"));

    private TauxTvaBelge() {
        // classe utilitaire
    }

    public static boolean estAdmis(BigDecimal taux) {
        return taux != null && TAUX_ADMIS.stream().anyMatch(admis -> admis.compareTo(taux) == 0);
    }

    /**
     * @return le taux tel quel s il est admis
     * @throws IllegalArgumentException pour tout taux hors de la liste belge
     */
    public static BigDecimal verifier(BigDecimal taux) {
        if (!estAdmis(taux)) {
            throw new IllegalArgumentException(
                    "Taux de TVA « %s » invalide : seuls 6, 12 et 21 %% sont admis au catalogue."
                            .formatted(taux));
        }
        return taux;
    }
}
