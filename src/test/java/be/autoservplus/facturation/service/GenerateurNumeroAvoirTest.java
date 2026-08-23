package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.CompteurAvoir;
import be.autoservplus.facturation.repository.CompteurAvoirRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * Attribution du numero d avoir : format, exercice, et ordre creation puis verrou.
 * La continuite reelle de la suite se prouve contre une vraie base
 * ({@code NumerotationAvoirIT}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GenerateurNumeroAvoir")
class GenerateurNumeroAvoirTest {

    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    @Mock private CompteurAvoirRepository compteurs;

    private GenerateurNumeroAvoir generateur(String instant) {
        return new GenerateurNumeroAvoir(compteurs,
                Clock.fixed(Instant.parse(instant), BRUXELLES));
    }

    @Test
    @DisplayName("format AV-ANNEE-NNNN : le prefixe distingue la suite de celle des factures")
    void format() {
        CompteurAvoir compteur = new CompteurAvoir((short) 2026);
        when(compteurs.verrouillerParExercice((short) 2026)).thenReturn(Optional.of(compteur));
        when(compteurs.saveAndFlush(any(CompteurAvoir.class))).thenAnswer(i -> i.getArgument(0));

        NumeroAvoir numero = generateur("2026-08-25T09:00:00Z").prochain();

        // Sans le prefixe, « 2026-0001 » designerait a la fois la premiere facture
        // et le premier avoir de l annee : les deux suites repartent a 1.
        assertThat(numero.valeur()).isEqualTo("AV-2026-0001");
        assertThat(numero.exercice()).isEqualTo((short) 2026);
        assertThat(numero.sequenceAnnuelle()).isEqualTo(1);
    }

    @Test
    @DisplayName("la suite s'incremente strictement dans l'exercice")
    void suiteCroissante() {
        CompteurAvoir compteur = new CompteurAvoir((short) 2026);
        when(compteurs.verrouillerParExercice((short) 2026)).thenReturn(Optional.of(compteur));
        when(compteurs.saveAndFlush(any(CompteurAvoir.class))).thenAnswer(i -> i.getArgument(0));
        GenerateurNumeroAvoir generateur = generateur("2026-08-25T09:00:00Z");

        assertThat(generateur.prochain().valeur()).isEqualTo("AV-2026-0001");
        assertThat(generateur.prochain().valeur()).isEqualTo("AV-2026-0002");
        assertThat(generateur.prochain().valeur()).isEqualTo("AV-2026-0003");
    }

    @Test
    @DisplayName("la ligne de l'exercice est creee AVANT d'etre verrouillee")
    void creationPuisVerrou() {
        // On ne peut pas verrouiller une ligne absente : inverser l ordre ferait
        // echouer la premiere emission de chaque annee.
        CompteurAvoir compteur = new CompteurAvoir((short) 2027);
        when(compteurs.verrouillerParExercice((short) 2027)).thenReturn(Optional.of(compteur));
        when(compteurs.saveAndFlush(any(CompteurAvoir.class))).thenAnswer(i -> i.getArgument(0));

        generateur("2027-01-01T00:30:00Z").prochain();

        var ordre = inOrder(compteurs);
        ordre.verify(compteurs).creerSiAbsent((short) 2027);
        ordre.verify(compteurs).verrouillerParExercice((short) 2027);
    }

    @Test
    @DisplayName("le passage d'annee repart a 0001, sans traitement de bascule")
    void passageDAnnee() {
        when(compteurs.verrouillerParExercice((short) 2027))
                .thenReturn(Optional.of(new CompteurAvoir((short) 2027)));
        when(compteurs.saveAndFlush(any(CompteurAvoir.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(generateur("2027-03-04T08:00:00Z").prochain().valeur())
                .isEqualTo("AV-2027-0001");
    }

    @Test
    @DisplayName("un compteur introuvable apres creation signale l'incoherence")
    void compteurIntrouvable() {
        when(compteurs.verrouillerParExercice((short) 2026)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> generateur("2026-08-25T09:00:00Z").prochain())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2026");
    }
}
