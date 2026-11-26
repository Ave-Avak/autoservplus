package be.autoservplus.demonstration;

import be.autoservplus.common.numerotation.CompteurAnnuel;
import be.autoservplus.common.numerotation.TypeCompteur;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.GenerateurNumeroAvoir;
import be.autoservplus.facturation.service.GenerateurNumeroFacture;
import be.autoservplus.facturation.service.PdfAvoirService;
import be.autoservplus.facturation.service.PdfFactureService;
import be.autoservplus.galerie.service.dto.PhotoVue;
import be.autoservplus.identite.mfa.Base32;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.web.PageArrivee;
import be.autoservplus.intervention.service.GenerateurNumeroIntervention;
import be.autoservplus.reservation.service.GenerateurNumeroRdv;
import be.autoservplus.stockage.service.DimensionsImage;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.service.GenerateurNumeroCommande;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La graine de démonstration, jouée sur une base vierge par l'application elle-même, sous
 * le profil {@code demo}.
 *
 * <p>Les fichiers de {@code db/demo} ne sont chargés que sous ce profil. Sans ce test, une
 * colonne {@code NOT NULL} sans défaut ou un CHECK resserré dans {@code db/migration}
 * casserait la graine sans qu'aucune build ne le dise, et on ne le verrait qu'au démarrage
 * de la démonstration, ou au gel en régénérant le dump. Le contexte démarre ici comme sur
 * un poste de démonstration : Flyway lit les deux dossiers, et les composants propres au
 * profil s'exécutent ({@code SecondFacteurDemonstration}).</p>
 *
 * <p>Le profil est posé par {@code spring.profiles.active} et non par
 * {@code @ActiveProfiles}, qui s'ajouterait au {@code dev} par défaut
 * d'{@code application.yml} : le test tournerait alors sous deux profils, ce que la
 * démonstration ne fait jamais.</p>
 *
 * <p>L'archive des factures et la racine des médias pointent vers un dossier temporaire :
 * les PDF composés ici n'arrivent pas dans le {@code ./data} du dossier de travail. Il est
 * supprimé après la classe.</p>
 *
 * <p>L'ordre des cas est fixé, le compte des lignes en premier. Les cas suivants se
 * connectent, tirent des numéros et composent des PDF : une connexion peut écrire au
 * journal de sécurité, et le compte doit lire la graine seule, quel que soit l'ordre
 * que JUnit choisirait.</p>
 */
@SpringBootTest(properties = "spring.profiles.active=demo")
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Graine de démonstration sur une base vierge, sous le profil demo (intégration)")
class GraineDemonstrationIT extends SocleIntegration {

    private static final Path DOSSIER = dossierTemporaire();

    /**
     * Lignes attendues dans chaque table une fois la graine jouée. Toute table du schéma
     * doit y figurer : une table ajoutée sans être comptée fait échouer le test, pour
     * qu'un lot qui touche le schéma ou la graine décide de ce que la démonstration y
     * porte. Les lots de la graine mettent ces valeurs à jour.
     */
    private static final Map<String, Long> LIGNES_ATTENDUES = lignesAttendues();

    /** Domaine réservé aux essais (RFC 2606) : aucune adresse de la graine ne peut joindre quelqu'un. */
    private static final String DOMAINE_RESERVE = ".test";

    /**
     * Seule exception au domaine réservé : l'administrateur de V10, suspendu par V45 hors
     * du profil {@code demo} et par V910 sous ce profil.
     */
    private static final String ADMINISTRATEUR_DE_V10 = "admin@autoservplus.be";

    /** Administrateur de la démonstration (V910), publié au README. */
    private static final String ADMINISTRATEUR_DE_LA_GRAINE = "admin@bielle-bleue.test";

