package be.autoservplus.vente.service;

import be.autoservplus.support.FuseauxDeTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GenerateurNumeroCommande")
class GenerateurNumeroCommandeTest {

    private static String numeroAu(String instant) {
        EntityManager entityManager = mock(EntityManager.class);
        Query sequence = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(sequence);
        when(sequence.getSingleResult()).thenReturn(7L);
        // Horloge en UTC, comme en production.
        return new GenerateurNumeroCommande(entityManager,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), FuseauxDeTest.bruxelles()).prochain();
    }

    @Test
    @DisplayName("le numéro porte l'année et la séquence sur quatre chiffres")
    void forme() {
        assertThat(numeroAu("2026-08-22T10:00:00Z")).isEqualTo("CMD-2026-0007");
    }

    @Test
    @DisplayName("une commande du 1er janvier à 0 h 30 porte la nouvelle année")
    void nuitDuNouvelAn() {
        assertThat(numeroAu("2026-12-31T23:30:00Z")).isEqualTo("CMD-2027-0007");
    }
}
