package be.autoservplus.vitrine.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Gabarit du site sur les écrans migrés : les mêmes entrées dans le même ordre sur chaque
 * page, selon l'état de la connexion, la page en cours marquée, une seule action orange.
 *
 * <p>Consigne de I3 : enseigne, puis Catalogue, Prendre rendez-vous et Contact, puis la
 * zone utilitaire (langue, recherche, compte), le panier tout à droite pour le membre.
 * Aucune entrée ne disparaît d'une page à l'autre (WCAG 3.2.3).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Gabarit du site (écrans migrés)")
class GabaritDuSiteIT extends SocleIntegration {

    enum Etat { VISITEUR, MEMBRE, ADMINISTRATEUR, SUPER_ADMINISTRATEUR }

    /** Écrans servis dans le nouveau gabarit, liste qui grandit à chaque écran migré. */
    static final List<String> ECRANS_MIGRES = List.of("/", "/services", "/pieces", "/accessoires", "/entretien", "/contact",
            "/inscription", "/inscription/confirmee", "/connexion");

    /** Écrans de la rubrique Catalogue, seuls à marquer son entrée de menu. */
    private static final List<String> LISTES_DU_CATALOGUE = List.of("/services", "/pieces", "/accessoires", "/entretien");

    private static final List<String> NAVIGATION_PRINCIPALE = List.of("Catalogue", "Prendre rendez-vous", "Contact");

    /** Zone utilitaire attendue, dans l'ordre, après les trois liens de langue. */
    private static final Map<Etat, List<String>> ZONE_UTILITAIRE = new EnumMap<>(Map.of(
            Etat.VISITEUR, List.of("Rechercher", "Connexion", "Créer un compte"),
            Etat.MEMBRE, List.of("Rechercher", "Messages", "Notifications", "Mon compte"),
            Etat.ADMINISTRATEUR, List.of("Rechercher", "Notifications", "Back-office", "Mon compte"),
            Etat.SUPER_ADMINISTRATEUR, List.of("Rechercher", "Back-office", "Mon compte")));

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;

    private final Map<Etat, String> comptes = new EnumMap<>(Etat.class);

    @BeforeAll
    void comptes() {
        comptes.put(Etat.MEMBRE, creer("gabarit-membre@exemple.be", TypeUtilisateur.MEMBRE));
        comptes.put(Etat.ADMINISTRATEUR, creer("gabarit-admin@exemple.be", TypeUtilisateur.ADMINISTRATEUR));
        comptes.put(Etat.SUPER_ADMINISTRATEUR, creer("gabarit-chef@exemple.be", TypeUtilisateur.SUPER_ADMINISTRATEUR));
    }

    private String creer(String email, TypeUtilisateur type) {
        Utilisateur u = new Utilisateur(email, "{noop}inutilise", "Nom", "Prenom", type);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.saveAndFlush(u).getEmail();
    }

    private Document ouvrir(String adresse, Etat etat) throws Exception {
        MockHttpServletRequestBuilder requete = get(adresse).locale(Locale.FRENCH);
        if (etat != Etat.VISITEUR) {
            requete = requete.with(user(comptes.get(etat)).roles(etat.name()));
        }
        String html = mvc.perform(requete).andReturn().getResponse().getContentAsString();
        return Jsoup.parse(html);
    }

    static Stream<Arguments> etatsEtEcrans() {
        return Stream.of(Etat.values()).flatMap(etat -> ECRANS_MIGRES.stream().map(ecran -> Arguments.of(etat, ecran)));
    }

    /**
     * Nom accessible d'un lien, sans son compteur : le texte, libellés réservés au lecteur
     * d'écran compris, pastilles aria-hidden retirées.
     */
    private static String nom(Element lien) {
        Element copie = lien.clone();
        copie.select("[aria-hidden=true]").remove();
        return copie.text().replaceAll("\\s*\\(\\d+\\)$", "").strip();
    }

