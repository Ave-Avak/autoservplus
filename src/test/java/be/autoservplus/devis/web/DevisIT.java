package be.autoservplus.devis.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Devis F15 de bout en bout : émission depuis le panier, liste, document, appartenance.
 * Chaque test a son membre, son panier et ses véhicules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Devis (intégration)")
class DevisIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    private Categorie categorie;

    @BeforeEach
    void categorie() {
        categorie = transactions.execute(statut -> categories.findByCode("IT-DEVIS-CAT")
                .orElseGet(() -> categories.save(new Categorie("IT-DEVIS-CAT", "Pièces", TypeCategorie.PIECE))));
    }

    private Utilisateur membre() {
        int n = COMPTEUR.getAndIncrement();
        return transactions.execute(statut -> utilisateurs.save(MembresDeTest.membre(
                "devis-" + n + "@exemple.be", "$2a$12$h", "Dupont", "Marie")));
    }

    private Vehicule vehicule(Utilisateur proprietaire) {
        int n = COMPTEUR.getAndIncrement();
        return transactions.execute(statut -> vehicules.save(new Vehicule(
                utilisateurs.findByEmail(proprietaire.getEmail()).orElseThrow(),
                "DEVIS" + n, "Volkswagen", "Golf", Motorisation.DIESEL)));
    }

    private Piece piece(String libelle, String prixHtva) {
        int n = COMPTEUR.getAndIncrement();
        return transactions.execute(statut -> {
            Piece piece = new Piece(categorie, "IT-DEVIS-" + n, libelle, new BigDecimal(prixHtva));
            piece.setTauxTva(new BigDecimal("21.00"));
            piece.setQuantiteStock(20);
            return pieces.save(piece);
        });
    }

    /** Ajout au panier par sa route, sous l'identité du membre. */
    private void ajouter(Utilisateur membre, UUID piece, int quantite) throws Exception {
        mvc.perform(post("/panier/ajouter").with(csrf()).with(connecte(membre))
                        .param("reference", piece.toString()).param("quantite", String.valueOf(quantite)))
                .andExpect(redirectedUrl("/panier"))
                .andExpect(flash().attributeExists("message"));
    }

    private static RequestPostProcessor connecte(Utilisateur membre) {
        return user(membre.getEmail()).roles("MEMBRE");
    }

    private String emettre(Utilisateur membre, Vehicule vehicule) throws Exception {
        return (String) mvc.perform(post("/mes-devis").with(csrf()).with(connecte(membre))
                        .param("vehicule", vehicule.getReference().toString()))
                .andExpect(redirectedUrl("/mes-devis"))
                .andReturn().getFlashMap().get("message");
    }

    private int nombreDeDevis(Utilisateur membre) {
        return jdbc.queryForObject("SELECT count(*) FROM devis d JOIN utilisateur u ON u.id = d.membre_id "
                + "WHERE u.email = ?", Integer.class, membre.getEmail());
    }

    private Map<String, Object> dernierDevis(Utilisateur membre) {
        return jdbc.queryForMap("SELECT d.* FROM devis d JOIN utilisateur u ON u.id = d.membre_id "
                + "WHERE u.email = ? ORDER BY d.id DESC LIMIT 1", membre.getEmail());
    }

    @Test
    @DisplayName("le panier présélectionne le seul véhicule du membre")
    void vehiculePreselectionne() throws Exception {
        Utilisateur marie = membre();
        Vehicule golf = vehicule(marie);
        ajouter(marie, piece("Filtre à huile", "9.99").getReference(), 1);

        mvc.perform(get("/panier").with(connecte(marie)).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + golf.getReference() + "\"")))
                .andExpect(content().string(containsString(
                        "selected=\"selected\">Volkswagen Golf (" + golf.getPlaque() + ")")))
                .andExpect(content().string(not(containsString("<option value=\"\">"))));
    }

    @Test
    @DisplayName("avec deux véhicules, le panier fait choisir, sans présélection")
    void choixEntreDeuxVehicules() throws Exception {
        Utilisateur marie = membre();
        vehicule(marie);
        vehicule(marie);
        ajouter(marie, piece("Filtre à huile", "9.99").getReference(), 1);

        mvc.perform(get("/panier").with(connecte(marie)).locale(Locale.FRENCH))
                .andExpect(content().string(containsString("<option value=\"\">Choisissez</option>")))
                .andExpect(content().string(not(containsString("selected=\"selected\""))));
    }

    @Test
    @DisplayName("sans véhicule, le panier invite à en ajouter un, sans formulaire de devis")
    void sansVehicule() throws Exception {
        Utilisateur marie = membre();
        ajouter(marie, piece("Filtre à huile", "9.99").getReference(), 1);

        mvc.perform(get("/panier").with(connecte(marie)).locale(Locale.FRENCH))
                .andExpect(content().string(containsString("href=\"/mes-vehicules/nouveau\"")))
                .andExpect(content().string(not(containsString("action=\"/mes-devis\""))));
    }

    @Test
    @DisplayName("le devis émis reprend le panier, qui reste intact")
    void emission() throws Exception {
        Utilisateur marie = membre();
        Vehicule golf = vehicule(marie);
        ajouter(marie, piece("Filtre à huile", "9.99").getReference(), 2);
        ajouter(marie, piece("Balais d'essuie-glace", "15.00").getReference(), 1);

        String message = emettre(marie, golf);

        Map<String, Object> devis = dernierDevis(marie);
        assertThat(message).contains((String) devis.get("numero"));
        assertThat((String) devis.get("numero")).matches("DEV-\\d{4}-\\d{4}");
        assertThat((BigDecimal) devis.get("montant_tvac")).isEqualByComparingTo("42.33");
        assertThat(devis.get("vehicule_plaque")).isEqualTo(golf.getPlaque());
        assertThat(devis.get("langue_document")).isEqualTo("fr");
        assertThat(jdbc.queryForList("SELECT libelle FROM ligne_devis WHERE devis_id = ? ORDER BY rang",
                String.class, devis.get("id")))
                .containsExactly("Filtre à huile", "Balais d'essuie-glace");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ligne_panier l JOIN panier p ON p.id = l.panier_id "
                + "JOIN utilisateur u ON u.id = p.membre_id WHERE u.email = ?", Integer.class, marie.getEmail()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("deux devis à la suite portent deux numéros consécutifs")
    void numerotation() throws Exception {
        Utilisateur marie = membre();
        Vehicule golf = vehicule(marie);
        ajouter(marie, piece("Filtre à huile", "9.99").getReference(), 1);

        emettre(marie, golf);
        String premier = (String) dernierDevis(marie).get("numero");
        emettre(marie, golf);
        String second = (String) dernierDevis(marie).get("numero");

        assertThat(premier.substring(0, 9)).isEqualTo(second.substring(0, 9));
        assertThat(Integer.parseInt(second.substring(9))).isEqualTo(Integer.parseInt(premier.substring(9)) + 1);
    }

    @Test
    @DisplayName("un membre néerlandophone reçoit un devis en néerlandais")
    void langueDuDocument() throws Exception {
        Utilisateur jan = membre();
        transactions.executeWithoutResult(statut -> {
            Utilisateur relu = utilisateurs.findByEmail(jan.getEmail()).orElseThrow();
            relu.setLangue(Langue.nl);
            utilisateurs.save(relu);
        });
        Vehicule golf = vehicule(jan);
        ajouter(jan, piece("Filtre à huile", "9.99").getReference(), 1);

        emettre(jan, golf);

        assertThat(dernierDevis(jan).get("langue_document")).isEqualTo("nl");
    }

    @Test
    @DisplayName("la liste montre le devis et son document se télécharge, nominatif et sans cache")
    void listeEtDocument() throws Exception {
        Utilisateur marie = membre();
        Vehicule golf = vehicule(marie);
        ajouter(marie, piece("Filtre à huile", "9.99").getReference(), 1);
        emettre(marie, golf);
        Map<String, Object> devis = dernierDevis(marie);
        String numero = (String) devis.get("numero");

        mvc.perform(get("/mes-devis").with(connecte(marie)).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(numero)))
                .andExpect(content().string(containsString("/mes-devis/" + devis.get("reference") + "/pdf")));

        byte[] pdf = mvc.perform(get("/mes-devis/{ref}/pdf", devis.get("reference")).with(connecte(marie)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("devis-" + numero + ".pdf")))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        PdfReader lecteur = new PdfReader(pdf);
        try {
            assertThat(new PdfTextExtractor(lecteur).getTextFromPage(1)).contains(numero, golf.getPlaque());
        } finally {
            lecteur.close();
        }
    }

    @Test
    @DisplayName("le devis d'un autre membre répond 404")
    void devisDAutrui() throws Exception {
        Utilisateur marie = membre();
        Utilisateur paul = membre();
        Vehicule golf = vehicule(marie);
        ajouter(marie, piece("Filtre à huile", "9.99").getReference(), 1);
        emettre(marie, golf);
        Object reference = dernierDevis(marie).get("reference");

        mvc.perform(get("/mes-devis/{ref}/pdf", reference).with(connecte(paul)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/mes-devis").with(connecte(paul)).locale(Locale.FRENCH))
                .andExpect(content().string(not(containsString(reference.toString()))));
    }

    @Test
    @DisplayName("le véhicule d'un autre membre répond 404, aucun devis n'est émis")
    void vehiculeDAutrui() throws Exception {
        Utilisateur marie = membre();
        Utilisateur paul = membre();
        Vehicule golfDeMarie = vehicule(marie);
        ajouter(paul, piece("Filtre à huile", "9.99").getReference(), 1);

        mvc.perform(post("/mes-devis").with(csrf()).with(connecte(paul))
                        .param("vehicule", golfDeMarie.getReference().toString()))
                .andExpect(status().isNotFound());

        assertThat(nombreDeDevis(paul)).isZero();
    }

    @Test
    @DisplayName("un panier vide est refusé avec un message, de retour au panier")
    void panierVide() throws Exception {
        Utilisateur marie = membre();
        Vehicule golf = vehicule(marie);

        mvc.perform(post("/mes-devis").with(csrf()).with(connecte(marie)).locale(Locale.FRENCH)
                        .param("vehicule", golf.getReference().toString()))
                .andExpect(redirectedUrl("/panier"))
                .andExpect(flash().attribute("erreur",
                        "Votre panier est vide. Un devis reprend les articles du panier."));

        assertThat(nombreDeDevis(marie)).isZero();
    }

    @Test
    @DisplayName("un article retiré de la vente est refusé, comme à la commande")
    void articleRetire() throws Exception {
        Utilisateur marie = membre();
        Vehicule golf = vehicule(marie);
        Piece filtre = piece("Filtre à huile", "9.99");
        ajouter(marie, filtre.getReference(), 1);
        transactions.executeWithoutResult(statut -> {
            Piece relue = pieces.findByReference(filtre.getReference()).orElseThrow();
            relue.desactiver();
            pieces.save(relue);
        });

        mvc.perform(post("/mes-devis").with(csrf()).with(connecte(marie)).locale(Locale.FRENCH)
                        .param("vehicule", golf.getReference().toString()))
                .andExpect(redirectedUrl("/panier"))
                .andExpect(flash().attributeExists("erreur"));

        assertThat(nombreDeDevis(marie)).isZero();
    }

    @Test
    @DisplayName("une émission sans jeton CSRF est refusée")
    void sansJeton() throws Exception {
        Utilisateur marie = membre();
        Vehicule golf = vehicule(marie);
        ajouter(marie, piece("Filtre à huile", "9.99").getReference(), 1);

        mvc.perform(post("/mes-devis").with(connecte(marie))
                        .param("vehicule", golf.getReference().toString()))
                .andExpect(status().isForbidden());

        assertThat(nombreDeDevis(marie)).isZero();
    }

    @Test
    @DisplayName("une référence inconnue répond 404")
    void referenceInconnue() throws Exception {
        mvc.perform(get("/mes-devis/{ref}/pdf", UUID.randomUUID()).with(connecte(membre())))
                .andExpect(status().isNotFound());
    }
}
