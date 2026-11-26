package be.autoservplus.identite.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Statut du compte face à la vérification de l'adresse et à la suspension (RM-04, F1). */
@DisplayName("Utilisateur, statut et vérification de l'adresse")
class UtilisateurStatutTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-26T10:00:00Z");

    private Utilisateur membre;

    @BeforeEach
    void preparer() {
        membre = new Utilisateur("marie@exemple.be", "$2a$12$empreinte",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    }

    @Test
    @DisplayName("un compte en attente devient actif par le lien de vérification")
    void activation() {
        membre.confirmerAdresseEmail(MAINTENANT);

        assertThat(membre.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
        assertThat(membre.isEmailVerifie()).isTrue();
    }

    @Test
    @DisplayName("un compte suspendu avant sa vérification le reste après le lien")
    void suspensionGardee() {
        membre.enregistrerJetonVerification("empreinte", MAINTENANT.plusSeconds(3600));
        membre.suspendre();
        membre.confirmerAdresseEmail(MAINTENANT);

        assertThat(membre.getStatut()).isEqualTo(StatutUtilisateur.SUSPENDU);
    }

    @Test
    @DisplayName("la suspension annule le lien de vérification encore valide")
    void lienAnnule() {
        membre.enregistrerJetonVerification("empreinte", MAINTENANT.plusSeconds(3600));
        membre.suspendre();

        assertThat(membre.getJetonVerificationHache()).isNull();
    }

    @Test
    @DisplayName("la réactivation d'un compte jamais vérifié le rend à l'attente de validation")
    void reactivationSansVerification() {
        membre.suspendre();
        membre.reactiver();

        assertThat(membre.getStatut()).isEqualTo(StatutUtilisateur.EN_ATTENTE_VALIDATION);
    }

    @Test
    @DisplayName("la réactivation d'un compte vérifié le rend actif")
    void reactivationApresVerification() {
        membre.confirmerAdresseEmail(MAINTENANT);
        membre.suspendre();
        membre.reactiver();

        assertThat(membre.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
    }
}
