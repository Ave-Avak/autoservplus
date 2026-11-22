package be.autoservplus.vente.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.MotifAnnulationCommande;
import be.autoservplus.vente.domain.Paiement;
import be.autoservplus.vente.domain.StatutCommande;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le job RM-21 contre le prestataire fictif et la base réelle : chaque cas que le
 * prestataire peut rapporter à l'échéance, puis ce que le membre peut encore faire.
 *
 * <p>La classe n'est pas transactionnelle : le job ouvre ses propres transactions
 * autour de ses lectures chez le prestataire, comme en production. L'horloge étant
 * celle du système, une commande est vieillie en reculant sa date en base.</p>
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Échéance de paiement RM-21 (intégration)")
class ExpirationCommandesIT extends SocleIntegration {

    private static final String EMAIL = "marie.echeance@exemple.be";

    @Autowired private ExpirationCommandesJob job;
    @Autowired private PaiementService paiementService;
    @Autowired private CommandeService commandeService;
    @Autowired private PanierService panierService;
    @Autowired private PrestatairePaiementFictif prestataire;
    @Autowired private CommandeRepository commandes;
    @Autowired private PaiementRepository paiements;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private JdbcTemplate jdbc;

    private Piece filtre;

    @BeforeAll
    void catalogue() {
        utilisateurs.save(MembresDeTest.membre(EMAIL, "$2a$12$h", "Dupont", "Marie"));
        Categorie moteur = categories.save(new Categorie("IT-ECH-MOT", "Moteur", TypeCategorie.PIECE));
        filtre = new Piece(moteur, "IT-ECH-001", "Filtre à huile", new BigDecimal("12.50"));
        filtre.setQuantiteStock(1000);
        filtre = pieces.save(filtre);
    }

