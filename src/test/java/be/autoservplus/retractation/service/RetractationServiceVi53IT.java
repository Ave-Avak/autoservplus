package be.autoservplus.retractation.service;

import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.service.InterventionService;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import be.autoservplus.vente.domain.StatutPaiement;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Articulation VI.53 / rétractation (F12-b), sur un PostgreSQL reel.
 *
 * <p>Couvre les <b>quatre</b> branches de la regle, parce que trois d entre elles
 * aboutissent au meme resultat par des chemins differents : seule la combinaison
 * « renonciation ET pleinement execute » retire le droit de retractation.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Retractation et renonciation VI.53 (integration)")
class RetractationServiceVi53IT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private PanierService paniers;
    @Autowired private CommandeService commandes;
    @Autowired private RetractationService retractations;
    @Autowired private InterventionService interventions;
    @Autowired private PrestationRepository prestations;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PrestatairePaiementFictif prestataire;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    private String membre;
    private int n;

    @BeforeEach
    void setUp() {
        n = COMPTEUR.getAndIncrement();
        membre = "vi53b-" + n + "@exemple.be";
        Utilisateur u = utilisateurs.save(new Utilisateur(membre,
                "$2a$12$abcdefghijklmnopqrstuv", "Test", "Alex", TypeUtilisateur.MEMBRE));
        vehicules.save(new Vehicule(u, "9-VI%03d-53".formatted(n), "Renault", "Clio",
                Motorisation.ESSENCE));
    }

    /** Les gestes reserves au garage ; ce n est pas l objet du test. */
    private void enAdministrateur(Runnable action) {
        SecurityContext avant = SecurityContextHolder.getContext();
        try {
            SecurityContextHolder.setContext(new SecurityContextImpl(
                    new UsernamePasswordAuthenticationToken("garage@autoservplus.be", "n/a",
                            List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATEUR")))));
            action.run();
        } finally {
            SecurityContextHolder.setContext(avant);
        }
    }

    /** Commande de services payee, avec ou sans renonciation VI.53. */
    private UUID commandePayee(boolean renonciation) throws Exception {
        UUID prestation = prestations.findByActifTrueOrderByLibelleAsc().get(0).getReference();
        paniers.ajouterService(membre, prestation, 1);
        UUID reference = commandes.passerCommande(membre, true, renonciation, "127.0.0.1")
                .reference();
        payer(reference);
        return reference;
    }

    /**
     * Paiement par la VRAIE chaine — depart, statut programme chez le prestataire
     * bouchonne, webhook — et non un UPDATE direct : c est le webhook qui fait basculer
     * la commande a PAYEE, et court-circuiter ce chemin testerait autre chose.
     */
    private void payer(UUID reference) throws Exception {
        // MockMvc VIDE le SecurityContextHolder a la fin de chaque requete : sans cette
        // sauvegarde, les appels de service directs qui suivent perdraient le contexte
        // pose par @WithMockUser et leveraient AuthenticationCredentialsNotFound.
        SecurityContext avant = SecurityContextHolder.getContext();
        try {
            String redirection = mvc.perform(post("/commande/{ref}/payer", reference)
                            .with(user(membre)).with(csrf()))
                    .andReturn().getResponse().getRedirectedUrl();
            String referencePrestataire =
                    redirection.substring(redirection.lastIndexOf('/') + 1);
            prestataire.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
            mvc.perform(post("/webhooks/paiement").with(anonymous())
                    .param("id", referencePrestataire)).andExpect(status().isOk());
        } finally {
            SecurityContextHolder.setContext(avant);
        }
    }

    private void ouvrirEtTerminerLeDossier(UUID commande) {
        UUID vehicule = vehicules.findByMembre(membre).getFirst().getReference();
        enAdministrateur(() -> {
            var it = interventions.creerDepuisCommande(commande, vehicule);
            interventions.demarrer(it.getReference());
            interventions.terminer(it.getReference());
        });
    }

    @Nested
    @DisplayName("Les quatre branches de la regle")
    class QuatreBranches {

        @Test
        @WithMockUser
        @DisplayName("service SANS renonciation, meme pleinement execute : retractable")
        void sansRenonciationMemeExecute() throws Exception {
            UUID commande = commandePayee(false);
            ouvrirEtTerminerLeDossier(commande);

            assertThatCode(() -> retractations.demander(membre, commande, null))
                    .as("sans accord prealable expres, l art. VI.53 ne retire rien")
                    .doesNotThrowAnyException();
        }

        @Test
        @WithMockUser
        @DisplayName("service AVEC renonciation mais PAS execute : retractable")
        void avecRenonciationNonExecute() throws Exception {
            UUID commande = commandePayee(true);
            // Aucun dossier d'atelier ouvert : rien n'a ete fait pour le client.

            assertThatCode(() -> retractations.demander(membre, commande, null))
                    .as("la renonciation ne prend effet qu a l execution pleine")
                    .doesNotThrowAnyException();
        }

        @Test
        @WithMockUser
        @DisplayName("service AVEC renonciation, dossier ouvert mais NON termine : retractable")
        void avecRenonciationEnCours() throws Exception {
            UUID commande = commandePayee(true);
            UUID vehicule = vehicules.findByMembre(membre).getFirst().getReference();
            enAdministrateur(() -> {
                var it = interventions.creerDepuisCommande(commande, vehicule);
                interventions.demarrer(it.getReference());
            });

            assertThatCode(() -> retractations.demander(membre, commande, null))
                    .as("commence n est pas pleinement execute")
                    .doesNotThrowAnyException();
        }

        @Test
        @WithMockUser
        @DisplayName("service AVEC renonciation ET pleinement execute : NON retractable")
        void avecRenonciationEtExecute() throws Exception {
            UUID commande = commandePayee(true);
            ouvrirEtTerminerLeDossier(commande);

            assertThatThrownBy(() -> retractations.demander(membre, commande, null))
                    .isInstanceOf(RetractationImpossibleException.class)
                    .hasFieldOrPropertyWithValue("motif",
                            MotifRefusRetractation.SERVICE_EXECUTE_APRES_RENONCIATION);
        }
    }

    @Nested
    @DisplayName("Non-regression du comportement piece")
    class PieceInchangee {

        @Test
        @WithMockUser
        @DisplayName("une commande de pieces reste retractable, renonciation impossible")
        void pieceToujoursRetractable() throws Exception {
            jdbc.update("""
                    INSERT INTO piece (categorie_id, reference_fabricant, libelle, prix_htva,
                                       quantite_stock)
                    VALUES ((SELECT id FROM categorie WHERE type = 'PIECE' LIMIT 1), ?, ?, 9.00, 10)
                    """, "REF-VI53B-" + n, "Piece VI53b");
            UUID piece = jdbc.queryForObject(
                    "SELECT reference FROM piece WHERE reference_fabricant = ?",
                    UUID.class, "REF-VI53B-" + n);

            paniers.ajouterPiece(membre, piece, 1);
            // Case cochee volontairement : elle doit rester sans effet sur des pieces.
            UUID commande = commandes.passerCommande(membre, true, true, "127.0.0.1")
                    .reference();
            payer(commande);

            assertThat(jdbc.queryForObject(
                    "SELECT renonciation_vi53 FROM commande WHERE reference = ?",
                    Boolean.class, commande))
                    .as("validation serveur : pas de renonciation sur des pieces")
                    .isFalse();
            assertThatCode(() -> retractations.demander(membre, commande, null))
                    .doesNotThrowAnyException();
        }
    }
}
