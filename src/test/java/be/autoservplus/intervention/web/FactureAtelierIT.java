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
