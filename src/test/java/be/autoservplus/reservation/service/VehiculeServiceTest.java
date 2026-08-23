package be.autoservplus.reservation.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.reservation.domain.Motorisation;
import be.autoservplus.reservation.domain.Vehicule;
import be.autoservplus.reservation.repository.VehiculeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires de la gestion du parc de vehicules.
 *
 * <p>Une part importante de ces tests porte sur le controle du proprietaire : c est le
 * seul rempart contre l acces au vehicule d autrui par manipulation de l URL.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VehiculeService")
class VehiculeServiceTest {

    private static final String MARIE = "marie@exemple.be";
    private static final String PAUL = "paul@exemple.be";

    @Mock private VehiculeRepository vehicules;
    @Mock private UtilisateurRepository membres;

    @InjectMocks private VehiculeService service;

    @Nested
    @DisplayName("ajout")
    class Ajout {

        @Test
        @DisplayName("enregistre le vehicule au nom du membre")
        void enregistreLeVehicule() {
            when(vehicules.existsByPlaque("1-ABC-123")).thenReturn(false);
            when(vehicules.countByMembreEmailAndActifTrue(MARIE)).thenReturn(0L);
            when(membres.findByEmailIgnoreCase(MARIE)).thenReturn(Optional.of(membre(MARIE)));
            when(vehicules.save(any(Vehicule.class))).thenAnswer(i -> i.getArgument(0));

            Vehicule vehicule = service.ajouter(MARIE, "1-ABC-123", "Volkswagen", "Golf",
                    Motorisation.DIESEL, (short) 2018, 85_000);

            assertThat(vehicule.getPlaque()).isEqualTo("1-ABC-123");
            assertThat(vehicule.getMarque()).isEqualTo("Volkswagen");
            assertThat(vehicule.getKilometrage()).isEqualTo(85_000);
            assertThat(vehicule.getReference()).isNotNull();
        }

        @Test
        @DisplayName("normalise la plaque en majuscules et sans espaces")
        void normaliseLaPlaque() {
            when(vehicules.existsByPlaque("1-ABC-123")).thenReturn(false);
            when(vehicules.countByMembreEmailAndActifTrue(MARIE)).thenReturn(0L);
            when(membres.findByEmailIgnoreCase(MARIE)).thenReturn(Optional.of(membre(MARIE)));
            when(vehicules.save(any(Vehicule.class))).thenAnswer(i -> i.getArgument(0));

            Vehicule vehicule = service.ajouter(MARIE, "  1-abc-123 ", "Volkswagen", "Golf",
                    Motorisation.DIESEL, null, null);

            assertThat(vehicule.getPlaque()).isEqualTo("1-ABC-123");
        }

        @Test
        @DisplayName("refuse une plaque deja enregistree")
        void refuseUnePlaqueDupliquee() {
            when(vehicules.existsByPlaque("1-ABC-123")).thenReturn(true);

            assertThatThrownBy(() -> service.ajouter(MARIE, "1-ABC-123", "Volkswagen", "Golf",
                    Motorisation.DIESEL, null, null))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-12");

            verify(vehicules, never()).save(any());
        }

        @Test
        @DisplayName("refuse au-dela de la limite de vehicules")
        void refuseAuDelaDeLaLimite() {
            when(vehicules.existsByPlaque("1-ABC-123")).thenReturn(false);
            when(vehicules.countByMembreEmailAndActifTrue(MARIE)).thenReturn(20L);

            assertThatThrownBy(() -> service.ajouter(MARIE, "1-ABC-123", "Volkswagen", "Golf",
                    Motorisation.DIESEL, null, null))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-13");
        }

