package be.autoservplus.identite.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Utilisateur, echecs de connexion")
class UtilisateurEchecsConnexionTest {

    private static final int SEUIL = 5;
    private static final Duration FENETRE = Duration.ofMinutes(15);
    private static final Duration VERROU = Duration.ofMinutes(15);
    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration UNE_NANO = Duration.ofNanos(1);

    private Utilisateur membre;

    @BeforeEach
    void preparer() {
        membre = new Utilisateur("marie@exemple.be", "$2a$12$empreinte",
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    }

    private boolean echec(Instant quand) {
        return membre.enregistrerEchecConnexion(SEUIL, FENETRE, VERROU, quand);
    }

    /** Quatre échecs de T0 à T0 + 3 min, le cinquième reste à placer. */
    private void quatreEchecs() {
        for (int i = 0; i < SEUIL - 1; i++) {
            assertThat(echec(T0.plus(Duration.ofMinutes(i)))).isFalse();
        }
    }

    @Nested
    @DisplayName("fenetre ouverte par le premier echec")
    class Fenetre {

        @Test
        @DisplayName("le cinquieme echec juste avant la fin de la fenetre verrouille")
        void cinquiemeEchecDansLaFenetre() {
            quatreEchecs();
            Instant dernier = T0.plus(FENETRE).minus(UNE_NANO);

            assertThat(echec(dernier)).isTrue();

            assertThat(membre.getVerrouilleJusquA()).isEqualTo(dernier.plus(VERROU));
            assertThat(membre.getTentativesEchouees()).isEqualTo((short) SEUIL);
        }

        @Test
        @DisplayName("a la fin exacte de la fenetre, le compteur repart de un")
        void cinquiemeEchecALaFinDeLaFenetre() {
            quatreEchecs();
            Instant fin = T0.plus(FENETRE);

            assertThat(echec(fin)).isFalse();

            assertThat(membre.getTentativesEchouees()).isEqualTo((short) 1);
            assertThat(membre.getPremierEchecLe()).isEqualTo(fin);
            assertThat(membre.getVerrouilleJusquA()).isNull();
        }

        @Test
        @DisplayName("la fenetre se mesure depuis le premier echec, pas depuis le dernier")
        void fenetreFixe() {
            quatreEchecs();

            echec(T0.plus(Duration.ofMinutes(20)));

            assertThat(membre.getPremierEchecLe()).isEqualTo(T0.plus(Duration.ofMinutes(20)));
            assertThat(membre.getTentativesEchouees()).isEqualTo((short) 1);
        }
    }

    @Nested
    @DisplayName("pendant et apres un verrou")
    class Verrou {

        private Instant echeance;

        @BeforeEach
        void verrouiller() {
            quatreEchecs();
            assertThat(echec(T0.plus(Duration.ofMinutes(4)))).isTrue();
            echeance = membre.getVerrouilleJusquA();
        }

        @Test
        @DisplayName("un echec juste avant la levee ne compte pas et ne prolonge rien")
        void echecPendantLeVerrou() {
            assertThat(echec(echeance.minus(UNE_NANO))).isFalse();

            assertThat(membre.getVerrouilleJusquA()).isEqualTo(echeance);
            assertThat(membre.getTentativesEchouees()).isEqualTo((short) SEUIL);
        }

        @Test
        @DisplayName("des echecs repetes pendant le verrou ne le reconduisent pas")
        void aucuneReconduction() {
            for (int i = 0; i < 3 * SEUIL; i++) {
                echec(echeance.minus(Duration.ofMinutes(10)).plusSeconds(i));
            }

            assertThat(membre.getVerrouilleJusquA()).isEqualTo(echeance);
        }

        @Test
        @DisplayName("a la levee exacte du verrou, le compteur repart de un")
        void echecALaLevee() {
            assertThat(echec(echeance)).isFalse();

            assertThat(membre.getTentativesEchouees()).isEqualTo((short) 1);
            assertThat(membre.getPremierEchecLe()).isEqualTo(echeance);
            assertThat(membre.estVerrouille(echeance)).isFalse();
        }
    }

    @Nested
    @DisplayName("combinaison avec le verrou du second facteur")
    class SecondFacteur {

        @Test
        @DisplayName("un mot de passe faux ne raccourcit pas un verrou de 24 h")
        void neRaccourcitPas() {
            membre.enregistrerEchecSecondFacteur(1, Duration.ofHours(24), Duration.ofHours(24), T0);
            Instant echeance = membre.getVerrouilleJusquA();

            for (int i = 0; i < SEUIL; i++) {
                assertThat(echec(T0.plus(Duration.ofHours(1)).plusSeconds(i))).isFalse();
            }

            assertThat(membre.getVerrouilleJusquA()).isEqualTo(echeance);
        }

        @Test
        @DisplayName("le second facteur garde l echeance la plus tardive des deux")
        void garderLaPlusTardive() {
            quatreEchecs();
            echec(T0.plus(Duration.ofMinutes(4)));
            Instant echeanceMotDePasse = membre.getVerrouilleJusquA();

            membre.enregistrerEchecSecondFacteur(1, Duration.ofMinutes(1), Duration.ofHours(24),
                    T0.plus(Duration.ofMinutes(5)));

            assertThat(membre.getVerrouilleJusquA()).isEqualTo(echeanceMotDePasse);
        }
    }

    @Nested
    @DisplayName("remise a zero")
    class RemiseAZero {

        @Test
        @DisplayName("une connexion reussie ferme la fenetre")
        void connexionReussie() {
            quatreEchecs();

            membre.enregistrerConnexionReussie(T0.plus(Duration.ofMinutes(5)));

            assertThat(membre.getTentativesEchouees()).isZero();
            assertThat(membre.getPremierEchecLe()).isNull();
        }

        @Test
        @DisplayName("un nouveau mot de passe ferme la fenetre")
        void changementDeMotDePasse() {
            quatreEchecs();

            membre.changerMotDePasse("$2a$12$nouvelle");

            assertThat(membre.getTentativesEchouees()).isZero();
            assertThat(membre.getPremierEchecLe()).isNull();
        }
    }
}
