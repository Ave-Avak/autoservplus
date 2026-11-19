package be.autoservplus.config;

import be.autoservplus.identite.service.VerificateurTurnstile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("AvertissementTurnstile")
class AvertissementTurnstileTest {

    private static MockEnvironment profils(String... actifs) {
        MockEnvironment environnement = new MockEnvironment();
        environnement.setActiveProfiles(actifs);
        return environnement;
    }

    @Test
    @DisplayName("avertit sans Turnstile sous prod et sous dev")
    void avertitHorsDemoEtTest() {
        assertThat(new AvertissementTurnstile(Optional.empty(), profils("prod")).avertissementDu()).isTrue();
        assertThat(new AvertissementTurnstile(Optional.empty(), profils("dev")).avertissementDu()).isTrue();
    }

    @Test
    @DisplayName("se tait sous demo et sous test, ou quand Turnstile est configuré")
    void seTait() {
        assertThat(new AvertissementTurnstile(Optional.empty(), profils("demo")).avertissementDu()).isFalse();
        assertThat(new AvertissementTurnstile(Optional.empty(), profils("test")).avertissementDu()).isFalse();
        assertThat(new AvertissementTurnstile(Optional.of(mock(VerificateurTurnstile.class)),
                profils("prod")).avertissementDu()).isFalse();
    }
}
