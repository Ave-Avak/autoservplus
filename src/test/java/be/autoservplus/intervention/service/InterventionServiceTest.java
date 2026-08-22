package be.autoservplus.intervention.service;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.common.exception.ConflitConcurrenceException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.DetailsDepassementCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.service.AuteurCourant;
import be.autoservplus.intervention.domain.HistoriqueStatutIntervention;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.HistoriqueStatutInterventionRepository;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.intervention.web.dto.InterventionVueMembre;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.domain.PosteAtelier;
import be.autoservplus.reservation.domain.Rdv;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterventionService")
class InterventionServiceTest {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    private static final Instant MAINTENANT = Instant.parse("2026-09-14T09:00:00Z");

    @Mock private InterventionRepository interventions;
    @Mock private HistoriqueStatutInterventionRepository historiques;
    @Mock private PrestationRepository prestations;
    @Mock private ParametreAtelierRepository parametres;
    @Mock private AuteurCourant auteurCourant;
    @Mock private GenerateurNumeroIntervention numeros;
    @Mock private ServiceCourriel courriel;
    @Mock private ApplicationEventPublisher evenements;

    private Clock horloge;
    private InterventionService service;

    private Utilisateur marie;
    private Vehicule golf;
    private PosteAtelier pont;
    private Prestation vidange;
    private Rdv rdv;

    @BeforeEach
    void setUp() {
        horloge = Clock.fixed(MAINTENANT, BRUXELLES);
        service = new InterventionService(interventions, historiques, prestations, parametres,
                auteurCourant, numeros, courriel, evenements, horloge);

        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        golf = new Vehicule(marie, "1-ABC-123", "VW", "Golf", Motorisation.DIESEL);
        pont = new PosteAtelier("Pont 1");
        Categorie entretien = new Categorie("ENT", "Entretien", TypeCategorie.SERVICE);
        vidange = new Prestation(entretien, "VID", "Vidange", new BigDecimal("49.00"), 30);
        rdv = new Rdv("RDV-2026-0001", marie, golf, pont, MAINTENANT,
                Duration.ofMinutes(30), List.of(vidange), null);
    }

