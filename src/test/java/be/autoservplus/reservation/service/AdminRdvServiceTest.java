package be.autoservplus.reservation.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.DetailsRdvCourriel;
import be.autoservplus.communication.service.PieceJointeCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.*;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.reservation.repository.RdvRepository;
import be.autoservplus.reservation.service.dto.AgendaAdmin;
import be.autoservplus.reservation.service.dto.FichierAgenda;
import be.autoservplus.reservation.web.dto.RdvVueAdmin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires d {@code AdminRdvService}. Aucun Spring : le domaine est utilise
 * tel quel (Rdv est une entite testable en isolation), seuls le repository, les
 * parametres et le service courriel sont mockes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRdvService")
class AdminRdvServiceTest {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    private static final Instant MAINTENANT =
            LocalDate.of(2026, 9, 12).atTime(9, 0).atZone(BRUXELLES).toInstant();
    private static final Instant MINUIT =
            LocalDate.of(2026, 9, 12).atStartOfDay(BRUXELLES).toInstant();
    private static final Instant DIMANCHE_10H =
            LocalDate.of(2026, 9, 13).atTime(10, 0).atZone(BRUXELLES).toInstant();

    @Mock private RdvRepository rdvs;
    @Mock private ParametreAtelierRepository parametres;
    @Mock private ServiceCourriel courriel;
    @Mock private be.autoservplus.intervention.service.InterventionService interventions;
    @Mock private ExportAgendaService exportAgenda;
    @Mock private org.springframework.context.ApplicationEventPublisher evenements;

    private Clock horloge;
    private AdminRdvService service;

    private Utilisateur marie;
    private Vehicule golf;
    private PosteAtelier pont;
    private Prestation vidange;

