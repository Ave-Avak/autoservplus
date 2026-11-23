package be.autoservplus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cache des ressources statiques")
class CacheDesStatiquesTest {

    private static final String DUREE = "spring.web.resources.cache.cachecontrol.max-age";

    @Test
    @DisplayName("la durée de cache n'est réglée que dans le bloc du profil prod")
    void sousProdSeul() throws IOException {
        List<PropertySource<?>> blocs = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        List<PropertySource<?>> reglages = blocs.stream()
                .filter(bloc -> bloc.containsProperty(DUREE))
                .toList();

        assertThat(reglages).hasSize(1);
        assertThat(String.valueOf(reglages.get(0).getProperty("spring.config.activate.on-profile")))
                .isEqualTo("prod");
        assertThat(String.valueOf(reglages.get(0).getProperty(DUREE))).isEqualTo("1h");
    }

    @Test
    @DisplayName("le poste de développement n'en règle aucune")
    void rienSousDev() throws IOException {
        List<PropertySource<?>> blocs = new YamlPropertySourceLoader()
                .load("application-dev", new ClassPathResource("application-dev.yml"));

        assertThat(blocs).noneMatch(bloc -> bloc.containsProperty(DUREE));
    }
}
