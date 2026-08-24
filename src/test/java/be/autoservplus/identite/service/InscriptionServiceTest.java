package be.autoservplus.identite.service;

import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.StatutUtilisateur;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Tests unitaires du service d inscription.
 *
 * <p>Le repository est simule : aucun acces a la base de donnees. L horloge est figee,
 * ce qui permet de verifier l expiration des jetons sans attendre.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InscriptionService")
class InscriptionServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-20T10:00:00Z");

    @Mock
    private UtilisateurRepository repository;
    @Mock
    private ServiceCourriel courriel;
    private PasswordEncoder encodeur;
    private InscriptionService service;

    @BeforeEach
    void preparer() {
        encodeur = new BCryptPasswordEncoder(4); // cout reduit : les tests doivent rester rapides
        Clock horlogeFigee = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        service = new InscriptionService(repository, encodeur, horlogeFigee, courriel);
    }

    @Nested
    @DisplayName("inscription")
    class Inscription {

        @Test
        @DisplayName("cree un membre en attente de validation")
        void creeUnMembreEnAttente() {
            when(repository.existsByEmailIgnoreCase("marie@exemple.be")).thenReturn(false);
            when(repository.save(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire(
                    "marie@exemple.be", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr);

            assertThat(membre.getEmail()).isEqualTo("marie@exemple.be");
            assertThat(membre.getNom()).isEqualTo("Dupont");
            assertThat(membre.getPrenom()).isEqualTo("Marie");
            assertThat(membre.getTypeUtilisateur()).isEqualTo(TypeUtilisateur.MEMBRE);
            assertThat(membre.getStatut()).isEqualTo(StatutUtilisateur.EN_ATTENTE_VALIDATION);
            assertThat(membre.isEmailVerifie()).isFalse();
            assertThat(membre.getReference()).isNotNull();
        }

        @Test
        @DisplayName("normalise l adresse en minuscules et retire les espaces")
        void normaliseLAdresse() {
            when(repository.existsByEmailIgnoreCase("marie@exemple.be")).thenReturn(false);
            when(repository.save(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire(
                    "  Marie@Exemple.BE  ", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr);

            assertThat(membre.getEmail()).isEqualTo("marie@exemple.be");
        }

        @Test
        @DisplayName("n enregistre jamais le mot de passe en clair")
        void chiffreLeMotDePasse() {
            when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
            when(repository.save(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            String motDePasseClair = "MotDePasseSolide2026";
            Utilisateur membre = service.inscrire(
                    "marie@exemple.be", motDePasseClair, "Dupont", "Marie", Langue.fr);

            assertThat(membre.getMotDePasseHache()).isNotEqualTo(motDePasseClair);
            assertThat(membre.getMotDePasseHache()).startsWith("$2a$");
            assertThat(encodeur.matches(motDePasseClair, membre.getMotDePasseHache())).isTrue();
        }

        @Test
        @DisplayName("genere un jeton de 64 caracteres valable vingt-quatre heures")
        void genereUnJetonValable24h() {
            when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
            when(repository.save(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire(
                    "marie@exemple.be", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr);

            assertThat(membre.getJetonVerification()).hasSize(64);
            assertThat(membre.getJetonExpiration())
                    .isEqualTo(MAINTENANT.plus(InscriptionService.VALIDITE_JETON));
        }

        @Test
        @DisplayName("refuse une adresse deja utilisee")
        void refuseUneAdresseDejaUtilisee() {
            when(repository.existsByEmailIgnoreCase("marie@exemple.be")).thenReturn(true);

            assertThatThrownBy(() -> service.inscrire(
                    "marie@exemple.be", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-01");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("refuse un mot de passe de moins de douze caracteres")
        void refuseUnMotDePasseTropCourt() {
            when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);

            assertThatThrownBy(() -> service.inscrire(
                    "marie@exemple.be", "court", "Dupont", "Marie", Langue.fr))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-02");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("refuse une adresse vide")
        void refuseUneAdresseVide() {
            assertThatThrownBy(() -> service.inscrire(
                    "   ", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-01");
        }

        @Test
        @DisplayName("applique le francais lorsque la langue n est pas precisee")
        void appliqueLeFrancaisParDefaut() {
            when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
            when(repository.save(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire(
                    "marie@exemple.be", "MotDePasseSolide2026", "Dupont", "Marie", null);

            assertThat(membre.getLangue()).isEqualTo(Langue.fr);
        }
        @Test
        @DisplayName("refuse une adresse nulle")
        void refuseUneAdresseNulle() {
            assertThatThrownBy(() -> service.inscrire(
                    null, "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-01");
        }
        @Test
        @DisplayName("refuse un mot de passe nul")
        void refuseUnMotDePasseNul() {
            when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);

            assertThatThrownBy(() -> service.inscrire(
                    "marie@exemple.be", null, "Dupont", "Marie", Langue.fr))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-02");
        }
        @Test
        @DisplayName("envoie le courriel de verification apres enregistrement")
        void envoieLeCourrielDeVerification() {
            when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
            when(repository.save(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire(
                    "marie@exemple.be", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr);

            verify(courriel).envoyerVerificationAdresse(
                    eq(membre), contains(membre.getJetonVerification()));
        }
    }

    @Nested
    @DisplayName("confirmation d adresse")
    class Confirmation {

        @Test
        @DisplayName("active le compte et efface le jeton")
        void activeLeCompte() {
            Utilisateur membre = membreEnAttente();
            membre.enregistrerJetonVerification("jeton-valide", MAINTENANT.plusSeconds(3600));
            when(repository.findByJetonVerification("jeton-valide")).thenReturn(Optional.of(membre));

            Utilisateur resultat = service.confirmerAdresse("jeton-valide");

            assertThat(resultat.isEmailVerifie()).isTrue();
            assertThat(resultat.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            assertThat(resultat.getJetonVerification()).isNull();
            assertThat(resultat.getJetonExpiration()).isNull();
        }

        @Test
        @DisplayName("refuse un jeton expire")
        void refuseUnJetonExpire() {
            Utilisateur membre = membreEnAttente();
            membre.enregistrerJetonVerification("jeton-perime", MAINTENANT.minusSeconds(1));
            when(repository.findByJetonVerification("jeton-perime")).thenReturn(Optional.of(membre));

            assertThatThrownBy(() -> service.confirmerAdresse("jeton-perime"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-03");

            assertThat(membre.isEmailVerifie()).isFalse();
        }

        @Test
        @DisplayName("refuse un jeton inconnu")
        void refuseUnJetonInconnu() {
            when(repository.findByJetonVerification("inexistant")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmerAdresse("inexistant"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }

    @Nested
    @DisplayName("renvoi de la verification")
    class Renvoi {

        @Test
        @DisplayName("genere un nouveau jeton")
        void genereUnNouveauJeton() {
            Utilisateur membre = membreEnAttente();
            membre.enregistrerJetonVerification("ancien", MAINTENANT.minusSeconds(1));
            when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

            Utilisateur resultat = service.renvoyerVerification("marie@exemple.be");

            assertThat(resultat.getJetonVerification()).isNotEqualTo("ancien").hasSize(64);
            assertThat(resultat.getJetonExpiration())
                    .isEqualTo(MAINTENANT.plus(InscriptionService.VALIDITE_JETON));
        }

        @Test
        @DisplayName("refuse une adresse deja verifiee")
        void refuseUneAdresseDejaVerifiee() {
            Utilisateur membre = membreEnAttente();
            membre.confirmerAdresseEmail();
            when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

            assertThatThrownBy(() -> service.renvoyerVerification("marie@exemple.be"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-04");
        }
        @Test
        @DisplayName("refuse une adresse inconnue")
        void refuseUneAdresseInconnue() {
            when(repository.findByEmailIgnoreCase("inconnu@exemple.be")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.renvoyerVerification("inconnu@exemple.be"))
                    .isInstanceOf(RessourceIntrouvableException.class)
                    .hasMessageContaining("inconnu@exemple.be");
        }
    }

    @Nested
    @DisplayName("Demande publique de renvoi (point d entree neutre)")
    class DemandeDeRenvoi {

        /**
         * Le coeur de la garantie : les trois situations doivent etre indiscernables du
         * dehors. Verifie ici sur le service, et de nouveau sur la reponse HTTP complete
         * par RenvoiVerificationIT — un ecran peut trahir ce que le service tait.
         */
        @Test
        @DisplayName("ne leve rien sur une adresse inconnue et n envoie aucun courriel")
        void adresseInconnueResteMuette() {
            when(repository.findByEmailIgnoreCase("inconnu@exemple.be")).thenReturn(Optional.empty());

            service.demanderRenvoiVerification("inconnu@exemple.be");

            verifyNoInteractions(courriel);
        }

        @Test
        @DisplayName("ne leve rien sur une adresse deja verifiee et n envoie aucun courriel")
        void adresseDejaVerifieeResteMuette() {
            Utilisateur membre = membreEnAttente();
            membre.confirmerAdresseEmail();
            when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

            service.demanderRenvoiVerification("marie@exemple.be");

            verifyNoInteractions(courriel);
        }

        /**
         * L adresse vide passe par normaliser(), qui leve RM-01. Ce chemin doit lui
         * aussi rester muet : une soumission a blanc ne doit pas se distinguer d une
         * soumission valide.
         */
        @Test
        @DisplayName("ne leve rien sur une adresse vide")
        void adresseVideResteMuette() {
            service.demanderRenvoiVerification("   ");

            verifyNoInteractions(courriel);
        }

        @Test
        @DisplayName("renvoie effectivement le courriel pour un compte non verifie")
        void compteNonVerifieRecoitSonCourriel() {
            Utilisateur membre = membreEnAttente();
            membre.enregistrerJetonVerification("ancien", MAINTENANT.plusSeconds(60));
            when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

            service.demanderRenvoiVerification("marie@exemple.be");

            ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
            verify(courriel).envoyerVerificationAdresse(eq(membre), lien.capture());
            assertThat(lien.getValue())
                    .startsWith("/inscription/verification?jeton=")
                    .doesNotContain("ancien");
        }
    }

    private Utilisateur membreEnAttente() {
        return new Utilisateur("marie@exemple.be", "$2a$04$empreinte",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    }
}
