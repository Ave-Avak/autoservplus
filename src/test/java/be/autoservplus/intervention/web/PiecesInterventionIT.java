package be.autoservplus.intervention.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pièces ajoutées à une intervention depuis le back-office (RM-14, cas 4 et 11) et mention
 * « à commander » calculée à l'affichage (Q-H-30).
 *
 * <p>Les interventions sont des entrées directes, ni rendez-vous ni commande (V32) : seul
 * compte ici leur devis de référence, qui décide si un ajout attend l'accord du membre.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("Pièces d'une intervention au back-office (intégration)")
class PiecesInterventionIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String A_COMMANDER = "À commander";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private InterventionService service;
    @Autowired private JdbcTemplate jdbc;

    private String marie;
    private Long idMarie;
    private Categorie freinage;

    @BeforeEach
    void setUp() {
        int n = COMPTEUR.getAndIncrement();
        marie = "pieces-marie" + n + "@exemple.be";
        idMarie = utilisateurs.save(new Utilisateur(marie, "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE)).getId();
        freinage = categories.save(new Categorie("IT-PCE-" + n, "Freinage", TypeCategorie.PIECE));
    }

    /** Entrée directe démarrée, au devis de référence donné. */
    private UUID interventionEnCours(String devisHtva) {
        int n = COMPTEUR.getAndIncrement();
        Long vehicule = jdbc.queryForObject("INSERT INTO vehicule (reference, membre_id, plaque, marque, "
                        + "modele, motorisation) VALUES (gen_random_uuid(), ?, ?, 'VW', 'Golf', 'DIESEL') "
                        + "RETURNING id", Long.class, idMarie, "PCE" + n);
        UUID reference = jdbc.queryForObject("INSERT INTO intervention (numero, vehicule_id, statut, "
                        + "montant_devis_htva) VALUES (?, ?, 'PLANIFIEE', ?) RETURNING reference",
                UUID.class, "INT-PCE-" + n, vehicule, new BigDecimal(devisHtva));
        service.demarrer(reference);
        return reference;
    }

    private Piece piece(String libelle, int stock) {
        Piece piece = new Piece(freinage, "IT-PCE-" + COMPTEUR.getAndIncrement(), libelle, new BigDecimal("40.00"));
        piece.setQuantiteStock(stock);
        return pieces.save(piece);
    }

    private void ajouter(UUID intervention, Piece piece, int quantite) throws Exception {
        mvc.perform(post("/admin/interventions/{ref}/lignes/piece", intervention)
                        .param("piece", piece.getReference().toString())
                        .param("quantite", String.valueOf(quantite))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"));
    }

    private String detail(UUID intervention) throws Exception {
        return mvc.perform(get("/admin/interventions/{ref}", intervention).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("le formulaire ajoute la pièce, le stock reste intact, aucune mention si le stock suffit")
    void ajoutParLeFormulaire() throws Exception {
        UUID dossier = interventionEnCours("1000.00");
        Piece disques = piece("Disques avant IT", 5);

        ajouter(dossier, disques, 2);

        assertThat(detail(dossier)).contains("Disques avant IT").doesNotContain(A_COMMANDER);
        assertThat(jdbc.queryForObject("SELECT quantite_stock FROM piece WHERE id = ?", Integer.class,
                disques.getId())).isEqualTo(5);
    }

    @Test
    @DisplayName("deux dossiers demandent la même pièce unique : la mention paraît sur les deux")
    void deuxDossiersMemePieceUnique() throws Exception {
        UUID premier = interventionEnCours("1000.00");
        UUID second = interventionEnCours("1000.00");
        Piece etrier = piece("Étrier IT", 1);

        ajouter(premier, etrier, 1);
        assertThat(detail(premier)).doesNotContain(A_COMMANDER);

        ajouter(second, etrier, 1);
        assertThat(detail(premier)).contains(A_COMMANDER);
        assertThat(detail(second)).contains(A_COMMANDER);
    }

    @Test
    @DisplayName("une ligne en attente de l'accord du membre compte, une ligne refusée ne compte plus")
    void ligneEnAttentePuisRefusee() throws Exception {
        UUID servi = interventionEnCours("1000.00");
        // Devis nul : tout ajout dépasse le seuil et attend la réponse du membre (RM-15).
        UUID soumis = interventionEnCours("0.00");
        Piece flexible = piece("Flexible IT", 1);
        ajouter(servi, flexible, 1);

        ajouter(soumis, flexible, 1);
        assertThat(jdbc.queryForObject("SELECT statut FROM intervention WHERE reference = ?", String.class,
                soumis)).isEqualTo(StatutIntervention.ATTENTE_VALIDATION_MEMBRE.name());
        assertThat(detail(servi)).contains(A_COMMANDER);

        // Le membre refuse par son propre formulaire : la ligne sort de la quantité engagée.
        mvc.perform(post("/mes-interventions/{ref}/refuser", soumis).with(user(marie)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(detail(servi)).doesNotContain(A_COMMANDER);
        assertThat(detail(soumis)).doesNotContain(A_COMMANDER);
    }

    @Test
    @DisplayName("une pièce retirée de la vente est refusée avec un message, aucune ligne n'est ajoutée")
    void pieceRetireeDeLaVente() throws Exception {
        UUID dossier = interventionEnCours("1000.00");
        Piece ancienne = piece("Pièce retirée IT", 3);
        ancienne.desactiver();
        pieces.saveAndFlush(ancienne);

        mvc.perform(post("/admin/interventions/{ref}/lignes/piece", dossier)
                        .param("piece", ancienne.getReference().toString())
                        .param("quantite", "1")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("erreur"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ligne_intervention l JOIN intervention i "
                + "ON i.id = l.intervention_id WHERE i.reference = ?", Integer.class, dossier)).isZero();
    }

    @Test
    @DisplayName("le membre voit la pièce dans son suivi, jamais la mention « à commander »")
    void mentionReserveeAuGarage() throws Exception {
        UUID dossier = interventionEnCours("1000.00");
        Piece rotule = piece("Rotule IT", 0);
        ajouter(dossier, rotule, 1);
        assertThat(detail(dossier)).contains(A_COMMANDER);

        mvc.perform(get("/mes-interventions/{ref}", dossier).with(user(marie)).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rotule IT")))
                .andExpect(content().string(not(containsString(A_COMMANDER))));
    }
}
