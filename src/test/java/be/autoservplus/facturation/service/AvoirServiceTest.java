package be.autoservplus.facturation.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.common.exception.RessourceIntrouvableException;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.support.FacturesDeTest;
import be.autoservplus.vente.domain.Commande;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Emission de la note de credit (F30) : contre-passation exacte, idempotence
 * applicative et appartenance. La continuite de la numerotation, elle, se prouve
 * contre une vraie base ({@code NumerotationAvoirIT}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AvoirService")
class AvoirServiceTest {

    private static final Instant EMISSION = Instant.parse("2026-08-22T14:30:00Z");
    private static final Instant MAINTENANT = Instant.parse("2026-08-25T09:00:00Z");

    @Mock private AvoirRepository avoirs;
    @Mock private GenerateurNumeroAvoir numeros;

    private AvoirService service;

    private Utilisateur marie;
    private Commande commande;
    private Facture facture;

    @BeforeEach
    void setUp() {
        service = new AvoirService(avoirs, numeros,
                Clock.fixed(MAINTENANT, ZoneId.of("Europe/Brussels")));
        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                EMISSION);
        facture = Facture.pourCommande("2026-0042", (short) 2026, 42, commande,
                new BigDecimal("21.00"), EMISSION);
    }

    @Test
    @DisplayName("contre-passe la facture avec un numero de la suite des avoirs")
    void contrePassation() {
        when(avoirs.findByFacture(facture)).thenReturn(Optional.empty());
        when(numeros.prochain()).thenReturn(new NumeroAvoir((short) 2026, 1, "AV-2026-0001"));
        when(avoirs.save(any(Avoir.class))).thenAnswer(i -> i.getArgument(0));

        Avoir avoir = service.contrePasser(facture, Avoir.MOTIF_RETRACTATION);

        // Numero de la suite des AVOIRS, distincte de celle des factures : « 2026-0042 »
        // pour la facture, « AV-2026-0001 » pour l avoir qui la corrige.
        assertThat(avoir.getNumero()).isEqualTo("AV-2026-0001");
        assertThat(avoir.getNumero()).isNotEqualTo(facture.getNumero());
        assertThat(avoir.getMontantHtva()).isEqualByComparingTo(facture.getMontantHtva());
        assertThat(avoir.getMontantTva()).isEqualByComparingTo(facture.getMontantTva());
        assertThat(avoir.getMontantTvac()).isEqualByComparingTo(facture.getMontantTvac());
        assertThat(avoir.getDateEmission()).isEqualTo(MAINTENANT);
    }

    @Test
    @DisplayName("idempotence : un avoir existant est retourne, aucun numero n'est consomme")
    void idempotenceApplicative() {
        Avoir existant = Avoir.contrePassant("AV-2026-0001", facture,
                Avoir.MOTIF_RETRACTATION, MAINTENANT);
        when(avoirs.findByFacture(facture)).thenReturn(Optional.of(existant));

        assertThat(service.contrePasser(facture, Avoir.MOTIF_RETRACTATION)).isSameAs(existant);

        // Consommer un numero pour rien creuserait un trou dans la suite legale.
        verify(numeros, never()).prochain();
        verify(avoirs, never()).save(any());
    }

    @Test
    @DisplayName("l'avoir d'autrui remonte comme introuvable : 404, jamais 403")
    void appartenance() {
        Avoir avoir = Avoir.contrePassant("AV-2026-0001", facture,
                Avoir.MOTIF_RETRACTATION, MAINTENANT);
        when(avoirs.findByReference(avoir.getReference())).thenReturn(Optional.of(avoir));

        assertThat(service.pourMembre(avoir.getReference(), "marie@exemple.be")).isSameAs(avoir);
        // Confirmer l existence d une note de credit a un tiers lui apprendrait au
        // passage que son titulaire s est retracte.
        assertThatThrownBy(() -> service.pourMembre(avoir.getReference(), "jean@exemple.be"))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("une reference inconnue remonte comme introuvable")
    void referenceInconnue() {
        UUID inconnue = UUID.randomUUID();
        when(avoirs.findByReference(inconnue)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pourMembre(inconnue, "marie@exemple.be"))
                .isInstanceOf(RessourceIntrouvableException.class);
    }

    @Test
    @DisplayName("annulation d'atelier : numéro de la suite des avoirs, précision gardée")
    void annulationDAtelier() {
        Facture atelier = FacturesDeTest.factureDAtelier(EMISSION);
        when(avoirs.findByFacture(atelier)).thenReturn(Optional.empty());
        when(numeros.prochain()).thenReturn(new NumeroAvoir((short) 2026, 3, "AV-2026-0003"));
        when(avoirs.save(any(Avoir.class))).thenAnswer(i -> i.getArgument(0));

        Avoir avoir = service.annulerFactureAtelier(atelier, "Erreur de quantité");

        assertThat(avoir.getNumero()).isEqualTo("AV-2026-0003");
        assertThat(avoir.getMotif()).isEqualTo(Avoir.MOTIF_ANNULATION_ATELIER);
        assertThat(avoir.getPrecision()).isEqualTo("Erreur de quantité");
        assertThat(avoir.getDateEmission()).isEqualTo(MAINTENANT);
    }

    @Test
    @DisplayName("annulation d'atelier refusée sans précision, ou sur une facture déjà annulée, sans numéro consommé")
    void annulationRefusee() {
        Facture atelier = FacturesDeTest.factureDAtelier(EMISSION);

        assertThatThrownBy(() -> service.annulerFactureAtelier(atelier, " "))
                .isInstanceOf(RefusTraduit.class)
                .extracting(e -> ((RefusTraduit) e).getCleMessage())
                .isEqualTo("admin.intervention.erreur.precision-obligatoire");
        assertThatThrownBy(() -> service.annulerFactureAtelier(atelier, "x".repeat(501)))
                .extracting(e -> ((RefusTraduit) e).getCleMessage())
                .isEqualTo("admin.intervention.erreur.precision-longueur");

        when(avoirs.findByFacture(atelier)).thenReturn(Optional.of(
                Avoir.annulantFactureAtelier("AV-2026-0002", atelier, "Erreur", MAINTENANT)));
        assertThatThrownBy(() -> service.annulerFactureAtelier(atelier, "Erreur"))
                .extracting(e -> ((RefusTraduit) e).getCleMessage())
                .isEqualTo("admin.intervention.erreur.facture-deja-annulee");
        verifyNoInteractions(numeros);
    }
}
