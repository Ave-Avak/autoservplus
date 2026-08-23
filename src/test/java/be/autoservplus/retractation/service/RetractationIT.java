package be.autoservplus.retractation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.domain.StatutDemandeAnnulation;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Retractation au bout de la chaine reelle (F30, RM-23) : panier, commande,
 * paiement, facture, demande du membre, decision du garage — puis la note de credit
 * et le remboursement.
 *
 * <p>Volontairement <b>sans</b> {@code @Transactional} de classe, comme
 * {@code EmissionFactureIT} : la facture nait d un listener AFTER_COMMIT, et une
 * transaction englobante rollbackee empecherait tout de se declencher. Consequence :
 * les donnees restent en base du conteneur (jetable, un par classe), d ou les
 * references uniques par test.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Retractation de bout en bout (integration)")
class RetractationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Sans rollback, chaque test laisse ses donnees : les references doivent differer. */
    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    private static final String MARIE = "marie@exemple.be";
    private static final String JEAN = "jean@exemple.be";
    private static final String ADMIN = "admin@autoservplus.be";
    private static final String MEMBRE = "ROLE_MEMBRE";
    private static final String ADMINISTRATEUR = "ROLE_ADMINISTRATEUR";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private CommandeRepository commandes;
    @Autowired private FactureService factures;
    @Autowired private RetractationService retractations;
    @Autowired private AdminRetractationService adminRetractations;
    @Autowired private AvoirRepository avoirs;
    @Autowired private PrestatairePaiementFictif prestataireFictif;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    private Categorie categorie;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(statut -> {
            utilisateurs.findByEmailIgnoreCase("marie@exemple.be")
                    .orElseGet(() -> utilisateurs.save(new Utilisateur(
                            "marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                            TypeUtilisateur.MEMBRE)));
            utilisateurs.findByEmailIgnoreCase("jean@exemple.be")
                    .orElseGet(() -> utilisateurs.save(new Utilisateur(
                            "jean@exemple.be", "$2a$12$h", "Martin", "Jean",
                            TypeUtilisateur.MEMBRE)));
            categorie = categories.findByCode("IT-RET-CAT")
                    .orElseGet(() -> categories.save(
                            new Categorie("IT-RET-CAT", "Pieces", TypeCategorie.PIECE)));
        });
    }

    // --- montage de la chaine ------------------------------------------------------------

    private Piece piece(String prixHtva, String tauxTva) {
        return transactions.execute(statut -> {
            Piece piece = new Piece(categorie, "IT-RET-" + COMPTEUR.getAndIncrement(),
                    "Piece de test", new BigDecimal(prixHtva));
            piece.setTauxTva(new BigDecimal(tauxTva));
            piece.setQuantiteStock(50);
            return pieces.save(piece);
        });
    }

    /** Panier, commande, paiement confirme : la commande ressort PAYEE et facturee. */
    private UUID commandePayee(Piece piece, int quantite) throws Exception {
        panierService.ajouterPiece("marie@exemple.be", piece.getReference(), quantite);
        String redirection = mvc.perform(post("/commande").with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID reference = UUID.fromString(
                redirection.replace("/commande/", "").replace("/confirmation", ""));

        String paiement = mvc.perform(post("/commande/{ref}/payer", reference).with(csrf()))
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = paiement.substring(paiement.lastIndexOf('/') + 1);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        mvc.perform(post("/webhooks/paiement").with(anonymous())
                .param("id", referencePrestataire)).andExpect(status().isOk());
        return reference;
    }

    private DemandeAnnulation demanderAnnulation(UUID referenceCommande) {
        return executerEn(MARIE, MEMBRE, () -> retractations.demander(
                "marie@exemple.be", referenceCommande, "piece non compatible"));
    }

    /**
     * Avoir de la commande, charge avec ses associations : la requete du repository
     * fetche la facture et son membre, que le test lit hors transaction.
     */
    private Avoir avoirDe(UUID referenceCommande) {
        UUID reference = jdbc.queryForObject("""
                SELECT a.reference FROM avoir a
                JOIN facture f ON f.id = a.facture_id
                JOIN commande c ON c.id = f.commande_id
                WHERE c.reference = ?
                """, UUID.class, referenceCommande);
        return transactions.execute(statut -> avoirs.findByReference(reference).orElseThrow());
    }

    // --- scenarios --------------------------------------------------------------------

    @Test
    @DisplayName("chaine complete : la validation contre-passe la facture, rembourse et cloture")
    void chaineComplete() throws Exception {
        UUID commande = commandePayee(piece("19.99", "21.00"), 2);
        Facture facture = transactions.execute(s -> factures.factureDe(commande).orElseThrow());
        DemandeAnnulation demande = demanderAnnulation(commande);
        assertThat(demande.getStatut()).isEqualTo(StatutDemandeAnnulation.EN_ATTENTE);

        validerParAdmin(demande.getReference());

        Avoir avoir = avoirDe(commande);
        // Numero de la suite des avoirs, distincte de celle des factures.
        assertThat(avoir.getNumero()).matches("AV-2\\d{3}-\\d{4}");
        assertThat(avoir.getNumero()).isNotEqualTo(facture.getNumero());
        // Contre-passation au centime : ce sont les montants de la facture, pas un
        // recalcul depuis le catalogue.
        assertThat(avoir.getMontantHtva()).isEqualByComparingTo("39.98");
        assertThat(avoir.getMontantTva()).isEqualByComparingTo("8.40");
        assertThat(avoir.getMontantTvac()).isEqualByComparingTo("48.38");
        assertThat(avoir.getCheminPdf())
                .as("Aucun PDF a l emission : il sera fabrique a la premiere demande")
                .isNull();

        assertThat(commandes.findByReference(commande).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.REMBOURSEE);
        assertThat(jdbc.queryForObject("""
                SELECT p.statut FROM paiement p JOIN commande c ON c.id = p.commande_id
                WHERE c.reference = ? AND p.statut = 'REMBOURSE'
                """, String.class, commande)).isEqualTo("REMBOURSE");
        assertThat(jdbc.queryForObject("""
                SELECT p.reference_remboursement FROM paiement p
                JOIN commande c ON c.id = p.commande_id WHERE c.reference = ?
                """, String.class, commande)).startsWith("re_fictif_");
        // La facture d origine reste intacte : c est l avoir qui la corrige.
        assertThat(jdbc.queryForObject(
                "SELECT montant_tvac FROM facture WHERE id = ?", BigDecimal.class, facture.getId()))
                .isEqualByComparingTo("48.38");
    }

    @Test
    @DisplayName("double validation : un seul avoir, un seul remboursement")
    void doubleValidation() throws Exception {
        UUID commande = commandePayee(piece("10.00", "21.00"), 1);
        DemandeAnnulation demande = demanderAnnulation(commande);
        validerParAdmin(demande.getReference());
        String numeroInitial = avoirDe(commande).getNumero();

        // Second clic de l administrateur : la machine a etats de la demande refuse.
        assertThatThrownBy(() -> validerParAdmin(demande.getReference()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM avoir a
                JOIN facture f ON f.id = a.facture_id
                JOIN commande c ON c.id = f.commande_id
                WHERE c.reference = ?
                """, Integer.class, commande))
                .as("Une facture, au plus un avoir (uq_avoir_facture)")
                .isEqualTo(1);
        assertThat(avoirDe(commande).getNumero()).isEqualTo(numeroInitial);
    }

    @Test
    @DisplayName("une seule demande en attente par commande (index partiel)")
    void uneSeuleDemandeEnAttente() throws Exception {
        UUID commande = commandePayee(piece("10.00", "21.00"), 1);
        demanderAnnulation(commande);

        assertThatThrownBy(() -> demanderAnnulation(commande))
                .isInstanceOf(RetractationImpossibleException.class);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM demande_annulation d
                JOIN commande c ON c.id = d.commande_id
                WHERE c.reference = ? AND d.statut = 'EN_ATTENTE'
                """, Integer.class, commande)).isEqualTo(1);
    }

    @Test
    @DisplayName("refus motive : aucun avoir, aucun remboursement, la commande reste PAYEE")
    void refusMotive() throws Exception {
        UUID commande = commandePayee(piece("10.00", "21.00"), 1);
        DemandeAnnulation demande = demanderAnnulation(commande);

        refuserParAdmin(demande.getReference(), "Piece deja montee sur le vehicule");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM avoir a
                JOIN facture f ON f.id = a.facture_id
                JOIN commande c ON c.id = f.commande_id
                WHERE c.reference = ?
                """, Integer.class, commande))
                .as("Un refus ne produit aucun mouvement comptable")
                .isZero();
        assertThat(commandes.findByReference(commande).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.PAYEE);
        assertThat(jdbc.queryForObject("""
                SELECT d.motif_decision FROM demande_annulation d
                JOIN commande c ON c.id = d.commande_id WHERE c.reference = ?
                """, String.class, commande)).isEqualTo("Piece deja montee sur le vehicule");
    }

    @Test
    @DisplayName("apres un refus, le membre peut redemander tant que le delai court")
    void redemandeApresRefus() throws Exception {
        // Un refus porte sur un constat date ; il ne prive pas le consommateur de
        // son droit pour toujours. L index partiel ne contraint que EN_ATTENTE.
        UUID commande = commandePayee(piece("10.00", "21.00"), 1);
        DemandeAnnulation premiere = demanderAnnulation(commande);
        refuserParAdmin(premiere.getReference(), "Emballage manquant");

        DemandeAnnulation seconde = demanderAnnulation(commande);

        assertThat(seconde.getReference()).isNotEqualTo(premiere.getReference());
        assertThat(seconde.getStatut()).isEqualTo(StatutDemandeAnnulation.EN_ATTENTE);
    }

    @Test
    @DisplayName("le membre ne peut pas valider sa propre demande (@PreAuthorize)")
    void membreNePeutPasValider() throws Exception {
        UUID commande = commandePayee(piece("10.00", "21.00"), 1);
        DemandeAnnulation demande = demanderAnnulation(commande);

        // Defense en profondeur : la protection d URL /admin/** n est pas la seule
        // garde — l appel direct au service est refuse lui aussi.
        assertThatThrownBy(() -> executerEn(MARIE, MEMBRE, () ->
                adminRetractations.valider(demande.getReference(), "marie@exemple.be")))
                .isInstanceOf(AccessDeniedException.class);
        mvc.perform(post("/admin/retractations/{ref}/valider", demande.getReference())
                        .with(user(MARIE)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/retractations").with(user(MARIE)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le membre ne demande l'annulation que de ses propres commandes")
    void commandeDAutrui() throws Exception {
        UUID commande = commandePayee(piece("10.00", "21.00"), 1);

        // 404, jamais 403 : un 403 confirmerait a Jean que cette commande existe.
        assertThatThrownBy(() -> executerEn(JEAN, MEMBRE, () ->
                retractations.demander("jean@exemple.be", commande, null)))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("la note de credit se telecharge, et seulement par son titulaire")
    void telechargementAvoir() throws Exception {
        UUID commande = commandePayee(piece("10.00", "21.00"), 1);
        DemandeAnnulation demande = demanderAnnulation(commande);
        validerParAdmin(demande.getReference());
        UUID referenceAvoir = avoirDe(commande).getReference();

        mvc.perform(get("/avoirs/{ref}/pdf", referenceAvoir).with(user(MARIE)))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .startsWith("%PDF".getBytes()));

        // Le PDF est archive a la premiere demande, puis relu tel quel (7 ans).
        assertThat(jdbc.queryForObject(
                "SELECT chemin_pdf FROM avoir WHERE reference = ?", String.class, referenceAvoir))
                .matches("\\d{4}/avoirs/AV-\\d{4}-\\d{4}\\.pdf");
    }

    @Test
    @DisplayName("l'avoir d'autrui remonte en 404, jamais en 403")
    void avoirDAutrui() throws Exception {
        UUID commande = commandePayee(piece("10.00", "21.00"), 1);
        DemandeAnnulation demande = demanderAnnulation(commande);
        validerParAdmin(demande.getReference());

        // Jean demande le document de Marie : 404. Un 403 lui apprendrait que cette
        // note de credit existe, et donc que sa titulaire s est retractee.
        mvc.perform(get("/avoirs/{ref}/pdf", avoirDe(commande).getReference())
                        .with(user(JEAN)))
                .andExpect(status().isNotFound());
    }

    // --- helpers d execution sous l identite de l administrateur -----------------------------

    /**
     * L administrateur de seed (V10) : il existe reellement en base, ce qui compte —
     * la demande stocke son decideur en cle etrangere, un principal purement fictif
     * ne suffirait pas.
     */
    private void validerParAdmin(UUID referenceDemande) {
        executerEn(ADMIN, ADMINISTRATEUR, () ->
                adminRetractations.valider(referenceDemande, "admin@autoservplus.be"));
    }

    private void refuserParAdmin(UUID referenceDemande, String motif) {
        executerEn(ADMIN, ADMINISTRATEUR, () ->
                adminRetractations.refuser(referenceDemande, motif, "admin@autoservplus.be"));
    }

    /**
     * Execute une action de service sous une identite donnee, dans sa transaction.
     *
     * <p>Le contexte de securite est pose <b>explicitement</b> et non herite de
     * {@code @WithMockUser} : la chaine de filtres vide le {@code SecurityContextHolder}
     * a la fin de chaque requete MockMvc, si bien qu un appel de service direct
     * effectue apres un {@code mvc.perform} ne trouverait plus d authentification et
     * echouerait sur le {@code @PreAuthorize} du service. Poser le contexte ici rend
     * chaque appel independant de ce qui le precede — et permet d exercer les deux
     * roles dans un meme test.</p>
     */
    private <T> T executerEn(String email, String role, java.util.function.Supplier<T> action) {
        SecurityContext contexte = SecurityContextHolder.createEmptyContext();
        List<SimpleGrantedAuthority> roles = List.of(new SimpleGrantedAuthority(role));
        contexte.setAuthentication(new UsernamePasswordAuthenticationToken(
                new User(email, "n/a", roles), "n/a", roles));
        SecurityContextHolder.setContext(contexte);
        try {
            return transactions.execute(statut -> action.get());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
