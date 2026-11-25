package be.autoservplus.intervention.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deux clôtures simultanées qui posent la même pièce : aucun décompte ne se perd.
 *
 * <p>Sans transaction de classe : la propriété tient dans deux transactions réellement
 * validées, sur deux connexions. Chaque clôture charge son intervention avec la pièce
 * (JOIN FETCH), cas où un verrou posé par une requête JPQL rendrait un stock périmé.</p>
 */
@SpringBootTest
@DisplayName("Stock décompté par deux clôtures simultanées (intégration)")
class StockClotureConcurrenceIT extends SocleIntegration {

    @Autowired private InterventionService service;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private JdbcTemplate jdbc;

    private <T> T enAdministrateur(Supplier<T> action) {
        SecurityContext contexte = SecurityContextHolder.createEmptyContext();
        contexte.setAuthentication(new TestingAuthenticationToken("admin@garage.be", "n/a",
                "ROLE_ADMINISTRATEUR"));
        SecurityContextHolder.setContext(contexte);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UUID interventionAvecLaPiece(Long membre, Piece piece, String suffixe) {
        Long vehicule = jdbc.queryForObject("INSERT INTO vehicule (reference, membre_id, plaque, marque, "
                        + "modele, motorisation) VALUES (gen_random_uuid(), ?, ?, 'VW', 'Golf', 'DIESEL') "
                        + "RETURNING id", Long.class, membre, "STK-" + suffixe);
        UUID reference = jdbc.queryForObject("INSERT INTO intervention (numero, vehicule_id, statut, "
                        + "montant_devis_htva) VALUES (?, ?, 'PLANIFIEE', 1000) RETURNING reference",
                UUID.class, "INT-STK-" + suffixe, vehicule);
        enAdministrateur(() -> {
            service.demarrer(reference);
            return service.ajouterLignePiece(reference, piece.getReference(), (short) 1);
        });
        return reference;
    }

    @Test
    @DisplayName("deux interventions closes en même temps sur la même pièce retirent chacune la leur")
    void deuxCloturesSimultanees() throws Exception {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        Long membre = utilisateurs.save(MembresDeTest.membre("stock-" + suffixe + "@exemple.be", "$2a$12$h",
                "Dupont", "Marie")).getId();
        Categorie freinage = categories.save(new Categorie("STK-" + suffixe, "Freinage", TypeCategorie.PIECE));
        Piece piece = new Piece(freinage, "STK-" + suffixe, "Plaquettes concurrence", new BigDecimal("10.00"));
        piece.setQuantiteStock(5);
        Piece enregistree = pieces.save(piece);
        UUID premiere = interventionAvecLaPiece(membre, enregistree, suffixe + "-1");
        UUID seconde = interventionAvecLaPiece(membre, enregistree, suffixe + "-2");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        Future<?> a = pool.submit(() -> {
            depart.await(5, TimeUnit.SECONDS);
            return enAdministrateur(() -> service.terminer(premiere));
        });
        Future<?> b = pool.submit(() -> {
            depart.await(5, TimeUnit.SECONDS);
            return enAdministrateur(() -> service.terminer(seconde));
        });
        depart.countDown();
        a.get(30, TimeUnit.SECONDS);
        b.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(jdbc.queryForObject("SELECT quantite_stock FROM piece WHERE id = ?", Integer.class,
                enregistree.getId())).isEqualTo(3);
    }
}
