package be.autoservplus.importcsv.service;

import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.importcsv.service.dto.RapportImport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Import CSV du catalogue de bout en bout (BL-2), sur un PostgreSQL reel.
 *
 * <p>Le point critique est le <b>tout ou rien</b> : il ne se verifie qu avec une vraie
 * transaction, et un test avec repository mocke ne prouverait rien. Une ligne invalide
 * placee <b>apres</b> des lignes valides doit annuler jusqu aux creations deja
 * faites.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Import CSV du catalogue (integration)")
class ImportCatalogueIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private ImportCatalogueService imports;
    @Autowired private PrestationRepository prestations;

    private static byte[] csv(String contenu) {
        return contenu.getBytes(StandardCharsets.UTF_8);
    }

    private String code() {
        return "IMP-" + COMPTEUR.getAndIncrement();
    }

    private static String enTete() {
        return "categorie;code;libelle;description;prix_htva;taux_tva;duree_minutes\r\n";
    }

    @Nested
    @DisplayName("Import nominal")
    class Nominal {

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("cree les prestations du fichier")
        void creation() {
            String code = code();
            RapportImport rapport = imports.importerPrestations(csv(enTete()
                    + "ENTRETIEN;" + code + ";Vidange importee;Desc;45,00;21,00;30\r\n"));

            assertThat(rapport.crees()).isEqualTo(1);
            assertThat(rapport.sansErreur()).isTrue();
            assertThat(prestations.findByCode(code)).isPresent();
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("rejouer le meme fichier met a jour au lieu de dupliquer")
        void rejeuMetAJour() {
            String code = code();
            String ligne = "ENTRETIEN;" + code + ";Vidange;Desc;45,00;21,00;30\r\n";
            imports.importerPrestations(csv(enTete() + ligne));

            RapportImport second = imports.importerPrestations(csv(enTete()
                    + "ENTRETIEN;" + code + ";Vidange revisee;Desc;50,00;21,00;30\r\n"));

            assertThat(second.crees()).isZero();
            assertThat(second.misAJour()).isEqualTo(1);
            assertThat(prestations.findByCode(code).orElseThrow().getPrixHtva())
                    .isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("accepte la virgule decimale du tableur belge")
        void virguleDecimale() {
            String code = code();
            imports.importerPrestations(csv(enTete()
                    + "ENTRETIEN;" + code + ";Test virgule;Desc;12,50;6,00;15\r\n"));

            assertThat(prestations.findByCode(code).orElseThrow().getPrixHtva())
                    .isEqualByComparingTo(new BigDecimal("12.50"));
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("l ordre des colonnes est libre, elles sont lues par leur nom")
        void ordreDesColonnesLibre() {
            String code = code();
            RapportImport rapport = imports.importerPrestations(csv(
                    "code;taux_tva;prix_htva;libelle;categorie;description;duree_minutes\r\n"
                            + code + ";21,00;33,00;Ordre inverse;ENTRETIEN;Desc;20\r\n"));

            assertThat(rapport.crees()).isEqualTo(1);
            assertThat(prestations.findByCode(code).orElseThrow().getPrixHtva())
                    .as("une inversion prix / taux serait invisible sans en-tete nommee")
                    .isEqualByComparingTo(new BigDecimal("33.00"));
        }
    }

    @Nested
    @DisplayName("Tout ou rien")
    class ToutOuRien {

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("une ligne invalide annule meme les creations qui la precedent")
        void annulationTotale() {
            String valide = code();
            String contenu = enTete()
                    + "ENTRETIEN;" + valide + ";Ligne valide;Desc;45,00;21,00;30\r\n"
                    + "ENTRETIEN;" + code() + ";Taux interdit;Desc;45,00;17,00;30\r\n";

            assertThatThrownBy(() -> imports.importerPrestations(csv(contenu)))
                    .isInstanceOf(ImportRefuseException.class);

            assertThat(prestations.findByCode(valide))
                    .as("un catalogue a moitie importe est pire qu un import refuse")
                    .isEmpty();
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("le rapport nomme la ligne du fichier et le motif")
        void rapportUtile() {
            String contenu = enTete()
                    + "ENTRETIEN;" + code() + ";Taux interdit;Desc;45,00;17,00;30\r\n";

            var erreur = assertThatThrownBy(() -> imports.importerPrestations(csv(contenu)))
                    .isInstanceOf(ImportRefuseException.class)
                    .extracting(e -> ((ImportRefuseException) e).rapport())
                    .extracting(RapportImport::erreurs);

            erreur.asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .hasSize(1)
                    .first()
                    .hasToString("LigneEnErreur[ligne=2, motif=Colonne « taux_tva » : "
                            + "taux « 17.00 » non admis en Belgique (0, 6, 12 ou 21).]");
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("toutes les erreurs sont listees, pas seulement la premiere")
        void toutesLesErreurs() {
            String contenu = enTete()
                    + "ENTRETIEN;" + code() + ";Sans prix;Desc;;21,00;30\r\n"
                    + "ENTRETIEN;" + code() + ";Taux interdit;Desc;45,00;17,00;30\r\n";

            assertThatThrownBy(() -> imports.importerPrestations(csv(contenu)))
                    .isInstanceOf(ImportRefuseException.class)
                    .satisfies(e -> assertThat(((ImportRefuseException) e).rapport().erreurs())
                            .as("le garage doit corriger son fichier en une fois")
                            .hasSize(2));
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("une en-tete incomplete refuse l import sans lire les donnees")
        void enteteIncomplete() {
            RapportImport rapport = imports.importerPrestations(csv(
                    "code;libelle\r\nVID;Vidange\r\n"));

            assertThat(rapport.sansErreur()).isFalse();
            assertThat(rapport.erreurs().getFirst().motif()).contains("Colonnes manquantes");
            assertThat(rapport.crees()).isZero();
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("un fichier vide est signale, pas ignore en silence")
        void fichierVide() {
            RapportImport rapport = imports.importerPrestations(csv(""));

            assertThat(rapport.sansErreur()).isFalse();
            assertThat(rapport.total()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Gardes")
    class Gardes {

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau URL : un membre n atteint pas l ecran d import")
        void urlReservee() throws Exception {
            mvc.perform(get("/admin/import")).andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
        @DisplayName("niveau methode : le service refuse un non-administrateur")
        void serviceRedouble() {
            assertThatThrownBy(() -> imports.importerPrestations(csv(enTete())))
                    .as("l import ecrit dans les prix du catalogue")
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @WithMockUser(username = "admin@autoservplus.be", roles = "ADMINISTRATEUR")
        @DisplayName("l ecran repond a l administrateur")
        void ecranAdministrateur() throws Exception {
            mvc.perform(get("/admin/import")).andExpect(status().isOk());
        }
    }
}
