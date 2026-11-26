package be.autoservplus.common.numerotation;

import be.autoservplus.support.FuseauxDeTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Forme du numéro et année retenue. La suite elle-même, sa remise à 1 et la concurrence se
 * prouvent contre la base ({@code CompteurAnnuelIT}).
 */
@DisplayName("CompteurAnnuel")
class CompteurAnnuelTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query requete = mock(Query.class);

    private String numeroAu(String instant, TypeCompteur type) {
        when(entityManager.createNativeQuery(anyString())).thenReturn(requete);
        when(requete.setParameter(anyString(), any())).thenReturn(requete);
        when(requete.getSingleResult()).thenReturn(7);
        // Horloge en UTC, comme en production.
        return new CompteurAnnuel(entityManager, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                FuseauxDeTest.bruxelles()).prochainNumero(type);
    }

    @Test
    @DisplayName("le numéro porte le préfixe du type, l'année et la suite sur quatre chiffres")
    void forme() {
        assertThat(numeroAu("2026-08-22T10:00:00Z", TypeCompteur.COMMANDE)).isEqualTo("CMD-2026-0007");
        assertThat(numeroAu("2026-08-22T10:00:00Z", TypeCompteur.INTERVENTION)).isEqualTo("INT-2026-0007");
        assertThat(numeroAu("2026-08-22T10:00:00Z", TypeCompteur.RDV)).isEqualTo("RDV-2026-0007");
    }

    @Test
    @DisplayName("un document du 1er janvier à 0 h 30 au garage porte la nouvelle année")
    void nuitDuNouvelAn() {
        assertThat(numeroAu("2026-12-31T23:30:00Z", TypeCompteur.COMMANDE)).isEqualTo("CMD-2027-0007");
        verify(requete).setParameter(eq("exercice"), eq((short) 2027));
    }

    @Test
    @DisplayName("la requête écrit dans la table du type, jamais dans une autre")
    void tableDuType() {
        numeroAu("2026-08-22T10:00:00Z", TypeCompteur.INTERVENTION);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).contains("INSERT INTO compteur_intervention")
                .doesNotContain("compteur_commande", "compteur_rdv");
    }
}
