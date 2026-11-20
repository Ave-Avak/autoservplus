package be.autoservplus.vente.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Commandes, factures et avoirs au back-office, sur une commande réellement payée.
 *
 * <p>Sans {@code @Transactional} de classe : la facture naît d'un écouteur exécuté après
 * la validation du paiement. Les données restent dans le conteneur, d'où des adresses
 * uniques par test et des recherches qui les visent.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Ventes au back-office (intégration)")
class AdminVentesIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test";
    private static final String ADMIN = "ventes-admin@exemple.be";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private FactureService factures;
    @Autowired private PrestatairePaiementFictif prestataireFictif;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private TransactionTemplate transactions;
    @Autowired private be.autoservplus.vente.repository.CommandeRepository commandes;

    private Utilisateur membre() {
        String email = "ventes-membre-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        return transactions.execute(statut -> {
            Utilisateur nouveau = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                    "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            nouveau.confirmerAdresseEmail();
            nouveau.setStatut(StatutUtilisateur.ACTIF);
            return utilisateurs.saveAndFlush(nouveau);
        });
    }

    /** Panier, commande, paiement confirmé : la commande ressort PAYEE et facturée. */
    private UUID commandePayee(Utilisateur acheteur) throws Exception {
        Categorie categorie = transactions.execute(statut -> categories.findByCode("IT-VEN-CAT")
                .orElseGet(() -> categories.save(new Categorie("IT-VEN-CAT", "Pieces", TypeCategorie.PIECE))));
        Piece piece = transactions.execute(statut -> {
            Piece nouvelle = new Piece(categorie, "IT-VEN-" + COMPTEUR.getAndIncrement(),
                    "Filtre a huile", new BigDecimal("19.99"));
            nouvelle.setQuantiteStock(50);
            return pieces.save(nouvelle);
        });
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                acheteur.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
        try {
            panierService.ajouterPiece(acheteur.getEmail(), piece.getReference(), 2);
        } finally {
            SecurityContextHolder.clearContext();
        }
        String redirection = mvc.perform(post("/commande").with(user(acheteur.getEmail()))
                        .with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID reference = UUID.fromString(redirection.replace("/commande/", "").replace("/confirmation", ""));
        String paiement = mvc.perform(post("/commande/{ref}/payer", reference)
                        .with(user(acheteur.getEmail())).with(csrf()))
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = paiement.substring(paiement.lastIndexOf('/') + 1);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        mvc.perform(post("/webhooks/paiement").with(anonymous()).param("id", referencePrestataire))
                .andExpect(status().isOk());
        return reference;
    }

    private Facture factureDe(UUID commande) {
        return transactions.execute(statut -> factures.factureDe(commande).orElseThrow());
    }

    @Test
    @DisplayName("ouverture sans paramètre : 200, filtre des trente derniers jours")
    void ouvertureParDefaut() throws Exception {
        for (String ecran : List.of("/admin/commandes", "/admin/factures", "/admin/avoirs")) {
            mvc.perform(get(ecran).with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("type=\"date\"")));
        }
    }

    @Test
    @DisplayName("aucun résultat sur une période saisie : message qui nomme la période")
    void listeVide() throws Exception {
        mvc.perform(get("/admin/commandes").param("du", "2000-01-01").param("au", "2000-01-31")
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aucune commande du 01/01/2000 au 31/01/2000")));
    }

    @Test
    @DisplayName("recherche sans date : tout l'historique, une commande ancienne se trouve par son numéro")
    void rechercheSansDate() throws Exception {
        Utilisateur marie = membre();
        String numero = "ANC-" + UUID.randomUUID().toString().substring(0, 12);
        transactions.executeWithoutResult(statut -> commandes.save(new be.autoservplus.vente.domain.Commande(
                numero, utilisateurs.findById(marie.getId()).orElseThrow(), new BigDecimal("10.00"),
                new BigDecimal("2.10"), new BigDecimal("12.10"),
                java.time.Instant.now().minus(90, java.time.temporal.ChronoUnit.DAYS))));

        mvc.perform(get("/admin/commandes").param("recherche", numero)
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(numero)));

        String du = java.time.LocalDate.now().minusDays(29).toString();
        mvc.perform(get("/admin/commandes").param("recherche", numero).param("du", du)
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(">" + numero + "<"))));

        mvc.perform(get("/admin/commandes").param("recherche", "aucune-commande-" + UUID.randomUUID())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(content().string(containsString("Aucune commande ne correspond")));
    }

    @Test
    @DisplayName("période inversée : message, aucune ligne")
    void periodeInversee() throws Exception {
        mvc.perform(get("/admin/factures").param("du", "2026-02-01").param("au", "2026-01-01")
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La date de début suit la date de fin")));
    }

    @Test
    @DisplayName("une commande payée : liste, détail, facture, téléchargement et fiche du membre")
    void parcoursComplet() throws Exception {
        Utilisateur marie = membre();
        UUID commande = commandePayee(marie);
        Facture facture = factureDe(commande);

        mvc.perform(get("/admin/commandes").param("recherche", marie.getEmail())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(content().string(containsString("/admin/commandes/" + commande)));

        mvc.perform(get("/admin/commandes/{ref}", commande)
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Filtre a huile")))
                .andExpect(content().string(containsString("/admin/factures/" + facture.getReference() + "/pdf")));

        mvc.perform(get("/admin/factures").param("recherche", facture.getNumero())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(content().string(containsString(facture.getNumero())));

        mvc.perform(get("/admin/factures/{ref}/pdf", facture.getReference())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mvc.perform(get("/admin/comptes/{ref}", marie.getReference())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(content().string(containsString("/admin/commandes/" + commande)));
    }

    @Test
    @DisplayName("un membre n'atteint ni la liste ni le document d'un autre par le back-office")
    void refuseAuMembre() throws Exception {
        Utilisateur marie = membre();
        UUID commande = commandePayee(marie);
        Facture facture = factureDe(commande);
        Utilisateur jean = membre();

        mvc.perform(get("/admin/commandes").with(user(jean.getEmail()).roles("MEMBRE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/factures/{ref}/pdf", facture.getReference())
                        .with(user(jean.getEmail()).roles("MEMBRE")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("%PDF"))));
    }
}
