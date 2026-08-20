package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private PasswordEncoder encodeur;
    private MotDePasseService service;

    @BeforeEach
    void preparer() {
        encodeur = new BCryptPasswordEncoder(4);
        Clock horlogeFigee = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        service = new MotDePasseService(repository, encodeur, courriel, horlogeFigee);
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

            verify(courriel).envoyerReinitialisationMotDePasse(eq(membre), anyString());
            verify(courriel, never()).envoyerRappelVerification(any(), anyString());
            assertThat(membre.getJetonVerification()).hasSize(64);
            assertThat(membre.getJetonExpiration())
                    .isEqualTo(MAINTENANT.plus(MotDePasseService.VALIDITE_JETON));
        }

        @Test
        @DisplayName("envoie un rappel de verification pour un compte jamais active")
        void envoieUnRappelPourUnCompteNonActive() {
            Utilisateur membre = membreEnAttente();
            when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

            service.demanderReinitialisation("marie@exemple.be");

            verify(courriel).envoyerRappelVerification(eq(membre), anyString());
            verify(courriel, never()).envoyerReinitialisationMotDePasse(any(), anyString());
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
        @DisplayName("remplace l empreinte et active le compte")
        void remplaceEtActive() {
            Utilisateur membre = membreEnAttente();
            membre.enregistrerJetonVerification("jeton-valide", MAINTENANT.plusSeconds(600));
            when(repository.findByJetonVerification("jeton-valide")).thenReturn(Optional.of(membre));

            service.reinitialiser("jeton-valide", "NouveauMotDePasse2026");

            assertThat(encodeur.matches("NouveauMotDePasse2026", membre.getMotDePasseHache())).isTrue();
            assertThat(membre.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            assertThat(membre.isEmailVerifie()).isTrue();
            assertThat(membre.getJetonVerification()).isNull();
        }

        @Test
        @DisplayName("leve le verrouillage du compte")
        void leveLeVerrouillage() {
            Utilisateur membre = membreActif();
            membre.enregistrerEchecConnexion(5, MAINTENANT.plusSeconds(900));
            membre.verrouillerJusqu(MAINTENANT.plusSeconds(900));
            membre.enregistrerJetonVerification("jeton-valide", MAINTENANT.plusSeconds(600));
            when(repository.findByJetonVerification("jeton-valide")).thenReturn(Optional.of(membre));

            service.reinitialiser("jeton-valide", "NouveauMotDePasse2026");

            assertThat(membre.estVerrouille(MAINTENANT)).isFalse();
            assertThat(membre.getTentativesEchouees()).isZero();
        }

        @Test
        @DisplayName("refuse un jeton expire")
        void refuseUnJetonExpire() {
            Utilisateur membre = membreActif();
            membre.enregistrerJetonVerification("jeton-perime", MAINTENANT.minusSeconds(1));
            when(repository.findByJetonVerification("jeton-perime")).thenReturn(Optional.of(membre));

            assertThatThrownBy(() -> service.reinitialiser("jeton-perime", "NouveauMotDePasse2026"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("RM-05");
        }

        @Test
        @DisplayName("refuse un jeton inconnu")
        void refuseUnJetonInconnu() {
            when(repository.findByJetonVerification("inexistant")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reinitialiser("inexistant", "NouveauMotDePasse2026"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }

        @Test
        @DisplayName("refuse un mot de passe trop court")
        void refuseUnMotDePasseTropCourt() {
            Utilisateur membre = membreActif();
            membre.enregistrerJetonVerification("jeton-valide", MAINTENANT.plusSeconds(600));
            when(repository.findByJetonVerification("jeton-valide")).thenReturn(Optional.of(membre));

            assertThatThrownBy(() -> service.reinitialiser("jeton-valide", "court"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("RM-02");
        }

        @Test
        @DisplayName("refuse un mot de passe nul")
        void refuseUnMotDePasseNul() {
            Utilisateur membre = membreActif();
            membre.enregistrerJetonVerification("jeton-valide", MAINTENANT.plusSeconds(600));
            when(repository.findByJetonVerification("jeton-valide")).thenReturn(Optional.of(membre));

            assertThatThrownBy(() -> service.reinitialiser("jeton-valide", null))
                    .isInstanceOf(RegleMetierException.class)
                    .hasMessageContaining("RM-02");
        }
    }

    @Nested
    @DisplayName("verification prealable du jeton")
    class Verification {

        @Test
        @DisplayName("accepte un jeton valide")
        void accepteUnJetonValide() {
            Utilisateur membre = membreActif();
            membre.enregistrerJetonVerification("jeton-valide", MAINTENANT.plusSeconds(600));
            when(repository.findByJetonVerification("jeton-valide")).thenReturn(Optional.of(membre));

            assertThatCode(() -> service.verifierJeton("jeton-valide")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejette un jeton expire")
        void rejetteUnJetonExpire() {
            Utilisateur membre = membreActif();
            membre.enregistrerJetonVerification("jeton-perime", MAINTENANT.minusSeconds(1));
            when(repository.findByJetonVerification("jeton-perime")).thenReturn(Optional.of(membre));

            assertThatThrownBy(() -> service.verifierJeton("jeton-perime"))
                    .isInstanceOf(RegleMetierException.class);
        }

        @Test
        @DisplayName("rejette un jeton inconnu")
        void rejetteUnJetonInconnu() {
            when(repository.findByJetonVerification("inexistant")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifierJeton("inexistant"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
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