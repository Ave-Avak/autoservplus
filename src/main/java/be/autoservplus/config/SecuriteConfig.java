package be.autoservplus.config;

import be.autoservplus.i18n.LangueApresConnexionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
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

    /**
     * {@code ROLE_SUPER_ADMINISTRATEUR} implique {@code ROLE_ADMINISTRATEUR}
     * (CdC 5.2.3).
     *
     * <p><b>Pourquoi une hierarchie plutot que treize annotations doublees.</b> Un
     * compte ne porte qu une autorite — {@code UtilisateurDetailsService} fait
     * {@code .roles(typeUtilisateur.name())}. Sans ce bean, le super-administrateur
     * serait refuse partout ou l administrateur est admis, et il faudrait reecrire les
     * <b>treize</b> {@code @PreAuthorize("hasRole('ADMINISTRATEUR')")} du projet plus
     * le matcher {@code /admin/**}. Chaque oubli serait un ecran inaccessible, et
     * chaque service ajoute plus tard une occasion de recommencer.</p>
     *
     * <p><b>Deux niveaux seulement, et c est une decision.</b> {@code ROLE_MEMBRE} n y
     * figure pas parce qu il n est <b>garde nulle part</b> : aucun
     * {@code hasRole('MEMBRE')} n existe dans le projet, et les ecrans personnels
     * ({@code /mes-vehicules}, {@code /panier}, {@code /commandes}) sont couverts par
     * {@code anyRequest().authenticated()} seul — leur cloisonnement vient de
     * l <i>ownership</i>, chacun projetant les donnees du principal. L inscrire dans la
     * hierarchie ne changerait donc rien du tout : ce serait du bruit dans une
     * declaration dont toute la valeur est d etre lisible d un coup d oeil.
     * {@code RoleMembreNestPasUneGardeTest} casse la build si une telle garde
     * apparaissait, auquel cas la question se reposerait.</p>
     *
     * <p><b>Ce bean suffit-il vraiment aux deux surfaces ?</b> Depuis Spring Security
     * 6.3, un {@code RoleHierarchy} unique est repris par l autorisation web ET par la
     * securite de methode. Le projet ne s en remet pas a cette lecture :
     * {@code HierarchieRolesIT} verifie les deux surfaces separement, parce qu un
     * comportement de framework suppose a deja coute trois formulaires publics au
     * projet (la propriete declaree vide jugee presente).</p>
     */
    @Bean
    public RoleHierarchy hierarchieDesRoles() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SUPER_ADMINISTRATEUR").implies("ADMINISTRATEUR")
                .build();
    }

    @Bean
    public SecurityFilterChain chaineDeFiltres(HttpSecurity http,
                                               EchecAuthentificationHandler echecHandler,
                                               LangueApresConnexionHandler succesHandler,
                                               SessionRegistry registreSessions)
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
                                "/compte-supprime",
                                // Confirmation d un changement d adresse (CdC 5.2.2).
                                // Le lien arrive dans la NOUVELLE boite, souvent
                                // ouverte ailleurs que dans la session du membre ;
                                // exiger une authentification y renverrait vers un
                                // formulaire de connexion portant l ANCIENNE adresse,
                                // et la bascule deviendrait impossible a conclure. Le
                                // jeton porte l autorisation, comme pour l activation
                                // de compte. Route ENUMEREE et non ouverte par joker :
                                // elle ne sert que cette confirmation.
                                "/changement-adresse/confirmation")
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
                // Les sessions authentifiees sont inscrites au registre, pour que la
                // suspension d un compte puisse couper celles deja ouvertes. Sans
                // cette ligne, le bean SessionRegistry existerait sans jamais rien
                // apprendre : la revocation ne trouverait aucune session a fermer, et
                // rien ne le signalerait.
                .sessionManagement(sessions -> sessions
                        .maximumSessions(-1)
                        .sessionRegistry(registreSessions))
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
                        // Turnstile : deux hotes ajoutes, un seul et le meme.
                        // script-src pour api.js, frame-src pour la trame du widget.
                        // frame-src et connect-src etaient jusqu ici ABSENTES et
                        // retombaient sur default-src 'self' : la trame aurait ete
                        // bloquee sans que la politique le laisse voir. Les declarer
                        // rend explicite ce qui n etait qu un repli.
                        // frame-ancestors reste 'none' : autoriser un tiers a charger
                        // NOS pages dans une trame n a rien a voir avec charger LA
                        // SIENNE dans les notres.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' https://challenges.cloudflare.com; "
                                        + "frame-src https://challenges.cloudflare.com; "
                                        + "connect-src 'self'; "
                                        + "style-src 'self'; "
                                        + "img-src 'self' data:; "
                                        + "form-action 'self' https://www.mollie.com; "
                                        + "frame-ancestors 'none'; base-uri 'self'"))
                        .frameOptions(cadre -> cadre.deny())
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                );
        return http.build();
    }
}