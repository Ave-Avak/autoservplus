package be.autoservplus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Fournit une horloge injectable plutot que des appels directs a Instant.now().
 *
 * <p>Les services qui dependent du temps peuvent ainsi etre testes de facon
 * deterministe, en substituant une horloge figee.</p>
 */
@Configuration
public class HorlogeConfig {

    @Bean
    public Clock horloge() {
        return Clock.systemUTC();
    }
}
