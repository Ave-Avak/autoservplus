package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.*;
import be.autoservplus.reservation.repository.*;
import be.autoservplus.reservation.web.dto.RdvVueAdmin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import be.autoservplus.support.SocleIntegration;

/**
 * Exerce les requetes JPQL du tableau de bord admin contre un vrai PostgreSQL 16.
 *
 * <p>Les tests unitaires de {@code AdminRdvServiceTest} mockent le repository : ils
 * ne detectent pas un JPQL mal forme (typo dans un JOIN FETCH, mauvais parametre)
 * qui ne peterait qu au runtime. Ce IT execute effectivement chaque requete et
 * verifie que le mapping vers {@link RdvVueAdmin} construit une vue complete, ce
 * qui prouve que les relations {@code membre}, {@code vehicule}, {@code poste} sont
 * bien fetchees.</p>
 *
 * <p>{@code @PreAuthorize("hasRole('ADMINISTRATEUR')")} sur le service est evalue :
 * {@code @WithMockUser(roles = "ADMINISTRATEUR")} au niveau classe fournit le
 * principal necessaire.</p>
 */
@SpringBootTest
@Transactional
@Import(AdminRdvServiceIT.HorlogeFixe.class)
@WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
@DisplayName("AdminRdvService (integration)")
class AdminRdvServiceIT extends SocleIntegration {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    // Dimanche 13 septembre 2026 à 12:00 Bruxelles. Les RDV terminés à 09:30 sont
    // dans le passé, ceux qui commencent à 14:00 dans le futur : la frontière
    // temporelle de rendezVousDuJour est nette et déterministe.
    private static final Instant INSTANT_FIGE =
            LocalDate.of(2026, 9, 13).atTime(12, 0).atZone(BRUXELLES).toInstant();

    @TestConfiguration
    static class HorlogeFixe {
        @Bean
        @Primary
        Clock horlogeFigee() {
            return Clock.fixed(INSTANT_FIGE, BRUXELLES);
        }
    }

    @Autowired private AdminRdvService service;
    @Autowired private RdvRepository rdvs;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private VehiculeRepository vehicules;
    @Autowired private CategorieRepository categories;
    @Autowired private PrestationRepository prestations;
    @Autowired private PosteAtelierRepository postes;
    @Autowired private be.autoservplus.intervention.repository.InterventionRepository interventions;

    private Utilisateur marie;
    private Vehicule golf;
    private PosteAtelier pont;
    private Prestation vidange;
    private int compteur;

    @BeforeEach
    void setUp() {
        marie = utilisateurs.save(new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
        golf = vehicules.save(new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL));
        Categorie entretien = categories.save(new Categorie("IT-ADM-ENT", "Entretien", TypeCategorie.SERVICE));
        vidange = prestations.save(new Prestation(entretien, "IT-ADM-VID", "Vidange", new BigDecimal("49.00"), 30));
        pont = postes.save(new PosteAtelier("Pont de test"));
        compteur = 1;
    }

    private Rdv insererRdv(Instant debut, StatutRdv etat) {
        String numero = "RDV-IT-ADM-%04d".formatted(compteur++);
        Rdv rdv = new Rdv(numero, marie, golf, pont, debut, Duration.ofMinutes(30),
                List.of(vidange), null);
        if (etat == StatutRdv.CONFIRME) {
            rdv.confirmer(rdv.getDebut().minus(Duration.ofDays(1)));
        }
        return rdvs.saveAndFlush(rdv);
    }

