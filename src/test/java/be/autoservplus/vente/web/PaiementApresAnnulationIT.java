package be.autoservplus.vente.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.communication.service.Courriel;
import be.autoservplus.communication.service.TransportCourriel;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.notification.domain.Notification;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.repository.NotificationRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutCommande;
import be.autoservplus.vente.domain.StatutPaiement;
import be.autoservplus.vente.repository.CommandeRepository;
import be.autoservplus.vente.repository.PaiementRepository;
import be.autoservplus.vente.service.CommandeService;
import be.autoservplus.vente.service.ExpirationCommandesJob;
import be.autoservplus.vente.service.PaiementService;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.service.PrestatairePaiementFictif;
import be.autoservplus.vente.service.RemboursementApresAnnulationService;
import be.autoservplus.common.exception.RefusTraduit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un paiement qui aboutit sur une commande déjà annulée, de la notification du
 * prestataire au remboursement par un administrateur.
 *
 * <p>La classe n'est pas transactionnelle : les avis partent d'écouteurs exécutés après
 * la validation. Le transport des courriels est espionné, pas remplacé.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Paiement reçu sur une commande annulée (intégration)")
class PaiementApresAnnulationIT extends SocleIntegration {

    private static final String MEMBRE = "lies.tardif@exemple.be";
    private static final String ADMIN = "tardif-admin@exemple.be";
    private static final String ECRAN = "/admin/paiements-a-rembourser";

    @Autowired private MockMvc mvc;
    @Autowired private ExpirationCommandesJob job;
    @Autowired private PaiementService paiementService;
    @Autowired private CommandeService commandeService;
    @Autowired private PanierService panierService;
    @MockitoSpyBean private PrestatairePaiementFictif prestataire;
    @Autowired private RemboursementApresAnnulationService remboursements;
    @Autowired private CommandeRepository commandes;
    @Autowired private PaiementRepository paiements;
    @Autowired private NotificationRepository notifications;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private TransportCourriel transport;

    private Piece filtre;
    private Utilisateur administrateur;

    @BeforeAll
    void donnees() {
        Utilisateur lies = MembresDeTest.membre(MEMBRE, "$2a$12$h", "Peeters", "Lies");
        lies.setLangue(Langue.nl);
        utilisateurs.save(lies);
        Utilisateur admin = new Utilisateur(ADMIN, "$2a$12$h", "Garage", "Admin",
                TypeUtilisateur.ADMINISTRATEUR);
        admin.setStatut(StatutUtilisateur.ACTIF);
        administrateur = utilisateurs.save(admin);
        Categorie moteur = categories.save(new Categorie("IT-TAR-MOT", "Moteur", TypeCategorie.PIECE));
        filtre = new Piece(moteur, "IT-TAR-001", "Filtre à huile", new BigDecimal("12.50"));
        filtre.setQuantiteStock(10);
        filtre = pieces.save(filtre);
    }

    @AfterEach
    void sansIdentite() {
        SecurityContextHolder.clearContext();
        reset(prestataire);
    }

