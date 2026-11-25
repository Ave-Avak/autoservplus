package be.autoservplus.facturation.service;

import be.autoservplus.common.exception.RefusTraduit;
import be.autoservplus.facturation.domain.Avoir;
import be.autoservplus.facturation.domain.Encaissement;
import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.domain.MoyenEncaissement;
import be.autoservplus.facturation.repository.AvoirRepository;
import be.autoservplus.facturation.repository.EncaissementRepository;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.support.FacturesDeTest;
import be.autoservplus.support.FuseauxDeTest;
import be.autoservplus.vente.domain.Commande;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ce que l'encaissement doit aux autres lignes : origine, note de crédit, règlement existant. */
@ExtendWith(MockitoExtension.class)
@DisplayName("EncaissementService")
class EncaissementServiceTest {

    /** Le 24 novembre à 23 h 30 en UTC, déjà le 25 à 0 h 30 au garage, à Bruxelles. */
    private static final Instant EMISSION = Instant.parse("2026-11-24T23:30:00Z");
    private static final Instant MAINTENANT = Instant.parse("2026-11-25T09:00:00Z");

    @Mock private EncaissementRepository encaissements;
    @Mock private AvoirRepository avoirs;

    private EncaissementService service;
    private Facture facture;

    @BeforeEach
    void setUp() {
        service = new EncaissementService(encaissements, avoirs, FuseauxDeTest.bruxelles(),
                Clock.fixed(MAINTENANT, ZoneOffset.UTC), new BigDecimal("3000.00"));
        facture = FacturesDeTest.factureDAtelier(EMISSION);
    }

    private static String cleDu(Throwable refus) {
        return ((RefusTraduit) refus).getCleMessage();
    }

    @Test
    @DisplayName("enregistre le total de la facture, l'émission lue au jour du garage et non en UTC")
    void enregistre() {
        when(avoirs.findByFacture(facture)).thenReturn(Optional.empty());
        when(encaissements.findByFactureAndAnnuleLeIsNull(facture)).thenReturn(Optional.empty());

        // Au garage, la facture est du 25 : le 24, admis en UTC, précède son émission.
        assertThatThrownBy(() -> service.enregistrer(facture, MoyenEncaissement.TERMINAL, LocalDate.of(2026, 11, 24)))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.date-avant-emission"));
        Encaissement encaissement = service.enregistrer(facture, MoyenEncaissement.TERMINAL, LocalDate.of(2026, 11, 25));

        assertThat(encaissement.getMontant()).isEqualByComparingTo("121.00");
        verify(encaissements).saveAndFlush(encaissement);
    }

    @Test
    @DisplayName("facture de commande, facture annulée, facture déjà réglée : refusées sans écriture")
    void refus() {
        Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        Facture deCommande = Facture.pourCommande("2026-0042", (short) 2026, 42,
                new Commande("CMD-2026-0001", marie, new BigDecimal("100.00"), new BigDecimal("21.00"),
                        new BigDecimal("121.00"), EMISSION), new BigDecimal("21.00"), EMISSION);
        LocalDate jour = LocalDate.of(2026, 11, 25);

        assertThatThrownBy(() -> service.enregistrer(deCommande, MoyenEncaissement.TERMINAL, jour))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.facture-atelier"));

        when(avoirs.findByFacture(facture)).thenReturn(Optional.of(
                Avoir.annulantFactureAtelier("AV-2026-0001", facture, "Erreur", MAINTENANT)));
        assertThatThrownBy(() -> service.enregistrer(facture, MoyenEncaissement.TERMINAL, jour))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.facture-annulee"));

        when(avoirs.findByFacture(facture)).thenReturn(Optional.empty());
        when(encaissements.findByFactureAndAnnuleLeIsNull(facture)).thenReturn(Optional.of(
                Encaissement.enregistrer(facture, MoyenEncaissement.ESPECES, new BigDecimal("121.00"), jour,
                        jour.minusDays(1), jour, new BigDecimal("3000.00"))));
        assertThatThrownBy(() -> service.enregistrer(facture, MoyenEncaissement.TERMINAL, jour))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.deja-reglee"));
        verify(encaissements, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("deux enregistrements simultanés : l'index refuse le second, traduit en facture déjà réglée")
    void collision() {
        when(avoirs.findByFacture(facture)).thenReturn(Optional.empty());
        when(encaissements.findByFactureAndAnnuleLeIsNull(facture)).thenReturn(Optional.empty());
        when(encaissements.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_encaissement_facture_actif"));

        assertThatThrownBy(() -> service.enregistrer(facture, MoyenEncaissement.TERMINAL, LocalDate.of(2026, 11, 25)))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.deja-reglee"));
    }

    @Test
    @DisplayName("annulation : sans encaissement, refusée, sinon posée avec l'auteur et l'instant")
    void annulation() {
        when(encaissements.findByFactureAndAnnuleLeIsNull(facture)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.annuler(facture, "Erreur", "admin@garage.be"))
                .satisfies(e -> assertThat(cleDu(e)).isEqualTo("encaissement.erreur.aucun"));

        LocalDate jour = LocalDate.of(2026, 11, 25);
        Encaissement encaissement = Encaissement.enregistrer(facture, MoyenEncaissement.ESPECES,
                new BigDecimal("121.00"), jour, jour, jour, new BigDecimal("3000.00"));
        when(encaissements.findByFactureAndAnnuleLeIsNull(facture)).thenReturn(Optional.of(encaissement));

        service.annuler(facture, "Saisi deux fois", "admin@garage.be");

        assertThat(encaissement.getAnnuleLe()).isEqualTo(MAINTENANT);
        assertThat(encaissement.getAnnulePar()).isEqualTo("admin@garage.be");
    }
}
