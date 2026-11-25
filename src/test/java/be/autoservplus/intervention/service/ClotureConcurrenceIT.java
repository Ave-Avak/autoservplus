package be.autoservplus.intervention.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.intervention.repository.InterventionRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clôture d'une intervention contre une écriture concurrente du même dossier : jamais de page
 * d'erreur, jamais de décompte sur des lignes périmées.
 *
 * <p>La clôture verrouille l'intervention avant toute lecture, puis décompte les pièces : une
 * écriture qui arrive pendant la clôture échoue en conflit traduit, une écriture validée avant
 * fait échouer une clôture qui avait lu le dossier plus tôt, et un verrou tenu au-delà du délai
 * donne le conflit traduit. Sans transaction de classe : deux transactions réellement validées,
 * sur deux connexions, comme {@code StockClotureConcurrenceIT}.</p>
 */
@SpringBootTest
@DisplayName("Clôture contre une écriture concurrente du dossier (intégration)")
class ClotureConcurrenceIT extends SocleIntegration {

    @Autowired private InterventionService service;
    @Autowired private InterventionRepository interventions;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    private record Dossier(UUID reference, Piece posee, Piece ajoutee) {
    }

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

    /** Dossier démarré qui pose une pièce, et une seconde pièce à ajouter pendant la clôture. */
    private Dossier dossier() {
        String suffixe = UUID.randomUUID().toString().substring(0, 8);
        Long membre = utilisateurs.save(MembresDeTest.membre("clo-" + suffixe + "@exemple.be", "$2a$12$h",
                "Dupont", "Marie")).getId();
        Categorie freinage = categories.save(new Categorie("CLO-" + suffixe, "Freinage", TypeCategorie.PIECE));
        Piece posee = piece(freinage, "CLO-A-" + suffixe, "Plaquettes clôture " + suffixe);
        Piece ajoutee = piece(freinage, "CLO-B-" + suffixe, "Disques clôture " + suffixe);
        Long vehicule = jdbc.queryForObject("INSERT INTO vehicule (reference, membre_id, plaque, marque, "
                        + "modele, motorisation) VALUES (gen_random_uuid(), ?, ?, 'VW', 'Golf', 'DIESEL') "
                        + "RETURNING id", Long.class, membre, "CLO-" + suffixe);
        UUID reference = jdbc.queryForObject("INSERT INTO intervention (numero, vehicule_id, statut, "
                        + "montant_devis_htva) VALUES (?, ?, 'PLANIFIEE', 5000) RETURNING reference",
                UUID.class, "INT-CLO-" + suffixe, vehicule);
        enAdministrateur(() -> {
            service.demarrer(reference);
            return service.ajouterLignePiece(reference, posee.getReference(), 1);
        });
        return new Dossier(reference, posee, ajoutee);
    }

    private Piece piece(Categorie categorie, String code, String libelle) {
        Piece piece = new Piece(categorie, code, libelle, new BigDecimal("10.00"));
        piece.setQuantiteStock(10);
        return pieces.save(piece);
    }

    private int stock(Piece piece) {
        return jdbc.queryForObject("SELECT quantite_stock FROM piece WHERE id = ?", Integer.class, piece.getId());
    }

    private int lignes(UUID dossier) {
        return jdbc.queryForObject("SELECT count(*) FROM ligne_intervention l JOIN intervention i "
                + "ON i.id = l.intervention_id WHERE i.reference = ?", Integer.class, dossier);
    }

    private String statut(UUID dossier) {
        return jdbc.queryForObject("SELECT statut FROM intervention WHERE reference = ?", String.class, dossier);
    }

