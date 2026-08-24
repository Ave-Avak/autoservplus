package be.autoservplus.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Point d enregistrement unique des blocs de {@code @ConfigurationProperties}. Le
 * projet n employant pas {@code @ConfigurationPropertiesScan}, la declaration est
 * explicite : un bloc de proprietes se lie ici, et nulle part ailleurs.
 *
 * <p>La classe s appelait {@code FacturationConfig} tant qu elle ne portait que
 * l identite du garage. Le nom a suivi le second bloc plutot que de le contredire :
 * les identifiants du prestataire de paiement ne relevent pas de la facturation, et
 * un nom qui ment sur son contenu coute plus cher qu un renommage.</p>
 */
@Configuration
@EnableConfigurationProperties({IdentiteGarage.class, MollieProprietes.class})
public class ProprietesConfig {
}
