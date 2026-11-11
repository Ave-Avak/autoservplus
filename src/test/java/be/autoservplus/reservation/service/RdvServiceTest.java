package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de la réservation, limités à l'issue d'une course perdue à l'écriture.
 * Le reste du parcours est couvert sur PostgreSQL réel par {@code RdvServiceIT} et
 * {@code RdvConcurrenceIT}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RdvService")
class RdvServiceTest {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    private static final Instant MAINTENANT =
            LocalDate.of(2026, 9, 12).atTime(9, 0).atZone(BRUXELLES).toInstant();
    private static final Instant DIMANCHE_10H =
            LocalDate.of(2026, 9, 13).atTime(10, 0).atZone(BRUXELLES).toInstant();
    private static final String EMAIL = "marie@exemple.be";

    @Mock private RdvRepository rdvs;
    @Mock private UtilisateurRepository membres;
    @Mock private PrestationRepository prestations;
    @Mock private ParametreAtelierRepository parametres;
    @Mock private VehiculeService vehicules;
    @Mock private DisponibiliteService disponibilites;
    @Mock private GenerateurNumeroRdv numeros;
    @Mock private AvisRendezVous avis;
    @Mock private EcheanceReponse echeances;
    @Mock private IdentiteGarage garage;

    private RdvService service;
    private UUID golfReference;
    private UUID vidangeReference;

    @BeforeEach
    void setUp() {
        service = new RdvService(rdvs, membres, prestations, parametres, vehicules, disponibilites,
                numeros, avis, echeances, garage, Clock.fixed(MAINTENANT, BRUXELLES));

        Utilisateur marie = new Utilisateur(EMAIL, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        Vehicule golf = new Vehicule(marie, "1-ABC-123", "Volkswagen", "Golf", Motorisation.DIESEL);
        Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
        Prestation vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("49.00"), 30);
        golfReference = golf.getReference();
        vidangeReference = vidange.getReference();

        when(parametres.courants()).thenReturn(new ParametreAtelier());
        when(membres.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(marie));
        when(vehicules.vehiculeDuMembre(golfReference, EMAIL)).thenReturn(golf);
        when(prestations.findByReference(vidangeReference)).thenReturn(Optional.of(vidange));
        when(disponibilites.estReservable(any(), any())).thenReturn(true);
        when(disponibilites.premierPosteLibre(any(), any())).thenReturn(Optional.of(new PosteAtelier("Pont 1")));
        when(numeros.prochain()).thenReturn("RDV-2026-0001");
    }

    private Rdv reserver() {
        return service.reserver(EMAIL, golfReference, List.of(vidangeReference), DIMANCHE_10H, null);
    }

    @Test
    @DisplayName("un interblocage à l'insertion vaut créneau pris")
    void interblocageVautCreneauPris() {
        CannotAcquireLockException interblocage = new CannotAcquireLockException("insertion du rendez-vous",
                new SQLException("ERROR: deadlock detected", "40P01"));
        when(rdvs.saveAndFlush(any(Rdv.class))).thenThrow(interblocage);

        assertThatThrownBy(this::reserver)
                .isInstanceOf(CreneauIndisponibleException.class)
                .hasCause(interblocage);
    }

    @Test
    @DisplayName("une autre erreur de verrou n'est pas déguisée en créneau pris")
    void autreErreurDeVerrouRemonteTelleQuelle() {
        CannotAcquireLockException attente = new CannotAcquireLockException("insertion du rendez-vous",
                new SQLException("ERROR: canceling statement due to lock timeout", "55P03"));
        when(rdvs.saveAndFlush(any(Rdv.class))).thenThrow(attente);

        assertThatThrownBy(this::reserver).isSameAs(attente);
    }
}
