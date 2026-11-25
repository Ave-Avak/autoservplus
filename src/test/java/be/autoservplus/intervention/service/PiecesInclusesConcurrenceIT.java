package be.autoservplus.intervention.service;

import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.intervention.domain.PieceIncluseRefuseeException;
import be.autoservplus.intervention.repository.InterventionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.domain.LigneIntervention;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deux administrateurs ajustent en même temps les pièces incluses du même dossier : aucune des
 * deux modifications ne se perd, et la version du dossier monte pour chacune. Contre une
 * clôture simultanée, l'une des deux l'emporte sans rien perdre : une modification validée
 * d'abord fait échouer la clôture en conflit, sans décompte, une clôture validée d'abord
 * ferme les pièces incluses.
 *
 * <p>Sans transaction de classe : la propriété tient dans deux transactions réellement
 * validées, sur deux connexions, comme {@code StockClotureConcurrenceIT}.</p>
 */
@SpringBootTest
@DisplayName("Pièces incluses modifiées par deux administrateurs en même temps (intégration)")
class PiecesInclusesConcurrenceIT extends SocleIntegration {

    @Autowired private InterventionService service;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PrestationRepository prestations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private InterventionRepository interventions;
    @Autowired private PlatformTransactionManager transactions;

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

    private Piece piece(Categorie categorie, String suffixe, String libelle) {
        Piece piece = new Piece(categorie, "INCC-" + suffixe + "-" + libelle.hashCode(), libelle, new BigDecimal("10.00"));
        piece.setQuantiteStock(20);
        return pieces.save(piece);
    }

    /** Dossier démarré, une ligne de forfait dont la composition est un filtre. */
    private record Dossier(UUID reference, Long ligne, Piece filtre, Piece joint, Piece bouchon) {
    }

    private Dossier dossier() {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        Long membre = utilisateurs.save(MembresDeTest.membre("incc-" + suffixe + "@exemple.be", "$2a$12$h",
                "Dupont", "Marie")).getId();
        Categorie filtres = categories.save(new Categorie("INCC-P" + suffixe, "Filtres", TypeCategorie.PIECE));
        Categorie atelier = categories.save(new Categorie("INCC-S" + suffixe, "Atelier", TypeCategorie.SERVICE));
        Piece filtre = piece(filtres, suffixe, "Filtre concurrence " + suffixe);
        Piece joint = piece(filtres, suffixe, "Joint concurrence " + suffixe);
        Piece bouchon = piece(filtres, suffixe, "Bouchon concurrence " + suffixe);
        Prestation forfait = new Prestation(atelier, "INCC-" + suffixe, "Vidange concurrence", new BigDecimal("89.00"), 30);
        forfait.fixerComposant(filtre, 1);
        Prestation vidange = prestations.save(forfait);

        Long vehicule = jdbc.queryForObject("INSERT INTO vehicule (reference, membre_id, plaque, marque, "
                        + "modele, motorisation) VALUES (gen_random_uuid(), ?, ?, 'VW', 'Golf', 'DIESEL') "
                        + "RETURNING id", Long.class, membre, "INCC-" + suffixe);
        UUID dossier = jdbc.queryForObject("INSERT INTO intervention (numero, vehicule_id, statut, "
                        + "montant_devis_htva) VALUES (?, ?, 'PLANIFIEE', 5000) RETURNING reference",
                UUID.class, "INT-INCC-" + suffixe, vehicule);
        Long ligne = enAdministrateur(() -> {
            service.demarrer(dossier);
            LigneIntervention creee = service.ajouterLigneMainOeuvre(dossier, vidange.getReference(),
                    BigDecimal.ONE, ChoixPieces.FOURNIES_PAR_GARAGE);
            return creee.getId();
        });
        return new Dossier(dossier, ligne, filtre, joint, bouchon);
    }

    private int stock(Piece piece) {
        return jdbc.queryForObject("SELECT quantite_stock FROM piece WHERE id = ?", Integer.class, piece.getId());
    }

