package be.autoservplus.common.exception;

import be.autoservplus.catalogue.service.SuppressionRefuseeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Separation du message utilisateur et du code de tracabilite.
 *
 * <p>Le code de regle a longtemps ete compose dans le message lui-meme et ressortait
 * a l ecran, partout ou un controleur remonte {@code getMessage()} en message flash.
 * Ce test verrouille les trois canaux pour que le prefixe ne revienne pas.</p>
 */
@DisplayName("RegleMetierException : message utilisateur et code de tracabilite")
class RegleMetierExceptionTest {

    @Nested
    @DisplayName("Message destine a l utilisateur")
    class MessageUtilisateur {

        @Test
        @DisplayName("ne porte aucun prefixe de code")
        void sansPrefixe() {
            var e = new RegleMetierException("RM-01", "L adresse de courriel est obligatoire.");

            assertThat(e.getMessage())
                    .as("le membre lisait « [RM-01] » sans que cela lui apprenne rien")
                    .isEqualTo("L adresse de courriel est obligatoire.")
                    .doesNotContain("RM-01")
                    .doesNotContain("[");
        }

        @Test
        @DisplayName("les sous-classes en heritent, sans avoir a y penser")
        void sousClassesAussi() {
            var e = new SuppressionRefuseeException("Vidange", 3);

            assertThat(e.getMessage())
                    .doesNotContain("RM-29")
                    .doesNotContain("[RM-");
        }

        @Test
        @DisplayName("le constructeur sans code laisse le message intact")
        void sansCode() {
            var e = new RegleMetierException("Un rendez-vous ne peut pas etre marque absent.");

            assertThat(e.getMessage()).isEqualTo("Un rendez-vous ne peut pas etre marque absent.");
            assertThat(e.getCodeRegle()).isNull();
        }
    }

    @Nested
    @DisplayName("Tracabilite")
    class Tracabilite {

        @Test
        @DisplayName("le code reste lisible par le code appelant")
        void codeExpose() {
            // C'est ainsi que RdvController choisit le champ de formulaire a annoter :
            // sur le code, jamais en analysant le texte du message.
            assertThat(new RegleMetierException("RM-11", "Trop tard.").getCodeRegle())
                    .isEqualTo("RM-11");
        }

        @Test
        @DisplayName("toString porte le code, pour les journaux et les traces de pile")
        void codeDansLesJournaux() {
            var e = new RegleMetierException("RM-02", "Mot de passe trop court.");

            assertThat(e.toString())
                    .as("la tracabilite n est pas perdue, elle est deplacee la ou elle sert")
                    .contains("RM-02")
                    .contains("Mot de passe trop court.");
        }

        @Test
        @DisplayName("sans code, toString reste celui de la classe mere")
        void toStringSansCode() {
            assertThat(new RegleMetierException("Message seul.").toString())
                    .contains("Message seul.")
                    .doesNotContain("[null]");
        }
    }
}
