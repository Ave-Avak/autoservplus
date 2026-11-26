package be.autoservplus.common.debit;

import be.autoservplus.common.debit.ProprietesPlafonds.Surface;
import be.autoservplus.common.debit.SeauxParCle.Decision;
import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Seaux de chaque surface plafonnée, construits une fois depuis la configuration.
 *
 * <p>Carte pleine : la demande passe, avec un avertissement au journal. Refuser toute clé
 * nouvelle bloquerait les clients légitimes dès qu'un tiers aurait rempli la carte, ce qui
 * ferait du plafond le déni de service qu'il combat. Remplir cent mille clés dans la
 * période d'une surface demande autant de comptes ou de réseaux /64 distincts.</p>
 */
@Component
public class PlafondsRequetes {

    private static final Logger JOURNAL = LoggerFactory.getLogger(PlafondsRequetes.class);

    private final Map<String, Surface> surfaces;
    private final Map<String, SeauxParCle> seaux;

    public PlafondsRequetes(ProprietesPlafonds proprietes, Clock horloge) {
        this.surfaces = proprietes.surfaces();
        this.seaux = surfaces.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                surface -> new SeauxParCle(surface.getValue().limites(), proprietes.clesMax(), horloge)));
    }

    /** Ce que la surface compte. Une surface absente de la configuration est une erreur de code. */
    public ProprietesPlafonds.Par par(String surface) {
        return configuree(surface).par();
    }

    /**
     * Consomme une demande de la clé sur la surface.
     *
     * @throws PlafondDepasseException si le plafond est atteint
     */
    public void consommer(String surface, String cle) {
        configuree(surface);
        Decision decision = seaux.get(surface).consommer(cle);
        if (decision.saturee()) {
            JOURNAL.warn("Plafond {} : carte des clés pleine, demande servie sans comptage.", surface);
            return;
        }
        if (!decision.acceptee()) {
            JOURNAL.info("Plafond {} atteint.", surface);
            throw new PlafondDepasseException(surface, decision.attente());
        }
    }

    private Surface configuree(String surface) {
        Surface configuration = surfaces.get(surface);
        if (configuration == null) {
            throw new IllegalStateException("Surface plafonnée sans configuration : " + surface);
        }
        return configuration;
    }
}
