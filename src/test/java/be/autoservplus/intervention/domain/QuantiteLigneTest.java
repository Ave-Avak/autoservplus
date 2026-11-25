package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.domain.UnitePrestation;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.service.FormatageQuantite;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.rgpd.service.dto.ExportDonnees;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unité figée et quantité d'une ligne d'intervention (V73, Q-H-35, Q-H-37) : décimale pour des
 * heures seulement, total taxe comprise arrondi au cent depuis le taux horaire affiché.
 */
@DisplayName("Quantité et unité d'une ligne d'intervention")
class QuantiteLigneTest {

    private static final BigDecimal TVA = new BigDecimal("21.00");

    private final Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
            TypeUtilisateur.MEMBRE);
    private final Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
    private final Categorie atelier = new Categorie("ATE", "Atelier", TypeCategorie.SERVICE);
    private final Prestation vidange = new Prestation(atelier, "VID", "Vidange", new BigDecimal("49.00"), 30);
    private final Prestation mainDOeuvre = new Prestation(atelier, "MO", "Main-d'œuvre", new BigDecimal("65.00"),
            60, UnitePrestation.HEURE);

    /** Devis initial large : aucun ajout de ces tests ne franchit le seuil RM-15. */
    private Intervention enCours() {
        Prestation revision = new Prestation(atelier, "REV", "Révision", new BigDecimal("1000.00"), 60);
        Rdv rdv = new Rdv("RDV-2026-0001", marie, golf, new PosteAtelier("Pont 1"),
                Instant.parse("2026-09-14T08:00:00Z"), Duration.ofMinutes(30), List.of(revision), null);
        Intervention it = new Intervention("INT-2026-0001", rdv);
        it.demarrer(Instant.parse("2026-09-14T08:05:00Z"));
        return it;
    }

    @Test
    @DisplayName("1,5 h à 65,00 HTVA : ligne à l'heure, 97,50 HTVA et 117,98 TVAC")
    void heureEtDemie() {
        LigneIntervention ligne = enCours().ajouterLigneMainOeuvre(mainDOeuvre, new BigDecimal("1.5"),
                mainDOeuvre.getPrixHtva(), TVA);

        assertThat(ligne.getUnite()).isEqualTo(UniteLigne.HEURE);
        assertThat(ligne.getQuantite()).isEqualByComparingTo("1.5");
        assertThat(ligne.totalHtva()).isEqualByComparingTo("97.50");
        assertThat(ligne.totalTvac()).isEqualByComparingTo("117.98");
    }

    @Test
    @DisplayName("une prestation au forfait et une pièce se comptent en unités entières")
    void forfaitEtPieceEntiers() {
        Intervention it = enCours();

        assertThat(it.ajouterLigneMainOeuvre(vidange, new BigDecimal("2"), vidange.getPrixHtva(), TVA).getUnite())
                .isEqualTo(UniteLigne.FORFAIT);
        assertThatThrownBy(() -> it.ajouterLigneMainOeuvre(vidange, new BigDecimal("1.5"), vidange.getPrixHtva(), TVA))
                .isInstanceOf(QuantiteLigneRefuseeException.class)
                .extracting(e -> ((QuantiteLigneRefuseeException) e).getCleMessage())
                .isEqualTo("intervention.erreur.quantite-entiere");

        Piece filtre = new Piece(new Categorie("FIL", "Filtres", TypeCategorie.PIECE), "F-1", "Filtre",
                new BigDecimal("12.40"));
        LigneIntervention piece = it.ajouterLignePiece(filtre, 3);
        assertThat(piece.getUnite()).isEqualTo(UniteLigne.PIECE);
        assertThat(piece.quantitePourLeStock()).isEqualTo(3);
    }

    @Test
    @DisplayName("une quantité nulle, à trois décimales ou hors de la colonne est refusée")
    void quantitesRefusees() {
        Intervention it = enCours();

        assertThatThrownBy(() -> it.ajouterLigneMainOeuvre(mainDOeuvre, BigDecimal.ZERO, mainDOeuvre.getPrixHtva(), TVA))
                .isInstanceOf(QuantiteLigneRefuseeException.class);
        assertThatThrownBy(() -> it.ajouterLigneMainOeuvre(mainDOeuvre, new BigDecimal("1.255"),
                mainDOeuvre.getPrixHtva(), TVA))
                .isInstanceOf(QuantiteLigneRefuseeException.class);
        assertThatThrownBy(() -> it.ajouterLigneMainOeuvre(mainDOeuvre, new BigDecimal("100000"),
                mainDOeuvre.getPrixHtva(), TVA))
                .isInstanceOf(QuantiteLigneRefuseeException.class);
        assertThat(it.getLignes()).hasSize(1);
    }

    @Test
    @DisplayName("la quantité se lit « 1,5 h » en français, « 1,5 u » en néerlandais, « 2 » pour un forfait")
    void lecture() {
        assertThat(FormatageQuantite.lisible(new BigDecimal("1.50"), UniteLigne.HEURE, Locale.FRENCH)).isEqualTo("1,5 h");
        assertThat(FormatageQuantite.lisible(new BigDecimal("1.50"), UniteLigne.HEURE, Locale.forLanguageTag("nl")))
                .isEqualTo("1,5 u");
        assertThat(FormatageQuantite.lisible(new BigDecimal("1.50"), UniteLigne.HEURE, Locale.ENGLISH)).isEqualTo("1.5 h");
        assertThat(FormatageQuantite.lisible(new BigDecimal("2.00"), UniteLigne.FORFAIT, Locale.FRENCH)).isEqualTo("2");
    }

    @Test
    @DisplayName("l'export F22 restitue la quantité décimale et l'unité")
    void export() {
        LigneIntervention ligne = enCours().ajouterLigneMainOeuvre(mainDOeuvre, new BigDecimal("1.5"),
                mainDOeuvre.getPrixHtva(), TVA);

        ExportDonnees.LigneInterventionExport exporte = ExportDonnees.LigneInterventionExport.de(ligne);

        assertThat(exporte.quantite()).isEqualByComparingTo("1.5");
        assertThat(exporte.unite()).isEqualTo("HEURE");
        assertThat(exporte.totalTvac()).isEqualByComparingTo("117.98");
    }
}
