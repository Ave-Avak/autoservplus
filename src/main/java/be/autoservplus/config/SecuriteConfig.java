package be.autoservplus.config;

import be.autoservplus.identite.service.EncodeurNormalise;
import be.autoservplus.identite.service.GardeConnexion;
import be.autoservplus.identite.service.UtilisateurDetailsService;
import be.autoservplus.identite.service.VerificateurTurnstile;
import be.autoservplus.identite.web.AuthentificationPartielle;
import be.autoservplus.identite.web.ConnexionPartielleHorsEtapes;
import be.autoservplus.identite.web.EtapeConnexion;
import be.autoservplus.identite.web.GardeConnexionFiltre;
import be.autoservplus.identite.web.ParcoursConnexion;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Optional;

/**
 * Configuration de sécurité : chaîne de filtres, en-têtes HTTP, encodage des mots de passe.
 *
 * <p>Le facteur de coût BCrypt est fixé à 12 : chaque vérification demande environ
 * 250 millisecondes, ce qui rend une attaque par force brute coûteuse sans dégrader
 * l'expérience de connexion. La protection CSRF reste active sur toutes les requêtes
 * mutantes.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecuriteConfig {

    private static final int COUT_BCRYPT = 12;

    /** POST du formulaire de connexion, reconnu par le garde et par le filtre d'authentification. */
    private static final RequestMatcher ENVOI_CONNEXION =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/connexion");

    /** BCrypt de coût 12 sur la forme NFKC du mot de passe, voir {@link EncodeurNormalise}. */
    @Bean
    public EncodeurNormalise passwordEncoder() {
        return new EncodeurNormalise(new BCryptPasswordEncoder(COUT_BCRYPT));
    }

    /**
     * Le mot de passe est vérifié AVANT le verrouillage et la désactivation.
     *
     * <p>Spring Security contrôle par défaut l'état du compte avant le hachage : un compte
     * verrouillé ou désactivé répondait sans BCrypt, une adresse inconnue payait le
     * hachage fictif de Spring, et la durée de la réponse disait laquelle des deux
     * avait été saisie. Les mêmes contrôles passent ici après la vérification. Toute
     * adresse paie donc un BCrypt de coût 12, et le refus d'un compte verrouillé reste
     * entier : un mot de passe juste ne passe pas davantage.</p>
     *
     * <p>Une connexion réussie par la forme brute d'un mot de passe, empreinte écrite
     * avant la normalisation NFKC, fait réécrire l'empreinte de sa forme normalisée. Le
     * mot de passe présenté n'est disponible qu'ici, avant l'effacement des
     * identifiants.</p>
     */
    @Bean
    public DaoAuthenticationProvider fournisseurAuthentification(
            UtilisateurDetailsService comptes, EncodeurNormalise encodeur) {
        DaoAuthenticationProvider fournisseur = new DaoAuthenticationProvider(comptes) {
            @Override
            protected Authentication createSuccessAuthentication(Object principal,
                                                                 Authentication authentification,
                                                                 UserDetails compte) {
                String presente = String.valueOf(authentification.getCredentials());
                if (encodeur.exigeMigration(presente, compte.getPassword())) {
                    comptes.normaliserEmpreinte(compte.getUsername(), encodeur.encode(presente));
                }
                return super.createSuccessAuthentication(principal, authentification, compte);
            }
        };
        fournisseur.setPasswordEncoder(encodeur);
        fournisseur.setPreAuthenticationChecks(compte -> { });
        fournisseur.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return fournisseur;
    }

    /**
     * {@code ROLE_SUPER_ADMINISTRATEUR} implique {@code ROLE_ADMINISTRATEUR}
     * (CdC 5.2.3).
     *
     * <p><b>Pourquoi une hiérarchie plutôt que treize annotations doublées.</b> Un
     * compte ne porte qu'une autorité : {@code UtilisateurDetailsService} fait
     * {@code .roles(typeUtilisateur.name())}. Sans ce bean, le super-administrateur
     * serait refusé partout où l'administrateur est admis, et il faudrait réécrire les
     * <b>treize</b> {@code @PreAuthorize("hasRole('ADMINISTRATEUR')")} du projet plus
     * le matcher {@code /admin/**}. Chaque oubli serait un écran inaccessible, et
     * chaque service ajouté plus tard une occasion de recommencer.</p>
     *
     * <p><b>Deux niveaux seulement, et c'est une décision.</b> {@code ROLE_MEMBRE} n'y
     * figure pas parce qu'il n'est <b>garde nulle part</b> : aucun
     * {@code hasRole('MEMBRE')} n'existe dans le projet, et les écrans personnels
     * ({@code /mes-vehicules}, {@code /panier}, {@code /commandes}) sont couverts par
     * {@code anyRequest().authenticated()} seul. Leur cloisonnement vient de
     * l'<i>ownership</i>, chacun projetant les données du principal. L'inscrire dans la
     * hiérarchie ne changerait donc rien du tout : ce serait du bruit dans une
     * déclaration dont toute la valeur est d'être lisible d'un coup d'oeil.
     * {@code RoleMembreNestPasUneGardeTest} casse la build si une telle garde
     * apparaissait, auquel cas la question se reposerait.</p>
     *
     * <p><b>Ce bean suffit-il vraiment aux deux surfaces ?</b> Depuis Spring Security
     * 6.3, un {@code RoleHierarchy} unique est repris par l'autorisation web ET par la
     * sécurité de méthode. Le projet ne s'en remet pas à cette lecture :
     * {@code HierarchieRolesIT} vérifie les deux surfaces séparément, parce qu'un
     * comportement de framework supposé a déjà coûté trois formulaires publics au
     * projet (la propriété déclarée vide jugée présente).</p>
     */
    @Bean
    public RoleHierarchy hierarchieDesRoles() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("SUPER_ADMINISTRATEUR").implies("ADMINISTRATEUR")
                .build();
    }

    /**
     * Remplace {@code anyRequest().authenticated()} : une connexion PARTIELLE (mot de
     * passe vérifié, second facteur ou changement imposé en attente) satisferait
     * {@code authenticated()}, et ouvrirait {@code /panier}, {@code /mon-compte} et tout
     * écran garde par la seule authentification. Les gardes {@code hasRole}, elles,
     * refusent déjà : le jeton partiel ne porte aucune autorité.
     */
    static AuthorizationManager<RequestAuthorizationContext> authentificationAboutie() {
        AuthenticationTrustResolver confiance = new AuthenticationTrustResolverImpl();
        return (authentification, contexte) -> {
            var courante = authentification.get();
            return new AuthorizationDecision(confiance.isAuthenticated(courante)
                    && !(courante instanceof AuthentificationPartielle));
        };
    }

    @Bean
    public SecurityFilterChain chaineDeFiltres(HttpSecurity http,
                                               EchecAuthentificationHandler echecHandler,
                                               ParcoursConnexion succesHandler,
                                               SessionRegistry registreSessions,
                                               GardeConnexion garde,
                                               Optional<VerificateurTurnstile> turnstile)
            throws Exception {
        http
                .authorizeHttpRequests(acces -> acces
                        // Dispatch d'erreur du conteneur vers /error : la page d'erreur
                        // elle-même. La requête d'origine a déjà été autorisée ou
                        // refusée. Refuser aussi le rendu de son échec renvoyait un
                        // visiteur anonyme à la connexion sur une simple adresse
                        // inconnue, et laissait la page Whitelabel aux autres cas.
                        // Une connexion partielle y est vue comme anonyme
                        // (ConnexionPartielleHorsEtapes filtre aussi ce dispatch).
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // /recherche : le formulaire de recherche est sur les pages du
                        // catalogue, publiques. La recherche l'est donc aussi (F5).
                        .requestMatchers("/", "/accueil", "/services/**", "/pieces/**", "/recherche",
                                "/inscription/**", "/connexion", "/mot-de-passe/**",
                                "/cgv", "/mentions-legales", "/confidentialite", "/garantie-legale",
                                // Texte gelé d'une version de document (F24). Public
                                // pour la même raison que les CGV elles-mêmes : exiger
                                // une connexion pour relire les conditions qu'on a
                                // acceptées serait une entrave sans motif, et le
                                // document n'est le secret de personne. Énuméré par
                                // préfixe et non par joker ouvert sur tout : la route
                                // ne sert que des textes contractuels généraux.
                                "/documents/*/*",
                                // Modèle de formulaire de rétractation (annexe 2 du
                                // livre VI CDE), public comme les CGV qui l'annoncent :
                                // il doit rester utilisable sans compte actif.
                                "/retractation/formulaire", "/retractation/formulaire/pdf",
                                // Page de contact : l'article VI.45 CDE veut que
                                // l'identité du professionnel, son adresse, son
                                // téléphone et son courriel soient accessibles AVANT
                                // que le consommateur ne soit lie, donc avant toute
                                // création de compte. Derrière une authentification,
                                // l'information arriverait après le moment où elle
                                // doit éclairer la décision.
                                "/contact",
                                // Bandeau et gestion des cookies (F25) : la question du
                                // consentement se pose dès la première visite, donc avant
                                // toute connexion. Exiger une authentification pour y
                                // répondre rendrait le refus impossible au visiteur, qui
                                // est justement celui à qui l'on demande.
                                "/cookies", "/cookies/**",
                                // Confirmation de suppression de compte (F23) : à
                                // l'instant où elle s'affiche, la session vient d'être
                                // invalidée et le compte n'existe plus. Une page
                                // authentifiée renverrait vers un formulaire de
                                // connexion que plus aucun identifiant ne satisfait.
                                "/compte-supprime",
                                // Confirmation d'un changement d'adresse (CdC 5.2.2).
                                // Le lien arrive dans la NOUVELLE boîte, souvent
                                // ouverte ailleurs que dans la session du membre.
                                // Exiger une authentification y renverrait vers un
                                // formulaire de connexion portant l ANCIENNE adresse,
                                // et la bascule deviendrait impossible à conclure. Le
                                // jeton porte l'autorisation, comme pour l'activation
                                // de compte. Route ÉNUMÉRÉE et non ouverte par joker :
                                // elle ne sert que cette confirmation.
                                "/changement-adresse/confirmation",
                                // Définition du premier mot de passe d'un administrateur
                                // créé au back-office. Le compte n'a encore AUCUN mot de
                                // passe utilisable : il ne peut pas s'authentifier pour
                                // atteindre cette page. Le jeton du lien porte
                                // l'autorisation : 256 bits, à usage unique, 24 h.
                                "/compte/definition")
                        .permitAll()
                        // Étapes d'une connexion commencée : seule surface ouverte à une
                        // connexion partielle, avec la deconnexion et les ressources
                        // statiques. authenticated() l'admet, là où AuthentificationAboutie
                        // la refuse partout ailleurs.
                        .requestMatchers(EtapeConnexion.PREFIXE, EtapeConnexion.PREFIXE + "/**")
                        .authenticated()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // API publique en LECTURE SEULE (BL-8) et sa documentation.
                        // Anonyme par construction : elle n'expose que ce qui figure
                        // déjà sur le site public, à savoir le catalogue des prestations et
                        // identité commerciale du garage. Aucun verbe d'écriture n'y
                        // est déclaré, donc aucune authentification à exiger. La table
                        // clef_api du socle reste volontairement inexploitée : y
                        // adosser des jetons supposerait quotas et révocation, hors
                        // périmètre V1.
                        //
                        // Les deux routes sont ÉNUMÉRÉES, et non couvertes par un joker
                        // /api/v1/**. Le joker est fail-open : le troisième endpoint,
                        // quel qu'il soit, naîtrait public sans que personne ne l'ait
                        // décidé, y compris un endpoint qui exposerait des données de
                        // membre. L'énumération est fail-closed : ajouter une route
                        // publique devient un geste explicite, vérifiable en revue.
                        // ApiPubliqueIT.surfaceFermeeParDefaut verrouille l'écart entre
                        // les deux écritures (302 attendu, 404 avec le joker).
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/prestations/**", "/api/v1/garages/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // Webhook du prestataire de paiement : appel serveur à serveur,
                        // sans session. L'authenticité ne vient pas d'un jeton mais de
                        // la stratégie sécurité §11 : le payload n'est jamais cru, le
                        // statut est relu auprès du prestataire via la passerelle.
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMINISTRATEUR")
                        // Toute adresse non déclarée publique exige la connexion, y
                        // compris une adresse qui n'existe pas. Répondre 404 à un anonyme
                        // dirait quelles adresses n'existent pas derrière la connexion, et
                        // donc lesquelles existent. La 404 traduite reste servie dans les
                        // zones publiques et aux utilisateurs connectés.
                        .anyRequest().access(authentificationAboutie())
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(new RefusAccesEnEtapes()))
                // Après l'autorisation : ce qui est public voit une connexion partielle
                // comme anonyme. Voir ConnexionPartielleHorsEtapes.
                .addFilterAfter(new ConnexionPartielleHorsEtapes(), AuthorizationFilter.class)
                // Avant l'authentification : voir GardeConnexionFiltre. Le garde et le
                // filtre d'authentification partagent le même matcher, voir plus bas.
                .addFilterBefore(new GardeConnexionFiltre(garde, echecHandler, ENVOI_CONNEXION,
                                turnstile),
                        UsernamePasswordAuthenticationFilter.class)

                .csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/**"))
                .formLogin(formulaire -> formulaire
                        .loginPage("/connexion")
                        .usernameParameter("email")
                        // Conduit les étapes de la connexion, applique la langue du profil
                        // (F6) et mène à la page demandée avant la connexion, à défaut à
                        // la page d'arrivée du rôle.
                        .successHandler(succesHandler)
                        .failureHandler(echecHandler)
                        .permitAll()
                        // Le matcher que Spring déduirait de loginPage est remplacé par celui
                        // du garde : les deux filtres reconnaissent alors exactement les mêmes
                        // requêtes, par construction et non par deux écritures à tenir égales.
                        .withObjectPostProcessor(new ObjectPostProcessor<UsernamePasswordAuthenticationFilter>() {
                            @Override
                            public <O extends UsernamePasswordAuthenticationFilter> O postProcess(O filtre) {
                                filtre.setRequiresAuthenticationRequestMatcher(ENVOI_CONNEXION);
                                return filtre;
                            }
                        })
                )
                // Les sessions authentifiées sont inscrites au registre, pour que la
                // suspension d'un compte puisse couper celles déjà ouvertes. Sans
                // cette ligne, le bean SessionRegistry existerait sans jamais rien
                // apprendre : la révocation ne trouverait aucune session à fermer, et
                // rien ne le signalerait.
                // expiredUrl : sans lui, une session coupée (suspension, activation du
                // second facteur ailleurs) recevait la page texte brute par défaut de
                // Spring Security, en anglais. La connexion est la suite logique.
                .sessionManagement(sessions -> sessions
                        .maximumSessions(-1)
                        .sessionRegistry(registreSessions)
                        .expiredUrl("/connexion"))
                // POST seul : un lien, une image ou une prélecture du navigateur ne doit pas
                // fermer la session (DeconnexionIT). PathPatternRequestMatcher remplace
                // AntPathRequestMatcher, déprécié et promis au retrait.
                .logout(deconnexion -> deconnexion
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, "/deconnexion"))
                        .logoutSuccessUrl("/?deconnecte")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .headers(entetes -> entetes
                        // form-action porte le seul assouplissement de cette politique, et il
                        // conditionne le parcours de paiement : le départ vers le prestataire
                        // est un POST dont la réponse redirige vers Mollie. Chrome et Edge
                        // appliquent form-action à la CIBLE de la redirection qui suit l'envoi
                        // d'un formulaire, et pas seulement à l'action écrite dans le gabarit :
                        // avec 'self' seul, ils refusent de suivre et le membre ne peut pas
                        // payer. Firefox ne l'applique pas, d'où un défaut invisible sur une
                        // partie des navigateurs.
                        // Un seul hôte, et aucun joker : la page Mollie renvoie ensuite vers
                        // les banques depuis SON domaine, hors de notre document et donc hors
                        // de cette politique. Élargir davantage n'aiderait à rien et ouvrirait
                        // l'envoi d'un formulaire du site vers un tiers.
                        // Turnstile : deux hôtes ajoutés, un seul et le même.
                        // script-src pour api.js, frame-src pour la trame du widget.
                        // frame-src et connect-src étaient jusqu'ici ABSENTES et
                        // retombaient sur default-src 'self' : la trame aurait été
                        // bloquée sans que la politique le laisse voir. Les déclarer
                        // rend explicite ce qui n'était qu'un repli.
                        // frame-ancestors reste 'none' : autoriser un tiers à charger
                        // NOS pages dans une trame n'a rien à voir avec charger LA
                        // SIENNE dans les nôtres.
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