    @ParameterizedTest(name = "{0} sur {1}")
    @MethodSource("etatsEtEcrans")
    @DisplayName("mêmes entrées dans le même ordre sur chaque écran, selon l'état de la connexion")
    void entreesParEtat(Etat etat, String ecran) throws Exception {
        Document page = ouvrir(ecran, etat);
        Element navigation = page.selectFirst("header.entete-site nav[aria-label=Navigation principale]");
        assertThat(navigation).as("en-tête du site sur " + ecran).isNotNull();

        assertThat(navigation.selectFirst("a.enseigne").attr("href")).isEqualTo("/");
        assertThat(navigation.select(".navigation-principale a").stream().map(Element::text))
                .containsExactlyElementsOf(NAVIGATION_PRINCIPALE);

        assertThat(navigation.select(".zone-utilitaire .choix-langue a").stream().map(a -> a.attr("lang")))
                .containsExactly("fr", "nl", "en");
        assertThat(navigation.select(".zone-utilitaire > li > a, .zone-utilitaire > li > .nav-link").stream()
                .filter(a -> !a.parent().hasClass("choix-langue"))
                .map(GabaritDuSiteIT::nom))
                .containsExactlyElementsOf(ZONE_UTILITAIRE.get(etat));

        boolean panier = navigation.selectFirst("a.lien-panier") != null;
        assertThat(panier).as("panier pour le membre seul").isEqualTo(etat == Etat.MEMBRE);
        if (panier) {
            assertThat(navigation.selectFirst(".container > a.lien-panier + button.bouton-menu"))
                    .as("panier en dernier, juste avant le bouton du menu replié").isNotNull();
        }
        assertThat(page.select("a[href=/aide]")).as("aucune entrée Aide tant que E13 n'existe pas").isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ecrans")
    @DisplayName("Catalogue marqué sur les quatre listes, Contact sur sa page, rien de marqué ailleurs")
    void rubriqueMarquee(String ecran) throws Exception {
        Document page = ouvrir(ecran, Etat.VISITEUR);
        Element catalogue = page.selectFirst(".navigation-principale a[href=/catalogue]");
        if (ecran.equals("/contact")) {
            assertThat(page.select(".navigation-principale a[aria-current]")).extracting(lien -> lien.attr("href"))
                    .containsExactly("/contact");
        } else if (LISTES_DU_CATALOGUE.contains(ecran)) {
            assertThat(catalogue.attr("aria-current")).isEqualTo("true");
        } else {
            assertThat(page.select(".navigation-principale a[aria-current]")).as("rien de marqué sur " + ecran).isEmpty();
        }
    }

    static Stream<String> ecrans() {
        return ECRANS_MIGRES.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ecrans")
    @DisplayName("au plus une action orange par écran, téléphone et horaires au bandeau, pied commun")
    void actionPrincipaleEtPied(String ecran) throws Exception {
        Document page = ouvrir(ecran, Etat.VISITEUR);
        assertThat(page.select(".btn-action")).hasSizeLessThanOrEqualTo(1);
        assertThat(page.select(".bandeau-garage a[href^=tel:]")).hasSize(1);
        assertThat(page.selectFirst(".bandeau-garage__etat span").text())
                .as("état du jour au bandeau").containsPattern("\\d{2}:\\d{2}");
        assertThat(page.selectFirst(".mention-demonstration")).as("mention des profils de démonstration").isNotNull();

        Element pied = page.selectFirst("footer.pied-site");
        assertThat(pied.select("a").stream().map(a -> a.attr("href"))).contains(
                "/cgv", "/mentions-legales", "/confidentialite", "/garantie-legale", "/retractation/formulaire",
                "/contact", "/cookies");
        assertThat(pied.selectFirst(".pied-site__bas").children().last().text())
                .as("« Propulsé par AutoServ+ » en dernier").startsWith("Propulsé par");
        assertThat(pied.selectFirst(".pied-site__propulse img").attr("alt")).isEqualTo("AutoServ+");
    }

    @Test
    @DisplayName("accueil : garage en JSON-LD AutoRepair, avis avec la règle de publication")
    void accueil() throws Exception {
        Document page = ouvrir("/", Etat.VISITEUR);

        JsonNode garage = new ObjectMapper().readTree(page.selectFirst("script[type=application/ld+json]").data());
        assertThat(garage.get("@type").asText()).isEqualTo("AutoRepair");
        assertThat(garage.get("name").asText()).isEqualTo("Bielle Bleue");
        assertThat(garage.get("telephone").asText()).isNotBlank();
        assertThat(garage.has("aggregateRating")).isFalse();

        assertThat(page.title()).endsWith(" | Bielle Bleue");
        assertThat(page.text()).contains("Seuls les clients dont l'intervention est terminée peuvent publier un avis.");
        assertThat(page.select(".accueil-ouverture .btn-action").attr("href")).isEqualTo("/mes-rendez-vous/nouveau");
    }
}
