package be.autoservplus.pilotage.service.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Période du tableau de bord (A25)")
class PeriodeTableauTest {

    /** Un samedi, en fin de mois : la semaine déborde sur décembre, le mois non. */
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 28);
    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    @Test
    @DisplayName("sans rien de saisi, le mois en cours, entier")
    void moisParDefaut() {
        PeriodeTableau periode = PeriodeTableau.de(null, null, null, AUJOURDHUI);

        assertThat(periode.du()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(periode.au()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(periode.code()).isEqualTo("mois");
        assertThat(periode.duSaisi()).isNull();
        assertThat(PeriodeTableau.de("inconnu", null, null, AUJOURDHUI)).isEqualTo(periode);
    }

    @Test
    @DisplayName("le jour, et la semaine du lundi au dimanche")
    void jourEtSemaine() {
        PeriodeTableau jour = PeriodeTableau.de("jour", null, null, AUJOURDHUI);
        assertThat(jour.du()).isEqualTo(AUJOURDHUI);
        assertThat(jour.au()).isEqualTo(AUJOURDHUI);
        assertThat(jour.jours()).containsExactly(AUJOURDHUI);

        PeriodeTableau semaine = PeriodeTableau.de("semaine", null, null, AUJOURDHUI);
        assertThat(semaine.du()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(semaine.au()).isEqualTo(LocalDate.of(2026, 11, 29));
        assertThat(semaine.jours()).hasSize(7);
    }

    @Test
    @DisplayName("un lundi est le premier jour de sa semaine, un dimanche le dernier")
    void bornesDeSemaine() {
        assertThat(PeriodeTableau.de("semaine", null, null, LocalDate.of(2026, 11, 23)).du())
                .isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(PeriodeTableau.de("semaine", null, null, LocalDate.of(2026, 11, 29)).au())
                .isEqualTo(LocalDate.of(2026, 11, 29));
    }

    @Test
    @DisplayName("une date saisie l'emporte sur le raccourci")
    void intervalleSaisi() {
        PeriodeTableau periode = PeriodeTableau.de("jour", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9), AUJOURDHUI);

        assertThat(periode.raccourci()).isNull();
        assertThat(periode.code()).isNull();
        assertThat(periode.duSaisi()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(periode.auSaisi()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(periode.jours()).hasSize(5);
    }

    @Test
    @DisplayName("une borne seule est complétée")
    void borneSeule() {
        PeriodeTableau depuis = PeriodeTableau.de(null, LocalDate.of(2026, 11, 10), null, AUJOURDHUI);
        assertThat(depuis.au()).as("« du » seul court jusqu'à aujourd'hui").isEqualTo(AUJOURDHUI);

        PeriodeTableau futur = PeriodeTableau.de(null, LocalDate.of(2026, 12, 24), null, AUJOURDHUI);
        assertThat(futur.au()).as("un début à venir donne une journée").isEqualTo(LocalDate.of(2026, 12, 24));

        PeriodeTableau jusqua = PeriodeTableau.de(null, null, LocalDate.of(2026, 10, 20), AUJOURDHUI);
        assertThat(jusqua.du()).as("« au » seul part du premier jour de son mois").isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("une période inversée est dite telle, et n'a aucun jour")
    void inversee() {
        PeriodeTableau periode = PeriodeTableau.de(null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 1), AUJOURDHUI);

        assertThat(periode.inversee()).isTrue();
        assertThat(periode.jours()).isEmpty();
    }

    @Test
    @DisplayName("les bornes sont celles des jours de l'atelier, fin comprise")
    void bornesEnInstants() {
        PeriodeTableau periode = PeriodeTableau.de("jour", null, null, AUJOURDHUI);

        assertThat(periode.debut(BRUXELLES)).isEqualTo(Instant.parse("2026-11-27T23:00:00Z"));
        assertThat(periode.finExclue(BRUXELLES)).isEqualTo(Instant.parse("2026-11-28T23:00:00Z"));
    }
}
