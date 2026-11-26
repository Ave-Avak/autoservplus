package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.*;
import be.autoservplus.reservation.repository.*;
import be.autoservplus.reservation.service.dto.CreneauDisponible;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests du calcul des disponibilites, avec horloge fixe.
 *
 * <p>Repere temporel : nous sommes le lundi 14 septembre 2026 a 08:00 UTC. Le garage
 * est a Bruxelles (UTC+2 en septembre). Une plage 08:00-12:00 locale correspond donc
 * a 06:00-10:00 UTC.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisponibiliteService")
class DisponibiliteServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-09-14T08:00:00Z");
    private static final LocalDate MERCREDI = LocalDate.of(2026, 9, 16);
    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    @Mock private ParametreAtelierRepository parametres;
    @Mock private PlageOuvertureRepository plages;
    @Mock private PosteAtelierRepository postes;
    @Mock private IndisponibiliteRepository indisponibilites;
    @Mock private RdvRepository rdvs;

    private final ParametreAtelier reglages = new ParametreAtelier();
    private final PosteAtelier pont1 = new PosteAtelier("Pont 1");
    private final PosteAtelier pont2 = new PosteAtelier("Pont 2");

    private DisponibiliteService service;

    @BeforeEach
    void setUp() {
        service = new DisponibiliteService(parametres, plages, postes, indisponibilites, rdvs,
                new EcheanceReponse(plages, indisponibilites), Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        lenient().when(parametres.courants()).thenReturn(reglages);
        lenient().when(indisponibilites.chevauchant(any(), any())).thenReturn(List.of());
        lenient().when(rdvs.actifsChevauchant(any(), any(), any())).thenReturn(List.of());
        lenient().when(plages.findByJourSemaineAndActifTrueOrderByHeureDebut(anyShort())).thenReturn(List.of());
    }

    private void ouvertLeMercrediDe8a12() {
        when(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 3))
                .thenReturn(List.of(new PlageOuverture(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
    }

    private static Instant local(LocalDate jour, int heure, int minute) {
        return jour.atTime(heure, minute).atZone(BRUXELLES).toInstant();
    }

    private static List<Instant> debuts(List<CreneauDisponible> creneaux) {
        return creneaux.stream().map(CreneauDisponible::debut).toList();
    }

    @Nested
    @DisplayName("confirmation manuelle : marge de réponse du garage")
    class ConfirmationManuelle {

        @BeforeEach
        void modeManuel() {
            reglages.modifier(reglages.getFuseauHoraire(), reglages.getPasMinutes(), reglages.getTamponMinutes(),
                    0, reglages.getHorizonJours(), reglages.getDelaiAnnulationHeures(), false,
                    reglages.getMaxRdvEnAttenteParMembre());
        }

        @Test
        @DisplayName("le mardi est ouvert : le mercredi est proposé à une demande du lundi, pas à une demande du mardi après-midi")
        void mercrediSelonLHeureDeLaDemande() {
            ouvertLeMercrediDe8a12();
            lenient().when(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 2))
                    .thenReturn(List.of(new PlageOuverture(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(18, 0))));
            lenient().when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));

            assertThat(service.creneauxDuJour(MERCREDI, 60)).isNotEmpty();
            assertThat(service.estReservable(local(MERCREDI, 9, 0), local(MERCREDI, 10, 0))).isTrue();

            DisponibiliteService mardiApresMidi = new DisponibiliteService(parametres, plages, postes,
                    indisponibilites, rdvs, new EcheanceReponse(plages, indisponibilites),
                    Clock.fixed(local(MERCREDI.minusDays(1), 15, 0), ZoneOffset.UTC));
            assertThat(mardiApresMidi.creneauxDuJour(MERCREDI, 60)).isEmpty();
            assertThat(mardiApresMidi.estReservable(local(MERCREDI, 9, 0), local(MERCREDI, 10, 0))).isFalse();
        }

        @Test
        @DisplayName("aucun jour ouvert avant le mercredi : rien n'est proposé en mode manuel")
        void aucunJourOuvertAvant() {
            // Le mercredi n'est jamais lu : le refus tombe avant, faute de jour ouvert.
            lenient().when(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 3))
                    .thenReturn(List.of(new PlageOuverture(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
            lenient().when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));

            assertThat(service.creneauxDuJour(MERCREDI, 60)).isEmpty();
        }
    }

    @Nested
    @DisplayName("jours fériés")
    class JoursFeriesLegaux {

        private final LocalDate armistice = LocalDate.of(2026, 11, 11);

        @Test
        @DisplayName("un mercredi férié n'offre aucun créneau, le mercredi précédent si")
        void mercrediFerie() {
            when(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 3))
                    .thenReturn(List.of(new PlageOuverture(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
            lenient().when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));

            assertThat(service.creneauxDuJour(armistice, 60)).isEmpty();
            assertThat(service.creneauxDuJour(armistice.minusWeeks(1), 60)).isNotEmpty();
        }

        @Test
        @DisplayName("un intervalle d'un jour férié n'est pas réservable")
        void nonReservable() {
            when(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 3))
                    .thenReturn(List.of(new PlageOuverture(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));

            assertThat(service.estReservable(local(armistice, 9, 0), local(armistice, 10, 0))).isFalse();
            assertThat(service.estReservable(local(armistice.minusWeeks(1), 9, 0),
                    local(armistice.minusWeeks(1), 10, 0))).isTrue();
        }
    }

    @Nested
    @DisplayName("cas vides")
    class CasVides {

        @Test
        @DisplayName("aucun poste actif : rien a proposer")
        void sansPoste() {
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of());

            assertThat(service.creneauxDuJour(MERCREDI, 60)).isEmpty();
        }

        @Test
        @DisplayName("jour sans plage d ouverture : rien a proposer")
        void sansPlage() {
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));

            assertThat(service.creneauxDuJour(MERCREDI, 60)).isEmpty();
        }

        @Test
        @DisplayName("au-dela de l horizon : rien a proposer")
        void horsHorizon() {

            assertThat(service.creneauxDuJour(LocalDate.of(2026, 12, 1), 60)).isEmpty();
        }
    }

    @Nested
    @DisplayName("generation des candidats")
    class Candidats {

        @Test
        @DisplayName("une plage de 4 h, pas de 30 min, duree 60 : sept departs")
        void septDeparts() {
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));

            List<CreneauDisponible> creneaux = service.creneauxDuJour(MERCREDI, 60);

            assertThat(debuts(creneaux)).containsExactly(
                    local(MERCREDI, 8, 0), local(MERCREDI, 8, 30), local(MERCREDI, 9, 0),
                    local(MERCREDI, 9, 30), local(MERCREDI, 10, 0), local(MERCREDI, 10, 30),
                    local(MERCREDI, 11, 0));
            assertThat(creneaux).allSatisfy(c -> {
                assertThat(c.poste()).isEqualTo(pont1);
                assertThat(c.postesLibres()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("80 minutes sont arrondies a 90 : le dernier depart recule")
        void dureeArrondie() {
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));

            List<CreneauDisponible> creneaux = service.creneauxDuJour(MERCREDI, 80);

            assertThat(debuts(creneaux)).last().isEqualTo(local(MERCREDI, 10, 30));
            assertThat(creneaux.get(0).fin()).isEqualTo(local(MERCREDI, 9, 30));
        }

        @Test
        @DisplayName("le delai minimal ecarte les departs trop proches")
        void delaiMinimal() {
            // Demain mardi 15 : plusTot = 15/09 08:00 UTC = 10:00 locale.
            LocalDate mardi = LocalDate.of(2026, 9, 15);
            when(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 2))
                    .thenReturn(List.of(new PlageOuverture(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));

            assertThat(debuts(service.creneauxDuJour(mardi, 60)))
                    .containsExactly(local(mardi, 10, 0), local(mardi, 10, 30), local(mardi, 11, 0));
        }
    }

    @Nested
    @DisplayName("indisponibilites")
    class Indisponibilites {

        @Test
        @DisplayName("une fermeture de l atelier retire les departs qui la chevauchent")
        void fermetureAtelier() {
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));
            when(indisponibilites.chevauchant(any(), any())).thenReturn(List.of(
                    new Indisponibilite(null, local(MERCREDI, 8, 0), local(MERCREDI, 10, 0), "Formation")));

            assertThat(debuts(service.creneauxDuJour(MERCREDI, 60)))
                    .containsExactly(local(MERCREDI, 10, 0), local(MERCREDI, 10, 30), local(MERCREDI, 11, 0));
        }

        @Test
        @DisplayName("un poste bloque ne retire rien si un autre est libre, mais compte un poste de moins")
        void posteBloque() {
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1, pont2));
            when(indisponibilites.chevauchant(any(), any())).thenReturn(List.of(
                    new Indisponibilite(pont1, local(MERCREDI, 8, 0), local(MERCREDI, 10, 0), "Panne")));

            List<CreneauDisponible> creneaux = service.creneauxDuJour(MERCREDI, 60);

            assertThat(creneaux).hasSize(7);
            CreneauDisponible a8h = creneaux.get(0);
            assertThat(a8h.postesLibres()).isEqualTo(1);
            assertThat(a8h.poste()).isEqualTo(pont2);
            CreneauDisponible a10h = creneaux.get(4);
            assertThat(a10h.postesLibres()).isEqualTo(2);
            assertThat(a10h.poste()).isEqualTo(pont1);
        }

        @Test
        @DisplayName("le tampon ne s applique pas aux indisponibilites")
        void pasDeTamponSurIndisponibilite() {
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));
            when(indisponibilites.chevauchant(any(), any())).thenReturn(List.of(
                    new Indisponibilite(null, local(MERCREDI, 8, 0), local(MERCREDI, 9, 0), "Livraison")));

            assertThat(debuts(service.creneauxDuJour(MERCREDI, 60))).first().isEqualTo(local(MERCREDI, 9, 0));
        }
    }

    @Nested
    @DisplayName("rendez-vous existants")
    class RendezVous {

        private Rdv rdvSur(PosteAtelier poste, Instant debut) {
            Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
            Categorie cat = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);
            Prestation p = new Prestation(cat, "VID", "Vidange", new BigDecimal("49.00"), 60);
            return new Rdv("RDV-2026-0001", marie, golf, poste, debut, Duration.ofMinutes(30), List.of(p), null);
        }

        @Test
        @DisplayName("un rendez-vous occupe son poste, tampon compris")
        void occupeAvecTampon() {
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));
            when(rdvs.actifsChevauchant(any(), any(), any()))
                    .thenReturn(List.of(rdvSur(pont1, local(MERCREDI, 8, 0)))); // 08:00-09:00

            // Tampon de 10 min : un depart a 09:00 empiete sur [07:50, 09:10) -> exclu.
            assertThat(debuts(service.creneauxDuJour(MERCREDI, 60)))
                    .containsExactly(local(MERCREDI, 9, 30), local(MERCREDI, 10, 0),
                            local(MERCREDI, 10, 30), local(MERCREDI, 11, 0));
        }

        @Test
        @DisplayName("avec deux postes, un rendez-vous n en bloque qu un")
        void unSeulPosteOccupe() {
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1, pont2));
            when(rdvs.actifsChevauchant(any(), any(), any()))
                    .thenReturn(List.of(rdvSur(pont1, local(MERCREDI, 8, 0))));

            List<CreneauDisponible> creneaux = service.creneauxDuJour(MERCREDI, 60);

            assertThat(creneaux).hasSize(7);
            assertThat(creneaux.get(0).poste()).isEqualTo(pont2);
            assertThat(creneaux.get(0).postesLibres()).isEqualTo(1);
            assertThat(creneaux.get(3).postesLibres()).isEqualTo(2);
        }

        @Test
        @DisplayName("sans tampon, un depart colle a la fin du precedent")
        void sansTampon() {
            reglages.modifier("Europe/Brussels", 30, 0, 24, 60, 24, true, 3);
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));
            when(rdvs.actifsChevauchant(any(), any(), any()))
                    .thenReturn(List.of(rdvSur(pont1, local(MERCREDI, 8, 0))));

            assertThat(debuts(service.creneauxDuJour(MERCREDI, 60))).first().isEqualTo(local(MERCREDI, 9, 0));
        }
    }

    @Nested
    @DisplayName("premierPosteLibre et estReservable")
    class Verification {

        @Test
        @DisplayName("retourne vide quand l atelier est ferme")
        void videSiFerme() {
            Instant d = local(MERCREDI, 9, 0);
            Instant f = local(MERCREDI, 10, 0);
            when(indisponibilites.chevauchant(d, f)).thenReturn(List.of(
                    new Indisponibilite(null, local(MERCREDI, 8, 0), local(MERCREDI, 12, 0), "Conge")));

            assertThat(service.premierPosteLibre(d, f)).isEmpty();
        }

        @Test
        @DisplayName("retourne le premier poste libre dans l ordre")
        void premierLibre() {
            Instant d = local(MERCREDI, 9, 0);
            Instant f = local(MERCREDI, 10, 0);
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1, pont2));

            assertThat(service.premierPosteLibre(d, f)).contains(pont1);
        }

        @Test
        @DisplayName("un intervalle dans une plage ouverte est reservable")
        void reservableDansLaPlage() {
            ouvertLeMercrediDe8a12();
            assertThat(service.estReservable(local(MERCREDI, 9, 0), local(MERCREDI, 10, 0))).isTrue();
        }

        @Test
        @DisplayName("un intervalle qui deborde de la plage ne l est pas")
        void pasReservableHorsPlage() {
            ouvertLeMercrediDe8a12();
            assertThat(service.estReservable(local(MERCREDI, 11, 30), local(MERCREDI, 12, 30))).isFalse();
        }

        @Test
        @DisplayName("un départ hors de la grille du pas n'est pas réservable, ni par le membre ni par le garage")
        void pasReservableHorsGrille() {
            ouvertLeMercrediDe8a12();
            assertThat(service.estReservable(local(MERCREDI, 9, 7), local(MERCREDI, 10, 7))).isFalse();
            assertThat(service.estReservable(local(MERCREDI, 9, 7), local(MERCREDI, 10, 7), ReglesDeCreneau.GARAGE))
                    .isFalse();
            assertThat(service.estReservable(local(MERCREDI, 9, 30), local(MERCREDI, 10, 30))).isTrue();
        }

        @Test
        @DisplayName("la grille part de l'ouverture de la plage, pas de l'heure pleine")
        void grilleDepuisLOuverture() {
            Duration pas = Duration.ofMinutes(30);
            assertThat(DisponibiliteService.surLaGrille(LocalTime.of(8, 15), LocalTime.of(9, 45), pas)).isTrue();
            assertThat(DisponibiliteService.surLaGrille(LocalTime.of(8, 15), LocalTime.of(9, 30), pas)).isFalse();
        }

        @Test
        @DisplayName("une fraction de seconde suffit à sortir de la grille")
        void fractionDeSeconde() {
            assertThat(DisponibiliteService.surLaGrille(LocalTime.of(8, 0), LocalTime.of(9, 0, 0, 500_000_000),
                    Duration.ofMinutes(30))).isFalse();
        }

        @Test
        @DisplayName("un depart sous le delai minimal ne l est pas")
        void pasReservableTropTot() {
            LocalDate lundi = LocalDate.of(2026, 9, 14);

            assertThat(service.estReservable(local(lundi, 14, 0), local(lundi, 15, 0))).isFalse();
        }
    }

    @Nested
    @DisplayName("déplacement d'un rendez-vous")
    class Deplacement {

        private final LocalDate lundi = LocalDate.of(2026, 9, 14);

        private Rdv rdvSur(PosteAtelier poste, Instant debut) {
            Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            Vehicule golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
            Categorie cat = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);
            Prestation p = new Prestation(cat, "VID", "Vidange", new BigDecimal("49.00"), 60);
            return new Rdv("RDV-2026-0001", marie, golf, poste, debut, Duration.ofMinutes(30), List.of(p), null);
        }

        private void ouvertLeLundiDe8a18() {
            when(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 1))
                    .thenReturn(List.of(new PlageOuverture(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(18, 0))));
        }

        @Test
        @DisplayName("le rendez-vous déplacé ne bloque pas son propre créneau")
        void neSeBloquePasLuiMeme() {
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));
            Rdv deplace = rdvSur(pont1, local(MERCREDI, 8, 0)); // 08:00-09:00
            when(rdvs.actifsChevauchant(any(), any(), any())).thenReturn(List.of(deplace));

            assertThat(debuts(service.creneauxPourDeplacer(deplace, MERCREDI, ReglesDeCreneau.MEMBRE)))
                    .as("08:30 recoupe l'ancien créneau, libéré par le déplacement")
                    .contains(local(MERCREDI, 8, 30))
                    .hasSize(7);
            assertThat(service.premierPosteLibre(local(MERCREDI, 8, 30), local(MERCREDI, 9, 30), deplace))
                    .contains(pont1);
            assertThat(service.premierPosteLibre(local(MERCREDI, 8, 30), local(MERCREDI, 9, 30)))
                    .as("sans exclusion, le rendez-vous occupe son poste")
                    .isEmpty();
        }

        @Test
        @DisplayName("les autres rendez-vous restent occupants")
        void autresOccupants() {
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));
            Rdv autre = rdvSur(pont1, local(MERCREDI, 8, 0));
            Rdv deplace = rdvSur(pont1, local(MERCREDI, 11, 0));
            when(rdvs.actifsChevauchant(any(), any(), any())).thenReturn(List.of(autre, deplace));

            assertThat(debuts(service.creneauxPourDeplacer(deplace, MERCREDI, ReglesDeCreneau.MEMBRE)))
                    .doesNotContain(local(MERCREDI, 8, 0), local(MERCREDI, 9, 0))
                    .contains(local(MERCREDI, 11, 0));
        }

        @Test
        @DisplayName("le garage choisit sous le délai minimal, le membre non")
        void garageSousLeDelaiMinimal() {
            ouvertLeLundiDe8a18();
            // Maintenant : lundi 10:00 à Bruxelles.
            assertThat(service.estReservable(local(lundi, 14, 0), local(lundi, 15, 0), ReglesDeCreneau.MEMBRE)).isFalse();
            assertThat(service.estReservable(local(lundi, 14, 0), local(lundi, 15, 0), ReglesDeCreneau.GARAGE)).isTrue();
        }

        @Test
        @DisplayName("le garage ne choisit pas un créneau déjà commencé")
        void garagePasDansLePasse() {
            lenient().when(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 1))
                    .thenReturn(List.of(new PlageOuverture(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(18, 0))));
            assertThat(service.estReservable(local(lundi, 9, 0), local(lundi, 10, 0), ReglesDeCreneau.GARAGE)).isFalse();
            assertThat(service.estReservable(local(lundi, 10, 0), local(lundi, 11, 0), ReglesDeCreneau.GARAGE))
                    .as("maintenant même n'est plus à venir")
                    .isFalse();
        }

        @Test
        @DisplayName("le garage choisit au-delà de l'horizon, le membre non")
        void garageAuDelaDeLHorizon() {
            LocalDate dansSeptanteJours = MERCREDI.plusDays(70);
            ouvertLeMercrediDe8a12();

            assertThat(service.estReservable(local(dansSeptanteJours, 9, 0), local(dansSeptanteJours, 10, 0),
                    ReglesDeCreneau.MEMBRE)).isFalse();
            assertThat(service.estReservable(local(dansSeptanteJours, 9, 0), local(dansSeptanteJours, 10, 0),
                    ReglesDeCreneau.GARAGE)).isTrue();
        }

        @Test
        @DisplayName("mode manuel : le garage n'attend pas sa propre réponse")
        void garageSansEcheance() {
            reglages.modifier(reglages.getFuseauHoraire(), reglages.getPasMinutes(), reglages.getTamponMinutes(),
                    0, reglages.getHorizonJours(), reglages.getDelaiAnnulationHeures(), false,
                    reglages.getMaxRdvEnAttenteParMembre());
            ouvertLeMercrediDe8a12();
            when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));
            Rdv deplace = rdvSur(pont1, local(MERCREDI, 11, 0));
            DisponibiliteService mardiApresMidi = new DisponibiliteService(parametres, plages, postes,
                    indisponibilites, rdvs, new EcheanceReponse(plages, indisponibilites),
                    Clock.fixed(local(MERCREDI.minusDays(1), 15, 0), ZoneOffset.UTC));

            assertThat(mardiApresMidi.creneauxPourDeplacer(deplace, MERCREDI, ReglesDeCreneau.MEMBRE)).isEmpty();
            assertThat(mardiApresMidi.creneauxPourDeplacer(deplace, MERCREDI, ReglesDeCreneau.GARAGE)).isNotEmpty();
        }

        @Test
        @DisplayName("un jour férié reste fermé au garage")
        void garageJourFerie() {
            LocalDate armistice = LocalDate.of(2026, 11, 11);
            lenient().when(plages.findByJourSemaineAndActifTrueOrderByHeureDebut((short) 3))
                    .thenReturn(List.of(new PlageOuverture(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))));
            lenient().when(postes.findByActifTrueOrderByOrdreAscLibelleAsc()).thenReturn(List.of(pont1));
            Rdv deplace = rdvSur(pont1, local(MERCREDI, 11, 0));

            assertThat(service.estReservable(local(armistice, 9, 0), local(armistice, 10, 0), ReglesDeCreneau.GARAGE)).isFalse();
            assertThat(service.creneauxPourDeplacer(deplace, armistice, ReglesDeCreneau.GARAGE)).isEmpty();
        }
    }
}