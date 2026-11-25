package be.autoservplus.intervention.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.ChoixPieces;
import be.autoservplus.catalogue.domain.Piece;
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
import be.autoservplus.rgpd.service.dto.ExportDonnees;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Pièces incluses d'une ligne de forfait (V77) : copiées de la composition quand le garage
 * fournit les pièces, ajustables jusqu'à la clôture, chaque changement rendu au journal.
 */
@DisplayName("Pièces incluses d'une ligne de forfait (V77)")
class PiecesInclusesTest {

    private static final Instant DEBUT = Instant.parse("2026-09-14T08:00:00Z");

    private final Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    private final Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
    private final PosteAtelier pont = new PosteAtelier("Pont 1");
    private final Categorie entretien = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);
    private final Categorie filtres = new Categorie("FIL", "Filtres", TypeCategorie.PIECE);
    private final Piece filtre = new Piece(filtres, "F-1", "Filtre à huile", new BigDecimal("12.40"));
    private final Piece huile = new Piece(filtres, "H-1", "Huile 5W30, un litre", new BigDecimal("9.90"));
    private final Piece joint = new Piece(filtres, "J-1", "Joint de vidange", new BigDecimal("1.50"));
    private final Prestation vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("69.00"), 30);
    private final Prestation mainDOeuvre = new Prestation(entretien, "MO", "Main-d'œuvre", new BigDecimal("65.00"), 60,
            UnitePrestation.HEURE);

    PiecesInclusesTest() {
        vidange.fixerPrix(new BigDecimal("69.00"), new BigDecimal("39.00"));
        vidange.fixerComposant(filtre, 1);
        vidange.fixerComposant(huile, 5);
    }

    private Intervention intervention(ChoixPieces choix) {
        Rdv rdv = new Rdv("RDV-2026-0001", marie, golf, pont, DEBUT, Duration.ofMinutes(30),
                List.of(new PrestationChoisie(vidange, choix)), null);
        return new Intervention("INT-2026-0001", rdv, Langue.fr);
    }

    private LigneIntervention premiere(Intervention it) {
        return it.getLignes().get(0);
    }

    @Nested
    @DisplayName("Copie de la composition")
    class Copie {

        @Test
        @DisplayName("pièces fournies : la composition devient les pièces incluses, rendues au journal")
        void piecesFournies() {
            Intervention it = intervention(ChoixPieces.FOURNIES_PAR_GARAGE);

            assertThat(premiere(it).getPiecesIncluses())
                    .extracting(PieceIncluse::getPiece, PieceIncluse::getQuantite)
                    .containsExactly(tuple(filtre, 1), tuple(huile, 5));
            assertThat(it.copieDesPiecesIncluses())
                    .extracting(ChangementPieceIncluse::evenement, ChangementPieceIncluse::avant,
                            ChangementPieceIncluse::apres)
                    .containsExactly(tuple(EvenementPieceIncluse.COPIE, null, 1),
                            tuple(EvenementPieceIncluse.COPIE, null, 5));
        }

        @Test
        @DisplayName("pose seule : le client apporte ses pièces, rien n'est inclus")
        void poseSeule() {
            assertThat(premiere(intervention(ChoixPieces.APPORTEES_PAR_CLIENT)).getPiecesIncluses()).isEmpty();
            assertThat(premiere(intervention(ChoixPieces.APPORTEES_PAR_CLIENT)).porteDesPiecesIncluses()).isFalse();
        }

        @Test
        @DisplayName("quantité de la ligne : deux forfaits consomment deux fois la composition")
        void foisLaQuantite() {
            Intervention it = intervention(ChoixPieces.FOURNIES_PAR_GARAGE);
            it.demarrer(DEBUT);

            LigneIntervention deux = it.ajouterLigneMainOeuvre(vidange, new BigDecimal("2"),
                    ChoixPieces.FOURNIES_PAR_GARAGE);

            assertThat(deux.getPiecesIncluses()).extracting(PieceIncluse::getQuantite).containsExactly(2, 10);
        }

        @Test
        @DisplayName("une prestation à l'heure n'en porte jamais")
        void aLHeure() {
            Intervention it = intervention(ChoixPieces.FOURNIES_PAR_GARAGE);
            it.demarrer(DEBUT);

            LigneIntervention heures = it.ajouterLigneMainOeuvre(mainDOeuvre, new BigDecimal("1.5"), null);

            assertThat(heures.porteDesPiecesIncluses()).isFalse();
            assertThatThrownBy(() -> it.fixerPieceIncluse(heures, joint, 1))
                    .isInstanceOf(PieceIncluseRefuseeException.class);
        }
    }

    @Nested
    @DisplayName("Ajustement par le garage")
    class Ajustement {

        @Test
        @DisplayName("ajout, puis modification de la quantité, puis retrait, chacun avec ses quantités")
        void ajoutModificationRetrait() {
            Intervention it = intervention(ChoixPieces.FOURNIES_PAR_GARAGE);
            LigneIntervention ligne = premiere(it);

            ChangementPieceIncluse ajout = it.fixerPieceIncluse(ligne, joint, 1);
            ChangementPieceIncluse modification = it.fixerPieceIncluse(ligne, huile, 4);
            ChangementPieceIncluse retrait = it.retirerPieceIncluse(ligne, filtre);

            assertThat(List.of(ajout, modification, retrait))
                    .extracting(ChangementPieceIncluse::evenement, ChangementPieceIncluse::avant,
                            ChangementPieceIncluse::apres)
                    .containsExactly(tuple(EvenementPieceIncluse.AJOUT, null, 1),
                            tuple(EvenementPieceIncluse.MODIFICATION, 5, 4),
                            tuple(EvenementPieceIncluse.RETRAIT, 1, null));
            assertThat(ligne.getPiecesIncluses())
                    .extracting(PieceIncluse::getPiece, PieceIncluse::getQuantite)
                    .containsExactly(tuple(huile, 4), tuple(joint, 1));
        }

        @Test
        @DisplayName("quantité inférieure à un, ou pièce absente : refusé")
        void refus() {
            Intervention it = intervention(ChoixPieces.FOURNIES_PAR_GARAGE);
            LigneIntervention ligne = premiere(it);

            assertThatThrownBy(() -> it.fixerPieceIncluse(ligne, huile, 0))
                    .isInstanceOfSatisfying(PieceIncluseRefuseeException.class,
                            e -> assertThat(e.getCleMessage()).isEqualTo("admin.intervention.incluses.erreur.quantite"));
            assertThatThrownBy(() -> it.retirerPieceIncluse(ligne, joint))
                    .isInstanceOfSatisfying(PieceIncluseRefuseeException.class,
                            e -> assertThat(e.getCleMessage()).isEqualTo("admin.intervention.incluses.erreur.absente"));
        }

        @Test
        @DisplayName("une pose seule n'en reçoit pas")
        void poseSeule() {
            Intervention it = intervention(ChoixPieces.APPORTEES_PAR_CLIENT);

            assertThatThrownBy(() -> it.fixerPieceIncluse(premiere(it), joint, 1))
                    .isInstanceOfSatisfying(PieceIncluseRefuseeException.class,
                            e -> assertThat(e.getCleMessage()).isEqualTo("admin.intervention.incluses.erreur.ligne"));
        }

        @Test
        @DisplayName("ouvertes dans tous les statuts non terminaux, fermées après la clôture")
        void closeApresLaCloture() {
            Intervention it = intervention(ChoixPieces.FOURNIES_PAR_GARAGE);
            LigneIntervention ligne = premiere(it);

            it.fixerPieceIncluse(ligne, joint, 1);
            it.demarrer(DEBUT);
            it.terminer(DEBUT.plusSeconds(3600));

            assertThatThrownBy(() -> it.fixerPieceIncluse(ligne, joint, 2))
                    .isInstanceOfSatisfying(PieceIncluseRefuseeException.class,
                            e -> assertThat(e.getCleMessage()).isEqualTo("admin.intervention.incluses.erreur.close"));
        }

        @Test
        @DisplayName("la ligne retirée rend le retrait de chaque pièce, sans pointer vers elle")
        void retraitAvecLaLigne() {
            Intervention it = intervention(ChoixPieces.FOURNIES_PAR_GARAGE);

            assertThat(premiere(it).retraitAvecLaLigne())
                    .extracting(ChangementPieceIncluse::ligne, ChangementPieceIncluse::evenement,
                            ChangementPieceIncluse::avant)
                    .containsExactly(tuple(null, EvenementPieceIncluse.RETRAIT, 1),
                            tuple(null, EvenementPieceIncluse.RETRAIT, 5));
        }
    }

    @Test
    @DisplayName("l'export F22 restitue les pièces incluses de la ligne")
    void export() {
        ExportDonnees.LigneInterventionExport ligne =
                ExportDonnees.LigneInterventionExport.de(premiere(intervention(ChoixPieces.FOURNIES_PAR_GARAGE)));

        assertThat(ligne.choixPieces()).isEqualTo("FOURNIES_PAR_GARAGE");
        assertThat(ligne.piecesIncluses())
                .containsExactly(new ExportDonnees.PieceIncluseExport("Filtre à huile", 1),
                        new ExportDonnees.PieceIncluseExport("Huile 5W30, un litre", 5));
    }
}
