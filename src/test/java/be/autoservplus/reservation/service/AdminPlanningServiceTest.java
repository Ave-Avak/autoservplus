package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.HorairePlage;
import be.autoservplus.reservation.domain.Indisponibilite;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.PlageOuverture;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.IndisponibiliteRepository;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.PlageOuvertureRepository;
import be.autoservplus.reservation.repository.PosteAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("AdminPlanningService")
class AdminPlanningServiceTest {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    private static final LocalDate JEUDI = LocalDate.of(2026, 11, 19);

    private final Utilisateur marie = new Utilisateur("marie@exemple.be", "h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    private final Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
    private final Prestation vidange = new Prestation(
            new Categorie("ENT", "Entretien", TypeCategorie.SERVICE), "VID", "Vidange", new BigDecimal("49.00"), 60);
    private final PosteAtelier pont1 = poste("Pont 1", 1L);
    private final PosteAtelier pont2 = poste("Pont 2", 2L);

    @Test
    @DisplayName("un rendez-vous couvert par l'ancien horaire et plus par aucune plage est découvert")
    void rendezVousDecouvert() {
        HorairePlage avant = horaire(8, 18);
        Rdv aQuatorze = rdv(pont1, 14);
        Rdv aNeuf = rdv(pont1, 9);

        List<Rdv> decouverts = AdminPlanningService.rendezVousDecouverts(
                avant, List.of(horaire(8, 12)), List.of(aQuatorze, aNeuf), BRUXELLES);

        assertThat(decouverts).containsExactly(aQuatorze);
    }

    @Test
    @DisplayName("un rendez-vous qu'une autre plage active couvre n'est pas en conflit")
    void couvertParUneAutrePlage() {
        Rdv aQuatorze = rdv(pont1, 14);

        assertThat(AdminPlanningService.rendezVousDecouverts(horaire(8, 18),
                List.of(horaire(8, 12), horaire(13, 18)), List.of(aQuatorze), BRUXELLES)).isEmpty();
    }

    @Test
    @DisplayName("un rendez-vous d'un autre jour ou hors de l'ancien horaire n'est pas concerné")
    void horsPeriode() {
        Rdv vendredi = new Rdv("RDV-T-0002", marie, golf, pont1,
                JEUDI.plusDays(1).atTime(14, 0).atZone(BRUXELLES).toInstant(), Duration.ofMinutes(30), List.of(vidange), null);
        Rdv aSeize = rdv(pont1, 16);

        assertThat(AdminPlanningService.rendezVousDecouverts(horaire(8, 12), List.of(),
                List.of(vendredi, aSeize), BRUXELLES)).isEmpty();
    }

    @Test
    @DisplayName("une ancienne plage inactive ne découvre rien")
    void ancienneInactive() {
        HorairePlage inactive = new HorairePlage(DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(18, 0), false);

        assertThat(AdminPlanningService.rendezVousDecouverts(inactive, List.of(),
                List.of(rdv(pont1, 14)), BRUXELLES)).isEmpty();
    }

    @Test
    @DisplayName("une indisponibilité de poste ne touche que ce poste, une fermeture touche tout l'atelier")
    void touchePar() {
        Instant debut = JEUDI.atTime(8, 0).atZone(BRUXELLES).toInstant();
        Instant fin = JEUDI.atTime(12, 0).atZone(BRUXELLES).toInstant();
        Rdv surPont1 = rdv(pont1, 10);
        Rdv surPont2 = rdv(pont2, 10);
        Rdv apres = rdv(pont1, 12);
        Indisponibilite panne = new Indisponibilite(pont1, debut, fin, "Panne");
        Indisponibilite fermeture = new Indisponibilite(null, debut, fin, "Formation");

        assertThat(AdminPlanningService.touchePar(panne, pont1, surPont1.getDebut(), surPont1.getFin())).isTrue();
        assertThat(AdminPlanningService.touchePar(panne, pont2, surPont2.getDebut(), surPont2.getFin())).isFalse();
        assertThat(AdminPlanningService.touchePar(panne, pont1, apres.getDebut(), apres.getFin())).isFalse();
        assertThat(AdminPlanningService.touchePar(fermeture, pont2, surPont2.getDebut(), surPont2.getFin())).isTrue();
    }

    @Test
    @DisplayName("la violation d'exclusion (23P01) devient un refus sous le champ de début")
    void chevauchementTraduit() {
        PlageOuvertureRepository plages = mock(PlageOuvertureRepository.class);
        when(plages.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("x",
                new SQLException("conflicting key value violates exclusion constraint", "23P01")));
        AdminPlanningService service = service(plages);

        assertThatThrownBy(() -> service.creerPlage(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), true))
                .isInstanceOfSatisfying(RefusPlanning.class, refus -> {
                    assertThat(refus.getChamp()).isEqualTo("heureDebut");
                    assertThat(refus.getCleMessage()).isEqualTo("admin.planning.plage.erreur.chevauchement");
                });
    }

    @Test
    @DisplayName("une autre violation d'intégrité n'est pas maquillée en chevauchement")
    void autreViolationPropagee() {
        PlageOuvertureRepository plages = mock(PlageOuvertureRepository.class);
        when(plages.saveAndFlush(any(PlageOuverture.class))).thenThrow(new DataIntegrityViolationException("x",
                new SQLException("check violation", "23514")));
        AdminPlanningService service = service(plages);

        assertThatThrownBy(() -> service.creerPlage(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("l'état SQL est cherché dans toute la chaîne des causes")
    void etatSql() {
        Exception enveloppe = new RuntimeException(new IllegalStateException(new SQLException("m", "23P01")));

        assertThat(EtatSql.de(enveloppe)).isEqualTo("23P01");
        assertThat(EtatSql.estUneExclusion(enveloppe)).isTrue();
        assertThat(EtatSql.de(new RuntimeException("sans cause"))).isNull();
        assertThat(EtatSql.estUneExclusion(new SQLException("unicité", "23505"))).isFalse();
    }

    private AdminPlanningService service(PlageOuvertureRepository plages) {
        return new AdminPlanningService(plages, mock(PosteAtelierRepository.class),
                mock(IndisponibiliteRepository.class), mock(RdvRepository.class),
                mock(ParametreAtelierRepository.class), Clock.systemUTC());
    }

    private static HorairePlage horaire(int debut, int fin) {
        return new HorairePlage(DayOfWeek.THURSDAY, LocalTime.of(debut, 0), LocalTime.of(fin, 0), true);
    }

    private Rdv rdv(PosteAtelier poste, int heure) {
        return new Rdv("RDV-T-" + UUID.randomUUID().toString().substring(0, 4), marie, golf, poste,
                JEUDI.atTime(heure, 0).atZone(BRUXELLES).toInstant(), Duration.ofMinutes(30), List.of(vidange), null);
    }

    private static PosteAtelier poste(String libelle, long id) {
        PosteAtelier poste = new PosteAtelier(libelle);
        ReflectionTestUtils.setField(poste, "id", id);
        return poste;
    }
}
