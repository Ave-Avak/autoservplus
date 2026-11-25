package be.autoservplus.vente.domain;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.PrestationALHeureRefuseeException;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.domain.UnitePrestation;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une prestation à l'heure ne se met pas au panier (Q-H-36). Le devis F15 naissant du
 * panier, il n'en porte jamais : ce refus suffit à le couvrir.
 */
@DisplayName("Panier et prestation à l'heure")
class PanierPrestationALHeureTest {

    private final Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
            TypeUtilisateur.MEMBRE);
    private final Categorie atelier = new Categorie("ATELIER", "Atelier", TypeCategorie.SERVICE);

    @Test
    @DisplayName("une prestation à l'heure est refusée, le panier reste vide")
    void prestationALHeureRefusee() {
        Panier panier = new Panier(marie);
        Prestation mainDOeuvre = new Prestation(atelier, "MO", "Main-d'œuvre", new BigDecimal("65.00"), 60,
                UnitePrestation.HEURE);

        assertThatThrownBy(() -> panier.ajouterService(mainDOeuvre, 1))
                .isInstanceOf(PrestationALHeureRefuseeException.class);
        assertThat(panier.getLignes()).isEmpty();
    }

    @Test
    @DisplayName("une prestation au forfait entre au panier comme avant")
    void forfaitAccepte() {
        Panier panier = new Panier(marie);

        panier.ajouterService(new Prestation(atelier, "VID", "Vidange", new BigDecimal("49.00"), 30), 1);

        assertThat(panier.getLignes()).hasSize(1);
    }
}
