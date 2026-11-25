package be.autoservplus.facturation.domain;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.support.FacturesDeTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Règles d'un encaissement au comptoir (RM-20 révisée, Q-H-43 à Q-H-45). */
@DisplayName("Encaissement")
class EncaissementTest {

    private static final Instant EMISSION = Instant.parse("2026-11-20T09:00:00Z");
    private static final LocalDate JOUR_EMISSION = LocalDate.of(2026, 11, 20);
    private static final LocalDate AUJOURD_HUI = LocalDate.of(2026, 11, 25);
    private static final BigDecimal PLAFOND = new BigDecimal("3000.00");

    private static Facture factureDe(String totalTvac) {
        Facture facture = FacturesDeTest.factureDAtelier(EMISSION);
        ReflectionTestUtils.setField(facture, "montantTvac", new BigDecimal(totalTvac));
        return facture;
    }

    private static Encaissement encaisser(Facture facture, MoyenEncaissement moyen, BigDecimal montant,
                                          LocalDate date) {
        return Encaissement.enregistrer(facture, moyen, montant, date, JOUR_EMISSION, AUJOURD_HUI, PLAFOND);
    }

    private static String cleDu(Throwable refus) {
        return ((RefusTraduit) refus).getCleMessage();
    }

    @Test
    @DisplayName("le total TVAC de la facture, au jour dit, par le moyen choisi")
    void enregistre() {
        Facture facture = factureDe("121.00");

        Encaissement encaissement = encaisser(facture, MoyenEncaissement.TERMINAL, new BigDecimal("121.00"),
                AUJOURD_HUI);

        assertThat(encaissement.getMontant()).isEqualByComparingTo("121.00");
        assertThat(encaissement.getMoyen()).isEqualTo(MoyenEncaissement.TERMINAL);
        assertThat(encaissement.getDateEncaissement()).isEqualTo(AUJOURD_HUI);
        assertThat(encaissement.estAnnule()).isFalse();
        assertThat(encaissement.getReference()).isNotNull();
    }

    @Test
    @DisplayName("un montant autre que le total est refusé, pas corrigé")
    void montantDifferent() {
        Facture facture = factureDe("121.00");

        assertThatThrownBy(() -> encaisser(facture, MoyenEncaissement.TERMINAL, new BigDecimal("120.99"), AUJOURD_HUI))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.montant"));
        assertThatThrownBy(() -> encaisser(facture, MoyenEncaissement.TERMINAL, null, AUJOURD_HUI))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.montant"));
    }

    @Test
    @DisplayName("une facture à 0 € n'a rien à régler (Q-H-44)")
    void rienARegler() {
        Facture facture = factureDe("0.00");

        assertThatThrownBy(() -> encaisser(facture, MoyenEncaissement.ESPECES, BigDecimal.ZERO, AUJOURD_HUI))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.rien-a-regler"));
    }

    @Test
    @DisplayName("date ni future ni antérieure à l'émission, les deux bornes admises")
    void dates() {
        Facture facture = factureDe("121.00");
        BigDecimal total = new BigDecimal("121.00");

        assertThat(encaisser(facture, MoyenEncaissement.VIREMENT, total, JOUR_EMISSION).getDateEncaissement())
                .isEqualTo(JOUR_EMISSION);
        assertThatThrownBy(() -> encaisser(facture, MoyenEncaissement.VIREMENT, total, AUJOURD_HUI.plusDays(1)))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.date-future"));
        assertThatThrownBy(() -> encaisser(facture, MoyenEncaissement.VIREMENT, total, JOUR_EMISSION.minusDays(1)))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.date-avant-emission"));
    }

    @Test
    @DisplayName("espèces : 3000,00 € TVAC admis, 3000,01 € refusé, le terminal sans plafond (Q-H-45)")
    void plafondDesEspeces() {
        Facture aLaLimite = factureDe("3000.00");
        Facture auDela = factureDe("3000.01");

        assertThat(encaisser(aLaLimite, MoyenEncaissement.ESPECES, new BigDecimal("3000.00"), AUJOURD_HUI)
                .getMoyen()).isEqualTo(MoyenEncaissement.ESPECES);
        assertThatThrownBy(() -> encaisser(auDela, MoyenEncaissement.ESPECES, new BigDecimal("3000.01"), AUJOURD_HUI))
                .satisfies(e -> {
                    assertThat(cleDu(e)).isEqualTo("encaissement.erreur.plafond-especes");
                    assertThat(((RefusTraduit) e).getArguments()).containsExactly(PLAFOND);
                });
        assertThat(encaisser(auDela, MoyenEncaissement.TERMINAL, new BigDecimal("3000.01"), AUJOURD_HUI)
                .getMoyen()).isEqualTo(MoyenEncaissement.TERMINAL);
    }

    @Test
    @DisplayName("annulation : motif obligatoire et borné, une seule fois")
    void annulation() {
        Encaissement encaissement = encaisser(factureDe("121.00"), MoyenEncaissement.ESPECES,
                new BigDecimal("121.00"), AUJOURD_HUI);
        Instant maintenant = Instant.parse("2026-11-25T10:00:00Z");

        assertThatThrownBy(() -> encaissement.annuler("  ", "admin@garage.be", maintenant))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.motif-obligatoire"));
        assertThatThrownBy(() -> encaissement.annuler("x".repeat(501), "admin@garage.be", maintenant))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.motif-longueur"));
        assertThat(encaissement.estAnnule()).isFalse();

        encaissement.annuler("  Saisi sur la mauvaise facture  ", "admin@garage.be", maintenant);

        assertThat(encaissement.estAnnule()).isTrue();
        assertThat(encaissement.getMotifAnnulation()).isEqualTo("Saisi sur la mauvaise facture");
        assertThat(encaissement.getAnnulePar()).isEqualTo("admin@garage.be");
        assertThatThrownBy(() -> encaissement.annuler("Encore", "admin@garage.be", maintenant))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.deja-annule"));
    }
}
