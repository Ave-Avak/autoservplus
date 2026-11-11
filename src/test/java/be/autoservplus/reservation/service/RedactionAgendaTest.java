package be.autoservplus.reservation.service;

import be.autoservplus.config.IdentiteGarage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedactionAgenda : lieu du fichier d'agenda")
class RedactionAgendaTest {

    @Test
    @DisplayName("adresse complète sur une ligne, pays compris")
    void adresseComplete() {
        assertThat(RedactionAgenda.lieu(garage("12", "Bruxelles", "Belgique")))
                .isEqualTo("Garage de démonstration, Rue de l'Atelier 12, 1000 Bruxelles, Belgique");
    }

    @Test
    @DisplayName("une partie absente disparaît avec son séparateur")
    void partiesAbsentes() {
        assertThat(RedactionAgenda.lieu(garage("", "Bruxelles", "")))
                .isEqualTo("Garage de démonstration, Rue de l'Atelier, 1000 Bruxelles");
        assertThat(RedactionAgenda.lieu(garage("12", null, " ")))
                .isEqualTo("Garage de démonstration, Rue de l'Atelier 12, 1000");
    }

    private static IdentiteGarage garage(String numeroRue, String localite, String pays) {
        return new IdentiteGarage(
                "Garage Démo", "Garage de démonstration", "SRL",
                "Rue de l'Atelier", numeroRue, "1000", localite, pays,
                "0123.456.789", "RPM Bruxelles", "BE0123456789", "BE68 5390 0754 7034",
                "+32 2 000 00 00", "garage@demo.test");
    }
}