        @Test
        @DisplayName("refuse un membre inconnu")
        void refuseUnMembreInconnu() {
            when(vehicules.existsByPlaque("1-ABC-123")).thenReturn(false);
            when(vehicules.countByMembreEmailAndActifTrue("inconnu@exemple.be")).thenReturn(0L);
            when(membres.findByEmailIgnoreCase("inconnu@exemple.be")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.ajouter("inconnu@exemple.be", "1-ABC-123",
                    "Volkswagen", "Golf", Motorisation.DIESEL, null, null))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("controle du proprietaire")
    class Proprietaire {

        @Test
        @DisplayName("retourne le vehicule a son proprietaire")
        void retourneAuProprietaire() {
            Vehicule vehicule = vehiculeDe(MARIE);
            when(vehicules.findByReference(vehicule.getReference())).thenReturn(Optional.of(vehicule));

            assertThat(service.vehiculeDuMembre(vehicule.getReference(), MARIE)).isEqualTo(vehicule);
        }

        @Test
        @DisplayName("refuse l acces au vehicule d un autre membre")
        void refuseLAccesAUnAutreMembre() {
            Vehicule vehicule = vehiculeDe(MARIE);
            when(vehicules.findByReference(vehicule.getReference())).thenReturn(Optional.of(vehicule));

            assertThatThrownBy(() -> service.vehiculeDuMembre(vehicule.getReference(), PAUL))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        @Test
        @DisplayName("repond de facon identique pour un vehicule inexistant et pour celui d autrui")
        void neRevelePasLExistence() {
            Vehicule vehicule = vehiculeDe(MARIE);
            UUID inconnue = UUID.randomUUID();
            when(vehicules.findByReference(vehicule.getReference())).thenReturn(Optional.of(vehicule));
            when(vehicules.findByReference(inconnue)).thenReturn(Optional.empty());

            Class<?> surVehiculeDAutrui = attraper(() -> service.vehiculeDuMembre(vehicule.getReference(), PAUL));
            Class<?> surReferenceInconnue = attraper(() -> service.vehiculeDuMembre(inconnue, PAUL));

            assertThat(surVehiculeDAutrui).isEqualTo(surReferenceInconnue);
        }

        @Test
        @DisplayName("tolere une difference de casse dans l adresse")
        void tolereLaCasse() {
            Vehicule vehicule = vehiculeDe(MARIE);
            when(vehicules.findByReference(vehicule.getReference())).thenReturn(Optional.of(vehicule));

            assertThat(service.vehiculeDuMembre(vehicule.getReference(), "MARIE@EXEMPLE.BE"))
                    .isEqualTo(vehicule);
        }
    }

    @Nested
    @DisplayName("kilometrage")
    class Kilometrage {

        @Test
        @DisplayName("enregistre un releve superieur au precedent")
        void enregistreUnReleveSuperieur() {
            Vehicule vehicule = vehiculeDe(MARIE);
            vehicule.mettreAJourKilometrage(85_000);
            when(vehicules.findByReference(vehicule.getReference())).thenReturn(Optional.of(vehicule));

            service.releverKilometrage(vehicule.getReference(), MARIE, 92_000);

            assertThat(vehicule.getKilometrage()).isEqualTo(92_000);
        }

        @Test
        @DisplayName("refuse un compteur qui recule")
        void refuseUnCompteurQuiRecule() {
            Vehicule vehicule = vehiculeDe(MARIE);
            vehicule.mettreAJourKilometrage(85_000);
            when(vehicules.findByReference(vehicule.getReference())).thenReturn(Optional.of(vehicule));

            assertThatThrownBy(() -> service.releverKilometrage(vehicule.getReference(), MARIE, 70_000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ne peut pas diminuer");

            assertThat(vehicule.getKilometrage()).isEqualTo(85_000);
        }

        @Test
        @DisplayName("refuse un kilometrage negatif")
        void refuseUnKilometrageNegatif() {
            Vehicule vehicule = vehiculeDe(MARIE);
            when(vehicules.findByReference(vehicule.getReference())).thenReturn(Optional.of(vehicule));

            assertThatThrownBy(() -> service.releverKilometrage(vehicule.getReference(), MARIE, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("modification et suppression")
    class ModificationSuppression {

        @Test
        @DisplayName("met a jour les caracteristiques sans toucher a la plaque")
        void modifieSansToucherALaPlaque() {
            Vehicule vehicule = vehiculeDe(MARIE);
            when(vehicules.findByReference(vehicule.getReference())).thenReturn(Optional.of(vehicule));

            service.modifier(vehicule.getReference(), MARIE, "Audi", "A3",
                    Motorisation.ESSENCE, (short) 2020, "WAUZZZ8V");

            assertThat(vehicule.getMarque()).isEqualTo("Audi");
            assertThat(vehicule.getModele()).isEqualTo("A3");
            assertThat(vehicule.getMotorisation()).isEqualTo(Motorisation.ESSENCE);
            assertThat(vehicule.getPlaque()).isEqualTo("1-ABC-123");
        }

        @Test
        @DisplayName("supprime logiquement sans appeler delete")
        void supprimeLogiquement() {
            Vehicule vehicule = vehiculeDe(MARIE);
            when(vehicules.findByReference(vehicule.getReference())).thenReturn(Optional.of(vehicule));

            service.supprimer(vehicule.getReference(), MARIE);

            assertThat(vehicule.estSupprime()).isTrue();
            assertThat(vehicule.getDeletedBy()).isEqualTo(MARIE);
            verify(vehicules, never()).delete(any());
        }

        @Test
        @DisplayName("refuse la suppression du vehicule d un autre membre")
        void refuseLaSuppressionDAutrui() {
            Vehicule vehicule = vehiculeDe(MARIE);
            when(vehicules.findByReference(vehicule.getReference())).thenReturn(Optional.of(vehicule));

            assertThatThrownBy(() -> service.supprimer(vehicule.getReference(), PAUL))
                    .isInstanceOf(RessourceIntrouvableException.class);

            assertThat(vehicule.estSupprime()).isFalse();
        }
    }

    @Nested
    @DisplayName("consultation")
    class Consultation {

        @Test
        @DisplayName("liste les vehicules du membre")
        void listeLesVehicules() {
            when(vehicules.findByMembre(MARIE)).thenReturn(List.of(vehiculeDe(MARIE)));

            assertThat(service.vehiculesDuMembre(MARIE))
                    .hasSize(1)
                    .first()
                    .extracting(Vehicule::getPlaque)
                    .isEqualTo("1-ABC-123");
        }
    }

    // --- utilitaires -------------------------------------------------------------------

    private Utilisateur membre(String email) {
        return new Utilisateur(email, "$2a$12$empreinte", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    }

    private Vehicule vehiculeDe(String email) {
        return new Vehicule(membre(email), "1-ABC-123", "Volkswagen", "Golf", Motorisation.DIESEL);
    }

    /** Retourne le type d exception levee, afin de comparer deux comportements. */
    private Class<?> attraper(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e.getClass();
        }
    }
}