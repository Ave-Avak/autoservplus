package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.domain.UnitePrestation;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.reservation.service.DisponibiliteService;
import be.autoservplus.reservation.service.dto.CreneauDisponible;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Choix des pièces sur les lignes (V77) : rendez-vous et panier sans présélection, choix
 * obligatoire signalé sous le groupe et dans le résumé, présélection au back-office, prix et
 * choix figés en base, contraintes des trois états.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Choix des pièces sur les lignes (intégration)")
class ChoixPiecesIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MENTION_APPORTEES = "Pose seule, pièces apportées par le client";

    @Autowired private MockMvc mvc;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PieceRepository pieces;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private DisponibiliteService disponibilites;
    @Autowired private InterventionService interventions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock horloge;

    private String suffixe;
    private Categorie atelier;
    private String email;
    private Long idMembre;
    private Vehicule golf;

    @BeforeEach
    void setUp() {
        suffixe = "CP" + COMPTEUR.getAndIncrement();
        atelier = categories.save(new Categorie("IT-" + suffixe, "Atelier " + suffixe, TypeCategorie.SERVICE));
        email = "choix-" + suffixe.toLowerCase(Locale.ROOT) + "@exemple.be";
        var membre = utilisateurs.save(MembresDeTest.membre(email, "$2a$12$h", "Dupont", "Marie"));
        idMembre = membre.getId();
        golf = vehicules.save(new Vehicule(membre, "1-" + suffixe, "VW", "Golf", Motorisation.DIESEL));
    }

    @AfterEach
    void langueParDefaut() {
        LocaleContextHolder.resetLocaleContext();
    }

    /** Forfait à 69,00 € HTVA, pose seule à 39,00 € quand {@code poseSeule} le demande. */
    private Prestation vidange(boolean poseSeule) {
        Prestation vidange = new Prestation(atelier, suffixe + (poseSeule ? "-VPS" : "-VID"),
                "Vidange " + suffixe + (poseSeule ? " PS" : ""), new BigDecimal("69.00"), 30);
        if (poseSeule) {
            vidange.fixerPrix(new BigDecimal("69.00"), new BigDecimal("39.00"));
        }
        return prestations.saveAndFlush(vidange);
    }

    private JdbcTemplate base() {
        entityManager.flush();
        return jdbc;
    }

    private Map<String, Object> ligneDuPanier() {
        return base().queryForMap("""
                SELECT l.pieces_client, l.prix_unitaire_htva FROM ligne_panier l
                JOIN panier p ON p.id = l.panier_id JOIN utilisateur u ON u.id = p.membre_id WHERE u.email = ?
                """, email);
    }

    @Nested
    @DisplayName("Rendez-vous")
    class RendezVous {

        private CreneauDisponible creneau(Prestation prestation) {
            for (int jours = 2; jours < 30; jours++) {
                List<CreneauDisponible> libres = disponibilites.creneauxDuJour(
                        LocalDate.now(horloge).plusDays(jours), prestation.getDureeMinutes());
                if (!libres.isEmpty()) {
                    return libres.get(0);
                }
            }
            throw new AssertionError("aucun jour ouvert dans le mois");
        }

        @Test
        @DisplayName("le formulaire montre les deux prix, sans bouton coché d'avance")
        void sansPreselection() throws Exception {
            Prestation vidange = vidange(true);

            mvc.perform(get("/mes-rendez-vous/nouveau").with(user(email).roles("MEMBRE")).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"choixPieces[" + vidange.getReference() + "]\"")))
                    .andExpect(content().string(containsString("47,19 € TVA comprise, soit 39,00 € hors TVA")))
                    .andExpect(content().string(not(containsString("checked=\"checked\" aria-invalid"))))
                    .andExpect(content().string(not(containsString("value=\"FOURNIES_PAR_GARAGE\" checked"))));
        }

        @Test
        @DisplayName("sans choix : erreur sous le groupe et dans le résumé, aucun rendez-vous")
        void choixObligatoire() throws Exception {
            Prestation vidange = vidange(true);
            CreneauDisponible creneau = creneau(vidange);
            String champ = "choixPieces[" + vidange.getReference() + "]";

            mvc.perform(post("/mes-rendez-vous/nouveau").with(user(email).roles("MEMBRE")).with(csrf())
                            .locale(Locale.FRENCH)
                            .param("vehicule", golf.getReference().toString())
                            .param("prestations", vidange.getReference().toString())
                            .param("date", creneau.debut().atZone(java.time.ZoneId.of("Europe/Brussels")).toLocalDate().toString())
                            .param("debut", creneau.debut().toString()))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrorCode("formulaire", champ, "choix-pieces.erreur.manquant"))
                    .andExpect(content().string(containsString("Corrigez les points suivants")))
                    .andExpect(content().string(containsString("href=\"#choix-" + vidange.getReference() + "\"")))
                    .andExpect(content().string(containsString("id=\"choix-" + vidange.getReference() + "-erreur\"")));

            assertThat(base().queryForObject("SELECT count(*) FROM rdv WHERE membre_id = ?", Integer.class, idMembre))
                    .isZero();
        }

        @Test
        @DisplayName("pose seule choisie : prix pose seule et choix figés sur la ligne, mention au détail")
        void poseSeuleFigee() throws Exception {
            Prestation vidange = vidange(true);
            Prestation simple = vidange(false);
            CreneauDisponible creneau = creneau(vidange);

            mvc.perform(post("/mes-rendez-vous/nouveau").with(user(email).roles("MEMBRE")).with(csrf())
                            .locale(Locale.FRENCH)
                            .param("vehicule", golf.getReference().toString())
                            .param("prestations", vidange.getReference().toString(), simple.getReference().toString())
                            .param("choixPieces[" + vidange.getReference() + "]", "APPORTEES_PAR_CLIENT")
                            .param("date", creneau.debut().atZone(java.time.ZoneId.of("Europe/Brussels")).toLocalDate().toString())
                            .param("debut", creneau.debut().toString()))
                    .andExpect(status().is3xxRedirection());

            List<Map<String, Object>> lignes = base().queryForList("""
                    SELECT s.code, l.pieces_client, l.prix_unitaire_htva FROM rdv_service l
                    JOIN rdv r ON r.id = l.rdv_id JOIN service s ON s.id = l.service_id
                    WHERE r.membre_id = ? ORDER BY s.code
                    """, idMembre);
            assertThat(lignes).hasSize(2);
            Map<String, Object> poseSeule = lignes.stream().filter(l -> l.get("code").equals(vidange.getCode()))
                    .findFirst().orElseThrow();
            Map<String, Object> sansChoix = lignes.stream().filter(l -> l.get("code").equals(simple.getCode()))
                    .findFirst().orElseThrow();
            assertThat(poseSeule.get("pieces_client")).isEqualTo(true);
            assertThat((BigDecimal) poseSeule.get("prix_unitaire_htva")).isEqualByComparingTo("39.00");
            assertThat(sansChoix.get("pieces_client")).isNull();

            UUID rdv = base().queryForObject("SELECT reference FROM rdv WHERE membre_id = ?", UUID.class, idMembre);
            mvc.perform(get("/mes-rendez-vous/{ref}", rdv).with(user(email).roles("MEMBRE")).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(MENTION_APPORTEES)));
        }
    }

    @Nested
    @DisplayName("Panier depuis la fiche")
    class Panier {

        @Test
        @DisplayName("la fiche propose le choix sans le présélectionner")
        void fiche() throws Exception {
            Prestation vidange = vidange(true);

            mvc.perform(get("/services/{ref}", vidange.getReference()).with(user(email).roles("MEMBRE"))
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"choix\"")))
                    .andExpect(content().string(not(containsString("value=\"FOURNIES_PAR_GARAGE\" checked"))));
        }

        @Test
        @DisplayName("sans choix : retour à la fiche avec l'erreur, rien au panier")
        void choixObligatoire() throws Exception {
            Prestation vidange = vidange(true);

            mvc.perform(post("/panier/ajouter-service").with(user(email).roles("MEMBRE")).with(csrf())
                            .locale(Locale.FRENCH)
                            .param("reference", vidange.getReference().toString()).param("quantite", "1"))
                    .andExpect(redirectedUrl("/services/" + vidange.getReference()))
                    .andExpect(flash().attribute("erreurChoixPieces", containsString("Choisissez qui fournit les pièces")));

            assertThat(base().queryForObject("""
                    SELECT count(*) FROM ligne_panier l JOIN panier p ON p.id = l.panier_id WHERE p.membre_id = ?
                    """, Integer.class, idMembre)).isZero();
        }

        @Test
        @DisplayName("pose seule choisie : prix et choix figés, mention au panier")
        void poseSeuleFigee() throws Exception {
            Prestation vidange = vidange(true);

            mvc.perform(post("/panier/ajouter-service").with(user(email).roles("MEMBRE")).with(csrf())
                            .locale(Locale.FRENCH)
                            .param("reference", vidange.getReference().toString()).param("quantite", "1")
                            .param("choix", "APPORTEES_PAR_CLIENT"))
                    .andExpect(redirectedUrl("/panier"))
                    .andExpect(flash().attributeExists("message"));

            Map<String, Object> ligne = ligneDuPanier();
            assertThat(ligne.get("pieces_client")).isEqualTo(true);
            assertThat((BigDecimal) ligne.get("prix_unitaire_htva")).isEqualByComparingTo("39.00");

            mvc.perform(get("/panier").with(user(email).roles("MEMBRE")).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(MENTION_APPORTEES)));
        }

        @Test
        @DisplayName("un forfait sans prix pose seule n'a aucun choix, la ligne reste à nul")
        void sansChoixPropose() throws Exception {
            Prestation vidange = vidange(false);

            mvc.perform(post("/panier/ajouter-service").with(user(email).roles("MEMBRE")).with(csrf())
                            .param("reference", vidange.getReference().toString()).param("quantite", "1"))
                    .andExpect(redirectedUrl("/panier"));

            assertThat(ligneDuPanier().get("pieces_client")).isNull();
        }
    }

    @Nested
    @DisplayName("Back-office")
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    class BackOffice {

        private UUID interventionEnCours() {
            UUID reference = jdbc.queryForObject("""
                    INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva)
                    VALUES (?, ?, 'PLANIFIEE', 1000.00) RETURNING reference
                    """, UUID.class, "INT-" + suffixe, golf.getId());
            interventions.demarrer(reference);
            return reference;
        }

        private void ajouter(UUID intervention, Prestation prestation, String choix) throws Exception {
            var requete = post("/admin/interventions/{ref}/lignes", intervention).with(csrf())
                    .param("prestation", prestation.getReference().toString()).param("quantite", "1");
            if (choix != null) {
                requete.param("choix", choix);
            }
            mvc.perform(requete)
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("message"));
        }

        private Map<String, Object> ligne(UUID intervention, Prestation prestation) {
            return base().queryForMap("""
                    SELECT l.pieces_client, l.prix_unitaire_htva FROM ligne_intervention l
                    JOIN intervention i ON i.id = l.intervention_id
                    WHERE i.reference = ? AND l.service_id = ?
                    """, intervention, prestation.getId());
        }

        @Test
        @DisplayName("le formulaire présélectionne les pièces fournies")
        void preselection() throws Exception {
            UUID dossier = interventionEnCours();

            mvc.perform(get("/admin/interventions/{ref}", dossier).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("value=\"FOURNIES_PAR_GARAGE\" checked=\"checked\"")));
        }

        @Test
        @DisplayName("sans choix envoyé, les pièces fournies au prix pièces comprises")
        void parDefaut() throws Exception {
            UUID dossier = interventionEnCours();
            Prestation vidange = vidange(true);

            ajouter(dossier, vidange, null);

            assertThat(ligne(dossier, vidange).get("pieces_client")).isEqualTo(false);
            assertThat((BigDecimal) ligne(dossier, vidange).get("prix_unitaire_htva")).isEqualByComparingTo("69.00");
        }

        @Test
        @DisplayName("pose seule choisie : prix pose seule et mention au détail")
        void poseSeule() throws Exception {
            UUID dossier = interventionEnCours();
            Prestation vidange = vidange(true);

            ajouter(dossier, vidange, "APPORTEES_PAR_CLIENT");

            assertThat(ligne(dossier, vidange).get("pieces_client")).isEqualTo(true);
            assertThat((BigDecimal) ligne(dossier, vidange).get("prix_unitaire_htva")).isEqualByComparingTo("39.00");
            mvc.perform(get("/admin/interventions/{ref}", dossier).locale(Locale.FRENCH))
                    .andExpect(content().string(containsString(MENTION_APPORTEES)));
        }

        @Test
        @DisplayName("le choix est ignoré pour un forfait sans prix pose seule et pour une prestation à l'heure")
        void ignore() throws Exception {
            UUID dossier = interventionEnCours();
            Prestation vidange = vidange(false);
            Prestation mainDOeuvre = prestations.saveAndFlush(new Prestation(atelier, suffixe + "-MO",
                    "Main-d'œuvre " + suffixe, new BigDecimal("65.00"), 60, UnitePrestation.HEURE));

            ajouter(dossier, vidange, "APPORTEES_PAR_CLIENT");
            ajouter(dossier, mainDOeuvre, "FOURNIES_PAR_GARAGE");

            assertThat(ligne(dossier, vidange).get("pieces_client")).isNull();
            assertThat(ligne(dossier, mainDOeuvre).get("pieces_client")).isNull();
        }
    }

    @Nested
    @DisplayName("Contraintes en base")
    class Contraintes {

        @Test
        @DisplayName("une ligne de pièce du panier ne porte aucun choix (ck_ligne_pieces_client)")
        void pieceDuPanier() {
            Categorie filtres = categories.save(new Categorie("IT-P-" + suffixe, "Filtres", TypeCategorie.PIECE));
            Piece filtre = pieces.saveAndFlush(new Piece(filtres, suffixe + "-F", "Filtre", new BigDecimal("12.40")));
            Long panier = jdbc.queryForObject("INSERT INTO panier (reference, membre_id) VALUES (gen_random_uuid(), ?) "
                    + "RETURNING id", Long.class, idMembre);

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO ligne_panier (panier_id, piece_id, libelle_fige, quantite, prix_unitaire_htva, taux_tva,
                                              pieces_client)
                    VALUES (?, ?, 'Filtre', 1, 12.40, 21.00, false)
                    """, panier, filtre.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_ligne_pieces_client");
        }

        @Test
        @DisplayName("une ligne d'intervention hors forfait ne porte aucun choix (ck_ligne_interv_pieces_client)")
        void horsForfait() {
            Prestation mainDOeuvre = prestations.saveAndFlush(new Prestation(atelier, suffixe + "-MO2",
                    "Main-d'œuvre", new BigDecimal("65.00"), 60, UnitePrestation.HEURE));
            Long intervention = jdbc.queryForObject("""
                    INSERT INTO intervention (numero, vehicule_id, statut, montant_devis_htva)
                    VALUES (?, ?, 'PLANIFIEE', 0) RETURNING id
                    """, Long.class, "INT-C-" + suffixe, golf.getId());

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO ligne_intervention (intervention_id, service_id, libelle_fige, quantite, unite,
                                                    prix_unitaire_htva, taux_tva, ajoutee_en_cours, pieces_client)
                    VALUES (?, ?, 'Main-d''œuvre', 1, 'HEURE', 65.00, 21.00, false, true)
                    """, intervention, mainDOeuvre.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_ligne_interv_pieces_client");
        }
    }
}
