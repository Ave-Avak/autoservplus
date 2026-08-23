package be.autoservplus.facturation.domain;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.vente.domain.Commande;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Note de credit (F30) : contre-passation exacte de la facture, montants positifs,
 * et exercice relu du numero.
 */
@DisplayName("Avoir")
class AvoirTest {

    private static final Instant EMISSION = Instant.parse("2026-08-22T14:30:00Z");
    private static final Instant CONTRE_PASSATION = Instant.parse("2026-08-25T09:00:00Z");

    private Facture facture;

    @BeforeEach
    void setUp() {
        Utilisateur marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
        Commande commande = new Commande("CMD-2026-0001", marie,
                new BigDecimal("39.98"), new BigDecimal("8.40"), new BigDecimal("48.38"),
                EMISSION);
        facture = Facture.pourCommande("2026-0042", (short) 2026, 42, commande,
                new BigDecimal("21.00"), EMISSION);
    }

    @Test
    @DisplayName("contre-passe la facture au centime, en montants POSITIFS")
    void contrePassationExacte() {
        Avoir avoir = Avoir.contrePassant("AV-2026-0001", facture,
                Avoir.MOTIF_RETRACTATION, CONTRE_PASSATION);

        assertThat(avoir.getMontantHtva()).isEqualByComparingTo("39.98");
        assertThat(avoir.getMontantTva()).isEqualByComparingTo("8.40");
        assertThat(avoir.getMontantTvac()).isEqualByComparingTo("48.38");
        // Le sens du document est porte par sa nature, pas par le signe : le CHECK
        // ck_avoir_montants du socle exige d ailleurs des montants positifs.
        assertThat(avoir.getMontantTvac()).isPositive();
        assertThat(avoir.getFacture()).isSameAs(facture);
        assertThat(avoir.getDateEmission()).isEqualTo(CONTRE_PASSATION);
        assertThat(avoir.getReference()).isNotNull();
    }

    @Test
    @DisplayName("le motif est stocke sous forme stable, non traduite")
    void motifNonTraduit() {
        Avoir avoir = Avoir.contrePassant("AV-2026-0001", facture,
                Avoir.MOTIF_RETRACTATION, CONTRE_PASSATION);

        // Ranger ici une phrase francaise donnerait une note de credit francaise a
        // un client neerlandophone : le PDF traduit a l impression.
        assertThat(avoir.getMotif()).isEqualTo("RETRACTATION_F30");
    }

    @Test
    @DisplayName("l'exercice se relit du numero, pas de la date d'emission")
    void exerciceLuDuNumero() {
        // Deduire l annee de date_emission dependrait du fuseau de conversion et
        // pourrait basculer d un exercice a l autre dans la nuit du 31 decembre.
        assertThat(Avoir.contrePassant("AV-2026-0001", facture, Avoir.MOTIF_RETRACTATION,
                CONTRE_PASSATION).exercice()).isEqualTo((short) 2026);
        assertThat(Avoir.contrePassant("AV-2027-0113", facture, Avoir.MOTIF_RETRACTATION,
                CONTRE_PASSATION).exercice()).isEqualTo((short) 2027);
    }

    @Test
    @DisplayName("l'archivage du PDF est la seule mutation admise apres emission")
    void archivage() {
        Avoir avoir = Avoir.contrePassant("AV-2026-0001", facture,
                Avoir.MOTIF_RETRACTATION, CONTRE_PASSATION);
        assertThat(avoir.estArchive()).isFalse();

        avoir.archiverPdf("2026/avoirs/AV-2026-0001.pdf");

        assertThat(avoir.estArchive()).isTrue();
        assertThat(avoir.getCheminPdf()).isEqualTo("2026/avoirs/AV-2026-0001.pdf");
    }

    @Test
    @DisplayName("refuse une facture, un numero ou une date absents")
    void argumentsObligatoires() {
        assertThatThrownBy(() -> Avoir.contrePassant("AV-2026-0001", null,
                Avoir.MOTIF_RETRACTATION, CONTRE_PASSATION))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Avoir.contrePassant(null, facture,
                Avoir.MOTIF_RETRACTATION, CONTRE_PASSATION))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Avoir.contrePassant("AV-2026-0001", facture,
                Avoir.MOTIF_RETRACTATION, null))
                .isInstanceOf(NullPointerException.class);
    }
}
