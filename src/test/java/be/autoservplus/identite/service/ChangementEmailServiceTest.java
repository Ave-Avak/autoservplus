package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.communication.service.DetailsChangementEmailCourriel;
import be.autoservplus.communication.service.ServiceCourriel;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Changement d adresse en deux temps (CdC 5.2.2).
 *
 * <p>Les cas de neutralite sont les plus importants du lot : ils verifient que deux
 * situations qui different en base produisent un comportement <b>indiscernable</b>
 * pour celui qui demande. Un test qui se contenterait de constater l absence
 * d exception ne prouverait rien — c est l egalite des effets observables qui
 * compte.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChangementEmailService")
class ChangementEmailServiceTest {

    private static final String ACTUELLE = "marie@exemple.be";
    private static final String CIBLE = "marie.dupont@exemple.be";
    private static final String MOT_DE_PASSE = "phrase de passe du test";
    private static final String IP = "203.0.113.7";
    private static final Instant MAINTENANT = Instant.parse("2026-09-21T10:00:00Z");

    @Mock private UtilisateurRepository repository;
    @Mock private ServiceCourriel courriel;
    @Mock private PasswordEncoder encodeur;
    @Mock private LimiteurDemandesCourriel limiteur;

    private ChangementEmailService service;
    private Utilisateur membre;

