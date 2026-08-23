package be.autoservplus.vente.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.domain.StatutPaiement;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Detail d une commande passee de bout en bout (F32), sur un PostgreSQL reel.
 *
 * <p>La commande est fabriquee par la <b>vraie chaine</b> — panier, conversion,
 * paiement, webhook — et non montee a la main : c est ainsi que les lignes portent
 * de vrais prix figes, que le paiement existe reellement et que la facture est
 * emise par son evenement. Un montage direct en base testerait le gabarit, pas
 * l ecran.</p>
 *
 * <p>Sans {@code @Transactional} de classe : l emission de facture pend a un
 * {@code AFTER_COMMIT}, elle n arriverait jamais dans une transaction de test
 * rollbackee. D ou des references uniques par test, les donnees restant dans le
 * conteneur.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Detail d une commande passee (integration)")
class CommandeDetailIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MARIE = "marie@exemple.be";
    private static final String JEAN = "jean@exemple.be";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private PrestatairePaiementFictif prestataireFictif;
    @Autowired private TransactionTemplate transactions;

    private Categorie categorie;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(statut -> {
            membre(MARIE, "Dupont", "Marie");
            membre(JEAN, "Martin", "Jean");
            categorie = categories.findByCode("IT-DET-CAT")
                    .orElseGet(() -> categories.save(
                            new Categorie("IT-DET-CAT", "Pieces", TypeCategorie.PIECE)));
        });
    }

    private void membre(String email, String nom, String prenom) {
        utilisateurs.findByEmailIgnoreCase(email)
                .orElseGet(() -> utilisateurs.save(new Utilisateur(
                        email, "$2a$12$h", nom, prenom, TypeUtilisateur.MEMBRE)));
    }

    private Piece piece(String libelle, String prixHtva) {
        return transactions.execute(statut -> {
            Piece piece = new Piece(categorie, "IT-DET-" + COMPTEUR.getAndIncrement(),
                    libelle, new BigDecimal(prixHtva));
            piece.setTauxTva(new BigDecimal("21.00"));
            piece.setQuantiteStock(50);
            return pieces.save(piece);
        });
    }

    /** Panier converti en commande, pour le membre indique ; rend sa reference. */
    private UUID commander(String email, Piece piece, int quantite) throws Exception {
        panierService.ajouterPiece(email, piece.getReference(), quantite);
        String redirection = mvc.perform(post("/commande").with(user(email).roles("MEMBRE"))
                        .with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return UUID.fromString(redirection.replace("/commande/", "").replace("/confirmation", ""));
    }

    /** Paie jusqu au webhook confirme : c est le commit qui emet la facture. */
    private void payer(String email, UUID referenceCommande) throws Exception {
        String redirection = mvc.perform(post("/commande/{ref}/payer", referenceCommande)
                        .with(user(email).roles("MEMBRE")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = redirection.substring(redirection.lastIndexOf('/') + 1);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        mvc.perform(post("/webhooks/paiement")
                        .with(csrf())
                        .param("id", referencePrestataire))
                .andExpect(status().isOk());
    }

    private String detail(String email, UUID reference) throws Exception {
        return mvc.perform(get("/commandes/{ref}", reference)
                        .with(user(email).roles("MEMBRE")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Nested
    @DisplayName("appartenance")
    class Appartenance {

        /**
         * La garde du bloc. Un 403 confirmerait a Jean que cette commande existe ;
         * seul un 404 ne dit rien — meme code qu une reference inventee.
         */
        @Test
        @DisplayName("la commande d'un autre membre remonte en 404, jamais en 403")
        void commandeDAutrui() throws Exception {
            UUID deMarie = commander(MARIE, piece("Plaquettes", "19.99"), 2);

            mvc.perform(get("/commandes/{ref}", deMarie)
                            .with(user(JEAN).roles("MEMBRE")).locale(Locale.FRENCH))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("une reference inconnue remonte en 404")
        void referenceInconnue() throws Exception {
            mvc.perform(get("/commandes/{ref}", UUID.randomUUID())
                            .with(user(MARIE).roles("MEMBRE")).locale(Locale.FRENCH))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("le membre voit sa propre commande")
        void saPropreCommande() throws Exception {
            UUID reference = commander(MARIE, piece("Plaquettes", "19.99"), 1);

            mvc.perform(get("/commandes/{ref}", reference)
                            .with(user(MARIE).roles("MEMBRE")).locale(Locale.FRENCH))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("contenu de la page")
    class Contenu {

        @Test
        @DisplayName("affiche les lignes avec quantites, prix unitaires et totaux de ligne")
        void lignesEtQuantites() throws Exception {
            Piece plaquettes = piece("Plaquettes de frein avant", "20.00");
            UUID reference = commander(MARIE, plaquettes, 3);

            String page = detail(MARIE, reference);

            assertContient(page, "Plaquettes de frein avant");
            assertContient(page, "Articles commandés");
            assertContient(page, "21 %");
            assertContient(page, euros("20,00"));   // prix unitaire HTVA
            assertContient(page, euros("60,00"));   // total ligne HTVA : 3 x 20,00
        }

        @Test
        @DisplayName("affiche les trois totaux de la commande")
        void troisTotaux() throws Exception {
            UUID reference = commander(MARIE, piece("Ampoule", "10.00"), 2);

            String page = detail(MARIE, reference);

            assertContient(page, "Total HTVA");
            assertContient(page, "Total TVA");
            assertContient(page, "Total TVAC");
            assertContient(page, euros("20,00"));   // HTVA : 2 x 10,00
            assertContient(page, euros("4,20"));    // TVA a 21 %
            assertContient(page, euros("24,20"));   // TVAC
        }

        /**
         * RM-30 : le detail doit continuer de concorder avec la facture archivee, qui
         * est immuable. Un tarif revise apres l achat ne reecrit pas ce qui a ete paye.
         */
        @Test
        @DisplayName("les prix restent ceux figes a la commande, meme si le catalogue change")
        void prixFiges() throws Exception {
            Piece filtre = piece("Filtre a huile", "15.00");
            UUID reference = commander(MARIE, filtre, 1);

            transactions.executeWithoutResult(statut -> {
                Piece rechargee = pieces.findById(filtre.getId()).orElseThrow();
                rechargee.modifierPrix(new BigDecimal("99.00"));
                pieces.saveAndFlush(rechargee);
            });

            String page = detail(MARIE, reference);

            assertContient(page, euros("15,00"));
            assertNeContientPas(page, euros("99,00"));
        }

        @Test
        @DisplayName("affiche le statut courant et, une fois payee, le paiement")
        void statutEtPaiement() throws Exception {
            UUID reference = commander(MARIE, piece("Balai", "12.00"), 1);

            assertContient(detail(MARIE, reference), "En attente de paiement");
            // Tant qu aucun paiement n a abouti, aucune section paiement : une case
            // vide donnerait a croire a une information perdue.
            assertNeContientPas(detail(MARIE, reference), "Mode de paiement");

            payer(MARIE, reference);

            String apresPaiement = detail(MARIE, reference);
            assertContient(apresPaiement, "Payée");
            assertContient(apresPaiement, "Mode de paiement");
            // Le bouchon ne renseigne pas le moyen employe : l ecran le dit au lieu
            // d en inventer un.
            assertContient(apresPaiement, "moyen non communiqué");
        }
    }

    @Nested
    @DisplayName("liens conditionnels")
    class LiensConditionnels {

        @Test
        @DisplayName("commande payee : lien de facture et demande d'annulation")
        void commandePayee() throws Exception {
            UUID reference = commander(MARIE, piece("Disque", "45.00"), 1);
            payer(MARIE, reference);

            String page = detail(MARIE, reference);

            assertContient(page, "/factures/");
            assertContient(page, "Télécharger la facture");
            // Meme calcul d eligibilite que la liste : commande payee, delai en cours.
            assertContient(page, "/commandes/" + reference + "/annulation");
        }

        @Test
        @DisplayName("commande non payee : ni facture ni annulation")
        void commandeNonPayee() throws Exception {
            UUID reference = commander(MARIE, piece("Essuie-glace", "9.00"), 1);

            String page = detail(MARIE, reference);

            assertNeContientPas(page, "/factures/");
            assertNeContientPas(page, "/annulation");
            assertContient(page, "Aucun document ni démarche");
        }
    }

    @Nested
    @DisplayName("presentation")
    class Presentation {

        @Test
        @DisplayName("l'ecran est traduit : rien n'est en dur dans le gabarit")
        void ecranTraduit() throws Exception {
            UUID reference = commander(MARIE, piece("Courroie", "30.00"), 1);

            String page = mvc.perform(get("/commandes/{ref}", reference)
                            .with(user(MARIE).roles("MEMBRE"))
                            .locale(Locale.forLanguageTag("nl")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertContient(page, "Bestelde artikelen");
            assertContient(page, "Totaal incl. btw");
            assertNeContientPas(page, "Articles commandés");
        }

        @Test
        @DisplayName("la liste mene au detail : le numero est un lien")
        void listeMeneAuDetail() throws Exception {
            UUID reference = commander(MARIE, piece("Bougie", "8.00"), 1);

            mvc.perform(get("/commandes").with(user(MARIE).roles("MEMBRE")).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/commandes/" + reference + "\"")));
        }
    }

    /**
     * Montant tel que le rend {@code FormatageRdv.euros} : le symbole est separe par
     * une espace <b>insecable</b>, invisible a la relecture. L ecrire en clair ici
     * evite un test qui echoue sur un caractere qu on ne voit pas.
     */
    private static String euros(String montant) {
        return montant + " €";
    }

    private static void assertContient(String page, String attendu) {
        org.assertj.core.api.Assertions.assertThat(page).contains(attendu);
    }

    private static void assertNeContientPas(String page, String interdit) {
        org.assertj.core.api.Assertions.assertThat(page).doesNotContain(interdit);
    }
}