    /** Attend qu'une autre connexion soit bloquée sur un verrou, au plus dix secondes. */
    private void attendreUneConnexionBloquee() {
        Instant limite = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(limite)) {
            Integer bloquees = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE wait_event_type = 'Lock' AND datname = current_database()", Integer.class);
            if (bloquees != null && bloquees > 0) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("aucune connexion bloquée sur un verrou");
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

    @Test
    @DisplayName("ajout de ligne pendant la clôture : il attend, trouve l'intervention close et est refusé")
    void ajoutPendantLaCloture() throws Exception {
        Dossier d = dossier();
        CountDownLatch clotureFaite = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // La clôture se fait et garde son verrou jusqu'à ce que l'ajout attende la ligne.
        Future<?> cloture = pool.submit(() -> enAdministrateur(() -> {
            new TransactionTemplate(transactions).executeWithoutResult(statut -> {
                service.terminer(d.reference());
                clotureFaite.countDown();
                attendreUneConnexionBloquee();
            });
            return null;
        }));
        Future<Throwable> ajout = pool.submit(() -> {
            attendre(clotureFaite);
            return enAdministrateur(() -> {
                try {
                    service.ajouterLignePiece(d.reference(), d.ajoutee().getReference(), 2);
                    return null;
                } catch (RuntimeException echec) {
                    return echec;
                }
            });
        });
        cloture.get(30, TimeUnit.SECONDS);
        Throwable echec = ajout.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // Refus de la machine à états (RM-14), que l'écran affiche comme un état qui ne permet
        // plus l'ajout : ni page d'erreur, ni ligne sur une intervention close.
        assertThat(echec).isInstanceOf(IllegalStateException.class);
        assertThat(statut(d.reference())).isEqualTo("TERMINEE");
        assertThat(lignes(d.reference())).isEqualTo(1);
        assertThat(stock(d.posee())).isEqualTo(9);
        assertThat(stock(d.ajoutee())).isEqualTo(10);
    }

    @Test
    @DisplayName("ajout validé pendant une clôture déjà commencée : la clôture échoue en conflit, rien n'est décompté")
    void ajoutAvantLaCloture() throws Exception {
        Dossier d = dossier();
        CountDownLatch clotureCommencee = new CountDownLatch(1);
        CountDownLatch ajoutValide = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        // La clôture lit le dossier, puis attend que l'ajout soit validé : elle tient une version
        // périmée, comme une clôture lancée une seconde trop tôt.
        Future<Throwable> cloture = pool.submit(() -> enAdministrateur(() -> {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(statut -> {
                    interventions.findByReference(d.reference()).orElseThrow();
                    clotureCommencee.countDown();
                    attendre(ajoutValide);
                    service.terminer(d.reference());
                });
                return null;
            } catch (RuntimeException echec) {
                return echec;
            }
        }));
        Future<?> ajout = pool.submit(() -> {
            attendre(clotureCommencee);
            enAdministrateur(() -> service.ajouterLignePiece(d.reference(), d.ajoutee().getReference(), 2));
            ajoutValide.countDown();
            return null;
        });
        ajout.get(30, TimeUnit.SECONDS);
        Throwable echec = cloture.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(echec).isInstanceOf(ConflitConcurrenceException.class);
        assertThat(statut(d.reference())).isEqualTo("EN_COURS");
        assertThat(stock(d.posee())).isEqualTo(10);
        assertThat(stock(d.ajoutee())).isEqualTo(10);

        enAdministrateur(() -> service.terminer(d.reference()));

        assertThat(stock(d.posee())).isEqualTo(9);
        assertThat(stock(d.ajoutee())).isEqualTo(8);
    }

    @Test
    @DisplayName("verrou tenu au-delà du délai : la clôture reçoit le conflit traduit, rien n'est décompté")
    void delaiDepasse() throws Exception {
        Dossier d = dossier();
        CountDownLatch verrouTenu = new CountDownLatch(1);
        CountDownLatch relacher = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> tenant = pool.submit(() -> {
            new TransactionTemplate(transactions).executeWithoutResult(statut -> {
                interventions.verrouiller(d.reference()).orElseThrow();
                verrouTenu.countDown();
                attendre(relacher);
            });
            return null;
        });
        attendre(verrouTenu);
        Instant debut = Instant.now();
        Throwable echec = enAdministrateur(() -> {
            try {
                service.terminer(d.reference());
                return null;
            } catch (RuntimeException conflit) {
                return conflit;
            }
        });
        Duration attente = Duration.between(debut, Instant.now());
        relacher.countDown();
        tenant.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(echec).isInstanceOf(ConflitConcurrenceException.class);
        assertThat(attente).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(15));
        assertThat(statut(d.reference())).isEqualTo("EN_COURS");
        assertThat(stock(d.posee())).isEqualTo(10);
    }
}
