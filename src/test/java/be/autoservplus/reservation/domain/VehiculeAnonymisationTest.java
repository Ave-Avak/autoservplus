package be.autoservplus.reservation.domain;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Anonymisation d un vehicule (F23, art. 17 RGPD).
 *
 * <p>Le critere que ce test verrouille : <b>part ce qui identifie une personne ou
 * renseigne sur son comportement ; reste ce qui decrit l objet.</b> Sans lui, rien
 * n empeche de reintroduire l effacement de l annee, qui contredisait ce principe et
 * qu il a fallu retirer en consolidation.</p>
 */
@DisplayName("Vehicule.anonymiser (F23)")
class VehiculeAnonymisationTest {

    private static final String MARQUEUR = "ANON-42";

    private Vehicule golf;

    @BeforeEach
    void setUp() {
        Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        golf = new Vehicule(marie, "1-ABC-123", "Volkswagen", "Golf", Motorisation.DIESEL);
        golf.setAnnee((short) 2015);
        golf.setNumeroChassis("WVWZZZ1KZAW000001");
        golf.mettreAJourKilometrage(142_000);
    }

    @Nested
    @DisplayName("Ce qui part")
    class CeQuiPart {

        @Test
        @DisplayName("la plaque est remplacee par le marqueur fourni")
        void plaqueRemplacee() {
            golf.anonymiser(MARQUEUR);

            assertThat(golf.getPlaque())
                    .as("la plaque identifie indirectement une personne aupres de la DIV")
                    .isEqualTo(MARQUEUR);
        }

        @Test
        @DisplayName("le numero de chassis est efface")
        void chassisEfface() {
            golf.anonymiser(MARQUEUR);

            assertThat(golf.getNumeroChassis()).isNull();
        }

        @Test
        @DisplayName("le kilometrage est efface : il renseigne sur les deplacements")
        void kilometrageEfface() {
            golf.anonymiser(MARQUEUR);

            assertThat(golf.getKilometrage())
                    .as("une donnee de comportement, donc rattachee a la personne")
                    .isNull();
        }

        @Test
        @DisplayName("le vehicule sort du parc actif")
        void retireDuParc() {
            golf.anonymiser(MARQUEUR);

            assertThat(golf.isActif()).isFalse();
        }
    }

    @Nested
    @DisplayName("Ce qui reste")
    class CeQuiReste {

        @Test
        @DisplayName("marque, modele et motorisation decrivent l objet, pas la personne")
        void caracteristiquesConservees() {
            golf.anonymiser(MARQUEUR);

            assertThat(golf.getMarque()).isEqualTo("Volkswagen");
            assertThat(golf.getModele()).isEqualTo("Golf");
            assertThat(golf.getMotorisation()).isEqualTo(Motorisation.DIESEL);
        }

        @Test
        @DisplayName("l annee reste : meme categorie que la marque et le modele")
        void anneeConservee() {
            golf.anonymiser(MARQUEUR);

            assertThat(golf.getAnnee())
                    .as("« Golf diesel de 2015 » ne designe pas plus quelqu un que "
                            + "« Golf diesel » ; l effacer contredisait le principe retenu")
                    .isEqualTo((short) 2015);
        }
    }

    @Nested
    @DisplayName("Garde")
    class Garde {

        @Test
        @DisplayName("refuse un marqueur absent : la plaque doit rester unique et non nulle")
        void marqueurObligatoire() {
            assertThatThrownBy(() -> golf.anonymiser(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("est rejouable sans effet supplementaire")
        void idempotent() {
            golf.anonymiser(MARQUEUR);
            golf.anonymiser(MARQUEUR);

            assertThat(golf.getPlaque()).isEqualTo(MARQUEUR);
            assertThat(golf.getAnnee()).isEqualTo((short) 2015);
            assertThat(golf.isActif()).isFalse();
        }
    }
}
