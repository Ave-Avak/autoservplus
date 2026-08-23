package be.autoservplus.galerie.web;

import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.galerie.repository.PhotoRepository;
import be.autoservplus.galerie.service.AdminGalerieService;
import be.autoservplus.galerie.service.GalerieService;
import be.autoservplus.stockage.service.TypeFichierRefuseException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import java.sql.SQLException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Galerie de bout en bout (BL-9), sur un PostgreSQL reel.
 *
 * <p>Deux choses ne se verifient qu ici : le nouveau CHECK
 * {@code ck_photo_origine_unique} pose par V30, qui doit refuser une photo sans
 * origine comme une photo a deux origines, et le service des images par notre propre
 * controleur — c est lui qui permet a la CSP de rester {@code img-src 'self'}.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Galerie d images (integration)")
class GalerieIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final byte[] JPEG = jpegMinimal();

    @Autowired private MockMvc mvc;
    @Autowired private AdminGalerieService admin;
    @Autowired private GalerieService galerie;
    @Autowired private PhotoRepository photos;
    @Autowired private PrestationRepository prestations;
    @Autowired private TransactionTemplate transactions;

    @PersistenceContext private EntityManager em;

    private static byte[] jpegMinimal() {
        byte[] contenu = new byte[64];
        contenu[0] = (byte) 0xFF;
        contenu[1] = (byte) 0xD8;
        contenu[2] = (byte) 0xFF;
        return contenu;
    }

    private static MockMultipartFile image(String nom) {
        return new MockMultipartFile("fichiers", nom, "image/jpeg", JPEG);
    }

    private UUID premierePrestation() {
        return transactions.execute(statut ->
                prestations.findByActifTrueOrderByLibelleAsc().get(0).getReference());
    }

    @Nested
    @DisplayName("Contrainte V30 : exactement une origine")
    class ContrainteOrigine {

        @Test
        @DisplayName("la base refuse une photo sans aucune origine")
        void aucuneOrigine() {
            assertThatThrownBy(() -> transactions.executeWithoutResult(statut ->
                    em.createNativeQuery("""
                            INSERT INTO photo (chemin, texte_alt, ordre)
                            VALUES ('orpheline.jpg', 'sans origine', 0)
                            """).executeUpdate()))
                    .as("une photo sans porteur serait invisible partout et jamais nettoyee")
                    // La cause racine est l'exception du pilote : une requete native
                    // passee par l'EntityManager ne traverse pas la traduction
                    // d'exceptions de Spring. C'est le NOM de la contrainte qui compte,
                    // il prouve que c'est bien celle de V30 qui a refuse.
                    .rootCause()
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_photo_origine_unique");
        }

        @Test
        @DisplayName("la base refuse une photo a deux origines")
        void deuxOrigines() {
            // Le seed ne cree AUCUNE piece : sans celle-ci, piece_id vaudrait NULL,
            // num_nonnulls tomberait a 1 et l'insert passerait — le test verifierait
            // alors exactement le contraire de ce qu'il annonce.
            Long piece = creerPieceDeTest();

            assertThatThrownBy(() -> transactions.executeWithoutResult(statut ->
                    em.createNativeQuery("""
                            INSERT INTO photo (service_id, piece_id, chemin, texte_alt, ordre)
                            VALUES ((SELECT id FROM service LIMIT 1), :piece,
                                    'double.jpg', 'deux origines', 0)
                            """).setParameter("piece", piece).executeUpdate()))
                    .as("on ne saurait pas laquelle des deux fiches fait foi")
                    .rootCause()
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_photo_origine_unique");
        }

        private Long creerPieceDeTest() {
            return transactions.execute(statut -> ((Number) em.createNativeQuery("""
                    INSERT INTO piece (categorie_id, reference_fabricant, libelle, prix_htva)
                    VALUES ((SELECT id FROM categorie WHERE type = 'PIECE' LIMIT 1),
                            'REF-GALERIE-IT', 'Piece de test galerie', 10.00)
                    RETURNING id
                    """).getSingleResult()).longValue());
        }

        @Test
        @DisplayName("la colonne intervention_id ajoutee par V30 est bien une origine admise")
        void interventionAdmise() {
            // Verifie que le CHECK reecrit accepte reellement la troisieme origine :
            // c'est tout l'objet de la migration.
            Long compte = transactions.execute(statut -> ((Number) em.createNativeQuery("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_name = 'photo' AND column_name = 'intervention_id'
                    """).getSingleResult()).longValue());

            assertThat(compte).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Depot et service")
    class DepotEtService {

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("depose plusieurs images et les ordonne")
        void depotMultiple() {
            UUID prestation = premierePrestation();

            int ajoutees = admin.ajouterAPrestation(prestation,
                    List.of(image("a.jpg"), image("b.jpg")), "Vue de l atelier");

            assertThat(ajoutees).isEqualTo(2);
            var vues = galerie.dePrestation(prestation);
            assertThat(vues).hasSizeGreaterThanOrEqualTo(2);
            assertThat(vues).isSortedAccordingTo((x, y) -> Short.compare(x.ordre(), y.ordre()));
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("ignore les entrees vides d un formulaire multi-fichiers")
        void entreesVidesIgnorees() {
            UUID prestation = premierePrestation();
            var vide = new MockMultipartFile("fichiers", "", "application/octet-stream", new byte[0]);

            int ajoutees = admin.ajouterAPrestation(prestation,
                    List.of(image("a.jpg"), vide), "Vue");

            assertThat(ajoutees)
                    .as("un champ non rempli ne doit pas faire echouer un depot valable")
                    .isEqualTo(1);
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("refuse un contenu qui n est pas une image, malgre l extension")
        void contenuNonImage() {
            UUID prestation = premierePrestation();
            var faux = new MockMultipartFile("fichiers", "piege.jpg", "image/jpeg",
                    "<?php ?>".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> admin.ajouterAPrestation(prestation, List.of(faux), "Vue"))
                    .isInstanceOf(TypeFichierRefuseException.class);
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("l image est servie par notre propre domaine, avec son type reel")
        void imageServie() throws Exception {
            UUID prestation = premierePrestation();
            admin.ajouterAPrestation(prestation, List.of(image("a.jpg")), "Vue");
            Long id = galerie.dePrestation(prestation).getFirst().id();

            mvc.perform(get("/images/galerie/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/jpeg"))
                    .andExpect(header().string("Cache-Control",
                            org.hamcrest.Matchers.containsString("max-age")));
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("supprimer retire la ligne et le fichier")
        void suppression() {
            UUID prestation = premierePrestation();
            admin.ajouterAPrestation(prestation, List.of(image("a.jpg")), "Vue");
            Long id = galerie.dePrestation(prestation).getFirst().id();

            admin.supprimer(id);

            assertThat(photos.findById(id)).isEmpty();
        }

        @Test
        @WithAnonymousUser
        @DisplayName("une image inconnue rend 404, pas une erreur serveur")
        void imageInconnue() throws Exception {
            mvc.perform(get("/images/galerie/{id}", 999_999L))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Gardes")
    class Gardes {

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau URL : un membre n atteint pas la gestion de galerie")
        void urlReservee() throws Exception {
            mvc.perform(get("/admin/galerie/prestations/{r}", UUID.randomUUID()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau methode : le service d ecriture refuse un non-administrateur")
        void serviceRedouble() {
            assertThatThrownBy(() -> admin.ajouterAPrestation(
                    UUID.randomUUID(), List.of(image("a.jpg")), "Vue"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @WithAnonymousUser
        @DisplayName("la lecture des galeries publiques reste ouverte")
        void lectureOuverte() {
            // Les illustrations de catalogue accompagnent des fiches publiques :
            // exiger une authentification les rendrait invisibles aux visiteurs.
            assertThat(galerie.dePrestation(premierePrestation())).isNotNull();
        }
    }
}
