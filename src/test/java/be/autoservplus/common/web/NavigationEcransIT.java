package be.autoservplus.common.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Navigation des ecrans : chaque route GET est classee, chaque menu s ouvre, chaque
 * ecran est atteignable par des clics.
 *
 * <p><b>Pourquoi ce test.</b> Le tableau de bord du back-office n avait aucun lien
 * entrant, cinq ecrans n etaient atteints que par leur adresse, un lien menait un
 * administrateur a un 403 et un autre a un 404 — et le {@code verify} restait vert, aucun
 * test n ouvrant ce qu un utilisateur ouvre. Trois volets :</p>
 * <ol>
 *   <li><b>Balayage</b> : les routes GET sont lues dans le mapping de Spring MVC et
 *       confrontees a la table {@link #ROUTES}. Une route absente de la table, ou une
 *       entree sans route, fait echouer le test : une route neuve doit etre classee.
 *       Pour chaque role — anonyme, membre, administrateur, super-administrateur — le
 *       statut attendu est verifie. Les variables de chemin recoivent une reference
 *       aleatoire : le role autorise recoit 404, le role refuse 403 — le refus precede
 *       toute recherche, ce que le volet « refus sans oracle » verifie avec une
 *       ressource reelle.</li>
 *   <li><b>Coherence des menus</b> : la page d arrivee de chaque role est rendue, ses
 *       liens d en-tete, de menu et de cartes sont ouverts un a un ; aucun ne doit
 *       repondre 403, 404 ou 500.</li>
 *   <li><b>Atteignabilite</b> : chaque ecran sans variable figure dans un menu d un role
 *       autorise, ou dans {@link #ATTEINTS_DEPUIS}, qui nomme la page qui y mene — et
 *       le test verifie que cette page porte bien le lien. Les ecrans qu aucun clic
 *       n atteint par construction (lien de courriel, etape de connexion) sont dans
 *       {@link #HORS_NAVIGATION}, avec leur motif.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Navigation des écrans (integration)")
class NavigationEcransIT extends SocleIntegration {

    enum Role { ANONYME, MEMBRE, ADMINISTRATEUR, SUPER_ADMINISTRATEUR }

    /** Qui peut ouvrir la route. */
    enum Acces { PUBLIC, CONNECTE, BACK_OFFICE, SUPER, ETAPE }

    /**
     * Une route GET classee.
     *
     * @param appel           adresse concrete appelee (variables remplacees, parametres requis)
     * @param ecran           page HTML de navigation ; {@code false} pour un fichier, un
     *                        fragment HTMX ou un retour de prestataire
     * @param statutAutorise  statut attendu pour un role autorise
     */
    record Route(String patron, String appel, Acces acces, boolean ecran, int statutAutorise) {

        boolean aVariable() {
            return patron.contains("{");
        }
    }

    private static final String REF = UUID.randomUUID().toString();
    private static final String PERIODE = "?depuis=2026-01-01&jusqua=2026-01-31";

    private static final List<Route> ROUTES = List.of(
            // --- public
            ecran("/", Acces.PUBLIC),
            ecran("/accueil", Acces.PUBLIC),
            ecran("/services", Acces.PUBLIC),
            ecran("/pieces", Acces.PUBLIC),
            ecran("/recherche", Acces.PUBLIC),
            ecran("/contact", Acces.PUBLIC),
            ecran("/cookies", Acces.PUBLIC),
            ecran("/cgv", Acces.PUBLIC),
            ecran("/mentions-legales", Acces.PUBLIC),
            ecran("/confidentialite", Acces.PUBLIC),
            ecran("/connexion", Acces.PUBLIC),
            ecran("/inscription", Acces.PUBLIC),
            ecran("/inscription/renvoyer-verification", Acces.PUBLIC),
            ecran("/mot-de-passe/oublie", Acces.PUBLIC),
            ecran("/compte-supprime", Acces.PUBLIC),
            new Route("/inscription/verification", "/inscription/verification?jeton=inconnu",
                    Acces.PUBLIC, true, 200),
            new Route("/mot-de-passe/nouveau", "/mot-de-passe/nouveau?jeton=inconnu",
                    Acces.PUBLIC, true, 200),
            // Jeton inconnu : 404, la ou les trois autres liens de courriel rendent une page
            // « lien invalide ». Ecart releve, non traite ici.
            new Route("/changement-adresse/confirmation", "/changement-adresse/confirmation?jeton=inconnu",
                    Acces.PUBLIC, true, 404),
            new Route("/compte/definition", "/compte/definition?jeton=inconnu", Acces.PUBLIC, true, 200),
            variable("/services/{reference}", Acces.PUBLIC),
            variable("/pieces/{reference}", Acces.PUBLIC),
            new Route("/documents/{type}/{version}", "/documents/CGV/inconnue", Acces.PUBLIC, true, 404),
            new Route("/images/galerie/{id}", "/images/galerie/999999999", Acces.PUBLIC, false, 404),
            // --- etapes de connexion : une connexion aboutie est renvoyee a sa page d arrivee
            ecran("/connexion/etape", Acces.ETAPE),
            ecran("/connexion/etape/second-facteur", Acces.ETAPE),
            ecran("/connexion/etape/mot-de-passe", Acces.ETAPE),
            ecran("/connexion/etape/enrolement", Acces.ETAPE),
            // --- espace connecte
            ecran("/mon-compte", Acces.CONNECTE),
            ecran("/mon-profil", Acces.CONNECTE),
            ecran("/mon-profil/adresse", Acces.CONNECTE),
            ecran("/mon-profil/securite", Acces.CONNECTE),
            ecran("/mon-profil/securite/enrolement", Acces.CONNECTE),
            new Route("/mon-profil/securite/codes", "/mon-profil/securite/codes", Acces.CONNECTE, true, 302),
            ecran("/mes-vehicules", Acces.CONNECTE),
            ecran("/mes-vehicules/nouveau", Acces.CONNECTE),
            variable("/mes-vehicules/{reference}/modifier", Acces.CONNECTE),
            ecran("/mes-rendez-vous", Acces.CONNECTE),
            ecran("/mes-rendez-vous/nouveau", Acces.CONNECTE),
            new Route("/mes-rendez-vous/nouveau/creneaux", "/mes-rendez-vous/nouveau/creneaux?date=2026-01-05",
                    Acces.CONNECTE, false, 200),
            variable("/mes-rendez-vous/{reference}", Acces.CONNECTE),
            new Route("/mes-rendez-vous/{reference}/agenda.ics", "/mes-rendez-vous/" + REF + "/agenda.ics",
                    Acces.CONNECTE, false, 404),
            variable("/mes-interventions/{reference}", Acces.CONNECTE),
            new Route("/mes-interventions/{reference}/statut", "/mes-interventions/" + REF + "/statut",
                    Acces.CONNECTE, false, 404),
            new Route("/mes-interventions/depuis-rdv/{rdvReference}", "/mes-interventions/depuis-rdv/" + REF,
                    Acces.CONNECTE, true, 404),
            variable("/mes-interventions/{reference}/validation", Acces.CONNECTE),
            variable("/mes-avis/{reference}/nouveau", Acces.CONNECTE),
            ecran("/mes-messages", Acces.CONNECTE),
            ecran("/mes-messages/nouveau", Acces.CONNECTE),
            variable("/mes-messages/{reference}", Acces.CONNECTE),
            ecran("/mes-notifications", Acces.CONNECTE),
            ecran("/mes-donnees", Acces.CONNECTE),
            ecran("/supprimer-mon-compte", Acces.CONNECTE),
            ecran("/panier", Acces.CONNECTE),
            ecran("/panier/vider", Acces.CONNECTE),
            new Route("/commande", "/commande", Acces.CONNECTE, true, 302),
            new Route("/commande/{reference}/retour", "/commande/" + REF + "/retour", Acces.CONNECTE, false, 404),
            variable("/commande/{reference}/confirmation", Acces.CONNECTE),
            ecran("/commandes", Acces.CONNECTE),
            variable("/commandes/{reference}", Acces.CONNECTE),
            // Reference inconnue : page « commande introuvable », rendue en 200.
            new Route("/commandes/{reference}/annulation", "/commandes/" + REF + "/annulation",
                    Acces.CONNECTE, true, 200),
            new Route("/factures/{reference}/pdf", "/factures/" + REF + "/pdf", Acces.CONNECTE, false, 404),
            new Route("/avoirs/{reference}/pdf", "/avoirs/" + REF + "/pdf", Acces.CONNECTE, false, 404),
            new Route("/paiement-fictif/{referencePrestataire}", "/paiement-fictif/inconnue",
                    Acces.CONNECTE, false, 404),
            // --- back-office
            ecran("/admin", Acces.BACK_OFFICE),
            ecran("/admin/rendez-vous", Acces.BACK_OFFICE),
            variable("/admin/rendez-vous/{reference}/refuser", Acces.BACK_OFFICE),
            variable("/admin/rendez-vous/{reference}/annuler", Acces.BACK_OFFICE),
            ecran("/admin/interventions", Acces.BACK_OFFICE),
            // Reference inconnue : retour a la liste, avec message.
            new Route("/admin/interventions/{reference}", "/admin/interventions/" + REF,
                    Acces.BACK_OFFICE, true, 302),
            ecran("/admin/commandes-a-planifier", Acces.BACK_OFFICE),
            variable("/admin/commandes-a-planifier/{reference}", Acces.BACK_OFFICE),
            ecran("/admin/catalogue/prestations", Acces.BACK_OFFICE),
            ecran("/admin/catalogue/prestations/nouvelle", Acces.BACK_OFFICE),
            variable("/admin/catalogue/prestations/{reference}/modifier", Acces.BACK_OFFICE),
            variable("/admin/catalogue/prestations/{reference}/supprimer", Acces.BACK_OFFICE),
            ecran("/admin/catalogue/pieces", Acces.BACK_OFFICE),
            ecran("/admin/catalogue/pieces/nouvelle", Acces.BACK_OFFICE),
            variable("/admin/catalogue/pieces/{reference}/modifier", Acces.BACK_OFFICE),
            variable("/admin/catalogue/pieces/{reference}/supprimer", Acces.BACK_OFFICE),
            // Reference inconnue : galerie vide, rendue en 200 — l ecran ne verifie pas que
            // la fiche existe. Ecart releve, non traite ici.
            new Route("/admin/galerie/{type}/{reference}", "/admin/galerie/interventions/" + REF,
                    Acces.BACK_OFFICE, true, 200),
            ecran("/admin/import", Acces.BACK_OFFICE),
            ecran("/admin/export", Acces.BACK_OFFICE),
            new Route("/admin/export/factures.csv", "/admin/export/factures.csv" + PERIODE,
                    Acces.BACK_OFFICE, false, 200),
            new Route("/admin/export/commandes.csv", "/admin/export/commandes.csv" + PERIODE,
                    Acces.BACK_OFFICE, false, 200),
            ecran("/admin/retractations", Acces.BACK_OFFICE),
            // Reference inconnue : page « demande introuvable », rendue en 200.
            new Route("/admin/retractations/{reference}/refuser", "/admin/retractations/" + REF + "/refuser",
                    Acces.BACK_OFFICE, true, 200),
            ecran("/admin/messages", Acces.BACK_OFFICE),
            variable("/admin/messages/{reference}", Acces.BACK_OFFICE),
            ecran("/admin/avis", Acces.BACK_OFFICE),
            ecran("/admin/comptes", Acces.BACK_OFFICE),
            variable("/admin/comptes/{reference}", Acces.BACK_OFFICE),
            ecran("/admin/journal", Acces.BACK_OFFICE),
            ecran("/admin/comptes/administrateurs", Acces.SUPER),
            ecran("/admin/comptes/administrateurs/nouveau", Acces.SUPER));

    /**
     * Routes GET qui ne sont pas des ecrans de l application : API publique et sa
     * documentation, page d erreur du conteneur. Classees pour que le balayage reste
     * exhaustif ; leur comportement est tenu ailleurs ({@code ApiPubliqueIT},
     * {@code PagesErreurIT}).
     */
    private static final Set<String> HORS_ECRANS_PREFIXES = Set.of("/api/", "/v3/api-docs", "/swagger-ui", "/error");

    /**
     * Ecrans sans variable atteints depuis une autre page que la page d arrivee : la
     * route, la page qui porte le lien, et le role qui l ouvre. Le test rend la page et y
     * cherche le lien.
     */
    private static final Map<String, Atteinte> ATTEINTS_DEPUIS = new LinkedHashMap<>();

    record Atteinte(String page, Role role) {
    }

    static {
        ATTEINTS_DEPUIS.put("/recherche", new Atteinte("/services", Role.ANONYME));
        ATTEINTS_DEPUIS.put("/inscription/renvoyer-verification", new Atteinte("/connexion", Role.ANONYME));
        ATTEINTS_DEPUIS.put("/mot-de-passe/oublie", new Atteinte("/connexion", Role.ANONYME));
        ATTEINTS_DEPUIS.put("/cookies", new Atteinte("/", Role.ANONYME));
        ATTEINTS_DEPUIS.put("/cgv", new Atteinte("/", Role.ANONYME));
        ATTEINTS_DEPUIS.put("/mentions-legales", new Atteinte("/", Role.ANONYME));
        ATTEINTS_DEPUIS.put("/confidentialite", new Atteinte("/", Role.ANONYME));
        ATTEINTS_DEPUIS.put("/mon-profil/adresse", new Atteinte("/mon-profil", Role.MEMBRE));
        ATTEINTS_DEPUIS.put("/mon-profil/securite", new Atteinte("/mon-profil", Role.MEMBRE));
        ATTEINTS_DEPUIS.put("/mon-profil/securite/enrolement", new Atteinte("/mon-profil/securite", Role.MEMBRE));
        ATTEINTS_DEPUIS.put("/mes-vehicules/nouveau", new Atteinte("/mes-vehicules", Role.MEMBRE));
        ATTEINTS_DEPUIS.put("/mes-messages/nouveau", new Atteinte("/mes-messages", Role.MEMBRE));
        ATTEINTS_DEPUIS.put("/admin/catalogue/prestations/nouvelle",
                new Atteinte("/admin/catalogue/prestations", Role.ADMINISTRATEUR));
        ATTEINTS_DEPUIS.put("/admin/catalogue/pieces/nouvelle",
                new Atteinte("/admin/catalogue/pieces", Role.ADMINISTRATEUR));
        ATTEINTS_DEPUIS.put("/admin/comptes/administrateurs/nouveau",
                new Atteinte("/admin/comptes/administrateurs", Role.SUPER_ADMINISTRATEUR));
    }

    /** Ecrans sans variable qu aucun clic n atteint, par construction, avec leur motif. */
    private static final Map<String, String> HORS_NAVIGATION = Map.ofEntries(
            Map.entry("/accueil", "alias de « / »"),
            Map.entry("/compte-supprime", "redirection après la suppression du compte"),
            Map.entry("/inscription/verification", "lien reçu par courriel"),
            Map.entry("/mot-de-passe/nouveau", "lien reçu par courriel"),
            Map.entry("/changement-adresse/confirmation", "lien reçu par courriel"),
            Map.entry("/compte/definition", "lien reçu par courriel"),
            Map.entry("/connexion/etape", "parcours de connexion"),
            Map.entry("/connexion/etape/second-facteur", "parcours de connexion"),
            Map.entry("/connexion/etape/mot-de-passe", "parcours de connexion"),
            Map.entry("/connexion/etape/enrolement", "parcours de connexion"),
            Map.entry("/mon-profil/securite/codes", "redirection après enrôlement ou régénération"),
            Map.entry("/panier/vider", "bouton du panier, affiché seulement s'il contient une ligne"),
            Map.entry("/commande", "bouton du panier, affiché seulement s'il contient une ligne"));

    private static Route ecran(String patron, Acces acces) {
        return new Route(patron, patron, acces, true, acces == Acces.ETAPE ? 302 : 200);
    }

    private static Route variable(String patron, Acces acces) {
        return new Route(patron, patron.replaceAll("\\{[^}]+}", REF), acces, true, 404);
    }

    private static final Map<Role, String> PAGES_ARRIVEE = Map.of(
            Role.ANONYME, "/",
            Role.MEMBRE, "/mon-compte",
            Role.ADMINISTRATEUR, "/admin",
            Role.SUPER_ADMINISTRATEUR, "/admin");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mapping;

    private final Map<Role, Utilisateur> comptes = new LinkedHashMap<>();

    @BeforeAll
    void comptes() {
        comptes.put(Role.MEMBRE, creer("balayage-membre@exemple.be", TypeUtilisateur.MEMBRE));
        comptes.put(Role.ADMINISTRATEUR, creer("balayage-admin@exemple.be", TypeUtilisateur.ADMINISTRATEUR));
        comptes.put(Role.SUPER_ADMINISTRATEUR,
                creer("balayage-chef@exemple.be", TypeUtilisateur.SUPER_ADMINISTRATEUR));
    }

    private Utilisateur creer(String email, TypeUtilisateur type) {
        Utilisateur u = new Utilisateur(email, "{noop}inutilise", "Nom", "Prenom", type);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.saveAndFlush(u);
    }

    private MockHttpServletResponse ouvrir(String adresse, Role role) throws Exception {
        MockHttpServletRequestBuilder requete = get(adresse).locale(Locale.FRENCH);
        if (role != Role.ANONYME) {
            requete = requete.with(user(comptes.get(role).getEmail()).roles(role.name()));
        }
        return mvc.perform(requete).andReturn().getResponse();
    }

    private Set<String> routesGetDuMapping() {
        Set<String> routes = new TreeSet<>();
        for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
            Set<RequestMethod> methodes = info.getMethodsCondition().getMethods();
            if (methodes.isEmpty() || methodes.contains(RequestMethod.GET)) {
                routes.addAll(info.getPatternValues());
            }
        }
        return routes;
    }

    private static boolean horsEcrans(String route) {
        return HORS_ECRANS_PREFIXES.stream().anyMatch(route::startsWith);
    }

    /** Statut attendu pour un role ; une redirection est notee 302. */
    private static int attendu(Route route, Role role) {
        boolean anonyme = role == Role.ANONYME;
        return switch (route.acces()) {
            case PUBLIC -> route.statutAutorise();
            case CONNECTE, ETAPE -> anonyme ? 302 : route.statutAutorise();
            case BACK_OFFICE -> anonyme ? 302 : role == Role.MEMBRE ? 403 : route.statutAutorise();
            case SUPER -> anonyme ? 302 : role == Role.SUPER_ADMINISTRATEUR ? route.statutAutorise() : 403;
        };
    }

    private static boolean autorise(Route route, Role role) {
        int statut = attendu(route, role);
        return statut != 403 && !(role == Role.ANONYME && route.acces() != Acces.PUBLIC);
    }

    /** Liens internes d une page : en-tete, menus et pied, ou la page entiere. */
    private static Set<String> liens(String html, boolean pageEntiere) {
        Document page = Jsoup.parse(html);
        String selecteur = pageEntiere
                ? "a[href], form[method=get][action], form:not([method])[action]"
                : "header a[href], nav a[href], footer a[href]";
        Set<String> liens = new TreeSet<>();
        for (Element element : page.select(selecteur)) {
            String cible = element.hasAttr("href") ? element.attr("href") : element.attr("action");
            if (cible.startsWith("/") && !cible.contains("lang=") && !cible.startsWith("/css/")) {
                liens.add(cible.split("[?#]")[0]);
            }
        }
        return liens;
    }

    @Nested
    @DisplayName("balayage des routes")
    class Balayage {

        @Test
        @DisplayName("toute route GET est classée, et toute entrée de la table existe")
        void tableExhaustive() {
            Set<String> mapping = routesGetDuMapping();
            Set<String> classees = ROUTES.stream().map(Route::patron).collect(Collectors.toCollection(TreeSet::new));

            assertThat(mapping.stream().filter(r -> !horsEcrans(r) && !classees.contains(r)).toList())
                    .as("routes GET non classées : ajoutez-les à ROUTES avec l'accès attendu")
                    .isEmpty();
            assertThat(classees.stream().filter(r -> !mapping.contains(r)).toList())
                    .as("entrées de ROUTES sans route : retirez-les")
                    .isEmpty();
        }

        @Test
        @DisplayName("chaque route répond, pour chaque rôle, le statut attendu")
        void statutsParRole() throws Exception {
            List<String> ecarts = new ArrayList<>();
            for (Route route : ROUTES) {
                for (Role role : Role.values()) {
                    MockHttpServletResponse reponse;
                    try {
                        reponse = ouvrir(route.appel(), role);
                    } catch (Exception e) {
                        // Hors de MockMvc, le conteneur en ferait un 500.
                        ecarts.add(role + " " + route.appel() + " : exception " + e.getMessage());
                        continue;
                    }
                    int attendu = attendu(route, role);
                    boolean conforme = reponse.getStatus() == attendu
                            && (attendu != 302 || role != Role.ANONYME
                                || String.valueOf(reponse.getRedirectedUrl()).endsWith("/connexion"));
                    if (!conforme) {
                        ecarts.add(role + " " + route.appel() + " : " + reponse.getStatus()
                                + " (attendu " + attendu + ") → " + reponse.getRedirectedUrl());
                    }
                }
            }
            assertThat(ecarts).as("statuts inattendus").isEmpty();
        }
    }

    /**
     * Le refus precede toute recherche : sans quoi la difference entre 403 (la ressource
     * existe) et 404 (elle n existe pas) dirait au role refuse quelles references
     * existent. Verifie avec une ressource reelle, pour chaque ecran a variable du
     * back-office dont la ressource se cree en une ligne.
     */
    @Nested
    @DisplayName("refus sans oracle")
    class RefusSansOracle {

        @Test
        @DisplayName("le membre reçoit 403 sur une fiche du back-office, qu'elle existe ou non")
        void memeRefusQueLaRessourceExisteOuNon() throws Exception {
            String reelle = "/admin/comptes/" + comptes.get(Role.MEMBRE).getReference();
            String inventee = "/admin/comptes/" + UUID.randomUUID();

            assertThat(ouvrir(reelle, Role.ADMINISTRATEUR).getStatus()).as("la fiche existe").isEqualTo(200);
            assertThat(ouvrir(reelle, Role.MEMBRE).getStatus()).isEqualTo(403);
            assertThat(ouvrir(inventee, Role.MEMBRE).getStatus()).isEqualTo(403);
        }

        /** Aucun ecran a variable n est garde au seul service : le refus est toujours celui de l URL. */
        @Test
        @DisplayName("les seuls refus posés au service portent sur des écrans sans variable")
        void gardesDeServiceSansVariable() {
            assertThat(ROUTES.stream().filter(r -> r.acces() == Acces.SUPER && r.aVariable()).toList())
                    .as("écran à variable réservé au super-administrateur : ajouter son cas réel ci-dessus")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("cohérence des menus")
    class CoherenceDesMenus {

        @Test
        @DisplayName("chaque lien de la page d'arrivée de chaque rôle s'ouvre sans 403, 404 ni 500")
        void liensDesPagesArrivee() throws Exception {
            List<String> ecarts = new ArrayList<>();
            for (Role role : Role.values()) {
                MockHttpServletResponse arrivee = ouvrir(PAGES_ARRIVEE.get(role), role);
                assertThat(arrivee.getStatus()).as("page d'arrivée de " + role).isEqualTo(200);
                for (String lien : liens(arrivee.getContentAsString(), false)) {
                    int statut = ouvrir(lien, role).getStatus();
                    if (statut == 403 || statut == 404 || statut >= 500) {
                        ecarts.add(role + " " + lien + " : " + statut);
                    }
                }
            }
            assertThat(ecarts).as("liens de menu cassés").isEmpty();
        }

        /**
         * Le droit d acces (art. 15) vaut pour tout titulaire ; la suppression, non : un
         * compte du back-office se ferme par le super-administrateur.
         */
        @Test
        @DisplayName("« Mon compte » d'un rôle du back-office : « Mes données » oui, suppression non")
        void mesDonneesSansSuppressionAuBackOffice() throws Exception {
            for (Role role : List.of(Role.ADMINISTRATEUR, Role.SUPER_ADMINISTRATEUR)) {
                Set<String> liens = liens(ouvrir("/mon-compte", role).getContentAsString(), true);
                assertThat(liens).as(role.name()).contains("/mes-donnees").doesNotContain("/supprimer-mon-compte");
            }
            assertThat(liens(ouvrir("/mon-compte", Role.MEMBRE).getContentAsString(), true))
                    .contains("/mes-donnees", "/supprimer-mon-compte");
        }

        /**
         * Motif « Disclosure Navigation » : le bouton nait cache et replie, il designe le
         * panneau qui porte les liens, et le script qui le revele est externe. Le rendu
         * replie lui-meme releve du navigateur ({@code lot/tests-navigateur}).
         */
        @Test
        @DisplayName("menu repliable : bouton caché sans script, lié au panneau des liens, script externe")
        void menuRepliable() throws Exception {
            for (Role role : Role.values()) {
                Document page = Jsoup.parse(ouvrir(PAGES_ARRIVEE.get(role), role).getContentAsString());
                Element bouton = page.selectFirst("header button.bouton-menu");
                assertThat(bouton).as(role.name()).isNotNull();
                assertThat(bouton.attr("type")).isEqualTo("button");
                assertThat(bouton.hasAttr("hidden")).as("caché tant que le script ne l'a pas révélé").isTrue();
                assertThat(bouton.attr("aria-expanded")).isEqualTo("false");
                assertThat(bouton.text()).isEqualTo("Menu");
                Element panneau = page.getElementById(bouton.attr("aria-controls"));
                assertThat(panneau).as("panneau désigné par aria-controls").isNotNull();
                assertThat(panneau.select("a[href=/mon-compte], a[href=/connexion]")).as(role.name()).isNotEmpty();
                assertThat(page.select("header script[src=/js/menu.js]")).hasSize(1);
                assertThat(page.select("script:not([src])").stream()
                        .filter(s -> !"application/ld+json".equals(s.attr("type")))).as("script en ligne").isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("atteignabilité")
    class Atteignabilite {

        @Test
        @DisplayName("chaque écran sans variable est dans un menu, ou nommé avec la page qui y mène")
        void chaqueEcranEstAtteint() throws Exception {
            Map<Role, Set<String>> menus = new LinkedHashMap<>();
            for (Role role : Role.values()) {
                menus.put(role, liens(ouvrir(PAGES_ARRIVEE.get(role), role).getContentAsString(), false));
            }
            List<String> orphelins = new ArrayList<>();
            for (Route route : ROUTES) {
                if (!route.ecran() || route.aVariable() || HORS_NAVIGATION.containsKey(route.patron())
                        || ATTEINTS_DEPUIS.containsKey(route.patron())) {
                    continue;
                }
                boolean dansUnMenu = menus.entrySet().stream()
                        .anyMatch(e -> autorise(route, e.getKey()) && e.getValue().contains(route.patron()));
                if (!dansUnMenu) {
                    orphelins.add(route.patron());
                }
            }
            assertThat(orphelins).as("écrans atteints par leur seule adresse").isEmpty();
        }

        @Test
        @DisplayName("chaque page nommée porte bien le lien annoncé")
        void pagesNommeesPortentLeLien() throws Exception {
            List<String> ecarts = new ArrayList<>();
            for (Map.Entry<String, Atteinte> entree : ATTEINTS_DEPUIS.entrySet()) {
                Atteinte atteinte = entree.getValue();
                Set<String> liens = liens(ouvrir(atteinte.page(), atteinte.role()).getContentAsString(), true);
                if (!liens.contains(entree.getKey())) {
                    ecarts.add(atteinte.page() + " ne mène pas à " + entree.getKey());
                }
            }
            assertThat(ecarts).isEmpty();
        }

        @Test
        @DisplayName("les listes d'exception ne nomment que des écrans existants, sans variable")
        void listesSansEntreePerimee() {
            Set<String> ecrans = ROUTES.stream().filter(r -> r.ecran() && !r.aVariable())
                    .map(Route::patron).collect(Collectors.toSet());
            assertThat(ATTEINTS_DEPUIS.keySet()).allMatch(ecrans::contains);
            assertThat(HORS_NAVIGATION.keySet()).allMatch(ecrans::contains);
        }
    }
}
