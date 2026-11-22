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
    void situationAvantLEcheance(StatutCommande commande, StatutPaiement derniere,
                                 SituationPaiement attendue, boolean payable) {
        situation(commande, derniere, attendue, payable);
        assertThat(SituationPaiement.de(commande, derniere, false)).isEqualTo(attendue);
    }

    @ParameterizedTest(name = "échéance passée, commande {0}, dernière tentative {1} : {2}")
    @CsvSource(nullValues = "AUCUNE", value = {
            "EN_ATTENTE_PAIEMENT, AUCUNE,    DELAI_DEPASSE, false",
            "EN_ATTENTE_PAIEMENT, INITIE,    A_PAYER,       true",
            "EN_ATTENTE_PAIEMENT, EXPIRE,    DELAI_DEPASSE, false",
            "EN_ATTENTE_PAIEMENT, EN_COURS,  EN_COURS,      false",
            "EN_ATTENTE_PAIEMENT, ECHOUE,    DELAI_DEPASSE, false",
            "PAYEE,               REUSSI,    PAYEE,         false",
            "ANNULEE,             ECHOUE,    CLOSE,         false"
    })
    @DisplayName("échéance passée : seule une tentative ouverte se reprend, et rien ne se dit échoué")
    void situationApresLEcheance(StatutCommande commande, StatutPaiement derniere,
                                 SituationPaiement attendue, boolean payable) {
        SituationPaiement situation = SituationPaiement.de(commande, derniere, true);

        assertThat(situation).isEqualTo(attendue);
        assertThat(situation.peutEtrePayee()).isEqualTo(payable);
    }

    private static void situation(StatutCommande commande, StatutPaiement derniere,
                                  SituationPaiement attendue, boolean payable) {
        SituationPaiement situation = SituationPaiement.de(commande, derniere);

        assertThat(situation).isEqualTo(attendue);
        assertThat(situation.peutEtrePayee()).isEqualTo(payable);
    }
}
