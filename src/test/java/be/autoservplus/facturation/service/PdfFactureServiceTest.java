package be.autoservplus.facturation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.dto.DocumentFacture;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Politique de cache du PDF : fabrique une fois a la demande, sert l archive
 * ensuite. Servir l archive n est pas une optimisation — c est le document remis
 * au client qui doit etre reservi, pas ce que le code produirait aujourd hui.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdfFactureService")
class PdfFactureServiceTest {

    private static final byte[] PDF = "%PDF-1.4 engendre".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PDF_ARCHIVE = "%PDF-1.4 archive".getBytes(StandardCharsets.UTF_8);
    private static final Instant MAINTENANT = Instant.parse("2026-08-22T14:30:00Z");

    @Mock private FactureRepository factures;
    @Mock private FactureService factureService;
    @Mock private GenerateurPdfFacture generateur;
    @Mock private ArchiveComptable archive;

    private PdfFactureService service;

    private Utilisateur marie;
    private Facture facture;
    private List<LignePanier> lignes;

    @BeforeEach
    void setUp() {
        service = new PdfFactureService(factures, factureService, generateur, archive);
        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        marie.setLangue(Langue.nl);

        Categorie categorie = new Categorie("PIECES", "Pieces", TypeCategorie.PIECE);
        Piece piece = new Piece(categorie, "P-1", "Plaquettes", new BigDecimal("19.99"));
        piece.setTauxTva(new BigDecimal("21.00"));
        piece.setQuantiteStock(10);
        Panier panier = new Panier(marie);
        panier.ajouterPiece(piece, 2);
        lignes = panier.getLignes();

        Commande commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                MAINTENANT.minusSeconds(600));
        commande.confirmerPaiement(MAINTENANT.minusSeconds(60));
        facture = Facture.pourCommande("2026-0042", (short) 2026, 42, commande,
                new BigDecimal("21.00"), MAINTENANT);
    }

    private void generationAboutit() {
        when(factureService.lignesDe(facture)).thenReturn(lignes);
        when(factureService.ventilationDe(facture)).thenReturn(VentilationTva.desLignes(lignes));
        when(generateur.engendrer(any(DocumentFacture.class))).thenReturn(PDF);
        when(archive.archiver(anyShort(), anyString(), any())).thenReturn("2026/2026-0042.pdf");
    }

    @Test
    @DisplayName("premiere demande : le PDF est fabrique, archive, et son chemin inscrit")
    void premiereDemandeGenereEtArchive() {
        generationAboutit();

        assertThat(service.pdfDe(facture)).isEqualTo(PDF);

        verify(archive).archiver((short) 2026, "2026-0042", PDF);
        assertThat(facture.getCheminPdf()).isEqualTo("2026/2026-0042.pdf");
        verify(factures).saveAndFlush(facture);
    }

    @Test
    @DisplayName("demandes suivantes : l'archive est servie, rien n'est regenere")
    void demandeSuivanteSertLeCache() {
        facture.archiverPdf("2026/2026-0042.pdf");
        when(archive.lire("2026/2026-0042.pdf")).thenReturn(Optional.of(PDF_ARCHIVE));

        assertThat(service.pdfDe(facture)).isEqualTo(PDF_ARCHIVE);

        // Le document conserve sept ans doit rester celui qui a ete remis : une
        // evolution du gabarit ne reecrit pas retroactivement une facture emise.
        verifyNoInteractions(generateur);
        verify(factures, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("archive perdue : le document est reconstruit plutot que refuse")
    void archivePerdueEstReconstruite() {
        facture.archiverPdf("2026/2026-0042.pdf");
        when(archive.lire("2026/2026-0042.pdf")).thenReturn(Optional.empty());
        generationAboutit();

        assertThat(service.pdfDe(facture)).isEqualTo(PDF);

        verify(generateur).engendrer(any(DocumentFacture.class));
    }

    @Test
    @DisplayName("le document imprime est constitue dans la langue du MEMBRE")
    void documentDansLaLangueDuMembre() {
        generationAboutit();

        service.pdfDe(facture);

        ArgumentCaptor<DocumentFacture> capture = ArgumentCaptor.forClass(DocumentFacture.class);
        verify(generateur).engendrer(capture.capture());
        DocumentFacture document = capture.getValue();
        // Marie est neerlandophone : sa facture l est aussi, quelle que soit la
        // langue de la session qui la telecharge.
        assertThat(document.locale()).isEqualTo(Locale.forLanguageTag("nl"));
        assertThat(document.numero()).isEqualTo("2026-0042");
        assertThat(document.numeroCommande()).isEqualTo("CMD-2026-0001");
        assertThat(document.client().nomComplet()).isEqualTo("Marie Dupont");
        assertThat(document.totalTvac()).isEqualByComparingTo("48.38");
        assertThat(document.lignes()).singleElement().satisfies(ligne -> {
            assertThat(ligne.libelle()).isEqualTo("Plaquettes");
            assertThat(ligne.quantite()).isEqualTo(2);
            assertThat(ligne.totalHtva()).isEqualByComparingTo("39.98");
        });
    }

    @Test
    @DisplayName("le nom de fichier propose porte le numero de facture")
    void nomDeFichier() {
        assertThat(service.nomDeFichier(facture)).isEqualTo("facture-2026-0042.pdf");
    }
}
