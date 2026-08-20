package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests du pont entre les comptes AutoServ+ et Spring Security.
 *
 * <p>Cette classe decide qui peut se connecter : compte actif, compte non verifie,
 * compte verrouille. Ses regles conditionnent l acces a toute l application.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UtilisateurDetailsService")
class UtilisateurDetailsServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-20T10:00:00Z");

    @Mock
    private UtilisateurRepository repository;

    private UtilisateurDetailsService service;

    @BeforeEach
    void preparer() {
        service = new UtilisateurDetailsService(repository, Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("autorise un compte actif et verifie")
    void autoriseUnCompteActif() {
        Utilisateur membre = membre();
        membre.confirmerAdresseEmail();
        when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

        UserDetails details = service.loadUserByUsername("marie@exemple.be");

        assertThat(details.getUsername()).isEqualTo("marie@exemple.be");
        assertThat(details.getPassword()).isEqualTo(membre.getMotDePasseHache());
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_MEMBRE");
    }

    @Test
    @DisplayName("refuse un compte dont l adresse n a jamais ete verifiee")
    void refuseUnCompteNonVerifie() {
        Utilisateur membre = membre();
        when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

        UserDetails details = service.loadUserByUsername("marie@exemple.be");

        assertThat(membre.getStatut()).isEqualTo(StatutUtilisateur.EN_ATTENTE_VALIDATION);
        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("signale un compte verrouille")
    void signaleUnCompteVerrouille() {
        Utilisateur membre = membre();
        membre.confirmerAdresseEmail();
        membre.verrouillerJusqu(MAINTENANT.plusSeconds(900));
        when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

        UserDetails details = service.loadUserByUsername("marie@exemple.be");

        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("libere le compte une fois la duree de verrouillage ecoulee")
    void libereApresExpiration() {
        Utilisateur membre = membre();
        membre.confirmerAdresseEmail();
        membre.verrouillerJusqu(MAINTENANT.minusSeconds(1));
        when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

        UserDetails details = service.loadUserByUsername("marie@exemple.be");

        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("attribue le role d administrateur")
    void attribueLeRoleAdministrateur() {
        Utilisateur admin = new Utilisateur("admin@autoservplus.be", "$2a$12$empreinte",
                "Systeme", "Administrateur", TypeUtilisateur.ADMINISTRATEUR);
        admin.confirmerAdresseEmail();
        when(repository.findByEmailIgnoreCase("admin@autoservplus.be")).thenReturn(Optional.of(admin));

        assertThat(service.loadUserByUsername("admin@autoservplus.be").getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMINISTRATEUR");
    }

    @Test
    @DisplayName("refuse une adresse inconnue sans reveler qu elle est inconnue")
    void refuseUneAdresseInconnue() {
        when(repository.findByEmailIgnoreCase("inconnu@exemple.be")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("inconnu@exemple.be"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Identifiants incorrects.");
    }

    @Test
    @DisplayName("refuse un compte suspendu")
    void refuseUnCompteSuspendu() {
        Utilisateur membre = membre();
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.SUSPENDU);
        when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

        assertThat(service.loadUserByUsername("marie@exemple.be").isEnabled()).isFalse();
    }

    private Utilisateur membre() {
        return new Utilisateur("marie@exemple.be", "$2a$12$empreinteBCryptDeSoixanteCaracteresExactement00",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    }
}