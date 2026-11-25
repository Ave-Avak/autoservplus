package be.autoservplus.devis.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Traductions;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.vente.domain.Panier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Devis")
class DevisTest {

    private static final Instant EMISSION = Instant.parse("2026-11-24T09:30:00Z");
    private static final LocalDate JOUR = LocalDate.of(2026, 11, 24);

    private final Categorie categorie = new Categorie("PIECES", "Pièces", TypeCategorie.PIECE);
    private final Utilisateur marie = new Utilisateur(
            "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    private final Vehicule golf = new Vehicule(marie, "1-ABC-123", "Volkswagen", "Golf", Motorisation.DIESEL);

    private Piece piece(String libelle, String prixHtva, String taux) {
        Piece piece = new Piece(categorie, libelle, libelle, new BigDecimal(prixHtva));
        piece.setTauxTva(new BigDecimal(taux));
        piece.setQuantiteStock(10);
        return piece;
    }

    private Panier panierDeMarie() {
        Panier panier = new Panier(marie);
        panier.ajouterPiece(piece("Filtre à huile", "9.99", "21"), 2);
        panier.ajouterPiece(piece("Huile 5W30", "20.00", "6"), 1);
        return panier;
    }

    @Test
    @DisplayName("reprend les lignes, les prix et les totaux du panier, ventilés par taux")
    void instantaneDuPanier() {
        Panier panier = panierDeMarie();

        Devis devis = Devis.depuisLePanier("DEV-2026-0001", panier, golf, EMISSION, JOUR, Langue.fr);

        assertThat(devis.getLignes()).extracting(LigneDevis::getLibelle)
                .containsExactly("Filtre à huile", "Huile 5W30");
        assertThat(devis.getLignes()).extracting(LigneDevis::getRang).containsExactly((short) 0, (short) 1);
        assertThat(devis.getLignes().get(0).totalTvac()).isEqualByComparingTo("24.18");
        assertThat(devis.getMontantTvac()).isEqualByComparingTo(panier.totalTvac());
        assertThat(devis.getMontantHtva()).isEqualByComparingTo(panier.totalHtva());
        assertThat(devis.getMontantTva()).isEqualByComparingTo(panier.totalTva());
        assertThat(devis.ventilation()).hasSize(2);
        assertThat(devis.getMontantHtva().add(devis.getMontantTva())).isEqualByComparingTo(devis.getMontantTvac());
    }

    @Test
    @DisplayName("nomme le client et le véhicule tels qu'ils sont à l'émission")
    void clientEtVehicule() {
        Devis devis = Devis.depuisLePanier("DEV-2026-0001", panierDeMarie(), golf, EMISSION, JOUR, Langue.fr);

        assertThat(devis.getClientPrenom()).isEqualTo("Marie");
        assertThat(devis.getClientNom()).isEqualTo("Dupont");
        assertThat(devis.vehiculeDesignation()).isEqualTo("Volkswagen Golf (1-ABC-123)");
        assertThat(devis.getMembre()).isSameAs(marie);
    }

    @Test
    @DisplayName("est valable trente jours depuis le jour de son émission, expiré le lendemain")
    void validite() {
        Devis devis = Devis.depuisLePanier("DEV-2026-0001", panierDeMarie(), golf, EMISSION, JOUR, Langue.fr);

        assertThat(devis.getValableJusquAu()).isEqualTo(LocalDate.of(2026, 12, 24));
        assertThat(devis.estExpireLe(LocalDate.of(2026, 12, 24))).isFalse();
        assertThat(devis.estExpireLe(LocalDate.of(2026, 12, 25))).isTrue();
    }

    @Test
    @DisplayName("écrit ses lignes dans la langue du document, en français pour un article non traduit")
    void langueDesLignes() {
        Piece traduite = piece("Filtre à huile", "9.99", "21");
        traduite.traduire(new Traductions("Oliefilter", null, null, null));
        Panier panier = new Panier(marie);
        panier.ajouterPiece(traduite, 1);
        panier.ajouterPiece(piece("Huile 5W30", "20.00", "6"), 1);

        Devis devis = Devis.depuisLePanier("DEV-2026-0001", panier, golf, EMISSION, JOUR, Langue.nl);

        assertThat(devis.getLangueDocument()).isEqualTo(Langue.nl);
        assertThat(devis.getLignes()).extracting(LigneDevis::getLibelle)
                .containsExactly("Oliefilter", "Huile 5W30");
    }

    @Test
    @DisplayName("refuse un panier vide")
    void panierVide() {
        Panier vide = new Panier(marie);

        assertThatThrownBy(() -> Devis.depuisLePanier("DEV-2026-0001", vide, golf, EMISSION, JOUR, Langue.fr))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refuse le véhicule d'un autre membre")
    void vehiculeDAutrui() {
        Utilisateur paul = new Utilisateur("paul@exemple.be", "$2a$12$h", "Martin", "Paul", TypeUtilisateur.MEMBRE);
        Vehicule clio = new Vehicule(paul, "2-DEF-456", "Renault", "Clio", Motorisation.ESSENCE);

        assertThatThrownBy(() -> Devis.depuisLePanier("DEV-2026-0001", panierDeMarie(), clio,
                EMISSION, JOUR, Langue.fr))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
