package be.autoservplus.communication.service;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @BeforeEach
    void setUp() {
        courriel = new CourrielConsole();
        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        rdv = new DetailsRdvCourriel("RDV-2026-0001", "dimanche 13 septembre 2026", "10:00");
    }

    @Test
    @DisplayName("envoie la confirmation d un rendez-vous sans jeter")
    void envoieConfirmation() {
        assertThatCode(() -> courriel.envoyerConfirmationRdv(marie, rdv))
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
}