    @BeforeEach
    void setUp() {
        service = new ChangementEmailService(repository, courriel, encodeur, limiteur,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        membre = new Utilisateur(ACTUELLE, "$2a$12$empreinte", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        lenient().when(repository.findByEmailIgnoreCase(ACTUELLE)).thenReturn(Optional.of(membre));
        lenient().when(encodeur.matches(MOT_DE_PASSE, membre.getMotDePasseHache())).thenReturn(true);
        lenient().when(limiteur.autoriser(anyString(), anyString())).thenReturn(true);
    }

    private void demander() {
        service.demander(ACTUELLE, CIBLE, MOT_DE_PASSE, IP);
    }

    private void cibleLibre() {
        when(repository.existsByEmailIgnoreCase(CIBLE)).thenReturn(false);
        when(repository.existsByEmailEnAttenteIgnoreCase(CIBLE)).thenReturn(false);
    }

    @Nested
    @DisplayName("demande")
    class Demande {

        @Test
        @DisplayName("enregistre l'adresse en attente sans toucher à l'adresse en vigueur")
        void adresseEnVigueurIntacte() {
            cibleLibre();

            demander();

            // Le coeur de l option A1 : tant que rien n est prouve, le compte reste
            // joignable a l adresse d origine.
            assertThat(membre.getEmail()).isEqualTo(ACTUELLE);
            assertThat(membre.getEmailEnAttente()).isEqualTo(CIBLE);
            assertThat(membre.getJetonChangementEmail()).isNotBlank();
            assertThat(membre.getJetonChangementExpiration())
                    .isEqualTo(MAINTENANT.plus(ChangementEmailService.VALIDITE_JETON));
        }

        @Test
        @DisplayName("le lien part vers la NOUVELLE adresse, l'avis vers l'ANCIENNE")
        void deuxDestinatairesDistincts() {
            cibleLibre();

            demander();

            ArgumentCaptor<DetailsChangementEmailCourriel> lien =
                    ArgumentCaptor.forClass(DetailsChangementEmailCourriel.class);
            verify(courriel).envoyerConfirmationNouvelleAdresse(lien.capture(), anyString());
            assertThat(lien.getValue().destinataire()).isEqualTo(CIBLE);

            ArgumentCaptor<DetailsChangementEmailCourriel> avis =
                    ArgumentCaptor.forClass(DetailsChangementEmailCourriel.class);
            verify(courriel).envoyerAvisChangementAdresse(avis.capture());
            assertThat(avis.getValue().destinataire()).isEqualTo(ACTUELLE);
        }

        @Test
        @DisplayName("l'adresse est normalisée : casse et espaces ne créent pas de doublon")
        void adresseNormalisee() {
            cibleLibre();

            service.demander(ACTUELLE, "  MARIE.DUPONT@Exemple.BE ", MOT_DE_PASSE, IP);

            assertThat(membre.getEmailEnAttente()).isEqualTo(CIBLE);
        }

        @Test
        @DisplayName("mot de passe faux : refus, et rien n'est enregistré")
        void motDePasseFaux() {
            assertThatThrownBy(() -> service.demander(ACTUELLE, CIBLE, "mauvais", IP))
                    .isInstanceOf(RegleMetierException.class)
                    .extracting(e -> ((RegleMetierException) e).getCodeRegle())
                    .isEqualTo("RM-32");

            assertThat(membre.changementEmailEnCours()).isFalse();
            verify(courriel, never()).envoyerAvisChangementAdresse(any());
        }

        /**
         * Sans cette garde, quelqu un pourrait s inscrire avec l adresse d autrui puis
         * s en detacher avant que le titulaire ne s en apercoive : le compte porterait
         * alors une adresse prouvee sans qu aucune ne l ait jamais ete.
         */
        @Test
        @DisplayName("adresse d'origine non vérifiée : refus")
        void origineNonVerifiee() {
            Utilisateur neuf = new Utilisateur(ACTUELLE, "$2a$12$empreinte", "Dupont",
                    "Marie", TypeUtilisateur.MEMBRE);
            when(repository.findByEmailIgnoreCase(ACTUELLE)).thenReturn(Optional.of(neuf));
            when(encodeur.matches(MOT_DE_PASSE, neuf.getMotDePasseHache())).thenReturn(true);

            assertThatThrownBy(ChangementEmailServiceTest.this::demander)
                    .isInstanceOf(RegleMetierException.class)
                    .extracting(e -> ((RegleMetierException) e).getCodeRegle())
                    .isEqualTo("RM-33");
        }

        @Test
        @DisplayName("adresse identique à l'actuelle : refus")
        void adresseIdentique() {
            assertThatThrownBy(() -> service.demander(ACTUELLE, "MARIE@exemple.be", MOT_DE_PASSE, IP))
                    .isInstanceOf(RegleMetierException.class)
                    .extracting(e -> ((RegleMetierException) e).getCodeRegle())
                    .isEqualTo("RM-34");
        }

        /**
         * Le plafond est consomme AVANT toute lecture liee a la cible : un decompte qui
         * ne s incrementerait que pour les adresses libres se lirait a la milliseconde
         * pres et rendrait le refus bavard.
         */
        @Test
        @DisplayName("plafond atteint : refus, sans interroger la base sur la cible")
        void plafondAtteint() {
            when(limiteur.autoriser(CIBLE, IP)).thenReturn(false);

            assertThatThrownBy(ChangementEmailServiceTest.this::demander)
                    .isInstanceOf(RegleMetierException.class)
                    .extracting(e -> ((RegleMetierException) e).getCodeRegle())
                    .isEqualTo("RM-35");

            verify(repository, never()).existsByEmailIgnoreCase(anyString());
        }
    }

    @Nested
    @DisplayName("neutralité (aucun oracle d'existence de compte)")
    class Neutralite {

        @Test
        @DisplayName("adresse déjà portée par un compte : aucun refus, aucun lien, mais l'avis part quand même")
        void cibleDejaPrise() {
            when(repository.existsByEmailIgnoreCase(CIBLE)).thenReturn(true);

            demander();

            // Ce que le demandeur NE voit pas : aucune exception, donc le meme ecran.
            assertThat(membre.changementEmailEnCours()).isFalse();
            verify(courriel, never()).envoyerConfirmationNouvelleAdresse(any(), anyString());
            // Ce qu il voit : l avis a son ancienne adresse, exactement comme en cas de
            // succes. Son absence trahirait l indisponibilite de la cible.
            verify(courriel).envoyerAvisChangementAdresse(any());
        }

        @Test
        @DisplayName("adresse déjà réservée par une autre demande : même comportement")
        void cibleDejaReservee() {
            when(repository.existsByEmailIgnoreCase(CIBLE)).thenReturn(false);
            when(repository.existsByEmailEnAttenteIgnoreCase(CIBLE)).thenReturn(true);

            demander();

            assertThat(membre.changementEmailEnCours()).isFalse();
            verify(courriel, never()).envoyerConfirmationNouvelleAdresse(any(), anyString());
            verify(courriel).envoyerAvisChangementAdresse(any());
        }
    }

    @Nested
    @DisplayName("confirmation")
    class Confirmation {

        private String armer() {
            cibleLibre();
            demander();
            return membre.getJetonChangementEmail();
        }

        @Test
        @DisplayName("bascule l'adresse, la marque vérifiée et rend l'ancienne")
        void bascule() {
            String jeton = armer();
            when(repository.findByJetonChangementEmail(jeton)).thenReturn(Optional.of(membre));
            when(repository.existsByEmailIgnoreCase(CIBLE)).thenReturn(false);

            String ancienne = service.confirmer(jeton);

            assertThat(ancienne).isEqualTo(ACTUELLE);
            assertThat(membre.getEmail()).isEqualTo(CIBLE);
            assertThat(membre.isEmailVerifie()).isTrue();
            assertThat(membre.changementEmailEnCours()).isFalse();
        }

        /**
         * L index partiel reserve l adresse contre une autre DEMANDE, pas contre une
         * INSCRIPTION intervenue depuis. Sans ce controle la bascule echouerait au
         * flush sur uq_utilisateur_email, c est-a-dire par une erreur technique.
         */
        @Test
        @DisplayName("adresse prise entre la demande et la confirmation : refus propre")
        void priseEntreTemps() {
            String jeton = armer();
            when(repository.findByJetonChangementEmail(jeton)).thenReturn(Optional.of(membre));
            when(repository.existsByEmailIgnoreCase(CIBLE)).thenReturn(true);

            assertThatThrownBy(() -> service.confirmer(jeton))
                    .isInstanceOf(RegleMetierException.class)
                    .extracting(e -> ((RegleMetierException) e).getCodeRegle())
                    .isEqualTo("RM-37");

            assertThat(membre.getEmail()).isEqualTo(ACTUELLE);
            // La demande est abandonnee : la laisser vivante ferait echouer chaque
            // nouveau clic de la meme facon, sans que le membre sache quoi faire.
            assertThat(membre.changementEmailEnCours()).isFalse();
        }

        @Test
        @DisplayName("lien expiré : refus, et la demande est abandonnée")
        void expire() {
            String jeton = armer();
            ChangementEmailService tardif = new ChangementEmailService(repository, courriel,
                    encodeur, limiteur,
                    Clock.fixed(MAINTENANT.plus(ChangementEmailService.VALIDITE_JETON)
                            .plusSeconds(1), ZoneOffset.UTC));
            when(repository.findByJetonChangementEmail(jeton)).thenReturn(Optional.of(membre));

            assertThatThrownBy(() -> tardif.confirmer(jeton))
                    .isInstanceOf(RegleMetierException.class)
                    .extracting(e -> ((RegleMetierException) e).getCodeRegle())
                    .isEqualTo("RM-36");

            assertThat(membre.getEmail()).isEqualTo(ACTUELLE);
            assertThat(membre.changementEmailEnCours()).isFalse();
        }
    }
}
