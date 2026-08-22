package be.autoservplus.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Active la liaison des proprietes de facturation. Le projet n employant pas
 * {@code @ConfigurationPropertiesScan}, l enregistrement est explicite : le jour ou
 * un second bloc de proprietes apparait, il se declare ici et nulle part ailleurs.
 */
@Configuration
@EnableConfigurationProperties(IdentiteGarage.class)
public class FacturationConfig {
}
