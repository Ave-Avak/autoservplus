package be.autoservplus.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetourInterne")
class RetourInterneTest {

    @ParameterizedTest
    @ValueSource(strings = {"/mes-rendez-vous/nouveau", "/commande", "/panier",
            "/mes-rendez-vous/nouveau?prestation=0b3c6f1e-9d2a-4c8b-a1f0-5e7d9c2b4a61"})
    @DisplayName("adresse de la liste fermée : suivie telle quelle")
    void permis(String retour) {
        assertThat(RetourInterne.permis(retour)).contains(retour);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "https://exemple.org", "//exemple.org/panier", "/admin", "/panier/",
            "panier", "/commande?suite=https://exemple.org", "/mes-rendez-vous/nouveau?prestation=1",
            "/mes-rendez-vous/nouveau?prestation=0b3c6f1e-9d2a-4c8b-a1f0-5e7d9c2b4a61&x=1",
            "/\\exemple.org", "/panier#ancre"})
    @DisplayName("toute autre adresse : ignorée, sans redirection ouverte")
    void refuse(String retour) {
        assertThat(RetourInterne.permis(retour)).isEmpty();
    }
}
