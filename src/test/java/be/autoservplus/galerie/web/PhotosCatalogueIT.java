package be.autoservplus.galerie.web;

import be.autoservplus.galerie.service.AdminGalerieService;
import be.autoservplus.galerie.service.GalerieService;
import be.autoservplus.galerie.service.dto.PhotoVue;
import be.autoservplus.stockage.service.StockageMedia;
import be.autoservplus.support.SocleIntegration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Photos du catalogue après V79 : visuel de catégorie, dimensions, vignette et texte
 * alternatif traduit, sur un PostgreSQL réel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Photos du catalogue, V79 (intégration)")
class PhotosCatalogueIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AdminGalerieService admin;
    @Autowired private GalerieService galerie;
    @Autowired private StockageMedia stockage;

    private long categorie;
    private UUID piece;

    @BeforeEach
    void categorieEtPieceDeTest() {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        categorie = jdbc.queryForObject("""
                INSERT INTO categorie (code, libelle, type, rayon)
                VALUES (?, 'Catégorie de test des photos', 'PIECE', 'PIECES_AUTO') RETURNING id
                """, Long.class, "T_PHOTO_" + suffixe);
        piece = jdbc.queryForObject("""
                INSERT INTO piece (categorie_id, reference_fabricant, libelle, prix_htva)
                VALUES (?, ?, 'Pièce de test des photos', 10.00) RETURNING reference
                """, UUID.class, categorie, "REF-PHOTO-" + suffixe);
    }

    /** La catégorie et la pièce de test ne restent pas dans les listes des autres classes. */
    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM photo WHERE categorie_id = ? OR piece_id = (SELECT id FROM piece WHERE reference = ?)",
                categorie, piece);
        jdbc.update("DELETE FROM piece WHERE reference = ?", piece);
        jdbc.update("DELETE FROM categorie WHERE id = ?", categorie);
    }

    private UUID photoDeCategorie(String chemin, String vignette, String nl) {
        return jdbc.queryForObject("""
                INSERT INTO photo (categorie_id, chemin, chemin_vignette, texte_alt, texte_alt_nl,
                                   ordre, largeur, hauteur)
                VALUES (?, ?, ?, 'Visuel de la catégorie', ?, 0, 1024, 768) RETURNING reference
                """, UUID.class, categorie, chemin, vignette, nl);
    }

    private static byte[] png(int largeur, int hauteur) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB), "png", sortie);
        return sortie.toByteArray();
    }

    private Document fiche(UUID reference, Locale langue) throws Exception {
        return Jsoup.parse(mvc.perform(get("/pieces/{r}", reference).locale(langue))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("Contraintes de V79")
    class Contraintes {

        @Test
        @DisplayName("la catégorie est une origine admise, exclusive des trois autres")
        void categorieExclusive() {
            assertThat(photoDeCategorie("t/admise.webp", null, null)).isNotNull();

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO photo (categorie_id, service_id, chemin, texte_alt, ordre)
                    VALUES (?, (SELECT id FROM service LIMIT 1), 't/double.webp', 'deux origines', 0)
                    """, categorie))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_photo_origine_unique");
        }

        @Test
        @DisplayName("une largeur sans hauteur est refusée, elle ne réserverait aucune place")
        void dimensionsEnsemble() {
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO photo (categorie_id, chemin, texte_alt, ordre, largeur)
                    VALUES (?, 't/largeur-seule.webp', 'largeur seule', 0, 640)
                    """, categorie))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_photo_dimensions");
        }
    }

    @Nested
    @DisplayName("Dimensions lues au dépôt")
    class Depot {

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("une image lisible garde sa largeur et sa hauteur")
        void dimensionsPosees() throws IOException {
            var fichier = new MockMultipartFile("fichiers", "vue.png", "image/png", png(40, 30));

            admin.ajouterAPiece(piece, List.of(fichier), "Vue de la pièce");

            PhotoVue vue = galerie.dePiece(piece).getFirst();
            assertThat(vue.largeur()).isEqualTo(40);
            assertThat(vue.hauteur()).isEqualTo(30);
            assertThat(vue.vignette()).as("une photo déposée reste en une taille").isFalse();
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("un en-tête illisible laisse la photo sans dimensions, sans refuser le dépôt")
        void enTeteIllisible() {
            byte[] tronque = new byte[64];
            tronque[0] = (byte) 0xFF;
            tronque[1] = (byte) 0xD8;
            tronque[2] = (byte) 0xFF;
            var fichier = new MockMultipartFile("fichiers", "tronque.jpg", "image/jpeg", tronque);

            assertThat(admin.ajouterAPiece(piece, List.of(fichier), "Vue")).isEqualTo(1);

            PhotoVue vue = galerie.dePiece(piece).getFirst();
            assertThat(vue.dimensionsConnues()).isFalse();
        }
    }

    @Nested
    @DisplayName("Fiche publique d'une pièce")
    class Fiche {

        @Test
        @DisplayName("sans photo propre, la fiche montre le visuel de la catégorie, vignette comprise")
        void visuelDeCategorie() throws Exception {
            UUID visuel = photoDeCategorie("t/visuel.webp", "t/visuel-480.webp", null);

            Element image = fiche(piece, Locale.FRENCH).selectFirst(".galerie img");

            assertThat(image).isNotNull();
            assertThat(image.attr("src")).isEqualTo("/images/galerie/" + visuel);
            assertThat(image.attr("srcset"))
                    .isEqualTo("/images/galerie/" + visuel + "/vignette 480w, /images/galerie/" + visuel + " 1024w");
            assertThat(image.attr("width")).isEqualTo("300");
            assertThat(image.attr("height")).as("hauteur affichée déduite des dimensions").isEqualTo("225");
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("avec une photo propre, le visuel de la catégorie n'apparaît pas")
        void photoPropreAvantVisuel() throws Exception {
            UUID visuel = photoDeCategorie("t/visuel.webp", null, null);
            admin.ajouterAPiece(piece, List.of(new MockMultipartFile("fichiers", "vue.png",
                    "image/png", png(8, 8))), "Vue propre");

            var images = fiche(piece, Locale.FRENCH).select(".galerie img");

            assertThat(images).hasSize(1);
            assertThat(images.getFirst().attr("src")).doesNotContain(visuel.toString());
            assertThat(images.getFirst().hasAttr("srcset")).as("pas de vignette, pas de srcset").isFalse();
        }

        @Test
        @DisplayName("le texte alternatif suit la langue, et le français déclare sa langue en repli")
        void texteAlternatifTraduit() throws Exception {
            photoDeCategorie("t/visuel.webp", null, "Visual van de categorie");

            Element neerlandais = fiche(piece, Locale.forLanguageTag("nl")).selectFirst(".galerie img");
            Element anglais = fiche(piece, Locale.ENGLISH).selectFirst(".galerie img");

            assertThat(neerlandais.attr("alt")).isEqualTo("Visual van de categorie");
            assertThat(neerlandais.hasAttr("lang")).isFalse();
            assertThat(anglais.attr("alt")).as("anglais absent : repli sur le français").isEqualTo("Visuel de la catégorie");
            assertThat(anglais.attr("lang")).isEqualTo("fr");
        }
    }

    @Nested
    @DisplayName("Vignette")
    class Vignette {

        @Test
        @DisplayName("la vignette est servie quand elle existe")
        void vignetteServie() throws Exception {
            String nom = "t/" + UUID.randomUUID() + "-480.png";
            stockage.deposerSiAbsent(nom, new ByteArrayInputStream(png(48, 36)));
            UUID photo = photoDeCategorie("t/sans-fichier.webp", nom, null);

            mvc.perform(get("/images/galerie/{p}/vignette", photo))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("une photo sans vignette répond 404 à l'adresse de la vignette")
        void sansVignette() throws Exception {
            UUID photo = photoDeCategorie("t/sans-vignette.webp", null, null);

            mvc.perform(get("/images/galerie/{p}/vignette", photo))
                    .andExpect(status().isNotFound());
        }
    }
}