    private static final Pattern LIGNE_DE_COMPTE =
            Pattern.compile("^\\| [^|]+ \\| `([^`]+)` \\| `([^`]+)` \\|$");
    private static final Pattern CLE_TOTP = Pattern.compile("^\\| `([A-Z2-7 ]+)` \\| SHA-1");
    private static final Pattern EMPREINTE_COUT_12 = Pattern.compile("^\\$2[aby]\\$12\\$.{53}$");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private Clock horloge;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private FactureRepository factures;
    @Autowired private AvoirRepository avoirs;
    @Autowired private PdfFactureService pdfFactures;
    @Autowired private PdfAvoirService pdfAvoirs;
    @Autowired private GenerateurNumeroCommande numerosCommande;
    @Autowired private GenerateurNumeroIntervention numerosIntervention;
    @Autowired private GenerateurNumeroRdv numerosRdv;
    @Autowired private GenerateurNumeroFacture numerosFacture;
    @Autowired private GenerateurNumeroAvoir numerosAvoir;
    @Autowired private CompteurAnnuel compteurs;

    @AfterAll
    static void supprimerLeDossierTemporaire() throws IOException {
        FileSystemUtils.deleteRecursively(DOSSIER);
    }

    @DynamicPropertySource
    static void dossiersTemporaires(DynamicPropertyRegistry registre) {
        registre.add("autoservplus.facturation.archive", () -> DOSSIER.resolve("factures").toString());
        registre.add("autoservplus.stockage.racine", () -> DOSSIER.resolve("medias").toString());
    }

