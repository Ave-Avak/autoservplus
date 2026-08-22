package be.autoservplus.identite.service;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Point unique de resolution de l auteur d une action journalisee : les quatre
 * branches sont couvertes ici, une fois, plutot que dans chaque service qui
 * journalise.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuteurCourant")
class AuteurCourantTest {

    @Mock private UtilisateurRepository utilisateurs;
    @InjectMocks private AuteurCourant auteurCourant;

    @AfterEach
    void tearDown() {
        // Le contexte de securite est un ThreadLocal : sans nettoyage, un test qui
        // pose un principal contaminerait les suivants.
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("resout l'utilisateur authentifie depuis le contexte de securite")
    void resoutLUtilisateurAuthentifie() {
        Utilisateur admin = new Utilisateur("admin@garage.be", "$2a$12$h", "Garage", "Paul",
                TypeUtilisateur.ADMINISTRATEUR);
        when(utilisateurs.findByEmailIgnoreCase("admin@garage.be")).thenReturn(Optional.of(admin));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@garage.be", "n/a", List.of()));

        assertThat(auteurCourant.resoudre()).isSameAs(admin);
    }

    @Test
    @DisplayName("sans authentification, l'auteur est nul : la trace existe quand meme")
    void sansAuthentification() {
        assertThat(auteurCourant.resoudre()).isNull();
        // Aucune requete inutile en base pour un traitement systeme.
        verifyNoInteractions(utilisateurs);
    }

    @Test
    @DisplayName("un principal anonyme n'est pas un auteur")
    void principalAnonyme() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "cle", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(auteurCourant.resoudre()).isNull();
        verifyNoInteractions(utilisateurs);
    }

    @Test
    @DisplayName("un principal sans compte en base ne bloque pas l'ecriture du journal")
    void principalSansCompte() {
        when(utilisateurs.findByEmailIgnoreCase("fantome@garage.be")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("fantome@garage.be", "n/a", List.of()));

        assertThat(auteurCourant.resoudre()).isNull();
    }
}
