package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.domain.UnitePrestation;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.importcsv.service.ImportCatalogueService;
import be.autoservplus.importcsv.service.ImportRefuseException;
import be.autoservplus.importcsv.service.dto.RapportImport;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prestation à l'heure (V72, Q-H-36) : unité choisie à la création et jamais modifiée, durée
 * d'une heure, ni réservation, ni panier, ni devis, taux « / h » sur la fiche publique.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Prestation à l'heure (intégration)")
class PrestationALHeureIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private ImportCatalogueService imports;
    @Autowired private JdbcTemplate jdbc;

    private String suffixe;
    private Categorie atelier;

    @BeforeEach
    void setUp() {
        suffixe = "PH" + COMPTEUR.getAndIncrement();
        atelier = categories.save(new Categorie("IT-" + suffixe, "Atelier " + suffixe, TypeCategorie.SERVICE));
    }

    @AfterEach
    void langueParDefaut() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Prestation prestation(String code, UnitePrestation unite) {
        int duree = unite == UnitePrestation.HEURE ? 60 : 30;
        return prestations.saveAndFlush(new Prestation(atelier, code, "Libellé " + code,
                new BigDecimal("65.00"), duree, unite));
    }

    private String uniteEnBase(String code) {
        return jdbc.queryForObject("SELECT unite FROM service WHERE code = ?", String.class, code);
    }

    private byte[] csv(String... lignes) {
        return String.join("\n", lignes).getBytes(StandardCharsets.UTF_8);
    }

    private String ligneCsv(String code, String duree, String unite) {
        return "IT-" + suffixe + ";" + code + ";Libellé " + code + ";;65.00;21.00;" + duree + ";" + unite;
    }

    private static final String ENTETE = "categorie;code;libelle;description;prix_htva;taux_tva;duree_minutes;unite";

    @Nested
    @DisplayName("Formulaire du back-office")
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    class Formulaire {

        @Test
        @DisplayName("création d'une prestation à l'heure d'une durée d'une heure")
        void creation() throws Exception {
            String code = suffixe + "-MO";
            mvc.perform(post("/admin/catalogue/prestations").with(csrf())
                            .param("codeCategorie", "IT-" + suffixe).param("code", code)
                            .param("libelle", "Main-d'œuvre " + suffixe).param("prixHtva", "65.00")
                            .param("tauxTva", "21.00").param("dureeMinutes", "60").param("unite", "HEURE"))
                    .andExpect(status().is3xxRedirection());

            assertThat(uniteEnBase(code)).isEqualTo("HEURE");
        }

        @Test
        @DisplayName("une prestation à l'heure d'une autre durée est refusée sous le champ durée")
        void dureeRefusee() throws Exception {
            mvc.perform(post("/admin/catalogue/prestations").with(csrf())
                            .param("codeCategorie", "IT-" + suffixe).param("code", suffixe + "-MO")
                            .param("libelle", "Main-d'œuvre " + suffixe).param("prixHtva", "65.00")
                            .param("tauxTva", "21.00").param("dureeMinutes", "30").param("unite", "HEURE"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrorCode("formulaire", "dureeMinutes",
                            "admin.catalogue.erreur.duree-heure"));
        }

        @Test
        @DisplayName("la modification affiche l'unité en lecture seule et refuse de la changer")
        void modificationRefusee() throws Exception {
            Prestation vidange = prestation(suffixe + "-VID", UnitePrestation.FORFAIT);

            mvc.perform(get("/admin/catalogue/prestations/{ref}/modifier", vidange.getReference())
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Unité : Au forfait")))
                    .andExpect(content().string(not(containsString("<select id=\"unite\""))));

            mvc.perform(post("/admin/catalogue/prestations/{ref}/modifier", vidange.getReference()).with(csrf())
                            .param("codeCategorie", "IT-" + suffixe).param("code", vidange.getCode())
                            .param("libelle", vidange.getLibelle()).param("prixHtva", "65.00")
                            .param("tauxTva", "21.00").param("dureeMinutes", "60").param("unite", "HEURE"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeHasFieldErrorCode("formulaire", "unite",
                            "admin.catalogue.erreur.unite-fixee"));

            assertThat(uniteEnBase(vidange.getCode())).isEqualTo("FORFAIT");
        }
    }

    @Nested
    @DisplayName("Import CSV")
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    class Import {

        @Test
        @DisplayName("une prestation nouvelle reçoit l'unité de la colonne")
        void nouvelleALHeure() {
            RapportImport rapport = imports.importerPrestations(csv(ENTETE, ligneCsv(suffixe + "-MO", "60", "HEURE")));

            assertThat(rapport.crees()).isEqualTo(1);
            assertThat(uniteEnBase(suffixe + "-MO")).isEqualTo("HEURE");
        }

        @Test
        @DisplayName("une unité égale à celle en place est acceptée")
        void memeUniteAcceptee() {
            Prestation vidange = prestation(suffixe + "-VID", UnitePrestation.FORFAIT);

            RapportImport rapport = imports.importerPrestations(csv(ENTETE, ligneCsv(vidange.getCode(), "30", "FORFAIT")));

            assertThat(rapport.misAJour()).isEqualTo(1);
        }

        @Test
        @DisplayName("une unité différente est refusée avec le numéro de ligne, en néerlandais à l'écran néerlandais")
        void changementRefuse() {
            Prestation vidange = prestation(suffixe + "-VID", UnitePrestation.FORFAIT);
            LocaleContextHolder.setLocale(Locale.forLanguageTag("nl"));

            ImportRefuseException refus = catchThrowableOfType(ImportRefuseException.class,
                    () -> imports.importerPrestations(csv(ENTETE, ligneCsv(vidange.getCode(), "60", "HEURE"))));

            assertThat(refus.rapport().erreurs()).singleElement().satisfies(erreur -> {
                assertThat(erreur.ligne()).isEqualTo(2);
                assertThat(erreur.motif()).startsWith("De eenheid");
            });
        }

        @Test
        @DisplayName("une prestation à l'heure d'une autre durée est refusée, en français par défaut")
        void dureeRefusee() {
            ImportRefuseException refus = catchThrowableOfType(ImportRefuseException.class,
                    () -> imports.importerPrestations(csv(ENTETE, ligneCsv(suffixe + "-MO", "30", "HEURE"))));

            assertThat(refus.rapport().erreurs()).singleElement().satisfies(erreur -> {
                assertThat(erreur.ligne()).isEqualTo(2);
                assertThat(erreur.motif()).contains("60 minutes");
            });
        }
    }

    @Nested
    @DisplayName("Vente et réservation")
    class VenteEtReservation {

        @Test
        @DisplayName("la fiche publique donne le taux « / h » sans bouton de réservation ni de panier")
        void fichePublique() throws Exception {
            Prestation mainDOeuvre = prestation(suffixe + "-MO", UnitePrestation.HEURE);

            mvc.perform(get("/services/{ref}", mainDOeuvre.getReference()).locale(Locale.FRENCH)
                            .with(user("membre-fiche@exemple.be").roles("MEMBRE")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/ h")))
                    .andExpect(content().string(containsString("Taux horaire appliqué par")))
                    .andExpect(content().string(not(containsString("/mes-rendez-vous/nouveau?prestation="))))
                    .andExpect(content().string(not(containsString("/panier/ajouter-service"))));
        }

        @Test
        @DisplayName("la liste publique annonce le taux au-dessus de la grille, sans carte pour lui")
        void listePublique() throws Exception {
            Prestation mainDOeuvre = prestation(suffixe + "-MOL", UnitePrestation.HEURE);
            Prestation vidange = prestation(suffixe + "-VIDL", UnitePrestation.FORFAIT);

            String page = mvc.perform(get("/services").locale(Locale.FRENCH))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            Document liste = Jsoup.parse(page);
            assertThat(liste.select(".carte-article a[href$=" + mainDOeuvre.getReference() + "]")).isEmpty();
            assertThat(liste.select(".carte-article a[href$=" + vidange.getReference() + "]")).hasSize(1);
            assertThat(liste.select(".catalogue__taux").text())
                    .contains(mainDOeuvre.getLibelle() + " : 78,65 € TVAC de l'heure, soit 65,00 € HTVA");
        }

        @Test
        @DisplayName("le formulaire de rendez-vous ne propose pas la prestation à l'heure")
        void rendezVous() throws Exception {
            String email = "membre-rdv-" + suffixe.toLowerCase(Locale.ROOT) + "@exemple.be";
            var membre = utilisateurs.save(MembresDeTest.membre(email, "$2a$12$h", "Dupont", "Marie"));
            // Le formulaire ne propose les prestations qu'à un membre qui a un véhicule.
            vehicules.save(new Vehicule(membre, "1-PR-" + COMPTEUR.getAndIncrement(), "VW", "Golf",
                    Motorisation.DIESEL));
            Prestation mainDOeuvre = prestation(suffixe + "-MO", UnitePrestation.HEURE);
            Prestation vidange = prestation(suffixe + "-VID", UnitePrestation.FORFAIT);

            mvc.perform(get("/mes-rendez-vous/nouveau").with(user(email).roles("MEMBRE")).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(vidange.getLibelle())))
                    .andExpect(content().string(not(containsString(mainDOeuvre.getLibelle()))));
        }

        @Test
        @DisplayName("le panier refuse la prestation à l'heure, aucun devis ne peut donc en naître")
        void panierEtDevis() throws Exception {
            String email = "membre-panier-" + suffixe.toLowerCase(Locale.ROOT) + "@exemple.be";
            var membre = utilisateurs.save(MembresDeTest.membre(email, "$2a$12$h", "Dupont", "Marie"));
            Vehicule golf = vehicules.save(new Vehicule(membre, "1-PH-" + COMPTEUR.getAndIncrement(), "VW", "Golf",
                    Motorisation.DIESEL));
            Prestation mainDOeuvre = prestation(suffixe + "-MO", UnitePrestation.HEURE);

            mvc.perform(post("/panier/ajouter-service").with(user(email).roles("MEMBRE")).with(csrf())
                            .locale(Locale.FRENCH)
                            .param("reference", mainDOeuvre.getReference().toString()).param("quantite", "1"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("erreur", containsString("se facture à l'heure")));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ligne_panier l JOIN panier p ON p.id = l.panier_id "
                    + "JOIN utilisateur u ON u.id = p.membre_id WHERE u.email = ?", Integer.class, email)).isZero();

            mvc.perform(post("/mes-devis").with(user(email).roles("MEMBRE")).with(csrf())
                            .param("vehicule", golf.getReference().toString()))
                    .andExpect(status().is3xxRedirection());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM devis d JOIN utilisateur u ON u.id = d.membre_id "
                    + "WHERE u.email = ?", Integer.class, email)).isZero();
        }

        @Test
        @DisplayName("l'API publique donne l'unité à côté de la durée")
        void apiPublique() throws Exception {
            Prestation mainDOeuvre = prestation(suffixe + "-MO", UnitePrestation.HEURE);

            String json = mvc.perform(get("/api/v1/prestations").param("taille", "100"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(json).contains("\"code\":\"" + mainDOeuvre.getCode() + "\"");
            assertThat(json).containsPattern("\"code\":\"" + mainDOeuvre.getCode()
                    + "\"[^}]*\"dureeMinutes\":60,\"unite\":\"HEURE\"");
        }
    }
}
