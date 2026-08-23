package be.autoservplus.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documentation de l API publique (BL-8).
 *
 * <p><b>Ceci est ce qui justifie springdoc.</b> La dependance figurait au
 * {@code pom.xml} depuis le socle sans qu aucun {@code @RestController} metier
 * n existe : elle exposait {@code /swagger-ui} et {@code /v3/api-docs} pour decrire
 * zero endpoint. Elle documente desormais les deux ressources publiques de
 * {@code /api/v1}.</p>
 *
 * <p><b>Le groupe est restreint a {@code /api/v1/**}.</b> Sans cette borne, springdoc
 * inventorierait aussi le webhook de paiement, qui n est pas une API publique mais un
 * point d entree serveur a serveur : le publier dans une documentation ouverte
 * reviendrait a en signaler l existence a qui n a pas a la connaitre.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiPublique(IdentiteGarage garage) {
        return new OpenAPI()
                .info(new Info()
                        .title("API publique AutoServ+")
                        .version("v1")
                        .description("""
                                Offre commerciale du garage, en lecture seule et sans \
                                authentification. Aucune donnee personnelle de membre, \
                                aucune commande et aucun rendez-vous n y sont accessibles.""")
                        .contact(new Contact()
                                .name(garage.raisonSociale())
                                .email(garage.courriel()))
                        .license(new License().name("Usage soumis aux conditions du garage")))
                // Domaine public du service. En developpement, springdoc ajoute de
                // lui-meme l hote courant : ce serveur ne masque donc pas les essais
                // locaux, il nomme la cible de production.
                .servers(List.of(new Server()
                        .url("https://autoservplus.be")
                        .description("Production")));
    }

    @Bean
    public GroupedOpenApi groupePublic() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
