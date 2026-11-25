package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.Panier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lignes de la facture d'atelier émise à la clôture (A15, RM-17, Q-H-41) : toutes les lignes
 * facturables, ou les seuls ajouts acceptés pour une intervention née d'une commande payée.
 */
@DisplayName("Lignes à facturer d'une intervention")
class LignesAFacturerTest {

    private static final Instant DEBUT = Instant.parse("2026-09-14T08:00:00Z");
    private static final BigDecimal TVA = new BigDecimal("21.00");

    private final Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
            TypeUtilisateur.MEMBRE);
    private final Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
    private final Categorie atelier = new Categorie("ATE", "Atelier", TypeCategorie.SERVICE);
    private final Prestation vidange = new Prestation(atelier, "VID", "Vidange", new BigDecimal("49.00"), 30);
    private final Prestation petite = new Prestation(atelier, "PET", "Petite", new BigDecimal("1.00"), 10);
    private final Prestation chere = new Prestation(atelier, "CHE", "Chère", new BigDecimal("500.00"), 60);

    @Test
    @DisplayName("née d'un rendez-vous : devis initial et ajouts acceptés, sans les lignes en attente ni refusées")
    void depuisUnRendezVous() {
        Intervention it = new Intervention("INT-2026-0001", new Rdv("RDV-2026-0001", marie, golf,
                new PosteAtelier("Pont 1"), DEBUT, Duration.ofMinutes(30), List.of(vidange), null));
        it.demarrer(DEBUT);
        it.ajouterLigneMainOeuvre(petite, BigDecimal.ONE, petite.getPrixHtva(), TVA);
        it.ajouterLigneMainOeuvre(chere, BigDecimal.ONE, chere.getPrixHtva(), TVA);
        it.refuserDepassement();

        assertThat(it.lignesAFacturer()).extracting(LigneIntervention::getLibelleFige)
                .containsExactly("Vidange", "Petite");
    }

    @Test
    @DisplayName("née d'une commande payée : les ajouts acceptés seuls, la commande ayant déjà sa facture")
    void depuisUneCommande() {
        Panier panier = new Panier(marie);
        panier.ajouterService(vidange, 1);
        Commande commande = new Commande("CMD-2026-0001", marie, new BigDecimal("49.00"),
                new BigDecimal("10.29"), new BigDecimal("59.29"), DEBUT);
        commande.reprendreLignes(panier.getLignes());
        Intervention it = Intervention.pourCommande("INT-2026-0002", commande, golf, panier.getLignes());
        it.demarrer(DEBUT);

        assertThat(it.lignesAFacturer()).isEmpty();

        it.ajouterLigneMainOeuvre(petite, BigDecimal.ONE, petite.getPrixHtva(), TVA);
        assertThat(it.lignesAFacturer()).extracting(LigneIntervention::getLibelleFige)
                .containsExactly("Petite");
    }
}
