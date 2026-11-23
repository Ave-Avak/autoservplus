package be.autoservplus.support;

import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Fuseau du garage pour les tests unitaires, sans base : celui des paramètres par défaut. */
public final class FuseauxDeTest {

    private FuseauxDeTest() {
    }

    /** Europe/Brussels, valeur par défaut des paramètres de l'atelier. */
    public static FuseauGarage bruxelles() {
        ParametreAtelierRepository parametres = mock(ParametreAtelierRepository.class,
                withSettings().strictness(Strictness.LENIENT));
        when(parametres.courants()).thenReturn(new ParametreAtelier());
        return new FuseauGarage(parametres);
    }
}
