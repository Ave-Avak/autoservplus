package be.autoservplus.common.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.web.NavigationEcransIT.Role;
import be.autoservplus.devis.service.DevisService;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.reservation.service.DisponibiliteService;
import be.autoservplus.reservation.service.RdvService;
import be.autoservplus.reservation.service.dto.CreneauDisponible;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.Navigateur;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.service.PanierService;
import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.CheckedNode;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

/**
 * Les écrans ouverts dans un vrai navigateur, sur un vrai serveur.
 *
 * <p>Les autres tests lisent le texte d'une page. Aucun ne la met en page, n'exécute ses
 * scripts ni n'applique sa politique de sécurité du contenu : un en-tête qui déborde, un
 * {@code onsubmit} bloqué sans erreur visible ou un menu qui ne se replie plus laissaient
 * la build verte, et seule une recette à la main les montrait. Quatre volets :</p>
 * <ol>
 *   <li>Connexion au clavier, une fois par rôle, second facteur compris. La session
 *       obtenue sert ensuite aux autres volets.</li>
 *   <li>Balayage : chaque écran sans variable de {@link NavigationEcransIT}, plus quelques
 *       écrans à variable sur des données semées, pour chaque rôle et à trois largeurs.
 *       Quatre contrôles par page : aucun débordement horizontal, aucune erreur
 *       JavaScript ni violation de la politique de sécurité, aucun ordre de tabulation
 *       forcé, aucune violation grave des règles d'accessibilité axe-core, aucune région
 *       en double.</li>
 *   <li>Clavier : lien d'évitement, menu repliable, touche Échap.</li>
 *   <li>Scripts : les créneaux chargés par HTMX, la confirmation d'une action
 *       destructive.</li>
 * </ol>
 *
 * <p>Les largeurs sont 320, 768 et 1280 px. 320 px est la largeur du critère WCAG 2.1
 * 1.4.10 (redistribution) : le contenu doit s'y lire sans défilement horizontal. Le
 * critère excepte ce qui exige deux dimensions, un tableau de données par exemple : c'est
 * la page qui est mesurée, et un tableau qui défile dans son conteneur passe.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Écrans dans le navigateur (serveur réel, Chromium)")
class EcransDansLeNavigateurIT extends SocleIntegration {

    /** Largeur du critère WCAG 1.4.10, puis une tablette et un écran de bureau. */
    private static final int ETROIT = 320;
    private static final int MOYEN = 768;
    private static final int LARGE = 1280;

    /** Largeur à laquelle la recette du lot de navigation avait mesuré 916 px de page pour un administrateur. */
    private static final int LARGEUR_DU_DEFAUT_CONSTATE = 657;

    private static final String MOT_DE_PASSE = "phrase de passe des tests du navigateur";
    private static final byte[] SECRET_TOTP = "secret de vingt octs".getBytes(StandardCharsets.US_ASCII);

    /** Une violation de ces niveaux fait échouer la build. Les autres vont au rapport. */
    private static final Set<String> IMPACTS_BLOQUANTS = Set.of("critical", "serious");

    /**
     * Règles hors des étiquettes wcag2a et wcag2aa, jouées en plus et bloquantes quel que
     * soit leur niveau. landmark-unique : chaque conteneur défilant est une région nommée,
     * et deux régions de même nom sur une page font deux repères qu'un lecteur d'écran
     * ne distingue pas.
     */
    private static final List<String> REGLES_EN_PLUS = List.of("landmark-unique");

    /**
     * Écrans retirés du balayage, avec leur motif. Un écran n'entre ici que pour un défaut
     * inscrit au registre de la dette, jamais pour faire passer la build.
     */
    private static final Map<String, String> HORS_BALAYAGE = Map.of();

    @LocalServerPort private int port;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PrestationRepository prestations;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ChiffrementSecretMfa chiffrement;
    @Autowired private Clock horloge;
    @Autowired private TransactionTemplate transactions;
    @Autowired private DisponibiliteService disponibilites;
    @Autowired private RdvService rdvService;
    @Autowired private PanierService panierService;
    @Autowired private DevisService devisService;
    @Autowired private CommandeService commandeService;

    private Navigateur navigateur;
    private final Map<Role, Utilisateur> comptes = new EnumMap<>(Role.class);
    private final Map<Role, String> sessions = new EnumMap<>(Role.class);
    private final Map<Role, List<String>> ecransSemes = new EnumMap<>(Role.class);
    /** Violations d'accessibilité non bloquantes : règle, puis les écrans où elle tombe. */
    private final Map<String, Set<String>> violationsMineures = new TreeMap<>();

    private Prestation prestation;
    private LocalDate jourLibre;
    private Vehicule voitureARetirer;

    @BeforeAll
    void semerEtLancer() {
        Utilisateur membre = MembresDeTest.membre("navigateur-membre@exemple.be", encodeur.encode(MOT_DE_PASSE),
                "Dupont", "Marie");
        comptes.put(Role.MEMBRE, activer(membre));
        comptes.put(Role.ADMINISTRATEUR, avecSecondFacteur(activer(new Utilisateur("navigateur-admin@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Martin", "Paul", TypeUtilisateur.ADMINISTRATEUR))));
        comptes.put(Role.SUPER_ADMINISTRATEUR, avecSecondFacteur(activer(new Utilisateur(
                "navigateur-exploitant@exemple.be", encodeur.encode(MOT_DE_PASSE), "Peeters", "Anne",
                TypeUtilisateur.SUPER_ADMINISTRATEUR))));
        semerLesDonnees(comptes.get(Role.MEMBRE));
        navigateur = new Navigateur(port);
    }

    @AfterAll
    void fermerEtRapporter() throws IOException {
        if (navigateur != null) {
            navigateur.close();
        }
        ecrireLeRapportDAccessibilite();
    }

    // --- 1. connexion ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Role.class, names = "ANONYME", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("connexion au clavier, sans souris, jusqu'à la page d'arrivée")
    void connexionAuClavier(Role role) {
        navigateur.parcourir("apres-connexion-" + role, LARGE, session(role), contexte -> {
            Page page = contexte.newPage();
            Response reponse = page.navigate(role == Role.MEMBRE ? "/mon-compte" : "/admin");
            assertThat(reponse.status()).isEqualTo(200);
            assertThat(page.url()).doesNotContain("/connexion");
        });
    }

    /**
     * La session d'un rôle, ouverte une seule fois, au clavier : adresse, tabulation, mot
     * de passe, Entrée, puis le code du second facteur pour les rôles du back-office. Les
     * champs reçoivent le focus sans clic, ce que le test vérifie avant de frapper.
     */
    private String session(Role role) {
        if (role == Role.ANONYME) {
            return null;
        }
        return sessions.computeIfAbsent(role, r -> {
            String[] etat = new String[1];
            navigateur.parcourir("connexion-" + r, LARGE, null, contexte -> {
                Page page = contexte.newPage();
                page.navigate("/connexion");
                attendreLeFocus(page.locator("#email"), "le champ de l'adresse a le focus à l'ouverture");
                page.keyboard().type(comptes.get(r).getEmail());
                page.keyboard().press("Tab");
                attendreLeFocus(page.locator("#password"), "une tabulation mène au mot de passe");
                page.keyboard().type(MOT_DE_PASSE);
                page.keyboard().press("Enter");
                if (r != Role.MEMBRE) {
                    page.waitForURL("**/connexion/etape/second-facteur");
                    attendreLeFocus(page.locator("#code"), "le champ du code a le focus");
                    page.keyboard().type(Totp.code(SECRET_TOTP, Totp.pasDe(horloge.instant())));
                    page.keyboard().press("Enter");
                }
                page.waitForURL(adresse -> !adresse.contains("/connexion"));
                etat[0] = contexte.storageState();
            });
            return etat[0];
        });
    }

    // --- 2. balayage -------------------------------------------------------------------

    static Stream<Arguments> rolesEtLargeurs() {
        return Stream.of(Role.values()).flatMap(role ->
                Stream.of(ETROIT, MOYEN, LARGE).map(largeur -> Arguments.of(role, largeur)));
    }

    @ParameterizedTest(name = "{0} à {1} px")
    @MethodSource("rolesEtLargeurs")
    @DisplayName("balayage : ni débordement, ni erreur de script, ni violation grave d'accessibilité")
    void balayage(Role role, int largeur) {
        List<String> ecrans = new ArrayList<>(NavigationEcransIT.ecransOuvertsA(role));
        ecrans.addAll(ecransSemes.getOrDefault(role, List.of()));
        ecrans.removeAll(HORS_BALAYAGE.keySet());
        // axe-core coûte près d'une seconde par page. Un écran déjà contrôlé sous un rôle
        // moins privilégié ne l'est pas de nouveau : seul l'en-tête y change, et les écrans
        // propres à chaque rôle le couvrent. Les écrans semés, peu nombreux, le sont toujours.
        Set<String> dejaControles = new TreeSet<>();
        for (Role moindre : Role.values()) {
            if (moindre.ordinal() < role.ordinal()) {
                dejaControles.addAll(NavigationEcransIT.ecransOuvertsA(moindre));
            }
        }
        assertThat(ecrans).as("écrans à balayer pour " + role).isNotEmpty();

        navigateur.parcourir("balayage-" + role + "-" + largeur, largeur, session(role), contexte -> {
            List<String> defauts = new ArrayList<>();
            List<String> journal = new ArrayList<>();
            Page page = contexte.newPage();
            page.onPageError(erreur -> journal.add("erreur JavaScript : " + erreur));
            page.onConsoleMessage(message -> {
                if ("error".equals(message.type())) {
                    journal.add((message.text().contains("Content Security Policy")
                            ? "politique de sécurité du contenu : " : "console : ") + message.text());
                }
            });
            for (String ecran : ecrans) {
                journal.clear();
                List<String> constats = controler(page, ecran, largeur, role, !dejaControles.contains(ecran));
                if (!constats.isEmpty()) {
                    Navigateur.capturer(page, "defaut-" + role + "-" + largeur + "-" + ecran);
                    constats.forEach(constat -> defauts.add(ecran + " : " + constat));
                }
                journal.forEach(ligne -> defauts.add(ecran + " : " + ligne));
            }
            assertThat(defauts).as("défauts de " + role + " à " + largeur + " px").isEmpty();
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Role.class, names = {"ADMINISTRATEUR", "SUPER_ADMINISTRATEUR"})
    @DisplayName("l'en-tête du back-office ne déborde plus à la largeur où la recette l'avait constaté")
    void enTeteDuBackOfficeALaLargeurDuDefaut(Role role) {
        navigateur.parcourir("en-tete-" + role, LARGEUR_DU_DEFAUT_CONSTATE, session(role), contexte -> {
            Page page = contexte.newPage();
            page.navigate("/admin");
            assertThat(debordement(page)).as("débordement de /admin à 657 px").isNull();
        });
    }

    /** Les défauts d'un écran, hors journal de la console, que l'appelant relève lui-même. */
    private List<String> controler(Page page, String ecran, int largeur, Role role, boolean accessibilite) {
        List<String> constats = new ArrayList<>();
        Response reponse = page.navigate(ecran);
        if (reponse == null || reponse.status() != 200) {
            constats.add("statut " + (reponse == null ? "absent" : reponse.status()));
            return constats;
        }
        String deborde = debordement(page);
        if (deborde != null) {
            constats.add(deborde);
        }
        // WCAG 1.4.10 : aucun élément visible ne passe le bord droit, même quand la page ne
        // défile pas (overflow coupé sur un ancêtre, élément en position fixe).
        Object horsCadre = page.evaluate(ELEMENT_HORS_DU_CADRE);
        if (horsCadre != null) {
            constats.add("élément coupé au bord droit : " + horsCadre);
        }
        // Un tabindex positif sort l'élément de l'ordre du document (WCAG 2.4.3).
        int ordreForce = (int) page.evaluate(
                "() => [...document.querySelectorAll('[tabindex]')].filter(e => e.tabIndex > 0).length");
        if (ordreForce > 0) {
            constats.add(ordreForce + " élément(s) avec un tabindex positif");
        }
        // Sous 40em le menu est replié, au-dessus il est déplié : deux mises en page, donc
        // deux passes d'axe-core. 768 px rend la même que 1280 px.
        if (accessibilite && largeur != MOYEN) {
            List<Rule> violations = new ArrayList<>(
                    new AxeBuilder(page).withTags(List.of("wcag2a", "wcag2aa")).analyze().getViolations());
            violations.addAll(new AxeBuilder(page).withRules(REGLES_EN_PLUS).analyze().getViolations());
            for (Rule violation : violations) {
                String regle = violation.getId() + " (" + violation.getImpact() + ") : " + violation.getHelp();
                if (IMPACTS_BLOQUANTS.contains(violation.getImpact()) || REGLES_EN_PLUS.contains(violation.getId())) {
                    constats.add("accessibilité, " + regle + " " + cibles(violation));
                } else {
                    violationsMineures.computeIfAbsent(regle, cle -> new TreeSet<>())
                            .add(role + ", " + largeur + " px, " + ecran + " " + cibles(violation));
                }
            }
        }
        return constats;
    }

    /**
     * Largeur du document comparée à celle de la fenêtre, ou {@code null} si la page tient.
     * Un tableau large dans un conteneur défilant n'élargit pas le document.
     */
    private static String debordement(Page page) {
        @SuppressWarnings("unchecked")
        Map<String, Object> mesure = (Map<String, Object>) page.evaluate(MESURE_DU_DEBORDEMENT);
        int largeurPage = ((Number) mesure.get("page")).intValue();
        int largeurFenetre = ((Number) mesure.get("fenetre")).intValue();
        return largeurPage > largeurFenetre
                ? "débordement horizontal, page de " + largeurPage + " px pour " + largeurFenetre
                        + " px de fenêtre, élément le plus à droite : " + mesure.get("coupable")
                : null;
    }

    /**
     * La mesure, et l'élément dont le bord droit va le plus loin : un échec doit se lire
     * dans le journal de l'intégration continue, où les polices ne sont pas celles du
     * poste et où personne n'a la page sous les yeux. Un élément logé dans un conteneur
     * qui coupe ou fait défiler son contenu n'élargit pas le document : il est écarté.
     * Parmi les éléments de même bord droit, le plus profond est le plus précis.
     */
    private static final String MESURE_DU_DEBORDEMENT = """
            () => {
                const racine = document.documentElement;
                const contenu = element => {
                    for (let parent = element.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
                        if (getComputedStyle(parent).overflowX !== 'visible') {
                            return true;
                        }
                    }
                    return false;
                };
                let coupable = null;
                let bord = racine.clientWidth;
                for (const element of document.body.querySelectorAll('*')) {
                    const cadre = element.getBoundingClientRect();
                    if (cadre.width > 0 && cadre.right >= bord + (coupable ? 0 : 0.5) && !contenu(element)) {
                        coupable = element;
                        bord = cadre.right;
                    }
                }
                const nom = element => element.tagName.toLowerCase()
                    + (element.id ? '#' + element.id : '')
                    + [...element.classList].map(classe => '.' + classe).join('');
                return {
                    page: racine.scrollWidth,
                    fenetre: racine.clientWidth,
                    coupable: coupable
                        ? nom(coupable) + ' dans ' + nom(coupable.parentElement)
                            + ', large de ' + Math.round(coupable.getBoundingClientRect().width)
                            + ' px, bord droit à ' + Math.round(bord) + ' px, texte « '
                            + coupable.textContent.trim().replace(/\\s+/g, ' ').slice(0, 40) + ' »'
                        : 'aucun élément ne dépasse, marge ou défilement du document',
                };
            }
            """;

    /**
     * Premier élément visible dont le bord droit passe celui de la fenêtre, ou {@code null}.
     * Un élément logé dans un conteneur qui coupe ou fait défiler son contenu est écarté,
     * comme pour la mesure du débordement : il défile dans son conteneur.
     */
    private static final String ELEMENT_HORS_DU_CADRE = """
            () => {
                const bord = document.documentElement.clientWidth + 0.5;
                const contenu = element => {
                    for (let parent = element.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
                        if (getComputedStyle(parent).overflowX !== 'visible') {
                            return true;
                        }
                    }
                    return false;
                };
                for (const element of document.body.querySelectorAll('*')) {
                    const cadre = element.getBoundingClientRect();
                    // checkVisibility écarte aussi le contenu d'un <details> fermé et d'un
                    // tiroir caché, que getBoundingClientRect mesure quand même.
                    if (cadre.width > 0 && cadre.height > 0 && cadre.right > bord
                            && element.checkVisibility({visibilityProperty: true}) && !contenu(element)) {
                        return element.tagName.toLowerCase() + [...element.classList].map(c => '.' + c).join('')
                            + ', bord droit à ' + Math.round(cadre.right) + ' px pour ' + Math.round(bord) + ' px';
                    }
                }
                return null;
            }
            """;

    private static String cibles(Rule violation) {
        return violation.getNodes().stream().map(CheckedNode::getTarget).map(String::valueOf).limit(3).toList()
                .toString();
    }

    // --- 3. clavier --------------------------------------------------------------------

    @Test
    @DisplayName("la première tabulation montre le lien d'évitement, qui mène au contenu")
    void lienDEvitement() {
        navigateur.parcourir("lien-d-evitement", LARGE, null, contexte -> {
            Page page = contexte.newPage();
            page.navigate("/services");
            page.keyboard().press("Tab");
            Locator lien = page.locator(".lien-evitement");
            attendreLeFocus(lien, "la première tabulation mène au lien d'évitement");
            assertThat(lien.boundingBox().x).as("le lien entre dans la fenêtre quand il a le focus")
                    .isGreaterThanOrEqualTo(0);
            page.keyboard().press("Enter");
            assertThat(page.url()).endsWith("#contenu");
            page.keyboard().press("Tab");
            attendreLeFocus(page.locator("main :focus"),
                    "la tabulation suivante entre dans le contenu, après l'en-tête");
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Role.class, names = {"ANONYME", "MEMBRE", "ADMINISTRATEUR"})
    @DisplayName("menu replié sous 40em : Entrée le déplie, Échap le referme et rend le focus au bouton")
    void menuRepliableAuClavier(Role role) {
        navigateur.parcourir("menu-" + role, ETROIT, session(role), contexte -> {
            Page page = contexte.newPage();
            page.navigate(role == Role.ADMINISTRATEUR ? "/admin" : "/services");
            Locator bouton = page.locator(".bouton-menu");
            Locator panneau = page.locator("#" + bouton.getAttribute("aria-controls"));
            // Gabarit du site : tiroir de Bootstrap, une fenêtre modale qui piège le focus.
            // Ancien en-tête (écrans pas encore migrés) : panneau à déplier, aria-expanded.
            boolean tiroir = panneau.getAttribute("class").contains("offcanvas");
            assertThat(bouton.isVisible()).as("le bouton est affiché").isTrue();
            if (!tiroir) {
                assertThat(bouton.getAttribute("aria-expanded")).isEqualTo("false");
            }
            assertThat(panneau.isVisible()).as("panneau replié à l'ouverture").isFalse();

            for (int i = 0; i < 20 && !estActif(bouton); i++) {
                page.keyboard().press("Tab");
            }
            attendreLeFocus(bouton, "le bouton s'atteint à la tabulation");
            page.keyboard().press("Enter");
            panneau.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
            if (tiroir) {
                // Bootstrap n'active le piège du focus qu'à la fin de la transition d'ouverture.
                page.locator("#" + bouton.getAttribute("aria-controls") + ".show:not(.showing)").waitFor();
                assertThat(panneau.getAttribute("role")).isEqualTo("dialog");
                assertThat(panneau.getAttribute("aria-modal")).isEqualTo("true");
            } else {
                assertThat(bouton.getAttribute("aria-expanded")).isEqualTo("true");
            }

            page.keyboard().press("Tab");
            attendreLeFocus(panneau.locator(":focus"), "la tabulation suivante entre dans le panneau");
            page.keyboard().press("Escape");
            panneau.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
            if (!tiroir) {
                assertThat(bouton.getAttribute("aria-expanded")).isEqualTo("false");
            }
            attendreLeFocus(bouton, "Échap rend le focus au bouton");
        });
    }

    /**
     * Emplacements du gabarit du site (consigne de I3), mesurés sur la page rendue. À
     * 1280 px : enseigne à gauche de la navigation principale, zone utilitaire centrée dans
     * le tiers droit, ses éléments dans l'ordre langue, recherche, compte, panier. À 320 px :
     * chaque entrée du menu s'atteint dans le tiroir, sans sortir de la fenêtre.
     */
    @ParameterizedTest(name = "{0} sur {1}")
    @MethodSource("rolesEtEcransTemoins")
    @DisplayName("emplacements du gabarit du site à 1280 et 320 px")
    void emplacementsDuGabarit(Role role, String ecran) {
        navigateur.parcourir("emplacements-" + role + "-large", LARGE, session(role), contexte -> {
            Page page = contexte.newPage();
            page.navigate(ecran);
            double enseigne = page.locator(".enseigne").boundingBox().x;
            double navigation = page.locator(".navigation-principale").boundingBox().x;
            assertThat(enseigne).as("enseigne à gauche de la navigation").isLessThan(navigation);

            var zone = page.locator(".zone-utilitaire").boundingBox();
            assertThat(zone.x + zone.width / 2).as("zone utilitaire dans le tiers droit").isGreaterThan(LARGE * 2.0 / 3);

            List<String> ordre = new ArrayList<>(List.of(".choix-langue", ".zone-utilitaire a[href='/recherche']"));
            ordre.add(role == Role.ANONYME ? ".zone-utilitaire a[href='/connexion']" : ".zone-utilitaire .dropdown-toggle");
            if (role == Role.ANONYME) {
                ordre.add(".zone-utilitaire a[href='/inscription']");
            }
            if (role == Role.MEMBRE) {
                ordre.add(".lien-panier");
            }
            double precedent = -1;
            for (String selecteur : ordre) {
                double x = page.locator(selecteur).boundingBox().x;
                assertThat(x).as(selecteur + " après l'élément précédent").isGreaterThan(precedent);
                precedent = x;
            }
        });
        navigateur.parcourir("emplacements-" + role + "-etroit", ETROIT, session(role), contexte -> {
            Page page = contexte.newPage();
            page.navigate(ecran);
            page.locator(".bouton-menu").click();
            Locator panneau = page.locator("#menu-principal");
            // Mesures prises tiroir ouvert, transition terminée : pendant le glissement, les
            // entrées sont encore à droite de la fenêtre.
            page.locator("#menu-principal.show:not(.showing)").waitFor();
            Locator liens = panneau.locator(".navigation-principale a, .zone-utilitaire a:not(.dropdown-item), "
                    + ".zone-utilitaire button:not(.dropdown-item)");
            assertThat(liens.count()).isGreaterThan(3);
            dansLaFenetre(liens);
            // Les entrées de « Mon compte » sont dans une liste repliée : ouverte, elles
            // s'affichent dans le tiroir.
            Locator compte = panneau.locator(".dropdown-toggle");
            if (compte.count() > 0) {
                compte.click();
                dansLaFenetre(panneau.locator(".dropdown-item"));
            }
        });
    }

    private static void dansLaFenetre(Locator entrees) {
        for (int i = 0; i < entrees.count(); i++) {
            var cadre = entrees.nth(i).boundingBox();
            assertThat(cadre).as("entrée " + entrees.nth(i).textContent().strip() + " affichée").isNotNull();
            assertThat(cadre.x + cadre.width).as("entrée dans la fenêtre").isLessThanOrEqualTo(ETROIT);
        }
    }

    static Stream<Arguments> rolesEtEcransTemoins() {
        return Stream.of(Role.ANONYME, Role.MEMBRE)
                .flatMap(role -> Stream.of("/", "/services").map(ecran -> Arguments.of(role, ecran)));
    }

    /**
     * Amélioration progressive : sans script, le tiroir ne s'ouvrirait jamais. La feuille
     * déplie alors le menu à toutes les largeurs (media scripting: none) : chaque entrée est
     * affichée, rien ne déborde.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/", "/services"})
    @DisplayName("sans script, à 320 px, le menu est déplié et la page ne déborde pas")
    void sansScript(String ecran) {
        navigateur.parcourir("sans-script", ETROIT, null, false, contexte -> {
            Page page = contexte.newPage();
            page.navigate(ecran);
            assertThat(page.locator(".bouton-menu").isVisible()).as("bouton sans effet, caché").isFalse();
            assertThat(page.locator(".navigation-principale a[href='/catalogue']").isVisible()).isTrue();
            assertThat(page.locator(".zone-utilitaire a[href='/connexion']").isVisible()).isTrue();
            assertThat(debordement(page)).isNull();
        });
    }

    @Test
    @DisplayName("au-dessus de 40em, le menu est déplié et le bouton n'est pas affiché")
    void menuDeplieSurGrandEcran() {
        navigateur.parcourir("menu-deplie", LARGE, null, contexte -> {
            Page page = contexte.newPage();
            page.navigate("/services");
            Locator bouton = page.locator(".bouton-menu");
            assertThat(bouton.isVisible()).isFalse();
            assertThat(page.locator("#" + bouton.getAttribute("aria-controls")).isVisible()).isTrue();
        });
    }

    /**
     * Heures des prochains créneaux de l'accueil : du texte, ni lien ni apparence de bouton
     * (fond, bordure), puisque le formulaire ne reçoit pas l'heure choisie.
     */
    @Test
    @DisplayName("créneaux de l'accueil : des heures en texte, sans apparence de bouton")
    void heuresDeLAccueilEnTexte() {
        navigateur.parcourir("heures-accueil", LARGE, null, contexte -> {
            Page page = contexte.newPage();
            page.navigate("/");
            Locator heures = page.locator(".carte-creneaux__heures li");
            assertThat(heures.count()).as("créneaux semés").isGreaterThan(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> style = (Map<String, Object>) heures.first().evaluate("""
                    e => { const s = getComputedStyle(e);
                           return {fond: s.backgroundColor, bordure: s.borderTopWidth, liens: e.querySelectorAll('a, button').length}; }""");
            assertThat(style.get("fond")).isEqualTo("rgba(0, 0, 0, 0)");
            assertThat(style.get("bordure")).isEqualTo("0px");
            assertThat(((Number) style.get("liens")).intValue()).isZero();
        });
    }

    /** À 320 px, les heures d'un jour ne laissent aucune heure seule sur sa ligne. */
    @Test
    @DisplayName("créneaux de l'accueil à 320 px : aucune heure seule sur sa ligne")
    void heuresSansOrphelin() {
        navigateur.parcourir("heures-orphelines", ETROIT, null, contexte -> {
            Page page = contexte.newPage();
            page.navigate("/");
            @SuppressWarnings("unchecked")
            List<Number> derniereLigne = (List<Number>) page.evaluate("""
                    () => [...document.querySelectorAll('.carte-creneaux__heures')].map(jour => {
                        const hauts = [...jour.children].map(h => Math.round(h.getBoundingClientRect().top));
                        const bas = Math.max(...hauts);
                        return hauts.length === 1 ? 2 : hauts.filter(h => h === bas).length; })""");
            assertThat(derniereLigne).isNotEmpty().allSatisfy(nombre ->
                    assertThat(nombre.intValue()).as("heures sur la dernière ligne").isGreaterThanOrEqualTo(2));
        });
    }

    /**
     * Les libellés néerlandais sont plus longs : les écrans du gabarit du site, en
     * néerlandais à 320 px, ne débordent pas (un mot composé dans un titre faisait défiler
     * la page de quatre pixels).
     */
    @Test
    @DisplayName("en néerlandais à 320 px, aucun écran du gabarit du site ne déborde")
    void neerlandaisSurTelephone() {
        navigateur.parcourir("neerlandais-320", ETROIT, null, contexte -> {
            Page page = contexte.newPage();
            page.navigate("/?lang=nl");
            List<String> defauts = new ArrayList<>();
            for (String ecran : List.of("/", "/services", "/pieces", "/accessoires", "/entretien")) {
                page.navigate(ecran);
                String deborde = debordement(page);
                if (deborde != null) {
                    defauts.add(ecran + " : " + deborde);
                }
            }
            assertThat(defauts).isEmpty();
        });
    }

    /**
     * Bandeau des cookies sur un petit téléphone (320 x 720) : au plus un tiers de l'écran,
     * et les trois choix de même taille, refuser aussi simple qu'accepter (APD, check-list
     * cookies).
     */
    @Test
    @DisplayName("bandeau des cookies à 320 px : un tiers de l'écran au plus, trois boutons égaux")
    void bandeauDesCookiesSurTelephone() {
        navigateur.parcourir("bandeau-cookies", ETROIT, null, contexte -> {
            Page page = contexte.newPage();
            page.setViewportSize(ETROIT, 720);
            page.navigate("/");
            var bandeau = page.locator(".bandeau-cookies").boundingBox();
            assertThat(bandeau.height).as("hauteur du bandeau").isLessThanOrEqualTo(720 / 3.0);
            Locator boutons = page.locator(".bandeau-cookies__actions > button.btn-cookies, .bandeau-cookies__actions summary.btn-cookies");
            assertThat(boutons.count()).isEqualTo(3);
            var premier = boutons.nth(0).boundingBox();
            for (int i = 1; i < 3; i++) {
                var autre = boutons.nth(i).boundingBox();
                assertThat(autre.width).as("même largeur").isCloseTo(premier.width, within(1.0));
                assertThat(autre.height).as("même hauteur").isCloseTo(premier.height, within(1.0));
            }
        });
    }

    /**
     * Anneau de focus visible au clavier sur les composants de Bootstrap, qui le retirent
     * eux-mêmes : le bouton d'action principale et un lien du menu, contour de 3 px au moins.
     */
    @Test
    @DisplayName("au clavier, le bouton principal et les liens du menu portent l'anneau de focus")
    void anneauDeFocusSurLesComposants() {
        navigateur.parcourir("anneau-de-focus", LARGE, null, contexte -> {
            Page page = contexte.newPage();
            page.navigate("/");
            for (String cible : List.of(".navigation-principale .nav-link", ".btn-action")) {
                for (int i = 0; i < 40 && !(boolean) page.evaluate("s => document.activeElement.matches(s)", cible); i++) {
                    page.keyboard().press("Tab");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> contour = (Map<String, Object>) page.evaluate("""
                        () => { const style = getComputedStyle(document.activeElement);
                                return {style: style.outlineStyle, largeur: parseFloat(style.outlineWidth)}; }""");
                assertThat(contour.get("style")).as("contour de " + cible).isEqualTo("solid");
                assertThat(((Number) contour.get("largeur")).doubleValue()).as("épaisseur").isGreaterThanOrEqualTo(3.0);
            }
        });
    }

    // --- 4. scripts --------------------------------------------------------------------

    @Test
    @DisplayName("réservation : cocher une prestation et choisir un jour charge les créneaux par HTMX")
    void creneauxChargesParHtmx() {
        navigateur.parcourir("creneaux-htmx", LARGE, session(Role.MEMBRE), contexte -> {
            Page page = contexte.newPage();
            List<String> erreurs = new ArrayList<>();
            page.onPageError(erreur -> erreurs.add(erreur));
            page.onConsoleMessage(message -> {
                if ("error".equals(message.type())) {
                    erreurs.add(message.text());
                }
            });
            page.navigate("/mes-rendez-vous/nouveau");
            page.locator("#date").fill(jourLibre.toString());
            Response fragment = page.waitForResponse(
                    reponse -> reponse.url().contains("/mes-rendez-vous/nouveau/creneaux"),
                    () -> page.locator("#p-" + prestation.getReference()).check());
            assertThat(fragment.status()).isEqualTo(200);
            // Le bouton radio est masqué par la feuille de style, son libellé en tient lieu :
            // c'est sa présence dans le document qui est attendue.
            page.locator("#creneaux input[name=debut]").first().waitFor(
                    new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
            assertThat(page.locator("#creneaux input[name=debut]").count()).isPositive();
            assertThat(erreurs).as("erreurs de script ou de politique de sécurité").isEmpty();
        });
    }

    @Test
    @DisplayName("retirer un véhicule : la confirmation porte le message de la page, un refus ne retire rien")
    void confirmationAvantRetrait() {
        navigateur.parcourir("confirmation-retrait", LARGE, session(Role.MEMBRE), contexte -> {
            Page page = contexte.newPage();
            page.navigate("/mes-vehicules");
            Locator formulaire = page.locator(
                    "form.formulaire-suppression[action*='" + voitureARetirer.getReference() + "']");
            String attendu = formulaire.getAttribute("data-message");
            assertThat(attendu).isNotBlank();

            List<String> messages = new ArrayList<>();
            page.onceDialog(boite -> {
                messages.add(boite.message());
                boite.dismiss();
            });
            formulaire.locator("button").click();
            assertThat(messages).as("la boîte s'ouvre, avec le message rédigé par le gabarit")
                    .containsExactly(attendu);
            assertThat(page.url()).endsWith("/mes-vehicules");
            assertThat(formulaire.count()).as("refus : le véhicule reste").isEqualTo(1);

            page.onceDialog(boite -> boite.accept());
            formulaire.locator("button").click();
            // Accord : le véhicule est retiré. L'assertion de Playwright attend le
            // rechargement qui suit l'envoi, là où waitForNavigation est déprécié.
            PlaywrightAssertions.assertThat(formulaire).hasCount(0);
        });
    }

    // --- outillage ---------------------------------------------------------------------

    /**
     * Le focus s'attend, il ne se lit pas une seule fois : après une navigation, le
     * navigateur applique {@code autofocus} un instant après la fin du chargement, et une
     * lecture immédiate de l'élément actif trouve encore le corps de la page.
     */
    private static void attendreLeFocus(Locator element, String constat) {
        assertThatCode(() -> PlaywrightAssertions.assertThat(element).isFocused())
                .as(constat).doesNotThrowAnyException();
    }

    private static boolean estActif(Locator element) {
        return Boolean.TRUE.equals(element.evaluate("e => e === document.activeElement"));
    }

    private Utilisateur activer(Utilisateur compte) {
        compte.confirmerAdresseEmail();
        compte.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.saveAndFlush(compte);
    }

    /** Le back-office exige le second facteur : secret semé, déjà enrôlé. */
    private Utilisateur avecSecondFacteur(Utilisateur compte) {
        ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(SECRET_TOTP, compte.getId());
        compte.commencerEnrolement(chiffre.donnees(), chiffre.version());
        compte.confirmerEnrolement(horloge.instant(), Totp.pasDe(horloge.instant()) - 10);
        return utilisateurs.saveAndFlush(compte);
    }

    /**
     * De quoi ouvrir des écrans à variable : une pièce, deux véhicules, un rendez-vous et
     * une commande. Puis un panier rempli et un devis, pour que le panier montre le choix du
     * véhicule et que la liste des devis ait une ligne. Les prestations, les postes et les
     * plages viennent des migrations.
     */
    private void semerLesDonnees(Utilisateur membre) {
        Piece piece = transactions.execute(statut -> {
            Categorie categorie = categories.save(new Categorie("IT-NAV", "Filtration du navigateur",
                    TypeCategorie.PIECE));
            Piece nouvelle = new Piece(categorie, "IT-NAV-1", "Filtre du navigateur", new BigDecimal("19.99"));
            nouvelle.setQuantiteStock(50);
            return pieces.save(nouvelle);
        });
        Vehicule voiture = vehicules.save(new Vehicule(membre, "1-NAV-001", "VW", "Golf", Motorisation.DIESEL));
        voitureARetirer = vehicules.save(new Vehicule(membre, "1-NAV-002", "Renault", "Clio", Motorisation.ESSENCE));
        prestation = prestations.findByActifTrueOrderByLibelleAsc().get(0);

        CreneauDisponible creneau = null;
        for (int jours = 2; jours < 30 && creneau == null; jours++) {
            LocalDate jour = LocalDate.now(horloge).plusDays(jours);
            List<CreneauDisponible> libres = disponibilites.creneauxDuJour(jour, prestation.getDureeMinutes());
            if (libres.size() > 1) {
                creneau = libres.get(0);
                jourLibre = jour;
            }
        }
        assertThat(creneau).as("un jour ouvert dans le mois, pour semer un rendez-vous").isNotNull();
        Rdv rdv = rdvService.reserver(membre.getEmail(), voiture.getReference(), List.of(prestation.getReference()),
                creneau.debut(), null);

        UUID commande;
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                membre.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
        try {
            panierService.ajouterPiece(membre.getEmail(), piece.getReference(), 2);
            commande = commandeService.passerCommande(membre.getEmail(), true, false, null).reference();
            panierService.ajouterPiece(membre.getEmail(), piece.getReference(), 1);
            devisService.emettre(membre.getEmail(), voiture.getReference());
        } finally {
            SecurityContextHolder.clearContext();
        }

        List<String> publics = List.of("/pieces/" + piece.getReference(), "/services/" + prestation.getReference());
        List<String> duMembre = new ArrayList<>(publics);
        duMembre.addAll(List.of(
                "/commandes/" + commande,
                "/commande/" + commande + "/confirmation",
                "/mes-rendez-vous/" + rdv.getReference(),
                "/mes-rendez-vous/" + rdv.getReference() + "/modifier",
                "/mes-vehicules/" + voiture.getReference() + "/modifier"));
        List<String> duBackOffice = List.of(
                "/admin/commandes/" + commande,
                "/admin/rendez-vous/" + rdv.getReference(),
                "/admin/rendez-vous/" + rdv.getReference() + "/modifier",
                "/admin/comptes/" + membre.getReference());
        ecransSemes.put(Role.ANONYME, publics);
        ecransSemes.put(Role.MEMBRE, duMembre);
        ecransSemes.put(Role.ADMINISTRATEUR, duBackOffice);
        ecransSemes.put(Role.SUPER_ADMINISTRATEUR, duBackOffice);
    }

    /**
     * Les violations qui ne font pas échouer la build, dans un fichier que le rapport du
     * lot reprend. Vide de violations, le fichier le dit : son absence signifierait que
     * le balayage n'a pas tourné.
     */
    private void ecrireLeRapportDAccessibilite() throws IOException {
        StringBuilder rapport = new StringBuilder("# Violations axe-core non bloquantes\n\n")
                .append("Règles wcag2a et wcag2aa. Les violations critical et serious font échouer la build ")
                .append("et ne figurent pas ici.\n\n");
        if (violationsMineures.isEmpty()) {
            rapport.append("Aucune.\n");
        }
        violationsMineures.forEach((regle, ecrans) -> {
            rapport.append("## ").append(regle).append("\n\n");
            ecrans.forEach(ecran -> rapport.append("- ").append(ecran).append('\n'));
            rapport.append('\n');
        });
        Files.createDirectories(Navigateur.SORTIE);
        Files.writeString(Navigateur.SORTIE.resolve("accessibilite-axe.md"), rapport, StandardCharsets.UTF_8);
    }
}
