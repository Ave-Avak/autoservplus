package be.autoservplus.notification.service;

import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.notification.domain.Notification;
import be.autoservplus.notification.domain.StatutNotification;
import be.autoservplus.notification.domain.TypeNotification;
import be.autoservplus.notification.repository.NotificationRepository;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticMessageSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link NotificationService} (BL-6).
 *
 * <p>{@link StaticMessageSource} plutot qu un mock de {@code MessageSource} : on veut
 * verifier que le service <b>compose le message</b> a partir du type et de l argument,
 * pas seulement qu il appelle une methode.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationService (BL-6)")
class NotificationServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-23T08:00:00Z");
    private static final String MARIE = "marie@exemple.be";
    private static final String PAUL = "paul@exemple.be";

    @Mock private NotificationRepository notifications;
    @Mock private UtilisateurRepository membres;
    @Mock private ParametreAtelierRepository parametres;

    private NotificationService service;
    private Utilisateur marie;
    private Utilisateur paul;

    @BeforeEach
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("notification.type.RDV_CONFIRME.titre", Locale.FRENCH,
                "Rendez-vous confirmé");
        messages.addMessage("notification.type.RDV_CONFIRME.corps", Locale.FRENCH,
                "Le garage a confirmé votre rendez-vous {0}.");

        marie = new Utilisateur(MARIE, "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        paul = new Utilisateur(PAUL, "$2a$12$h", "Martin", "Paul", TypeUtilisateur.MEMBRE);

        when(membres.findByEmailIgnoreCase(MARIE)).thenReturn(Optional.of(marie));
        when(membres.findByEmailIgnoreCase(PAUL)).thenReturn(Optional.of(paul));
        when(parametres.courants()).thenReturn(new ParametreAtelier());

        Locale.setDefault(Locale.FRENCH);
        service = new NotificationService(notifications, membres, parametres, messages,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    private Notification notificationDe(Utilisateur membre) {
        return new Notification(membre, TypeNotification.RDV_CONFIRME,
                "trace", "RDV-2026-0007", MAINTENANT);
    }

    @Nested
    @DisplayName("Depot")
    class Depot {

        @Test
        @DisplayName("enregistre une notification non lue portant l argument metier")
        void depotEnregistre() {
            service.deposer(marie, TypeNotification.RDV_CONFIRME, "RDV-2026-0007");

            ArgumentCaptor<Notification> capture = ArgumentCaptor.forClass(Notification.class);
            verify(notifications).save(capture.capture());
            Notification enregistree = capture.getValue();
            assertThat(enregistree.getMembre()).isEqualTo(marie);
            assertThat(enregistree.getType()).isEqualTo(TypeNotification.RDV_CONFIRME);
            assertThat(enregistree.getCorps()).isEqualTo("RDV-2026-0007");
            assertThat(enregistree.estNonLue()).isTrue();
            assertThat(enregistree.getDateEnvoi()).isEqualTo(MAINTENANT);
        }
    }

    @Nested
    @DisplayName("Lecture")
    class Lecture {

        @Test
        @DisplayName("rend le texte compose depuis le type et l argument, pas depuis la base")
        void rendDansLaLangueDuLecteur() {
            when(notifications.findByMembreOrderByDateEnvoiDescIdDesc(marie))
                    .thenReturn(List.of(notificationDe(marie)));

            var vues = service.mesNotifications(MARIE);

            assertThat(vues).hasSize(1);
            assertThat(vues.getFirst().titre()).isEqualTo("Rendez-vous confirmé");
            assertThat(vues.getFirst().corps())
                    .as("l argument metier est injecte dans le message")
                    .isEqualTo("Le garage a confirmé votre rendez-vous RDV-2026-0007.");
            assertThat(vues.getFirst().lue()).isFalse();
        }

        @Test
        @DisplayName("compte les non lues du membre")
        void compteur() {
            when(notifications.countByMembreAndStatut(marie, StatutNotification.NON_LUE))
                    .thenReturn(3L);

            assertThat(service.nombreNonLues(MARIE)).isEqualTo(3L);
        }

        @Test
        @DisplayName("rend zero si le compte est inconnu, sans lever")
        void compteurCompteInconnu() {
            when(membres.findByEmailIgnoreCase("fantome@exemple.be")).thenReturn(Optional.empty());

            assertThat(service.nombreNonLues("fantome@exemple.be")).isZero();
        }
    }

    @Nested
    @DisplayName("Ownership")
    class Ownership {

        @Test
        @DisplayName("marque la notification du titulaire")
        void marquageDuTitulaire() {
            Notification sienne = notificationDe(marie);
            when(notifications.findByIdAndMembre(7L, marie)).thenReturn(Optional.of(sienne));

            service.marquerLue(MARIE, 7L);

            assertThat(sienne.estNonLue()).isFalse();
            assertThat(sienne.getDateLecture()).isEqualTo(MAINTENANT);
        }

        @Test
        @DisplayName("la notification d autrui remonte en 404, pas en 403")
        void notificationDAutrui() {
            // Le repository filtre sur le couple (id, membre) : pour Paul, la
            // notification de Marie n'existe simplement pas.
            when(notifications.findByIdAndMembre(7L, paul)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.marquerLue(PAUL, 7L))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        @Test
        @DisplayName("un identifiant inconnu remonte le meme 404")
        void identifiantInconnu() {
            when(notifications.findByIdAndMembre(999L, marie)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.marquerLue(MARIE, 999L))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        @Test
        @DisplayName("le marquage global ne touche que les non lues du membre")
        void marquageGlobal() {
            Notification une = notificationDe(marie);
            Notification deux = notificationDe(marie);
            when(notifications.findByMembreAndStatut(marie, StatutNotification.NON_LUE))
                    .thenReturn(List.of(une, deux));

            service.marquerToutesLues(MARIE);

            assertThat(une.estNonLue()).isFalse();
            assertThat(deux.estNonLue()).isFalse();
            verify(notifications, never()).findByMembreAndStatut(paul, StatutNotification.NON_LUE);
            verify(notifications, never()).save(any());
        }
    }
}
