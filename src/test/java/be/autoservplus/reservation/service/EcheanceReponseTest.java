package be.autoservplus.reservation.service;

import be.autoservplus.reservation.domain.Indisponibilite;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.repository.IndisponibiliteRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("Échéance de réponse d'une demande en mode manuel")
class EcheanceReponseTest {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    // Semaine du lundi 14 septembre 2026, sans jour férié.
    private static final LocalDate MARDI = LocalDate.of(2026, 9, 15);
    private static final LocalDate MERCREDI = LocalDate.of(2026, 9, 16);
    private static final LocalDate JEUDI = LocalDate.of(2026, 9, 17);
    private static final LocalDate VENDREDI = LocalDate.of(2026, 9, 18);
    private static final LocalDate SAMEDI = LocalDate.of(2026, 9, 19);
    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 21);

    @Mock private PlageOuvertureRepository plages;
    @Mock private IndisponibiliteRepository indisponibilites;

    private EcheanceReponse echeances;
    private final ParametreAtelier reglages = new ParametreAtelier();

    @BeforeEach
    void setUp() {
        echeances = new EcheanceReponse(plages, indisponibilites);
        lenient().when(indisponibilites.chevauchant(any(), any())).thenReturn(List.of());
        ouvertLes(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY));
    }

    private void ouvertLes(Set<DayOfWeek> jours) {
        // doAnswer : un second appel redéfinit la réponse sans rejouer la précédente.
        lenient().doAnswer(appel -> {
            DayOfWeek jour = DayOfWeek.of((short) appel.getArgument(0));
            return jours.contains(jour)
                    ? List.of(new PlageOuverture(jour, LocalTime.of(8, 0), LocalTime.of(18, 0)))
                    : List.of();
        }).when(plages).findByJourSemaineAndActifTrueOrderByHeureDebut(anyShort());
    }

    private static Instant a(LocalDate jour, int heure) {
        return jour.atTime(heure, 0).atZone(BRUXELLES).toInstant();
    }

    @Test
    @DisplayName("demande un mardi après-midi pour le mercredi : midi du mardi est passé, créneau absent")
    void mardiApresMidiPourMercredi() {
        Instant maintenant = a(MARDI, 15);

        assertThat(echeances.pour(maintenant, MERCREDI, reglages)).isEqualTo(a(MARDI, 12));
        assertThat(echeances.laisseLeTempsDeRepondre(MERCREDI, maintenant, reglages)).isFalse();
    }

    @Test
    @DisplayName("demande un mardi matin pour le mercredi : midi du mardi n'est qu'à une heure, créneau absent")
    void mardiMatinTropPres() {
        assertThat(echeances.laisseLeTempsDeRepondre(MERCREDI, a(MARDI, 11), reglages)).isFalse();
        assertThat(echeances.laisseLeTempsDeRepondre(MERCREDI, a(MARDI, 10), reglages)).isTrue();
    }

    @Test
    @DisplayName("rendez-vous un lundi, atelier ouvert le samedi : échéance samedi 12:00")
    void lundiApresSamediOuvert() {
        reglages.modifierDelaiReponse(72);

        assertThat(echeances.pour(a(JEUDI, 10), LUNDI, reglages)).isEqualTo(a(SAMEDI, 12));
    }

    @Test
    @DisplayName("rendez-vous un lundi après un samedi fermé : échéance vendredi 12:00")
    void lundiApresSamediFerme() {
        ouvertLes(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        reglages.modifierDelaiReponse(72);

        assertThat(echeances.pour(a(JEUDI, 10), LUNDI, reglages)).isEqualTo(a(VENDREDI, 12));
    }

    @Test
    @DisplayName("le délai de réponse l'emporte quand il tombe avant midi du dernier jour ouvert")
    void delaiPlusCourt() {
        assertThat(echeances.pour(a(MARDI, 9), LUNDI, reglages)).isEqualTo(a(MERCREDI, 9));
    }

    @Test
    @DisplayName("un jour férié n'est pas un jour ouvert : le jeudi 12 novembre renvoie au mardi 10")
    void jourFerie() {
        LocalDate mardi = LocalDate.of(2026, 11, 10);
        LocalDate jeudi = LocalDate.of(2026, 11, 12);
        reglages.modifierDelaiReponse(72);

        assertThat(echeances.pour(a(LocalDate.of(2026, 11, 9), 9), jeudi, reglages)).isEqualTo(a(mardi, 12));
    }

    @Test
    @DisplayName("un samedi fermé par une indisponibilité de tout l'atelier ne compte pas, une fermeture partielle si")
    void indisponibiliteDeToutLAtelier() {
        reglages.modifierDelaiReponse(72);
        Indisponibilite journee = new Indisponibilite(null, a(SAMEDI, 0), a(SAMEDI.plusDays(1), 0), "Inventaire");
        lenient().when(indisponibilites.chevauchant(a(SAMEDI, 0), a(SAMEDI.plusDays(1), 0)))
                .thenReturn(List.of(journee));
        assertThat(echeances.pour(a(JEUDI, 10), LUNDI, reglages)).isEqualTo(a(VENDREDI, 12));

        Indisponibilite matin = new Indisponibilite(null, a(SAMEDI, 8), a(SAMEDI, 12), "Formation");
        lenient().when(indisponibilites.chevauchant(a(SAMEDI, 0), a(SAMEDI.plusDays(1), 0)))
                .thenReturn(List.of(matin));
        assertThat(echeances.pour(a(JEUDI, 10), LUNDI, reglages)).isEqualTo(a(SAMEDI, 12));
    }

    @Test
    @DisplayName("deux fermetures jointives couvrent la plage, un trou entre elles la laisse ouverte")
    void fermeturesJointives() {
        Indisponibilite matin = new Indisponibilite(null, a(SAMEDI, 8), a(SAMEDI, 13), "a");
        Indisponibilite apresMidi = new Indisponibilite(null, a(SAMEDI, 13), a(SAMEDI, 18), "b");
        Indisponibilite tardive = new Indisponibilite(null, a(SAMEDI, 14), a(SAMEDI, 18), "c");

        assertThat(EcheanceReponse.couverte(a(SAMEDI, 8), a(SAMEDI, 18), List.of(matin, apresMidi))).isTrue();
        assertThat(EcheanceReponse.couverte(a(SAMEDI, 8), a(SAMEDI, 18), List.of(matin, tardive))).isFalse();
    }

    @Test
    @DisplayName("aucun jour ouvert avant le rendez-vous : échue dès la création")
    void aucunJourOuvert() {
        ouvertLes(EnumSet.of(DayOfWeek.MONDAY));
        Instant maintenant = a(VENDREDI, 9);

        assertThat(echeances.pour(maintenant, LUNDI, reglages)).isEqualTo(maintenant);
        assertThat(echeances.laisseLeTempsDeRepondre(LUNDI, maintenant, reglages)).isFalse();
    }
}
