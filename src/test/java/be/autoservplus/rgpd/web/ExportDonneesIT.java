package be.autoservplus.rgpd.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PanierRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Export du droit d acces de bout en bout (F22), sur un PostgreSQL reel.
 *
 * <p>Ce que les tests unitaires ne peuvent pas prouver, et qui se joue ici :
 * <ul>
 *   <li>l <b>etancheite</b> reelle, cote SQL. Deux membres ont chacun un vehicule,
 *       une commande, un rendez-vous, une intervention et un consentement ; le
 *       document produit pour l un ne doit porter aucune trace de l autre. Les
 *       bouchons d un test unitaire rendent ce qu on leur dicte — seule une vraie
 *       requete valide les clauses {@code WHERE};</li>
 *   <li>les {@code JOIN FETCH} du module : {@code open-in-view} est desactive et
 *       les entites sont converties en objets de transfert, un chargement paresseux
 *       oublie se verrait ici ;</li>
 *   <li>le rendu reel du template et la resolution des cles i18n.</li>
 * </ul>
 *
 * <p>Chaque test emploie une <b>adresse distincte</b> : la limite de 24 heures vit
 * dans un composant singleton, que le rollback transactionnel de la base ne remet
 * pas a zero entre deux methodes.
 *
 * <p>Les requetes fixent la locale francaise, MockMvc etant anglophone par defaut.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@DisplayName("Export RGPD (integration)")
class ExportDonneesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String MDP_MARIE = "MotDePasseDeMarie2026";
    private static final String MDP_JEAN = "MotDePasseDeJean2026";

    @PersistenceContext private EntityManager entites;

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private ConsentementRepository consentements;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PrestationRepository prestations;
    @Autowired private PanierRepository paniers;
    @Autowired private CommandeRepository commandes;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private InterventionRepository interventions;
    @Autowired private FactureService facturation;

    private final ObjectMapper lecteur = new ObjectMapper();

    /** Numero legal attribue a la facture de chaque membre, releve a la creation. */
    private final Map<String, String> numerosFactures = new HashMap<>();

    private Piece plaquettes;
    private Piece ampoule;
    private Prestation vidange;
    private PosteAtelier poste;

    @BeforeEach
    void setUp() {
        Categorie freinage = categories.save(
                new Categorie("IT-RGPD-FRE", "Freinage", TypeCategorie.PIECE));
        plaquettes = new Piece(freinage, "IT-RGPD-001", "Plaquettes avant",
                new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(50);
        plaquettes = pieces.save(plaquettes);
        ampoule = new Piece(freinage, "IT-RGPD-002", "Ampoule H7", new BigDecimal("10.01"));
        ampoule.setTauxTva(new BigDecimal("6.00"));
        ampoule.setQuantiteStock(50);
        ampoule = pieces.save(ampoule);

        Categorie entretien = categories.save(
                new Categorie("IT-RGPD-ENT", "Entretien", TypeCategorie.SERVICE));
        vidange = prestations.save(new Prestation(entretien, "IT-RGPD-VID", "Vidange",
                new BigDecimal("49.00"), 30));

        poste = postes.save(new PosteAtelier("Pont RGPD"));
    }

    /**
     * Cree un membre complet : vehicule, consentement horodate avec adresse IP,
     * commande convertie d un panier, rendez-vous et intervention. Chaque section
     * de l export a ainsi de quoi se remplir — et de quoi fuir, si l etancheite
     * etait defaillante.
     */
    private Utilisateur membreComplet(String email, String motDePasse, String plaque,
                                      String marque, String numeroDossier, String ip,
                                      Instant debutRdv) {
        Utilisateur membre = utilisateurs.save(new Utilisateur(email,
                encodeur.encode(motDePasse), "Nom" + marque, "Prenom" + marque,
                TypeUtilisateur.MEMBRE));

        Vehicule vehicule = vehicules.save(
                new Vehicule(membre, plaque, marque, "Modele", Motorisation.DIESEL));

        consentements.save(Consentement.acceptation(membre, TypeDocumentConsentement.CGV,
                Consentement.CGV_VERSION_COURANTE, ip, Instant.parse("2026-03-01T07:59:00Z")));

        Panier panier = new Panier(membre);
        panier.ajouterPiece(plaquettes, 2);
        paniers.save(panier);
        Commande commande = commandes.save(new Commande("CMD-" + numeroDossier, membre,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                Instant.parse("2026-03-01T08:00:00Z")));
        commande.reprendreLignes(panier.getLignes());
        // Les lignes changent de rattachement (panier_id -> commande_id) : le flush
        // pousse ce changement de cle etrangere avant que l'export ne relise.
        paniers.flush();

        // Commande payee puis facturee par le vrai service : la facture porte un
        // numero legal attribue par le compteur, que le test ne fabrique pas.
        commande.confirmerPaiement(Instant.parse("2026-03-01T08:05:00Z"));
        commandes.saveAndFlush(commande);
        numerosFactures.put(email,
                facturation.emettrePourCommande(commande.getReference()).getNumero());

        // Article laisse au panier apres la commande : une piece DIFFERENTE, sinon
        // l'ajout retrouverait la ligne deja rattachee a la commande (la collection
        // du panier n'est pas purgee) et se heurterait a son immuabilite comptable.
        panier.ajouterPiece(ampoule, 3);
        paniers.flush();

        Rdv rdv = rdvs.save(new Rdv("RDV-" + numeroDossier, membre, vehicule, poste,
                debutRdv, Duration.ofMinutes(30), List.of(vidange), "Bruit a l avant"));
        interventions.save(new Intervention("INT-" + numeroDossier, rdv));

        // Le contexte de persistance est vide avant l'export : en production celui-ci
        // s'execute dans sa propre transaction et relit tout depuis la base. Sans ce
        // clear, les collections deja chargees ici (les lignes deplacees du panier
        // vers la commande, notamment) masqueraient ce que les requetes rendent
        // vraiment.
        entites.flush();
        entites.clear();
        return utilisateurs.findByEmailIgnoreCase(email).orElseThrow();
    }

    private void deuxMembres() {
        membreComplet("marie@exemple.be", MDP_MARIE, "1-ABC-123", "Volkswagen",
                "MARIE", "81.240.10.7", Instant.parse("2026-06-10T08:00:00Z"));
        membreComplet("jean@exemple.be", MDP_JEAN, "2-XYZ-789", "Renault",
                "JEAN", "195.130.1.1", Instant.parse("2026-06-11T08:00:00Z"));
    }

    private String exporter(String motDePasse) throws Exception {
        return mvc.perform(post("/mes-donnees/export").with(csrf())
                        .locale(Locale.FRANCE)
                        .param("motDePasse", motDePasse))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @WithMockUser(username = "marie@exemple.be")
    @DisplayName("le document ne contient que les donnees du membre connecte")
    void etancheiteEntreMembres() throws Exception {
        deuxMembres();

        String document = exporter(MDP_MARIE);

        assertThat(document)
                .contains("1-ABC-123").contains("Volkswagen")
                .contains("CMD-MARIE").contains("RDV-MARIE").contains("INT-MARIE")
                .contains("81.240.10.7")
                .contains(numerosFactures.get("marie@exemple.be"));
        // Rien de Jean : ni vehicule, ni dossier, ni adresse IP de consentement,
        // ni surtout sa facture — un document comptable nominatif.
        assertThat(document)
                .doesNotContain("2-XYZ-789").doesNotContain("Renault")
                .doesNotContain("CMD-JEAN").doesNotContain("RDV-JEAN").doesNotContain("INT-JEAN")
                .doesNotContain("195.130.1.1").doesNotContain("jean@exemple.be")
                .doesNotContain(numerosFactures.get("jean@exemple.be"));
    }

    @Test
    @WithMockUser(username = "paul@exemple.be")
    @DisplayName("toutes les sections attendues sont presentes et remplies")
    void documentComplet() throws Exception {
        membreComplet("paul@exemple.be", MDP_MARIE, "3-PAU-001", "Peugeot",
                "PAUL", "81.240.10.9", Instant.parse("2026-06-12T08:00:00Z"));

        JsonNode racine = lecteur.readTree(exporter(MDP_MARIE));
        JsonNode donnees = racine.path("donnees_personnelles");

        assertThat(racine.path("genere_le").asText()).endsWith("Z");
        assertThat(donnees.path("profil").path("email").asText()).isEqualTo("paul@exemple.be");
        assertThat(donnees.path("profil").path("adresse").has("code_postal")).isTrue();
        assertThat(donnees.path("vehicules").size()).isEqualTo(1);
        assertThat(donnees.path("vehicules").get(0).path("supprime").asBoolean()).isFalse();
        assertThat(donnees.path("commandes").size()).isEqualTo(1);
        assertThat(donnees.path("factures").size()).isEqualTo(1);
        assertThat(donnees.path("rendez_vous").size()).isEqualTo(1);
        assertThat(donnees.path("interventions").size()).isEqualTo(1);
        assertThat(donnees.path("consentements").size()).isEqualTo(1);
        assertThat(donnees.path("connexion_et_securite").has("derniere_connexion")).isTrue();

        // Panier en cours : articles choisis, jamais commandes, mais detenus.
        JsonNode panier = donnees.path("panier_en_cours");
        assertThat(panier.path("nombre_articles").asInt()).isEqualTo(3);
        assertThat(panier.path("lignes").size()).isEqualTo(1);
        assertThat(panier.path("lignes").get(0).path("libelle").asText()).isEqualTo("Ampoule H7");
        assertThat(panier.path("lignes").get(0).path("taux_tva").decimalValue())
                .isEqualByComparingTo("6.00");
        // La date d'ajout vient de ligne_panier.created_at, que l'entite n'expose pas.
        assertThat(panier.path("lignes").get(0).path("date_ajout").asText()).endsWith("Z");
        assertThat(panier.path("total_tvac").decimalValue()).isEqualByComparingTo("31.83");

        // Les lignes suivent leurs parents : le JOIN FETCH et le regroupement des
        // lignes de commande fonctionnent contre une vraie base.
        assertThat(donnees.path("commandes").get(0).path("lignes").size()).isEqualTo(1);
        assertThat(donnees.path("commandes").get(0).path("lignes").get(0)
                .path("total_tvac").decimalValue()).isEqualByComparingTo("48.38");
        assertThat(donnees.path("rendez_vous").get(0).path("prestations").size()).isEqualTo(1);
        assertThat(donnees.path("interventions").get(0).path("lignes").size()).isEqualTo(1);

        // Facture : la vraie, celle du module facturation, avec le numero legal
        // attribue par le compteur — et non un decalque de la commande payee.
        JsonNode facture = donnees.path("factures").get(0);
        assertThat(facture.path("numero").asText())
                .isEqualTo(numerosFactures.get("paul@exemple.be"))
                .matches("\\d{4}-\\d{4}");
        assertThat(facture.path("numero_commande").asText()).isEqualTo("CMD-PAUL");
        assertThat(facture.path("montant_htva").decimalValue()).isEqualByComparingTo("39.98");
        assertThat(facture.path("montant_tva").decimalValue()).isEqualByComparingTo("8.40");
        assertThat(facture.path("montant_tvac").decimalValue()).isEqualByComparingTo("48.38");
        assertThat(facture.path("date_emission").asText()).endsWith("Z");
        // Metadonnees seulement : aucun binaire, aucun chemin de fichier.
        assertThat(facture.path("pdf_archive").asBoolean()).isFalse();

        // L'IP du consentement est une donnee du membre : elle sort.
        assertThat(donnees.path("consentements").get(0).path("adresse_ip").asText())
                .isEqualTo("81.240.10.9");

        JsonNode traitement = racine.path("informations_traitement");
        assertThat(traitement.path("finalites").size()).isPositive();
        assertThat(traitement.path("categories_donnees").size()).isPositive();
        assertThat(traitement.path("destinataires").size()).isPositive();
        assertThat(traitement.path("durees_conservation").size()).isPositive();
        assertThat(traitement.path("droits").size()).isPositive();
        assertThat(traitement.path("responsable_traitement").asText()).isNotBlank();

        assertThat(racine.path("exclusions").path("donnees_bancaires").asText())
                .containsIgnoringCase("carte bancaire");
    }

    @Test
    @WithMockUser(username = "lucie@exemple.be")
    @DisplayName("le document ne porte ni empreinte de mot de passe ni secret technique")
    void aucunSecretDansLeDocument() throws Exception {
        Utilisateur lucie = membreComplet("lucie@exemple.be", MDP_MARIE, "4-LUC-001",
                "Citroen", "LUCIE", "81.240.10.10", Instant.parse("2026-06-13T08:00:00Z"));
        lucie.enregistrerJetonVerification("jeton-it-a-ne-jamais-exporter",
                Instant.parse("2027-01-01T00:00:00Z"));
        utilisateurs.flush();

        String document = exporter(MDP_MARIE);

        assertThat(document)
                .doesNotContain(lucie.getMotDePasseHache())
                // Aucune empreinte BCrypt, quel que soit le champ qui la porterait.
                .doesNotContain("$2a$")
                .doesNotContain("mot_de_passe_hache")
                .doesNotContain("jeton-it-a-ne-jamais-exporter")
                .doesNotContain("jeton_verification");
        // « mot_de_passe » apparait bien dans le document, mais uniquement comme
        // cle de la note d'exclusion qui explique pourquoi il n'y figure pas.
        assertThat(lecteur.readTree(document).path("exclusions").path("mot_de_passe").asText())
                .isNotBlank();
    }

    @Test
    @WithMockUser(username = "chloe@exemple.be")
    @DisplayName("un vehicule supprime du parc reste exporte, marque, avec son historique")
    void vehiculeSupprimeResteExporte() throws Exception {
        membreComplet("chloe@exemple.be", MDP_MARIE, "8-CHL-001", "Fiat",
                "CHLOE", "81.240.10.14", Instant.parse("2026-06-17T08:00:00Z"));
        Vehicule fiat = vehicules.findByMembre("chloe@exemple.be").get(0);
        fiat.marquerSupprime("chloe@exemple.be");
        entites.flush();
        entites.clear();

        JsonNode donnees = lecteur.readTree(exporter(MDP_MARIE)).path("donnees_personnelles");

        // Le vehicule sort malgre le @SQLRestriction de l'entite : la ligne est
        // toujours en base, donc la donnee est toujours detenue.
        JsonNode vehicule = donnees.path("vehicules").get(0);
        assertThat(donnees.path("vehicules").size()).isEqualTo(1);
        assertThat(vehicule.path("plaque").asText()).isEqualTo("8-CHL-001");
        assertThat(vehicule.path("supprime").asBoolean()).isTrue();
        assertThat(vehicule.path("date_suppression").asText()).endsWith("Z");

        // Et son historique d'atelier ne disparait pas avec lui : c'est tout
        // l'interet d'une suppression logique.
        assertThat(donnees.path("rendez_vous").size()).isEqualTo(1);
        assertThat(donnees.path("rendez_vous").get(0).path("vehicule").asText())
                .isEqualTo("8-CHL-001");
        assertThat(donnees.path("interventions").size()).isEqualTo(1);
        assertThat(donnees.path("interventions").get(0).path("vehicule").asText())
                .isEqualTo("8-CHL-001");
    }

    @Test
    @WithMockUser(username = "hugo@exemple.be")
    @DisplayName("mauvais mot de passe : refus, aucun document")
    void mauvaisMotDePasse() throws Exception {
        membreComplet("hugo@exemple.be", MDP_MARIE, "5-HUG-001", "Opel",
                "HUGO", "81.240.10.11", Instant.parse("2026-06-14T08:00:00Z"));

        mvc.perform(post("/mes-donnees/export").with(csrf()).locale(Locale.FRANCE)
                        .param("motDePasse", "PasLeBonMotDePasse"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("http://localhost/mes-donnees?erreur=motdepasse"))
                .andExpect(content().string(""));

        // Le quota n'a pas ete consomme : le bon mot de passe aboutit dans la foulee.
        assertThat(exporter(MDP_MARIE)).contains("5-HUG-001");
    }

    @Test
    @WithMockUser(username = "sarah@exemple.be")
    @DisplayName("un second export dans les 24 heures est refuse et le message annonce le delai")
    void secondExportRefuse() throws Exception {
        membreComplet("sarah@exemple.be", MDP_MARIE, "6-SAR-001", "Ford",
                "SARAH", "81.240.10.12", Instant.parse("2026-06-15T08:00:00Z"));

        exporter(MDP_MARIE);

        mvc.perform(post("/mes-donnees/export").with(csrf()).locale(Locale.FRANCE)
                        .param("motDePasse", MDP_MARIE))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("http://localhost/mes-donnees?erreur=limite"));

        mvc.perform(get("/mes-donnees").locale(Locale.FRANCE).param("erreur", "limite"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("24 dernières heures")))
                .andExpect(content().string(containsString("Un nouvel export sera possible dans")));
    }

    @Test
    @WithMockUser(username = "nadia@exemple.be")
    @DisplayName("l'ecran s'affiche en francais, avec le rappel du droit et des exclusions")
    void ecranRendu() throws Exception {
        membreComplet("nadia@exemple.be", MDP_MARIE, "7-NAD-001", "Toyota",
                "NADIA", "81.240.10.13", Instant.parse("2026-06-16T08:00:00Z"));

        mvc.perform(get("/mes-donnees").locale(Locale.FRANCE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mes données personnelles")))
                .andExpect(content().string(containsString("article 15")))
                .andExpect(content().string(containsString("Ce que le fichier ne contient pas")))
                .andExpect(content().string(containsString("Exporter mes données")))
                // Champ de re-authentification present, avec sa protection CSRF.
                .andExpect(content().string(containsString("name=\"motDePasse\"")))
                .andExpect(content().string(containsString("_csrf")));
    }
}