    @BeforeEach
    void membre() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                EMAIL, "n/a", List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
    }

    @AfterEach
    void sansIdentite() {
        SecurityContextHolder.clearContext();
    }

    private UUID commande() {
        panierService.ajouterPiece(EMAIL, filtre.getReference(), 1);
        return commandeService.passerCommande(EMAIL, true, false, "127.0.0.1").reference();
    }

    /** Commande dont le membre a ouvert la page de paiement, puis laissé passer l'échéance. */
    private UUID commandeEchueAvecTentative() {
        UUID reference = commande();
        paiementService.initierPaiement(reference, EMAIL);
        vieillir(reference);
        return reference;
    }

    private void vieillir(UUID reference) {
        jdbc.update("UPDATE commande SET date_commande = now() - interval '31 minutes' "
                + "WHERE reference = ?", reference);
    }

    private Commande relue(UUID reference) {
        return commandes.findByReference(reference).orElseThrow();
    }

    private Paiement tentativeDe(UUID reference) {
        Commande commande = relue(reference);
        return paiements.findFirstByCommandeOrderByDateInitiationDescIdDesc(commande).orElseThrow();
    }

    @Test
    @DisplayName("commande échue sans tentative : annulée pour délai dépassé")
    void sansTentative() {
        UUID reference = commande();
        vieillir(reference);

        job.annulerLesCommandesExpirees();

        assertThat(relue(reference).getStatut()).isEqualTo(StatutCommande.ANNULEE);
        assertThat(relue(reference).getMotifAnnulation())
                .isEqualTo(MotifAnnulationCommande.TIMEOUT_PAIEMENT);
    }

    @Test
    @DisplayName("commande de moins de trente minutes : laissée, quelle que soit sa tentative")
    void avantLEcheance() {
        UUID reference = commande();
        paiementService.initierPaiement(reference, EMAIL);

        job.annulerLesCommandesExpirees();

        assertThat(relue(reference).getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
        assertThat(tentativeDe(reference).getStatut()).isEqualTo(StatutPaiement.INITIE);
    }

    @Test
    @DisplayName("paiement ouvert et annulable : annulé chez le prestataire, commande annulée pour délai dépassé")
    void ouvertAnnulable() {
        UUID reference = commandeEchueAvecTentative();
        String chezLePrestataire = tentativeDe(reference).getReferenceMollie();

        job.annulerLesCommandesExpirees();

        assertThat(prestataire.lireEtat(chezLePrestataire).statut())
                .as("plus aucun règlement ne peut arriver").isEqualTo(StatutPaiement.ECHOUE);
        assertThat(tentativeDe(reference).getStatut()).isEqualTo(StatutPaiement.ECHOUE);
        assertThat(relue(reference).getStatut()).isEqualTo(StatutCommande.ANNULEE);
        assertThat(relue(reference).getMotifAnnulation())
                .as("le motif reste le délai, pas un échec de paiement")
                .isEqualTo(MotifAnnulationCommande.TIMEOUT_PAIEMENT);
    }

    @Test
    @DisplayName("paiement ouvert non annulable : la commande attend, puis suit le statut définitif payé")
    void nonAnnulablePuisPaye() {
        UUID reference = commandeEchueAvecTentative();
        String chezLePrestataire = tentativeDe(reference).getReferenceMollie();
        prestataire.programmerAnnulable(chezLePrestataire, false);

        job.annulerLesCommandesExpirees();
        assertThat(relue(reference).getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
        assertThat(tentativeDe(reference).getStatut()).isEqualTo(StatutPaiement.INITIE);

        // Le membre règle à la quarantième minute, et aucune notification n'arrive.
        prestataire.programmerStatut(chezLePrestataire, StatutPaiement.REUSSI);
        job.annulerLesCommandesExpirees();

        assertThat(relue(reference).getStatut()).isEqualTo(StatutCommande.PAYEE);
        assertThat(tentativeDe(reference).getStatut()).isEqualTo(StatutPaiement.REUSSI);
    }

    @Test
    @DisplayName("paiement ouvert non annulable qui expire chez le prestataire : la commande est alors annulée")
    void nonAnnulablePuisExpire() {
        UUID reference = commandeEchueAvecTentative();
        String chezLePrestataire = tentativeDe(reference).getReferenceMollie();
        prestataire.programmerAnnulable(chezLePrestataire, false);
        job.annulerLesCommandesExpirees();

        prestataire.programmerStatut(chezLePrestataire, StatutPaiement.EXPIRE);
        job.annulerLesCommandesExpirees();

        assertThat(tentativeDe(reference).getStatut()).isEqualTo(StatutPaiement.EXPIRE);
        assertThat(relue(reference).getStatut()).isEqualTo(StatutCommande.ANNULEE);
    }

    @Test
    @DisplayName("paiement en cours de traitement : la commande attend")
    void enCours() {
        UUID reference = commandeEchueAvecTentative();
        prestataire.programmerStatut(tentativeDe(reference).getReferenceMollie(), StatutPaiement.EN_COURS);

        job.annulerLesCommandesExpirees();

        assertThat(relue(reference).getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
        assertThat(tentativeDe(reference).getStatut()).isEqualTo(StatutPaiement.EN_COURS);
    }

    @Test
    @DisplayName("prestataire en panne : rien ne bouge, et le passage suivant décide")
    void panne() {
        UUID reference = commandeEchueAvecTentative();
        String chezLePrestataire = tentativeDe(reference).getReferenceMollie();
        prestataire.programmerPanne(chezLePrestataire, true);

        job.annulerLesCommandesExpirees();
        assertThat(relue(reference).getStatut()).isEqualTo(StatutCommande.EN_ATTENTE_PAIEMENT);
        assertThat(tentativeDe(reference).getStatut()).isEqualTo(StatutPaiement.INITIE);

        prestataire.programmerPanne(chezLePrestataire, false);
        job.annulerLesCommandesExpirees();
        assertThat(relue(reference).getStatut()).isEqualTo(StatutCommande.ANNULEE);
    }

    @Test
    @DisplayName("échéance passée : le membre retrouve la tentative ouverte, mais n'en crée plus")
    void repriseSansNouvelleTentative() {
        UUID reference = commandeEchueAvecTentative();
        String chezLePrestataire = tentativeDe(reference).getReferenceMollie();

        assertThat(paiementService.initierPaiement(reference, EMAIL))
                .isEqualTo(DepartPaiement.vers("/paiement-fictif/" + chezLePrestataire));

        prestataire.programmerStatut(chezLePrestataire, StatutPaiement.ECHOUE);

        assertThat(paiementService.initierPaiement(reference, EMAIL))
                .isEqualTo(DepartPaiement.sans(DepartPaiement.Issue.DELAI_DEPASSE));
        assertThat(paiements.findAll().stream()
                .filter(p -> p.getCommande().getId().equals(relue(reference).getId())))
                .as("aucune seconde tentative").hasSize(1);
    }
}
