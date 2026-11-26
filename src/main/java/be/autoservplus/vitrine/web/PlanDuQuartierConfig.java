package be.autoservplus.vitrine.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Plan du quartier de la page de contact, servi sous {@code /images/plan/} tant que le site
 * se présente comme une démonstration. Le plan est fictif (tuiles d'OpenStreetMap du centre
 * de Bruxelles, sans repère) : il illustre la page d'un garage qui n'existe pas. Un garage
 * réel n'en reçoit aucun, plutôt qu'un plan faux, et garde les liens d'itinéraire.
 *
 * <p>Le fichier reste hors de {@code static/}, qui le publierait sous tout profil. Une image
 * servie par l'application, et non une carte embarquée, laisse la CSP à {@code img-src
 * 'self'} et n'appelle aucun service de cartographie à l'ouverture de la page.</p>
 */
@Configuration
@ConditionalOnProperty(name = "autoservplus.vitrine.mention-demonstration", havingValue = "true")
public class PlanDuQuartierConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registre) {
        registre.addResourceHandler("/images/plan/**")
                .addResourceLocations("classpath:/demonstration/plan/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
    }
}
