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
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.InterventionRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @Mock private PrestationRepository prestations;
    @Mock private ParametreAtelierRepository parametres;
    @Mock private GenerateurNumeroIntervention numeros;
    @Mock private ServiceCourriel courriel;

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
        service = new InterventionService(interventions, prestations, parametres, numeros,
                courriel, horloge);

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
            assertThat(l.isValidee()).isTrue();
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
