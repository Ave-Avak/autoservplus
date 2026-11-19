package be.autoservplus.identite.service;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdresseCourriel")
class AdresseCourrielTest {

    @Test
    @DisplayName("sans espaces autour et en minuscules")
    void normaliser() {
        assertThat(AdresseCourriel.normaliser("  Marie.Dupont@Exemple.BE \t")).isEqualTo("marie.dupont@exemple.be");
        assertThat(AdresseCourriel.normaliser(null)).isEmpty();
    }

    @Test
    @DisplayName("indépendante de la langue du serveur : en turc, « I » reste « i »")
    void independanteDeLaLangue() {
        Locale avant = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat("TITI@EXEMPLE.BE".toLowerCase()).isNotEqualTo("titi@exemple.be");

            assertThat(AdresseCourriel.normaliser("TITI@EXEMPLE.BE")).isEqualTo("titi@exemple.be");
        } finally {
            Locale.setDefault(avant);
        }
    }

    @Test
    @DisplayName("reconnaît les deux contraintes de casse, et elles seules")
    void violeLaCasse() {
        assertThat(AdresseCourriel.violeLaCasse(violation("ck_utilisateur_email_minuscules"))).isTrue();
        assertThat(AdresseCourriel.violeLaCasse(violation("ck_utilisateur_email_en_attente_minuscules"))).isTrue();
        assertThat(AdresseCourriel.violeLaCasse(violation("uq_utilisateur_email"))).isFalse();
        assertThat(AdresseCourriel.violeLaCasse(new IllegalStateException("autre"))).isFalse();
    }

    static DataIntegrityViolationException violation(String contrainte) {
        return new DataIntegrityViolationException("refus", new ConstraintViolationException(
                "refus", new SQLException("refus", "23514"), contrainte));
    }
}
