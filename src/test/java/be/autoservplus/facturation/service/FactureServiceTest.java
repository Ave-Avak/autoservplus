package be.autoservplus.facturation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import be.autoservplus.vente.repository.CommandeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Emission d une facture de commande (F31) : recopie des montants figes,
 * ventilation, idempotence et refus d une commande non payee. La continuite de la
 * numerotation, elle, se prouve contre une vraie base ({@code NumerotationFactureIT}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FactureService")
class FactureServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-22T14:30:00Z");

    @Mock private FactureRepository factures;
    @Mock private CommandeRepository commandes;
    @Mock private GenerateurNumeroFacture numeros;

    private FactureService service;

    private final Categorie categorie = new Categorie("PIECES", "Pieces", TypeCategorie.PIECE);
    private final Utilisateur marie = new Utilisateur(
            "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);

    @BeforeEach
    void setUp() {
        service = new FactureService(factures, commandes, numeros,
                Clock.fixed(MAINTENANT, ZoneId.of("Europe/Brussels")));
    }

    private Piece piece(String reference, String prixHtva, String tauxTva) {
        Piece piece = new Piece(categorie, reference, "Piece " + reference, new BigDecimal(prixHtva));
        piece.setTauxTva(new BigDecimal(tauxTva));
        piece.setQuantiteStock(100);
        return piece;
    }

    /** Commande payee portant les lignes indiquees, montants sommes ligne a ligne. */
    private Commande commandePayeeAvec(List<LignePanier> lignes) {
        Panier panier = new Panier(marie);
        BigDecimal htva = lignes.stream().map(LignePanier::totalHtva)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tva = lignes.stream().map(LignePanier::totalTva)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Commande commande = new Commande("CMD-2026-0001", panier.getMembre(),
                htva, tva, htva.add(tva), MAINTENANT.minusSeconds(600));
        commande.confirmerPaiement(MAINTENANT.minusSeconds(60));
        when(commandes.findByReference(commande.getReference())).thenReturn(Optional.of(commande));
        return commande;
    }

    private List<LignePanier> lignes(Object... pieceEtQuantite) {
        Panier panier = new Panier(marie);
        for (int i = 0; i < pieceEtQuantite.length; i += 2) {
            panier.ajouterPiece((Piece) pieceEtQuantite[i], (int) pieceEtQuantite[i + 1]);
        }
        return panier.getLignes();
    }

    private void numeroAttribue(String valeur, short exercice, int sequence) {
        when(numeros.prochain()).thenReturn(new NumeroFacture(exercice, sequence, valeur));
    }

    @Nested
    @DisplayName("emission")
    class Emission {

        @Test
        @DisplayName("recopie les montants figes de la commande et son numero attribue")
        void emetLaFacture() {
            List<LignePanier> lignes = lignes(piece("P-1", "19.99", "21.00"), 2);
            Commande commande = commandePayeeAvec(lignes);
            when(factures.findByCommande(commande)).thenReturn(Optional.empty());
            when(commandes.lignesDe(commande)).thenReturn(lignes);
            numeroAttribue("2026-0001", (short) 2026, 1);
            when(factures.save(any(Facture.class))).thenAnswer(i -> i.getArgument(0));

            Facture facture = service.emettrePourCommande(commande.getReference());

            assertThat(facture.getNumero()).isEqualTo("2026-0001");
            assertThat(facture.getExercice()).isEqualTo((short) 2026);
            assertThat(facture.getSequenceAnnuelle()).isEqualTo(1);
            assertThat(facture.getCommande()).isSameAs(commande);
            assertThat(facture.getMembre()).isSameAs(marie);
            assertThat(facture.getMontantHtva()).isEqualByComparingTo("39.98");
            assertThat(facture.getMontantTva()).isEqualByComparingTo("8.40");
            assertThat(facture.getMontantTvac()).isEqualByComparingTo("48.38");
            // Horodatee par l horloge injectee, jamais par Instant.now().
            assertThat(facture.getDateEmission()).isEqualTo(MAINTENANT);
            // Encaissement deja realise : rien a reclamer, donc pas d echeance.
            assertThat(facture.getDateEcheance()).isNull();
            assertThat(facture.getCheminPdf())
                    .as("Le PDF est fabrique a la premiere demande, pas a l emission")
                    .isNull();
        }

        @Test
        @DisplayName("taux unique : la colonne taux_tva_applique le porte")
        void tauxUniquePorte() {
            List<LignePanier> lignes = lignes(piece("P-1", "19.99", "21.00"), 2);
            Commande commande = commandePayeeAvec(lignes);
            when(factures.findByCommande(commande)).thenReturn(Optional.empty());
            when(commandes.lignesDe(commande)).thenReturn(lignes);
            numeroAttribue("2026-0001", (short) 2026, 1);
            when(factures.save(any(Facture.class))).thenAnswer(i -> i.getArgument(0));

            assertThat(service.emettrePourCommande(commande.getReference()).getTauxTvaApplique())
                    .isEqualByComparingTo("21.00");
        }

        @Test
        @DisplayName("taux mixtes : la colonne vaut NULL, la ventilation fait foi")
        void tauxMixtesSansTauxUnique() {
            List<LignePanier> lignes = lignes(
                    piece("P-1", "100.00", "21.00"), 1,
                    piece("P-2", "50.00", "6.00"), 1);
            Commande commande = commandePayeeAvec(lignes);
            when(factures.findByCommande(commande)).thenReturn(Optional.empty());
            when(commandes.lignesDe(commande)).thenReturn(lignes);
            numeroAttribue("2026-0002", (short) 2026, 2);
            when(factures.save(any(Facture.class))).thenAnswer(i -> i.getArgument(0));

            Facture facture = service.emettrePourCommande(commande.getReference());

            assertThat(facture.getTauxTvaApplique()).isNull();
            assertThat(facture.getMontantTva()).isEqualByComparingTo("24.00");
        }
    }

    @Nested
    @DisplayName("idempotence et refus")
    class IdempotenceEtRefus {

        @Test
        @DisplayName("une commande deja facturee retourne sa facture sans consommer de numero")
        void idempotent() {
            List<LignePanier> lignes = lignes(piece("P-1", "19.99", "21.00"), 2);
            Commande commande = commandePayeeAvec(lignes);
            Facture existante = Facture.pourCommande("2026-0001", (short) 2026, 1,
                    commande, new BigDecimal("21.00"), MAINTENANT);
            when(factures.findByCommande(commande)).thenReturn(Optional.of(existante));

            assertThat(service.emettrePourCommande(commande.getReference())).isSameAs(existante);

            // Le point qui compte : un rejeu ne doit pas bruler un numero, sinon la
            // suite legale se troue a chaque webhook redistribue.
            verify(numeros, never()).prochain();
            verify(factures, never()).save(any());
        }

        @Test
        @DisplayName("une commande non payee ne peut pas etre facturee")
        void refuseUneCommandeNonPayee() {
            Commande enAttente = new Commande("CMD-2026-0002", marie,
                    new BigDecimal("10.00"), new BigDecimal("2.10"), new BigDecimal("12.10"),
                    MAINTENANT);
            when(commandes.findByReference(enAttente.getReference()))
                    .thenReturn(Optional.of(enAttente));

            assertThatThrownBy(() -> service.emettrePourCommande(enAttente.getReference()))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("CMD-2026-0002");

            verify(numeros, never()).prochain();
        }

        @Test
        @DisplayName("une reference de commande inconnue leve une ressource introuvable")
        void refuseUneReferenceInconnue() {
            UUID inconnue = UUID.randomUUID();
            when(commandes.findByReference(inconnue)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.emettrePourCommande(inconnue))
                    .isInstanceOf(RessourceIntrouvableException.class);

            verify(numeros, never()).prochain();
        }
    }
}
