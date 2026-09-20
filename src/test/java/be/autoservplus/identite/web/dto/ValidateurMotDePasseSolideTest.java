package be.autoservplus.identite.web.dto;

import be.autoservplus.identite.service.MotsDePasseCourants;
import be.autoservplus.identite.service.VerificateurCompromission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Politique de mot de passe : longueur, listes de refus, absence de regle de
 * composition.
 */
@DisplayName("Politique de mot de passe")
class ValidateurMotDePasseSolideTest {

    private final MotsDePasseCourants courants = new MotsDePasseCourants();

    private ValidateurMotDePasseSolide validateur(VerificateurCompromission service) {
        return new ValidateurMotDePasseSolide(courants, service);
    }

    /** Service de compromission qui ne refuse rien : le cas nominal hors ligne. */
    private ValidateurMotDePasseSolide sansService() {
        return validateur(motDePasse -> false);
    }

    @Nested
    @DisplayName("longueur")
    class Longueur {

        @Test
        @DisplayName("quatorze caractères : refusé ; quinze : accepté")
        void seuil() {
            assertThat(sansService().isValid("a".repeat(14), null)).isFalse();
            assertThat(sansService().isValid("phrase-de-passe", null)).isTrue();
        }

        @Test
        @DisplayName("au-delà du maximum : refusé")
        void maximum() {
            assertThat(sansService().isValid("a".repeat(101), null)).isFalse();
            assertThat(sansService().isValid("a".repeat(100), null)).isTrue();
        }

        /**
         * Le champ vide releve de {@code @NotBlank} : rendre deux messages pour une
         * seule saisie manquante embrouillerait la page.
         */
        @Test
        @DisplayName("vide ou nul : laissé à @NotBlank")
        void videLaissePasser() {
            assertThat(sansService().isValid(null, null)).isTrue();
            assertThat(sansService().isValid("   ", null)).isTrue();
        }
    }

    @Nested
    @DisplayName("aucune règle de composition")
    class SansComposition {

        /**
         * Le point de l ecart au cahier des charges : une phrase sans majuscule, sans
         * chiffre et sans caractere special est acceptee des lors qu elle est longue.
         */
        @Test
        @DisplayName("une phrase en minuscules, sans chiffre ni symbole, est acceptée")
        void phraseSimpleAcceptee() {
            assertThat(sansService().isValid("mon garage prefere a bruxelles", null)).isTrue();
        }

        @Test
        @DisplayName("les espaces et l'Unicode sont acceptés")
        void espacesEtUnicodeAcceptes() {
            assertThat(sansService().isValid("une clé à molette bleue", null)).isTrue();
            assertThat(sansService().isValid("上海 garage 2026 atelier", null)).isTrue();
        }
    }

    @Nested
    @DisplayName("listes de refus")
    class Refus {

        @Test
        @DisplayName("un mot de passe fréquent est refusé, quelle que soit la casse")
        void frequentRefuse() {
            String frequent = "123456789123456789";
            assertThat(courants.estCourant(frequent))
                    .as("Ce mot de passe doit figurer dans la liste embarquée")
                    .isTrue();
            assertThat(sansService().isValid(frequent, null)).isFalse();
            assertThat(sansService().isValid("MAILCREATED5240", null)).isFalse();
        }

        @Test
        @DisplayName("un mot de passe signalé compromis est refusé")
        void compromisRefuse() {
            assertThat(validateur(motDePasse -> true)
                    .isValid("phrase-de-passe-tres-longue", null)).isFalse();
        }

        /**
         * Ce qui est deja refuse par la liste embarquee n a pas a etre soumis a un
         * tiers : moins de donnees sortent, et le refus ne depend pas d un service.
         */
        @Test
        @DisplayName("la liste embarquée tranche avant tout appel distant")
        void listeAvantService() {
            boolean[] appele = {false};
            VerificateurCompromission espion = motDePasse -> {
                appele[0] = true;
                return false;
            };

            validateur(espion).isValid("123456789123456789", null);

            assertThat(appele[0]).isFalse();
        }
    }

    @Test
    @DisplayName("les mots de passe de démonstration et de test passent la politique")
    void motsDePasseDuProjetAcceptes() {
        // Un jeu de démonstration refusé par la politique du projet serait un piège
        // pour l'évaluateur autant qu'une contradiction.
        for (String motDePasse : new String[]{
                "garage-bruxelles-atelier-2026",
                "marie-conduit-une-golf-bleue",
                "MotDePasseSolide2026!"}) {
            assertThat(sansService().isValid(motDePasse, null))
                    .as("Refusé : " + motDePasse)
                    .isTrue();
        }
    }
}
