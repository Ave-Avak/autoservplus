package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.communication.service.OrigineMotDePasse;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Changement du mot de passe depuis le profil")
class ChangementMotDePasseServiceTest {

    private static final String EMAIL = "marie@exemple.be";
    private static final String ACTUEL = "phrase actuelle du membre";
    private static final String NOUVEAU = "nouvelle phrase du membre";

    @Mock private UtilisateurRepository utilisateurs;
    @Mock private PasswordEncoder encodeur;
    @Mock private PolitiqueMotDePasse politique;
    @Mock private JournalSecuriteCompte journal;
    @Mock private RevocationSessions revocation;
    @Mock private ServiceCourriel courriel;

    private ChangementMotDePasseService service;
    private Utilisateur marie;

    @BeforeEach
    void setUp() {
        service = new ChangementMotDePasseService(utilisateurs, encodeur, politique, journal,
                revocation, courriel);
        marie = new Utilisateur(EMAIL, "$2a$12$actuelle", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        when(utilisateurs.findByEmail(EMAIL)).thenReturn(Optional.of(marie));
        lenient().when(encodeur.matches(ACTUEL, "$2a$12$actuelle")).thenReturn(true);
        lenient().when(encodeur.encode(NOUVEAU)).thenReturn("$2a$12$nouvelle");
    }

    @Test
    @DisplayName("succès : empreinte remplacée, journal, autres sessions fermées, avis au titulaire")
    void succes() {
        service.changer(EMAIL, ACTUEL, NOUVEAU, "session-courante");

        assertThat(marie.getMotDePasseHache()).isEqualTo("$2a$12$nouvelle");
        verify(politique).exiger(NOUVEAU);
        verify(journal).consigner(marie, TypeEvenementSecurite.MOT_DE_PASSE_CHANGE, marie);
        verify(revocation).revoquerSauf(EMAIL, "session-courante");
        verify(courriel).envoyerAvisMotDePasseModifie(marie, OrigineMotDePasse.CHANGEMENT_PROFIL);
    }

    @Test
    @DisplayName("mot de passe actuel faux : refus typé, rien ne change")
    void actuelIncorrect() {
        assertThatThrownBy(() -> service.changer(EMAIL, "faux", NOUVEAU, "s"))
                .isInstanceOf(MotDePasseActuelIncorrectException.class);

        assertThat(marie.getMotDePasseHache()).isEqualTo("$2a$12$actuelle");
        verifyNoInteractions(politique, journal, revocation, courriel);
    }

    @Test
    @DisplayName("nouveau mot de passe hors politique : refus, rien ne change")
    void horsPolitique() {
        doThrow(new RefusTraduit("validation.motDePasse.politique", "refus"))
                .when(politique).exiger(anyString());

        assertThatThrownBy(() -> service.changer(EMAIL, ACTUEL, "court", "s"))
                .isInstanceOf(RefusTraduit.class)
                .isNotInstanceOf(MotDePasseActuelIncorrectException.class);

        assertThat(marie.getMotDePasseHache()).isEqualTo("$2a$12$actuelle");
        verifyNoInteractions(journal, revocation, courriel);
    }

    @Test
    @DisplayName("nouveau identique à l'actuel : refus")
    void identique() {
        assertThatThrownBy(() -> service.changer(EMAIL, ACTUEL, ACTUEL, "s"))
                .isInstanceOf(RefusTraduit.class)
                .isNotInstanceOf(MotDePasseActuelIncorrectException.class);

        verifyNoInteractions(journal, revocation, courriel);
    }

    @Test
    @DisplayName("blocage : consigné au journal du compte, sans verrouiller le compte")
    void blocage() {
        service.consignerBlocage(EMAIL);

        verify(journal).consigner(marie, TypeEvenementSecurite.CHANGEMENT_MOT_DE_PASSE_BLOQUE, null);
        assertThat(marie.getVerrouilleJusquA()).isNull();
    }
}
