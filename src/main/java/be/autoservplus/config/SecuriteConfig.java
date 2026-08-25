package be.autoservplus.config;

import be.autoservplus.i18n.LangueApresConnexionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                                               EchecAuthentificationHandler echecHandler,
                                               LangueApresConnexionHandler succesHandler)
            throws Exception {
        http
                .authorizeHttpRequests(acces -> acces
                        .requestMatchers("/", "/accueil", "/services/**", "/pieces/**",
                                "/inscription/**", "/connexion", "/mot-de-passe/**",
                                "/cgv", "/mentions-legales", "/confidentialite",
                                // Texte gele d une version de document (F24). Public
                                // pour la meme raison que les CGV elles-memes : exiger
                                // une connexion pour relire les conditions qu on a
                                // acceptees serait une entrave sans motif, et le
                                // document n est le secret de personne. Enumere par
                                // prefixe et non par joker ouvert sur tout : la route
                                // ne sert que des textes contractuels generaux.
                                "/documents/*/*",
                                // Page de contact : l article VI.45 CDE veut que
                                // l identite du professionnel, son adresse, son
                                // telephone et son courriel soient accessibles AVANT
                                // que le consommateur ne soit lie — donc avant toute
                                // creation de compte. Derriere une authentification,
                                // l information arriverait apres le moment ou elle
                                // doit eclairer la decision.
                                "/contact",
                                // Bandeau et gestion des cookies (F25) : la question du
                                // consentement se pose des la premiere visite, donc avant
                                // toute connexion. Exiger une authentification pour y
                                // repondre rendrait le refus impossible au visiteur, qui
                                // est justement celui a qui l on demande.
                                "/cookies", "/cookies/**",
                                // Confirmation de suppression de compte (F23) : a
                                // l instant ou elle s affiche, la session vient d etre
                                // invalidee et le compte n existe plus. Une page
                                // authentifiee renverrait vers un formulaire de
                                // connexion que plus aucun identifiant ne satisfait.
                                "/compte-supprime")
                        .permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // API publique en LECTURE SEULE (BL-8) et sa documentation.
                        // Anonyme par construction : elle n expose que ce qui figure
                        // deja sur le site public — catalogue des prestations et
                        // identite commerciale du garage. Aucun verbe d ecriture n y
                        // est declare, donc aucune authentification a exiger. La table
                        // clef_api du socle reste volontairement inexploitee : y
                        // adosser des jetons supposerait quotas et revocation, hors
                        // perimetre V1.
                        //
                        // Les deux routes sont ENUMEREES, et non couvertes par un joker
                        // /api/v1/**. Le joker est fail-open : le troisieme endpoint,
                        // quel qu il soit, naitrait public sans que personne ne l ait
                        // decide — y compris un endpoint qui exposerait des donnees de
                        // membre. L enumeration est fail-closed : ajouter une route
                        // publique devient un geste explicite, verifiable en revue.
                        // ApiPubliqueIT.surfaceFermeeParDefaut verrouille l ecart entre
                        // les deux ecritures (302 attendu, 404 avec le joker).
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/prestations/**", "/api/v1/garages/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
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
                        // Remplace defaultSuccessUrl("/mon-compte", true) a comportement
                        // strictement egal — meme classe, memes deux reglages — en y
                        // ajoutant l application de la langue enregistree au profil (F6).
                        .successHandler(succesHandler)
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
                        // form-action porte le seul assouplissement de cette politique, et il
                        // conditionne le parcours de paiement : le depart vers le prestataire
                        // est un POST dont la reponse redirige vers Mollie. Chrome et Edge
                        // appliquent form-action a la CIBLE de la redirection qui suit l envoi
                        // d un formulaire, et pas seulement a l action ecrite dans le gabarit :
                        // avec 'self' seul, ils refusent de suivre et le membre ne peut pas
                        // payer. Firefox ne l applique pas, d ou un defaut invisible sur une
                        // partie des navigateurs.
                        // Un seul hote, et aucun joker : la page Mollie renvoie ensuite vers
                        // les banques depuis SON domaine, hors de notre document et donc hors
                        // de cette politique. Elargir davantage n aiderait a rien et ouvrirait
                        // l envoi d un formulaire du site vers un tiers.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; " +
                                        "img-src 'self' data:; " +
                                        "form-action 'self' https://www.mollie.com; " +
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