    @Test
    @DisplayName("creerDepuisRdv cree une nouvelle intervention quand aucune n'existe")
    void creationNouvelle() {
        when(interventions.findByRdvId(any())).thenReturn(Optional.empty());
        when(numeros.prochain()).thenReturn("INT-2026-0001");
        when(interventions.saveAndFlush(any(Intervention.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Intervention creee = service.creerDepuisRdv(rdv);

        assertThat(creee.getStatut()).isEqualTo(StatutIntervention.PLANIFIEE);
        assertThat(creee.getNumero()).isEqualTo("INT-2026-0001");
        assertThat(creee.getLignes()).hasSize(1);
        verify(interventions).saveAndFlush(any(Intervention.class));
    }

    @Test
    @DisplayName("creerDepuisRdv fige le devis initial en HTVA depuis les lignes du RDV (RM-15)")
    void creationRenseigneLeDevisInitial() {
        when(interventions.findByRdvId(any())).thenReturn(Optional.empty());
        when(numeros.prochain()).thenReturn("INT-2026-0001");
        when(interventions.saveAndFlush(any(Intervention.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Intervention creee = service.creerDepuisRdv(rdv);

        // La vidange vaut 49.00 HTVA ; le devis doit valoir le HT, pas le TVAC (59.29).
        assertThat(creee.getMontantDevisInitialHtva())
                .as("Sans devis fige, RM-15 n'a aucune base de comparaison")
                .isNotNull()
                .isEqualByComparingTo("49.00");
        assertThat(creee.devisReferenceHtva()).isEqualByComparingTo("49.00");
        assertThat(creee.getLignes()).allSatisfy(l -> {
            assertThat(l.isAjouteeEnCours()).isFalse();
            assertThat(l.estFacturable()).isTrue();
        });
    }

    @Test
    @DisplayName("creerDepuisRdv est idempotent : retourne l'existante sans doublon")
    void idempotence() {
        Intervention existante = new Intervention("INT-2026-0001", rdv);
        when(interventions.findByRdvId(any())).thenReturn(Optional.of(existante));

        Intervention resultat = service.creerDepuisRdv(rdv);

        assertThat(resultat).isSameAs(existante);
        verify(interventions, never()).saveAndFlush(any());
        verify(numeros, never()).prochain();
    }

    @Test
    @DisplayName("demarrer passe en EN_COURS et sauvegarde")
    void demarrer() {
        Intervention it = new Intervention("INT-2026-0001", rdv);
        UUID ref = it.getReference();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(it));
        when(interventions.saveAndFlush(it)).thenReturn(it);

        Intervention resultat = service.demarrer(ref);

        assertThat(resultat.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
        assertThat(resultat.getDebutReel()).isEqualTo(MAINTENANT);
    }

    @Test
    @DisplayName("reference absente -> RessourceIntrouvableException")
    void referenceAbsente() {
        UUID ref = UUID.randomUUID();
        when(interventions.findByReference(ref)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.demarrer(ref))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("OptimisticLockingFailureException -> ConflitConcurrenceException")
    void traduitConflitConcurrence() {
        Intervention it = new Intervention("INT-2026-0001", rdv);
        UUID ref = it.getReference();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(it));
        doThrow(new OptimisticLockingFailureException("stale"))
                .when(interventions).saveAndFlush(it);

        assertThatThrownBy(() -> service.demarrer(ref))
                .isInstanceOf(ConflitConcurrenceException.class)
                .hasMessageContaining("rechargez");
    }

    @Test
    @DisplayName("suspendre : EN_COURS -> SUSPENDUE et sauvegarde")
    void suspendre() {
        Intervention it = new Intervention("INT-2026-0001", rdv);
        it.demarrer(MAINTENANT);
        UUID ref = it.getReference();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(it));
        when(interventions.saveAndFlush(it)).thenReturn(it);

        Intervention resultat = service.suspendre(ref);

        assertThat(resultat.getStatut()).isEqualTo(StatutIntervention.SUSPENDUE);
    }

    @Test
    @DisplayName("annuler : bascule en ANNULEE et sauvegarde")
    void annuler() {
        Intervention it = new Intervention("INT-2026-0001", rdv);
        UUID ref = it.getReference();
        when(interventions.findByReference(ref)).thenReturn(Optional.of(it));
        when(interventions.saveAndFlush(it)).thenReturn(it);

        Intervention resultat = service.annuler(ref);

        assertThat(resultat.getStatut()).isEqualTo(StatutIntervention.ANNULEE);
    }

    @Nested
    @DisplayName("depassement de devis (RM-15)")
    class Depassement {

        private static final ZoneId ZONE = ZoneId.of("Europe/Brussels");

        /** Devis initial : la vidange seule, 49,00 € HTVA. Seuil a 53,90 €. */
        private Intervention interventionEnCours() {
            Intervention it = new Intervention("INT-2026-0001", rdv);
            it.demarrer(MAINTENANT);
            when(interventions.findByReference(it.getReference())).thenReturn(Optional.of(it));
            when(interventions.saveAndFlush(it)).thenReturn(it);
            return it;
        }

        private Prestation prestationA(String prixHtva) {
            Categorie c = new Categorie("REP", "Reparation", TypeCategorie.SERVICE);
            Prestation p = new Prestation(c, "REP-1", "Plaquettes", new BigDecimal(prixHtva), 60);
            when(prestations.findByReference(p.getReference())).thenReturn(Optional.of(p));
            return p;
        }

        @Test
        @DisplayName("ajout au-dela du seuil : bascule et courriel de demande de validation")
        void notifieLeMembreALaBascule() {
            when(parametres.courants()).thenReturn(new ParametreAtelier());
            Intervention it = interventionEnCours();
            Prestation chere = prestationA("89.00");

            service.ajouterLigneMainOeuvre(it.getReference(), chere.getReference(), (short) 1);

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
            verify(courriel).envoyerDemandeValidationDepassement(eq(marie), any(),
                    any(DetailsDepassementCourriel.class));
        }

        @Test
        @DisplayName("ajout sous le seuil : aucun courriel, le garage n'est pas interrompu")
        void pasDeCourrielSousLeSeuil() {
            Intervention it = interventionEnCours();
            Prestation petite = prestationA("2.00");

            service.ajouterLigneMainOeuvre(it.getReference(), petite.getReference(), (short) 1);

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            verify(courriel, never()).envoyerDemandeValidationDepassement(any(), any(), any());
        }

        @Test
        @DisplayName("un fournisseur mail en panne n'annule pas la bascule deja persistee")
        void echecCourrielNAnnulePasLaBascule() {
            when(parametres.courants()).thenReturn(new ParametreAtelier());
            Intervention it = interventionEnCours();
            Prestation chere = prestationA("89.00");
            doThrow(new RuntimeException("SMTP indisponible"))
                    .when(courriel).envoyerDemandeValidationDepassement(any(), any(), any());

            service.ajouterLigneMainOeuvre(it.getReference(), chere.getReference(), (short) 1);

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
        }

        @Test
        @DisplayName("valider : le membre proprietaire fait repartir l'intervention")
        void validerParLeProprietaire() {
            Intervention it = interventionEnCours();
            Prestation chere = prestationA("89.00");
            when(parametres.courants()).thenReturn(new ParametreAtelier());
            service.ajouterLigneMainOeuvre(it.getReference(), chere.getReference(), (short) 1);

            Intervention resultat = service.validerDepassement(it.getReference(), "marie@exemple.be");

            assertThat(resultat.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(resultat.totalFacturableHtva()).isEqualByComparingTo("138.00");
        }

        @Test
        @DisplayName("refuser : lignes conservees hors total, intervention repartie")
        void refuserParLeProprietaire() {
            Intervention it = interventionEnCours();
            Prestation chere = prestationA("89.00");
            when(parametres.courants()).thenReturn(new ParametreAtelier());
            service.ajouterLigneMainOeuvre(it.getReference(), chere.getReference(), (short) 1);

            Intervention resultat = service.refuserDepassement(it.getReference(), "marie@exemple.be");

            assertThat(resultat.getStatut()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(resultat.getLignes()).hasSize(2);
            assertThat(resultat.totalFacturableHtva()).isEqualByComparingTo("49.00");
        }

        @Test
        @DisplayName("ownership : un autre membre recoit 404, pas 403 (l'existence n'est pas confirmee)")
        void ownershipSurLesReponses() {
            Intervention it = new Intervention("INT-2026-0001", rdv);
            it.demarrer(MAINTENANT);
            UUID ref = it.getReference();
            doReturn(Optional.of(it)).when(interventions).findByReference(ref);

            assertThatThrownBy(() -> service.validerDepassement(ref, "intrus@exemple.be"))
                    .isInstanceOf(RessourceIntrouvableException.class);
            assertThatThrownBy(() -> service.refuserDepassement(ref, "intrus@exemple.be"))
                    .isInstanceOf(RessourceIntrouvableException.class);
            assertThatThrownBy(() -> service.demandeValidation(ref, "intrus@exemple.be"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        /**
         * RM-14 vu du service : le trou de RM-15 se refermait a la source. Un ajout en
         * PLANIFIEE ne passait par aucun controle de seuil ; il est desormais refuse,
         * donc toute ligne parvenue au dossier a franchi le controle. Rien n est
         * persiste, et aucun courriel de depassement ne part.
         */
        @Test
        @DisplayName("ajout en PLANIFIEE : refuse, rien n'est ecrit, aucun courriel (RM-14)")
        void ajoutRefuseAvantDemarrage() {
            Intervention it = new Intervention("INT-2026-0001", rdv);
            when(interventions.findByReference(it.getReference())).thenReturn(Optional.of(it));
            Prestation chere = prestationA("500.00");

            assertThatThrownBy(() -> service.ajouterLigneMainOeuvre(
                    it.getReference(), chere.getReference(), (short) 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RM-14");

            assertThat(it.getStatut()).isEqualTo(StatutIntervention.PLANIFIEE);
            assertThat(it.getLignes()).hasSize(1);
            verify(interventions, never()).saveAndFlush(any());
            verify(courriel, never()).envoyerDemandeValidationDepassement(any(), any(), any());
        }

        @Test
        @DisplayName("demandeValidation hors attente : 404, on n'affiche pas un ecran de decision vide")
        void demandeValidationHorsAttente() {
            Intervention it = new Intervention("INT-2026-0001", rdv);
            UUID ref = it.getReference();
            doReturn(Optional.of(it)).when(interventions).findByReference(ref);

            assertThatThrownBy(() -> service.demandeValidation(ref, "marie@exemple.be"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("chronologie des statuts (F17)")
    class Chronologie {

        /** Derniere ligne d historique ecrite par le service. */
        private HistoriqueStatutIntervention derniereEntree() {
            ArgumentCaptor<HistoriqueStatutIntervention> captor =
                    ArgumentCaptor.forClass(HistoriqueStatutIntervention.class);
            verify(historiques).save(captor.capture());
            return captor.getValue();
        }

        private Intervention interventionChargee() {
            Intervention it = new Intervention("INT-2026-0001", rdv);
            doReturn(Optional.of(it)).when(interventions).findByReference(it.getReference());
            doReturn(it).when(interventions).saveAndFlush(it);
            return it;
        }

        @Test
        @DisplayName("creerDepuisRdv ecrit exactement une entree null -> PLANIFIEE")
        void creationHistorisee() {
            when(interventions.findByRdvId(any())).thenReturn(Optional.empty());
            when(numeros.prochain()).thenReturn("INT-2026-0001");
            when(interventions.saveAndFlush(any(Intervention.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.creerDepuisRdv(rdv);

            HistoriqueStatutIntervention entree = derniereEntree();
            assertThat(entree.getStatutAvant())
                    .as("La naissance du dossier n a pas d etat anterieur")
                    .isNull();
            assertThat(entree.getStatutApres()).isEqualTo(StatutIntervention.PLANIFIEE);
            assertThat(entree.getHorodatage()).isEqualTo(MAINTENANT);
            assertThat(entree.getAuteur())
                    .as("Sans contexte de securite, la transition est systeme")
                    .isNull();
        }

        @Test
        @DisplayName("creerDepuisRdv idempotent : aucune entree au second appel")
        void idempotenceSansEntree() {
            Intervention existante = new Intervention("INT-2026-0001", rdv);
            when(interventions.findByRdvId(any())).thenReturn(Optional.of(existante));

            service.creerDepuisRdv(rdv);

            verify(historiques, never()).save(any());
        }

        @Test
        @DisplayName("demarrer historise PLANIFIEE -> EN_COURS a l'instant de l'horloge")
        void demarrerHistorise() {
            Intervention it = interventionChargee();

            service.demarrer(it.getReference());

            HistoriqueStatutIntervention entree = derniereEntree();
            assertThat(entree.getStatutAvant()).isEqualTo(StatutIntervention.PLANIFIEE);
            assertThat(entree.getStatutApres()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(entree.getHorodatage()).isEqualTo(MAINTENANT);
        }

        @Test
        @DisplayName("suspendre historise EN_COURS -> SUSPENDUE")
        void suspendreHistorise() {
            Intervention it = interventionChargee();
            it.demarrer(MAINTENANT);

            service.suspendre(it.getReference());

            HistoriqueStatutIntervention entree = derniereEntree();
            assertThat(entree.getStatutAvant()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(entree.getStatutApres()).isEqualTo(StatutIntervention.SUSPENDUE);
        }

        @Test
        @DisplayName("reprendre historise SUSPENDUE -> EN_COURS")
        void reprendreHistorise() {
            Intervention it = interventionChargee();
            it.demarrer(MAINTENANT);
            it.suspendre();

            service.reprendre(it.getReference());

            HistoriqueStatutIntervention entree = derniereEntree();
            assertThat(entree.getStatutAvant()).isEqualTo(StatutIntervention.SUSPENDUE);
            assertThat(entree.getStatutApres()).isEqualTo(StatutIntervention.EN_COURS);
        }

        @Test
        @DisplayName("terminer historise EN_COURS -> TERMINEE")
        void terminerHistorise() {
            Intervention it = interventionChargee();
            it.demarrer(MAINTENANT);

            service.terminer(it.getReference());

            HistoriqueStatutIntervention entree = derniereEntree();
            assertThat(entree.getStatutAvant()).isEqualTo(StatutIntervention.EN_COURS);
            assertThat(entree.getStatutApres()).isEqualTo(StatutIntervention.TERMINEE);
            assertThat(entree.getHorodatage()).isEqualTo(MAINTENANT);
        }

        @Test
        @DisplayName("annuler historise PLANIFIEE -> ANNULEE")
        void annulerHistorise() {
            Intervention it = interventionChargee();

            service.annuler(it.getReference());

            HistoriqueStatutIntervention entree = derniereEntree();
            assertThat(entree.getStatutAvant()).isEqualTo(StatutIntervention.PLANIFIEE);
            assertThat(entree.getStatutApres()).isEqualTo(StatutIntervention.ANNULEE);
        }

        @Test
        @DisplayName("valider/refuser un depassement historise aussi : l'historique n'a pas de trou")
        void reponseMembreHistorisee() {
            when(parametres.courants()).thenReturn(new ParametreAtelier());
            Intervention it = interventionChargee();
            it.demarrer(MAINTENANT);
            Categorie c = new Categorie("REP", "Reparation", TypeCategorie.SERVICE);
            Prestation chere = new Prestation(c, "REP-1", "Plaquettes", new BigDecimal("89.00"), 60);
            when(prestations.findByReference(chere.getReference())).thenReturn(Optional.of(chere));

            // L ajout au-dela du seuil bascule l entite : premiere entree historisee.
            service.ajouterLigneMainOeuvre(it.getReference(), chere.getReference(), (short) 1);
            // La reponse du membre fait repartir le travail : seconde entree.
            service.validerDepassement(it.getReference(), "marie@exemple.be");

            ArgumentCaptor<HistoriqueStatutIntervention> captor =
                    ArgumentCaptor.forClass(HistoriqueStatutIntervention.class);
            verify(historiques, org.mockito.Mockito.times(2)).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getStatutApres())
                    .isEqualTo(StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
            assertThat(captor.getAllValues().get(1).getStatutAvant())
                    .isEqualTo(StatutIntervention.ATTENTE_VALIDATION_MEMBRE);
            assertThat(captor.getAllValues().get(1).getStatutApres())
                    .isEqualTo(StatutIntervention.EN_COURS);
        }

        @Test
        @DisplayName("transition illegale : l'exception sort du domaine, aucune entree ecrite")
        void transitionIllegaleSansEntree() {
            Intervention it = new Intervention("INT-2026-0001", rdv);
            doReturn(Optional.of(it)).when(interventions).findByReference(it.getReference());

            // PLANIFIEE -> TERMINEE est interdit par la machine a etats.
            assertThatThrownBy(() -> service.terminer(it.getReference()))
                    .isInstanceOf(IllegalStateException.class);

            verify(historiques, never()).save(any());
            verify(interventions, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("l'auteur de l'entree vient du resolveur, jamais d'un parametre")
        void auteurResoluDepuisLeContexte() {
            Utilisateur admin = new Utilisateur("admin@garage.be", "$2a$12$h", "Garage", "Paul",
                    TypeUtilisateur.ADMINISTRATEUR);
            // La resolution elle-meme (contexte de securite, anonyme, compte absent)
            // est prouvee par AuteurCourantTest : ici on verifie le raccordement.
            when(auteurCourant.resoudre()).thenReturn(admin);
            Intervention it = interventionChargee();

            service.demarrer(it.getReference());

            assertThat(derniereEntree().getAuteur()).isSameAs(admin);
        }

        @Test
        @DisplayName("la vue membre projette la chronologie sur les percus : les internes fusionnent (RM-16)")
        void vueMembreProjetteLesPercus() {
            when(parametres.courants()).thenReturn(new ParametreAtelier());
            // Pas de stub saveAndFlush ici : lecture pure, Mockito strict le refuserait.
            Intervention it = new Intervention("INT-2026-0001", rdv);
            doReturn(Optional.of(it)).when(interventions).findByReference(it.getReference());
            List<HistoriqueStatutIntervention> journal = List.of(
                    new HistoriqueStatutIntervention(it, null,
                            StatutIntervention.PLANIFIEE, MAINTENANT, null, null),
                    new HistoriqueStatutIntervention(it, StatutIntervention.PLANIFIEE,
                            StatutIntervention.EN_COURS, MAINTENANT.plusSeconds(600), null, null),
                    // Suspension et reprise : percues « En cours » toutes les deux,
                    // elles ne doivent produire aucune entree supplementaire.
                    new HistoriqueStatutIntervention(it, StatutIntervention.EN_COURS,
                            StatutIntervention.SUSPENDUE, MAINTENANT.plusSeconds(1200), null, null),
                    new HistoriqueStatutIntervention(it, StatutIntervention.SUSPENDUE,
                            StatutIntervention.EN_COURS, MAINTENANT.plusSeconds(1800), null, null),
                    new HistoriqueStatutIntervention(it, StatutIntervention.EN_COURS,
                            StatutIntervention.TERMINEE, MAINTENANT.plusSeconds(2400), null, null));
            when(historiques.findByInterventionOrderByHorodatageAscIdAsc(it)).thenReturn(journal);

            InterventionVueMembre vue = service.interventionDuMembre(
                    it.getReference(), "marie@exemple.be");

            assertThat(vue.chronologie())
                    .extracting(InterventionVueMembre.EntreeChronologieVue::statutApres)
                    .as("5 transitions techniques, 3 etapes percues, dans l ordre")
                    .containsExactly(StatutIntervention.PLANIFIEE, StatutIntervention.EN_COURS,
                            StatutIntervention.TERMINEE);
            assertThat(vue.chronologie().get(0).horodatage())
                    .as("La date arrive pre-formatee, fuseau applique")
                    .contains("septembre 2026");
        }

        @Test
        @DisplayName("chronologie d'autrui : 404, le journal n'est meme pas lu")
        void chronologieOwnership() {
            Intervention it = new Intervention("INT-2026-0001", rdv);
            doReturn(Optional.of(it)).when(interventions).findByReference(it.getReference());

            assertThatThrownBy(() -> service.interventionDuMembre(
                    it.getReference(), "intrus@exemple.be"))
                    .isInstanceOf(RessourceIntrouvableException.class);

            verify(historiques, never()).findByInterventionOrderByHorodatageAscIdAsc(any());
        }
    }

    @Nested
    @DisplayName("evenement de cloture (F17)")
    class EvenementCloture {

        private Intervention interventionChargee() {
            Intervention it = new Intervention("INT-2026-0001", rdv);
            doReturn(Optional.of(it)).when(interventions).findByReference(it.getReference());
            doReturn(it).when(interventions).saveAndFlush(it);
            return it;
        }

        @Test
        @DisplayName("terminer publie exactement un InterventionTermineeEvent avec la reference")
        void terminerPublie() {
            Intervention it = interventionChargee();
            it.demarrer(MAINTENANT);

            service.terminer(it.getReference());

            verify(evenements).publishEvent(new InterventionTermineeEvent(it.getReference()));
            org.mockito.Mockito.verifyNoMoreInteractions(evenements);
        }

        @Test
        @DisplayName("demarrer, suspendre, reprendre et annuler ne publient rien")
        void autresTransitionsSansEvenement() {
            Intervention it = interventionChargee();

            service.demarrer(it.getReference());
            service.suspendre(it.getReference());
            service.reprendre(it.getReference());
            service.annuler(it.getReference());

            org.mockito.Mockito.verifyNoInteractions(evenements);
        }

        @Test
        @DisplayName("terminer refuse par le domaine : aucun evenement ne part")
        void transitionRefuseeSansEvenement() {
            Intervention it = new Intervention("INT-2026-0001", rdv);
            doReturn(Optional.of(it)).when(interventions).findByReference(it.getReference());

            // PLANIFIEE -> TERMINEE est interdit : l exception sort avant la publication.
            assertThatThrownBy(() -> service.terminer(it.getReference()))
                    .isInstanceOf(IllegalStateException.class);

            org.mockito.Mockito.verifyNoInteractions(evenements);
        }
    }

    @Test
    @DisplayName("modifierCommentaireAdmin trim et persiste")
    void commentaire() {
        Intervention it = new Intervention("INT-2026-0001", rdv);
        UUID ref = it.getReference();
        doReturn(Optional.of(it)).when(interventions).findByReference(ref);
        doReturn(it).when(interventions).saveAndFlush(it);

        Intervention resultat = service.modifierCommentaireAdmin(ref, "  Piece commandee ");

        assertThat(resultat.getCommentaireAdmin()).isEqualTo("Piece commandee");
    }
}
