package be.autoservplus.vente.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.service.PanierDeNatureMixteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prestation au panier (F12-a) : figement des valeurs et separation des natures.
 */
@DisplayName("Panier : prestations (F12)")
class PanierServiceLigneTest {

    private Panier panier;
    private Prestation vidange;
    private Piece filtre;

    @BeforeEach
    void setUp() {
        Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        panier = new Panier(marie);

        Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
        vidange = new Prestation(entretien, "VID-01", "Vidange",
                new BigDecimal("45.00"), 30);
        Categorie filtres = new Categorie("FILTRES", "Filtres", TypeCategorie.PIECE);
        filtre = new Piece(filtres, "REF-001", "Filtre à huile", new BigDecimal("9.00"));
    }

    @Nested
    @DisplayName("Figement des valeurs (RM-30)")
    class Figement {

        @Test
        @DisplayName("libelle, prix et taux sont recopies a l ajout")
        void valeursFigees() {
            LignePanier ligne = panier.ajouterService(vidange, 1);

            assertThat(ligne.estService()).isTrue();
            assertThat(ligne.getPrestation()).isEqualTo(vidange);
            assertThat(ligne.getLibelleFige()).isEqualTo("Vidange");
            assertThat(ligne.getPrixUnitaireHtva()).isEqualByComparingTo("45.00");
            assertThat(ligne.getTauxTva()).isEqualByComparingTo("21.00");
        }

        @Test
        @DisplayName("un tarif revu apres l ajout ne change pas la ligne")
        void tarifReviseSansEffet() {
            LignePanier ligne = panier.ajouterService(vidange, 1);

            vidange.modifierPrix(new BigDecimal("60.00"));

            assertThat(ligne.getPrixUnitaireHtva())
                    .as("c est ce que le membre a vu au moment de decider qui fait foi")
                    .isEqualByComparingTo("45.00");
        }

        @Test
        @DisplayName("un second ajout fusionne la ligne et conserve les valeurs figees")
        void fusionDuDoublon() {
            panier.ajouterService(vidange, 1);
            vidange.modifierPrix(new BigDecimal("60.00"));

            LignePanier ligne = panier.ajouterService(vidange, 2);

            assertThat(panier.getLignes()).hasSize(1);
            assertThat(ligne.getQuantite()).isEqualTo((short) 3);
            assertThat(ligne.getPrixUnitaireHtva()).isEqualByComparingTo("45.00");
        }

        @Test
        @DisplayName("les totaux somment les lignes de service comme celles de piece")
        void totaux() {
            panier.ajouterService(vidange, 2);

            assertThat(panier.totalHtva()).isEqualByComparingTo("90.00");
            assertThat(panier.totalTvac()).isEqualByComparingTo("108.90");
            assertThat(panier.totalTva())
                    .as("TVA = TVAC - HTVA, identite verifiee par ck_commande_coherence")
                    .isEqualByComparingTo(panier.totalTvac().subtract(panier.totalHtva()));
        }
    }

    @Nested
    @DisplayName("Separation des natures")
    class SeparationDesNatures {

        @Test
        @DisplayName("un panier de pieces refuse l ajout d une prestation")
        void serviceDansUnPanierDePieces() {
            panier.ajouterPiece(filtre, 1);

            assertThatThrownBy(() -> panier.ajouterService(vidange, 1))
                    .as("une commande mixte imposerait une retractation partielle, remise en V2")
                    .isInstanceOf(PanierDeNatureMixteException.class)
                    .hasMessageContaining("pièces");
        }

        @Test
        @DisplayName("un panier de prestations refuse l ajout d une piece")
        void pieceDansUnPanierDeServices() {
            panier.ajouterService(vidange, 1);

            assertThatThrownBy(() -> panier.ajouterPiece(filtre, 1))
                    .isInstanceOf(PanierDeNatureMixteException.class)
                    .hasMessageContaining("prestations");
        }

        @Test
        @DisplayName("le refus laisse le panier intact")
        void refusSansEffetDeBord() {
            panier.ajouterPiece(filtre, 1);

            assertThatCode(() -> {
                try {
                    panier.ajouterService(vidange, 1);
                } catch (PanierDeNatureMixteException attendu) {
                    // ignore : c'est l'etat du panier qu'on verifie
                }
            }).doesNotThrowAnyException();
            assertThat(panier.getLignes()).hasSize(1);
            assertThat(panier.estPanierDeServices()).isFalse();
        }

        @Test
        @DisplayName("plusieurs prestations differentes cohabitent sans probleme")
        void plusieursServices() {
            Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
            Prestation freins = new Prestation(entretien, "FRE-01", "Plaquettes",
                    new BigDecimal("120.00"), 60);

            panier.ajouterService(vidange, 1);
            panier.ajouterService(freins, 1);

            assertThat(panier.getLignes()).hasSize(2);
            assertThat(panier.estPanierDeServices()).isTrue();
        }

        @Test
        @DisplayName("un panier vide n est un panier de services ni d autre chose")
        void panierVide() {
            assertThat(panier.estPanierDeServices()).isFalse();
        }
    }
}
