package be.autoservplus.facturation.service;

import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.facturation.repository.FactureRepository;
import be.autoservplus.facturation.service.dto.EspaceDocumentsVue;
import be.autoservplus.intervention.domain.Intervention;
import be.autoservplus.intervention.domain.StatutIntervention;
import be.autoservplus.intervention.repository.InterventionRepository;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import be.autoservplus.vente.domain.Commande;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EspaceDocumentsServiceTest {

    private static final String EMAIL = "marie@exemple.be";

    @Mock private FactureRepository factures;
    @Mock private AvoirRepository avoirs;
    @Mock private InterventionRepository interventions;
    @Mock private ParametreAtelierRepository parametres;

    private EspaceDocumentsService service;

    @BeforeEach
    void setUp() {
        ParametreAtelier courants = mock(ParametreAtelier.class);
        when(courants.zone()).thenReturn(ZoneId.of("Europe/Brussels"));
        when(parametres.courants()).thenReturn(courants);
        service = new EspaceDocumentsService(factures, avoirs, interventions, parametres);
    }

    @Test
    @DisplayName("aucun document : la page est vide et ne propose aucune année")
    void sansDocument() {
        when(factures.documentsDuMembre(EMAIL)).thenReturn(List.of());
        when(avoirs.avoirsDuMembre(EMAIL)).thenReturn(List.of());
        when(interventions.dossiersDuTitulaire(EMAIL)).thenReturn(List.of());

        EspaceDocumentsVue vue = service.documentsDe(EMAIL, null);

        assertThat(vue.estVide()).isTrue();
        assertThat(vue.annees()).isEmpty();
        assertThat(vue.annee()).isNull();
    }

    @Test
    @DisplayName("une facture d'atelier annulée renvoie à son intervention et à sa note de crédit")
    void factureAnnulee() {
        Intervention dossier = intervention(7L, "INT-2026-0007", "2026-11-20T09:00:00Z");
        Facture atelier = facture(1L, "2026-0042", "2026-11-25T10:00:00Z", dossier, null);
        Avoir note = avoir(atelier, "NC-2026-0001", "2026-11-26T10:00:00Z");
        when(factures.documentsDuMembre(EMAIL)).thenReturn(List.of(atelier));
        when(avoirs.avoirsDuMembre(EMAIL)).thenReturn(List.of(note));
        when(interventions.dossiersDuTitulaire(EMAIL)).thenReturn(List.<Object[]>of(
                new Object[]{7L, "1-ABC-123", "Volkswagen", "Golf"}));
        when(interventions.findAllById(anyIterable())).thenReturn(List.of(dossier));

        EspaceDocumentsVue vue = service.documentsDe(EMAIL, null);

        var ligne = vue.factures().getFirst();
        assertThat(ligne.cleOrigine()).isEqualTo("documents.origine.intervention");
        assertThat(ligne.numeroOrigine()).isEqualTo("INT-2026-0007");
        assertThat(ligne.estAnnulee()).isTrue();
        assertThat(ligne.numeroAvoir()).isEqualTo("NC-2026-0001");
        assertThat(vue.avoirs().getFirst().numeroFacture()).isEqualTo("2026-0042");
        assertThat(vue.dossiers().getFirst().vehicule()).isEqualTo("Volkswagen Golf (1-ABC-123)");
        assertThat(vue.dossiers().getFirst().date()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    @DisplayName("le filtre retient l'année demandée, et une année inconnue affiche tout")
    void filtreParAnnee() {
        Commande commande = mock(Commande.class);
        when(commande.getNumero()).thenReturn("CMD-2025-0001");
        Facture ancienne = facture(1L, "2025-0009", "2025-12-31T23:30:00Z", null, commande);
        Facture recente = facture(2L, "2026-0001", "2026-03-02T10:00:00Z", null, commande);
        when(factures.documentsDuMembre(EMAIL)).thenReturn(List.of(recente, ancienne));
        when(avoirs.avoirsDuMembre(EMAIL)).thenReturn(List.of());
        when(interventions.dossiersDuTitulaire(EMAIL)).thenReturn(List.of());

        EspaceDocumentsVue de2026 = service.documentsDe(EMAIL, 2026);
        EspaceDocumentsVue inconnue = service.documentsDe(EMAIL, 1999);

        // 31 décembre 2025 à 23 h 30 UTC : 1er janvier 2026 à 0 h 30 à Bruxelles.
        assertThat(de2026.annees()).containsExactly(2026);
        assertThat(de2026.factures()).hasSize(2);
        assertThat(de2026.factures().getFirst().cleOrigine()).isEqualTo("documents.origine.commande");
        assertThat(inconnue.annee()).isNull();
        assertThat(inconnue.factures()).hasSize(2);
    }

    private static Facture facture(Long id, String numero, String emission, Intervention dossier, Commande commande) {
        Facture facture = mock(Facture.class);
        when(facture.getId()).thenReturn(id);
        when(facture.getReference()).thenReturn(UUID.randomUUID());
        when(facture.getNumero()).thenReturn(numero);
        when(facture.getDateEmission()).thenReturn(Instant.parse(emission));
        when(facture.getIntervention()).thenReturn(dossier);
        when(facture.getCommande()).thenReturn(commande);
        when(facture.getMontantTvac()).thenReturn(new BigDecimal("117.98"));
        return facture;
    }

    private static Avoir avoir(Facture facture, String numero, String emission) {
        Avoir avoir = mock(Avoir.class);
        when(avoir.getFacture()).thenReturn(facture);
        when(avoir.getReference()).thenReturn(UUID.randomUUID());
        when(avoir.getNumero()).thenReturn(numero);
        when(avoir.getDateEmission()).thenReturn(Instant.parse(emission));
        when(avoir.getMontantTvac()).thenReturn(new BigDecimal("117.98"));
        return avoir;
    }

    private static Intervention intervention(Long id, String numero, String reference) {
        Intervention dossier = mock(Intervention.class);
        when(dossier.getId()).thenReturn(id);
        when(dossier.getReference()).thenReturn(UUID.randomUUID());
        when(dossier.getNumero()).thenReturn(numero);
        when(dossier.dateDeReference()).thenReturn(Instant.parse(reference));
        when(dossier.getStatut()).thenReturn(StatutIntervention.TERMINEE);
        return dossier;
    }
}
