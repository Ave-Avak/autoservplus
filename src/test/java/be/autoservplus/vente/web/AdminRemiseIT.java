package be.autoservplus.vente.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.retractation.service.RetractationService;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Date de remise des pièces saisie au back-office, et délai de rétractation qui en
 * découle (CDE, art. VI.47, § 2, 2°).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Date de remise et délai de rétractation d'un bien (intégration)")
class AdminRemiseIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String ADMIN = "remise-admin@exemple.be";
    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private PrestatairePaiementFictif prestataireFictif;
    @Autowired private RetractationService retractations;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    private Utilisateur membre() {
        String email = "remise-membre-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        return transactions.execute(statut -> {
            Utilisateur nouveau = new Utilisateur(email, encodeur.encode("phrase de passe du test"),
                    "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            nouveau.confirmerAdresseEmail();
            nouveau.setStatut(StatutUtilisateur.ACTIF);
            return utilisateurs.saveAndFlush(nouveau);
        });
    }

    private <T> T en(String email, Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UUID commandePayee(Utilisateur acheteur) throws Exception {
        Categorie categorie = transactions.execute(statut -> categories.findByCode("IT-REM-CAT")
                .orElseGet(() -> categories.save(new Categorie("IT-REM-CAT", "Pieces", TypeCategorie.PIECE))));
        Piece piece = transactions.execute(statut -> {
            Piece nouvelle = new Piece(categorie, "IT-REM-" + COMPTEUR.getAndIncrement(),
                    "Ampoule H7", new BigDecimal("10.00"));
            nouvelle.setQuantiteStock(50);
            return pieces.save(nouvelle);
        });
        en(acheteur.getEmail(), () -> {
            panierService.ajouterPiece(acheteur.getEmail(), piece.getReference(), 1);
            return null;
        });
        String redirection = mvc.perform(post("/commande").with(user(acheteur.getEmail()))
                        .with(csrf()).param("cgv", "true"))
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

    /** L'auteur d'une saisie doit exister en base : la trace porte une clé vers son compte. */
    private void administrateurExiste() {
        if (utilisateurs.findByEmail(ADMIN).isEmpty()) {
            transactions.executeWithoutResult(statut -> {
                Utilisateur admin = new Utilisateur(ADMIN, encodeur.encode("phrase de passe du test"),
                        "Garage", "Chef", TypeUtilisateur.ADMINISTRATEUR);
                admin.setStatut(StatutUtilisateur.ACTIF);
                utilisateurs.saveAndFlush(admin);
            });
        }
    }

    private org.springframework.test.web.servlet.ResultActions saisir(UUID commande, LocalDate jour)
            throws Exception {
        administrateurExiste();
        return mvc.perform(post("/admin/commandes/{ref}/remise", commande)
                .with(user(ADMIN).roles("ADMINISTRATEUR")).with(csrf()).locale(Locale.FRENCH)
                .param("dateRemise", jour.toString()));
    }

    private LocalDate dateRemise(UUID commande) {
        return jdbc.queryForObject("SELECT date_remise FROM commande WHERE reference = ?",
                LocalDate.class, commande);
    }

    @Test
    @DisplayName("date du jour acceptée, date future et date antérieure au paiement refusées")
    void controlesDeLaDate() throws Exception {
        UUID commande = commandePayee(membre());
        LocalDate aujourdhui = LocalDate.now(BRUXELLES);

        saisir(commande, aujourdhui.plusDays(1)).andExpect(flash().attributeExists("erreur"));
        saisir(commande, aujourdhui.minusDays(1)).andExpect(flash().attributeExists("erreur"));
        assertThat(dateRemise(commande)).isNull();

        saisir(commande, aujourdhui).andExpect(flash().attributeExists("message"));
        assertThat(dateRemise(commande)).isEqualTo(aujourdhui);

        // Trace : une ligne avec l'auteur et l'ancienne valeur (aucune), puis rien de plus
        // pour une date renvoyée inchangée.
        saisir(commande, aujourdhui).andExpect(flash().attributeExists("message"));
        List<java.util.Map<String, Object>> traces = jdbc.queryForList("""
                SELECT h.champ, h.valeur_avant, h.valeur_apres, u.email
                FROM historique_modification_commande h
                JOIN commande c ON c.id = h.commande_id
                JOIN utilisateur u ON u.id = h.auteur_id
                WHERE c.reference = ?
                """, commande);
        assertThat(traces).hasSize(1);
        assertThat(traces.get(0)).containsEntry("champ", "DATE_REMISE")
                .containsEntry("valeur_apres", aujourdhui.toString())
                .containsEntry("email", ADMIN);
        assertThat(traces.get(0).get("valeur_avant")).isNull();
    }

    @Test
    @DisplayName("une demande de rétractation existe : la date de remise ne se modifie plus")
    void verrouApresDemande() throws Exception {
        Utilisateur marie = membre();
        UUID commande = commandePayee(marie);
        LocalDate aujourdhui = LocalDate.now(BRUXELLES);
        saisir(commande, aujourdhui).andExpect(flash().attributeExists("message"));
        en(marie.getEmail(), () -> retractations.demander(marie.getEmail(), commande, null));

        saisir(commande, aujourdhui).andExpect(flash().attributeExists("erreur"));

        assertThat(dateRemise(commande)).isEqualTo(aujourdhui);
    }

    @Test
    @DisplayName("sans date de remise : la demande reste possible, comme avant")
    void sansDate() throws Exception {
        Utilisateur marie = membre();
        UUID commande = commandePayee(marie);

        assertThat(en(marie.getEmail(), () -> retractations.etatDeLaCommande(marie.getEmail(), commande))
                .demandable()).isTrue();
    }

    @Test
    @DisplayName("délai échu depuis la remise : la demande n'est plus proposée")
    void delaiEchu() throws Exception {
        Utilisateur marie = membre();
        UUID commande = commandePayee(marie);
        jdbc.update("UPDATE commande SET date_remise = ? WHERE reference = ?",
                LocalDate.now(BRUXELLES).minusDays(30), commande);

        assertThat(en(marie.getEmail(), () -> retractations.etatDeLaCommande(marie.getEmail(), commande))
                .demandable()).isFalse();
    }

    @Test
    @DisplayName("quatorze jours après la remise : la demande reste possible")
    void dernierJour() throws Exception {
        Utilisateur marie = membre();
        UUID commande = commandePayee(marie);
        jdbc.update("UPDATE commande SET date_remise = ? WHERE reference = ?",
                LocalDate.now(BRUXELLES).minusDays(14), commande);

        assertThat(en(marie.getEmail(), () -> retractations.etatDeLaCommande(marie.getEmail(), commande))
                .demandable()).isTrue();
    }
}
