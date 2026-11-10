package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * L implementation console journalise sans effet de bord externe : le seul contrat
 * verifiable ici est que chaque envoi s execute sans lever d exception, meme sur les
 * nouvelles methodes de notification de rendez-vous.
 */
@DisplayName("CourrielConsole")
class CourrielConsoleTest {

    private CourrielConsole courriel;
    private Utilisateur marie;
    private DetailsRdvCourriel rdv;
    private ListAppender<ILoggingEvent> journal;

    @BeforeEach
    void setUp() {
        // Meme configuration que spring.messages : UTF-8, aucun repli sur la langue du
        // systeme, le fichier sans suffixe tenant le francais.
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        courriel = new CourrielConsole(messages);
        journal = new ListAppender<>();
        journal.start();
        ((Logger) LoggerFactory.getLogger(CourrielConsole.class)).addAppender(journal);
        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        rdv = new DetailsRdvCourriel("RDV-2026-0001", "dimanche 13 septembre 2026", "10:00");
    }

    @Test
    @DisplayName("envoie la confirmation d un rendez-vous sans jeter")
    void envoieConfirmation() {
        PieceJointeCourriel agenda = new PieceJointeCourriel(
                "rendez-vous-RDV-2026-0001.ics", "text/calendar", "BEGIN:VCALENDAR".getBytes());

        assertThatCode(() -> courriel.envoyerConfirmationRdv(marie, rdv, agenda))
                .doesNotThrowAnyException();
    }

    /**
     * La piece jointe est facultative (F38) : le fichier d agenda peut manquer sans
     * que la confirmation elle-meme soit retenue. Le cas est teste parce que c est
     * exactement celui ou une journalisation naive dereferencerait un null.
     */
    @Test
    @DisplayName("envoie la confirmation meme sans fichier d agenda")
    void envoieConfirmationSansAgenda() {
        assertThatCode(() -> courriel.envoyerConfirmationRdv(marie, rdv, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("envoie le refus d un rendez-vous sans jeter")
    void envoieRefus() {
        assertThatCode(() -> courriel.envoyerRefusRdv(marie, rdv, "Piece indisponible"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("envoie l annulation par le garage sans jeter")
    void envoieAnnulationParLeGarage() {
        assertThatCode(() -> courriel.envoyerAnnulationParLeGarage(marie, rdv, "Panne du pont"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("envoie l invitation a recuperer le vehicule sans jeter")
    void envoieInterventionTerminee() {
        assertThatCode(() -> courriel.envoyerInterventionTerminee(new DetailsInterventionTerminee(
                "marie@exemple.be", "Marie", "INT-2026-0001", "VW Golf", "1-ABC-123",
                "/mes-avis/11111111-1111-1111-1111-111111111111/nouveau")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("envoie le recu de paiement sans jeter")
    void envoieConfirmationPaiement() {
        assertThatCode(() -> courriel.envoyerConfirmationPaiement(new DetailsPaiementCourriel(
                "marie@exemple.be", "Marie", "CMD-2026-0001", "80,21 €")))
                .doesNotThrowAnyException();
    }

    @AfterEach
    void detacher() {
        ((Logger) LoggerFactory.getLogger(CourrielConsole.class)).detachAppender(journal);
    }

    private String dernierCourriel() {
        return journal.list.get(journal.list.size() - 1).getFormattedMessage();
    }

    @ParameterizedTest
    @EnumSource(OrigineMotDePasse.class)
    @DisplayName("l avis de mot de passe se compose dans les trois langues, pour chaque origine")
    void avisDansChaqueLangue(OrigineMotDePasse origine) {
        for (Langue langue : Langue.values()) {
            marie.setLangue(langue);
            assertThatCode(() -> courriel.envoyerAvisMotDePasseModifie(marie, origine))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("l avis suit la langue du compte, pas celle du serveur")
    void avisDansLaLangueDuTitulaire() {
        marie.setLangue(Langue.nl);
        courriel.envoyerAvisMotDePasseModifie(marie, OrigineMotDePasse.REINITIALISATION);
        assertThat(dernierCourriel()).contains("Uw wachtwoord is gewijzigd")
                .doesNotContain("Votre mot de passe");

        marie.setLangue(Langue.fr);
        courriel.envoyerAvisMotDePasseModifie(marie, OrigineMotDePasse.CHANGEMENT_IMPOSE);
        assertThat(dernierCourriel()).contains("Votre mot de passe a été modifié")
                .contains("première connexion");
    }

    @Test
    @DisplayName("l avis ne porte ni lien ni jeton")
    void avisSansLien() {
        courriel.envoyerAvisMotDePasseModifie(marie, OrigineMotDePasse.REINITIALISATION);
        assertThat(dernierCourriel()).doesNotContain("http").doesNotContain("jeton=");
    }
}
