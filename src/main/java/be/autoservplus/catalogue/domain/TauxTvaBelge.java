package be.autoservplus.catalogue.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Taux de TVA belges admis au catalogue : 0, 6, 12 et 21 %.
 *
 * <p>Les contraintes {@code ck_service_tva} et {@code ck_piece_tva} en base
 * n imposent qu un intervalle 0..100 : la liste fermee des taux legaux est un
 * invariant applicatif, verrouille ici pour les deux entites du catalogue.
 * La comparaison passe par {@code compareTo} : « 21 », « 21.0 » et « 21.00 »
 * designent le meme taux quelle que soit l echelle du BigDecimal.</p>
 */
public final class TauxTvaBelge {

    /** Liste exposee aux formulaires d administration pour construire le choix. */
    public static final List<BigDecimal> TAUX_ADMIS = List.of(
            new BigDecimal("0.00"),
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
                    "Taux de TVA « %s » invalide : seuls 0, 6, 12 et 21 %% sont admis en Belgique."
                            .formatted(taux));
        }
        return taux;
    }
}
