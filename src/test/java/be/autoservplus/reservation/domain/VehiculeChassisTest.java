package be.autoservplus.reservation.domain;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Numéro de châssis normalisé par le domaine, à l'ajout comme à la modification (M68, Q-H-54, Q-H-55). */
@DisplayName("Numéro de châssis d'un véhicule")
class VehiculeChassisTest {

    @Test
    @DisplayName("espaces retirés partout, majuscules, saisie vide ou blanche ramenée à NULL")
    void normalisation() {
        assertThat(Vehicule.normaliserChassis(" wvw zzz1kzaw\t000003 ")).isEqualTo("WVWZZZ1KZAW000003");
        assertThat(Vehicule.normaliserChassis("")).isNull();
        assertThat(Vehicule.normaliserChassis("   ")).isNull();
        assertThat(Vehicule.normaliserChassis(null)).isNull();
    }

    @Test
    @DisplayName("aucun contrôle ISO 3779 : un numéro court d'un véhicule ancien est gardé tel quel")
    void horsNorme() {
        assertThat(Vehicule.normaliserChassis("ab 1234")).isEqualTo("AB1234");
    }

    @Test
    @DisplayName("la modification normalise aussi : un champ vidé n'enregistre pas une chaîne vide")
    void modification() {
        Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);

        golf.modifier("VW", "Golf", Motorisation.DIESEL, null, " vf1 rja ");
        assertThat(golf.getNumeroChassis()).isEqualTo("VF1RJA");

        golf.modifier("VW", "Golf", Motorisation.DIESEL, null, "");
        assertThat(golf.getNumeroChassis()).isNull();
    }
}
