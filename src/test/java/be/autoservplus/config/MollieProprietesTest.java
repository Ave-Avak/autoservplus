package be.autoservplus.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Configuration du prestataire de paiement")
class MollieProprietesTest {

    private static MollieProprietes avec(String cle, String profil) {
        return new MollieProprietes(cle, profil, true);
    }

    @Nested
    @DisplayName("Presence d un identifiant")
    class Presence {

        @Test
        @DisplayName("absent, vide ou blanc : aucun prestataire configure")
        void absent() {
            assertThat(avec(null, null).estConfigure()).isFalse();
            assertThat(avec("", null).estConfigure()).isFalse();
            // Une variable d environnement vide se traduit souvent par des espaces :
            // les traiter comme une cle activerait la passerelle reelle sans cle.
            assertThat(avec("   ", null).estConfigure()).isFalse();
        }

        @Test
        @DisplayName("renseigne : prestataire configure")
        void present() {
            assertThat(avec("test_abc123", null).estConfigure()).isTrue();
        }
    }

    @Nested
    @DisplayName("Forme de l identifiant")
    class Forme {

        @Test
        @DisplayName("le prefixe access_ designe un jeton d organisation")
        void jetonOrganisation() {
            assertThat(avec("access_abc123", "pfl_1").estJetonOrganisation()).isTrue();
        }

        @Test
        @DisplayName("une cle API test_ ou live_ n en est pas un")
        void cleApi() {
            assertThat(avec("test_abc123", null).estJetonOrganisation()).isFalse();
            assertThat(avec("live_abc123", null).estJetonOrganisation()).isFalse();
        }

        @Test
        @DisplayName("les espaces autour de l identifiant sont retires")
        void jetonNettoye() {
            // Une variable d environnement copiee-collee traine volontiers un espace
            // ou un retour a la ligne ; l en-tete Authorization ne le pardonnerait pas.
            assertThat(avec("  test_abc123\n", null).jeton()).isEqualTo("test_abc123");
            assertThat(avec(" access_abc ", "pfl_1").estJetonOrganisation()).isTrue();
        }
    }

    @Nested
    @DisplayName("Coherence verifiee au demarrage")
    class Coherence {

        @Test
        @DisplayName("jeton d organisation sans profil : refus, avec la marche a suivre")
        void jetonSansProfil() {
            // Mollie exige profileId a la creation d un paiement quand le jeton
            // authentifie une organisation. Sans lui, l echec surviendrait au premier
            // achat d un client plutot qu au demarrage.
            assertThatThrownBy(() -> avec("access_abc123", null).verifierCoherence())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("profil-id")
                    .hasMessageContaining("MOLLIE_PROFILE_ID");
            assertThatThrownBy(() -> avec("access_abc123", "  ").verifierCoherence())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("jeton d organisation avec profil : accepte")
        void jetonAvecProfil() {
            assertThatCode(() -> avec("access_abc123", "pfl_1").verifierCoherence())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("cle API sans profil : accepte, la cle porte deja le profil")
        void cleApiSansProfil() {
            assertThatCode(() -> avec("test_abc123", null).verifierCoherence())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("aucune configuration : accepte, c est le cas du repli")
        void aucuneConfiguration() {
            assertThatCode(() -> avec(null, null).verifierCoherence())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("le message d erreur ne contient jamais l identifiant")
        void secretJamaisDivulgue() {
            // Un secret ne doit pas pouvoir atterrir dans un journal par le detour
            // d une erreur de demarrage.
            assertThatThrownBy(() -> avec("access_SECRET_A_NE_PAS_DIVULGUER", null)
                    .verifierCoherence())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining("SECRET_A_NE_PAS_DIVULGUER");
        }
    }
}
