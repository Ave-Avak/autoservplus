package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.service.PanierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendu des ecrans du back-office catalogue (A1-A6) contre le contexte Spring
 * complet : i18n FR/NL par le MessageSource (y compris les messages de contrainte
 * Bean Validation en cles i18n), diagnostic RM-29 a l ecran, securite d URL reelle
 * de SecuriteConfig et CSRF. Les requetes fixent la locale : MockMvc est anglophone
 * par defaut.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("Templates back-office catalogue (integration)")
class AdminCatalogueTemplatesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PieceRepository pieces;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PanierService panierService;

    private Categorie entretien;
    private Categorie freinage;
    private Prestation vidange;
    private Piece plaquettes;

    @BeforeEach
    void setUp() {
        entretien = categories.save(new Categorie("IT-TPL-ENT", "Entretien", TypeCategorie.SERVICE));
        freinage = categories.save(new Categorie("IT-TPL-FRE", "Freinage", TypeCategorie.PIECE));
        vidange = prestations.save(new Prestation(entretien, "IT-TPL-VID", "Vidange",
                new BigDecimal("49.00"), 30));
        Prestation diagnostic = new Prestation(entretien, "IT-TPL-DIA", "Diagnostic",
                new BigDecimal("39.00"), 20);
        diagnostic.desactiver();
        prestations.save(diagnostic);
        plaquettes = new Piece(freinage, "IT-TPL-001", "Plaquettes avant", new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(10);
        plaquettes = pieces.save(plaquettes);
    }

    @Test
    @DisplayName("la liste des prestations affiche actifs ET inactifs avec leur statut")
    void listePrestationsCompleTe() throws Exception {
        mvc.perform(get("/admin/catalogue/prestations").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vidange")))
                .andExpect(content().string(containsString("Diagnostic")))
                .andExpect(content().string(containsString("Inactif")))
                .andExpect(content().string(containsString("Nouvelle prestation")));
    }

    @Test
    @DisplayName("A1 : la creation aboutit de bout en bout et la prestation est en base")
    void creationDeBoutEnBout() throws Exception {
        mvc.perform(get("/admin/catalogue/prestations/nouvelle").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nouvelle prestation")))
                .andExpect(content().string(containsString("Entretien")));

        mvc.perform(post("/admin/catalogue/prestations").with(csrf()).locale(Locale.FRENCH)
                        .param("codeCategorie", "IT-TPL-ENT")
                        .param("code", "IT-TPL-GEO")
                        .param("libelle", "Géométrie")
                        .param("prixHtva", "89.00")
                        .param("tauxTva", "21.00")
                        .param("dureeMinutes", "45")
                        .param("actif", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/catalogue/prestations"))
                .andExpect(flash().attributeExists("message"));

        assertThat(prestations.existsByCode("IT-TPL-GEO")).isTrue();
        assertThat(prestations.existsByLibelleIgnoreCase("Géométrie")).isTrue();
    }

    @Test
    @DisplayName("la validation serveur re-affiche le formulaire avec le message i18n de contrainte")
    void validationServeurAvecMessageI18n() throws Exception {
        mvc.perform(post("/admin/catalogue/prestations").with(csrf()).locale(Locale.FRENCH)
                        .param("codeCategorie", "IT-TPL-ENT")
                        .param("code", "IT-TPL-X")
                        .param("libelle", "")
                        .param("prixHtva", "10.00")
                        .param("tauxTva", "21.00")
                        .param("dureeMinutes", "30"))
                .andExpect(status().isOk())
                // Cle {admin.catalogue.validation.libelle} resolue par le MessageSource.
                .andExpect(content().string(containsString("Le nom est obligatoire")));
    }

    @Test
    @DisplayName("A1 : un nom deja pris re-affiche le formulaire avec l erreur sur le champ")
    void nomDupliqueSignaleAuChamp() throws Exception {
        mvc.perform(post("/admin/catalogue/prestations").with(csrf()).locale(Locale.FRENCH)
                        .param("codeCategorie", "IT-TPL-ENT")
                        .param("code", "IT-TPL-VID2")
                        .param("libelle", "Vidange")
                        .param("prixHtva", "59.00")
                        .param("tauxTva", "21.00")
                        .param("dureeMinutes", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("porte déjà ce nom")));

        assertThat(prestations.existsByCode("IT-TPL-VID2")).isFalse();
    }

    @Test
    @DisplayName("RM-29 : la confirmation d une piece au panier ne propose QUE la desactivation")
    void ecranSuppressionPieceReferencee() throws Exception {
        utilisateurs.save(new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        panierService.ajouterPiece("marie@exemple.be", plaquettes.getReference(), 2);

        mvc.perform(get("/admin/catalogue/pieces/{ref}/supprimer", plaquettes.getReference())
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("seule la désactivation est proposée")))
                .andExpect(content().string(containsString("Désactiver")))
                .andExpect(content().string(not(containsString("Oui, supprimer définitivement"))));
    }

    @Test
    @DisplayName("RM-29 : la confirmation d une piece jamais referencee propose la suppression definitive")
    void ecranSuppressionPieceLibre() throws Exception {
        mvc.perform(get("/admin/catalogue/pieces/{ref}/supprimer", plaquettes.getReference())
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Oui, supprimer définitivement")))
                .andExpect(content().string(not(containsString("seule la désactivation est proposée"))));
    }

    @Test
    @DisplayName("RM-28 : la desactivation aboutit et la piece disparait du catalogue public")
    void desactivationDeBoutEnBout() throws Exception {
        mvc.perform(post("/admin/catalogue/pieces/{ref}/desactiver", plaquettes.getReference())
                        .with(csrf()).locale(Locale.FRENCH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"));

        assertThat(pieces.findByReference(plaquettes.getReference()).orElseThrow().isActif())
                .isFalse();

        // RM-28 cote public : la piece n apparait plus dans la liste du catalogue.
        mvc.perform(get("/pieces").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Plaquettes avant"))));
    }

    @Test
    @DisplayName("les ecrans sont traduits : la liste rendue en neerlandais")
    void listeEnNeerlandais() throws Exception {
        mvc.perform(get("/admin/catalogue/prestations").locale(Locale.forLanguageTag("nl")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Catalogus — diensten")))
                .andExpect(content().string(containsString("Nieuwe dienst")));
    }

    @Test
    @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
    @DisplayName("la securite d URL reelle refuse un membre (403) sur tout /admin/catalogue")
    void interditAuxMembres() throws Exception {
        mvc.perform(get("/admin/catalogue/prestations"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/catalogue/pieces/nouvelle"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST sans jeton CSRF est rejete")
    void csrfRequis() throws Exception {
        mvc.perform(post("/admin/catalogue/prestations")
                        .param("libelle", "Vidange"))
                .andExpect(status().isForbidden());
    }
}
