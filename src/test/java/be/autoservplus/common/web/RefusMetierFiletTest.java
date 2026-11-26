package be.autoservplus.common.web;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RegleMetierException;
import be.autoservplus.common.exception.TransitionRefusee;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filet des refus métier : statut posé par le gestionnaire, puis message rendu par la page
 * d'erreur, enchaînés à la main comme le conteneur le fait par {@code /error}.
 */
@DisplayName("Refus métier non rattrapés")
class RefusMetierFiletTest {

    private final GestionnaireErreursGlobal gestionnaire = new GestionnaireErreursGlobal();
    private PagesErreurResolver resolveur;
    private MockHttpServletRequest requete;
    private MockHttpServletResponse reponse;

    @BeforeEach
    void preparer() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("erreur.422.titre", Locale.FRENCH, "Demande refusée");
        messages.addMessage("erreur.422.message", Locale.FRENCH, "Message générique");
        messages.addMessage("panier.erreur.mixte", Locale.FRENCH, "Panier mixte refusé");
        messages.addMessage("rdv.erreur.delai", Locale.FRENCH, "Plus possible à moins de {0} heures");
        resolveur = new PagesErreurResolver(messages);
        requete = new MockHttpServletRequest("POST", "/commande");
        reponse = new MockHttpServletResponse();
        LocaleContextHolder.setLocale(Locale.FRENCH);
    }

    @AfterEach
    void nettoyer() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Map<String, Object> pageRendue() {
        ModelAndView vue = resolveur.resolveErrorView(requete, HttpStatus.valueOf(reponse.getStatus()), Map.of());
        assertThat(vue.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        return vue.getModel();
    }

    @Test
    @DisplayName("un refus traduit sort en 422 avec son propre message")
    void refusTraduit() throws Exception {
        gestionnaire.refusTraduit(new RefusTraduit("panier.erreur.mixte", "Phrase du journal"), requete, reponse);

        assertThat(reponse.getStatus()).isEqualTo(422);
        assertThat(pageRendue())
                .containsEntry("titre", "Demande refusée")
                .containsEntry("messageErreur", "Panier mixte refusé");
    }

    @Test
    @DisplayName("les valeurs d'un refus traduit entrent dans son message")
    void refusAvecArguments() throws Exception {
        gestionnaire.refusTraduit(new RefusTraduit("RM-11", "rdv.erreur.delai", new Object[] {24}, "Journal"),
                requete, reponse);

        assertThat(pageRendue()).containsEntry("messageErreur", "Plus possible à moins de 24 heures");
    }

    @Test
    @DisplayName("une transition refusée par l'entité sort en 422 avec sa clé")
    void transitionRefusee() throws Exception {
        gestionnaire.transitionRefusee(new TransitionRefusee("panier.erreur.mixte", "Journal"), requete, reponse);

        assertThat(reponse.getStatus()).isEqualTo(422);
        assertThat(pageRendue()).containsEntry("messageErreur", "Panier mixte refusé");
    }

    @Test
    @DisplayName("un refus sans clé affiche le texte générique traduit, jamais la phrase française du journal")
    void refusSansCle() throws Exception {
        gestionnaire.regleMetier(new RegleMetierException("Phrase française du journal"), requete, reponse);

        assertThat(reponse.getStatus()).isEqualTo(422);
        assertThat(pageRendue()).containsEntry("messageErreur", "Message générique");
    }

    @Test
    @DisplayName("une clé absente des fichiers retombe sur le texte générique")
    void cleInconnue() throws Exception {
        gestionnaire.refusTraduit(new RefusTraduit("cle.absente", "Journal"), requete, reponse);

        assertThat(pageRendue()).containsEntry("messageErreur", "Message générique");
    }
}
