package be.autoservplus.vente.service;

import be.autoservplus.vente.web.PaiementSimuleController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les issues simulées, attente de confirmation comprise, n'existent qu'avec le
 * prestataire bouchonné. Une clé de prestataire configurée retire la page, son service
 * et le bouchon : aucun chemin ne permet alors de programmer un statut.
 */
@DisplayName("Paiement simulé : absent dès qu'un prestataire est configuré")
class SimulationPaiementConditionTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withUserConfiguration(PrestatairePaiementFictif.class,
                    SimulationPaiementService.class, PaiementSimuleController.class);

    @Test
    @DisplayName("avec une clé Mollie : ni bouchon, ni service de simulation, ni page")
    void absentAvecMollie() {
        contexte.withPropertyValues(ConditionPrestataire.CLE + "=test_cle_de_site")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(PrestatairePaiementFictif.class);
                    assertThat(ctx).doesNotHaveBean(SimulationPaiementService.class);
                    assertThat(ctx).doesNotHaveBean(PaiementSimuleController.class);
                });
    }

    @Test
    @DisplayName("sans clé : le bouchon est la seule implémentation du prestataire")
    void bouchonSansCle() {
        new ApplicationContextRunner()
                .withUserConfiguration(PrestatairePaiementFictif.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(PrestatairePaiementFictif.class));
    }
}
