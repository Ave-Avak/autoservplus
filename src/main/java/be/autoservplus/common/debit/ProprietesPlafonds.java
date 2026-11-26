package be.autoservplus.common.debit;

import be.autoservplus.common.debit.SeauxParCle.Limite;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites de chaque surface plafonnée, lues sous {@code autoservplus.plafonds}.
 *
 * @param clesMax  nombre de clés gardées en mémoire par surface
 * @param surfaces limites par nom de surface, celui de {@link Plafonne}
 */
@ConfigurationProperties("autoservplus.plafonds")
public record ProprietesPlafonds(
        @DefaultValue("100000") int clesMax,
        Map<String, Surface> surfaces) {

    public ProprietesPlafonds {
        surfaces = surfaces == null ? Map.of() : Map.copyOf(surfaces);
    }

    /**
     * @param par      ce qui est compté. {@code COMPTE} retombe sur l'IP pour un visiteur
     *                 anonyme
     * @param limites  toutes s'appliquent, la plus stricte l'emporte
     */
    public record Surface(Par par, List<Limite> limites) {
    }

    public enum Par { COMPTE, IP }
}
