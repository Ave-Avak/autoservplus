package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.domain.UnitePrestation;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.importcsv.service.ImportCatalogueService;
import be.autoservplus.importcsv.service.ImportRefuseException;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prix pose seule et composition d'une prestation (V76) : formulaire et historique du
 * back-office, garde RM-29 des pièces, contraintes en base, fiche publique, API et import.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Prix pose seule et composition d'une prestation (intégration)")
class PoseSeuleEtCompositionIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String ENTETE = "categorie;code;libelle;description;prix_htva;taux_tva;duree_minutes";

    @Autowired private MockMvc mvc;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PieceRepository pieces;
    @Autowired private ImportCatalogueService imports;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private String suffixe;
    private Categorie atelier;
    private Categorie filtres;

    @BeforeEach
    void setUp() {
        suffixe = "PS" + COMPTEUR.getAndIncrement();
        atelier = categories.save(new Categorie("IT-" + suffixe, "Atelier " + suffixe, TypeCategorie.SERVICE));
        filtres = categories.save(new Categorie("IT-P-" + suffixe, "Filtres " + suffixe, TypeCategorie.PIECE));
    }

    @AfterEach
    void langueParDefaut() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Prestation vidange(String poseSeule) {
        Prestation vidange = new Prestation(atelier, suffixe + "-VID", "Vidange " + suffixe,
                new BigDecimal("69.00"), 30);
        if (poseSeule != null) {
            vidange.fixerPrix(new BigDecimal("69.00"), new BigDecimal(poseSeule));
        }
        return prestations.saveAndFlush(vidange);
    }

    private Piece piece(String reference) {
        return pieces.saveAndFlush(new Piece(filtres, suffixe + "-" + reference, "Pièce " + reference,
                new BigDecimal("12.40")));
    }

    /**
     * Les écritures du service attendent le flush de la transaction du test : une lecture
     * par JDBC ne les verrait pas sans lui.
     */
    private JdbcTemplate base() {
        entityManager.flush();
        return jdbc;
    }

    /**
     * Fin d'une requête du back-office. La transaction du test sert toutes les requêtes
     * d'un cas : sans ce vidage, la collection de la prestation garderait l'instantané pris à
     * sa création, et Hibernate ne verrait pas l'orphelin d'un retrait. En service, chaque
     * requête relit la prestation dans une session neuve.
     */
    private void finDeRequete() {
        entityManager.flush();
        entityManager.clear();
    }

    private BigDecimal poseSeuleEnBase(String code) {
        return base().queryForObject("SELECT prix_pose_seule_htva FROM service WHERE code = ?", BigDecimal.class, code);
    }

    private List<String> composition(Prestation prestation) {
        return base().queryForList("""
                SELECT p.reference_fabricant || ':' || c.quantite FROM composition_prestation c
                JOIN piece p ON p.id = c.piece_id WHERE c.service_id = ? ORDER BY c.id
                """, String.class, prestation.getId());
    }

    private List<String> historique(Prestation prestation, String champ) {
        return base().queryForList("""
                SELECT coalesce(valeur_avant, '-') || ' / ' || coalesce(valeur_apres, '-')
                FROM historique_modification_catalogue
                WHERE type_entite = 'PRESTATION' AND entite_id = ? AND champ_modifie = ? ORDER BY id
                """, String.class, prestation.getId(), champ);
    }

    @Nested
    @DisplayName("Formulaire du back-office")
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    class Formulaire {

        @Test
        @DisplayName("création avec un prix pose seule")
        void creation() throws Exception {
            String code = suffixe + "-PLAQ";
            mvc.perform(post("/admin/catalogue/prestations").with(csrf())
                            .param("codeCategorie", "IT-" + suffixe).param("code", code)
                            .param("libelle", "Plaquettes " + suffixe).param("prixHtva", "99.00")
                            .param("prixPoseSeuleHtva", "59.00")
                            .param("tauxTva", "21.00").param("dureeMinutes", "60").param("unite", "FORFAIT"))
                    .andExpect(status().is3xxRedirection());

            assertThat(poseSeuleEnBase(code)).isEqualByComparingTo("59.00");
        }

        @Test
        @DisplayName("un prix pose seule égal au prix pièces comprises est refusé sous son champ")
        void egalRefuse() throws Exception {
            mvc.perform(post("/admin/catalogue/prestations").with(csrf())
                            .param("codeCategorie", "IT-" + suffixe).param("code", suffixe + "-PLAQ")
                            .param("libelle", "Plaquettes " + suffixe).param("prixHtva", "99.00")
                            .param("prixPoseSeuleHtva", "99.00")
                            .param("tauxTva", "21.00").param("dureeMinutes", "60").param("unite", "FORFAIT"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrorCode("formulaire", "prixPoseSeuleHtva",
                            "admin.catalogue.erreur.pose-seule-prix"));
        }

        @Test
        @DisplayName("un prix pose seule sur une prestation à l'heure est refusé sous son champ")
        void aLHeureRefuse() throws Exception {
            mvc.perform(post("/admin/catalogue/prestations").with(csrf())
                            .param("codeCategorie", "IT-" + suffixe).param("code", suffixe + "-MO")
                            .param("libelle", "Main-d'œuvre " + suffixe).param("prixHtva", "65.00")
                            .param("prixPoseSeuleHtva", "30.00")
                            .param("tauxTva", "21.00").param("dureeMinutes", "60").param("unite", "HEURE"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrorCode("formulaire", "prixPoseSeuleHtva",
                            "admin.catalogue.erreur.pose-seule-heure"));
        }

        @Test
        @DisplayName("la modification écrit le prix pose seule à l'historique, et un champ vide le retire")
        void modificationHistorisee() throws Exception {
            Prestation vidange = vidange(null);

            for (String poseSeule : new String[]{"39.00", ""}) {
                mvc.perform(post("/admin/catalogue/prestations/{ref}/modifier", vidange.getReference()).with(csrf())
                                .param("codeCategorie", "IT-" + suffixe).param("code", vidange.getCode())
                                .param("libelle", vidange.getLibelle()).param("prixHtva", "69.00")
                                .param("prixPoseSeuleHtva", poseSeule)
                                .param("tauxTva", "21.00").param("dureeMinutes", "30").param("unite", "FORFAIT")
                                .param("actif", "true"))
                        .andExpect(status().is3xxRedirection());
            }

            assertThat(poseSeuleEnBase(vidange.getCode())).isNull();
            assertThat(historique(vidange, "prixPoseSeuleHtva")).containsExactly("- / 39.00", "39.00 / -");
        }

        @Test
        @DisplayName("la page de modification montre la composition et les pièces à y ajouter")
        void pageDeModification() throws Exception {
            Prestation vidange = vidange("39.00");
            Piece filtre = piece("FILTRE");

            mvc.perform(get("/admin/catalogue/prestations/{ref}/modifier", vidange.getReference())
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"composition\"")))
                    .andExpect(content().string(containsString("value=\"39.00\"")))
                    .andExpect(content().string(containsString(filtre.getReferenceFabricant())));
        }
    }

    @Nested
    @DisplayName("Composition")
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    class Composition {

        private void fixer(Prestation prestation, Piece piece, String quantite) throws Exception {
            mvc.perform(post("/admin/catalogue/prestations/{ref}/composition", prestation.getReference())
                            .with(csrf()).param("piece", piece.getReference().toString()).param("quantite", quantite))
                    .andExpect(redirectedUrl("/admin/catalogue/prestations/" + prestation.getReference()
                            + "/modifier#composition"));
            finDeRequete();
        }

        @Test
        @DisplayName("ajout, changement de quantité et retrait, chacun écrit à l'historique")
        void ajoutQuantiteRetrait() throws Exception {
            Prestation vidange = vidange(null);
            Piece huile = piece("HUILE");
            Piece filtre = piece("FILTRE");

            fixer(vidange, huile, "1");
            fixer(vidange, filtre, "1");
            fixer(vidange, huile, "2");
            mvc.perform(post("/admin/catalogue/prestations/{ref}/composition/{piece}/retirer",
                            vidange.getReference(), filtre.getReference()).with(csrf()))
                    .andExpect(status().is3xxRedirection());

            finDeRequete();

            assertThat(composition(vidange)).containsExactly(huile.getReferenceFabricant() + ":2");
            String h = huile.getReferenceFabricant();
            String f = filtre.getReferenceFabricant();
            assertThat(historique(vidange, "composition")).containsExactly(
                    "- / " + h + " (1)",
                    h + " (1) / " + h + " (1), " + f + " (1)",
                    h + " (1), " + f + " (1) / " + h + " (2), " + f + " (1)",
                    h + " (2), " + f + " (1) / " + h + " (2)");
        }

        @Test
        @DisplayName("une quantité nulle est refusée par un message, rien n'est écrit")
        void quantiteRefusee() throws Exception {
            Prestation vidange = vidange(null);
            Piece filtre = piece("FILTRE");

            mvc.perform(post("/admin/catalogue/prestations/{ref}/composition", vidange.getReference())
                            .with(csrf()).param("piece", filtre.getReference().toString()).param("quantite", "0")
                            .locale(Locale.FRENCH))
                    .andExpect(flash().attribute("erreur", "La quantité d'une pièce de la composition vaut au moins 1."));

            assertThat(composition(vidange)).isEmpty();
            assertThat(historique(vidange, "composition")).isEmpty();
        }

        @Test
        @DisplayName("une pièce présente dans une composition compte comme référencée (RM-29)")
        void pieceReferencee() throws Exception {
            Prestation vidange = vidange(null);
            Piece filtre = piece("FILTRE");
            fixer(vidange, filtre, "1");

            assertThat(pieces.nombreReferencesHistoriques(filtre.getId())).isEqualTo(1);
            mvc.perform(post("/admin/catalogue/pieces/{ref}/supprimer", filtre.getReference()).with(csrf()))
                    .andExpect(flash().attributeExists("erreur"));
            assertThat(pieces.findByReference(filtre.getReference())).isPresent();
        }

        @Test
        @DisplayName("la suppression définitive d'une prestation emporte sa composition")
        void suppressionDeLaPrestation() throws Exception {
            Prestation vidange = vidange(null);
            Piece filtre = piece("FILTRE");
            fixer(vidange, filtre, "1");

            mvc.perform(post("/admin/catalogue/prestations/{ref}/supprimer", vidange.getReference()).with(csrf()))
                    .andExpect(flash().attributeExists("message"));

            assertThat(base().queryForObject("SELECT count(*) FROM composition_prestation WHERE piece_id = ?",
                    Long.class, filtre.getId())).isZero();
        }
    }

    @Nested
    @DisplayName("Contraintes en base (V76)")
    class Contraintes {

        @Test
        @DisplayName("un prix pose seule égal au prix pièces comprises est refusé par ck_service_pose_seule")
        void poseSeuleEgale() {
            Prestation vidange = vidange(null);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE service SET prix_pose_seule_htva = prix_htva WHERE id = ?", vidange.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_service_pose_seule");
        }

        @Test
        @DisplayName("un prix pose seule sur une prestation à l'heure est refusé par ck_service_pose_seule_forfait")
        void poseSeuleALHeure() {
            Prestation mainDOeuvre = prestations.saveAndFlush(new Prestation(atelier, suffixe + "-MO",
                    "Main-d'œuvre " + suffixe, new BigDecimal("65.00"), 60, UnitePrestation.HEURE));

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE service SET prix_pose_seule_htva = 10.00 WHERE id = ?", mainDOeuvre.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_service_pose_seule_forfait");
        }

        @Test
        @DisplayName("une quantité nulle est refusée par ck_composition_quantite")
        void quantiteNulle() {
            Prestation vidange = vidange(null);
            Piece filtre = piece("FILTRE");

            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO composition_prestation (service_id, piece_id, quantite) VALUES (?, ?, 0)",
                    vidange.getId(), filtre.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_composition_quantite");
        }

        @Test
        @DisplayName("une pièce en double dans une composition est refusée par uq_composition_prestation")
        void doublon() {
            Prestation vidange = vidange(null);
            Piece filtre = piece("FILTRE");
            String insertion = "INSERT INTO composition_prestation (service_id, piece_id, quantite) VALUES (?, ?, 1)";
            jdbc.update(insertion, vidange.getId(), filtre.getId());

            assertThatThrownBy(() -> jdbc.update(insertion, vidange.getId(), filtre.getId()))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uq_composition_prestation");
        }
    }

    @Nested
    @DisplayName("Catalogue public et API")
    class Public {

        @Test
        @DisplayName("la fiche donne les deux prix, TVA comprise et hors TVA")
        void fiche() throws Exception {
            Prestation vidange = vidange("39.00");

            mvc.perform(get("/services/{ref}", vidange.getReference()).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Pièces fournies par le garage.")))
                    .andExpect(content().string(containsString(
                            "Si vous apportez vos pièces : 47,19 € TVA comprise, soit 39,00 € hors TVA.")));
        }

        @Test
        @DisplayName("une fiche sans prix pose seule ne parle pas du choix")
        void ficheSansChoix() throws Exception {
            Prestation vidange = vidange(null);

            mvc.perform(get("/services/{ref}", vidange.getReference()).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            containsString("Si vous apportez vos pièces"))));
        }

        @Test
        @DisplayName("l'API publique donne le prix pose seule, nul sans choix")
        void api() throws Exception {
            Prestation avec = vidange("39.00");

            String json = mvc.perform(get("/api/v1/prestations").param("taille", "100"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(json).containsPattern("\"code\":\"" + avec.getCode()
                    + "\"[^}]*\"prixPoseSeuleHtva\":39\\.00,\"prixPoseSeuleTvac\":47\\.19");
        }
    }

    @Nested
    @DisplayName("Import CSV")
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    class Import {

        private byte[] csv(String entete, String ligne) {
            return (entete + "\n" + ligne).getBytes(StandardCharsets.UTF_8);
        }

        private String ligne(Prestation prestation, String prix) {
            return "IT-" + suffixe + ";" + prestation.getCode() + ";" + prestation.getLibelle() + ";;"
                    + prix + ";21.00;30";
        }

        @Test
        @DisplayName("colonne absente : le prix pose seule en place reste")
        void colonneAbsente() {
            Prestation vidange = vidange("39.00");

            imports.importerPrestations(csv(ENTETE, ligne(vidange, "72.00")));

            assertThat(poseSeuleEnBase(vidange.getCode())).isEqualByComparingTo("39.00");
        }

        @Test
        @DisplayName("colonne présente : une valeur fixe le prix, une cellule vide le retire")
        void colonnePresente() {
            Prestation vidange = vidange(null);
            String entete = ENTETE + ";prix_pose_seule_htva";

            imports.importerPrestations(csv(entete, ligne(vidange, "69.00") + ";41,50"));
            assertThat(poseSeuleEnBase(vidange.getCode())).isEqualByComparingTo("41.50");

            imports.importerPrestations(csv(entete, ligne(vidange, "69.00") + ";"));
            assertThat(poseSeuleEnBase(vidange.getCode())).isNull();
        }

        @Test
        @DisplayName("un prix pose seule supérieur au prix est refusé avec le numéro de ligne, dans la langue de l'écran")
        void refuse() {
            Prestation vidange = vidange(null);
            LocaleContextHolder.setLocale(Locale.forLanguageTag("nl"));

            ImportRefuseException refus = catchThrowableOfType(ImportRefuseException.class,
                    () -> imports.importerPrestations(csv(ENTETE + ";prix_pose_seule_htva",
                            ligne(vidange, "69.00") + ";80.00")));

            assertThat(refus.rapport().erreurs()).singleElement().satisfies(erreur -> {
                assertThat(erreur.ligne()).isEqualTo(2);
                assertThat(erreur.motif()).startsWith("De prijs alleen montage");
            });
            assertThat(poseSeuleEnBase(vidange.getCode())).isNull();
        }
    }
}
