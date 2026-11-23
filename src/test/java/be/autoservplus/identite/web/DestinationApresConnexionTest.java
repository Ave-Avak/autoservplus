package be.autoservplus.identite.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.SavedRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Destination après connexion")
class DestinationApresConnexionTest {

    private static MockHttpServletRequest demande(String methode, String chemin) {
        MockHttpServletRequest requete = new MockHttpServletRequest(methode, chemin);
        requete.setServerName("garage.exemple.be");
        return requete;
    }

    private static SavedRequest sauvegardee(MockHttpServletRequest requete) {
        return new DefaultSavedRequest(requete);
    }

    @Test
    @DisplayName("une page du site est reprise, avec ses paramètres")
    void pageDuSite() {
        MockHttpServletRequest requete = demande("GET", "/commandes/3f2a");
        assertThat(DestinationApresConnexion.pageDemandee(sauvegardee(requete), ""))
                .isEqualTo("/commandes/3f2a");

        MockHttpServletRequest filtree = demande("GET", "/admin/commandes");
        filtree.setQueryString("statut=PAYEE&page=2");
        assertThat(DestinationApresConnexion.pageDemandee(sauvegardee(filtree), ""))
                .isEqualTo("/admin/commandes?statut=PAYEE&page=2");
    }

    @Test
    @DisplayName("l'hôte de la requête sauvegardée n'est jamais repris : la redirection reste sur le site")
    void hoteExterneIgnore() {
        MockHttpServletRequest requete = demande("GET", "/mes-rendez-vous");
        requete.setServerName("site-pirate.example");
        requete.addHeader("Host", "site-pirate.example");

        assertThat(DestinationApresConnexion.pageDemandee(sauvegardee(requete), ""))
                .isEqualTo("/mes-rendez-vous");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"//site-pirate.example/page", "/\\site-pirate.example", "/page\\autre"})
    @DisplayName("un chemin qu'un navigateur lirait comme une adresse externe est refusé")
    void adresseExterneRefusee(String chemin) {
        assertThat(DestinationApresConnexion.pageDemandee(sauvegardee(demande("GET", chemin)), ""))
                .isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/connexion", "/connexion/second-facteur", "/deconnexion", "/error",
            "/api/v1/services", "/webhooks/paiement", "/apple-touch-icon.png", "/images/logo.svg"})
    @DisplayName("la connexion, la page d'erreur et ce qui n'est pas une page ne sont pas des destinations")
    void adressesEcartees(String chemin) {
        assertThat(DestinationApresConnexion.pageDemandee(sauvegardee(demande("GET", chemin)), ""))
                .isNull();
    }

    @Test
    @DisplayName("ni un envoi de formulaire, ni un fragment demandé par HTMX")
    void niEnvoiNiFragment() {
        assertThat(DestinationApresConnexion.pageDemandee(sauvegardee(demande("POST", "/panier/lignes")), ""))
                .isNull();

        MockHttpServletRequest fragment = demande("GET", "/mes-rendez-vous/creneaux");
        fragment.addHeader("HX-Request", "true");
        assertThat(DestinationApresConnexion.pageDemandee(sauvegardee(fragment), "")).isNull();
    }

    @Test
    @DisplayName("le préfixe de déploiement est retiré, et une adresse hors de lui est refusée")
    void contexteDeDeploiement() {
        assertThat(DestinationApresConnexion.pageDemandee(sauvegardee(demande("GET", "/garage/commandes")), "/garage"))
                .isEqualTo("/commandes");
        assertThat(DestinationApresConnexion.pageDemandee(sauvegardee(demande("GET", "/autre/commandes")), "/garage"))
                .isNull();
    }

    @Test
    @DisplayName("sans requête sauvegardée, aucune destination")
    void sansRequeteSauvegardee() {
        assertThat(DestinationApresConnexion.pageDemandee(null, "")).isNull();
    }
}
