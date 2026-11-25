package be.autoservplus.intervention.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.PrestationChoisie;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pièces incluses d'une ligne de forfait (V77) : copie de la composition et son journal,
 * ajustement au back-office jusqu'à la clôture, décompte du stock, « à commander » sur trois
 * sources, suivi du membre en lecture seule, absence de la facture, garde RM-29, journal d'audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("Pièces incluses d'une intervention (intégration)")
class PiecesInclusesIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String A_COMMANDER = "À commander";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private RdvRepository rdvs;
    @Autowired private InterventionService service;
    @Autowired private FactureService factures;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private int n;
    private String email;
    private Utilisateur marie;
    private Vehicule golf;
    private Piece filtre;
    private Piece huile;
    private Prestation vidange;

    @BeforeEach
    void setUp() {
        n = COMPTEUR.getAndIncrement();
        email = "incluses-marie" + n + "@exemple.be";
        marie = utilisateurs.save(MembresDeTest.membre(email, "$2a$12$h", "Dupont", "Marie"));
        golf = vehicules.save(new Vehicule(marie, "INC" + n, "VW", "Golf", Motorisation.DIESEL));
        Categorie filtres = categories.save(new Categorie("IT-INC-P" + n, "Filtres", TypeCategorie.PIECE));
        Categorie atelier = categories.save(new Categorie("IT-INC-S" + n, "Atelier", TypeCategorie.SERVICE));
        filtre = piece(filtres, "Filtre à huile IT" + n, 10);
        huile = piece(filtres, "Huile IT" + n, 20);
        Prestation forfait = new Prestation(atelier, "INC-VID" + n, "Vidange IT" + n, new BigDecimal("89.00"), 30);
        forfait.fixerPrix(new BigDecimal("89.00"), new BigDecimal("49.00"));
        forfait.fixerComposant(filtre, 1);
        forfait.fixerComposant(huile, 5);
        vidange = prestations.saveAndFlush(forfait);
    }

    private Piece piece(Categorie categorie, String libelle, int stock) {
        Piece piece = new Piece(categorie, "IT-INC-" + COMPTEUR.getAndIncrement(), libelle, new BigDecimal("10.00"));
        piece.setQuantiteStock(stock);
        return pieces.saveAndFlush(piece);
    }

    /** Entrée directe démarrée, au devis de référence large : aucun ajout n'attend le membre. */
    private UUID interventionEnCours() {
        UUID reference = jdbc.queryForObject("INSERT INTO intervention (numero, vehicule_id, statut, "
                        + "montant_devis_htva) VALUES (?, ?, 'PLANIFIEE', 5000.00) RETURNING reference",
                UUID.class, "INT-INC-" + n + "-" + COMPTEUR.getAndIncrement(), golf.getId());
        service.demarrer(reference);
        return reference;
    }

    /** @param choix {@code null} pour la présélection du formulaire, les pièces fournies */
    private void ajouterForfait(UUID intervention, String choix) throws Exception {
        mvc.perform(post("/admin/interventions/{ref}/lignes", intervention).with(csrf())
                        .param("prestation", vidange.getReference().toString()).param("quantite", "1")
                        .param("choix", choix != null ? choix : "FOURNIES_PAR_GARAGE"))
                .andExpect(flash().attributeExists("message"));
        finDeRequete();
    }

    /**
     * Fin d'une requête du back-office. La transaction du test sert toutes les requêtes d'un
     * cas : sans ce vidage, la session garderait les traces de la copie, qui pointent vers la
     * ligne qu'un retrait supprime, et l'écriture suivante échouerait sur une référence que
     * Hibernate croit encore vivante. En service, chaque requête ouvre une session neuve.
     */
    private void finDeRequete() {
        entityManager.flush();
        entityManager.clear();
    }

    private Long ligneDuForfait(UUID intervention) {
        return base().queryForObject("""
                SELECT l.id FROM ligne_intervention l JOIN intervention i ON i.id = l.intervention_id
                WHERE i.reference = ? AND l.service_id = ?
                """, Long.class, intervention, vidange.getId());
    }

    /**
     * Clôture par la route du back-office. Après une requête, le filtre de sécurité a vidé le
     * contexte du fil : un appel direct au service n'aurait plus d'administrateur.
     */
    private void cloturer(UUID intervention) throws Exception {
        mvc.perform(post("/admin/interventions/{ref}/terminer", intervention).with(csrf()).locale(Locale.FRENCH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"));
        finDeRequete();
    }

    private List<String> incluses(UUID intervention) {
        return base().queryForList("""
                SELECT p.libelle || ':' || pi.quantite FROM piece_incluse pi
                JOIN piece p ON p.id = pi.piece_id
                JOIN ligne_intervention l ON l.id = pi.ligne_intervention_id
                JOIN intervention i ON i.id = l.intervention_id
                WHERE i.reference = ? ORDER BY pi.id
                """, String.class, intervention);
    }

    private List<String> journal(UUID intervention) {
        return base().queryForList("""
                SELECT h.evenement || ':' || p.libelle || ':' || coalesce(CAST(h.quantite_avant AS text), '-')
                       || ':' || coalesce(CAST(h.quantite_apres AS text), '-')
                FROM historique_piece_incluse h JOIN piece p ON p.id = h.piece_id
                JOIN intervention i ON i.id = h.intervention_id
                WHERE i.reference = ? ORDER BY h.id
                """, String.class, intervention);
    }

    private JdbcTemplate base() {
        entityManager.flush();
        return jdbc;
    }

    private int stock(Piece piece) {
        return base().queryForObject("SELECT quantite_stock FROM piece WHERE id = ?", Integer.class, piece.getId());
    }

    @Nested
    @DisplayName("Copie de la composition")
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    class Copie {

        @Test
        @DisplayName("à l'ajout d'un forfait aux pièces du garage, copiée et journalisée")
        void alAjout() throws Exception {
            UUID dossier = interventionEnCours();

            ajouterForfait(dossier, null);

            assertThat(incluses(dossier)).containsExactly(filtre.getLibelle() + ":1", huile.getLibelle() + ":5");
            assertThat(journal(dossier)).containsExactly(
                    "COPIE:" + filtre.getLibelle() + ":-:1", "COPIE:" + huile.getLibelle() + ":-:5");
        }

        @Test
        @DisplayName("en pose seule, rien n'est copié")
        void poseSeule() throws Exception {
            UUID dossier = interventionEnCours();

            ajouterForfait(dossier, "APPORTEES_PAR_CLIENT");

            assertThat(incluses(dossier)).isEmpty();
            assertThat(journal(dossier)).isEmpty();
        }

        @Test
        @DisplayName("à la création depuis le rendez-vous, selon le choix figé sur sa ligne")
        void depuisLeRendezVous() {
            Instant debut = Instant.parse("2031-01-06T08:00:00Z").plus(Duration.ofDays(n));
            Rdv rdv = rdvs.saveAndFlush(new Rdv("RDV-INC-" + n, marie, golf, postes.findAll().get(0), debut,
                    Duration.ofMinutes(30), List.of(new PrestationChoisie(vidange, ChoixPieces.FOURNIES_PAR_GARAGE)),
                    null));

            Intervention creee = service.creerDepuisRdv(rdv);

            assertThat(incluses(creee.getReference()))
                    .containsExactly(filtre.getLibelle() + ":1", huile.getLibelle() + ":5");
            assertThat(journal(creee.getReference())).hasSize(2).allMatch(trace -> trace.startsWith("COPIE:"));
        }
    }

    @Nested
    @DisplayName("Ajustement au back-office")
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    class Ajustement {

        @Test
        @DisplayName("modification, ajout et retrait, chacun journalisé, retour sur la ligne")
        void ajuster() throws Exception {
            UUID dossier = interventionEnCours();
            ajouterForfait(dossier, null);
            Long ligne = ligneDuForfait(dossier);
            Piece joint = piece(filtre.getCategorie(), "Joint IT" + n, 4);

            mvc.perform(post("/admin/interventions/{ref}/lignes/{id}/pieces-incluses", dossier, ligne).with(csrf())
                            .param("piece", huile.getReference().toString()).param("quantite", "4"))
                    .andExpect(redirectedUrl("/admin/interventions/" + dossier + "#incluses-" + ligne))
                    .andExpect(flash().attributeExists("message"));
            finDeRequete();
            mvc.perform(post("/admin/interventions/{ref}/lignes/{id}/pieces-incluses", dossier, ligne).with(csrf())
                            .param("piece", joint.getReference().toString()).param("quantite", "1"))
                    .andExpect(flash().attributeExists("message"));
            finDeRequete();
            mvc.perform(post("/admin/interventions/{ref}/lignes/{id}/pieces-incluses/{piece}/retirer",
                            dossier, ligne, filtre.getReference()).with(csrf()))
                    .andExpect(flash().attributeExists("message"));
            finDeRequete();

            assertThat(incluses(dossier)).containsExactly(huile.getLibelle() + ":4", joint.getLibelle() + ":1");
            assertThat(journal(dossier)).endsWith(
                    "MODIFICATION:" + huile.getLibelle() + ":5:4",
                    "AJOUT:" + joint.getLibelle() + ":-:1",
                    "RETRAIT:" + filtre.getLibelle() + ":1:-");
        }

        @Test
        @DisplayName("le retrait de la ligne retire ses pièces, le journal les garde sans la ligne")
        void retraitDeLaLigne() throws Exception {
            UUID dossier = interventionEnCours();
            ajouterForfait(dossier, null);
            Long ligne = ligneDuForfait(dossier);

            mvc.perform(post("/admin/interventions/{ref}/lignes/{id}/supprimer", dossier, ligne).with(csrf()))
                    .andExpect(flash().attributeExists("message"));
            finDeRequete();

            assertThat(incluses(dossier)).isEmpty();
            assertThat(journal(dossier)).endsWith("RETRAIT:" + filtre.getLibelle() + ":1:-",
                    "RETRAIT:" + huile.getLibelle() + ":5:-");
            assertThat(base().queryForObject("""
                    SELECT count(*) FROM historique_piece_incluse h JOIN intervention i ON i.id = h.intervention_id
                    WHERE i.reference = ? AND h.ligne_intervention_id IS NOT NULL
                    """, Integer.class, dossier)).isZero();
        }

        @Test
        @DisplayName("après la clôture, refusé avec un message, rien ne change")
        void apresLaCloture() throws Exception {
            UUID dossier = interventionEnCours();
            ajouterForfait(dossier, null);
            Long ligne = ligneDuForfait(dossier);
            cloturer(dossier);
            finDeRequete();

            mvc.perform(post("/admin/interventions/{ref}/lignes/{id}/pieces-incluses", dossier, ligne).with(csrf())
                            .locale(Locale.FRENCH)
                            .param("piece", huile.getReference().toString()).param("quantite", "2"))
                    .andExpect(flash().attribute("erreur", containsString("après la clôture")));

            assertThat(incluses(dossier)).contains(huile.getLibelle() + ":5");
        }

        @Test
        @DisplayName("le détail montre les pièces incluses et leurs formulaires")
        void detail() throws Exception {
            UUID dossier = interventionEnCours();
            ajouterForfait(dossier, null);

            mvc.perform(get("/admin/interventions/{ref}", dossier).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Pièces incluses, non facturées")))
                    .andExpect(content().string(containsString(huile.getLibelle() + " : 5")))
                    .andExpect(content().string(containsString("Ajouter une pièce incluse")));
        }
    }

    @Nested
    @DisplayName("Stock")
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    class Stock {

        @Test
        @DisplayName("la clôture décompte les pièces incluses avec les lignes de pièce")
        void decompteALaCloture() throws Exception {
            UUID dossier = interventionEnCours();
            ajouterForfait(dossier, null);

            cloturer(dossier);

            assertThat(stock(filtre)).isEqualTo(9);
            assertThat(stock(huile)).isEqualTo(15);
        }

        @Test
        @DisplayName("aucune pièce incluse sur la facture d'atelier")
        void horsFacture() throws Exception {
            UUID dossier = interventionEnCours();
            ajouterForfait(dossier, null);

            cloturer(dossier);

            Intervention close = entityManager.createQuery(
                            "SELECT i FROM Intervention i WHERE i.reference = :r", Intervention.class)
                    .setParameter("r", dossier).getSingleResult();
            var facture = factures.factureDe(close).orElseThrow();
            assertThat(factures.lignesDuDocument(facture))
                    .extracting(be.autoservplus.facturation.service.dto.DocumentFacture.LigneFacture::libelle)
                    .containsExactly(vidange.getLibelle());
        }

        @Test
        @DisplayName("« à commander » cumule les pièces incluses et la composition des rendez-vous sans intervention")
        void aCommanderSurTroisSources() throws Exception {
            // Stock de 10 filtres : 6 rendez-vous de vidange à venir en réclament 6, l'intervention
            // un de plus, plus 4 en ligne de pièce : 11.
            UUID dossier = interventionEnCours();
            ajouterForfait(dossier, null);
            mvc.perform(post("/admin/interventions/{ref}/lignes/piece", dossier).with(csrf())
                    .param("piece", filtre.getReference().toString()).param("quantite", "4"));
            finDeRequete();
            for (int i = 0; i < 6; i++) {
                Instant debut = Instant.parse("2032-01-05T08:00:00Z").plus(Duration.ofDays(30L * n + i));
                rdvs.saveAndFlush(new Rdv("RDV-INC-" + n + "-" + i, marie, golf, postes.findAll().get(0), debut,
                        Duration.ofMinutes(30),
                        List.of(new PrestationChoisie(vidange, ChoixPieces.FOURNIES_PAR_GARAGE)), null));
            }

            String page = mvc.perform(get("/admin/interventions/{ref}", dossier).locale(Locale.FRENCH))
                    .andReturn().getResponse().getContentAsString();

            assertThat(page).contains(A_COMMANDER);
        }

        @Test
        @DisplayName("sous le stock, aucune mention, et la pose seule d'un rendez-vous ne compte pas")
        void sousLeStock() throws Exception {
            UUID dossier = interventionEnCours();
            ajouterForfait(dossier, null);
            for (int i = 0; i < 12; i++) {
                Instant debut = Instant.parse("2033-01-03T08:00:00Z").plus(Duration.ofDays(30L * n + i));
                rdvs.saveAndFlush(new Rdv("RDV-INP-" + n + "-" + i, marie, golf, postes.findAll().get(0), debut,
                        Duration.ofMinutes(30),
                        List.of(new PrestationChoisie(vidange, ChoixPieces.APPORTEES_PAR_CLIENT)), null));
            }

            mvc.perform(get("/admin/interventions/{ref}", dossier).locale(Locale.FRENCH))
                    .andExpect(content().string(not(containsString(A_COMMANDER))));
        }
    }

    @Test
    @DisplayName("le membre lit les pièces comprises sur son suivi, sans formulaire")
    void suiviDuMembre() throws Exception {
        UUID dossier = interventionEnCours();
        ajouterForfait(dossier, null);

        mvc.perform(get("/mes-interventions/{ref}", dossier).with(user(email).roles("MEMBRE")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Comprise : " + huile.getLibelle() + " (5)")))
                .andExpect(content().string(not(containsString("pieces-incluses"))));
    }

    @Test
    @DisplayName("une pièce incluse compte comme référencée (RM-29)")
    void referencee() throws Exception {
        UUID dossier = interventionEnCours();
        Piece seule = piece(filtre.getCategorie(), "Bouchon IT" + n, 2);
        assertThat(pieces.nombreReferencesHistoriques(seule.getId())).isZero();
        ajouterForfait(dossier, null);
        Long ligne = ligneDuForfait(dossier);

        mvc.perform(post("/admin/interventions/{ref}/lignes/{id}/pieces-incluses", dossier, ligne).with(csrf())
                .param("piece", seule.getReference().toString()).param("quantite", "1"));

        assertThat(pieces.nombreReferencesHistoriques(seule.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("le journal d'audit montre la copie de la composition")
    void journalDAudit() throws Exception {
        UUID dossier = interventionEnCours();
        ajouterForfait(dossier, null);
        entityManager.flush();

        mvc.perform(get("/admin/journal").param("type", "PIECE_INCLUSE").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Copie de la composition, " + huile.getLibelle())));
    }
}
