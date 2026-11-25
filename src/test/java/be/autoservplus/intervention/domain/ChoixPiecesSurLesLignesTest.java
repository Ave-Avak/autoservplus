package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.catalogue.domain.ChoixPiecesRefuseException;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.PrestationChoisie;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.domain.UnitePrestation;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le choix des pièces se fige sur la ligne avec son prix, au rendez-vous, au panier et à
 * l'intervention, et passe tel quel d'une ligne à celle qui en naît.
 */
@DisplayName("Choix des pièces figé sur les lignes (V77)")
class ChoixPiecesSurLesLignesTest {

    private static final Instant DEBUT = Instant.parse("2026-09-14T08:00:00Z");
    private static final Duration PAS = Duration.ofMinutes(30);

    private final Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    private final Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
    private final PosteAtelier pont = new PosteAtelier("Pont 1");
    private final Categorie entretien = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);
    private final Prestation vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("69.00"), 30);
    private final Prestation freins = new Prestation(entretien, "FRE", "Plaquettes", new BigDecimal("89.00"), 60);
    private final Prestation mainDOeuvre = new Prestation(entretien, "MO", "Main-d'œuvre", new BigDecimal("65.00"), 60,
            UnitePrestation.HEURE);

    ChoixPiecesSurLesLignesTest() {
        vidange.fixerPrix(new BigDecimal("69.00"), new BigDecimal("39.00"));
    }

    private Rdv rdv(List<PrestationChoisie> prestations) {
        return new Rdv("RDV-2026-0001", marie, golf, pont, DEBUT, PAS, prestations, null);
    }

    @Nested
    @DisplayName("Rendez-vous")
    class RendezVous {

        @Test
        @DisplayName("la ligne fige le prix pose seule et le choix")
        void poseSeule() {
            Rdv rdv = rdv(List.of(new PrestationChoisie(vidange, ChoixPieces.APPORTEES_PAR_CLIENT),
                    new PrestationChoisie(freins, null)));

            assertThat(rdv.getLignes().get(0).getPrixUnitaireHtva()).isEqualByComparingTo("39.00");
            assertThat(rdv.getLignes().get(0).getChoixPieces()).contains(ChoixPieces.APPORTEES_PAR_CLIENT);
            assertThat(rdv.getLignes().get(1).getChoixPieces()).isEmpty();
            assertThat(rdv.montantHtva()).isEqualByComparingTo("128.00");
        }

        @Test
        @DisplayName("un forfait qui propose le choix ne se réserve pas sans réponse")
        void sansReponse() {
            assertThatThrownBy(() -> rdv(List.of(new PrestationChoisie(vidange, null))))
                    .isInstanceOf(ChoixPiecesRefuseException.class);
        }

        @Test
        @DisplayName("une prestation deux fois, avec deux choix, est refusée (uq_rdv_service)")
        void deuxFois() {
            assertThatThrownBy(() -> rdv(List.of(new PrestationChoisie(vidange, ChoixPieces.FOURNIES_PAR_GARAGE),
                    new PrestationChoisie(vidange, ChoixPieces.APPORTEES_PAR_CLIENT))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("la surcharge des tests prend les pièces fournies")
        void surchargeDesTests() {
            Rdv rdv = new Rdv("RDV-2026-0002", marie, golf, pont, DEBUT, PAS, List.of(vidange), null);

            assertThat(rdv.getLignes().get(0).getChoixPieces()).contains(ChoixPieces.FOURNIES_PAR_GARAGE);
            assertThat(rdv.getLignes().get(0).getPrixUnitaireHtva()).isEqualByComparingTo("69.00");
        }
    }

    @Nested
    @DisplayName("Intervention")
    class InterventionNee {

        @Test
        @DisplayName("les lignes du rendez-vous passent avec leur choix et leur prix")
        void depuisLeRendezVous() {
            Rdv rdv = rdv(List.of(new PrestationChoisie(vidange, ChoixPieces.APPORTEES_PAR_CLIENT)));

            Intervention it = new Intervention("INT-2026-0001", rdv, Langue.fr);

            assertThat(it.getLignes().get(0).getChoixPieces()).contains(ChoixPieces.APPORTEES_PAR_CLIENT);
            assertThat(it.getLignes().get(0).getPrixUnitaireHtva()).isEqualByComparingTo("39.00");
            assertThat(it.getMontantDevisInitialHtva()).isEqualByComparingTo("39.00");
        }

        @Test
        @DisplayName("un ajout du garage fige le prix du choix retenu")
        void ajoutDuGarage() {
            Intervention it = new Intervention("INT-2026-0001",
                    rdv(List.of(new PrestationChoisie(freins, null))), Langue.fr);
            it.demarrer(DEBUT);

            LigneIntervention ligne = it.ajouterLigneMainOeuvre(vidange, BigDecimal.ONE,
                    ChoixPieces.APPORTEES_PAR_CLIENT);

            assertThat(ligne.getPrixUnitaireHtva()).isEqualByComparingTo("39.00");
            assertThat(ligne.getChoixPieces()).contains(ChoixPieces.APPORTEES_PAR_CLIENT);
        }

        @Test
        @DisplayName("une prestation à l'heure n'admet aucun choix")
        void aLHeure() {
            Intervention it = new Intervention("INT-2026-0001",
                    rdv(List.of(new PrestationChoisie(freins, null))), Langue.fr);
            it.demarrer(DEBUT);

            assertThatThrownBy(() -> it.ajouterLigneMainOeuvre(mainDOeuvre, BigDecimal.ONE,
                    ChoixPieces.FOURNIES_PAR_GARAGE))
                    .isInstanceOf(ChoixPiecesRefuseException.class);
            assertThat(it.ajouterLigneMainOeuvre(mainDOeuvre, new BigDecimal("1.5"), null).getChoixPieces())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Panier")
    class AuPanier {

        @Test
        @DisplayName("une ligne par couple prestation et choix, fusion sur le même couple")
        void uneLigneParCouple() {
            Panier panier = new Panier(marie);

            panier.ajouterService(vidange, 1, ChoixPieces.FOURNIES_PAR_GARAGE);
            panier.ajouterService(vidange, 2, ChoixPieces.APPORTEES_PAR_CLIENT);
            LignePanier fusionnee = panier.ajouterService(vidange, 1, ChoixPieces.APPORTEES_PAR_CLIENT);

            assertThat(panier.getLignes()).hasSize(2);
            assertThat(fusionnee.getQuantite()).isEqualTo((short) 3);
            assertThat(fusionnee.getPrixUnitaireHtva()).isEqualByComparingTo("39.00");
            assertThat(panier.quantitePour(vidange)).isEqualTo(4);
        }

        @Test
        @DisplayName("sans réponse sur un forfait qui propose le choix : refusé")
        void sansReponse() {
            Panier panier = new Panier(marie);

            assertThatThrownBy(() -> panier.ajouterService(vidange, 1, null))
                    .isInstanceOf(ChoixPiecesRefuseException.class);
            assertThat(panier.estVide()).isTrue();
        }
    }
}
