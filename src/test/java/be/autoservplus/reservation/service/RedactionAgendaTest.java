package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedactionAgenda : lieu et révision du fichier d'agenda")
class RedactionAgendaTest {

    @Test
    @DisplayName("un rendez-vous déplacé garde son UID et monte en révision")
    void revisionApresDeplacement() {
        Instant maintenant = Instant.parse("2026-09-10T08:00:00Z");
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        RedactionAgenda redaction = new RedactionAgenda(garage("12", "Bruxelles", "Belgique"), messages,
                Clock.fixed(maintenant, ZoneOffset.UTC), "https://exemple.be");

        Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
        Prestation vidange = new Prestation(new Categorie("ENT", "Entretien", TypeCategorie.SERVICE),
                "VID", "Vidange", new BigDecimal("49.00"), 60);
        PosteAtelier pont = new PosteAtelier("Pont 1");
        Rdv rdv = new Rdv("RDV-2026-0001", marie, golf, pont, Instant.parse("2026-09-15T08:00:00Z"),
                Duration.ofMinutes(30), List.of(vidange), null);

        String avant = redaction.pour(rdv, Locale.FRENCH).contenu();
        rdv.modifierParLeMembre(Instant.parse("2026-09-16T08:00:00Z"), pont, maintenant,
                Duration.ofHours(24), true, null);
        // La version est écrite par JPA au flush. Le test pose ce que le flush poserait.
        ReflectionTestUtils.setField(rdv, "version", 2L);
        String apres = redaction.pour(rdv, Locale.FRENCH).contenu();

        String uid = "UID:" + rdv.getReference() + "@autoservplus";
        assertThat(avant).contains(uid).contains("SEQUENCE:0");
        assertThat(apres).contains(uid).contains("SEQUENCE:2").contains("DTSTART:20260916T080000Z");
    }

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
