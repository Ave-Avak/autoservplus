package be.autoservplus.messagerie.domain;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
        return new Conversation("FIL-2026-0001", membre, null, "Question sur ma vidange");
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

            assertThatThrownBy(() -> new Conversation("FIL-2026-0001", marie, null, "   "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new Conversation("FIL-2026-0001", marie, null, "S".repeat(300)).getSujet())
                    .hasSize(Conversation.LONGUEUR_SUJET);
        }

        @Test
        @DisplayName("l'appartenance se lit sur l'adresse du membre, telle quelle : la base la garantit en minuscules (V55)")
        void appartenance() {
            Conversation fil = filDe(marie());

            assertThat(fil.appartientA("marie@exemple.be")).isTrue();
            assertThat(fil.appartientA("MARIE@EXEMPLE.BE")).isFalse();
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

            fil.marquerLuPar(RoleExpediteur.MEMBRE, T0.plusSeconds(60));

            assertThat(fil.nombreNonLusPar(RoleExpediteur.MEMBRE)).isZero();
            assertThat(fil.nombreNonLusPar(RoleExpediteur.ADMINISTRATEUR))
                    .as("la lecture du membre ne doit pas eteindre le compteur du garage")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("au plus un avis au garage par quart d'heure, compté depuis le dernier avis")
        void avisAuGarage() {
            Duration quartDHeure = Duration.ofMinutes(15);
            Conversation fil = filDe(marie());
            Utilisateur membre = fil.getMembre();

            fil.ajouter(membre, RoleExpediteur.MEMBRE, "Premier", T0);
            assertThat(fil.dernierMessageAppelleUnAvis(quartDHeure)).isTrue();

            fil.ajouter(membre, RoleExpediteur.MEMBRE, "Deuxième", T0.plus(Duration.ofMinutes(10)));
            assertThat(fil.dernierMessageAppelleUnAvis(quartDHeure)).isFalse();

            // 20 minutes après le premier avis, même si le message précédent date de 10 minutes.
            fil.ajouter(membre, RoleExpediteur.MEMBRE, "Troisième", T0.plus(Duration.ofMinutes(20)));
            assertThat(fil.dernierMessageAppelleUnAvis(quartDHeure)).isTrue();

            fil.ajouter(patron(), RoleExpediteur.ADMINISTRATEUR, "Réponse", T0.plus(Duration.ofMinutes(21)));
            assertThat(fil.dernierMessageAppelleUnAvis(quartDHeure))
                    .as("une réponse du garage n'appelle aucun avis au garage")
                    .isFalse();
        }

        @Test
        @DisplayName("la lecture est datée une fois, une relecture ne déplace pas la date")
        void dateDeLecture() {
            Conversation fil = filDe(marie());
            fil.ajouter(fil.getMembre(), RoleExpediteur.MEMBRE, "Bonjour ?", T0);
            fil.ajouter(patron(), RoleExpediteur.ADMINISTRATEUR, "Bonjour, oui.", T0);

            fil.marquerLuPar(RoleExpediteur.MEMBRE, T0.plusSeconds(60));
            fil.marquerLuPar(RoleExpediteur.MEMBRE, T0.plusSeconds(600));

            assertThat(fil.getMessages().get(1).getLuLe()).isEqualTo(T0.plusSeconds(60));
            assertThat(fil.getMessages().get(0).getLuLe())
                    .as("le message du membre n est pas lu par son auteur")
                    .isNull();
        }
    }
}
