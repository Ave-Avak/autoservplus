package be.autoservplus.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IBAN")
class IbanTest {

    @ParameterizedTest
    @ValueSource(strings = {"BE68539007547034", "BE68 5390 0754 7034", "be68 5390 0754 7034", "BE32123456789002"})
    @DisplayName("belge valide, avec ou sans espaces, quelle que soit la casse")
    void belgeValide(String iban) {
        assertThat(Iban.estValide(iban)).isTrue();
    }

    @Test
    @DisplayName("étranger valide : seule la clé ISO s'applique")
    void etrangerValide() {
        assertThat(Iban.estValide("NL91 ABNA 0417 1643 00")).isTrue();
    }

    @Test
    @DisplayName("clé ISO fausse")
    void cleIsoFausse() {
        assertThat(Iban.estValide("BE69 5390 0754 7034")).isFalse();
        assertThat(Iban.estValide("NL92 ABNA 0417 1643 00")).isFalse();
    }

    @Test
    @DisplayName("clé ISO juste mais clé nationale belge fausse")
    void cleBelgeFausse() {
        // Compte 123456789003 : la clé nationale attendue est 02. Les deux chiffres
        // ISO (05) ont été calculés sur ce compte faux, la clé ISO passe donc.
        assertThat(Iban.estValide("BE05 1234 5678 9003")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BE6853900754703", "BE685390075470344", "BE68 5390 0754 703A"})
    @DisplayName("IBAN belge de longueur fausse ou portant une lettre dans le compte")
    void belgeHorsForme(String iban) {
        assertThat(Iban.estValide(iban)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "68BE53900754703", "BE68-5390-0754-7034"})
    @DisplayName("absent, vide ou hors de la forme ISO")
    void horsForme(String iban) {
        assertThat(Iban.estValide(iban)).isFalse();
    }
}
