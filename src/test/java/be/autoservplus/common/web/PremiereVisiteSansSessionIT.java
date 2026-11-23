package be.autoservplus.common.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.cookies.domain.PreferencesCookies;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.JetonLien;
import be.autoservplus.support.SocleIntegration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Visite sans session sur chaque écran public, sur un vrai serveur.
 *
 * <p>Le jeton CSRF d'un formulaire crée la session à sa première lecture, et le conteneur
 * refuse de créer une session une fois la réponse partie : quand le jeton est lu pendant
 * le rendu, après les 8 ko du tampon de réponse, la page sort tronquée, en 200, sans fin
 * de document. MockMvc n'envoie rien avant la fin du rendu et ne connaît pas ce
 * tampon.</p>
 *
 * <p>Deux visiteurs, tous deux sans session. Le premier n'a aucun cookie : le bandeau des
 * cookies, en fin de page, est son premier formulaire. Le second a déjà choisi ses
 * cookies et revient après l'expiration de sa session, ce qui est le cas courant : le
 * bandeau ne s'affiche plus, et le premier formulaire est celui de la page.</p>
 *
 * <p>Deux tampons. Au tampon réel, quatre pages longues prouvent le défaut tel qu'il
 * s'est produit. Mais ce cas ne voit une page que si elle dépasse 8 ko avant son
 * formulaire, ce qui dépend de ses données : la fiche d'une pièce passait avec une
 * description courte et cassait avec une longue. Le second cas fait donc partir la
 * réponse dès son premier octet, par un filtre de test : toute page publique qui lit son
 * jeton pendant le rendu y échoue, quelle que soit sa longueur. C'est ce qui rend
 * {@link FormulairePublic} obligatoire.</p>
 *
 * <p>La liste des écrans vient du classement de {@code NavigationEcransIT}. S'y ajoutent
 * les pages que le classement n'appelle qu'avec une référence ou un jeton inconnu, et qui
 * rendent alors une page d'erreur ou de lien invalide : une vraie fiche de prestation,
 * une vraie fiche de pièce, et les quatre liens reçus par courriel avec un jeton valide.
 * Deux de ces liens rendent un formulaire (nouveau mot de passe, définition du mot de
 * passe), les deux autres un simple message (vérification de l'adresse, changement
 * d'adresse).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PremiereVisiteSansSessionIT.ReponseQuiPartAussitot.class)
@DisplayName("Visite sans session des écrans publics (serveur réel)")
class PremiereVisiteSansSessionIT extends SocleIntegration {

    private static final String CHOIX_DES_COOKIES =
            PreferencesCookies.NOM_COOKIE + "=" + PreferencesCookies.refusTotal().versValeurCookie();
    private static final String BANDEAU = "class=\"bandeau-cookies\"";
    private static final String CODE_PIECE = "IT-VISITE-001";
    /** En-tête par lequel une requête du test demande une réponse qui part aussitôt. */
    private static final String EN_TETE_DEPART_IMMEDIAT = "X-Test-Reponse-Immediate";
    /** Tampon de réponse par défaut de Tomcat, au-delà duquel la réponse part. */
    private static final int TAMPON = 8192;
    /** Témoin posé par le filtre de test quand il a fait partir une réponse. */
    private static volatile boolean departObserve;

    /**
     * Fait partir la réponse dès le premier octet écrit par le gabarit, pour les seules
     * requêtes qui le demandent. Le tampon de Tomcat ne se réduit pas par
     * {@code setBufferSize}, qui ne sait que l'agrandir : la réponse est donc vidée à la
     * première écriture, ce qui revient à un tampon d'un octet. Tout ce qui précède le
     * rendu, advices compris, s'exécute avant, comme en production.
     */
    @TestConfiguration
    static class ReponseQuiPartAussitot {

        @Bean
        FilterRegistrationBean<OncePerRequestFilter> filtreReponseImmediate() {
            OncePerRequestFilter filtre = new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                                FilterChain chaine) throws ServletException, IOException {
                    boolean demande = requete.getHeader(EN_TETE_DEPART_IMMEDIAT) != null;
                    chaine.doFilter(requete, demande ? new DepartAuPremierOctet(reponse) : reponse);
                }
            };
            FilterRegistrationBean<OncePerRequestFilter> enregistrement = new FilterRegistrationBean<>(filtre);
            enregistrement.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return enregistrement;
        }
    }

    private static final class DepartAuPremierOctet extends HttpServletResponseWrapper {

        private PrintWriter ecrivain;

        DepartAuPremierOctet(HttpServletResponse reponse) {
            super(reponse);
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (ecrivain == null) {
                PrintWriter reel = super.getWriter();
                ecrivain = new PrintWriter(new Writer() {
                    private boolean partie;

                    @Override
                    public void write(char[] tampon, int debut, int longueur) throws IOException {
                        reel.write(tampon, debut, longueur);
                        if (!partie && longueur > 0) {
                            partie = true;
                            departObserve = true;
                            reel.flush();
                            flushBuffer();
                        }
                    }

                    @Override
                    public void flush() {
                        reel.flush();
                    }

                    @Override
                    public void close() {
                        reel.close();
                    }
                });
            }
            return ecrivain;
        }
    }

    @LocalServerPort private int port;
    @Autowired private PrestationRepository prestations;
    @Autowired private PieceRepository pieces;
    @Autowired private CategorieRepository categories;
    @Autowired private TransactionTemplate transactions;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private Clock horloge;

    @Nested
    @DisplayName("Tampon réel de 8192 octets")
    class TamponReel {

        /**
         * Trois des quatre pages où le défaut a été constaté, et où il se reproduit sur une
         * version sans le correctif. La page de contact n'y figure plus : ses commentaires de
         * gabarit ne partant plus dans la page, elle tient sous le tampon.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"/services", "/cgv", "/confidentialite"})
        @DisplayName("sans aucun cookie, une page longue arrive entière, bandeau compris")
        void pageLongue(String adresse) throws Exception {
            HttpResponse<String> reponse;
            try {
                reponse = ouvrir(adresse, null, false);
            } catch (IOException coupure) {
                // Le conteneur ferme la connexion au milieu de l'envoi : le client ne reçoit
                // pas la fin du corps annoncé par morceaux.
                throw new AssertionError("réponse coupée avant la fin sur " + adresse, coupure);
            }
            String page = reponse.body();

            assertThat(reponse.statusCode()).isEqualTo(200);
            assertThat(page).as("bandeau des cookies sur " + adresse).contains(BANDEAU, "name=\"_csrf\"");
            assertThat(page.stripTrailing()).as("fin du document sur " + adresse).endsWith("</html>");
            assertThat(page).as("identifiant de session dans une adresse sur " + adresse).doesNotContain("jsessionid");
            // Sans cette condition, une page raccourcie passerait sous le tampon et le cas
            // ne traverserait plus le défaut qu'il garde.
            int avantLeBandeau = page.substring(0, page.indexOf(BANDEAU)).getBytes(StandardCharsets.UTF_8).length;
            assertThat(avantLeBandeau).as("octets envoyés avant le bandeau sur " + adresse).isGreaterThan(TAMPON);
        }
    }

    @Nested
    @DisplayName("Réponse qui part dès le premier octet")
    class DepartImmediat {

        @Test
        @DisplayName("sans aucun cookie : chaque écran public arrive entier, bandeau compris")
        void sansAucunCookie() throws Exception {
            assertThat(defauts(null)).isEmpty();
        }

        @Test
        @DisplayName("choix des cookies déjà fait, session expirée : chaque écran public arrive entier")
        void choixDejaFait() throws Exception {
            assertThat(defauts(CHOIX_DES_COOKIES)).isEmpty();
        }

        /** Garde-fou du filtre : s'il cessait d'agir, les deux cas ci-dessus passeraient sans rien prouver. */
        @Test
        @DisplayName("le filtre de test fait bien partir la réponse avant la fin du rendu")
        void leFiltreAgit() throws Exception {
            departObserve = false;
            HttpResponse<String> entiere = ouvrir("/cgv", CHOIX_DES_COOKIES, false);
            assertThat(departObserve).as("aucun départ forcé sans l'en-tête").isFalse();
            HttpResponse<String> immediate = ouvrir("/cgv", CHOIX_DES_COOKIES, true);
            assertThat(departObserve).as("départ forcé au premier octet").isTrue();
            assertThat(immediate.body()).isEqualTo(entiere.body());
        }
    }

    /** Adresse appelée et statut attendu, dans l'ordre du classement. */
    private Map<String, Integer> ecrans() {
        Map<String, Integer> ecrans = new LinkedHashMap<>();
        for (NavigationEcransIT.Route route : NavigationEcransIT.ecransPublics()) {
            ecrans.put(route.appel(), route.statutAutorise());
        }
        assertThat(ecrans).as("écrans publics du classement").hasSizeGreaterThan(15);
        transactions.executeWithoutResult(statut -> {
            ecrans.put("/services/" + prestations.findByActifTrueOrderByLibelleAsc().get(0).getReference(), 200);
            Piece piece = pieces.findAll().stream()
                    .filter(p -> CODE_PIECE.equals(p.getReferenceFabricant())).findFirst()
                    .orElseGet(() -> {
                        Categorie categorie = categories.save(
                                new Categorie("IT-VISITE", "Visite sans session", TypeCategorie.PIECE));
                        Piece neuve = new Piece(categorie, CODE_PIECE, "Pièce de la visite sans session",
                                new BigDecimal("12.50"));
                        neuve.setQuantiteStock(5);
                        return pieces.save(neuve);
                    });
            ecrans.put("/pieces/" + piece.getReference(), 200);
            liensAJetonValide().forEach(adresse -> ecrans.put(adresse, 200));
        });
        return ecrans;
    }

    /**
     * Les quatre liens reçus par courriel, chacun avec un jeton valide sur un compte neuf.
     * La vérification et le changement d'adresse consomment leur jeton à l'ouverture :
     * les comptes sont recréés à chaque appel, donc pour chacun des deux visiteurs.
     */
    private List<String> liensAJetonValide() {
        Instant echeance = horloge.instant().plus(Duration.ofHours(1));
        String suffixe = UUID.randomUUID().toString().substring(0, 8);

        JetonLien verification = JetonLien.tirer();
        Utilisateur inscrit = compte("visite-inscrit-" + suffixe, TypeUtilisateur.MEMBRE, false);
        inscrit.enregistrerJetonVerification(verification.empreinte(), echeance);

        JetonLien reinitialisation = JetonLien.tirer();
        Utilisateur oublieux = compte("visite-oubli-" + suffixe, TypeUtilisateur.MEMBRE, true);
        oublieux.preparerReinitialisation(reinitialisation.empreinte(), echeance);

        JetonLien changement = JetonLien.tirer();
        Utilisateur demenage = compte("visite-adresse-" + suffixe, TypeUtilisateur.MEMBRE, true);
        demenage.demanderChangementEmail("visite-nouvelle-" + suffixe + "@exemple.be", changement.empreinte(), echeance);

        JetonLien definition = JetonLien.tirer();
        Utilisateur administrateur = compte("visite-admin-" + suffixe, TypeUtilisateur.ADMINISTRATEUR, true);
        administrateur.preparerDefinitionMotDePasse(definition.empreinte(), echeance);

        utilisateurs.saveAllAndFlush(List.of(inscrit, oublieux, demenage, administrateur));
        return List.of(
                "/inscription/verification?jeton=" + verification.clair(),
                "/mot-de-passe/nouveau?jeton=" + reinitialisation.clair(),
                "/changement-adresse/confirmation?jeton=" + changement.clair(),
                "/compte/definition?jeton=" + definition.clair());
    }

    private static Utilisateur compte(String nom, TypeUtilisateur type, boolean adresseVerifiee) {
        Utilisateur compte = new Utilisateur(nom + "@exemple.be", "$2a$12$h", "Visite", "Sans session", type);
        if (adresseVerifiee) {
            compte.confirmerAdresseEmail();
        }
        return compte;
    }

    /** Un défaut par écran qui n'arrive pas entier, pour les nommer tous d'un coup. */
    private List<String> defauts(String cookie) throws InterruptedException {
        List<String> defauts = new ArrayList<>();
        for (Map.Entry<String, Integer> ecran : ecrans().entrySet()) {
            String adresse = ecran.getKey();
            try {
                HttpResponse<String> reponse = ouvrir(adresse, cookie, true);
                String page = reponse.body();
                // Un jeton ne s'écrit pas dans le message d'un échec.
                adresse = adresse.replaceAll("jeton=[^&]{20,}", "jeton=(valide)");
                if (reponse.statusCode() != ecran.getValue()) {
                    defauts.add(adresse + " : statut " + reponse.statusCode() + " au lieu de " + ecran.getValue());
                } else if (!page.stripTrailing().endsWith("</html>")) {
                    defauts.add(adresse + " : document sans fin");
                } else if (page.contains("jsessionid")) {
                    defauts.add(adresse + " : identifiant de session dans une adresse");
                } else if (cookie == null && !page.contains(BANDEAU)) {
                    defauts.add(adresse + " : bandeau des cookies absent");
                } else if (cookie != null && page.contains(BANDEAU)) {
                    defauts.add(adresse + " : bandeau des cookies affiché malgré le choix");
                }
            } catch (IOException coupure) {
                // Le conteneur ferme la connexion au milieu de l'envoi.
                defauts.add(adresse.replaceAll("jeton=[^&]{20,}", "jeton=(valide)") + " : réponse coupée avant la fin");
            }
        }
        return defauts;
    }

    /** Un client neuf par appel : aucune session ne passe d'une page à l'autre. */
    private HttpResponse<String> ouvrir(String adresse, String cookie, boolean departImmediat)
            throws IOException, InterruptedException {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + adresse))
                .header("Accept", "text/html").header("Accept-Language", "fr").GET();
        if (cookie != null) {
            requete.header("Cookie", cookie);
        }
        if (departImmediat) {
            requete.header(EN_TETE_DEPART_IMMEDIAT, "oui");
        }
        return HttpClient.newHttpClient().send(requete.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
