package be.autoservplus.identite.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Utilisateur : second facteur et premier mot de passe")
class UtilisateurSecondFacteurTest {

    private static final Instant MAINTENANT = Instant.parse("2026-10-08T08:00:00Z");
    private static final Duration INITIAL = Duration.ofMinutes(15);
    private static final Duration PLAFOND = Duration.ofHours(24);

    private static Utilisateur compte(TypeUtilisateur type) {
        return new Utilisateur("titulaire@exemple.be", "empreinte", "Nom", "Prenom", type);
    }

    private static Utilisateur enrole() {
        Utilisateur u = compte(TypeUtilisateur.MEMBRE);
        u.commencerEnrolement(new byte[]{1}, (short) 1);
        u.confirmerEnrolement(MAINTENANT, 100L);
        return u;
    }

    @Nested
    @DisplayName("verrouillage progressif")
    class Verrouillage {

        private Optional<Instant> cinqEchecs(Utilisateur u, Instant instant) {
            Optional<Instant> resultat = Optional.empty();
            for (int i = 0; i < 5; i++) {
                resultat = u.enregistrerEchecSecondFacteur(5, INITIAL, PLAFOND, instant);
            }
            return resultat;
        }

        @Test
        @DisplayName("quatre échecs ne verrouillent pas, le cinquième verrouille quinze minutes")
        void seuil() {
            Utilisateur u = enrole();
            for (int i = 0; i < 4; i++) {
                assertThat(u.enregistrerEchecSecondFacteur(5, INITIAL, PLAFOND, MAINTENANT)).isEmpty();
            }
            assertThat(u.estVerrouille(MAINTENANT)).isFalse();

            assertThat(u.enregistrerEchecSecondFacteur(5, INITIAL, PLAFOND, MAINTENANT))
                    .contains(MAINTENANT.plus(INITIAL));
            assertThat(u.estVerrouille(MAINTENANT.plusSeconds(60))).isTrue();
        }

        @Test
        @DisplayName("la durée double à chaque verrouillage, plafonnée à 24 h")
        void doublementPlafonne() {
            Utilisateur u = enrole();
            long[] minutesAttendues = {15, 30, 60, 120, 240, 480, 960, 1440, 1440};
            for (long minutes : minutesAttendues) {
                assertThat(cinqEchecs(u, MAINTENANT)).contains(MAINTENANT.plus(Duration.ofMinutes(minutes)));
            }
        }

        @Test
        @DisplayName("une connexion aboutie remet échecs et verrouillages à zéro")
        void remiseAZero() {
            Utilisateur u = enrole();
            cinqEchecs(u, MAINTENANT);
            cinqEchecs(u, MAINTENANT);
            u.enregistrerEchecSecondFacteur(5, INITIAL, PLAFOND, MAINTENANT);

            u.enregistrerConnexionReussie(MAINTENANT);

            assertThat(u.getMfaEchecs()).isZero();
            assertThat(u.getMfaVerrouillages()).isZero();
            assertThat(u.estVerrouille(MAINTENANT)).isFalse();
            assertThat(cinqEchecs(u, MAINTENANT)).contains(MAINTENANT.plus(INITIAL));
        }
    }

    @Nested
    @DisplayName("enrôlement et rejeu")
    class Enrolement {

        @Test
        @DisplayName("un secret en attente n'est pas actif tant qu'un code ne l'a pas confirmé")
        void enAttente() {
            Utilisateur u = compte(TypeUtilisateur.MEMBRE);
            u.commencerEnrolement(new byte[]{1}, (short) 1);

            assertThat(u.enrolementEnCours()).isTrue();
            assertThat(u.secondFacteurActif()).isFalse();

            u.confirmerEnrolement(MAINTENANT, 7L);

            assertThat(u.secondFacteurActif()).isTrue();
            assertThat(u.getMfaDernierPas()).isEqualTo(7L);
        }

        @Test
        @DisplayName("un second facteur actif ne se remplace pas sans être retiré")
        void pasDeRemplacementSilencieux() {
            Utilisateur u = enrole();

            assertThatThrownBy(() -> u.commencerEnrolement(new byte[]{2}, (short) 1))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("le compte refuse lui-même un pas déjà consommé")
        void rejeuRefuseParLeCompte() {
            Utilisateur u = enrole();

            assertThatThrownBy(() -> u.accepterPas(100L)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> u.accepterPas(99L)).isInstanceOf(IllegalArgumentException.class);
            u.accepterPas(101L);
            assertThat(u.getMfaDernierPas()).isEqualTo(101L);
        }

        @Test
        @DisplayName("obligatoire pour le back-office, facultatif pour un membre")
        void obligation() {
            assertThat(compte(TypeUtilisateur.MEMBRE).secondFacteurObligatoire()).isFalse();
            assertThat(compte(TypeUtilisateur.ADMINISTRATEUR).secondFacteurObligatoire()).isTrue();
            assertThat(compte(TypeUtilisateur.SUPER_ADMINISTRATEUR).secondFacteurObligatoire()).isTrue();
        }

        @Test
        @DisplayName("l'anonymisation retire le secret")
        void anonymisation() {
            Utilisateur u = enrole();

            u.anonymiser("anonyme-1@supprime.invalid", "inerte", MAINTENANT);

            assertThat(u.secondFacteurActif()).isFalse();
            assertThat(u.getMfaSecretChiffre()).isNull();
        }
    }

    @Nested
    @DisplayName("premier mot de passe")
    class PremierMotDePasse {

        @Test
        @DisplayName("tout mot de passe choisi par le titulaire lève l'obligation de changement")
        void changementLeveLObligation() {
            Utilisateur u = compte(TypeUtilisateur.SUPER_ADMINISTRATEUR);
            u.exigerChangementMotDePasse();

            u.changerMotDePasse("nouvelle");

            assertThat(u.doitChangerMotDePasse()).isFalse();
        }

        @Test
        @DisplayName("la définition active le compte, vérifie l'adresse et consomme le jeton")
        void definition() {
            Utilisateur u = compte(TypeUtilisateur.ADMINISTRATEUR);
            u.preparerDefinitionMotDePasse("empreinte-jeton", MAINTENANT.plus(Duration.ofHours(24)));

            assertThat(u.motDePasseADefinir()).isTrue();
            assertThat(u.doitChangerMotDePasse()).isTrue();

            u.definirMotDePasse("choisi");

            assertThat(u.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            assertThat(u.isEmailVerifie()).isTrue();
            assertThat(u.getJetonDefinitionHache()).isNull();
            assertThat(u.doitChangerMotDePasse()).isFalse();
            assertThat(u.motDePasseADefinir()).isFalse();
        }

        @Test
        @DisplayName("le jeton expire à l'échéance")
        void expiration() {
            Utilisateur u = compte(TypeUtilisateur.ADMINISTRATEUR);
            u.preparerDefinitionMotDePasse("e", MAINTENANT);

            assertThat(u.jetonDefinitionExpire(MAINTENANT)).isFalse();
            assertThat(u.jetonDefinitionExpire(MAINTENANT.plusSeconds(1))).isTrue();
        }
    }
}