    @BeforeEach
    void setUp() {
        horloge = Clock.fixed(MAINTENANT, BRUXELLES);
        service = new AdminRdvService(rdvs, parametres, courriel, interventions, exportAgenda, evenements, horloge);

        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        golf = new Vehicule(marie, "1-ABC-123", "Volkswagen", "Golf", Motorisation.DIESEL);
        pont = new PosteAtelier("Pont 1");
        Categorie entretien = new Categorie("ENTRETIEN", "Entretien", TypeCategorie.SERVICE);
        vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("49.00"), 30);
    }

    // Rdv frais construit sur DIMANCHE_10H, positionne dans l etat demande par des
    // transitions successives : le domaine reste seule source de verite pour l etat.
    private Rdv rdvDans(StatutRdv etat) {
        Rdv rdv = new Rdv("RDV-2026-0001", marie, golf, pont, DIMANCHE_10H,
                Duration.ofMinutes(30), List.of(vidange), null);
        switch (etat) {
            case EN_ATTENTE -> { /* etat initial */ }
            case CONFIRME -> rdv.confirmer(rdv.getDebut().minus(Duration.ofDays(1)));
            case REFUSE -> rdv.refuser("motif", MAINTENANT);
            case ANNULE -> rdv.annulerParLeGarage("motif", MAINTENANT);
            case HONORE -> { rdv.confirmer(rdv.getDebut().minus(Duration.ofDays(1))); rdv.marquerHonore(rdv.getFin().plus(Duration.ofHours(1))); }
            case ABSENT -> { rdv.confirmer(rdv.getDebut().minus(Duration.ofDays(1))); rdv.marquerAbsent(rdv.getFin().plus(Duration.ofHours(1))); }
        }
        return rdv;
    }

    // Stubbing recurrent : parametres courants sont interroges par detailsPour et par
    // les methodes de tableau de bord. On retourne les defauts (fuseau Bruxelles).
    private void stubParametresCourants() {
        when(parametres.courants()).thenReturn(new ParametreAtelier());
    }

    private void stubLookup(Rdv rdv) {
        when(rdvs.findByReference(rdv.getReference())).thenReturn(Optional.of(rdv));
    }

    // Le fichier iCalendar joint a la confirmation (F38) est produit par un service
    // dedie, teste ailleurs : ici seul importe qu il soit demande et transmis.
    private void stubAgenda() {
        when(exportAgenda.pourLeCourriel(any(Rdv.class)))
                .thenReturn(new FichierAgenda("rendez-vous-RDV-2026-0001.ics", "BEGIN:VCALENDAR"));
    }

    // ---------------------------------------------------------------------------------

    @Nested
    @DisplayName("confirmer")
    class Confirmer {

        @Test
        @DisplayName("passe le RDV en CONFIRME, sauvegarde et notifie")
        void confirmeEtNotifie() {
            Rdv rdv = rdvDans(StatutRdv.EN_ATTENTE);
            stubLookup(rdv);
            when(rdvs.saveAndFlush(rdv)).thenReturn(rdv);
            stubParametresCourants();
            stubAgenda();

            Rdv resultat = service.confirmer(rdv.getReference());

            assertThat(resultat.getStatut()).isEqualTo(StatutRdv.CONFIRME);
            verify(rdvs).saveAndFlush(rdv);
            verify(courriel).envoyerConfirmationRdv(eq(marie), any(DetailsRdvCourriel.class),
                    any(PieceJointeCourriel.class));
        }

        @Test
        @DisplayName("laisse remonter IllegalStateException RM-10 si l etat est final")
        void refuseDepuisEtatFinal() {
            Rdv rdv = rdvDans(StatutRdv.REFUSE);
            stubLookup(rdv);

            assertThatThrownBy(() -> service.confirmer(rdv.getReference()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RM-10");
            verify(rdvs, never()).saveAndFlush(any());
            verify(courriel, never()).envoyerConfirmationRdv(any(), any(), any());
        }

        @Test
        @DisplayName("refuse une demande dont le début est passé, sans écrire ni prévenir")
        void refuseApresLeDebut() {
            Rdv rdv = new Rdv("RDV-2026-0009", marie, golf, pont, MAINTENANT.minus(Duration.ofHours(1)),
                    Duration.ofMinutes(30), List.of(vidange), null);
            stubLookup(rdv);

            assertThatThrownBy(() -> service.confirmer(rdv.getReference()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("seulement refusé");
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.EN_ATTENTE);
            verify(rdvs, never()).saveAndFlush(any());
            verify(courriel, never()).envoyerConfirmationRdv(any(), any(), any());
        }

        @Test
        @DisplayName("reference absente -> RessourceIntrouvableException")
        void referenceAbsente() {
            UUID ref = UUID.randomUUID();
            when(rdvs.findByReference(ref)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmer(ref))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        @Test
        @DisplayName("OptimisticLockingFailureException -> ConflitConcurrenceException")
        void traduitConflitConcurrence() {
            Rdv rdv = rdvDans(StatutRdv.EN_ATTENTE);
            stubLookup(rdv);
            when(rdvs.saveAndFlush(rdv)).thenThrow(new OptimisticLockingFailureException("stale"));

            assertThatThrownBy(() -> service.confirmer(rdv.getReference()))
                    .isInstanceOf(ConflitConcurrenceException.class)
                    .hasMessageContaining("rechargez");
            verify(courriel, never()).envoyerConfirmationRdv(any(), any(), any());
        }

        @Test
        @DisplayName("un ServiceCourriel qui jette ne fait PAS echouer la confirmation")
        void courrielQuiJetteNeCassePasLaTransition() {
            Rdv rdv = rdvDans(StatutRdv.EN_ATTENTE);
            stubLookup(rdv);
            when(rdvs.saveAndFlush(rdv)).thenReturn(rdv);
            stubParametresCourants();
            stubAgenda();
            doThrow(new RuntimeException("SMTP down"))
                    .when(courriel).envoyerConfirmationRdv(any(), any(), any());

            Rdv resultat = service.confirmer(rdv.getReference());

            assertThat(resultat.getStatut()).isEqualTo(StatutRdv.CONFIRME);
            verify(rdvs).saveAndFlush(rdv);
        }
    }

    /**
     * Le fichier d agenda est un confort, la confirmation est l information. Si le
     * redacteur iCalendar jette, le courriel doit partir <b>quand meme</b> (sans
     * piece jointe) et la transition rester acquise. Ce test verrouille cet ordre de
     * priorite, qu une refactorisation placant la production du fichier hors du bloc
     * absorbant casserait en silence.
     */
    @Nested
    @DisplayName("confirmer : panne du generateur d agenda")
    class ConfirmerSansAgenda {

        @Test
        @DisplayName("un export iCalendar qui jette laisse partir la confirmation, sans piece jointe")
        void exportQuiJetteNEmpechePasLeCourriel() {
            Rdv rdv = rdvDans(StatutRdv.EN_ATTENTE);
            stubLookup(rdv);
            when(rdvs.saveAndFlush(rdv)).thenReturn(rdv);
            stubParametresCourants();
            when(exportAgenda.pourLeCourriel(any(Rdv.class)))
                    .thenThrow(new IllegalStateException("redacteur indisponible"));

            Rdv resultat = service.confirmer(rdv.getReference());

            assertThat(resultat.getStatut()).isEqualTo(StatutRdv.CONFIRME);
            verify(rdvs).saveAndFlush(rdv);
            // isNull() et non any() : c est precisement l absence de piece jointe qui
            // distingue ce cas du cas nominal. any() passerait aussi si le fichier
            // avait ete produit, et le test ne prouverait plus rien.
            verify(courriel).envoyerConfirmationRdv(eq(marie), any(DetailsRdvCourriel.class), isNull());
        }
    }

    @Nested
    @DisplayName("refuser")
    class Refuser {

        @Test
        @DisplayName("passe le RDV en REFUSE avec motif, sauvegarde et notifie")
        void refuseEtNotifie() {
            Rdv rdv = rdvDans(StatutRdv.EN_ATTENTE);
            stubLookup(rdv);
            when(rdvs.saveAndFlush(rdv)).thenReturn(rdv);
            stubParametresCourants();

            Rdv resultat = service.refuser(rdv.getReference(), "Piece indisponible");

            assertThat(resultat.getStatut()).isEqualTo(StatutRdv.REFUSE);
            assertThat(resultat.getMotifRefus()).isEqualTo("Piece indisponible");
            verify(rdvs).saveAndFlush(rdv);
            verify(courriel).envoyerRefusRdv(eq(marie), any(DetailsRdvCourriel.class), eq("Piece indisponible"));
        }
    }

    @Nested
    @DisplayName("annulerParLeGarage")
    class AnnulerParLeGarage {

        @Test
        @DisplayName("passe le RDV en ANNULE avec motif, sauvegarde et notifie")
        void annuleEtNotifie() {
            Rdv rdv = rdvDans(StatutRdv.CONFIRME);
            stubLookup(rdv);
            when(rdvs.saveAndFlush(rdv)).thenReturn(rdv);
            stubParametresCourants();

            Rdv resultat = service.annulerParLeGarage(rdv.getReference(), "Panne du pont");

            assertThat(resultat.getStatut()).isEqualTo(StatutRdv.ANNULE);
            assertThat(resultat.getMotifRefus()).isEqualTo("Panne du pont");
            verify(rdvs).saveAndFlush(rdv);
            verify(courriel).envoyerAnnulationParLeGarage(eq(marie), any(DetailsRdvCourriel.class), eq("Panne du pont"));
        }

        @Test
        @DisplayName("refuse un rendez-vous confirmé dont le début est passé, sans écrire ni prévenir")
        void refuseApresLeDebut() {
            Rdv rdv = new Rdv("RDV-2026-0009", marie, golf, pont, MAINTENANT.minus(Duration.ofHours(1)),
                    Duration.ofMinutes(30), List.of(vidange), null);
            rdv.confirmer(MAINTENANT.minus(Duration.ofDays(1)));
            stubLookup(rdv);

            assertThatThrownBy(() -> service.annulerParLeGarage(rdv.getReference(), "Panne du pont"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("honoré ou absent");
            assertThat(rdv.getStatut()).isEqualTo(StatutRdv.CONFIRME);
            verify(rdvs, never()).saveAndFlush(any());
            verify(courriel, never()).envoyerAnnulationParLeGarage(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("marquerHonore")
    class MarquerHonore {

        /** RDV CONFIRME commence il y a 2h : debut atteint, honore autorise. */
        private Rdv rdvCommenceIlYA(java.time.Duration duree) {
            Rdv rdv = new Rdv("RDV-2026-0001", marie, golf, pont,
                    MAINTENANT.minus(duree), java.time.Duration.ofMinutes(30),
                    List.of(vidange), null);
            rdv.confirmer(rdv.getDebut().minus(Duration.ofDays(1)));
            return rdv;
        }

        @Test
        @DisplayName("passe le RDV en HONORE, sauvegarde, NE notifie PAS le membre")
        void honoreSansMail() {
            Rdv rdv = rdvCommenceIlYA(java.time.Duration.ofHours(2));
            stubLookup(rdv);
            when(rdvs.saveAndFlush(rdv)).thenReturn(rdv);

            Rdv resultat = service.marquerHonore(rdv.getReference());

            assertThat(resultat.getStatut()).isEqualTo(StatutRdv.HONORE);
            verify(rdvs).saveAndFlush(rdv);
            verify(courriel, never()).envoyerConfirmationRdv(any(), any(), any());
            verify(courriel, never()).envoyerRefusRdv(any(), any(), any());
            verify(courriel, never()).envoyerAnnulationParLeGarage(any(), any(), any());
        }

        @Test
        @DisplayName("refuse un marquage avant l'heure de debut (garde de l'entité)")
        void refuseAvantLeDebut() {
            // RDV commencant dans 3h : debut > maintenant, donc refus.
            Rdv futur = new Rdv("RDV-2026-0002", marie, golf, pont,
                    MAINTENANT.plus(java.time.Duration.ofHours(3)), java.time.Duration.ofMinutes(30),
                    List.of(vidange), null);
            futur.confirmer(futur.getDebut().minus(Duration.ofDays(1)));
            stubLookup(futur);

            assertThatThrownBy(() -> service.marquerHonore(futur.getReference()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("avant l'heure de début");

            assertThat(futur.getStatut())
                    .as("Le statut ne doit pas avoir bascule en HONORE")
                    .isEqualTo(StatutRdv.CONFIRME);
            verify(rdvs, never()).saveAndFlush(any());
            verify(interventions, never()).creerDepuisRdv(any());
        }
    }

    @Nested
    @DisplayName("marquerAbsent")
    class MarquerAbsent {

        /** RDV CONFIRME dont le creneau s est termine il y a {@code depuis} : fin passee. */
        private Rdv rdvTermineIlYA(java.time.Duration depuis) {
            java.time.Duration duree = java.time.Duration.ofMinutes(30);
            Rdv rdv = new Rdv("RDV-2026-0001", marie, golf, pont,
                    MAINTENANT.minus(depuis).minus(duree), duree,
                    List.of(vidange), null);
            rdv.confirmer(rdv.getDebut().minus(Duration.ofDays(1)));
            return rdv;
        }

        @Test
        @DisplayName("passe le RDV en ABSENT, sauvegarde, NE notifie PAS le membre")
        void absentSansMail() {
            Rdv rdv = rdvTermineIlYA(java.time.Duration.ofMinutes(15));
            stubLookup(rdv);
            when(rdvs.saveAndFlush(rdv)).thenReturn(rdv);

            Rdv resultat = service.marquerAbsent(rdv.getReference());

            assertThat(resultat.getStatut()).isEqualTo(StatutRdv.ABSENT);
            verify(rdvs).saveAndFlush(rdv);
            verify(courriel, never()).envoyerConfirmationRdv(any(), any(), any());
            verify(courriel, never()).envoyerRefusRdv(any(), any(), any());
            verify(courriel, never()).envoyerAnnulationParLeGarage(any(), any(), any());
        }

        @Test
        @DisplayName("refuse un marquage avant la fin du creneau (garde de l'entité)")
        void refuseAvantLaFin() {
            // RDV commence il y a 10 min, fin 20 min dans le futur : creneau en cours.
            Rdv enCours = new Rdv("RDV-2026-0003", marie, golf, pont,
                    MAINTENANT.minus(java.time.Duration.ofMinutes(10)), java.time.Duration.ofMinutes(30),
                    List.of(vidange), null);
            enCours.confirmer(enCours.getDebut().minus(Duration.ofDays(1)));
            stubLookup(enCours);

            assertThatThrownBy(() -> service.marquerAbsent(enCours.getReference()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("avant la fin");

            assertThat(enCours.getStatut())
                    .as("Le statut ne doit pas avoir bascule en ABSENT")
                    .isEqualTo(StatutRdv.CONFIRME);
            verify(rdvs, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("vue")
    class Vue {

        @Test
        @DisplayName("charge le RDV et le mappe en RdvVueAdmin")
        void vueDuRdv() {
            Rdv rdv = rdvDans(StatutRdv.EN_ATTENTE);
            stubLookup(rdv);
            stubParametresCourants();

            RdvVueAdmin vue = service.vue(rdv.getReference());

            assertThat(vue.reference()).isEqualTo(rdv.getReference());
            assertThat(vue.statut()).isEqualTo("EN_ATTENTE");
            assertThat(vue.peutConfirmer()).isTrue();
        }

        @Test
        @DisplayName("reference absente -> RessourceIntrouvableException")
        void referenceAbsente() {
            UUID ref = UUID.randomUUID();
            when(rdvs.findByReference(ref)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.vue(ref))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("tableau de bord")
    class TableauDeBord {

        @Test
        @DisplayName("demandesEnAttente delegue au repository et mappe en RdvVueAdmin")
        void listeDemandesEnAttente() {
            Rdv rdv = rdvDans(StatutRdv.EN_ATTENTE);
            when(rdvs.findByStatutOrderByDebut(StatutRdv.EN_ATTENTE)).thenReturn(List.of(rdv));
            stubParametresCourants();

            List<RdvVueAdmin> vues = service.demandesEnAttente();

            assertThat(vues).hasSize(1);
            RdvVueAdmin vue = vues.get(0);
            assertThat(vue.statut()).isEqualTo("EN_ATTENTE");
            assertThat(vue.membreEmail()).isEqualTo("marie@exemple.be");
            assertThat(vue.peutConfirmer()).isTrue();
            assertThat(vue.peutMarquerHonore()).isFalse();
        }

        @Test
        @DisplayName("rendez-vous du jour : confirmés commencés depuis minuit, heure de l'atelier")
        void listeRendezVousDuJour() {
            // Commencé il y a 2 h, fini 90 min avant maintenant : début atteint et fin
            // passée, les deux flags temporels sont vrais.
            Rdv passe = new Rdv("RDV-PASSE", marie, golf, pont,
                    MAINTENANT.minus(Duration.ofHours(2)), Duration.ofMinutes(30),
                    List.of(vidange), null);
            passe.confirmer(passe.getDebut().minus(Duration.ofDays(1)));
            when(rdvs.commencesEntre(StatutRdv.CONFIRME, MINUIT, MAINTENANT)).thenReturn(List.of(passe));
            stubParametresCourants();

            List<RdvVueAdmin> vues = service.rendezVousDuJour();

            assertThat(vues).hasSize(1);
            RdvVueAdmin vue = vues.get(0);
            assertThat(vue.statut()).isEqualTo("CONFIRME");
            assertThat(vue.peutMarquerHonore()).isTrue();
            assertThat(vue.peutMarquerAbsent()).isTrue();
            assertThat(vue.peutConfirmer()).isFalse();
            assertThat(vue.debut()).isEqualTo(LocalDate.of(2026, 9, 12).atTime(7, 0));
            assertThat(vue.poste()).isEqualTo("Pont 1");
        }

        @Test
        @DisplayName("rendez-vous passés sans issue : confirmés commencés avant minuit")
        void listePassesSansIssue() {
            Rdv ancien = new Rdv("RDV-ANCIEN", marie, golf, pont,
                    MAINTENANT.minus(Duration.ofDays(3)), Duration.ofMinutes(30),
                    List.of(vidange), null);
            ancien.confirmer(ancien.getDebut().minus(Duration.ofDays(1)));
            when(rdvs.commencesAvant(StatutRdv.CONFIRME, MINUIT)).thenReturn(List.of(ancien));
            stubParametresCourants();

            assertThat(service.rendezVousPassesSansIssue())
                    .extracting(RdvVueAdmin::numero).containsExactly("RDV-ANCIEN");
        }

        @Test
        @DisplayName("peutMarquerAbsent est faux si le creneau n est pas encore ecoule")
        void absentInterditPendantLeCreneau() {
            // RDV commence il y a 10 min, fin 20 min DANS LE FUTUR : debut atteint
            // (peutMarquerHonore=true) mais creneau pas ecoule (peutMarquerAbsent=false).
            Rdv enCours = new Rdv("RDV-EN-COURS", marie, golf, pont,
                    MAINTENANT.minus(Duration.ofMinutes(10)), Duration.ofMinutes(30),
                    List.of(vidange), null);
            enCours.confirmer(enCours.getDebut().minus(Duration.ofDays(1)));
            when(rdvs.commencesEntre(StatutRdv.CONFIRME, MINUIT, MAINTENANT))
                    .thenReturn(List.of(enCours));
            stubParametresCourants();

            RdvVueAdmin vue = service.rendezVousDuJour().get(0);

            assertThat(vue.peutMarquerHonore())
                    .as("Debut atteint : le client peut etre marque present")
                    .isTrue();
            assertThat(vue.peutMarquerAbsent())
                    .as("Creneau en cours : on ne declare pas absent tant que la fin n est pas passee")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("agenda")
    class Agenda {

        @Test
        @DisplayName("sans borne : du jour courant à J+14 compris, en heure de l'atelier")
        void periodeParDefaut() {
            stubParametresCourants();
            Instant debut = LocalDate.of(2026, 9, 12).atStartOfDay(BRUXELLES).toInstant();
            Instant finExclue = LocalDate.of(2026, 9, 27).atStartOfDay(BRUXELLES).toInstant();
            when(rdvs.agenda(debut, finExclue)).thenReturn(List.of(rdvDans(StatutRdv.EN_ATTENTE)));

            AgendaAdmin agenda = service.agenda(null, null);

            assertThat(agenda.du()).isEqualTo(LocalDate.of(2026, 9, 12));
            assertThat(agenda.au()).isEqualTo(LocalDate.of(2026, 9, 26));
            assertThat(agenda.periodeInversee()).isFalse();
            assertThat(agenda.rendezVous()).hasSize(1);
        }

        @Test
        @DisplayName("bornes données : le jour de fin est compris")
        void periodeDonnee() {
            stubParametresCourants();
            LocalDate jour = LocalDate.of(2026, 10, 1);
            when(rdvs.agenda(jour.atStartOfDay(BRUXELLES).toInstant(),
                    jour.plusDays(1).atStartOfDay(BRUXELLES).toInstant())).thenReturn(List.of());

            AgendaAdmin agenda = service.agenda(jour, jour);

            assertThat(agenda.rendezVous()).isEmpty();
            assertThat(agenda.periodeInversee()).isFalse();
        }

        @Test
        @DisplayName("fin avant début : période signalée, aucune requête")
        void periodeInversee() {
            stubParametresCourants();

            AgendaAdmin agenda = service.agenda(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1));

            assertThat(agenda.periodeInversee()).isTrue();
            assertThat(agenda.rendezVous()).isEmpty();
            verify(rdvs, never()).agenda(any(), any());
        }
    }
}
