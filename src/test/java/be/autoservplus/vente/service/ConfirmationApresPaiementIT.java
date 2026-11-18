package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.communication.service.Courriel;
import be.autoservplus.communication.service.PieceJointeCourriel;
import be.autoservplus.communication.service.TransportCourriel;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirmation du contrat (art. VI.46, § 7, CDE) partie au bout du parcours réel :
 * commande, paiement, webhook, puis le courriel remis au transport.
 *
 * <p>La classe n'est pas transactionnelle, comme {@code PaiementConcurrenceIT} : la
 * confirmation part d'un écouteur AFTER_COMMIT, qu'une transaction de test annulée ne
 * déclencherait jamais. Le transport est espionné, pas remplacé : le courriel capté est
 * celui que le relais aurait reçu.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(username = ConfirmationApresPaiementIT.EMAIL)
@DisplayName("Confirmation du contrat envoyée après le commit du paiement (intégration)")
class ConfirmationApresPaiementIT extends SocleIntegration {

    static final String EMAIL = "lies.confirmation@exemple.be";
    private static final String TYPE = "confirmation-commande";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PanierService panierService;
    @Autowired private CommandeRepository commandes;
    @Autowired private PrestatairePaiementFictif prestataire;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private TransportCourriel transport;

    private Piece filtre;

    @BeforeAll
    void catalogue() {
        Utilisateur lies = new Utilisateur(EMAIL, "$2a$12$h", "Peeters", "Lies", TypeUtilisateur.MEMBRE);
        lies.setLangue(Langue.nl);
        utilisateurs.save(lies);
        Categorie moteur = categories.save(new Categorie("IT-CAP-MOT", "Moteur", TypeCategorie.PIECE));
        filtre = new Piece(moteur, "IT-CAP-001", "Filtre a huile", new BigDecimal("12.50"));
        filtre.setQuantiteStock(10);
        filtre = pieces.save(filtre);
    }

    @Test
    @DisplayName("paiement réussi : un courriel après le commit, CGV de la commande puis formulaire, en néerlandais")
    void confirmationApresLeCommit() throws Exception {
        panierService.ajouterPiece(EMAIL, filtre.getReference(), 1);
        String confirmation = mvc.perform(post("/commande").with(csrf()).param("cgv", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID reference = UUID.fromString(confirmation.replace("/commande/", "").replace("/confirmation", ""));
        String page = mvc.perform(post("/commande/{ref}/payer", reference).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/paiement-fictif/*"))
                .andReturn().getResponse().getRedirectedUrl();
        String referencePrestataire = page.substring(page.lastIndexOf('/') + 1);
        prestataire.programmerStatut(referencePrestataire, StatutPaiement.REUSSI);

        assertThat(confirmationsParties()).as("avant le webhook").isEmpty();

        mvc.perform(post("/webhooks/paiement").with(anonymous()).param("id", referencePrestataire))
                .andExpect(status().isOk());

        List<Courriel> parties = confirmationsParties();
        assertThat(parties).as("confirmations remises au transport").hasSize(1);
        Courriel courriel = parties.get(0);
        String numero = commandes.findByReference(reference).orElseThrow().getNumero();
        String version = jdbc.queryForObject("""
                SELECT k.version_acceptee FROM consentement k
                JOIN commande c ON c.id = k.commande_id
                WHERE c.reference = ? AND k.type_document = 'CGV'
                """, String.class, reference);
        assertThat(version).isEqualTo("CGV-2026-04");
        assertThat(courriel.adresse()).isEqualTo(EMAIL);
        assertThat(courriel.sujet()).isEqualTo("Bevestiging van uw bestelling");
        assertThat(courriel.piecesJointes()).extracting(PieceJointeCourriel::nomFichier)
                .containsExactly("algemene-voorwaarden-" + version + ".pdf", "modelformulier-herroeping.pdf");
        assertThat(texte(courriel.piecesJointes().get(0).contenu()))
                .contains("Versie " + version)
                .contains("voor bestelling " + numero)
                .contains("Handelsnaam: Garage Démo");
        assertThat(texte(courriel.piecesJointes().get(1).contenu()))
                .contains("Modelformulier voor herroeping");
    }

    private List<Courriel> confirmationsParties() {
        ArgumentCaptor<Courriel> remis = ArgumentCaptor.forClass(Courriel.class);
        verify(transport, atLeast(0)).transmettre(remis.capture());
        return remis.getAllValues().stream().filter(courriel -> TYPE.equals(courriel.type())).toList();
    }

    private static String texte(byte[] document) throws IOException {
        PdfReader lecteur = new PdfReader(document);
        PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
        StringBuilder texte = new StringBuilder();
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
            texte.append(extracteur.getTextFromPage(page)).append('\n');
        }
        return texte.toString();
    }
}
