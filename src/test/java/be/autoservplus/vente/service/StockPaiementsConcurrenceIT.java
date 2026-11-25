package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deux paiements confirmés en même temps sur la même pièce : aucun décompte ne se perd.
 *
 * <p>Sans transaction de classe : deux transactions réellement validées, sur deux
 * connexions. Chaque fil ouvre sa transaction, y charge la pièce, attend l'autre, puis
 * confirme son paiement dans cette même transaction. C'est la situation de production :
 * la confirmation lit les lignes de la commande avec leurs pièces avant de décompter. Le
 * loquet ne fait que garantir que les deux lectures précèdent la première validation,
 * fenêtre que deux notifications du prestataire peuvent ouvrir sans lui.</p>
 */
@SpringBootTest
@DisplayName("Stock décompté par deux paiements simultanés (intégration)")
class StockPaiementsConcurrenceIT extends SocleIntegration {

    @Autowired private PaiementService paiementService;
    @Autowired private CommandeService commandeService;
    @Autowired private PanierService panierService;
    @Autowired private CommandeRepository commandes;
    @Autowired private PaiementRepository paiements;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private TransactionTemplate transaction;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void sansIdentite() {
        SecurityContextHolder.clearContext();
    }

    private static void authentifier(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(email, null,
                        List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
    }

    /** Commande d'une pièce, paiement initié chez le prestataire fictif : rend sa référence. */
    private String paiementOuvert(String email, Piece piece) {
        panierService.ajouterPiece(email, piece.getReference(), 1);
        UUID commande = commandeService.passerCommande(email, true, false, "127.0.0.1").reference();
        paiementService.initierPaiement(commande, email);
        Long idCommande = commandes.findByReference(commande).orElseThrow().getId();
        return paiements.findAll().stream()
                .filter(p -> p.getCommande().getId().equals(idCommande))
                .map(Paiement::getReferenceMollie)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("deux paiements confirmés en même temps sur la même pièce retirent chacun la leur")
    void deuxPaiementsSimultanes() throws Exception {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        String email = "stock-vente-" + suffixe + "@exemple.be";
        utilisateurs.save(MembresDeTest.membre(email, "$2a$12$h", "Dupont", "Marie"));
        authentifier(email);
        Categorie moteur = categories.save(new Categorie("SPC-" + suffixe, "Moteur", TypeCategorie.PIECE));
        Piece filtre = new Piece(moteur, "SPC-" + suffixe, "Filtre concurrence", new BigDecimal("12.50"));
        filtre.setQuantiteStock(5);
        Long idPiece = pieces.save(filtre).getId();
        String premier = paiementOuvert(email, filtre);
        String second = paiementOuvert(email, filtre);

        CountDownLatch lues = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> a = pool.submit(() -> confirmerApresLecture(premier, idPiece, lues));
        Future<?> b = pool.submit(() -> confirmerApresLecture(second, idPiece, lues));
        a.get(30, TimeUnit.SECONDS);
        b.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(jdbc.queryForObject("SELECT quantite_stock FROM piece WHERE id = ?", Integer.class, idPiece))
                .as("cinq en stock, deux commandes d'une pièce payées")
                .isEqualTo(3);
    }

    private Object confirmerApresLecture(String referencePrestataire, Long idPiece, CountDownLatch lues) {
        return transaction.execute(statut -> {
            pieces.findById(idPiece).orElseThrow();
            lues.countDown();
            try {
                if (!lues.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("L'autre fil n'a jamais lu la pièce.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return paiementService.constater(referencePrestataire,
                    new EtatPaiement(StatutPaiement.REUSSI, "creditcard", null));
        });
    }
}
