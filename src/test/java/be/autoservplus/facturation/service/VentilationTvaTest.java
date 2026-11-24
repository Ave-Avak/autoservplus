package be.autoservplus.facturation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.domain.Panier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Ventilation de la TVA par taux, mention legale d une facture multi-taux.
 *
 * <p>Les lignes sont construites par un vrai panier : leurs montants sortent des
 * memes methodes que ceux de la commande, ce qui rend verifiable la propriete qui
 * compte, le total des tranches egale au centime les montants factures.</p>
 */
@DisplayName("VentilationTva")
class VentilationTvaTest {

    private final Categorie categorie = new Categorie("PIECES", "Pieces", TypeCategorie.PIECE);
    private final Utilisateur marie = new Utilisateur(
            "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);

    private Piece piece(String libelle, String prixHtva, String tauxTva) {
        Piece piece = new Piece(categorie, "REF-" + libelle, libelle, new BigDecimal(prixHtva));
        piece.setTauxTva(new BigDecimal(tauxTva));
        piece.setQuantiteStock(100);
        return piece;
    }

    /** Panier reel : les lignes portent les memes calculs que ceux de la commande. */
    private List<LignePanier> lignes(Object... pieceEtQuantite) {
        return panier(pieceEtQuantite).getLignes();
    }

    private Panier panier(Object... pieceEtQuantite) {
        Panier panier = new Panier(marie);
        for (int i = 0; i < pieceEtQuantite.length; i += 2) {
            panier.ajouterPiece((Piece) pieceEtQuantite[i], (int) pieceEtQuantite[i + 1]);
        }
        return panier;
    }

    @Test
    @DisplayName("un seul taux : une tranche, et le taux de la facture est connu")
    void tauxUnique() {
        VentilationTva ventilation = VentilationTva.desLignes(
                lignes(piece("Plaquettes", "19.99", "21.00"), 2));

        assertThat(ventilation.estMultiTaux()).isFalse();
        assertThat(ventilation.tauxUnique()).hasValueSatisfying(
                taux -> assertThat(taux).isEqualByComparingTo("21.00"));
        assertThat(ventilation.tranches()).singleElement().satisfies(tranche -> {
            assertThat(tranche.baseHtva()).isEqualByComparingTo("39.98");
            assertThat(tranche.montantTva()).isEqualByComparingTo("8.40");
            assertThat(tranche.montantTvac()).isEqualByComparingTo("48.38");
        });
    }

    @Test
    @DisplayName("taux mixtes 6 % et 21 % : une tranche par taux, triees par taux croissant")
    void tauxMixtes() {
        VentilationTva ventilation = VentilationTva.desLignes(lignes(
                piece("Plaquettes", "100.00", "21.00"), 1,
                piece("Recyclage", "50.00", "6.00"), 1));

        assertThat(ventilation.estMultiTaux()).isTrue();
        // Multi-taux : aucun taux unique n est vrai, la colonne facture vaut NULL.
        assertThat(ventilation.tauxUnique()).isEmpty();
        assertThat(ventilation.tranches())
                .extracting(v -> v.taux().toPlainString(),
                        v -> v.baseHtva().toPlainString(),
                        v -> v.montantTva().toPlainString())
                .containsExactly(
                        tuple("6", "50.00", "3.00"),
                        tuple("21", "100.00", "21.00"));
    }

    @Test
    @DisplayName("plusieurs lignes d'un meme taux se cumulent dans une seule tranche")
    void cumulDansUneTranche() {
        VentilationTva ventilation = VentilationTva.desLignes(lignes(
                piece("Plaquettes", "19.99", "21.00"), 2,
                piece("Ampoule", "9.90", "21.00"), 3));

        assertThat(ventilation.tranches()).hasSize(1);
        // 2 x 24,19 et 3 x 11,98 : 84,32 payés. La base se déduit de ce total, 69,69, et non
        // de la somme des prix hors TVA des lignes, qui vaudrait 69,68.
        assertThat(ventilation.totalTvac()).isEqualByComparingTo("84.32");
        assertThat(ventilation.totalHtva()).isEqualByComparingTo("69.69");
        assertThat(ventilation.totalTva()).isEqualByComparingTo("14.63");
    }

    @Test
    @DisplayName("le total des tranches égale au centime les montants que la commande fige")
    void totauxCoherentsAvecLaCommande() {
        // Prix à décimales fâchées. La commande fige les totaux du panier, la facture
        // recalcule sa ventilation depuis les lignes : les deux doivent donner les mêmes
        // nombres, sans quoi le PDF contredirait la facture.
        Panier panier = panier(
                piece("Joint", "0.07", "21.00"), 3,
                piece("Vis", "0.03", "6.00"), 7);
        VentilationTva ventilation = VentilationTva.desLignes(panier.getLignes());

        BigDecimal tvacDesLignes = panier.getLignes().stream().map(LignePanier::totalTvac)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3 x 0,08 à 21 % et 7 x 0,03 à 6 %.
        assertThat(ventilation.totalTvac()).isEqualByComparingTo("0.45").isEqualByComparingTo(tvacDesLignes);
        assertThat(ventilation.totalHtva()).isEqualByComparingTo("0.40").isEqualByComparingTo(panier.totalHtva());
        assertThat(ventilation.totalTva()).isEqualByComparingTo("0.05").isEqualByComparingTo(panier.totalTva());
        assertThat(panier.totalTvac()).isEqualByComparingTo(ventilation.totalTvac());
    }

    @Test
    @DisplayName("un taux ecrit 21 ou 21.00 designe la meme tranche")
    void memeTauxEchellesDifferentes() {
        VentilationTva ventilation = VentilationTva.desLignes(lignes(
                piece("Plaquettes", "10.00", "21.00"), 1,
                piece("Ampoule", "10.00", "21"), 1));

        assertThat(ventilation.tranches())
                .as("L echelle du BigDecimal ne doit pas scinder une tranche en deux")
                .hasSize(1);
    }
    @Test
    @DisplayName("par montants, une commande donne la même ventilation que par ses lignes")
    void parMontantsCommeParLignes() {
        List<LignePanier> lignes = lignes(
                piece("Joint", "0.07", "21.00"), 3,
                piece("Vis", "0.03", "6.00"), 7);

        VentilationTva parMontants = VentilationTva.desMontants(lignes.stream()
                .map(l -> new CalculTva.MontantTaxe(l.getTauxTva(), l.totalTvac()))
                .toList());

        assertThat(parMontants).isEqualTo(VentilationTva.desLignes(lignes));
    }

    @Test
    @DisplayName("une ligne en heures entre dans la tranche de son taux : 1,5 h et 15,00 donnent 132,98")
    void ligneEnHeures() {
        BigDecimal heures = CalculTva.totalTvac(
                new BigDecimal("65.00"), new BigDecimal("21.00"), new BigDecimal("1.5"));

        VentilationTva ventilation = VentilationTva.desMontants(List.of(
                new CalculTva.MontantTaxe(new BigDecimal("21.00"), heures),
                new CalculTva.MontantTaxe(new BigDecimal("21.00"), new BigDecimal("15.00"))));

        assertThat(heures).isEqualByComparingTo("117.98");
        assertThat(ventilation.totalTvac()).isEqualByComparingTo("132.98");
        assertThat(ventilation.totalHtva()).isEqualByComparingTo("109.90");
        assertThat(ventilation.totalTva()).isEqualByComparingTo("23.08");
    }
}
