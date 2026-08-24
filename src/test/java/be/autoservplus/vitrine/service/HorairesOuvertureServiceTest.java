package be.autoservplus.vitrine.service;

import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.vitrine.web.dto.JourOuvertureVue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regroupement des plages d ouverture pour la vitrine.
 *
 * <p>Le service ne calcule rien de complexe ; ce qui merite d etre verrouille, c est
 * ce qu il fait des cas <b>absents</b> — un jour sans plage, une plage desactivee —
 * parce que ce sont eux qui produisent une page fausse plutot qu une page vide.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Horaires d ouverture de la vitrine")
class HorairesOuvertureServiceTest {

    @Mock private PlageOuvertureRepository plages;

    private HorairesOuvertureService service;

    private static PlageOuverture plage(DayOfWeek jour, String debut, String fin) {
        return new PlageOuverture(jour, LocalTime.parse(debut), LocalTime.parse(fin));
    }

    private List<JourOuvertureVue> semaineAvec(PlageOuverture... plagesEnBase) {
        service = new HorairesOuvertureService(plages);
        when(this.plages.findAllByOrderByJourSemaineAscHeureDebutAsc())
                .thenReturn(List.of(plagesEnBase));
        return service.semaine();
    }

    @Test
    @DisplayName("rend les sept jours, dans l ordre ISO lundi -> dimanche")
    void septJoursDansLOrdre() {
        List<JourOuvertureVue> semaine = semaineAvec(plage(DayOfWeek.MONDAY, "08:00", "12:00"));

        assertThat(semaine).hasSize(7);
        assertThat(semaine.get(0).cleJour()).isEqualTo("vitrine.jour.lundi");
        assertThat(semaine.get(6).cleJour()).isEqualTo("vitrine.jour.dimanche");
    }

    /**
     * Le cas du garage reel : matin et apres-midi separes par la pause de midi. Les
     * deux plages doivent rester distinctes — les fusionner en « 08:00 – 18:00 »
     * annoncerait une ouverture a l heure du diner.
     */
    @Test
    @DisplayName("garde distinctes les deux plages d une meme journee, dans l ordre")
    void matinEtApresMidi() {
        List<JourOuvertureVue> semaine = semaineAvec(
                plage(DayOfWeek.MONDAY, "08:00", "12:00"),
                plage(DayOfWeek.MONDAY, "13:00", "18:00"));

        assertThat(semaine.get(0).plages()).containsExactly("08:00 – 12:00", "13:00 – 18:00");
        assertThat(semaine.get(0).ferme()).isFalse();
    }

    /**
     * Un jour sans plage n est pas omis : « Fermé » est une information, une ligne
     * manquante n en est pas une. Le lecteur ne saurait pas distinguer une fermeture
     * d une saisie oubliee.
     */
    @Test
    @DisplayName("marque ferme un jour sans aucune plage")
    void jourSansPlage() {
        List<JourOuvertureVue> semaine = semaineAvec(plage(DayOfWeek.MONDAY, "08:00", "12:00"));

        JourOuvertureVue dimanche = semaine.get(6);
        assertThat(dimanche.ferme()).isTrue();
        assertThat(dimanche.plages()).isEmpty();
    }

    /**
     * Une plage desactivee reste en base — elle porte l historique — mais ne doit
     * pas etre annoncee au public : le client se deplacerait pour rien. Le
     * repository ne filtrant pas l activite, le tri est ici et nulle part ailleurs.
     */
    @Test
    @DisplayName("ignore une plage desactivee")
    void plageDesactivee() {
        PlageOuverture retiree = plage(DayOfWeek.SATURDAY, "09:00", "13:00");
        retiree.desactiver();

        List<JourOuvertureVue> semaine = semaineAvec(plage(DayOfWeek.MONDAY, "08:00", "12:00"), retiree);

        assertThat(semaine.get(5).ferme()).isTrue();
    }

    /**
     * Format {@code HH:mm} fixe, jamais celui de la locale : un horaire de garage se
     * lit pareil dans les trois versions du site, et une bascule de langue ne doit
     * pas donner l impression que les heures ont change.
     */
    @Test
    @DisplayName("formate les heures en HH:mm, sans dependre de la locale")
    void formatFixe() {
        List<JourOuvertureVue> semaine = semaineAvec(plage(DayOfWeek.FRIDAY, "08:00", "17:00"));

        assertThat(semaine.get(4).plages()).containsExactly("08:00 – 17:00");
    }
}
