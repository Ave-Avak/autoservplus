package be.autoservplus.messagerie.domain;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Conversation et Message (BL-5)")
class ConversationTest {

    private static final Instant T0 = Instant.parse("2026-08-23T08:00:00Z");

    private static Utilisateur marie() {
        return new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
    }

    private static Utilisateur patron() {
        return new Utilisateur("patron@autoservplus.be", "$2a$12$h", "Garage", "Patron",
                TypeUtilisateur.ADMINISTRATEUR);
    }

    private static Conversation filDe(Utilisateur membre) {
        return new Conversation(membre, null, "Question sur ma vidange");
    }

    @Nested
    @DisplayName("Ouverture")
    class Ouverture {

        @Test
        @DisplayName("nait ouverte, sans message, avec une reference")
        void etatInitial() {
            Conversation fil = filDe(marie());

            assertThat(fil.isCloturee()).isFalse();
            assertThat(fil.getMessages()).isEmpty();
            assertThat(fil.getReference()).isNotNull();
            assertThat(fil.getIntervention())
                    .as("un fil libre est prevu : intervention_id est nullable au socle")
                    .isNull();
        }

        @Test
        @DisplayName("refuse un sujet vide et tronque un sujet trop long")
        void sujet() {
            Utilisateur marie = marie();

            assertThatThrownBy(() -> new Conversation(marie, null, "   "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new Conversation(marie, null, "S".repeat(300)).getSujet())
                    .hasSize(Conversation.LONGUEUR_SUJET);
        }

        @Test
        @DisplayName("l appartenance se lit sur le membre, sans tenir compte de la casse")
        void appartenance() {
            Conversation fil = filDe(marie());

            assertThat(fil.appartientA("MARIE@EXEMPLE.BE")).isTrue();
            assertThat(fil.appartientA("paul@exemple.be")).isFalse();
        }
    }

    @Nested
    @DisplayName("Echange")
    class Echange {

        @Test
        @DisplayName("refuse un message vide")
        void messageVide() {
            Conversation fil = filDe(marie());
            Utilisateur marie = fil.getMembre();

            assertThatThrownBy(() -> fil.ajouter(marie, RoleExpediteur.MEMBRE, "  ", T0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("un fil clos n accepte plus de message")
        void filClos() {
            Conversation fil = filDe(marie());
            Utilisateur marie = fil.getMembre();
            fil.cloturer();

            assertThatThrownBy(() -> fil.ajouter(marie, RoleExpediteur.MEMBRE, "Encore ?", T0))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("la reouverture rend le fil de nouveau accessible")
        void reouverture() {
            Conversation fil = filDe(marie());
            fil.cloturer();
            fil.rouvrir();

            assertThat(fil.isCloturee()).isFalse();
            assertThat(fil.ajouter(fil.getMembre(), RoleExpediteur.MEMBRE, "Merci.", T0))
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("Lecture")
    class Lecture {

        @Test
        @DisplayName("un message ne compte comme non lu que pour le camp oppose")
        void nonLuPourLeCampOppose() {
            Conversation fil = filDe(marie());
            fil.ajouter(fil.getMembre(), RoleExpediteur.MEMBRE, "Bonjour ?", T0);

            assertThat(fil.nombreNonLusPar(RoleExpediteur.ADMINISTRATEUR))
                    .as("c est au garage de lire ce que le membre a ecrit")
                    .isEqualTo(1);
            assertThat(fil.nombreNonLusPar(RoleExpediteur.MEMBRE))
                    .as("un expediteur n a pas a lire son propre message")
                    .isZero();
        }

        @Test
        @DisplayName("marquer lu n affecte que les messages venus d en face")
        void marquageCible() {
            Conversation fil = filDe(marie());
            fil.ajouter(fil.getMembre(), RoleExpediteur.MEMBRE, "Bonjour ?", T0);
            fil.ajouter(patron(), RoleExpediteur.ADMINISTRATEUR, "Bonjour, oui.", T0);

            fil.marquerLuPar(RoleExpediteur.MEMBRE);

            assertThat(fil.nombreNonLusPar(RoleExpediteur.MEMBRE)).isZero();
            assertThat(fil.nombreNonLusPar(RoleExpediteur.ADMINISTRATEUR))
                    .as("la lecture du membre ne doit pas eteindre le compteur du garage")
                    .isEqualTo(1);
        }
    }
}
