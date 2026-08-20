package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TentativesConnexionService")
class TentativesConnexionServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-20T10:00:00Z");
    private static final int TENTATIVES_MAX = 5;

    @Mock
    private UtilisateurRepository repository;

    private TentativesConnexionService service;
    private Utilisateur membre;

    @BeforeEach
    void preparer() {
        Clock horlogeFigee = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
        service = new TentativesConnexionService(repository, horlogeFigee, TENTATIVES_MAX, 15);
        membre = new Utilisateur("marie@exemple.be", "$2a$12$empreinte",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    }

    @Test
    @DisplayName("incremente le compteur a chaque echec")
    void incrementeLeCompteur() {
        when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

        service.surEchec(echec("marie@exemple.be"));
        service.surEchec(echec("marie@exemple.be"));

        assertThat(membre.getTentativesEchouees()).isEqualTo((short) 2);
        assertThat(membre.estVerrouille(MAINTENANT)).isFalse();
    }

    @Test
    @DisplayName("verrouille le compte au cinquieme echec")
    void verrouilleAuCinquiemeEchec() {
        when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

        for (int i = 0; i < TENTATIVES_MAX; i++) {
            service.surEchec(echec("marie@exemple.be"));
        }

        assertThat(membre.getTentativesEchouees()).isEqualTo((short) TENTATIVES_MAX);
        assertThat(membre.estVerrouille(MAINTENANT)).isTrue();
        assertThat(membre.getVerrouilleJusquA()).isEqualTo(MAINTENANT.plusSeconds(15 * 60));
    }

    @Test
    @DisplayName("libere le compte apres la duree de verrouillage")
    void libereApresLaDuree() {
        when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));

        for (int i = 0; i < TENTATIVES_MAX; i++) {
            service.surEchec(echec("marie@exemple.be"));
        }

        assertThat(membre.estVerrouille(MAINTENANT.plusSeconds(16 * 60))).isFalse();
    }

    @Test
    @DisplayName("remet le compteur a zero apres une connexion reussie")
    void remetLeCompteurAZero() {
        when(repository.findByEmailIgnoreCase("marie@exemple.be")).thenReturn(Optional.of(membre));
        service.surEchec(echec("marie@exemple.be"));
        service.surEchec(echec("marie@exemple.be"));

        service.surSucces(succes("marie@exemple.be"));

        assertThat(membre.getTentativesEchouees()).isZero();
        assertThat(membre.getVerrouilleJusquA()).isNull();
        assertThat(membre.getDerniereConnexion()).isEqualTo(MAINTENANT);
    }

    @Test
    @DisplayName("ignore un echec sur une adresse inconnue")
    void ignoreUneAdresseInconnue() {
        when(repository.findByEmailIgnoreCase("inconnu@exemple.be")).thenReturn(Optional.empty());

        service.surEchec(echec("inconnu@exemple.be"));

        assertThat(membre.getTentativesEchouees()).isZero();
    }

    private AuthenticationFailureBadCredentialsEvent echec(String email) {
        Authentication auth = new UsernamePasswordAuthenticationToken(email, "mauvais");
        return new AuthenticationFailureBadCredentialsEvent(auth,
                new org.springframework.security.authentication.BadCredentialsException("test"));
    }

    private AuthenticationSuccessEvent succes(String email) {
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(email, null, java.util.List.of());
        return new AuthenticationSuccessEvent(auth);
    }
}