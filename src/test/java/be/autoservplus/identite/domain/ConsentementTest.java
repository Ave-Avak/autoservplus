package be.autoservplus.identite.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Consentement")
class ConsentementTest {

    private final Utilisateur marie = new Utilisateur(
            "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);

    private Consentement acceptationCgv() {
        return Consentement.acceptation(marie, TypeDocumentConsentement.CGV, "CGV-2026-03",
                "127.0.0.1", Instant.parse("2026-11-17T10:00:00Z"));
    }

    @Test
    @DisplayName("un consentement sans commande, celui des cookies, n'en désigne aucune")
    void sansCommande() {
        assertThat(acceptationCgv().getCommandeId()).isNull();
    }

    @Test
    @DisplayName("rattaché à sa commande une fois, il ne change plus de commande")
    void rattachementUnique() {
        Consentement preuve = acceptationCgv().pourLaCommande(42L);

        assertThat(preuve.getCommandeId()).isEqualTo(42L);
        assertThatThrownBy(() -> preuve.pourLaCommande(43L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(preuve.getCommandeId()).isEqualTo(42L);
    }
}
