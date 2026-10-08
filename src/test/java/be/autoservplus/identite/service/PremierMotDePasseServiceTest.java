package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.identite.domain.StatutUtilisateur;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Premier mot de passe : définition par lien, changement imposé")
class PremierMotDePasseServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-08T08:00:00Z");

    @Mock private UtilisateurRepository utilisateurs;
    @Mock private JournalSecuriteCompte journal;

    private final PasswordEncoder encodeur = new BCryptPasswordEncoder(4);
    private PremierMotDePasseService service;
    private Utilisateur admin;

    @BeforeEach
    void setUp() {
        service = new PremierMotDePasseService(utilisateurs, encodeur, journal,
                Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        admin = new Utilisateur("admin@exemple.be", encodeur.encode("inerte"), "Nom", "Prenom",
                TypeUtilisateur.ADMINISTRATEUR);
    }

    @Test
    @DisplayName("le jeton est cherché par son empreinte, jamais en clair, et ne sert qu'une fois")
    void definition() {
        JetonDefinition jeton = JetonDefinition.tirer();
        admin.preparerDefinitionMotDePasse(jeton.empreinte(), MAINTENANT.plus(Duration.ofHours(24)));
        when(utilisateurs.findByJetonDefinitionHache(jeton.empreinte())).thenReturn(Optional.of(admin));

        service.definir(jeton.clair(), "phrase de passe choisie");

        assertThat(jeton.empreinte()).isNotEqualTo(jeton.clair()).hasSize(64);
        assertThat(encodeur.matches("phrase de passe choisie", admin.getMotDePasseHache())).isTrue();
        assertThat(admin.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
        assertThat(admin.getJetonDefinitionHache()).isNull();
        verify(journal).consigner(admin, TypeEvenementSecurite.MOT_DE_PASSE_DEFINI, null);
    }

    @Test
    @DisplayName("un jeton expiré est refusé, sans rien changer")
    void expire() {
        JetonDefinition jeton = JetonDefinition.tirer();
        admin.preparerDefinitionMotDePasse(jeton.empreinte(), MAINTENANT.minusSeconds(1));
        when(utilisateurs.findByJetonDefinitionHache(jeton.empreinte())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.definir(jeton.clair(), "phrase de passe choisie"))
                .isInstanceOf(RefusTraduit.class);
        assertThat(admin.getStatut()).isEqualTo(StatutUtilisateur.EN_ATTENTE_VALIDATION);
    }

    @Test
    @DisplayName("un jeton inconnu ou absent est introuvable, et ne figure pas dans le message")
    void inconnu() {
        when(utilisateurs.findByJetonDefinitionHache(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifierJeton("abcdef"))
                .isInstanceOf(RessourceIntrouvableException.class)
                .hasMessageNotContaining("abcdef");
        assertThatThrownBy(() -> service.verifierJeton(" "))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("le changement imposé refuse le mot de passe remis, accepte un autre")
    void changementImpose() {
        Utilisateur exploitant = new Utilisateur("chef@exemple.be", encodeur.encode("secret du fichier env"),
                "Nom", "Prenom", TypeUtilisateur.SUPER_ADMINISTRATEUR);
        exploitant.exigerChangementMotDePasse();
        when(utilisateurs.findByEmailIgnoreCase("chef@exemple.be")).thenReturn(Optional.of(exploitant));

        assertThatThrownBy(() -> service.changerImpose("chef@exemple.be", "secret du fichier env"))
                .isInstanceOf(RefusTraduit.class);
        assertThat(exploitant.doitChangerMotDePasse()).isTrue();

        service.changerImpose("chef@exemple.be", "phrase choisie par le chef");

        assertThat(exploitant.doitChangerMotDePasse()).isFalse();
        verify(journal).consigner(exploitant, TypeEvenementSecurite.MOT_DE_PASSE_CHANGE, null);
    }
}
