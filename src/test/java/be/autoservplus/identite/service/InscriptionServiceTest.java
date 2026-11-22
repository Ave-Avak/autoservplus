package be.autoservplus.identite.service;

import be.autoservplus.communication.service.LiensCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.VersionsDocumentsService;
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
    @Mock
    private MotDePasseService motsDePasse;
    @Mock
    private ConsentementRepository consentements;
    @Mock
    private VersionsDocumentsService versionsDocuments;
    private PasswordEncoder encodeur;
    private InscriptionService service;

    @BeforeEach
    void preparer() {
        encodeur = spy(new BCryptPasswordEncoder(4)); // cout reduit : les tests doivent rester rapides
        Clock horlogeFigee = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        service = new InscriptionService(repository, encodeur, horlogeFigee, courriel,
                new PolitiqueMotDePasse(new MotsDePasseCourants(), motDePasse -> false),
                new LiensCourriel("http://localhost:8080"), motsDePasse,
                new LimiteurDemandesCourriel(horlogeFigee, 5, 20, 15),
                consentements, versionsDocuments);
        lenient().when(versionsDocuments.versionCourante(TypeDocumentVersionne.CGV))
                .thenReturn("CGV-2026-03");
    }

    @Nested
    @DisplayName("inscription")
    class Inscription {

        @Test
        @DisplayName("cree un membre en attente de validation")
        void creeUnMembreEnAttente() {
            when(repository.findByEmail("marie@exemple.be")).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire(
                    "marie@exemple.be", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr, true, "203.0.113.1").orElseThrow();

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
            when(repository.findByEmail("marie@exemple.be")).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire(
                    "  Marie@Exemple.BE  ", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr, true, "203.0.113.1").orElseThrow();

            assertThat(membre.getEmail()).isEqualTo("marie@exemple.be");
        }

        @Test
        @DisplayName("n enregistre jamais le mot de passe en clair")
        void chiffreLeMotDePasse() {
            when(repository.findByEmail(any())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            String motDePasseClair = "MotDePasseSolide2026";
            Utilisateur membre = service.inscrire(
                    "marie@exemple.be", motDePasseClair, "Dupont", "Marie", Langue.fr, true, "203.0.113.1").orElseThrow();

            assertThat(membre.getMotDePasseHache()).isNotEqualTo(motDePasseClair);
            assertThat(membre.getMotDePasseHache()).startsWith("$2a$");
            assertThat(encodeur.matches(motDePasseClair, membre.getMotDePasseHache())).isTrue();
        }

        @Test
        @DisplayName("genere un jeton de 64 caracteres valable vingt-quatre heures")
        void genereUnJetonValable24h() {
            when(repository.findByEmail(any())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire(
                    "marie@exemple.be", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr, true, "203.0.113.1").orElseThrow();

            assertThat(membre.getJetonVerificationHache()).hasSize(64);
            assertThat(membre.getJetonExpiration())
                    .isEqualTo(MAINTENANT.plus(InscriptionService.VALIDITE_JETON));
        }

        private Utilisateur titulaire(StatutUtilisateur statut) {
            Utilisateur existant = new Utilisateur("marie@exemple.be", "$2a$04$empreinte",
                    "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            existant.setStatut(statut);
            when(repository.findByEmail("marie@exemple.be")).thenReturn(Optional.of(existant));
            return existant;
        }

        private Optional<Utilisateur> inscrireMarie() {
            return service.inscrire("marie@exemple.be", "MotDePasseSolide2026", "Dupont",
                    "Marie", Langue.fr, true, "203.0.113.1");
        }

        @Test
        @DisplayName("adresse d'un compte actif : rien n'est créé, le titulaire est averti, sans exception")
        void adresseDUnCompteActif() {
            Utilisateur existant = titulaire(StatutUtilisateur.ACTIF);

            assertThat(inscrireMarie()).isEmpty();

            verify(repository, never()).saveAndFlush(any());
            verify(courriel).envoyerAvisTentativeInscription(existant);
            verifyNoInteractions(motsDePasse);
        }

        @Test
        @DisplayName("adresse d'un compte suspendu : même avertissement")
        void adresseDUnCompteSuspendu() {
            Utilisateur existant = titulaire(StatutUtilisateur.SUSPENDU);

            assertThat(inscrireMarie()).isEmpty();

            verify(courriel).envoyerAvisTentativeInscription(existant);
        }

        @Test
        @DisplayName("adresse d'un compte en attente : le courriel de « Mot de passe oublié », nouveau lien d'activation")
        void adresseDUnCompteEnAttente() {
            Utilisateur existant = titulaire(StatutUtilisateur.EN_ATTENTE_VALIDATION);

            assertThat(inscrireMarie()).isEmpty();

            verify(motsDePasse).envoyerCourrielAdapte(existant);
            verifyNoInteractions(courriel);
        }

        @Test
        @DisplayName("adresse d'un compte anonymisé : rien n'est envoyé")
        void adresseDUnCompteAnonymise() {
            titulaire(StatutUtilisateur.SUPPRIME);

            assertThat(inscrireMarie()).isEmpty();

            verifyNoInteractions(courriel, motsDePasse);
        }

        @Test
        @DisplayName("adresse connue : le mot de passe passe la politique et un hachage, comme pour une adresse libre")
        void memeTravailQuUneAdresseLibre() {
            titulaire(StatutUtilisateur.ACTIF);

            inscrireMarie();

            verify(encodeur).encode("MotDePasseSolide2026");
            assertThatThrownBy(() -> service.inscrire("marie@exemple.be", "atelier garage",
                    "Dupont", "Marie", Langue.fr, true, "203.0.113.1"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-02");
        }

        @Test
        @DisplayName("au-delà du plafond de l'adresse, le titulaire n'est plus averti, la réponse reste la même")
        void plafondDesAvertissements() {
            titulaire(StatutUtilisateur.ACTIF);

            for (int i = 0; i < 6; i++) {
                assertThat(inscrireMarie()).isEmpty();
            }

            verify(courriel, times(5)).envoyerAvisTentativeInscription(any());
        }

        @Test
        @DisplayName("refuse, appelé directement, un mot de passe de quatorze caractères")
        void refuseUnMotDePasseTropCourt() {
            // Quatorze : l ancien plancher de douze l acceptait, la politique le refuse.
            assertThatThrownBy(() -> service.inscrire(
                    "marie@exemple.be", "atelier garage", "Dupont", "Marie", Langue.fr, true, "203.0.113.1"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-02");

            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("la contrainte de casse de la base devient un refus sur le champ, sans courriel")
        void contrainteDeCasse() {
            when(repository.findByEmail(any())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(Utilisateur.class)))
                    .thenThrow(AdresseCourrielTest.violation("ck_utilisateur_email_minuscules"));

            assertThatThrownBy(() -> service.inscrire(
                    "marie@exemple.be", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr, true, "203.0.113.1"))
                    .isInstanceOf(RefusTraduit.class)
                    .hasFieldOrPropertyWithValue("cleMessage", AdresseCourriel.CLE_REFUS_CASSE);
            verifyNoInteractions(courriel);
        }

        @Test
        @DisplayName("refuse une adresse vide")
        void refuseUneAdresseVide() {
            assertThatThrownBy(() -> service.inscrire(
                    "   ", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr, true, "203.0.113.1"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-01");
        }

        @Test
        @DisplayName("applique le francais lorsque la langue n est pas precisee")
        void appliqueLeFrancaisParDefaut() {
            when(repository.findByEmail(any())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire(
                    "marie@exemple.be", "MotDePasseSolide2026", "Dupont", "Marie", null, true, "203.0.113.1").orElseThrow();

            assertThat(membre.getLangue()).isEqualTo(Langue.fr);
        }
        @Test
        @DisplayName("refuse une adresse nulle")
        void refuseUneAdresseNulle() {
            assertThatThrownBy(() -> service.inscrire(
                    null, "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr, true, "203.0.113.1"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-01");
        }
        @Test
        @DisplayName("refuse un mot de passe nul")
        void refuseUnMotDePasseNul() {
            assertThatThrownBy(() -> service.inscrire(
                    "marie@exemple.be", null, "Dupont", "Marie", Langue.fr, true, "203.0.113.1"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-02");
        }
        @Test
        @DisplayName("envoie le courriel de verification apres enregistrement")
        void envoieLeCourrielDeVerification() {
            when(repository.findByEmail(any())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire(
                    "marie@exemple.be", "MotDePasseSolide2026", "Dupont", "Marie", Langue.fr, true, "203.0.113.1").orElseThrow();

            // Le lien porte le jeton en clair, la base sa seule empreinte.
            ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
            verify(courriel).envoyerVerificationAdresse(eq(membre), lien.capture());
            assertThat(lien.getValue()).startsWith("http://localhost:8080/inscription/verification?jeton=");
            String clair = lien.getValue().substring(lien.getValue().indexOf('=') + 1);
            assertThat(membre.getJetonVerificationHache())
                    .isEqualTo(JetonLien.empreinte(clair))
                    .isNotEqualTo(clair);
        }
    }

    @Nested
    @DisplayName("acceptation des CGV (F1)")
    class AcceptationCgv {

        @Test
        @DisplayName("sans la case, refus avant toute recherche de l'adresse, rien n'est écrit")
        void refusSansCgv() {
            assertThatThrownBy(() -> service.inscrire("marie@exemple.be", "MotDePasseSolide2026",
                    "Dupont", "Marie", Langue.fr, false, "203.0.113.1"))
                    .isInstanceOf(RefusTraduit.class)
                    .hasFieldOrPropertyWithValue("cleMessage", InscriptionService.CLE_CGV_OBLIGATOIRES);

            verifyNoInteractions(repository, consentements, courriel, motsDePasse);
            verify(encodeur, never()).encode(any());
        }

        @Test
        @DisplayName("compte créé : preuve de la version en vigueur, avec l'adresse IP et l'instant, sans commande")
        void preuveEcrite() {
            when(repository.findByEmail(any())).thenReturn(Optional.empty());
            when(repository.saveAndFlush(any(Utilisateur.class))).thenAnswer(i -> i.getArgument(0));

            Utilisateur membre = service.inscrire("marie@exemple.be", "MotDePasseSolide2026",
                    "Dupont", "Marie", Langue.fr, true, "203.0.113.9").orElseThrow();

            ArgumentCaptor<Consentement> preuve = ArgumentCaptor.forClass(Consentement.class);
            verify(consentements).save(preuve.capture());
            assertThat(preuve.getValue().getUtilisateur()).isSameAs(membre);
            assertThat(preuve.getValue().getTypeDocument()).isEqualTo(TypeDocumentConsentement.CGV);
            assertThat(preuve.getValue().getVersionAcceptee()).isEqualTo("CGV-2026-03");
            assertThat(preuve.getValue().isAccorde()).isTrue();
            assertThat(preuve.getValue().getAdresseIp()).isEqualTo("203.0.113.9");
            assertThat(preuve.getValue().getDateConsentement()).isEqualTo(MAINTENANT);
            assertThat(preuve.getValue().getCommandeId()).isNull();
        }

        @Test
        @DisplayName("adresse qui a déjà un compte : aucune preuve, rien n'est créé")
        void aucunePreuvePourUneAdresseConnue() {
            Utilisateur existant = new Utilisateur("marie@exemple.be", "$2a$04$empreinte",
                    "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            existant.setStatut(StatutUtilisateur.ACTIF);
            when(repository.findByEmail("marie@exemple.be")).thenReturn(Optional.of(existant));

            assertThat(service.inscrire("marie@exemple.be", "MotDePasseSolide2026",
                    "Dupont", "Marie", Langue.fr, true, "203.0.113.1")).isEmpty();

            verifyNoInteractions(consentements);
        }
    }

    @Nested
    @DisplayName("confirmation d adresse")
    class Confirmation {

        @Test
        @DisplayName("active le compte et efface le jeton")
        void activeLeCompte() {
            Utilisateur membre = membreEnAttente();
            membre.enregistrerJetonVerification(JetonLien.empreinte("jeton-valide"), MAINTENANT.plusSeconds(3600));
            when(repository.findByJetonVerificationHache(JetonLien.empreinte("jeton-valide")))
                    .thenReturn(Optional.of(membre));

            Utilisateur resultat = service.confirmerAdresse("jeton-valide");

            assertThat(resultat.isEmailVerifie()).isTrue();
            assertThat(resultat.getEmailVerifieLe()).as("la vérification est datée").isEqualTo(MAINTENANT);
            assertThat(resultat.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            assertThat(resultat.getJetonVerificationHache()).isNull();
            assertThat(resultat.getJetonExpiration()).isNull();
        }

        @Test
        @DisplayName("refuse un jeton expire")
        void refuseUnJetonExpire() {
            Utilisateur membre = membreEnAttente();
            membre.enregistrerJetonVerification(JetonLien.empreinte("jeton-perime"), MAINTENANT.minusSeconds(1));
            when(repository.findByJetonVerificationHache(JetonLien.empreinte("jeton-perime")))
                    .thenReturn(Optional.of(membre));

            assertThatThrownBy(() -> service.confirmerAdresse("jeton-perime"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-03");

            assertThat(membre.isEmailVerifie()).isFalse();
        }

        @Test
        @DisplayName("refuse un jeton inconnu")
        void refuseUnJetonInconnu() {
            when(repository.findByJetonVerificationHache(JetonLien.empreinte("inexistant")))
                    .thenReturn(Optional.empty());

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
            membre.enregistrerJetonVerification(JetonLien.empreinte("ancien"), MAINTENANT.minusSeconds(1));
            when(repository.findByEmail("marie@exemple.be")).thenReturn(Optional.of(membre));

            Utilisateur resultat = service.renvoyerVerification("marie@exemple.be");

            // Le jeton precedent ne peut pas etre relu en clair : un jeton neuf le remplace.
            assertThat(resultat.getJetonVerificationHache())
                    .isNotEqualTo(JetonLien.empreinte("ancien")).hasSize(64);
            assertThat(resultat.getJetonExpiration())
                    .isEqualTo(MAINTENANT.plus(InscriptionService.VALIDITE_JETON));
        }

        @Test
        @DisplayName("refuse une adresse deja verifiee")
        void refuseUneAdresseDejaVerifiee() {
            Utilisateur membre = membreEnAttente();
            membre.confirmerAdresseEmail();
            when(repository.findByEmail("marie@exemple.be")).thenReturn(Optional.of(membre));

            assertThatThrownBy(() -> service.renvoyerVerification("marie@exemple.be"))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("codeRegle", "RM-04");
        }
        @Test
        @DisplayName("refuse une adresse inconnue")
        void refuseUneAdresseInconnue() {
            when(repository.findByEmail("inconnu@exemple.be")).thenReturn(Optional.empty());

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
         * par RenvoiVerificationIT : un ecran peut trahir ce que le service tait.
         */
        @Test
        @DisplayName("ne leve rien sur une adresse inconnue et n envoie aucun courriel")
        void adresseInconnueResteMuette() {
            when(repository.findByEmail("inconnu@exemple.be")).thenReturn(Optional.empty());

            service.demanderRenvoiVerification("inconnu@exemple.be");

            verifyNoInteractions(courriel);
        }

        @Test
        @DisplayName("ne leve rien sur une adresse deja verifiee et n envoie aucun courriel")
        void adresseDejaVerifieeResteMuette() {
            Utilisateur membre = membreEnAttente();
            membre.confirmerAdresseEmail();
            when(repository.findByEmail("marie@exemple.be")).thenReturn(Optional.of(membre));

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
            when(repository.findByEmail("marie@exemple.be")).thenReturn(Optional.of(membre));

            service.demanderRenvoiVerification("marie@exemple.be");

            ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
            verify(courriel).envoyerVerificationAdresse(eq(membre), lien.capture());
            assertThat(lien.getValue())
                    .startsWith("http://localhost:8080/inscription/verification?jeton=")
                    .doesNotContain("ancien");
        }
    }

    private Utilisateur membreEnAttente() {
        return new Utilisateur("marie@exemple.be", "$2a$04$empreinte",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    }
}