    @Test
    @Order(2)
    @DisplayName("chaque fichier de db/migration et de db/demo est appliqué, sans échec")
    void toutesLesMigrationsSontAppliquees() throws IOException {
        Set<String> fichiers = new TreeSet<>();
        fichiers.addAll(versionsDu("db/migration"));
        fichiers.addAll(versionsDu("db/demo"));

        Set<String> appliquees = new TreeSet<>(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL", String.class));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Long.class)).isZero();
        assertThat(appliquees).isEqualTo(fichiers);
    }

    @Test
    @Order(1)
    @DisplayName("chaque table porte le nombre de lignes attendu, et aucune table n'est oubliée")
    void lignesParTable() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """, String.class);
        Map<String, Long> lues = new LinkedHashMap<>();
        tables.forEach(table -> lues.put(table,
                jdbc.queryForObject("SELECT count(*) FROM \"" + table + "\"", Long.class)));

        assertThat(lues).isEqualTo(LIGNES_ATTENDUES);
    }

    /**
     * Règle des prix de la démonstration : les pièces de la composition achetées au catalogue
     * plus la pose seule coûtent plus que le forfait, qui coûte plus que la pose seule. La pose
     * seule n'est proposée que lorsque les pièces de la composition dépassent 20,00 €. Prix
     * comparés TVA comprise, tels que le site les affiche.
     */
    @Test
    @Order(8)
    @DisplayName("pièces plus pose seule, puis forfait, puis pose seule : ordre strict sur chaque prestation")
    void ordreDesPrixDesForfaits() {
        List<Map<String, Object>> forfaits = jdbc.queryForList("""
                SELECT s.code,
                       round(s.prix_htva * (1 + s.taux_tva / 100), 2) AS forfait,
                       round(s.prix_pose_seule_htva * (1 + s.taux_tva / 100), 2) AS pose_seule,
                       coalesce(sum(cp.quantite * round(p.prix_htva * (1 + p.taux_tva / 100), 2)), 0) AS pieces
                FROM service s
                LEFT JOIN composition_prestation cp ON cp.service_id = s.id
                LEFT JOIN piece p ON p.id = cp.piece_id
                WHERE s.actif AND s.unite = 'FORFAIT'
                GROUP BY s.code, s.prix_htva, s.prix_pose_seule_htva, s.taux_tva
                """);
        assertThat(forfaits).isNotEmpty();
        List<String> ecarts = new ArrayList<>();
        for (Map<String, Object> ligne : forfaits) {
            BigDecimal forfait = (BigDecimal) ligne.get("forfait");
            BigDecimal poseSeule = (BigDecimal) ligne.get("pose_seule");
            BigDecimal pieces = (BigDecimal) ligne.get("pieces");
            boolean proposable = pieces.compareTo(new BigDecimal("20.00")) > 0;
            if ((poseSeule != null) != proposable) {
                ecarts.add(ligne.get("code") + " : pose seule " + poseSeule + " pour " + pieces + " € de pièces");
            } else if (poseSeule != null && !(pieces.add(poseSeule).compareTo(forfait) > 0
                    && forfait.compareTo(poseSeule) > 0)) {
                ecarts.add(ligne.get("code") + " : pièces " + pieces + ", pose seule " + poseSeule
                        + ", forfait " + forfait);
            }
        }
        assertThat(ecarts).isEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("le prochain numéro de chaque document est libre après la graine")
    void numerosLibresApresLaGraine() {
        Map<String, String> prochains = new LinkedHashMap<>();
        // Facture, note de crédit, devis et fil passent par un compteur qui exige une
        // transaction. Elle est annulée : le compteur garde sa valeur, et le compte des
        // lignes reste celui de la graine pour les autres cas de la classe.
        TransactionTemplate annulee = new TransactionTemplate(transactions);
        annulee.executeWithoutResult(etat -> {
            prochains.put("commande", numerosCommande.prochain());
            prochains.put("intervention", numerosIntervention.prochain());
            prochains.put("rdv", numerosRdv.prochain());
            prochains.put("facture", numerosFacture.prochain().valeur());
            prochains.put("avoir", numerosAvoir.prochain().valeur());
            prochains.put("devis", compteurs.prochainNumero(TypeCompteur.DEVIS));
            prochains.put("conversation", compteurs.prochainNumero(TypeCompteur.FIL));
            etat.setRollbackOnly();
        });

        prochains.forEach((table, numero) -> assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM " + table + " WHERE numero = ?", Long.class, numero))
                .as("numéro %s déjà porté par la graine dans %s", numero, table)
                .isZero());
    }

    @Test
    @Order(6)
    @DisplayName("chaque facture et chaque note de crédit de la graine se compose en PDF")
    void documentsDeLaGraineEnPdf() {
        List<String> composes = new ArrayList<>();
        // Une transaction par document, comme la requête qui le sert : la composition
        // lit la commande ou l'intervention par des associations paresseuses.
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.execute(etat -> factures.findAll()).forEach(lue -> transaction.executeWithoutResult(etat -> {
            var facture = factures.findById(lue.getId()).orElseThrow();
            assertThat(new String(pdfFactures.pdfDe(facture), 0, 5, StandardCharsets.US_ASCII))
                    .as("PDF de la facture %s", facture.getNumero()).isEqualTo("%PDF-");
            composes.add(facture.getNumero());
        }));
        transaction.execute(etat -> avoirs.findAll()).forEach(lu -> transaction.executeWithoutResult(etat -> {
            var avoir = avoirs.findById(lu.getId()).orElseThrow();
            assertThat(new String(pdfAvoirs.pdfDe(avoir), 0, 5, StandardCharsets.US_ASCII))
                    .as("PDF de la note de crédit %s", avoir.getNumero()).isEqualTo("%PDF-");
            composes.add(avoir.getNumero());
        }));

        assertThat(composes).hasSize(Math.toIntExact(
                LIGNES_ATTENDUES.get("facture") + LIGNES_ATTENDUES.get("avoir")));
    }

    @Test
    @Order(7)
    @DisplayName("chaque compte publié au README se connecte, second facteur compris")
    void comptesDuReadmeSeConnectent() throws Exception {
        List<String> readme = Files.readAllLines(Path.of("README.md"), StandardCharsets.UTF_8);
        Map<String, String> comptes = comptesPublies(readme);
        byte[] secret = Base32.decoder(clePubliee(readme).replace(" ", ""));
        assertThat(comptes).containsKey(ADMINISTRATEUR_DE_LA_GRAINE).doesNotContainKey(ADMINISTRATEUR_DE_V10);
        assertThat(jdbc.queryForObject("SELECT statut FROM utilisateur WHERE email = ?", String.class,
                ADMINISTRATEUR_DE_V10)).as("administrateur de V10 suspendu sous la graine").isEqualTo("SUSPENDU");

        for (Map.Entry<String, String> compte : comptes.entrySet()) {
            MvcResult motDePasse = mvc.perform(post("/connexion").with(csrf())
                            .param("email", compte.getKey()).param("password", compte.getValue()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            MockHttpSession session = (MockHttpSession) motDePasse.getRequest().getSession(false);
            String destination = motDePasse.getResponse().getRedirectedUrl();

            if ("/connexion/etape/second-facteur".equals(destination)) {
                destination = mvc.perform(post("/connexion/etape/second-facteur").session(session).with(csrf())
                                .param("code", Totp.code(secret, Totp.pasDe(horloge.instant()))))
                        .andExpect(status().is3xxRedirection())
                        .andReturn().getResponse().getRedirectedUrl();
            }

            assertThat(destination).as("arrivée de %s", compte.getKey())
                    .isIn(PageArrivee.MEMBRE, PageArrivee.BACK_OFFICE);
            mvc.perform(get(destination).session(session)).andExpect(status().isOk());
        }
    }

    @Test
    @Order(4)
    @DisplayName("chaque photo de la graine a ses deux fichiers, aux dimensions déclarées, et ses trois textes")
    void photosAvecLeurFichier() {
        Path racine = DOSSIER.resolve("medias");
        List<Map<String, Object>> photos = jdbc.queryForList("""
                SELECT chemin, chemin_vignette, largeur, hauteur, texte_alt_nl, texte_alt_en FROM photo
                """);

        assertThat(photos).hasSize(Math.toIntExact(LIGNES_ATTENDUES.get("photo")));
        assertThat(photos).allSatisfy(photo -> {
            Path original = racine.resolve((String) photo.get("chemin"));
            Path vignette = racine.resolve((String) photo.get("chemin_vignette"));
            assertThat(original).isRegularFile();
            assertThat(vignette).isRegularFile();
            assertThat(DimensionsImage.lire(Files.readAllBytes(original)))
                    .as("dimensions déclarées de %s", original.getFileName())
                    .contains(new DimensionsImage((Integer) photo.get("largeur"), (Integer) photo.get("hauteur")));
            assertThat(DimensionsImage.lire(Files.readAllBytes(vignette)))
                    .contains(new DimensionsImage(PhotoVue.LARGEUR_VIGNETTE, 360));
            assertThat((String) photo.get("texte_alt_nl")).isNotBlank();
            assertThat((String) photo.get("texte_alt_en")).isNotBlank();
        });
    }

    @Test
    @Order(9)
    @DisplayName("une pièce sans photo propre montre le visuel de sa catégorie, servi avec sa vignette")
    void visuelDeCategorieSurLaFiche() throws Exception {
        String piece = jdbc.queryForObject("""
                SELECT p.reference::text FROM piece p JOIN categorie c ON c.id = p.categorie_id
                 WHERE p.reference_fabricant = 'BB-P020' AND c.code = 'P_BATTERIE'
                """, String.class);
        String visuel = jdbc.queryForObject("""
                SELECT ph.reference::text FROM photo ph JOIN categorie c ON c.id = ph.categorie_id
                 WHERE c.code = 'P_BATTERIE'
                """, String.class);

        String fiche = mvc.perform(get("/pieces/{r}", piece)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(fiche).contains("/images/galerie/" + visuel + "/vignette 480w");
        mvc.perform(get("/images/galerie/{p}", visuel)).andExpect(status().isOk());
        mvc.perform(get("/images/galerie/{p}/vignette", visuel)).andExpect(status().isOk());

        // Dans la liste, rien ne se répète de carte en carte : chaque photo affichée est la
        // photo propre d'un article, et une carte sans photo n'a aucun visuel, sa catégorie
        // en texte au-dessus du nom.
        String liste = mvc.perform(get("/pieces")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(liste).doesNotContain("/images/galerie/" + visuel);
        Document cartes = Jsoup.parse(liste);
        List<String> sources = cartes.select(".carte-article__image").eachAttr("src");
        assertThat(sources).doesNotHaveDuplicates();
        List<Element> sansPhoto = cartes.select(".carte-article:not(:has(.carte-article__image))");
        assertThat(sansPhoto).isNotEmpty().allSatisfy(carte -> {
            assertThat(carte.select("svg, img")).isEmpty();
            Element corps = carte.selectFirst(".carte-article__corps");
            assertThat(corps).isNotNull();
            assertThat(corps.child(0).hasClass("carte-article__categorie")).isTrue();
            assertThat(corps.child(1).hasClass("carte-article__nom")).isTrue();
        });
    }

    @Test
    @Order(14)
    @DisplayName("liste des prestations : première vignette en priorité, les autres différées, 480 px au téléphone")
    void vignettesDeLaListe() throws Exception {
        String liste = mvc.perform(get("/services")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<Element> images = Jsoup.parse(liste).select(".carte-article__image");
        assertThat(images).hasSizeGreaterThan(3);
        assertThat(images.get(0).attr("fetchpriority")).isEqualTo("high");
        assertThat(images.get(0).hasAttr("loading")).isFalse();
        assertThat(images.subList(1, images.size())).allSatisfy(image ->
                assertThat(image.attr("loading")).isEqualTo("lazy"));
        assertThat(images).allSatisfy(image -> {
            assertThat(image.attr("sizes")).endsWith(" 274px");
            assertThat(image.hasAttr("width") && image.hasAttr("height")).isTrue();
        });
    }

    @Test
    @Order(13)
    @DisplayName("articles rangés : joint et bouchon de vidange au moteur, chaînes à neige à l'hiver")
    void articlesRanges() {
        assertThat(jdbc.queryForList("""
                SELECT c.code FROM piece p JOIN categorie c ON c.id = p.categorie_id
                WHERE p.reference_fabricant IN ('BB-P005', 'BB-P006') ORDER BY p.reference_fabricant""", String.class))
                .containsExactly("P_MOTEUR", "P_MOTEUR");
        assertThat(jdbc.queryForObject("""
                SELECT c.code FROM piece p JOIN categorie c ON c.id = p.categorie_id
                WHERE p.reference_fabricant = 'BB-A025'""", String.class)).isEqualTo("A_HIVER");
    }

    @Test
    @Order(13)
    @DisplayName("avis de la graine : note sur les cartes à partir de trois avis, moyenne de 4,5 à l'accueil")
    void avisDeLaGraine() throws Exception {
        assertThat(jdbc.queryForList("""
                SELECT s.code, count(*) FROM avis a
                JOIN ligne_intervention l ON l.intervention_id = a.intervention_id
                JOIN service s ON s.id = l.service_id
                WHERE a.publie GROUP BY s.code ORDER BY s.code""").stream()
                .collect(Collectors.toMap(l -> (String) l.get("code"), l -> (Long) l.get("count"))))
                .containsExactlyInAnyOrderEntriesOf(Map.of("VIDANGE_STD", 3L, "REVISION_ANNUELLE", 4L,
                        "PLAQUETTES_AV", 3L, "RECHARGE_CLIM", 2L, "MONTAGE_PNEU", 1L));
        // Chaque avis porte sur une intervention terminée de son auteur, règle de dépôt du module.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM avis a
                JOIN intervention i ON i.id = a.intervention_id
                JOIN vehicule v ON v.id = i.vehicule_id
                WHERE i.statut <> 'TERMINEE' OR v.membre_id <> a.membre_id""", Long.class)).isZero();

        Document liste = Jsoup.parse(mvc.perform(get("/services").header("Accept-Language", "fr")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        Map<String, String> notes = new HashMap<>();
        for (Element carte : liste.select(".carte-article")) {
            Element note = carte.selectFirst(".carte-article__note");
            if (note != null) {
                notes.put(carte.selectFirst(".carte-article__nom").text(), note.text());
            }
        }
        assertThat(notes).hasSize(3).containsValues("4,7 sur 5 (3 avis)", "4,8 sur 5 (4 avis)");

        String accueil = mvc.perform(get("/").header("Accept-Language", "fr")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(accueil).contains("4,5 sur 5").contains("13 avis publiés.");
    }

    @Test
    @Order(12)
    @DisplayName("tarifs de l'accueil : six forfaits rangés de 1 à 6, la vidange en premier")
    void prestationsDeLAccueil() {
        assertThat(jdbc.queryForList(
                "SELECT code FROM service WHERE ordre_vitrine IS NOT NULL ORDER BY ordre_vitrine", String.class))
                .containsExactly("VIDANGE_STD", "REVISION_ANNUELLE", "PLAQUETTES_AV", "RECHARGE_CLIM",
                        "MONTAGE_PNEU", "DIAG_ELEC");
    }

    @Test
    @Order(11)
    @DisplayName("le plan du quartier est livré en WebP, en deux tailles")
    void planDuQuartier() throws IOException {
        Map<String, DimensionsImage> attendus = Map.of(
                "plan-quartier.webp", new DimensionsImage(1024, 768),
                "plan-quartier-480.webp", new DimensionsImage(PhotoVue.LARGEUR_VIGNETTE, 360));
        for (Map.Entry<String, DimensionsImage> attendu : attendus.entrySet()) {
            Resource fichier = new PathMatchingResourcePatternResolver()
                    .getResource("classpath:demonstration/plan/" + attendu.getKey());
            assertThat(fichier.exists()).as(attendu.getKey()).isTrue();
            assertThat(DimensionsImage.lire(fichier.getContentAsByteArray())).contains(attendu.getValue());
        }
    }

    @Test
    @Order(10)
    @DisplayName("sous le profil demo, les mentions légales disent d'où viennent les photos")
    void mentionDesPhotos() throws Exception {
        String page = mvc.perform(get("/mentions-legales").locale(Locale.FRENCH)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(page).contains("licence Unsplash").contains("docs/credits-photos.md");
    }

    @Test
    @Order(10)
    @DisplayName("sous le profil demo, la page de contact montre le plan fictif, servi par l'application")
    void planDeLaPageDeContact() throws Exception {
        String page = mvc.perform(get("/contact").locale(Locale.FRENCH)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("src=\"/images/plan/plan-quartier.webp\"").contains("Plan fictif")
                .contains("contributeurs d&#39;OpenStreetMap");

        mvc.perform(get("/images/plan/plan-quartier-480.webp")).andExpect(status().isOk());
    }

    @Test
    @Order(3)
    @DisplayName("comptes de la graine : adresses dans un domaine réservé, empreintes BCrypt de coût 12")
    void comptesSansAdresseReelle() {
        Map<String, String> empreintes = new HashMap<>();
        jdbc.query("SELECT email, mot_de_passe_hache FROM utilisateur",
                ligne -> { empreintes.put(ligne.getString(1), ligne.getString(2)); });

        assertThat(empreintes.keySet()).allSatisfy(adresse -> assertThat(
                adresse.endsWith(DOMAINE_RESERVE) || adresse.equals(ADMINISTRATEUR_DE_V10))
                .as("adresse %s hors du domaine réservé", adresse).isTrue());
        assertThat(empreintes.values()).allSatisfy(empreinte -> assertThat(empreinte).matches(EMPREINTE_COUT_12));
    }

    private static Map<String, String> comptesPublies(List<String> readme) {
        Map<String, String> comptes = new LinkedHashMap<>();
        boolean dansLeTableau = false;
        for (String ligne : readme) {
            if (ligne.startsWith("| Rôle | Adresse | Mot de passe |")) {
                dansLeTableau = true;
                continue;
            }
            if (dansLeTableau && !ligne.startsWith("|")) {
                break;
            }
            Matcher compte = LIGNE_DE_COMPTE.matcher(ligne);
            if (dansLeTableau && compte.matches()) {
                comptes.put(compte.group(1), compte.group(2));
            }
        }
        assertThat(comptes).as("comptes de démonstration lus dans le README").isNotEmpty();
        return comptes;
    }

    private static String clePubliee(List<String> readme) {
        return readme.stream().map(CLE_TOTP::matcher).filter(Matcher::find)
                .map(cle -> cle.group(1)).findFirst()
                .orElseThrow(() -> new AssertionError("clé TOTP absente du README"));
    }

    private static Set<String> versionsDu(String dossier) throws IOException {
        Set<String> versions = new TreeSet<>();
        Pattern nom = Pattern.compile("^V(\\d+)__.+\\.sql$");
        for (Resource fichier : new PathMatchingResourcePatternResolver()
                .getResources("classpath:" + dossier + "/V*.sql")) {
            Matcher version = nom.matcher(fichier.getFilename());
            assertThat(version.matches()).as("nom de migration %s", fichier.getFilename()).isTrue();
            versions.add(version.group(1));
        }
        return versions;
    }

    private static Map<String, Long> lignesAttendues() {
        Map<String, Long> lignes = new LinkedHashMap<>();
        lignes.put("avis", 13L);
        lignes.put("avoir", 0L);
        lignes.put("categorie", 24L);
        lignes.put("clef_api", 0L);
        lignes.put("code_secours", 0L);
        lignes.put("commande", 1L);
        lignes.put("compteur_avoir", 0L);
        lignes.put("compteur_commande", 1L);
        lignes.put("compteur_devis", 0L);
        lignes.put("compteur_facture", 1L);
        lignes.put("compteur_fil", 1L);
        lignes.put("compteur_intervention", 1L);
        lignes.put("compteur_rdv", 1L);
        lignes.put("composition_prestation", 32L);
        lignes.put("consentement", 3L);
        lignes.put("conversation", 1L);
        lignes.put("demande_annulation", 0L);
        lignes.put("devis", 0L);
        lignes.put("encaissement", 0L);
        lignes.put("evenement_securite_compte", 0L);
        lignes.put("facture", 1L);
        lignes.put("historique_modification_catalogue", 0L);
        lignes.put("historique_modification_commande", 0L);
        lignes.put("historique_modification_compte", 0L);
        lignes.put("historique_piece_incluse", 0L);
        lignes.put("historique_statut_intervention", 49L);
        lignes.put("historique_statut_utilisateur", 3L);
        lignes.put("indisponibilite", 0L);
        lignes.put("intervention", 18L);
        lignes.put("ligne_devis", 0L);
        lignes.put("ligne_intervention", 20L);
        lignes.put("ligne_panier", 2L);
        lignes.put("message", 2L);
        lignes.put("notification", 4L);
        lignes.put("paiement", 1L);
        lignes.put("panier", 1L);
        lignes.put("parametre_atelier", 1L);
        lignes.put("photo", 50L);
        lignes.put("piece", 99L);
        lignes.put("piece_incluse", 0L);
        lignes.put("place_parking", 8L);
        lignes.put("plage_ouverture", 11L);
        lignes.put("poste_atelier", 3L);
        lignes.put("rdv", 6L);
        lignes.put("rdv_service", 2L);
        lignes.put("reservation_parking", 0L);
        lignes.put("service", 31L);
        lignes.put("utilisateur", 15L);
        lignes.put("vehicule", 14L);
        lignes.put("version_document", 24L);
        return Collections.unmodifiableMap(lignes);
    }

    private static Path dossierTemporaire() {
        try {
            return Files.createTempDirectory("graine-demonstration");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
