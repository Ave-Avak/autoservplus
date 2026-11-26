package be.autoservplus.common.debit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Inscrit {@link IntercepteurPlafonds}. Les tests de la couche web ({@code @WebMvcTest})
 * chargent cette classe sans les services : l'intercepteur n'y est alors pas posé, et les
 * plafonds se vérifient en intégration.
 */
@Configuration
class PlafondsConfig implements WebMvcConfigurer {

    private final ObjectProvider<PlafondsRequetes> plafonds;

    PlafondsConfig(ObjectProvider<PlafondsRequetes> plafonds) {
        this.plafonds = plafonds;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registre) {
        plafonds.ifAvailable(disponibles -> registre.addInterceptor(new IntercepteurPlafonds(disponibles)));
    }
}
