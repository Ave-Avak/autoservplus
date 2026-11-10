package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.LiensCourriel;
import be.autoservplus.communication.service.OrigineMotDePasse;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires de la reinitialisation de mot de passe.
 *
 * <p>Verifie notamment que le service ne revele jamais l existence d un compte et que le
 * courriel emis s adapte a l etat reel du compte.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MotDePasseService")
class MotDePasseServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-20T10:00:00Z");

    @Mock
    private UtilisateurRepository repository;

    @Mock
    private ServiceCourriel courriel;

    @Mock
    private RevocationSessions sessions;

    @Mock
    private JournalSecuriteCompte journal;

    private PasswordEncoder encodeur;
    private MotDePasseService service;

    @BeforeEach
    void preparer() {
        encodeur = new BCryptPasswordEncoder(4);
        Clock horlogeFigee = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        service = new MotDePasseService(repository, encodeur, courriel, horlogeFigee,
                new PolitiqueMotDePasse(new MotsDePasseCourants(), motDePasse -> false),
                new LiensCourriel("http://localhost:8080/"), sessions, journal);
    }

    @Nested
    @DisplayName("demande de reinitialisation")
    class Demande {

        @Test
        @DisplayName("envoie un lien de reinitialisation pour un compte actif")
        void envoieUnLienPourUnCompteActif() {
            Utilisateur membre = membreActif();
            when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

            service.demanderReinitialisation("marie@exemple.be");

            ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
            verify(courriel).envoyerReinitialisationMotDePasse(eq(membre), lien.capture());
            verify(courriel, never()).envoyerRappelVerification(any(), anyString());
            assertThat(lien.getValue()).startsWith("http://localhost:8080/mot-de-passe/nouveau?jeton=");

            // Seule l empreinte du jeton du lien est en base, dans ses propres colonnes.
            String clair = lien.getValue().substring(lien.getValue().indexOf('=') + 1);
            assertThat(membre.getJetonReinitialisationHache())
                    .isEqualTo(JetonLien.empreinte(clair))
                    .isNotEqualTo(clair);
            assertThat(membre.getJetonReinitialisationExpiration())
                    .isEqualTo(MAINTENANT.plus(MotDePasseService.VALIDITE_JETON));
            assertThat(membre.getJetonVerificationHache()).isNull();
        }

        @Test
        @DisplayName("envoie un rappel de verification pour un compte jamais active")
        void envoieUnRappelPourUnCompteNonActive() {
            Utilisateur membre = membreEnAttente();
            when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

            service.demanderReinitialisation("marie@exemple.be");

            verify(courriel).envoyerRappelVerification(eq(membre), anyString());
            verify(courriel, never()).envoyerReinitialisationMotDePasse(any(), anyString());
            // Un jeton de verification, de la duree ordinaire : il ne pose aucun mot de passe.
            assertThat(membre.getJetonReinitialisationHache()).isNull();
            assertThat(membre.getJetonExpiration())
                    .isEqualTo(MAINTENANT.plus(InscriptionService.VALIDITE_JETON));
        }

        @Test
        @DisplayName("reste silencieux sur une adresse inconnue")
        void resteSilencieuxSurUneAdresseInconnue() {
            when(repository.findByEmailIgnoreCase("inconnu@exemple.be")).thenReturn(Optional.empty());

            assertThatCode(() -> service.demanderReinitialisation("inconnu@exemple.be"))
                    .doesNotThrowAnyException();

            verifyNoInteractions(courriel);
        }

        @Test
        @DisplayName("ignore une adresse vide sans consulter la base")
        void ignoreUneAdresseVide() {
            service.demanderReinitialisation("   ");

            verifyNoInteractions(repository, courriel);
        }

        @Test
        @DisplayName("ignore une adresse nulle sans consulter la base")
        void ignoreUneAdresseNulle() {
            service.demanderReinitialisation(null);

            verifyNoInteractions(repository, courriel);
        }

        @Test
        @DisplayName("normalise l adresse avant la recherche")
        void normaliseLAdresse() {
            Utilisateur membre = membreActif();
            when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

            service.demanderReinitialisation("  Marie@Exemple.BE  ");

            verify(repository).findByEmailIgnoreCase("marie@exemple.be");
        }
    }

    @Nested
    @DisplayName("application du nouveau mot de passe")
    class Application {

        @Test
        @DisplayName("remplace l empreinte, consomme le jeton, ferme les sessions et avertit")
        void remplaceEtAvertit() {
            Utilisateur membre = membreActif();
            armer(membre, "jeton-valide", MAINTENANT.plusSeconds(600));

            service.reinitialiser("jeton-valide", "NouveauMotDePasse2026");

            assertThat(encodeur.matches("NouveauMotDePasse2026", membre.getMotDePasseHache())).isTrue();
            assertThat(membre.getJetonReinitialisationHache()).isNull();
            assertThat(membre.getJetonReinitialisationExpiration()).isNull();
            verify(journal).consigner(membre, TypeEvenementSecurite.MOT_DE_PASSE_REINITIALISE, membre);
            verify(sessions).revoquer("marie@exemple.be");
            verify(courriel).envoyerAvisMotDePasseModifie(membre, OrigineMotDePasse.REINITIALISATION);
        }

        @Test
        @DisplayName("un jeton de verification n ouvre pas la reinitialisation")
        void jetonDeVerificationRefuse() {
            Utilisateur membre = membreEnAttente();
            membre.enregistrerJetonVerification(JetonLien.empreinte("jeton-verif"),
                    MAINTENANT.plusSeconds(600));
            when(repository.findByJetonReinitialisationHache(JetonLien.empreinte("jeton-verif")))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reinitialiser("jeton-verif", "NouveauMotDePasse2026"))
                    .isInstanceOf(RessourceIntrouvableException.class);
            verifyNoInteractions(sessions, journal);
        }

        @Test
        @DisplayName("leve le verrouillage du compte")
        void leveLeVerrouillage() {
            Utilisateur membre = membreActif();
            membre.enregistrerEchecConnexion(5, MAINTENANT.plusSeconds(900));
            membre.verrouillerJusqu(MAINTENANT.plusSeconds(900));
            armer(membre, "jeton-valide", MAINTENANT.plusSeconds(600));

            service.reinitialiser("jeton-valide", "NouveauMotDePasse2026");

            assertThat(membre.estVerrouille(MAINTENANT)).isFalse();
            assertThat(membre.getTentativesEchouees()).isZero();
        }

        @Test
        @DisplayName("refuse un jeton expire")
        void refuseUnJetonExpire() {
            Utilisateur membre = membreActif();
            armer(membre, "jeton-perime", MAINTENANT.minusSeconds(1));

            assertThatThrownBy(() -> service.reinitialiser("jeton-perime", "NouveauMotDePasse2026"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-05");
        }

        @Test
        @DisplayName("refuse un jeton inconnu")
        void refuseUnJetonInconnu() {
            when(repository.findByJetonReinitialisationHache(JetonLien.empreinte("inexistant")))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reinitialiser("inexistant", "NouveauMotDePasse2026"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        @Test
        @DisplayName("refuse, appelé directement, un mot de passe de quatorze caractères")
        void refuseUnMotDePasseTropCourt() {
            Utilisateur membre = membreActif();
            armer(membre, "jeton-valide", MAINTENANT.plusSeconds(600));

            // Quatorze : l ancien plancher de douze l acceptait, la politique le refuse.
            assertThatThrownBy(() -> service.reinitialiser("jeton-valide", "atelier garage"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-02");
        }

        @Test
        @DisplayName("refuse un mot de passe nul")
        void refuseUnMotDePasseNul() {
            Utilisateur membre = membreActif();
            armer(membre, "jeton-valide", MAINTENANT.plusSeconds(600));

            assertThatThrownBy(() -> service.reinitialiser("jeton-valide", null))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-02");
        }
    }

    @Nested
    @DisplayName("verification prealable du jeton")
    class Verification {

        @Test
        @DisplayName("accepte un jeton valide")
        void accepteUnJetonValide() {
            Utilisateur membre = membreActif();
            armer(membre, "jeton-valide", MAINTENANT.plusSeconds(600));

            assertThatCode(() -> service.verifierJeton("jeton-valide")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejette un jeton expire")
        void rejetteUnJetonExpire() {
            Utilisateur membre = membreActif();
            armer(membre, "jeton-perime", MAINTENANT.minusSeconds(1));

            assertThatThrownBy(() -> service.verifierJeton("jeton-perime"))
                    .isInstanceOf(RegleMetierException.class);
        }

        @Test
        @DisplayName("rejette un jeton inconnu")
        void rejetteUnJetonInconnu() {
            when(repository.findByJetonReinitialisationHache(JetonLien.empreinte("inexistant")))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifierJeton("inexistant"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    /** Arme un jeton de reinitialisation, retrouve par son empreinte comme en base. */
    private void armer(Utilisateur membre, String clair, Instant expiration) {
        membre.preparerReinitialisation(JetonLien.empreinte(clair), expiration);
        when(repository.findByJetonReinitialisationHache(JetonLien.empreinte(clair)))
                .thenReturn(Optional.of(membre));
    }

    private Utilisateur membreEnAttente() {
        return new Utilisateur("marie@exemple.be", "$2a$04$ancienne",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    }

    private Utilisateur membreActif() {
        Utilisateur membre = membreEnAttente();
        membre.confirmerAdresseEmail();
        return membre;
    }
}