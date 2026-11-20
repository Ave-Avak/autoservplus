package be.autoservplus.facturation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.rgpd.service.ExportDonneesService;
import be.autoservplus.rgpd.service.dto.ExportDonnees;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Instantané du client sur la facture (V61) : figé à l'émission, lu par le PDF, restitué
 * et restitué par l'export.
 *
 * <p>Sans {@code @Transactional} de classe : la facture naît d'un écouteur exécuté après
 * la validation du paiement.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Instantané client de la facture (intégration)")
class InstantaneClientIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private FactureService factures;
    @Autowired private PdfFactureService pdfFactures;
    @Autowired private ExportDonneesService export;
    @Autowired private PrestatairePaiementFictif prestataireFictif;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    private Utilisateur creer(boolean avecAdresse) {
        String email = "instantane-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        return transactions.execute(statut -> {
            Utilisateur membre = avecAdresse
                    ? MembresDeTest.membre(email, encodeur.encode(MOT_DE_PASSE), "Dupont", "Marie")
                    : MembresDeTest.sansAdresse(email, encodeur.encode(MOT_DE_PASSE), "Dupont", "Marie");
            membre.confirmerAdresseEmail();
            membre.setStatut(StatutUtilisateur.ACTIF);
            return utilisateurs.saveAndFlush(membre);
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

    private void panier(Utilisateur acheteur) {
        Categorie categorie = transactions.execute(statut -> categories.findByCode("IT-INS-CAT")
                .orElseGet(() -> categories.save(new Categorie("IT-INS-CAT", "Pieces", TypeCategorie.PIECE))));
        Piece piece = transactions.execute(statut -> {
            Piece nouvelle = new Piece(categorie, "IT-INS-" + COMPTEUR.getAndIncrement(),
                    "Balai d'essuie-glace", new BigDecimal("15.00"));
            nouvelle.setQuantiteStock(50);
            return pieces.save(nouvelle);
        });
        en(acheteur.getEmail(), () -> {
            panierService.ajouterPiece(acheteur.getEmail(), piece.getReference(), 1);
            return null;
        });
    }

    private UUID commander(Utilisateur acheteur) throws Exception {
        String redirection = mvc.perform(post("/commande").with(user(acheteur.getEmail()))
                        .with(csrf()).param("cgv", "true"))
                .andExpect(redirectedUrlPattern("/commande/*/confirmation"))
                .andReturn().getResponse().getRedirectedUrl();
        return UUID.fromString(redirection.replace("/commande/", "").replace("/confirmation", ""));
    }

    private void payer(Utilisateur acheteur, UUID commande) throws Exception {
        String paiement = mvc.perform(post("/commande/{ref}/payer", commande)
                        .with(user(acheteur.getEmail())).with(csrf()))
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = paiement.substring(paiement.lastIndexOf('/') + 1);
        prestataireFictif.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);
        mvc.perform(post("/webhooks/paiement").with(anonymous()).param("id", referencePrestataire))
                .andExpect(status().isOk());
    }

    private String texteDuPdf(UUID commande) throws Exception {
        byte[] pdf = transactions.execute(statut -> pdfFactures.pdfDe(
                factures.factureDe(commande).orElseThrow()));
        PdfReader lecteur = new PdfReader(pdf);
        StringBuilder texte = new StringBuilder();
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
            texte.append(new PdfTextExtractor(lecteur).getTextFromPage(page));
        }
        return texte.toString();
    }

    private Map<String, Object> instantane(UUID commande) {
        return jdbc.queryForMap("""
                SELECT f.client_prenom, f.client_nom, f.client_rue, f.client_localite,
                       f.client_pays, f.langue_document
                FROM facture f JOIN commande c ON c.id = f.commande_id
                WHERE c.reference = ?
                """, commande);
    }

    @Test
    @DisplayName("figé à l'émission : une adresse et une langue changées ensuite ne touchent pas la facture")
    void figeALEmission() throws Exception {
        Utilisateur marie = creer(true);
        panier(marie);
        UUID commande = commander(marie);
        payer(marie, commande);

        jdbc.update("UPDATE utilisateur SET rue = 'Rue Neuve', localite = 'Namur', langue = 'nl' WHERE id = ?",
                marie.getId());

        assertThat(instantane(commande))
                .containsEntry("client_prenom", "Marie")
                .containsEntry("client_nom", "Dupont")
                .containsEntry("client_rue", MembresDeTest.RUE)
                .containsEntry("client_localite", MembresDeTest.LOCALITE)
                .containsEntry("langue_document", "fr");
        String texte = texteDuPdf(commande);
        assertThat(texte).contains("Marie Dupont", MembresDeTest.RUE)
                .doesNotContain("Rue Neuve")
                .doesNotContain(marie.getEmail());
    }

    /**
     * Hors du profil demo, aucun trigger ne complète l'instantané : la facture émise par
     * le code porte le sien, et une insertion qui l'oublierait échoue sur le NOT NULL.
     * Le complément de la graine (db/demo, V899) n'existe pas dans ce schéma.
     */
    @Test
    @DisplayName("hors demo : instantané fourni par le code, aucun complément en base")
    void sansComplementHorsDemo() throws Exception {
        Utilisateur marie = creer(true);
        panier(marie);
        UUID commande = commander(marie);
        payer(marie, commande);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgrelid = 'facture'::regclass AND NOT tgisinternal",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname LIKE 'tg_facture_instantane%'",
                Long.class)).isZero();
        assertThat(instantane(commande)).containsEntry("client_nom", "Dupont")
                .containsEntry("client_rue", MembresDeTest.RUE);

        Long membre = marie.getId();
        Long idCommande = jdbc.queryForObject("SELECT id FROM commande WHERE reference = ?",
                Long.class, commande);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO facture (numero, exercice, sequence_annuelle, commande_id, membre_id,
                                     montant_htva, montant_tva, montant_tvac)
                VALUES ('2099-0001', 2099, 1, ?, ?, 1.00, 0.21, 1.21)
                """, idCommande, membre))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .hasMessageContaining("client_prenom");
    }

    @Test
    @DisplayName("l'export F22 restitue l'instantané de chaque facture")
    void export() throws Exception {
        Utilisateur marie = creer(true);
        panier(marie);
        UUID commande = commander(marie);
        payer(marie, commande);

        ExportDonnees donnees = en(marie.getEmail(), () -> export.assembler(marie.getEmail()));

        assertThat(donnees.donneesPersonnelles().factures()).singleElement().satisfies(facture -> {
            assertThat(facture.clientNom()).isEqualTo("Dupont");
            assertThat(facture.adresseClient().rue()).isEqualTo(MembresDeTest.RUE);
        });
    }
}