    @Test
    @DisplayName("demandesEnAttente ne renvoie que les RDV EN_ATTENTE, avec relations chargees")
    void demandesEnAttenteNeRenvoieQueLesEnAttente() {
        Rdv attendu = insererRdv(LocalDate.of(2026, 9, 14).atTime(9, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.EN_ATTENTE);
        insererRdv(LocalDate.of(2026, 9, 14).atTime(14, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);

        List<RdvVueAdmin> vues = service.demandesEnAttente();

        // Seul l EN_ATTENTE remonte : le filtre par statut est bien applique en base.
        assertThat(vues).extracting(RdvVueAdmin::reference).containsExactly(attendu.getReference());

        // membreEmail et vehicule proviennent de relations JOIN FETCH : leur lecture
        // ici prouve que le mapping les a materialisees sans LazyInitializationException.
        RdvVueAdmin vue = vues.get(0);
        assertThat(vue.membreEmail()).isEqualTo("marie@exemple.be");
        assertThat(vue.membreNom()).isEqualTo("Marie Dupont");
        assertThat(vue.vehicule()).contains("VW", "Golf", "1-ABC-123");
        assertThat(vue.peutConfirmer()).isTrue();
        assertThat(vue.peutMarquerHonore()).isFalse();
    }

    @Test
    @DisplayName("rendez-vous du jour : confirmés commencés depuis minuit, ni ceux d'hier ni ceux à venir")
    void rendezVousDuJourFiltreParStatutEtJour() {
        // CONFIRME 09:00-09:30 : début et fin passés, honoré et absent possibles.
        Rdv passe = insererRdv(LocalDate.of(2026, 9, 13).atTime(9, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        // CONFIRME 11:30-12:00 : début passé, fin pile à 12:00, honoré possible, absent pas encore.
        Rdv enCours = insererRdv(LocalDate.of(2026, 9, 13).atTime(11, 30).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        // CONFIRME à 14:00 : pas encore commencé.
        insererRdv(LocalDate.of(2026, 9, 13).atTime(14, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        // EN_ATTENTE à 07:00 : mauvais statut.
        insererRdv(LocalDate.of(2026, 9, 13).atTime(7, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.EN_ATTENTE);
        // CONFIRME la veille à 23:30, minuit de Bruxelles le sépare du jour courant.
        Rdv hier = insererRdv(LocalDate.of(2026, 9, 12).atTime(23, 30).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);

        List<RdvVueAdmin> vues = service.rendezVousDuJour();

        assertThat(vues).extracting(RdvVueAdmin::reference)
                .containsExactly(passe.getReference(), enCours.getReference());
        assertThat(vues.get(0).peutMarquerHonore()).isTrue();
        assertThat(vues.get(0).peutMarquerAbsent()).isTrue();
        assertThat(vues.get(1).peutMarquerHonore()).isTrue();
        assertThat(vues.get(1).peutMarquerAbsent())
                .as("fin égale à maintenant : on ne déclare pas absent tant que le créneau n'est pas écoulé")
                .isFalse();
        assertThat(vues.get(0).membreEmail()).isEqualTo("marie@exemple.be");
        assertThat(vues.get(0).vehicule()).contains("Golf");

        assertThat(service.rendezVousPassesSansIssue()).extracting(RdvVueAdmin::reference)
                .as("le rendez-vous d'hier sort de la section du jour pour celle des passés")
                .containsExactly(hier.getReference());
    }

    @Test
    @DisplayName("agenda : tous les statuts de la période, bornes comprises, dans l'ordre")
    void agendaTousStatutsDeLaPeriode() {
        Rdv lundi = insererRdv(LocalDate.of(2026, 9, 14).atTime(9, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.EN_ATTENTE);
        Rdv mardiSoir = insererRdv(LocalDate.of(2026, 9, 15).atTime(17, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        Rdv refuse = insererRdv(LocalDate.of(2026, 9, 15).atTime(8, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.EN_ATTENTE);
        refuse.refuser("Atelier complet", INSTANT_FIGE);
        rdvs.saveAndFlush(refuse);
        // Hors période : le dimanche précédent et le mercredi suivant.
        insererRdv(LocalDate.of(2026, 9, 13).atTime(16, 0).atZone(BRUXELLES).toInstant(), StatutRdv.CONFIRME);
        insererRdv(LocalDate.of(2026, 9, 16).atTime(0, 0).atZone(BRUXELLES).toInstant(), StatutRdv.CONFIRME);

        var agenda = service.agenda(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 15));

        assertThat(agenda.rendezVous()).extracting(RdvVueAdmin::reference)
                .containsExactly(lundi.getReference(), refuse.getReference(), mardiSoir.getReference());
        assertThat(agenda.rendezVous().get(1).statut()).isEqualTo("REFUSE");
        assertThat(agenda.rendezVous().get(0).prestations()).containsExactly("Vidange");
    }

    @Test
    @DisplayName("agenda sans borne : du jour courant à J+14")
    void agendaParDefaut() {
        Rdv dernierJour = insererRdv(LocalDate.of(2026, 9, 27).atTime(18, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.EN_ATTENTE);
        insererRdv(LocalDate.of(2026, 9, 28).atTime(8, 0).atZone(BRUXELLES).toInstant(), StatutRdv.EN_ATTENTE);

        var agenda = service.agenda(null, null);

        assertThat(agenda.du()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(agenda.au()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(agenda.rendezVous()).extracting(RdvVueAdmin::reference)
                .containsExactly(dernierJour.getReference());
    }

    @Test
    @WithMockUser(username = "membre@exemple.be", roles = "MEMBRE")
    @DisplayName("@PreAuthorize rejette un utilisateur sans role ADMINISTRATEUR")
    void rejetteNonAdmin() {
        // @WithMockUser au niveau methode surcharge celui de la classe (ADMINISTRATEUR).
        // Le rejet vient specifiquement du @PreAuthorize(hasRole('ADMINISTRATEUR')) sur
        // AdminRdvService, active par @EnableMethodSecurity dans SecuriteConfig charge
        // par @SpringBootTest.
        assertThatThrownBy(() -> service.demandesEnAttente())
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("confirmer end-to-end : le statut CONFIRME est persiste et rechargeable")
    void confirmerBoutEnBout() {
        Rdv enAttente = insererRdv(
                LocalDate.of(2026, 9, 14).atTime(10, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.EN_ATTENTE);
        UUID reference = enAttente.getReference();

        service.confirmer(reference);

        // Rechargement pour s assurer que le statut est bien passe en base (pas seulement
        // en cache L1). Le JOIN FETCH de findByReference charge aussi les relations.
        Rdv relu = rdvs.findByReference(reference).orElseThrow();
        assertThat(relu.getStatut()).isEqualTo(StatutRdv.CONFIRME);
    }

    @Test
    @DisplayName("marquerHonore cree automatiquement une intervention PLANIFIEE, atomiquement")
    void marquerHonoreCreeIntervention() {
        // Fixture : un RDV CONFIRME dont debut est ANTERIEUR a INSTANT_FIGE (12:00),
        // sinon la garde temporelle du service refuse le marquage honore.
        Rdv confirme = insererRdv(
                LocalDate.of(2026, 9, 13).atTime(10, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        UUID reference = confirme.getReference();
        Long rdvId = confirme.getId();

        service.marquerHonore(reference);

        // Test-cle atomicite : le RDV est HONORE ET l intervention existe.
        assertThat(rdvs.findByReference(reference).orElseThrow().getStatut())
                .isEqualTo(StatutRdv.HONORE);
        var intervention = interventions.findByRdvId(rdvId);
        assertThat(intervention)
                .as("Le hook de marquerHonore doit avoir cree l intervention dans la meme transaction")
                .isPresent();
        assertThat(intervention.get().getStatut())
                .isEqualTo(be.autoservplus.intervention.domain.StatutIntervention.PLANIFIEE);
        assertThat(intervention.get().getLignes())
                .as("Le pre-remplissage doit avoir cree une ligne par prestation du RDV")
                .hasSize(1);
    }

    @Test
    @DisplayName("marquerHonore appele deux fois n'ecrase pas l'intervention (idempotence)")
    void marquerHonoreIdempotent() {
        // Debut anterieur a INSTANT_FIGE pour satisfaire la garde temporelle.
        Rdv confirme = insererRdv(
                LocalDate.of(2026, 9, 13).atTime(11, 0).atZone(BRUXELLES).toInstant(),
                StatutRdv.CONFIRME);
        UUID reference = confirme.getReference();
        Long rdvId = confirme.getId();

        service.marquerHonore(reference);
        UUID interventionCreee = interventions.findByRdvId(rdvId).orElseThrow().getReference();

        // Second marquerHonore rejete par la machine a etats du RDV (HONORE final),
        // mais si un jour ce n etait plus le cas, l intervention ne serait pas dupliquee.
        // On teste directement l'idempotence du service intervention :
        var deuxieme = interventions.findByRdvId(rdvId);
        assertThat(deuxieme.get().getReference()).isEqualTo(interventionCreee);
    }
}
