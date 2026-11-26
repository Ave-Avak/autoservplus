package be.autoservplus.vitrine.service;

import be.autoservplus.common.temps.FuseauGarage;
import be.autoservplus.reservation.domain.Indisponibilite;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.repository.IndisponibiliteRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.vitrine.web.dto.EtatDuJour;
import be.autoservplus.vitrine.web.dto.EtatDuJour.Etat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * État du jour du bandeau, sur une semaine d'essai : du lundi au vendredi de 8 h à 12 h et
 * de 13 h à 18 h, le samedi de 9 h à 12 h, fermé le dimanche. Semaine de référence, celle
 * du lundi 23 novembre 2026, sans jour férié.
 */
@DisplayName("État du jour dans le bandeau du site")
class EtatDuJourServiceTest {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    private final PlageOuvertureRepository plages = mock(PlageOuvertureRepository.class);
    private final IndisponibiliteRepository indisponibilites = mock(IndisponibiliteRepository.class);
    private final FuseauGarage fuseau = mock(FuseauGarage.class);
    private final List<Indisponibilite> fermetures = new ArrayList<>();

    @BeforeEach
    void semaineDuGarage() {
        List<PlageOuverture> semaine = new ArrayList<>();
        for (DayOfWeek jour : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            semaine.add(new PlageOuverture(jour, LocalTime.of(8, 0), LocalTime.of(12, 0)));
            semaine.add(new PlageOuverture(jour, LocalTime.of(13, 0), LocalTime.of(18, 0)));
        }
        semaine.add(new PlageOuverture(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        when(plages.findAllByOrderByJourSemaineAscHeureDebutAsc()).thenReturn(semaine);
        when(indisponibilites.chevauchant(any(), any())).thenReturn(fermetures);
        when(fuseau.zone()).thenReturn(BRUXELLES);
    }

    private EtatDuJour a(int jour, int heure, int minute) {
        LocalDateTime ici = LocalDateTime.of(2026, 11, jour, heure, minute);
        Clock horloge = Clock.fixed(ici.atZone(BRUXELLES).toInstant(), ZoneId.of("UTC"));
        return new EtatDuJourService(plages, indisponibilites, fuseau, horloge).maintenant();
    }

    @Test
    @DisplayName("pendant une plage : ouvert jusqu'à sa fin")
    void ouvert() {
        EtatDuJour etat = a(24, 9, 30);

        assertThat(etat.etat()).isEqualTo(Etat.OUVERT);
        assertThat(etat.heure()).isEqualTo("12:00");
        assertThat(etat.cleMessage()).isEqualTo("site.etat.ouvert");
    }

    @Test
    @DisplayName("entre deux plages du même jour : pause, réouverture à 13:00")
    void pause() {
        EtatDuJour etat = a(24, 12, 0);

        assertThat(etat.etat()).isEqualTo(Etat.PAUSE);
        assertThat(etat.heure()).isEqualTo("13:00");
    }

    @Test
    @DisplayName("avant la première plage : fermé, ouvre aujourd'hui")
    void avantLOuverture() {
        EtatDuJour etat = a(24, 7, 15);

        assertThat(etat.cleMessage()).isEqualTo("site.etat.ouvre.aujourdhui");
        assertThat(etat.heure()).isEqualTo("08:00");
    }

    @Test
    @DisplayName("après la dernière plage : fermé, ouvre demain")
    void apresLaFermeture() {
        EtatDuJour etat = a(24, 18, 0);

        assertThat(etat.cleMessage()).isEqualTo("site.etat.ouvre.demain");
        assertThat(etat.heure()).isEqualTo("08:00");
        assertThat(etat.exceptionnel()).isFalse();
    }

    @Test
    @DisplayName("samedi après midi, veille d'un dimanche fermé : ouvre lundi")
    void veilleDUnJourFerme() {
        EtatDuJour etat = a(28, 14, 0);

        assertThat(etat.cleMessage()).isEqualTo("site.etat.ouvre.jour");
        assertThat(etat.cleJour()).isEqualTo("site.etat.jour.lundi");
        assertThat(etat.dansNJours()).isEqualTo(2);
    }

    @Test
    @DisplayName("jour férié en semaine : fermeture exceptionnelle, ouvre le lendemain")
    void jourFerie() {
        // Mercredi 11 novembre 2026, Armistice.
        EtatDuJour etat = a(11, 10, 0);

        assertThat(etat.etat()).isEqualTo(Etat.FERME);
        assertThat(etat.exceptionnel()).isTrue();
        assertThat(etat.cleMessage()).isEqualTo("site.etat.ouvre.demain");
    }

    @Test
    @DisplayName("fermeture de tout l'atelier sur l'après-midi : plage fermée, ouvre demain")
    void fermetureDeLAtelier() {
        fermetures.add(new Indisponibilite(null,
                LocalDateTime.of(2026, 11, 24, 13, 0).atZone(BRUXELLES).toInstant(),
                LocalDateTime.of(2026, 11, 24, 18, 0).atZone(BRUXELLES).toInstant(), "Formation"));

        EtatDuJour etat = a(24, 12, 30);

        assertThat(etat.cleMessage()).isEqualTo("site.etat.ouvre.demain");
    }

    @Test
    @DisplayName("un poste en panne ne ferme pas le garage")
    void posteEnPanne() {
        fermetures.add(new Indisponibilite(new PosteAtelier("Pont 1"),
                LocalDateTime.of(2026, 11, 24, 8, 0).atZone(BRUXELLES).toInstant(),
                LocalDateTime.of(2026, 11, 24, 18, 0).atZone(BRUXELLES).toInstant(), "Pont en panne"));

        assertThat(a(24, 9, 30).etat()).isEqualTo(Etat.OUVERT);
    }

    @Test
    @DisplayName("aucune ouverture dans les quinze jours : renvoi aux horaires")
    void fermeLongtemps() {
        when(plages.findAllByOrderByJourSemaineAscHeureDebutAsc()).thenReturn(List.of());

        EtatDuJour etat = a(24, 9, 30);

        assertThat(etat.etat()).isEqualTo(Etat.FERME_LONGTEMPS);
        assertThat(etat.cleMessage()).isEqualTo("site.etat.ferme");
    }
}
