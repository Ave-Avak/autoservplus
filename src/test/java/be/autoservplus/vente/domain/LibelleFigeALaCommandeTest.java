package be.autoservplus.vente.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Traductions;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.support.MembresDeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Écart à RM-30 : le libellé d'une ligne se fige à la conclusion de la commande, dans la
 * langue de ses documents, et non à l'ajout au panier. Le prix et le taux restent figés
 * à l'ajout.
 */
@DisplayName("Ligne de commande : libellé figé dans la langue des documents")
class LibelleFigeALaCommandeTest {

    private static final Locale NL = Locale.forLanguageTag("nl");

    private Utilisateur marie;
    private Piece filtre;
    private Piece joint;

    @BeforeEach
    void catalogue() {
        marie = MembresDeTest.membre("marie@exemple.be", "$2a$12$h", "Dupont", "Marie");
        Categorie filtration = new Categorie("FIL", "Filtration", TypeCategorie.PIECE);
        filtre = new Piece(filtration, "FH-1", "Filtre à huile", new BigDecimal("12.40"));
        filtre.setQuantiteStock(10);
        filtre.traduire(new Traductions("Oliefilter", "Oil filter", null, null));
        joint = new Piece(filtration, "JB-1", "Joint de bouchon", new BigDecimal("3.00"));
        joint.setQuantiteStock(10);
    }

    private Commande commande(Langue langue) {
        return new Commande("CMD-2026-0001", marie, new BigDecimal("15.40"), new BigDecimal("3.23"),
                new BigDecimal("18.63"), Instant.parse("2026-11-22T10:00:00Z"), false, langue);
    }

    @Test
    @DisplayName("au panier, le libellé suit la langue de l'écran")
    void auPanier() {
        LignePanier ligne = new Panier(marie).ajouterPiece(filtre, 1);

        assertThat(ligne.libelleAffiche(NL)).isEqualTo("Oliefilter");
        assertThat(ligne.libelleAffiche(Locale.ENGLISH)).isEqualTo("Oil filter");
        assertThat(ligne.libelleAffiche(Locale.FRENCH)).isEqualTo("Filtre à huile");
    }

    @Test
    @DisplayName("commande en néerlandais : la traduction se fige, l'article non traduit se fige en français")
    void commandeNeerlandaise() {
        Panier panier = new Panier(marie);
        LignePanier traduite = panier.ajouterPiece(filtre, 1);
        LignePanier francaise = panier.ajouterPiece(joint, 1);

        commande(Langue.nl).reprendreLignes(List.of(traduite, francaise));

        assertThat(traduite.getLibelleFige()).isEqualTo("Oliefilter");
        assertThat(francaise.getLibelleFige()).isEqualTo("Joint de bouchon");
        assertThat(traduite.libelleAffiche(Locale.ENGLISH)).as("figé : l'écran ne le traduit plus").isEqualTo("Oliefilter");
    }

    @Test
    @DisplayName("commande en français : le libellé d'origine")
    void commandeFrancaise() {
        LignePanier ligne = new Panier(marie).ajouterPiece(filtre, 1);

        commande(Langue.fr).reprendreLignes(List.of(ligne));

        assertThat(ligne.getLibelleFige()).isEqualTo("Filtre à huile");
    }

    @Test
    @DisplayName("un libellé changé au catalogue après la commande ne touche pas la ligne, ni un tarif revu avant")
    void figeUneFois() {
        LignePanier ligne = new Panier(marie).ajouterPiece(filtre, 1);
        filtre.modifierPrix(new BigDecimal("99.00"));
        commande(Langue.nl).reprendreLignes(List.of(ligne));

        filtre.traduire(new Traductions("Andere naam", "Other name", null, null));

        assertThat(ligne.getLibelleFige()).isEqualTo("Oliefilter");
        assertThat(ligne.getPrixUnitaireHtva()).as("prix figé à l'ajout au panier").isEqualByComparingTo("12.40");
    }
}
