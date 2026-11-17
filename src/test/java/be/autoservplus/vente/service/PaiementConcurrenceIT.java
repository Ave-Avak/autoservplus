package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/**
 * Initiation du paiement sur deux fils réels, et frontière des transactions autour
 * des appels au prestataire.
 *
 * <p>La classe n'est pas transactionnelle, et c'est ce qui donne leur sens aux tests :
 * une transaction de test serait rejointe par {@code initierPaiement} ({@code SUPPORTS})
 * et ferait passer les appels au prestataire pour transactionnels, ou masquerait la
 * course. Les écritures sont validées pour de bon, dans la base propre à la classe.</p>
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Paiement : initiations simultanées et transactions (intégration)")
class PaiementConcurrenceIT extends SocleIntegration {

    private static final String EMAIL = "marie.paiement@exemple.be";

    @Autowired private PaiementService paiementService;
    @Autowired private CommandeService commandeService;
    @Autowired private PanierService panierService;
    @Autowired private CommandeRepository commandes;
    @Autowired private PaiementRepository paiements;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private TransactionTemplate transaction;
    @MockitoSpyBean private PrestatairePaiementFictif prestataire;

    private Piece filtre;

    @BeforeAll
    void catalogue() {
        utilisateurs.save(new Utilisateur(EMAIL, "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE));
        Categorie moteur = categories.save(new Categorie("IT-PCO-MOT", "Moteur", TypeCategorie.PIECE));
        filtre = new Piece(moteur, "IT-PCO-001", "Filtre a huile", new BigDecimal("12.50"));
        filtre.setQuantiteStock(1000);
        filtre = pieces.save(filtre);
    }

    @BeforeEach
    void membre() {
        authentifier();
    }

    @AfterEach
    void sansIdentite() {
        SecurityContextHolder.clearContext();
        reset(prestataire);
    }

    /** Commande neuve en attente de paiement, validée pour de bon. */
    private UUID commande() {
        panierService.ajouterPiece(EMAIL, filtre.getReference(), 1);
        return commandeService.passerCommande(EMAIL, true, false, "127.0.0.1").reference();
    }

    private List<Paiement> tentativesDe(UUID reference) {
        Commande commande = commandes.findByReference(reference).orElseThrow();
        return paiements.findAll().stream()
                .filter(p -> p.getCommande().getId().equals(commande.getId()))
                .toList();
    }

    @Test
    @DisplayName("deux clics simultanés sur « payer » : une seule tentative chez le prestataire")
    void deuxClicsSimultanes() throws Exception {
        UUID reference = commande();

        List<Object> issues = enMemeTemps(
                () -> paiementService.initierPaiement(reference, EMAIL),
                () -> paiementService.initierPaiement(reference, EMAIL));

        List<Paiement> tentatives = tentativesDe(reference);
        assertThat(tentatives).hasSize(1);
        String page = "/paiement-fictif/" + tentatives.get(0).getReferenceMollie();
        // Le second geste voit la tentative du premier : sous le verrou, ou déjà validée
        // à sa relecture, auquel cas il retrouve la même page.
        assertThat(issues).allSatisfy(issue -> assertThat(issue).isIn(
                DepartPaiement.vers(page),
                DepartPaiement.sans(DepartPaiement.Issue.TENTATIVE_CONCURRENTE)));
        assertThat(issues).contains(DepartPaiement.vers(page));
    }

    @Test
    @DisplayName("tentative insérée hors du verrou : l'index refuse la seconde, message et non 500")
    void filetDeLIndex() throws Exception {
        UUID reference = commande();
        CountDownLatch reverifiee = new CountDownLatch(1);
        CountDownLatch inseree = new CountDownLatch(1);
        // Le membre a passé la revérification sous verrou et appelle le prestataire :
        // l'insertion concurrente est alors validée, avant la sienne.
        doAnswer(appel -> {
            reverifiee.countDown();
            attendre(inseree);
            return appel.callRealMethod();
        }).when(prestataire).creerPaiement(any());

        List<Object> issues = enParallele(
                () -> paiementService.initierPaiement(reference, EMAIL),
                () -> {
                    attendre(reverifiee);
                    try {
                        return transaction.execute(statut -> {
                            Commande commande = commandes.findByReference(reference).orElseThrow();
                            return paiements.saveAndFlush(new Paiement(commande,
                                    commande.getMontantTvac(), java.time.Instant.now()));
                        });
                    } finally {
                        inseree.countDown();
                    }
                });

        assertThat(issues.get(0))
                .isEqualTo(DepartPaiement.sans(DepartPaiement.Issue.TENTATIVE_CONCURRENTE));
        assertThat(tentativesDe(reference)).singleElement()
                .extracting(Paiement::getStatut).isEqualTo(StatutPaiement.INITIE);
    }

    @Test
    @DisplayName("la relecture se fait hors transaction, la création sous le verrou de la commande")
    void appelsAuPrestataireEtTransactions() {
        UUID reference = commande();
        List<Boolean> pendantLaCreation = new CopyOnWriteArrayList<>();
        List<Boolean> pendantLaRelecture = new CopyOnWriteArrayList<>();
        doAnswer(appel -> {
            pendantLaCreation.add(TransactionSynchronizationManager.isActualTransactionActive());
            return appel.callRealMethod();
        }).when(prestataire).creerPaiement(any());
        doAnswer(appel -> {
            pendantLaRelecture.add(TransactionSynchronizationManager.isActualTransactionActive());
            return appel.callRealMethod();
        }).when(prestataire).lireEtat(anyString());

        DepartPaiement premier = paiementService.initierPaiement(reference, EMAIL);
        DepartPaiement reprise = paiementService.initierPaiement(reference, EMAIL);

        assertThat(reprise).isEqualTo(premier);
        assertThat(pendantLaRelecture).as("relecture de la tentative ouverte").containsExactly(false);
        // La création reste dans la transaction qui verrouille la commande et revérifie
        // l'absence de tentative ouverte : c'est elle qui départage deux clics.
        assertThat(pendantLaCreation).as("création de la tentative").containsExactly(true);
    }

    // --- fils ------------------------------------------------------------------------

    @SafeVarargs
    private List<Object> enMemeTemps(Callable<?>... actions) throws Exception {
        CountDownLatch depart = new CountDownLatch(1);
        List<Callable<?>> retenues = new ArrayList<>();
        for (Callable<?> action : actions) {
            retenues.add(() -> {
                attendre(depart);
                return action.call();
            });
        }
        return executer(retenues, depart);
    }

    @SafeVarargs
    private List<Object> enParallele(Callable<?>... actions) throws Exception {
        return executer(List.of(actions), null);
    }

    /** Chaque fil reçoit l'identité du membre : le contexte de sécurité ne passe pas d'un fil à l'autre. */
    private List<Object> executer(List<Callable<?>> actions, CountDownLatch depart) throws Exception {
        ExecutorService fils = Executors.newFixedThreadPool(actions.size());
        try {
            List<Future<?>> futurs = new ArrayList<>();
            for (Callable<?> action : actions) {
                futurs.add(fils.submit(() -> {
                    authentifier();
                    try {
                        return action.call();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            if (depart != null) {
                depart.countDown();
            }
            List<Object> issues = new ArrayList<>();
            for (Future<?> futur : futurs) {
                try {
                    issues.add(futur.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException echec) {
                    issues.add(echec.getCause());
                }
            }
            return issues;
        } finally {
            fils.shutdownNow();
        }
    }

    private static void authentifier() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(EMAIL, null,
                        List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
    }

    private static void attendre(CountDownLatch loquet) {
        try {
            if (!loquet.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Loquet jamais ouvert");
            }
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruption);
        }
    }
}