    @Test
    @DisplayName("deux ajouts simultanés sur la même ligne : les deux pièces sont incluses et journalisées")
    void deuxAjoutsSimultanes() throws Exception {
        Dossier d = dossier();
        UUID dossier = d.reference();
        Long ligne = d.ligne();
        Piece filtre = d.filtre();
        Piece joint = d.joint();
        Piece bouchon = d.bouchon();
        long versionAvant = jdbc.queryForObject("SELECT version FROM intervention WHERE reference = ?",
                Long.class, dossier);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        Future<?> a = pool.submit(() -> {
            depart.await(5, TimeUnit.SECONDS);
            return enAdministrateur(() -> {
                service.fixerPieceIncluse(dossier, ligne, joint.getReference(), 1);
                return null;
            });
        });
        Future<?> b = pool.submit(() -> {
            depart.await(5, TimeUnit.SECONDS);
            return enAdministrateur(() -> {
                service.fixerPieceIncluse(dossier, ligne, bouchon.getReference(), 2);
                return null;
            });
        });
        depart.countDown();
        a.get(30, TimeUnit.SECONDS);
        b.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(jdbc.queryForList("""
                SELECT p.libelle || ':' || pi.quantite FROM piece_incluse pi JOIN piece p ON p.id = pi.piece_id
                WHERE pi.ligne_intervention_id = ? ORDER BY p.libelle
                """, String.class, ligne))
                .containsExactly(bouchon.getLibelle() + ":2", filtre.getLibelle() + ":1", joint.getLibelle() + ":1");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM historique_piece_incluse WHERE ligne_intervention_id = ? AND evenement = 'AJOUT'
                """, Integer.class, ligne)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT version FROM intervention WHERE reference = ?", Long.class, dossier))
                .isEqualTo(versionAvant + 2);
    }

    @Test
    @DisplayName("modification validée pendant une clôture déjà commencée : la clôture échoue en conflit, rien n'est décompté")
    void modificationAvantLaCloture() throws Exception {
        Dossier d = dossier();
        CountDownLatch clotureCommencee = new CountDownLatch(1);
        CountDownLatch modificationValidee = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // La clôture lit le dossier, puis attend que la modification soit validée avant de
        // clore : elle tient une version périmée, comme une clôture lancée une seconde trop tôt.
        Future<Throwable> cloture = pool.submit(() -> enAdministrateur(() -> {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(statut -> {
                    interventions.findByReference(d.reference()).orElseThrow();
                    clotureCommencee.countDown();
                    attendre(modificationValidee);
                    service.terminer(d.reference());
                });
                return null;
            } catch (RuntimeException echec) {
                return echec;
            }
        }));
        Future<?> modification = pool.submit(() -> {
            attendre(clotureCommencee);
            enAdministrateur(() -> {
                service.fixerPieceIncluse(d.reference(), d.ligne(), d.filtre().getReference(), 3);
                return null;
            });
            modificationValidee.countDown();
            return null;
        });
        modification.get(30, TimeUnit.SECONDS);
        Throwable echec = cloture.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(echec).isInstanceOf(ConflitConcurrenceException.class);
        assertThat(stock(d.filtre())).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT statut FROM intervention WHERE reference = ?", String.class,
                d.reference())).isEqualTo("EN_COURS");

        enAdministrateur(() -> service.terminer(d.reference()));

        assertThat(stock(d.filtre())).isEqualTo(17);
    }

    @Test
    @DisplayName("clôture validée d'abord : la modification est refusée, l'intervention est close")
    void clotureAvantLaModification() {
        Dossier d = dossier();

        enAdministrateur(() -> service.terminer(d.reference()));

        assertThatThrownBy(() -> enAdministrateur(() -> {
            service.fixerPieceIncluse(d.reference(), d.ligne(), d.joint().getReference(), 1);
            return null;
        }))
                .isInstanceOfSatisfying(PieceIncluseRefuseeException.class,
                        refus -> assertThat(refus.getCleMessage()).isEqualTo("admin.intervention.incluses.erreur.close"));
        assertThat(stock(d.filtre())).isEqualTo(19);
        assertThat(stock(d.joint())).isEqualTo(20);
    }

    @Test
    @DisplayName("dossier tenu au-delà du délai : la modification reçoit le conflit traduit, rien ne change")
    void delaiDepasse() throws Exception {
        Dossier d = dossier();
        CountDownLatch verrouTenu = new CountDownLatch(1);
        CountDownLatch relacher = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);

        Future<?> tenant = pool.submit(() -> {
            new TransactionTemplate(transactions).executeWithoutResult(statut -> {
                interventions.verrouiller(d.reference()).orElseThrow();
                verrouTenu.countDown();
                attendre(relacher);
            });
            return null;
        });
        attendre(verrouTenu);
        Throwable echec = enAdministrateur(() -> {
            try {
                service.fixerPieceIncluse(d.reference(), d.ligne(), d.joint().getReference(), 1);
                return null;
            } catch (RuntimeException conflit) {
                return conflit;
            }
        });
        relacher.countDown();
        tenant.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(echec).isInstanceOf(ConflitConcurrenceException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM piece_incluse WHERE ligne_intervention_id = ?
                """, Integer.class, d.ligne())).isEqualTo(1);
    }

    private static void attendre(CountDownLatch signal) {
        try {
            if (!signal.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("signal attendu en vain");
            }
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruption);
        }
    }
}
