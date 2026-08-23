package be.autoservplus.avis.domain;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Avis (BL-4)")
class AvisTest {

    private static final Instant DEPOT = Instant.parse("2026-08-23T08:00:00Z");

    private static Utilisateur marie() {
        return new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
    }

    private static Intervention interventionAuStatut(StatutIntervention statut) {
        Intervention intervention = mock(Intervention.class);
        when(intervention.getStatut()).thenReturn(statut);
        return intervention;
    }

    private static Avis avisDe(short note, String commentaire) {
        return new Avis(marie(), interventionAuStatut(StatutIntervention.TERMINEE),
                note, commentaire, DEPOT);
    }

    @Nested
    @DisplayName("Depot")
    class Depot {

        @Test
        @DisplayName("nait publie et non signale : moderation a posteriori")
        void etatInitial() {
            Avis avis = avisDe((short) 4, "Travail soigne.");

            assertThat(avis.isPublie())
                    .as("un avis retenu jusqu a approbation ne serait plus un avis")
                    .isTrue();
            assertThat(avis.isSignale()).isFalse();
            assertThat(avis.getNote()).isEqualTo((short) 4);
            assertThat(avis.getReference()).isNotNull();
            assertThat(avis.getDateDepot()).isEqualTo(DEPOT);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5})
        @DisplayName("accepte toute note dans les bornes du CHECK du socle")
        void notesAdmises(int note) {
            assertThat(avisDe((short) note, null).getNote()).isEqualTo((short) note);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 6, -1, 100})
        @DisplayName("refuse une note hors bornes avant meme d atteindre la base")
        void notesRefusees(int note) {
            assertThatThrownBy(() -> avisDe((short) note, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("note");
        }

        @ParameterizedTest
        @DisplayName("refuse toute intervention non terminee")
        @ValueSource(strings = {"PLANIFIEE", "EN_COURS", "SUSPENDUE",
                "ATTENTE_VALIDATION_MEMBRE", "ANNULEE"})
        void interventionNonTerminee(String statut) {
            Intervention intervention = interventionAuStatut(StatutIntervention.valueOf(statut));

            assertThatThrownBy(() -> new Avis(marie(), intervention, (short) 5, null, DEPOT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("terminee");
        }

        @Test
        @DisplayName("un commentaire vide vaut absence de commentaire")
        void commentaireVide() {
            assertThat(avisDe((short) 3, "   ").aUnCommentaire()).isFalse();
            assertThat(avisDe((short) 3, "").aUnCommentaire()).isFalse();
            assertThat(avisDe((short) 3, null).aUnCommentaire()).isFalse();
        }

        @Test
        @DisplayName("le commentaire est deleste de ses espaces de bord")
        void commentaireNormalise() {
            assertThat(avisDe((short) 3, "  Rien a redire.  ").getCommentaire())
                    .isEqualTo("Rien a redire.");
        }
    }

    @Nested
    @DisplayName("Moderation")
    class Moderation {

        @Test
        @DisplayName("masquer retire de l affichage sans rien effacer")
        void masquer() {
            Avis avis = avisDe((short) 1, "Contenu litigieux.");

            avis.masquer();

            assertThat(avis.isPublie()).isFalse();
            assertThat(avis.getCommentaire())
                    .as("la trace reste : le garage doit pouvoir justifier un retrait")
                    .isEqualTo("Contenu litigieux.");
            assertThat(avis.getNote()).isEqualTo((short) 1);
        }

        @Test
        @DisplayName("publier et signaler sont deux axes independants")
        void axesIndependants() {
            Avis avis = avisDe((short) 2, "A verifier.");

            avis.signaler();

            assertThat(avis.isSignale()).isTrue();
            assertThat(avis.isPublie())
                    .as("un doute signale ne masque pas automatiquement")
                    .isTrue();

            avis.leverLeSignalement();
            assertThat(avis.isSignale()).isFalse();
        }
    }

    @Nested
    @DisplayName("Anonymisation (F23)")
    class Anonymisation {

        @Test
        @DisplayName("efface le commentaire et conserve la note")
        void commentaireEfface() {
            Avis avis = avisDe((short) 5, "Merci a Marie Dupont, 1-ABC-123.");

            avis.anonymiserCommentaire();

            assertThat(avis.getCommentaire()).isNull();
            assertThat(avis.aUnCommentaire()).isFalse();
            assertThat(avis.getNote())
                    .as("un chiffre de 1 a 5 ne designe personne et nourrit une moyenne publique")
                    .isEqualTo((short) 5);
        }

        @Test
        @DisplayName("laisse l avis publie : le depublier serait une decision commerciale")
        void resteEnLigne() {
            Avis avis = avisDe((short) 1, "Tres decu.");

            avis.anonymiserCommentaire();

            assertThat(avis.isPublie()).isTrue();
        }
    }
}
