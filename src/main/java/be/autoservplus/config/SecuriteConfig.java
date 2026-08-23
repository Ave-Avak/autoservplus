package be.autoservplus.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Configuration de securite : chaine de filtres, en-tetes HTTP, encodage des mots de passe.
 *
 * <p>Le facteur de cout BCrypt est fixe a 12 : chaque verification demande environ
 * 250 millisecondes, ce qui rend une attaque par force brute couteuse sans degrader
 * l experience de connexion. La protection CSRF reste active sur toutes les requetes
 * mutantes.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecuriteConfig {

    private static final int COUT_BCRYPT = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(COUT_BCRYPT);
    }

    @Bean
    public SecurityFilterChain chaineDeFiltres(HttpSecurity http,
                                               EchecAuthentificationHandler echecHandler) throws Exception {
        http
                .authorizeHttpRequests(acces -> acces
                        .requestMatchers("/", "/accueil", "/services/**", "/pieces/**",
                                "/inscription/**", "/connexion", "/mot-de-passe/**",
                                "/cgv", "/mentions-legales", "/confidentialite", "/cookies",
                                // Confirmation de suppression de compte (F23) : a
                                // l instant ou elle s affiche, la session vient d etre
                                // invalidee et le compte n existe plus. Une page
                                // authentifiee renverrait vers un formulaire de
                                // connexion que plus aucun identifiant ne satisfait.
                                "/compte-supprime")
                        .permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Webhook du prestataire de paiement : appel serveur a serveur,
                        // sans session. L authenticite ne vient pas d un jeton mais de
                        // la strategie securite §11 — le payload n est jamais cru, le
                        // statut est relu aupres du prestataire via la passerelle.
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMINISTRATEUR")
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/**"))
                .formLogin(formulaire -> formulaire
                        .loginPage("/connexion")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/mon-compte", true)
                        .failureHandler(echecHandler)
                        .permitAll()
                )
                .logout(deconnexion -> deconnexion
                        .logoutRequestMatcher(new AntPathRequestMatcher("/deconnexion", "POST"))
                        .logoutSuccessUrl("/?deconnecte")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .headers(entetes -> entetes
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; " +
                                        "img-src 'self' data:; form-action 'self'; " +
                                        "frame-ancestors 'none'; base-uri 'self'"))
                        .frameOptions(cadre -> cadre.deny())
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                );
        return http.build();
    }
}