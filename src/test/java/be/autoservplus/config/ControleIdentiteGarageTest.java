package be.autoservplus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Démarrage refusé ou accepté selon l'identité du garage et le profil actif.
 *
 * <p>{@link ApplicationContextRunner} plutôt qu'un {@code @SpringBootTest} : seuls la
 * liaison des propriétés et le contrôle sont chargés, sans base ni sécurité. Le vrai
 * {@code application.yml} est lu, et lui seul ({@code spring.config.location}), avec son
 * document des profils {@code dev} et {@code demo}. C'est ce qui permet de vérifier
 * qu'aucune valeur de démonstration ne survit sous {@code prod}.</p>
 *
 * <p>Le cas accepté sous {@code prod} est indispensable : sans lui, un contrôle qui
 * refuserait tout passerait les trois cas de refus.</p>
 */
@DisplayName("Contrôle de l'identité du garage au démarrage")
class ControleIdentiteGarageTest {

    /** Numéros calculés pour l'occasion, recopiés d'aucune entreprise. */
    private static final String[] IDENTITE_VALIDE = {
            "autoservplus.garage.nom-commercial=Garage Test",
            "autoservplus.garage.raison-sociale=Garage Test",
            "autoservplus.garage.forme-juridique=SRL",
            "autoservplus.garage.rue=Rue du Test",
            "autoservplus.garage.numero-rue=1",
            "autoservplus.garage.code-postal=1000",
            "autoservplus.garage.localite=Bruxelles",
            "autoservplus.garage.pays=Belgique",
            "autoservplus.garage.numero-bce=0400.000.086",
            "autoservplus.garage.rpm=RPM Bruxelles",
            "autoservplus.garage.numero-tva=BE0400000086",
            "autoservplus.garage.iban=BE32 1234 5678 9002",
            "autoservplus.garage.telephone=+32 2 000 00 00",
            "autoservplus.garage.courriel=garage@test.test"};

    /**
     * Le profil passe par {@code spring.profiles.active}, comme en déploiement par
     * {@code SPRING_PROFILES_ACTIVE}. Un {@code setActiveProfiles} se cumulerait avec le
     * {@code dev} par défaut d'{@code application.yml} : le cas « prod » tournerait alors
     * avec l'identité de démonstration, et le test ne prouverait rien.
     */
    private static ApplicationContextRunner sous(String profil) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(
                        "spring.config.location=classpath:/application.yml",
                        "spring.profiles.active=" + profil)
                .withUserConfiguration(ProprietesConfig.class, ControleIdentiteGarage.class);
    }

    @Nested
    @DisplayName("Sous prod")
    class Production {

        @Test
        @DisplayName("refusé sans les variables, toutes nommées en une fois")
        void sansVariables() {
            sous("prod").run(contexte -> {
                assertThat(contexte).hasFailed();
                assertThat(contexte.getStartupFailure()).rootCause()
                        .hasMessageContaining("GARAGE_RAISON_SOCIALE absente ou vide")
                        .hasMessageContaining("GARAGE_BCE absente ou vide")
                        .hasMessageContaining("GARAGE_TVA absente ou vide")
                        .hasMessageContaining("GARAGE_IBAN absente ou vide")
                        .hasMessageContaining("GARAGE_COURRIEL absente ou vide");
            });
        }

        @Test
        @DisplayName("refusé avec une variable vide")
        void variableVide() {
            sous("prod")
                    .withPropertyValues(IDENTITE_VALIDE)
                    .withPropertyValues("autoservplus.garage.telephone=")
                    .run(contexte -> {
                        assertThat(contexte).hasFailed();
                        assertThat(contexte.getStartupFailure()).rootCause()
                                .hasMessageContaining("GARAGE_TELEPHONE absente ou vide")
                                .hasMessageNotContaining("GARAGE_BCE");
                    });
        }

        @Test
        @DisplayName("refusé avec un numéro d'entreprise faux, et la TVA qui ne lui correspond plus")
        void bceFaux() {
            sous("prod")
                    .withPropertyValues(IDENTITE_VALIDE)
                    .withPropertyValues("autoservplus.garage.numero-bce=0123.456.789")
                    .run(contexte -> {
                        assertThat(contexte).hasFailed();
                        assertThat(contexte.getStartupFailure()).rootCause()
                                .hasMessageContaining("GARAGE_BCE invalide")
                                .hasMessageContaining("GARAGE_TVA invalide");
                    });
        }

        @Test
        @DisplayName("refusé avec une forme juridique sans mention RPM")
        void formeSansRpm() {
            sous("prod")
                    .withPropertyValues(IDENTITE_VALIDE)
                    .withPropertyValues("autoservplus.garage.rpm=")
                    .run(contexte -> assertThat(contexte.getStartupFailure()).rootCause()
                            .hasMessageContaining("GARAGE_RPM absente ou vide"));
        }

        @Test
        @DisplayName("accepté avec un jeu valide")
        void jeuValide() {
            sous("prod").withPropertyValues(IDENTITE_VALIDE).run(contexte -> {
                assertThat(contexte).hasNotFailed();
                assertThat(contexte).hasSingleBean(ControleIdentiteGarage.class);
            });
        }

        @Test
        @DisplayName("accepté pour une personne physique, sans forme juridique ni RPM")
        void personnePhysique() {
            sous("prod")
                    .withPropertyValues(IDENTITE_VALIDE)
                    .withPropertyValues("autoservplus.garage.forme-juridique=", "autoservplus.garage.rpm=")
                    .run(contexte -> assertThat(contexte).hasNotFailed());
        }

        @Test
        @DisplayName("accepté sans nom commercial, qui se replie sur la raison sociale")
        void sansNomCommercial() {
            sous("prod")
                    .withPropertyValues(IDENTITE_VALIDE)
                    .withPropertyValues("autoservplus.garage.nom-commercial=",
                            "autoservplus.garage.raison-sociale=Garage Dupont")
                    .run(contexte -> {
                        assertThat(contexte).hasNotFailed();
                        assertThat(contexte.getBean(IdentiteGarage.class).enseigne())
                                .isEqualTo("Garage Dupont");
                    });
        }
    }

    @Nested
    @DisplayName("Hors prod")
    class HorsProduction {

        @Test
        @DisplayName("sous demo : accepté avec les valeurs de démonstration, et le contrôle absent")
        void demo() {
            sous("demo").run(contexte -> {
                assertThat(contexte).hasNotFailed();
                assertThat(contexte).doesNotHaveBean(ControleIdentiteGarage.class);
                IdentiteGarage identite = contexte.getBean(IdentiteGarage.class);
                assertThat(identite.raisonSociale()).isEqualTo("Garage de démonstration");
                assertThat(identite.numeroBce()).isEqualTo("0123.456.789");
            });
        }

        @Test
        @DisplayName("sous dev : mêmes valeurs de démonstration")
        void dev() {
            sous("dev").run(contexte -> {
                assertThat(contexte).hasNotFailed();
                assertThat(contexte.getBean(IdentiteGarage.class).denomination())
                        .isEqualTo("Garage de démonstration SRL");
            });
        }
    }
}
