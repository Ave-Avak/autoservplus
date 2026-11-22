package be.autoservplus.pilotage.service.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TableauDeBord (BL-1)")
class TableauDeBordTest {

    private static TableauDeBord bord(long reservees, long capacite,
                                      BigDecimal caHtva, BigDecimal avoirsHtva) {
        return bord(reservees, capacite, caHtva, avoirsHtva,
                new MontantPeriode(BigDecimal.ZERO, BigDecimal.ZERO, 0));
    }

    private static TableauDeBord bord(long reservees, long capacite, BigDecimal caHtva,
                                      BigDecimal avoirsHtva, MontantPeriode commandesPayees) {
        return new TableauDeBord(
                PeriodeTableau.de("mois", null, null, java.time.LocalDate.of(2026, 8, 15)),
                new MontantPeriode(caHtva, caHtva, 3),
                new MontantPeriode(avoirsHtva, avoirsHtva, 1),
                new MontantPeriode(BigDecimal.ZERO, BigDecimal.ZERO, 0),
                List.of(), new Annulations(0, 0, 0, 0, 0), 0, commandesPayees,
                reservees, capacite, 3, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("le panier moyen divise le montant hors TVA par le nombre de commandes payées")
    void panierMoyen() {
        TableauDeBord troisCommandes = bord(0, 0, BigDecimal.TEN, BigDecimal.ZERO,
                new MontantPeriode(new BigDecimal("100.00"), new BigDecimal("121.00"), 3));

        assertThat(troisCommandes.panierMoyenHtva()).isEqualByComparingTo("33.33");
    }

    @Test
    @DisplayName("sans commande payée, le panier moyen est nul et non zéro")
    void panierMoyenSansCommande() {
        assertThat(bord(0, 0, BigDecimal.TEN, BigDecimal.ZERO).panierMoyenHtva()).isNull();
    }

    @Test
    @DisplayName("le taux d'annulation rapporte les annulés au total, absences et refus à part")
    void tauxDAnnulation() {
        Annulations annulations = new Annulations(18, 1, 2, 4, 5);

        assertThat(annulations.annules()).isEqualTo(3);
        assertThat(annulations.taux()).as("3 sur 18, et non 12 sur 18").isEqualTo(17);
    }

    @Test
    @DisplayName("sans rendez-vous, le taux d'annulation est nul et non zéro")
    void tauxDAnnulationSansRendezVous() {
        assertThat(new Annulations(0, 0, 0, 0, 0).taux()).isNull();
    }

    @Test
    @DisplayName("le taux est arrondi a l entier le plus proche")
    void tauxArrondi() {
        assertThat(bord(500, 1000, BigDecimal.TEN, BigDecimal.ZERO).tauxOccupation()).isEqualTo(50);
        assertThat(bord(333, 1000, BigDecimal.TEN, BigDecimal.ZERO).tauxOccupation()).isEqualTo(33);
        assertThat(bord(335, 1000, BigDecimal.TEN, BigDecimal.ZERO).tauxOccupation()).isEqualTo(34);
    }

    @Test
    @DisplayName("une capacite nulle rend null et non zero")
    void capaciteInconnue() {
        TableauDeBord sansCapacite = bord(0, 0, BigDecimal.TEN, BigDecimal.ZERO);

        assertThat(sansCapacite.tauxOccupation())
                .as("afficher 0 % annoncerait un atelier vide alors que la question n a pas de reponse")
                .isNull();
        assertThat(sansCapacite.capaciteConnue()).isFalse();
    }

    @Test
    @DisplayName("les avoirs viennent en deduction du chiffre d affaires")
    void chiffreAffaireNet() {
        TableauDeBord avecAvoir = bord(0, 0, new BigDecimal("1000.00"), new BigDecimal("150.00"));

        assertThat(avecAvoir.chiffreAffaireNetHtva()).isEqualByComparingTo("850.00");
    }

    @Test
    @DisplayName("un montant sans piece comptable est signale comme vide")
    void montantVide() {
        assertThat(new MontantPeriode(BigDecimal.ZERO, BigDecimal.ZERO, 0).estVide()).isTrue();
        assertThat(new MontantPeriode(BigDecimal.TEN, BigDecimal.TEN, 1).estVide()).isFalse();
    }

    @Test
    @DisplayName("une piece a zero est en rupture, pas seulement sous le seuil")
    void rupture() {
        assertThat(new PieceEnAlerte(null, "Filtre", 0, 5).enRupture()).isTrue();
        assertThat(new PieceEnAlerte(null, "Filtre", 2, 5).enRupture()).isFalse();
    }
}
