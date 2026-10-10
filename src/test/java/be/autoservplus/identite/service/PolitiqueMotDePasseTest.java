package be.autoservplus.identite.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.identite.service.PolitiqueMotDePasse.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Politique de mot de passe : bornes en caracteres et en octets, refus traduits.
 * Les listes de refus sont couvertes par {@code ValidateurMotDePasseSolideTest}.
 */
@DisplayName("Politique de mot de passe")
class PolitiqueMotDePasseTest {

    private final PolitiqueMotDePasse politique =
            new PolitiqueMotDePasse(new MotsDePasseCourants(), motDePasse -> false);

    /** Phrase hors liste de refus, a la longueur exacte demandee. */
    private static String phrase(int longueur) {
        return "atelier du garage de la rue haute ".repeat(5).substring(0, longueur);
    }

    private static int octets(String texte) {
        return texte.getBytes(StandardCharsets.UTF_8).length;
    }

    @Test
    @DisplayName("72 octets : acceptés")
    void soixanteDouzeOctetsAcceptes() {
        String limite = phrase(72);
        assertThat(octets(limite)).isEqualTo(72);
        assertThat(politique.evaluer(limite)).isEqualTo(Verdict.CONFORME);
    }

    @Test
    @DisplayName("73 octets : refusés pour longueur")
    void soixanteTreizeOctetsRefuses() {
        String depasse = phrase(73);
        assertThat(octets(depasse)).isEqualTo(73);
        assertThat(politique.evaluer(depasse)).isEqualTo(Verdict.TROP_LONG);
    }

    /**
     * Le cas que la limite en caracteres laissait passer : quarante caracteres, sous
     * l ancien maximum de cent, mais quatre-vingts octets, que BCrypt refuse.
     */
    @Test
    @DisplayName("40 caractères accentués dépassant 72 octets : refusés, sans exception")
    void accentuesRefusesSansException() {
        String accentue = "é".repeat(40);
        assertThat(accentue).hasSize(40);
        assertThat(octets(accentue)).isEqualTo(80);

        assertThat(politique.evaluer(accentue)).isEqualTo(Verdict.TROP_LONG);
        assertThatThrownBy(() -> politique.exiger(accentue))
                .isInstanceOfSatisfying(RefusTraduit.class, refus -> {
                    assertThat(refus.getCleMessage()).isEqualTo("validation.motDePasse.tropLong");
                    assertThat(refus.getCodeRegle()).isEqualTo("RM-02");
                });
    }

    @Test
    @DisplayName("quatorze caractères refusés, quinze acceptés")
    void minimum() {
        assertThat(politique.evaluer(phrase(14))).isEqualTo(Verdict.REFUSE);
        assertThat(politique.evaluer(phrase(15))).isEqualTo(Verdict.CONFORME);
        assertThatThrownBy(() -> politique.exiger(phrase(14)))
                .isInstanceOfSatisfying(RefusTraduit.class, refus ->
                        assertThat(refus.getCleMessage()).isEqualTo("validation.motDePasse.politique"));
        assertThatCode(() -> politique.exiger(phrase(15))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nul : refusé, sans exception")
    void nul() {
        assertThat(politique.evaluer(null)).isEqualTo(Verdict.REFUSE);
    }
}
