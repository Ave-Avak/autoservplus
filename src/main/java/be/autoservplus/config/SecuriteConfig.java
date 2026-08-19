package be.autoservplus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuration de securite.
 *
 * <p>Le facteur de cout BCrypt est fixe a 12 : chaque verification demande environ
 * 250 millisecondes, ce qui rend une attaque par force brute couteuse sans degrader
 * l experience de connexion.</p>
 */
@Configuration
public class SecuriteConfig {

    private static final int COUT_BCRYPT = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(COUT_BCRYPT);
    }
}