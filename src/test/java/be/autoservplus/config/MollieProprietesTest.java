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
        @DisplayName("une cle API test_ ou live_ porte deja son profil et son mode")
        void cleApi() {
            assertThat(avec("test_abc123", null).exigeContexteOrganisation()).isFalse();
            assertThat(avec("live_abc123", null).exigeContexteOrganisation()).isFalse();
        }

        @Test
        @DisplayName("le jeton d organisation access_ exige le contexte")
        void jetonOrganisation() {
            assertThat(avec("access_abc123", "pfl_1").exigeContexteOrganisation()).isTrue();
        }

        @Test
        @DisplayName("un jeton d acces avance l exige aussi, quel que soit son prefixe")
        void jetonAvance() {
            // Mollie a unifie ses pages de jetons et propose des « jetons d acces
            // avances » dont la documentation publique ne donne aucun prefixe. Une
            // liste de prefixes acceptes aurait pris ceux-la pour des cles API et
            // omis profileId et testmode — c est-a-dire encaisse dans le mauvais mode
            // sans que rien ne le signale. La reconnaissance se fait donc par
            // exclusion de la seule forme dont le prefixe EST documente.
            assertThat(avec("adv_inconnu", "pfl_1").exigeContexteOrganisation()).isTrue();
            assertThat(avec("mollie_at_inconnu", "pfl_1").exigeContexteOrganisation()).isTrue();
            assertThat(avec("sans_prefixe_du_tout", "pfl_1").exigeContexteOrganisation())
                    .isTrue();
        }

        @Test
        @DisplayName("aucun identifiant : rien a exiger, c est le cas du repli")
        void aucunIdentifiant() {
            // Sans cette garde, le repli sur la passerelle bouchonnee refuserait de
            // demarrer faute de profil — une chaine vide n etant pas un prefixe de
            // cle API.
            assertThat(avec(null, null).exigeContexteOrganisation()).isFalse();
            assertThat(avec("  ", null).exigeContexteOrganisation()).isFalse();
        }

        @Test
        @DisplayName("les espaces autour de l identifiant sont retires")
        void jetonNettoye() {
            // Une variable d environnement copiee-collee traine volontiers un espace
            // ou un retour a la ligne ; l en-tete Authorization ne le pardonnerait pas.
            assertThat(avec("  test_abc123\n", null).jeton()).isEqualTo("test_abc123");
            // Et sans le strip, « test_... » precede d un espace cesserait d etre
            // reconnu comme une cle API : profileId partirait a tort.
            assertThat(avec("  test_abc123\n", null).exigeContexteOrganisation()).isFalse();
            assertThat(avec(" access_abc ", "pfl_1").exigeContexteOrganisation()).isTrue();
        }
    }

    @Nested
    @DisplayName("Coherence verifiee au demarrage")
    class Coherence {

        @Test
        @DisplayName("jeton d acces sans profil : refus, avec la marche a suivre")
        void jetonSansProfil() {
            // Mollie exige profileId a la creation d un paiement quand l identifiant
            // authentifie une organisation. Sans lui, l echec surviendrait au premier
            // achat d un client plutot qu au demarrage.
            assertThatThrownBy(() -> avec("access_abc123", null).verifierCoherence())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("profil-id")
                    .hasMessageContaining("MOLLIE_PROFILE_ID")
                    // Le message doit nommer les DEUX formes : un exploitant qui lit
                    // « access_... » alors que son jeton commence autrement conclurait
                    // que le diagnostic ne le concerne pas.
                    .hasMessageContaining("access_")
                    .hasMessageContaining("avance");
            assertThatThrownBy(() -> avec("access_abc123", "  ").verifierCoherence())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("jeton d acces avance sans profil : meme refus, meme marche a suivre")
        void jetonAvanceSansProfil() {
            assertThatThrownBy(() -> avec("adv_inconnu", null).verifierCoherence())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MOLLIE_PROFILE_ID");
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
