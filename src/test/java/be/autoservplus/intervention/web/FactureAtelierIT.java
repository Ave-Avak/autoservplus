package be.autoservplus.intervention.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.domain.UnitePrestation;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Facture d'atelier émise à la clôture d'une intervention (A15, RM-17, Q-H-41, Q-H-42).
 *
 * <p>Sans transaction de classe : la facture d'une commande naît d'un listener après la
 * validation du paiement, comme en production. Les données de chaque test sont propres.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Facture d'atelier à la clôture (intégration)")
class FactureAtelierIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private InterventionService interventions;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PanierService paniers;
    @Autowired private CommandeService commandes;
    @Autowired private PrestatairePaiementFictif prestataire;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private be.autoservplus.rgpd.service.ExportDonneesService exportDonnees;

    private int n;
    private Categorie atelier;
    /** Compte réel : la modification d'un compte est imputée à son auteur. */
    private RequestPostProcessor admin;

    @BeforeEach
    void setUp() {
        n = COMPTEUR.getAndIncrement();
        atelier = categories.save(new Categorie("IT-FAT-" + n, "Atelier", TypeCategorie.SERVICE));
        Utilisateur administrateur = new Utilisateur("fat-admin-" + n + "@exemple.be", "$2a$12$h", "Garage",
                "Admin", TypeUtilisateur.ADMINISTRATEUR);
        administrateur.confirmerAdresseEmail();
        administrateur.setStatut(StatutUtilisateur.ACTIF);
        admin = user(utilisateurs.saveAndFlush(administrateur).getEmail()).roles("ADMINISTRATEUR");
    }

    @AfterEach
    void sansIdentite() {
        SecurityContextHolder.clearContext();
    }

    private <T> T enMembre(Utilisateur membre, Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                membre.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private <T> T enAdministrateur(Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "admin@garage.be", null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATEUR"))));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Utilisateur membre(boolean avecAdresse) {
        String email = "fat-" + n + "-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        Utilisateur membre = avecAdresse
                ? MembresDeTest.membre(email, "$2a$12$h", "Dupont", "Marie")
                : new Utilisateur(email, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        return utilisateurs.save(membre);
    }

    private Vehicule vehicule(Utilisateur membre) {
        return vehicules.save(new Vehicule(membre, "1-FA-" + COMPTEUR.getAndIncrement(), "VW", "Golf",
                Motorisation.DIESEL));
    }

    private Prestation prestation(String prixHtva, UnitePrestation unite) {
        return prestations.save(new Prestation(atelier, "IT-FAT-" + COMPTEUR.getAndIncrement(),
                "Prestation IT " + n, new BigDecimal(prixHtva), unite == UnitePrestation.HEURE ? 60 : 30, unite));
    }

    /** Entrée directe démarrée, devis large : les ajouts sont acceptés d'office (RM-15). */
    private UUID entreeDirecteEnCours(Vehicule vehicule) {
        UUID reference = jdbc.queryForObject("INSERT INTO intervention (numero, vehicule_id, statut, "
                        + "montant_devis_htva) VALUES (?, ?, 'PLANIFIEE', 1000) RETURNING reference",
                UUID.class, "INT-FAT-" + COMPTEUR.getAndIncrement(), vehicule.getId());
        enAdministrateur(() -> interventions.demarrer(reference));
        return reference;
    }

    private void ajouter(UUID intervention, Prestation prestation, String quantite) {
        enAdministrateur(() -> interventions.ajouterLigneMainOeuvre(intervention, prestation.getReference(),
                new BigDecimal(quantite)));
    }

    private String factureDe(UUID intervention, String colonne) {
        List<String> valeurs = jdbc.queryForList("SELECT f." + colonne + "::text FROM facture f JOIN intervention i "
                + "ON i.id = f.intervention_id WHERE i.reference = ?", String.class, intervention);
        return valeurs.isEmpty() ? null : valeurs.getFirst();
    }

    @Test
    @DisplayName("entrée directe : la clôture émet la facture de toutes les lignes, au suivi et en PDF")
    void factureDUneEntreeDirecte() throws Exception {
        Utilisateur marie = membre(true);
        UUID dossier = entreeDirecteEnCours(vehicule(marie));
        ajouter(dossier, prestation("65.00", UnitePrestation.HEURE), "1.5");
        ajouter(dossier, prestation("12.40", UnitePrestation.FORFAIT), "1");

        mvc.perform(post("/admin/interventions/{ref}/terminer", dossier).with(admin).with(csrf())
                        .locale(Locale.FRENCH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", containsString("Facture " + factureDe(dossier, "numero"))));

        // 1,5 h à 78,65 = 117,98 et 15,00 : 132,98 TVAC, base 109,90 par taux sur le document.
        assertThat(new BigDecimal(factureDe(dossier, "montant_tvac"))).isEqualByComparingTo("132.98");
        assertThat(new BigDecimal(factureDe(dossier, "montant_htva"))).isEqualByComparingTo("109.90");
        assertThat(LocalDate.parse(factureDe(dossier, "date_echeance"))).isNotNull();
        String referenceFacture = factureDe(dossier, "reference");

        mvc.perform(get("/mes-interventions/{ref}", dossier).with(user(marie.getEmail())).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Votre facture")))
                .andExpect(content().string(containsString("/factures/" + referenceFacture + "/pdf")));
        mvc.perform(get("/factures/{ref}/pdf", referenceFacture).with(user(marie.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/admin/factures/{ref}/pdf", referenceFacture).with(admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("une intervention annulée n'a jamais de facture")
    void annuleeSansFacture() throws Exception {
        UUID dossier = entreeDirecteEnCours(vehicule(membre(true)));
        ajouter(dossier, prestation("49.00", UnitePrestation.FORFAIT), "1");

        mvc.perform(post("/admin/interventions/{ref}/annuler", dossier).with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(factureDe(dossier, "numero")).isNull();
    }

    @Test
    @DisplayName("sans adresse du client : clôture refusée, lien vers la modification du compte, rien d'écrit")
    void sansAdresseRefusee() throws Exception {
        Utilisateur paul = membre(false);
        UUID dossier = entreeDirecteEnCours(vehicule(paul));
        ajouter(dossier, prestation("49.00", UnitePrestation.FORFAIT), "1");

        mvc.perform(get("/admin/interventions/{ref}", dossier).with(admin).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/admin/comptes/" + paul.getReference() + "/modification?intervention=" + dossier)));

        mvc.perform(post("/admin/interventions/{ref}/terminer", dossier).with(admin).with(csrf())
                        .locale(Locale.FRENCH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("compteACompleter", paul.getReference()))
                .andExpect(flash().attribute("erreur", containsString("adresse du client manque")));

        assertThat(jdbc.queryForObject("SELECT statut FROM intervention WHERE reference = ?", String.class, dossier))
                .isEqualTo("EN_COURS");
        assertThat(factureDe(dossier, "numero")).isNull();
    }

    @Test
    @DisplayName("la modification du compte revient à l'intervention par sa référence, jamais vers une adresse reçue")
    void retourSansRedirectionOuverte() throws Exception {
        Utilisateur paul = membre(false);
        UUID dossier = entreeDirecteEnCours(vehicule(paul));

        mvc.perform(get("/admin/comptes/{ref}/modification", paul.getReference()).with(admin)
                        .param("intervention", dossier.toString()).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + dossier + "\"")))
                .andExpect(content().string(containsString("/admin/interventions/" + dossier)));

        mvc.perform(modification(paul).param("intervention", "https://exemple.invalid/piege"))
                .andExpect(redirectedUrl("/admin/comptes/" + paul.getReference()));
        mvc.perform(modification(paul).param("intervention", "//exemple.invalid"))
                .andExpect(redirectedUrl("/admin/comptes/" + paul.getReference()));
        mvc.perform(modification(paul).param("intervention", dossier.toString()))
                .andExpect(redirectedUrl("/admin/interventions/" + dossier));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder modification(Utilisateur compte) {
        return post("/admin/comptes/{ref}/modification", compte.getReference()).with(admin).with(csrf())
                .param("prenom", "Marie").param("nom", "Dupont").param("rue", "Rue de la Loi")
                .param("numeroRue", "16").param("codePostal", "1000").param("localite", "Bruxelles")
                .param("pays", "Belgique").param("langue", "fr").param("motif", "Adresse donnée au comptoir");
    }

    @Test
    @DisplayName("née d'une commande payée : sans ajout, aucune facture complémentaire, et la confirmation le dit")
    void commandeSansAjout() throws Exception {
        Utilisateur marie = membre(true);
        Vehicule golf = vehicule(marie);
        UUID commande = commandePayee(marie);
        UUID dossier = enAdministrateur(() -> interventions.creerDepuisCommande(commande, golf.getReference()))
                .getReference();
        enAdministrateur(() -> interventions.demarrer(dossier));

        mvc.perform(post("/admin/interventions/{ref}/terminer", dossier).with(admin).with(csrf())
                        .locale(Locale.FRENCH))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", containsString("Aucune facture complémentaire")));

        assertThat(factureDe(dossier, "numero")).isNull();
    }

    @Test
    @DisplayName("née d'une commande payée : les ajouts acceptés seuls, avec renvoi à la facture de la commande")
    void commandeAvecAjout() throws Exception {
        Utilisateur marie = membre(true);
        Vehicule golf = vehicule(marie);
        UUID commande = commandePayee(marie);
        UUID dossier = enAdministrateur(() -> interventions.creerDepuisCommande(commande, golf.getReference()))
                .getReference();
        enAdministrateur(() -> interventions.demarrer(dossier));
        // Une heure à 1,00 HTVA reste sous le seuil RM-15 : l'ajout est accepté d'office.
        ajouter(dossier, prestation("1.00", UnitePrestation.HEURE), "1");

        mvc.perform(post("/admin/interventions/{ref}/terminer", dossier).with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(new BigDecimal(factureDe(dossier, "montant_tvac"))).isEqualByComparingTo("1.21");
        String factureCommande = jdbc.queryForObject("SELECT f.numero FROM facture f JOIN commande c "
                + "ON c.id = f.commande_id WHERE c.reference = ?", String.class, commande);
        String factureAtelier = factureDe(dossier, "reference");
        byte[] pdf = mvc.perform(get("/admin/factures/{ref}/pdf", factureAtelier).with(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String texte = new com.lowagie.text.pdf.parser.PdfTextExtractor(new com.lowagie.text.pdf.PdfReader(pdf))
                .getTextFromPage(1);
        assertThat(texte).contains(factureCommande);
    }

    @Test
    @DisplayName("la liste des factures du back-office et l'export comptable nomment l'intervention")
    void listeEtExport() throws Exception {
        UUID dossier = entreeDirecteEnCours(vehicule(membre(true)));
        ajouter(dossier, prestation("49.00", UnitePrestation.FORFAIT), "1");
        enAdministrateur(() -> interventions.terminer(dossier));
        String numeroIntervention = jdbc.queryForObject("SELECT numero FROM intervention WHERE reference = ?",
                String.class, dossier);

        mvc.perform(get("/admin/factures").with(admin).param("recherche", numeroIntervention))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/interventions/" + dossier)));
        LocalDate aujourdHui = LocalDate.now();
        mvc.perform(get("/admin/export/factures.csv").with(admin)
                        .param("depuis", aujourdHui.minusDays(1).toString())
                        .param("jusqua", aujourdHui.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Intervention")))
                .andExpect(content().string(containsString(numeroIntervention)));
    }

    /** Entrée directe terminée, une ligne à 49,00 HTVA : sa facture d'atelier est émise. */
    private UUID factureAtelierEmise(Utilisateur membre) {
        UUID dossier = entreeDirecteEnCours(vehicule(membre));
        ajouter(dossier, prestation("49.00", UnitePrestation.FORFAIT), "1");
        enAdministrateur(() -> interventions.terminer(dossier));
        return dossier;
    }

    private org.springframework.test.web.servlet.ResultActions annuler(UUID dossier, String precision) throws Exception {
        return mvc.perform(post("/admin/interventions/{ref}/facture/annulation", dossier).with(admin).with(csrf())
                .param("precision", precision).locale(Locale.FRENCH));
    }

    private String avoirDe(UUID dossier, String colonne) {
        List<String> valeurs = jdbc.queryForList("SELECT a." + colonne + "::text FROM avoir a JOIN facture f "
                + "ON f.id = a.facture_id JOIN intervention i ON i.id = f.intervention_id WHERE i.reference = ?",
                String.class, dossier);
        return valeurs.isEmpty() ? null : valeurs.getFirst();
    }

    @Nested
    @DisplayName("note de crédit d'une facture d'atelier (Q-H-50, Q-H-51, Q-H-52)")
    class NoteDeCredit {

        @Test
        @DisplayName("l'administrateur annule la facture : statut, liens vers le PDF, liste des avoirs")
        void annulation() throws Exception {
            Utilisateur marie = membre(true);
            UUID dossier = factureAtelierEmise(marie);

            annuler(dossier, "  Vidange facturée deux fois  ")
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("message", containsString("Note de crédit AV-")));

            assertThat(avoirDe(dossier, "motif")).isEqualTo("ANNULATION_ATELIER");
            assertThat(avoirDe(dossier, "precision")).isEqualTo("Vidange facturée deux fois");
            String avoir = avoirDe(dossier, "reference");
            String numero = avoirDe(dossier, "numero");

            mvc.perform(get("/admin/interventions/{ref}", dossier).with(admin).locale(Locale.FRENCH))
                    .andExpect(content().string(containsString("Annulée par la note de crédit " + numero)))
                    .andExpect(content().string(containsString("/admin/avoirs/" + avoir + "/pdf")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/facture/annulation"))));
            mvc.perform(get("/mes-interventions/{ref}", dossier).with(user(marie.getEmail())).locale(Locale.FRENCH))
                    .andExpect(content().string(containsString("Annulée par la note de crédit " + numero)))
                    .andExpect(content().string(containsString("/avoirs/" + avoir + "/pdf")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("au retrait de votre véhicule"))));

            byte[] pdf = mvc.perform(get("/avoirs/{ref}/pdf", avoir).with(user(marie.getEmail())))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            String texte = new com.lowagie.text.pdf.parser.PdfTextExtractor(new com.lowagie.text.pdf.PdfReader(pdf))
                    .getTextFromPage(1).replaceAll("\\s+", " ");
            String numeroIntervention = jdbc.queryForObject("SELECT numero FROM intervention WHERE reference = ?",
                    String.class, dossier);
            assertThat(texte).contains(numeroIntervention, "Vidange facturée deux fois",
                    "Annulation de la facture d'atelier");

            mvc.perform(get("/admin/avoirs").with(admin).param("recherche", numeroIntervention).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/admin/interventions/" + dossier)))
                    .andExpect(content().string(containsString("Annulation de la facture d&#39;atelier")))
                    .andExpect(content().string(containsString("Vidange facturée deux fois")));
        }

        @Test
        @DisplayName("précision blanche refusée et rendue au formulaire, seconde annulation refusée")
        void refus() throws Exception {
            UUID dossier = factureAtelierEmise(membre(true));

            annuler(dossier, "   ")
                    .andExpect(flash().attribute("erreur", containsString("précision de l'annulation est obligatoire")))
                    .andExpect(flash().attribute("precisionSaisie", "   "));
            assertThat(avoirDe(dossier, "numero")).isNull();

            annuler(dossier, "Erreur de quantité").andExpect(flash().attribute("message", containsString("AV-")));
            annuler(dossier, "Encore")
                    .andExpect(flash().attribute("erreur", containsString("déjà sa note de crédit")));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM avoir a JOIN facture f ON f.id = a.facture_id "
                    + "JOIN intervention i ON i.id = f.intervention_id WHERE i.reference = ?", Integer.class, dossier))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("la note de crédit d'une facture complémentaire laisse intacte la facture de la commande")
        void factureComplementaire() throws Exception {
            Utilisateur marie = membre(true);
            Vehicule golf = vehicule(marie);
            UUID commande = commandePayee(marie);
            UUID dossier = enAdministrateur(() -> interventions.creerDepuisCommande(commande, golf.getReference()))
                    .getReference();
            enAdministrateur(() -> interventions.demarrer(dossier));
            ajouter(dossier, prestation("1.00", UnitePrestation.HEURE), "1");
            enAdministrateur(() -> interventions.terminer(dossier));

            annuler(dossier, "Ajout facturé par erreur").andExpect(flash().attribute("message", containsString("AV-")));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM avoir a JOIN facture f ON f.id = a.facture_id "
                    + "JOIN commande c ON c.id = f.commande_id WHERE c.reference = ?", Integer.class, commande))
                    .isZero();
        }

        @Test
        @DisplayName("en base : précision exigée pour une annulation d'atelier, interdite sinon, immuable après émission")
        void contraintesEnBase() throws Exception {
            UUID dossier = factureAtelierEmise(membre(true));
            Long facture = jdbc.queryForObject("SELECT f.id FROM facture f JOIN intervention i "
                    + "ON i.id = f.intervention_id WHERE i.reference = ?", Long.class, dossier);
            String insertion = "INSERT INTO avoir (numero, facture_id, montant_htva, montant_tva, montant_tvac, "
                    + "motif, precision) VALUES (?, ?, 1.00, 0.00, 1.00, ?, ?)";

            assertThatThrownBy(() -> jdbc.update(insertion, "AV-IT-" + n + "-1", facture, "ANNULATION_ATELIER", null))
                    .hasMessageContaining("ck_avoir_precision");
            assertThatThrownBy(() -> jdbc.update(insertion, "AV-IT-" + n + "-2", facture, "ANNULATION_ATELIER", " \t "))
                    .hasMessageContaining("ck_avoir_precision");
            assertThatThrownBy(() -> jdbc.update(insertion, "AV-IT-" + n + "-3", facture, "RETRACTATION_F30", "Texte"))
                    .hasMessageContaining("ck_avoir_precision");

            annuler(dossier, "Erreur de quantité");
            assertThatThrownBy(() -> jdbc.update("UPDATE avoir SET precision = 'Réécrite' WHERE reference = ?::uuid",
                    avoirDe(dossier, "reference")))
                    .hasMessageContaining("immuable");
            assertThat(avoirDe(dossier, "precision")).isEqualTo("Erreur de quantité");
        }
    }

    private org.springframework.test.web.servlet.ResultActions encaisser(UUID dossier, String moyen, LocalDate date)
            throws Exception {
        return mvc.perform(post("/admin/interventions/{ref}/facture/encaissement", dossier).with(admin).with(csrf())
                .param("moyen", moyen).param("date", date.toString()).locale(Locale.FRENCH));
    }

    private org.springframework.test.web.servlet.ResultActions annulerReglement(UUID dossier, String motif)
            throws Exception {
        return mvc.perform(post("/admin/interventions/{ref}/facture/encaissement/annulation", dossier).with(admin)
                .with(csrf()).param("motif", motif).locale(Locale.FRENCH));
    }

    /** Jour courant au fuseau du garage, celui que le domaine compare à la date saisie. */
    private static LocalDate aujourdHui() {
        return LocalDate.now(java.time.ZoneId.of("Europe/Brussels"));
    }

    private int encaissementsDe(UUID dossier, boolean annules) {
        return jdbc.queryForObject("SELECT count(*) FROM encaissement e JOIN facture f ON f.id = e.facture_id "
                + "JOIN intervention i ON i.id = f.intervention_id WHERE i.reference = ? AND (e.annule_le IS NOT NULL) = ?",
                Integer.class, dossier, annules);
    }

    /** Entrée directe terminée sur une seule ligne au prix donné, devis assez large pour l'accepter (RM-15). */
    private UUID factureAtelierDe(Utilisateur membre, String prixHtva) {
        UUID dossier = entreeDirecteEnCours(vehicule(membre));
        jdbc.update("UPDATE intervention SET montant_devis_htva = 10000 WHERE reference = ?", dossier);
        ajouter(dossier, prestation(prixHtva, UnitePrestation.FORFAIT), "1");
        enAdministrateur(() -> interventions.terminer(dossier));
        return dossier;
    }

    @Nested
    @DisplayName("règlement au comptoir d'une facture d'atelier (RM-20, Q-H-43 à Q-H-46)")
    class Reglement {

        @Test
        @DisplayName("réglée au terminal : statut au back-office et au suivi, formulaire retiré, journal d'audit")
        void reglement() throws Exception {
            Utilisateur marie = membre(true);
            UUID dossier = factureAtelierEmise(marie);
            String facture = factureDe(dossier, "numero");

            encaisser(dossier, "TERMINAL", aujourdHui())
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("message", "Facture " + facture + " réglée."));

            assertThat(jdbc.queryForObject("SELECT montant::text FROM encaissement e JOIN facture f ON f.id = e.facture_id "
                    + "WHERE f.numero = ?", String.class, facture)).isEqualTo("59.29");
            String date = aujourdHui().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            mvc.perform(get("/admin/interventions/{ref}", dossier).with(admin).locale(Locale.FRENCH))
                    .andExpect(content().string(containsString("Réglée le " + date + " (Terminal de paiement)")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/facture/encaissement\""))))
                    .andExpect(content().string(containsString("/facture/encaissement/annulation")));
            mvc.perform(get("/mes-interventions/{ref}", dossier).with(user(marie.getEmail())).locale(Locale.FRENCH))
                    .andExpect(content().string(containsString("Réglée le " + date)))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("au retrait de votre véhicule"))));
            mvc.perform(get("/admin/journal").with(admin).param("type", "ENCAISSEMENT").locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(facture)))
                    .andExpect(content().string(containsString("Règlement de 59,29")));
        }

        @Test
        @DisplayName("annulé avec motif, puis réglé à nouveau : deux lignes, une annulée, et le journal les montre")
        void annulationPuisNouveauReglement() throws Exception {
            UUID dossier = factureAtelierEmise(membre(true));
            encaisser(dossier, "TERMINAL", aujourdHui());

            annulerReglement(dossier, "  ")
                    .andExpect(flash().attribute("erreur", "Le motif de l'annulation est obligatoire."))
                    .andExpect(flash().attribute("motifSaisi", "  "));
            annulerReglement(dossier, "Saisi sur la mauvaise facture")
                    .andExpect(flash().attribute("message", containsString("de nouveau à régler")));
            encaisser(dossier, "ESPECES", aujourdHui())
                    .andExpect(flash().attribute("message", containsString("réglée")));

            assertThat(encaissementsDe(dossier, true)).isEqualTo(1);
            assertThat(encaissementsDe(dossier, false)).isEqualTo(1);
            encaisser(dossier, "VIREMENT", aujourdHui())
                    .andExpect(flash().attribute("erreur", "Cette facture est déjà réglée."));
            mvc.perform(get("/admin/journal").with(admin).param("type", "ENCAISSEMENT").locale(Locale.FRENCH))
                    .andExpect(content().string(containsString("Saisi sur la mauvaise facture")));
        }

        @Test
        @DisplayName("dates hors bornes, champs manquants : refusés, rien d'écrit")
        void datesEtChamps() throws Exception {
            UUID dossier = factureAtelierEmise(membre(true));

            encaisser(dossier, "TERMINAL", aujourdHui().plusDays(1))
                    .andExpect(flash().attribute("erreur", "La date du règlement ne peut pas être future."));
            encaisser(dossier, "TERMINAL", aujourdHui().minusDays(1))
                    .andExpect(flash().attribute("erreur", containsString("précéder l'émission")));
            mvc.perform(post("/admin/interventions/{ref}/facture/encaissement", dossier).with(admin).with(csrf())
                            .param("moyen", "TERMINAL").locale(Locale.FRENCH))
                    .andExpect(flash().attribute("erreur", "Choisissez le moyen et la date du règlement."));
            assertThat(encaissementsDe(dossier, false)).isZero();
        }

        @Test
        @DisplayName("espèces : 3000,00 € TVAC admis, 3000,01 € refusé avec le plafond dans la langue de l'écran")
        void plafondDesEspeces() throws Exception {
            // 2479,34 HTVA donne 3000,00 TVAC, 2479,35 HTVA donne 3000,01 (prix unitaire TVAC arrondi au cent).
            UUID aLaLimite = factureAtelierDe(membre(true), "2479.34");
            UUID auDela = factureAtelierDe(membre(true), "2479.35");
            assertThat(factureDe(aLaLimite, "montant_tvac")).isEqualTo("3000.00");
            assertThat(factureDe(auDela, "montant_tvac")).isEqualTo("3000.01");

            encaisser(aLaLimite, "ESPECES", aujourdHui()).andExpect(flash().attribute("message", containsString("réglée")));
            encaisser(auDela, "ESPECES", aujourdHui())
                    .andExpect(flash().attribute("erreur", containsString("ne se règle pas en espèces")))
                    .andExpect(flash().attribute("erreur", containsString("000,00 € TVAC")));
            encaisser(auDela, "TERMINAL", aujourdHui()).andExpect(flash().attribute("message", containsString("réglée")));
        }

        @Test
        @DisplayName("facture à 0 € sans formulaire, facture annulée qui ne se règle plus, rappel du remboursement")
        void rienAReglerEtFactureAnnulee() throws Exception {
            UUID gratuite = factureAtelierDe(membre(true), "0.00");
            mvc.perform(get("/admin/interventions/{ref}", gratuite).with(admin).locale(Locale.FRENCH))
                    .andExpect(content().string(containsString("Rien à régler")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/facture/encaissement\""))));
            encaisser(gratuite, "ESPECES", aujourdHui())
                    .andExpect(flash().attribute("erreur", "Une facture à 0 € n'a rien à régler."));

            UUID annulee = factureAtelierEmise(membre(true));
            annuler(annulee, "Erreur de prestation");
            encaisser(annulee, "TERMINAL", aujourdHui())
                    .andExpect(flash().attribute("erreur", containsString("ne se règle plus")));

            UUID regleePuisAnnulee = factureAtelierEmise(membre(true));
            encaisser(regleePuisAnnulee, "TERMINAL", aujourdHui());
            annuler(regleePuisAnnulee, "Prestation non réalisée").andExpect(flash().attribute("message", containsString("AV-")));
            mvc.perform(get("/admin/interventions/{ref}", regleePuisAnnulee).with(admin).locale(Locale.FRENCH))
                    .andExpect(content().string(containsString("puis annulée : le remboursement au client se fait hors de l&#39;application")));
        }

        @Test
        @DisplayName("l'export F22 du membre restitue ses règlements, annulés compris, sans l'auteur")
        void exportDesDonnees() throws Exception {
            Utilisateur marie = membre(true);
            UUID dossier = factureAtelierEmise(marie);
            encaisser(dossier, "TERMINAL", aujourdHui());
            annulerReglement(dossier, "Saisi deux fois");
            encaisser(dossier, "VIREMENT", aujourdHui());

            var factures = enMembre(marie, () -> exportDonnees.assembler(marie.getEmail())).donneesPersonnelles().factures();

            assertThat(factures).singleElement().satisfies(f -> {
                assertThat(f.numeroIntervention()).isNotNull();
                assertThat(f.encaissements()).extracting(e -> e.moyen()).containsExactly("TERMINAL", "VIREMENT");
                assertThat(f.encaissements().getFirst().motifAnnulation()).isEqualTo("Saisi deux fois");
                assertThat(f.encaissements().getLast().annuleLe()).isNull();
            });
        }

        @Test
        @DisplayName("en base : un seul règlement non annulé, immuable, jamais supprimé, annulation complète ou absente")
        void contraintesEnBase() throws Exception {
            UUID dossier = factureAtelierEmise(membre(true));
            encaisser(dossier, "TERMINAL", aujourdHui());
            Long facture = jdbc.queryForObject("SELECT f.id FROM facture f JOIN intervention i "
                    + "ON i.id = f.intervention_id WHERE i.reference = ?", Long.class, dossier);

            assertThatThrownBy(() -> jdbc.update("INSERT INTO encaissement (facture_id, moyen, montant, date_encaissement) "
                    + "VALUES (?, 'ESPECES', 59.29, CURRENT_DATE)", facture))
                    .hasMessageContaining("uq_encaissement_facture_actif");
            assertThatThrownBy(() -> jdbc.update("UPDATE encaissement SET montant = 1.00 WHERE facture_id = ?", facture))
                    .hasMessageContaining("immuable");
            assertThatThrownBy(() -> jdbc.update("UPDATE encaissement SET annule_le = now() WHERE facture_id = ?", facture))
                    .hasMessageContaining("ck_encaissement_annulation");

            annulerReglement(dossier, "Erreur");
            assertThatThrownBy(() -> jdbc.update("UPDATE encaissement SET annule_le = NULL, annule_par = NULL, "
                    + "motif_annulation = NULL WHERE facture_id = ?", facture))
                    .hasMessageContaining("immuable");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM encaissement WHERE facture_id = ?", facture))
                    .hasMessageContaining("immuable");
            assertThat(encaissementsDe(dossier, true)).isEqualTo(1);
        }
    }

    /** Commande d'une prestation payée par la vraie chaîne : la facture de commande naît du listener. */
    private UUID commandePayee(Utilisateur membre) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                membre.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
        UUID reference;
        try {
            paniers.ajouterService(membre.getEmail(), prestation("49.00", UnitePrestation.FORFAIT).getReference(), 1);
            reference = commandes.passerCommande(membre.getEmail(), true, false, "127.0.0.1").reference();
        } finally {
            SecurityContextHolder.clearContext();
        }
        String redirection = mvc.perform(post("/commande/{ref}/payer", reference)
                        .with(user(membre.getEmail())).with(csrf()))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = redirection.substring(redirection.lastIndexOf('/') + 1);
        prestataire.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        mvc.perform(post("/webhooks/paiement").with(anonymous()).param("id", referencePrestataire))
                .andExpect(status().isOk());
        return reference;
    }
}
