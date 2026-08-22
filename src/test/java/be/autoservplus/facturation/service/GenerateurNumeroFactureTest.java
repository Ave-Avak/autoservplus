package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.CompteurFacture;
import be.autoservplus.facturation.repository.CompteurFactureRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Forme du numero et lecture de l exercice depuis l horloge injectee. Les
 * proprietes qui ne se prouvent que contre une vraie base — verrou, absence de
 * trou au rollback, concurrence — sont couvertes par {@code NumerotationFactureIT}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GenerateurNumeroFacture")
class GenerateurNumeroFactureTest {

    @Mock private CompteurFactureRepository compteurs;

    private GenerateurNumeroFacture generateurAu(String instant) {
        return new GenerateurNumeroFacture(compteurs,
                Clock.fixed(Instant.parse(instant), ZoneId.of("Europe/Brussels")));
    }

    private void compteurExistant(short exercice, int dernierNumero) {
        CompteurFacture compteur = new CompteurFacture(exercice);
        for (int i = 0; i < dernierNumero; i++) {
            compteur.consommerProchainNumero();
        }
        when(compteurs.verrouillerParExercice(exercice)).thenReturn(Optional.of(compteur));
    }

    @Test
    @DisplayName("la premiere facture d'un exercice porte le numero ANNEE-0001")
    void premiereFactureDeLExercice() {
        compteurExistant((short) 2026, 0);

        NumeroFacture numero = generateurAu("2026-08-22T10:00:00Z").prochain();

        assertThat(numero.valeur()).isEqualTo("2026-0001");
        assertThat(numero.exercice()).isEqualTo((short) 2026);
        assertThat(numero.sequenceAnnuelle()).isEqualTo(1);
    }

    @Test
    @DisplayName("le numero suit le dernier attribue, sur quatre chiffres")
    void numeroSuivant() {
        compteurExistant((short) 2026, 41);

        assertThat(generateurAu("2026-08-22T10:00:00Z").prochain().valeur())
                .isEqualTo("2026-0042");
    }

    @Test
    @DisplayName("la ligne de compteur de l'exercice est creee avant d'etre verrouillee")
    void creeLeCompteurAvantDeVerrouiller() {
        compteurExistant((short) 2026, 0);

        generateurAu("2026-08-22T10:00:00Z").prochain();

        // Sans cette creation prealable, il n y aurait aucune ligne a verrouiller
        // pour la toute premiere facture de l annee.
        verify(compteurs).creerSiAbsent((short) 2026);
    }

    @Test
    @DisplayName("l'exercice vient de l'horloge injectee : un 31 decembre reste sur l'annee courante")
    void exerciceLuDepuisLHorloge() {
        // 23h30 UTC le 31/12 = 00h30 le 01/01 a Bruxelles : c'est le fuseau de
        // l horloge qui tranche l exercice, pas UTC.
        compteurExistant((short) 2027, 0);

        assertThat(generateurAu("2026-12-31T23:30:00Z").prochain().valeur())
                .isEqualTo("2027-0001");
    }

    @Test
    @DisplayName("un compteur introuvable apres creation est une anomalie, pas un numero improvise")
    void refuseUnCompteurIntrouvable() {
        when(compteurs.verrouillerParExercice(anyShort())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> generateurAu("2026-08-22T10:00:00Z").prochain())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2026");
    }
}
