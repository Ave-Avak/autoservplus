package be.autoservplus.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("ControlePaiementProduction (RM-20)")
class ControlePaiementProductionTest {

    private static ApplicationContextRunner sous(String profil) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.config.location=classpath:/application.yml",
                        "spring.profiles.active=" + profil)
                .withUserConfiguration(ProprietesConfig.class, ControlePaiementProduction.class);
    }

    @Nested
    @DisplayName("Sous prod")
    class Production {

        @Test
        @DisplayName("refusé sans identifiant : le paiement simulé prendrait le relais")
        void sansCle() {
            sous("prod").run(contexte -> {
                assertThat(contexte).hasFailed();
                assertThat(contexte.getStartupFailure()).rootCause()
                        .hasMessageContaining("MOLLIE_API_KEY absente ou vide");
            });
        }

        @Test
        @DisplayName("refusé avec une clé de test")
        void cleDeTest() {
            sous("prod").withPropertyValues("autoservplus.paiement.mollie.cle-api=test_abc")
                    .run(contexte -> {
                        assertThat(contexte).hasFailed();
                        assertThat(contexte.getStartupFailure()).rootCause()
                                .hasMessageContaining("cle de test");
                    });
        }

        @Test
        @DisplayName("refusé avec un jeton d'accès en mode test, même par défaut")
        void jetonEnModeTest() {
            sous("prod").withPropertyValues(
                            "autoservplus.paiement.mollie.cle-api=access_abc",
                            "autoservplus.paiement.mollie.profil-id=pfl_abc")
                    .run(contexte -> {
                        assertThat(contexte).hasFailed();
                        assertThat(contexte.getStartupFailure()).rootCause()
                                .hasMessageContaining("MOLLIE_MODE_TEST doit valoir false");
                    });
        }

        @Test
        @DisplayName("accepté avec une clé réelle, ou un jeton d'accès hors mode test")
        void identifiantReel() {
            sous("prod").withPropertyValues("autoservplus.paiement.mollie.cle-api=live_abc")
                    .run(contexte -> assertThat(contexte).hasNotFailed());
            sous("prod").withPropertyValues(
                            "autoservplus.paiement.mollie.cle-api=access_abc",
                            "autoservplus.paiement.mollie.profil-id=pfl_abc",
                            "autoservplus.paiement.mollie.mode-test=false")
                    .run(contexte -> assertThat(contexte).hasNotFailed());
        }
    }

    @Test
    @DisplayName("sous demo, sans identifiant, le démarrage n'est pas contrôlé")
    void demoSansCle() {
        sous("demo").run(contexte -> {
            assertThat(contexte).hasNotFailed();
            assertThat(contexte).doesNotHaveBean(ControlePaiementProduction.class);
        });
    }
}