    private static void enAdministrateur() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ADMIN, "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATEUR"))));
    }

    /**
     * Commande annulée à l'échéance, dont la tentative a été close de notre côté, puis
     * réglée chez le prestataire : le cas que le job évite désormais, et dont il reste
     * les tentatives closes avant lui.
     */
    private String paiementRegleApresAnnulation() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                MEMBRE, "n/a", List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
        panierService.ajouterPiece(MEMBRE, filtre.getReference(), 1);
        UUID reference = commandeService.passerCommande(MEMBRE, true, false, "127.0.0.1").reference();
        String page = paiementService.initierPaiement(reference, MEMBRE).urlRedirection();
        String chezLePrestataire = page.substring(page.lastIndexOf('/') + 1);
        jdbc.update("UPDATE commande SET date_commande = now() - interval '31 minutes' "
                + "WHERE reference = ?", reference);
        job.annulerLesCommandesExpirees();
        assertThat(commandes.findByReference(reference).orElseThrow().getStatut())
                .isEqualTo(StatutCommande.ANNULEE);
        SecurityContextHolder.clearContext();

        prestataire.programmerStatut(chezLePrestataire, StatutPaiement.REUSSI);
        return chezLePrestataire;
    }

    private Paiement relu(String chezLePrestataire) {
        return paiements.findByReferenceMollie(chezLePrestataire).orElseThrow();
    }

    private List<Courriel> courriels(String type) {
        ArgumentCaptor<Courriel> remis = ArgumentCaptor.forClass(Courriel.class);
        verify(transport, atLeast(0)).transmettre(remis.capture());
        return remis.getAllValues().stream().filter(courriel -> type.equals(courriel.type())).toList();
    }

    private long avisAuxAdministrateurs(String numeroCommande) {
        return notifications.findByMembreOrderByDateEnvoiDescIdDesc(administrateur).stream()
                .filter(n -> n.getType() == TypeNotification.PAIEMENT_A_REMBOURSER)
                .map(Notification::getCorps)
                .filter(numeroCommande::equals)
                .count();
    }

    @Test
    @DisplayName("de la notification au remboursement : enregistré, signalé une fois, remboursé et tracé")
    void parcoursComplet() throws Exception {
        String chezLePrestataire = paiementRegleApresAnnulation();
        int avisMembreAvant = courriels("paiement-apres-annulation").size();
        int avisGarageAvant = courriels("paiement-a-rembourser").size();

        mvc.perform(post("/webhooks/paiement").with(anonymous()).param("id", chezLePrestataire))
                .andExpect(status().isOk());
        // Le prestataire rejoue ses notifications : rien ne doit partir deux fois.
        mvc.perform(post("/webhooks/paiement").with(anonymous()).param("id", chezLePrestataire))
                .andExpect(status().isOk());

        Paiement paiement = relu(chezLePrestataire);
        Commande commande = paiement.getCommande();
        assertThat(paiement.getStatut()).isEqualTo(StatutPaiement.REUSSI);
        assertThat(paiement.estARembourser()).isTrue();
        assertThat(commande.getStatut()).as("la commande ne ressuscite pas")
                .isEqualTo(StatutCommande.ANNULEE);
        assertThat(pieces.findById(filtre.getId()).orElseThrow().getQuantiteStock())
                .as("aucun stock décrémenté").isEqualTo(10);

        List<Courriel> auMembre = courriels("paiement-apres-annulation");
        assertThat(auMembre).hasSize(avisMembreAvant + 1);
        Courriel avis = auMembre.get(auMembre.size() - 1);
        assertThat(avis.adresse()).isEqualTo(MEMBRE);
        assertThat(avis.sujet()).as("dans la langue du membre")
                .isEqualTo("Betaling ontvangen na de annulering van uw bestelling");
        assertThat(avis.corps()).contains(commande.getNumero()).contains("terugbetaald");
        assertThat(courriels("paiement-a-rembourser")).hasSize(avisGarageAvant + 1);
        assertThat(avisAuxAdministrateurs(commande.getNumero())).isEqualTo(1);

        // La liste ne rembourse pas : elle mène à la page qui demande confirmation.
        mvc.perform(get(ECRAN).with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(commande.getNumero())))
                .andExpect(content().string(containsString(ECRAN + "/" + paiement.getReference() + "\"")))
                .andExpect(content().string(not(containsString("/rembourser"))));
        mvc.perform(get(ECRAN + "/{ref}", paiement.getReference())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirmer le remboursement de")))
                .andExpect(content().string(containsString(commande.getNumero())))
                .andExpect(content().string(containsString(
                        ECRAN + "/" + paiement.getReference() + "/rembourser")));

        // La page de confirmation, point de chute du retour de paiement, et la liste.
        mvc.perform(get("/commande/{ref}/confirmation", commande.getReference())
                        .with(user(MEMBRE).roles("MEMBRE")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Commande annulée</h1>")))
                .andExpect(content().string(containsString("son délai de paiement est dépassé")))
                .andExpect(content().string(containsString("ce montant vous sera remboursé")))
                .andExpect(content().string(not(containsString("Commande enregistrée"))))
                .andExpect(content().string(not(containsString("Montant à régler"))))
                .andExpect(content().string(not(containsString("chou"))));
        mvc.perform(get("/commandes").with(user(MEMBRE).roles("MEMBRE")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">Remboursement en cours<")));

        // Les deux détails disent le paiement reçu, sans « payée » ni « échoué ».
        mvc.perform(get("/admin/commandes/{ref}", commande.getReference())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Encaissé après annulation le")))
                .andExpect(content().string(containsString("Non communiqué")))
                .andExpect(content().string(not(containsString("Aucun paiement abouti"))))
                .andExpect(content().string(not(containsString("Payée le"))))
                .andExpect(content().string(not(containsString("Remboursé le"))))
                .andExpect(content().string(not(containsString("chou"))));
        mvc.perform(get("/commandes/{ref}", commande.getReference())
                        .with(user(MEMBRE).roles("MEMBRE")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paiement reçu après l&#39;annulation")))
                .andExpect(content().string(containsString("ce montant vous sera remboursé")))
                .andExpect(content().string(not(containsString("Payée le"))))
                .andExpect(content().string(not(containsString("chou"))));

        mvc.perform(post(ECRAN + "/{ref}/rembourser", paiement.getReference()).with(csrf())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(redirectedUrl(ECRAN))
                .andExpect(flash().attributeExists("message"));

        Paiement rembourse = relu(chezLePrestataire);
        assertThat(rembourse.getStatut()).isEqualTo(StatutPaiement.REMBOURSE);
        assertThat(rembourse.getRemboursePar()).isEqualTo(ADMIN);
        assertThat(rembourse.getRembourseLe()).isNotNull();
        assertThat(rembourse.getReferenceRemboursement()).startsWith("re_fictif_");
        assertThat(prestataire.lireEtat(chezLePrestataire).statut())
                .as("remboursé chez le prestataire").isEqualTo(StatutPaiement.REMBOURSE);
        List<Courriel> remboursements = courriels("remboursement-apres-annulation");
        assertThat(remboursements).isNotEmpty();
        assertThat(remboursements.get(remboursements.size() - 1).sujet())
                .isEqualTo("Uw betaling is terugbetaald");

        mvc.perform(get(ECRAN).with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("par " + ADMIN)))
                .andExpect(content().string(not(containsString(
                        ECRAN + "/" + paiement.getReference() + "\""))));
        // La page de confirmation d'un paiement déjà remboursé renvoie à la liste.
        mvc.perform(get(ECRAN + "/{ref}", paiement.getReference())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(redirectedUrl(ECRAN))
                .andExpect(flash().attribute("erreur", "Ce paiement est déjà remboursé."));

        // Journal d'audit : auteur, commande, montant et référence du remboursement.
        mvc.perform(get("/admin/journal").param("type", "REMBOURSEMENT")
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(commande.getNumero())))
                .andExpect(content().string(containsString("Admin Garage")))
                .andExpect(content().string(containsString("Paiement reçu après annulation remboursé")))
                .andExpect(content().string(containsString(rembourse.getReferenceRemboursement())));

        mvc.perform(get("/admin/commandes/{ref}", commande.getReference())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(content().string(containsString("Encaissé après annulation le")))
                .andExpect(content().string(containsString("Remboursé le")))
                .andExpect(content().string(not(containsString("Payée le"))))
                .andExpect(content().string(not(containsString("chou"))));
        mvc.perform(get("/commande/{ref}/confirmation", commande.getReference())
                        .with(user(MEMBRE).roles("MEMBRE")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Commande annulée</h1>")))
                .andExpect(content().string(containsString("a été remboursé sur le compte")))
                .andExpect(content().string(containsString("Remboursé le")))
                .andExpect(content().string(not(containsString("Montant à régler"))));
        mvc.perform(get("/commande/{ref}/confirmation", commande.getReference())
                        .with(user(MEMBRE).roles("MEMBRE")).locale(new Locale("nl")))
                .andExpect(content().string(containsString("<h1>Bestelling geannuleerd</h1>")))
                .andExpect(content().string(containsString("de betalingstermijn is verstreken")))
                .andExpect(content().string(containsString("is terugbetaald op de rekening")));
        mvc.perform(get("/commande/{ref}/confirmation", commande.getReference())
                        .with(user(MEMBRE).roles("MEMBRE")).locale(Locale.ENGLISH))
                .andExpect(content().string(containsString("<h1>Order cancelled</h1>")))
                .andExpect(content().string(containsString("its payment deadline has passed")))
                .andExpect(content().string(containsString("has been refunded")));
        mvc.perform(get("/commandes").with(user(MEMBRE).roles("MEMBRE")).locale(Locale.FRENCH))
                .andExpect(content().string(containsString(">Remboursé<")));

        mvc.perform(get("/commandes/{ref}", commande.getReference())
                        .with(user(MEMBRE).roles("MEMBRE")).locale(new Locale("nl")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Betaling ontvangen na de annulering")))
                .andExpect(content().string(containsString("is terugbetaald op de rekening")))
                .andExpect(content().string(not(containsString("mislukt"))));
        mvc.perform(get("/commandes/{ref}", commande.getReference())
                        .with(user(MEMBRE).roles("MEMBRE")).locale(Locale.ENGLISH))
                .andExpect(content().string(containsString("Payment received after cancellation")))
                .andExpect(content().string(containsString("has been refunded")))
                .andExpect(content().string(not(containsString("failed"))));

        // Second clic, ou seconde fenêtre : refusé, et rien ne repart chez le prestataire.
        mvc.perform(post(ECRAN + "/{ref}/rembourser", paiement.getReference()).with(csrf())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(redirectedUrl(ECRAN))
                .andExpect(flash().attribute("erreur", "Ce paiement est déjà remboursé."));
        assertThat(courriels("remboursement-apres-annulation")).hasSameSizeAs(remboursements);
    }

    @Test
    @DisplayName("un paiement ordinaire ne se rembourse pas par cet écran, et un membre n'y entre pas")
    void gardes() throws Exception {
        String chezLePrestataire = paiementRegleApresAnnulation();
        UUID reference = relu(chezLePrestataire).getReference();

        // Pas encore constaté : ce n'est pas un paiement reçu après annulation.
        mvc.perform(post(ECRAN + "/{ref}/rembourser", reference).with(csrf())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")))
                .andExpect(status().isNotFound());
        mvc.perform(post(ECRAN + "/{ref}/rembourser", UUID.randomUUID()).with(csrf())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")))
                .andExpect(status().isNotFound());

        mvc.perform(get(ECRAN).with(user(MEMBRE).roles("MEMBRE")))
                .andExpect(status().isForbidden());
        mvc.perform(post(ECRAN + "/{ref}/rembourser", reference).with(csrf())
                        .with(user(MEMBRE).roles("MEMBRE")))
                .andExpect(status().isForbidden());
        assertThat(relu(chezLePrestataire).getStatut()).isEqualTo(StatutPaiement.ECHOUE);
    }

    @Test
    @DisplayName("prestataire en panne au remboursement : message, et le paiement reste à rembourser")
    void panneAuRemboursement() throws Exception {
        String chezLePrestataire = paiementRegleApresAnnulation();
        paiementService.traiterNotification(chezLePrestataire);
        UUID reference = relu(chezLePrestataire).getReference();
        prestataire.programmerPanne(chezLePrestataire, true);

        mvc.perform(post(ECRAN + "/{ref}/rembourser", reference).with(csrf())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(redirectedUrl(ECRAN))
                .andExpect(flash().attribute("erreur", containsString("Rien n'a été remboursé")));

        assertThat(relu(chezLePrestataire).estARembourser()).isTrue();
        assertThat(relu(chezLePrestataire).getRemboursePar()).isNull();

        prestataire.programmerPanne(chezLePrestataire, false);
        mvc.perform(post(ECRAN + "/{ref}/rembourser", reference).with(csrf())
                        .with(user(ADMIN).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(flash().attributeExists("message"));
        assertThat(relu(chezLePrestataire).getStatut()).isEqualTo(StatutPaiement.REMBOURSE);
    }

    /**
     * Double clic, ou deux administrateurs sur la même ligne. La première demande est
     * retenue dans l'appel au prestataire, la seconde part pendant ce temps : sans le
     * verrou, elle lirait un paiement encore à rembourser et appellerait le prestataire
     * à son tour.
     */
    @Test
    @DisplayName("commande annulée sans paiement reçu : la confirmation dit l'annulation, rien à régler ni à rembourser")
    void confirmationAnnuleeSansPaiement() throws Exception {
        // Le prestataire n'a encore rien notifié : la commande est annulée, sans plus.
        String chezLePrestataire = paiementRegleApresAnnulation();
        UUID reference = jdbc.queryForObject("""
                SELECT c.reference FROM commande c JOIN paiement p ON p.commande_id = c.id
                WHERE p.reference_mollie = ?
                """, UUID.class, chezLePrestataire);

        mvc.perform(get("/commande/{ref}/confirmation", reference)
                        .with(user(MEMBRE).roles("MEMBRE")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Commande annulée</h1>")))
                .andExpect(content().string(containsString("<title>Commande annulée | AutoServ+</title>")))
                .andExpect(content().string(containsString("son délai de paiement est dépassé")))
                .andExpect(content().string(not(containsString("Commande enregistrée"))))
                .andExpect(content().string(not(containsString("Montant à régler"))))
                .andExpect(content().string(not(containsString("remboursé"))))
                .andExpect(content().string(not(containsString("Procéder au paiement"))));
    }

    @Test
    @DisplayName("deux demandes simultanées : un seul appel au prestataire, la seconde est refusée")
    void deuxRemboursementsSimultanes() throws Exception {
        String chezLePrestataire = paiementRegleApresAnnulation();
        paiementService.traiterNotification(chezLePrestataire);
        UUID reference = relu(chezLePrestataire).getReference();
        int avisAvant = courriels("remboursement-apres-annulation").size();

        CountDownLatch dansLAppel = new CountDownLatch(1);
        CountDownLatch secondePartie = new CountDownLatch(1);
        doAnswer(appel -> {
            dansLAppel.countDown();
            // Laisse à la seconde demande le temps d'atteindre le verrou.
            secondePartie.await(5, TimeUnit.SECONDS);
            Thread.sleep(400);
            return appel.callRealMethod();
        }).when(prestataire).rembourser(any());

        ExecutorService fils = Executors.newFixedThreadPool(2);
        try {
            Future<Object> premiere = fils.submit(() -> demander(reference));
            assertThat(dansLAppel.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Object> seconde = fils.submit(() -> {
                secondePartie.countDown();
                return demander(reference);
            });

            assertThat(premiere.get(20, TimeUnit.SECONDS)).isEqualTo("rembourse");
            assertThat(seconde.get(20, TimeUnit.SECONDS)).isInstanceOf(RefusTraduit.class);
        } finally {
            fils.shutdownNow();
        }

        verify(prestataire, times(1)).rembourser(any());
        assertThat(relu(chezLePrestataire).getStatut()).isEqualTo(StatutPaiement.REMBOURSE);
        assertThat(courriels("remboursement-apres-annulation")).hasSize(avisAvant + 1);
    }

    private Object demander(UUID reference) {
        enAdministrateur();
        try {
            remboursements.rembourser(reference, ADMIN);
            return "rembourse";
        } catch (RuntimeException refus) {
            return refus;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
