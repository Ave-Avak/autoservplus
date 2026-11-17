package be.autoservplus.vente.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SituationPaiement")
class SituationPaiementTest {

    @ParameterizedTest(name = "commande {0}, dernière tentative {1} : {2}")
    @CsvSource(nullValues = "AUCUNE", value = {
            "EN_ATTENTE_PAIEMENT, AUCUNE,    A_PAYER,  true",
            "EN_ATTENTE_PAIEMENT, INITIE,    A_PAYER,  true",
            "EN_ATTENTE_PAIEMENT, EXPIRE,    A_PAYER,  true",
            "EN_ATTENTE_PAIEMENT, EN_COURS,  EN_COURS, false",
            "EN_ATTENTE_PAIEMENT, ECHOUE,    ECHOUE,   true",
            "PAYEE,               REUSSI,    PAYEE,    false",
            "ANNULEE,             EXPIRE,    CLOSE,    false",
            "REMBOURSEE,          REMBOURSE, CLOSE,    false"
    })
    @DisplayName("la commande et sa dernière tentative disent ce que l'écran propose")
    void situation(StatutCommande commande, StatutPaiement derniere, SituationPaiement attendue,
                   boolean payable) {
        SituationPaiement situation = SituationPaiement.de(commande, derniere);

        assertThat(situation).isEqualTo(attendue);
        assertThat(situation.peutEtrePayee()).isEqualTo(payable);
    }
}
