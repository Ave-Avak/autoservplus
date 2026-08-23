package be.autoservplus.rgpd.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.rgpd.repository.TracesAuditRepository;
import be.autoservplus.rgpd.repository.VehiculeAnonymisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suppression de compte (F23) : les deux gardes, la capture de l adresse avant
 * ecrasement, et le traitement du parc. La chaine reelle contre une vraie base est
 * couverte par {@code SuppressionCompteIT}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuppressionCompteService")
class SuppressionCompteServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-23T10:00:00Z");
    private static final String MOT_DE_PASSE = "MonMotDePasse2026!";

    @Mock private UtilisateurRepository utilisateurs;
    @Mock private VehiculeAnonymisationRepository vehicules;
    @Mock private be.autoservplus.avis.repository.AvisRepository avis;
    @Mock private TracesAuditRepository tracesAudit;
    @Mock private ApplicationEventPublisher evenements;

    /** Encodeur reel : la re-authentification doit etre exercee, pas simulee. */
    private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);

    private SuppressionCompteService service;
    private Utilisateur marie;

    @BeforeEach
    void setUp() {
        service = new SuppressionCompteService(utilisateurs, vehicules, avis, tracesAudit,
                encodeur, evenements, Clock.fixed(MAINTENANT, ZoneId.of("Europe/Brussels")));
        marie = new Utilisateur("marie@exemple.be", encodeur.encode(MOT_DE_PASSE),
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    }

    private void compteExiste() {
        when(utilisateurs.findByEmailIgnoreCase("marie@exemple.be"))
                .thenReturn(Optional.of(marie));
    }

    private void parcVide() {
        when(vehicules.tousLesVehicules(marie.getId())).thenReturn(List.of());
        when(utilisateurs.saveAndFlush(marie)).thenReturn(marie);
        when(tracesAudit.anonymiser(anyString(), anyString())).thenReturn(7);
    }

    private Vehicule vehicule(String plaque) {
        return new Vehicule(marie, plaque, "VW", "Golf", Motorisation.DIESEL);
    }

    @Nested
    @DisplayName("gardes")
    class Gardes {

        @Test
        @DisplayName("mot de passe incorrect : rien n'est ecrit")
        void motDePasseIncorrect() {
            compteExiste();

            assertThatThrownBy(() -> service.supprimer(
                    "marie@exemple.be", "mauvais", "SUPPRIMER"))
                    .isInstanceOf(ReauthentificationEchoueeException.class);

            assertThat(marie.estAnonymise()).isFalse();
            assertThat(marie.getEmail()).isEqualTo("marie@exemple.be");
            verify(vehicules, never()).tousLesVehicules(any());
            verify(tracesAudit, never()).anonymiser(anyString(), anyString());
            verify(evenements, never()).publishEvent(any(CompteSupprimeEvent.class));
        }

        @Test
        @DisplayName("mot de passe absent : refus propre, pas d'exception technique")
        void motDePasseAbsent() {
            compteExiste();

            // BCrypt n accepte pas le null : il est ecarte avant l appel a l encodeur.
            assertThatThrownBy(() -> service.supprimer("marie@exemple.be", null, "SUPPRIMER"))
                    .isInstanceOf(ReauthentificationEchoueeException.class);
            assertThatThrownBy(() -> service.supprimer("marie@exemple.be", "", "SUPPRIMER"))
                    .isInstanceOf(ReauthentificationEchoueeException.class);
        }

        @Test
        @DisplayName("confirmation absente ou mal recopiee : rien n'est ecrit")
        void confirmationInvalide() {
            compteExiste();

            for (String saisie : new String[]{null, "", "supprimer", "SUPPRIME", "SUPPRIMER!"}) {
                assertThatThrownBy(() -> service.supprimer(
                        "marie@exemple.be", MOT_DE_PASSE, saisie))
                        .isInstanceOf(ConfirmationSuppressionInvalideException.class);
            }
            assertThat(marie.estAnonymise()).isFalse();
            verify(evenements, never()).publishEvent(any(CompteSupprimeEvent.class));
        }

        @Test
        @DisplayName("les espaces autour du mot sont tolerees, la casse ne l'est pas")
        void confirmationNettoyee() {
            compteExiste();
            parcVide();

            service.supprimer("marie@exemple.be", MOT_DE_PASSE, "  SUPPRIMER  ");

            assertThat(marie.estAnonymise()).isTrue();
        }

        @Test
        @DisplayName("une adresse inconnue remonte comme introuvable")
        void compteInconnu() {
            when(utilisateurs.findByEmailIgnoreCase("fantome@exemple.be"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.supprimer(
                    "fantome@exemple.be", MOT_DE_PASSE, "SUPPRIMER"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("anonymisation")
    class Anonymisation {

        @Test
        @DisplayName("l'adresse est capturee AVANT l'ecrasement et voyage dans l'evenement")
        void adresseCapturee() {
            compteExiste();
            parcVide();

            service.supprimer("marie@exemple.be", MOT_DE_PASSE, "SUPPRIMER");

            ArgumentCaptor<CompteSupprimeEvent> capture =
                    ArgumentCaptor.forClass(CompteSupprimeEvent.class);
            verify(evenements).publishEvent(capture.capture());
            CompteSupprimeEvent evenement = capture.getValue();
            // Recharger apres coup ne donnerait que le jeton non routable : sans
            // capture, le courriel de confirmation n aurait plus de destinataire.
            assertThat(evenement.adresseEmail()).isEqualTo("marie@exemple.be");
            assertThat(evenement.prenom()).isEqualTo("Marie");
            assertThat(evenement.reference()).isEqualTo(marie.getReference());
            assertThat(marie.getEmail()).isNotEqualTo("marie@exemple.be");
        }

        @Test
        @DisplayName("le jeton de substitution est non routable et le hachage reste un vrai BCrypt")
        void jetonEtHachage() {
            compteExiste();
            parcVide();

            service.supprimer("marie@exemple.be", MOT_DE_PASSE, "SUPPRIMER");

            // TLD reserve par la RFC 2606 : aucun envoi accidentel ne partira.
            assertThat(marie.getEmail()).endsWith("@supprime.invalid");
            // Format BCrypt conserve : l ancien mot de passe ne connecte plus, et la
            // verification refuse proprement au lieu de lever sur un format invalide.
            assertThat(marie.getMotDePasseHache()).hasSize(60).startsWith("$2");
            assertThat(encodeur.matches(MOT_DE_PASSE, marie.getMotDePasseHache())).isFalse();
        }

        @Test
        @DisplayName("le balayage des traces d'audit porte l'ancienne adresse et le jeton")
        void balayageDesTraces() {
            compteExiste();
            parcVide();

            service.supprimer("marie@exemple.be", MOT_DE_PASSE, "SUPPRIMER");

            verify(tracesAudit).anonymiser("marie@exemple.be", marie.getEmail());
            // Le compte est ecrit avant le balayage : sinon l audit JPA reposerait
            // updated_by = adresse du membre apres le passage du balayage.
            verify(utilisateurs).saveAndFlush(marie);
        }
    }

    @Nested
    @DisplayName("parc de vehicules")
    class Parc {

        @Test
        @DisplayName("un vehicule sans historique est supprime physiquement")
        void vehiculeSansHistorique() {
            compteExiste();
            Vehicule golf = vehicule("1-ABC-123");
            when(vehicules.tousLesVehicules(marie.getId())).thenReturn(List.of(golf));
            when(vehicules.nombreReferencesHistoriques(golf.getId())).thenReturn(0L);
            when(utilisateurs.saveAndFlush(marie)).thenReturn(marie);
            when(tracesAudit.anonymiser(anyString(), anyString())).thenReturn(3);

            service.supprimer("marie@exemple.be", MOT_DE_PASSE, "SUPPRIMER");

            verify(vehicules).supprimerPhysiquement(golf.getId());
            verify(vehicules, never()).saveAndFlush(any(Vehicule.class));
        }

        @Test
        @DisplayName("un vehicule reference par un historique est anonymise, pas supprime")
        void vehiculeAvecHistorique() {
            compteExiste();
            Vehicule golf = vehicule("1-ABC-123");
            when(vehicules.tousLesVehicules(marie.getId())).thenReturn(List.of(golf));
            when(vehicules.nombreReferencesHistoriques(golf.getId())).thenReturn(2L);
            lenient().when(vehicules.saveAndFlush(golf)).thenReturn(golf);
            when(utilisateurs.saveAndFlush(marie)).thenReturn(marie);
            when(tracesAudit.anonymiser(anyString(), anyString())).thenReturn(3);

            service.supprimer("marie@exemple.be", MOT_DE_PASSE, "SUPPRIMER");

            // Les FK ON DELETE RESTRICT interdisent la suppression physique : le
            // patron RM-29 anonymise a la place, l historique reste lisible.
            verify(vehicules, never()).supprimerPhysiquement(any());
            assertThat(golf.getPlaque()).isNotEqualTo("1-ABC-123").startsWith("ANON-");
            assertThat(golf.getNumeroChassis()).isNull();
            assertThat(golf.getKilometrage()).isNull();
            assertThat(golf.isActif()).isFalse();
            // Marque et modele restent : ils decrivent un objet, pas une personne.
            assertThat(golf.getMarque()).isEqualTo("VW");
        }
    }
}
