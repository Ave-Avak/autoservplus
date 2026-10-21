package be.autoservplus.common.web;

import be.autoservplus.identite.web.AuthentificationPartielle;
import be.autoservplus.messagerie.service.MessagerieService;
import be.autoservplus.messagerie.web.MessagerieModelAdvice;
import be.autoservplus.notification.service.NotificationService;
import be.autoservplus.notification.web.NotificationModelAdvice;
import be.autoservplus.vente.service.PanierService;
import be.autoservplus.vente.web.PanierModelAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les trois compteurs de l en-tete commun ne comptent que pour une connexion aboutie.
 *
 * <p>Le cas qui compte est la connexion <b>partielle</b> : elle satisfait
 * {@code isAuthenticated()}, et les advices, devenus globaux, interrogeraient la base
 * pour un compte dont le second facteur n est pas encore saisi.</p>
 */
@DisplayName("Compteurs de l'en-tête")
class CompteursEnTeteTest {

    private static final String MARIE = "marie@exemple.be";

    private final PanierService panier = mock(PanierService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final MessagerieService messagerie = mock(MessagerieService.class);

    private final PanierModelAdvice compteurPanier = new PanierModelAdvice(fournisseur(panier));
    private final NotificationModelAdvice compteurNotifications =
            new NotificationModelAdvice(fournisseur(notifications));
    private final MessagerieModelAdvice compteurMessages =
            new MessagerieModelAdvice(fournisseur(messagerie));

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> fournisseur(T service) {
        ObjectProvider<T> fournisseur = mock(ObjectProvider.class);
        when(fournisseur.getIfAvailable()).thenReturn(service);
        return fournisseur;
    }

    private static Authentication complete() {
        return UsernamePasswordAuthenticationToken.authenticated(MARIE, null,
                AuthorityUtils.createAuthorityList("ROLE_MEMBRE"));
    }

    @Test
    @DisplayName("connexion aboutie : les trois services sont interrogés")
    void connexionAboutie() {
        when(panier.nombreArticles(MARIE)).thenReturn(3);
        when(notifications.nombreNonLues(MARIE)).thenReturn(2L);
        when(messagerie.nombreFilsNonLus(MARIE)).thenReturn(1L);

        assertThat(compteurPanier.nombreArticlesPanier(complete())).isEqualTo(3);
        assertThat(compteurNotifications.nombreNonLues(complete())).isEqualTo(2L);
        assertThat(compteurMessages.nombreFilsNonLus(complete())).isEqualTo(1L);
    }

    @Test
    @DisplayName("connexion partielle : zéro, sans aucune requête")
    void connexionPartielle() {
        Authentication partielle = new AuthentificationPartielle(complete(), false);

        assertThat(compteurPanier.nombreArticlesPanier(partielle)).isZero();
        assertThat(compteurNotifications.nombreNonLues(partielle)).isZero();
        assertThat(compteurMessages.nombreFilsNonLus(partielle)).isZero();
        verify(panier, never()).nombreArticles(anyString());
        verify(notifications, never()).nombreNonLues(anyString());
        verify(messagerie, never()).nombreFilsNonLus(anyString());
    }

    @Test
    @DisplayName("visiteur anonyme : zéro, sans aucune requête")
    void visiteurAnonyme() {
        Authentication anonyme = new AnonymousAuthenticationToken("cle", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(compteurPanier.nombreArticlesPanier(anonyme)).isZero();
        assertThat(compteurNotifications.nombreNonLues(null)).isZero();
        assertThat(compteurMessages.nombreFilsNonLus(anonyme)).isZero();
        verify(panier, never()).nombreArticles(anyString());
        verify(messagerie, never()).nombreFilsNonLus(anyString());
    }
}
