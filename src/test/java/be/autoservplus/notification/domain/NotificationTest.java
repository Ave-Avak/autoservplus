package be.autoservplus.notification.domain;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Notification (BL-6)")
class NotificationTest {

    private static final Instant ENVOI = Instant.parse("2026-08-23T08:00:00Z");
    private static final Instant LECTURE = Instant.parse("2026-08-23T09:00:00Z");
    private static final Instant PLUS_TARD = Instant.parse("2026-08-24T10:00:00Z");

    private static Utilisateur marie() {
        return new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
    }

    private static Notification notification() {
        return new Notification(marie(), TypeNotification.RDV_CONFIRME,
                "Rendez-vous confirme", "RDV-2026-0007", ENVOI);
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("nait non lue, sur le canal applicatif, sans date de lecture")
        void etatInitial() {
            Notification notification = notification();

            assertThat(notification.estNonLue()).isTrue();
            assertThat(notification.getStatut()).isEqualTo(StatutNotification.NON_LUE);
            assertThat(notification.getCanal()).isEqualTo(CanalNotification.APPLICATION);
            assertThat(notification.getDateEnvoi()).isEqualTo(ENVOI);
            assertThat(notification.getDateLecture()).isNull();
            assertThat(notification.getCorps()).isEqualTo("RDV-2026-0007");
        }

        @Test
        @DisplayName("tronque la trace au format de la colonne titre du socle (150)")
        void traceTronquee() {
            String trop_long = "T".repeat(200);

            Notification notification = new Notification(marie(), TypeNotification.RDV_CONFIRME,
                    trop_long, "RDV-2026-0007", ENVOI);

            assertThat(notification.getTitre()).hasSize(150);
        }

        @Test
        @DisplayName("refuse un membre, un type ou un argument absent")
        void argumentsObligatoires() {
            assertThatThrownBy(() -> new Notification(null, TypeNotification.RDV_CONFIRME,
                    "t", "a", ENVOI)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Notification(marie(), null,
                    "t", "a", ENVOI)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Notification(marie(), TypeNotification.RDV_CONFIRME,
                    "t", null, ENVOI)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Marquage comme lue")
    class Marquage {

        @Test
        @DisplayName("bascule le statut et horodate la lecture")
        void premiereLecture() {
            Notification notification = notification();

            notification.marquerLue(LECTURE);

            assertThat(notification.estNonLue()).isFalse();
            assertThat(notification.getStatut()).isEqualTo(StatutNotification.LUE);
            assertThat(notification.getDateLecture()).isEqualTo(LECTURE);
        }

        @Test
        @DisplayName("est idempotent : un second appel ne deplace pas la date de lecture")
        void secondAppelSansEffet() {
            Notification notification = notification();
            notification.marquerLue(LECTURE);

            notification.marquerLue(PLUS_TARD);

            assertThat(notification.getDateLecture())
                    .as("la premiere lecture est celle qui compte")
                    .isEqualTo(LECTURE);
        }
    }

    @Nested
    @DisplayName("Cles i18n du type")
    class Cles {

        @Test
        @DisplayName("derivent du nom de la valeur")
        void clesDerivees() {
            assertThat(TypeNotification.AVOIR_EMIS.cleTitre())
                    .isEqualTo("notification.type.AVOIR_EMIS.titre");
            assertThat(TypeNotification.AVOIR_EMIS.cleCorps())
                    .isEqualTo("notification.type.AVOIR_EMIS.corps");
        }
    }
}
