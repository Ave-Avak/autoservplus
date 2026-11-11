package be.autoservplus.reservation.service;

import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V48 active la confirmation automatique sur toute base, la graine V903 rend le mode
 * manuel à la démonstration. La graine vit dans {@code db/demo}, qu'aucun démarrage de
 * test ne joue : elle est appliquée ici à la main, sur la base propre à cette classe.
 */
@SpringBootTest
@DisplayName("Graine de démonstration en confirmation manuelle (intégration)")
class GraineConfirmationManuelleIT extends SocleIntegration {

    @Autowired private ParametreAtelierRepository parametres;
    @Autowired private DataSource source;

    @Test
    @DisplayName("confirmation automatique sur une base sans graine, manuelle après V903")
    void graineRendLeModeManuel() {
        assertThat(parametres.courants().isConfirmationAutomatique()).isTrue();

        new ResourceDatabasePopulator(new ClassPathResource(
                "db/demo/V903__demonstration_confirmation_manuelle.sql")).execute(source);

        assertThat(parametres.courants().isConfirmationAutomatique()).isFalse();
    }
}
