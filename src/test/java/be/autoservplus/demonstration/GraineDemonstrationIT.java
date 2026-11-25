package be.autoservplus.demonstration;

import be.autoservplus.common.numerotation.CompteurAnnuel;
import be.autoservplus.common.numerotation.TypeCompteur;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.GenerateurNumeroAvoir;
import be.autoservplus.facturation.service.GenerateurNumeroFacture;
import be.autoservplus.facturation.service.PdfAvoirService;
import be.autoservplus.facturation.service.PdfFactureService;
import be.autoservplus.identite.mfa.Base32;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.web.PageArrivee;
import be.autoservplus.intervention.service.GenerateurNumeroIntervention;
import be.autoservplus.reservation.service.GenerateurNumeroRdv;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.service.GenerateurNumeroCommande;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * les PDF composés ici n'arrivent pas dans le {@code ./data} du dossier de travail.</p>
 */
@SpringBootTest(properties = "spring.profiles.active=demo")
@AutoConfigureMockMvc
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
     * Seule exception au domaine réservé : l'administrateur de V10, publié au README et
     * suspendu par V45 hors du profil {@code demo}.
     */
    private static final String ADMINISTRATEUR_DE_V10 = "admin@autoservplus.be";

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

    @DynamicPropertySource
    static void dossiersTemporaires(DynamicPropertyRegistry registre) {
        registre.add("autoservplus.facturation.archive", () -> DOSSIER.resolve("factures").toString());
        registre.add("autoservplus.stockage.racine", () -> DOSSIER.resolve("medias").toString());
    }

    @Test
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

    @Test
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
    @DisplayName("chaque compte publié au README se connecte, second facteur compris")
    void comptesDuReadmeSeConnectent() throws Exception {
        List<String> readme = Files.readAllLines(Path.of("README.md"), StandardCharsets.UTF_8);
        Map<String, String> comptes = comptesPublies(readme);
        byte[] secret = Base32.decoder(clePubliee(readme).replace(" ", ""));
        assertThat(comptes).containsKeys(ADMINISTRATEUR_DE_V10);

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
    @DisplayName("chaque photo de la graine a son fichier sous la racine des médias")
    void photosAvecLeurFichier() {
        Path racine = DOSSIER.resolve("medias");
        List<String> chemins = jdbc.queryForList("SELECT chemin FROM photo", String.class);

        assertThat(chemins).hasSize(Math.toIntExact(LIGNES_ATTENDUES.get("photo")));
        assertThat(chemins).allSatisfy(chemin -> assertThat(racine.resolve(chemin)).isRegularFile());
    }

    @Test
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
        lignes.put("avis", 1L);
        lignes.put("avoir", 0L);
        lignes.put("categorie", 11L);
        lignes.put("clef_api", 0L);
        lignes.put("code_secours", 0L);
        lignes.put("commande", 1L);
        lignes.put("compteur_avoir", 0L);
        lignes.put("compteur_devis", 0L);
        lignes.put("compteur_facture", 1L);
        lignes.put("compteur_fil", 1L);
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
        lignes.put("historique_statut_intervention", 13L);
        lignes.put("historique_statut_utilisateur", 2L);
        lignes.put("indisponibilite", 0L);
        lignes.put("intervention", 6L);
        lignes.put("ligne_devis", 0L);
        lignes.put("ligne_intervention", 8L);
        lignes.put("ligne_panier", 2L);
        lignes.put("message", 2L);
        lignes.put("notification", 4L);
        lignes.put("paiement", 1L);
        lignes.put("panier", 1L);
        lignes.put("parametre_atelier", 1L);
        lignes.put("photo", 0L);
        lignes.put("piece", 6L);
        lignes.put("place_parking", 8L);
        lignes.put("plage_ouverture", 11L);
        lignes.put("poste_atelier", 3L);
        lignes.put("rdv", 6L);
        lignes.put("rdv_service", 2L);
        lignes.put("reservation_parking", 0L);
        lignes.put("service", 10L);
        lignes.put("utilisateur", 2L);
        lignes.put("vehicule", 2L);
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
