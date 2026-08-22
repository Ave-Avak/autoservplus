package be.autoservplus.vente.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Immuabilite d une ligne rattachee a une commande : piece comptable, elle ne se
 * modifie plus et ne se supprime plus — la garde vit dans l ENTITE, pas dans une
 * convention d appel. Le scenario adverse est celui de la transaction de
 * conversion : la collection du panier n est pas purgee en session, un appelant y
 * tient donc encore la ligne deplacee ; chaque chemin doit se heurter a la garde.
 */
@DisplayName("LignePanier — immuabilite apres rattachement a une commande")
class LignePanierTest {

    private Panier panier;
    private LignePanier ligne;
    private Commande commande;

    @BeforeEach
    void setUp() {
        Utilisateur marie = new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        Categorie freinage = new Categorie("FRE", "Freinage", TypeCategorie.PIECE);
        Piece plaquettes = new Piece(freinage, "FRE-001", "Plaquettes avant", new BigDecimal("19.99"));
        plaquettes.setQuantiteStock(10);

        panier = new Panier(marie);
        ligne = panier.ajouterPiece(plaquettes, 2);
        ReflectionTestUtils.setField(ligne, "id", 1L);

        commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                Instant.parse("2026-09-14T09:00:00Z"));
        commande.reprendreLignes(panier.getLignes());
    }

    @Test
    @DisplayName("changerQuantite sur une ligne de commande : refuse par l'entite")
    void changerQuantiteRefuse() {
        assertThatThrownBy(() -> ligne.changerQuantite(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immuable");
        assertThat(ligne.getQuantite()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("augmenterQuantite sur une ligne de commande : refuse par l'entite")
    void augmenterQuantiteRefuse() {
        assertThatThrownBy(() -> ligne.augmenterQuantite(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immuable");
    }

    @Test
    @DisplayName("re-rattacher la ligne a une autre commande : refuse, le rattachement est definitif")
    void reRattachementRefuse() {
        Commande autre = new Commande("CMD-2026-0002", ligne.getCommande().getMembre(),
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                Instant.parse("2026-09-14T10:00:00Z"));

        assertThatThrownBy(() -> autre.reprendreLignes(java.util.List.of(ligne)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immuable");
        assertThat(ligne.getCommande()).isSameAs(commande);
    }

    /**
     * Chemin adverse realiste : dans la transaction de conversion, la collection du
     * panier contient encore la ligne deplacee. La modifier via l agregat doit
     * echouer sur la garde de l entite, pas reussir en silence.
     */
    @Test
    @DisplayName("via l'agregat panier encore en memoire : la modification echoue aussi")
    void modificationViaLAgregatRefusee() {
        assertThatThrownBy(() -> panier.modifierQuantite(1L, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immuable");
    }

    @Test
    @DisplayName("retirerLigne epargne une ligne de commande : pas de suppression orphanRemoval")
    void retraitEpargneLaLigneDeCommande() {
        boolean retiree = panier.retirerLigne(1L);

        assertThat(retiree)
                .as("La ligne n'est plus une ligne de panier : le retrait ne la trouve pas")
                .isFalse();
        assertThat(ligne.getCommande()).isSameAs(commande);
    }

    @Test
    @DisplayName("vider() epargne les lignes de commande presentes dans la collection en memoire")
    void viderEpargneLesLignesDeCommande() {
        panier.vider();

        assertThat(ligne.getCommande())
                .as("La piece comptable survit au vidage du panier")
                .isSameAs(commande);
    }
}
