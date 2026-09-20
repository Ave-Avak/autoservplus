package be.autoservplus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fous du drapeau de planification.
 *
 * <p>Le drapeau existe pour les tests d integration, ou l ordonnanceur ne rend aucun
 * service. Le risque n est donc pas qu il soit mal ecrit, c est qu il <b>fuite en
 * production</b> — ou que son extinction y passe inapercue. Ces trois cas verrouillent
 * les deux moities : la valeur par defaut, et le bruit qu une extinction doit faire.</p>
 *
 * <p>{@link ApplicationContextRunner} plutot qu un {@code @SpringBootTest} : c est le
 * cablage conditionnel qui est en cause, pas l application. Un contexte complet
 * couterait une base de donnees pour verifier la presence d un bean.</p>
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Drapeau de planification")
class PlanificationConfigTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withUserConfiguration(PlanificationConfig.class,
                    PlanificationDesactiveeAvertissement.class);

    /**
     * Le cas qui compte le plus. Une planification qui exigerait un reglage explicite
     * pour tourner serait eteinte partout ou personne n y a pense — a commencer par le
     * deploiement, qui ne connait pas ce drapeau et n a pas a le connaitre.
     */
    @Test
    @DisplayName("propriete absente : la planification est ACTIVE")
    void activeeParDefaut() {
        contexte.run(monte -> {
            assertThat(monte).hasSingleBean(PlanificationConfig.class);
            assertThat(monte).doesNotHaveBean(PlanificationDesactiveeAvertissement.class);
        });
    }

    @Test
    @DisplayName("propriete a true : la planification est active")
    void activeeExplicitement() {
        contexte.withPropertyValues("autoservplus.planification.activee=true")
                .run(monte -> assertThat(monte).hasSingleBean(PlanificationConfig.class));
    }

    @Test
    @DisplayName("propriete a false : planification eteinte, et un avertissement nomme RM-21")
    void desactiveeAvecAvertissement(CapturedOutput journal) {
        contexte.withPropertyValues("autoservplus.planification.activee=false")
                .run(monte -> {
                    assertThat(monte).doesNotHaveBean(PlanificationConfig.class);
                    assertThat(monte).hasSingleBean(PlanificationDesactiveeAvertissement.class);
                });

        // Le message doit nommer la CONSEQUENCE, pas seulement le reglage : un journal
        // se lit sans le code sous les yeux.
        assertThat(journal).contains("Planification DESACTIVEE");
        assertThat(journal).contains("RM-21");
        assertThat(journal).contains("EN_ATTENTE_PAIEMENT");
    }
}
