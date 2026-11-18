package be.autoservplus.config;

import be.autoservplus.common.web.IdentifiantRequeteFiltre;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Inscrit {@link IdentifiantRequeteFiltre} en tête de la chaîne des filtres du conteneur,
 * avant Spring Security : les lignes de l'authentification portent déjà l'identifiant.
 * Inscrit aussi pour le dispatch d'erreur, sans quoi la page d'un 500 serait rendue sans
 * lui.
 */
@Configuration
public class JournalisationConfig {

    @Bean
    public FilterRegistrationBean<IdentifiantRequeteFiltre> identifiantRequete() {
        FilterRegistrationBean<IdentifiantRequeteFiltre> inscription =
                new FilterRegistrationBean<>(new IdentifiantRequeteFiltre());
        inscription.setOrder(Ordered.HIGHEST_PRECEDENCE);
        inscription.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR,
                DispatcherType.ASYNC);
        return inscription;
    }
}
