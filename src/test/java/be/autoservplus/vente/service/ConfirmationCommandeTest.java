package be.autoservplus.vente.service;

import be.autoservplus.communication.service.DetailsConfirmationCommande;
import be.autoservplus.communication.service.PieceJointeCourriel;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.ConditionsGeneralesPdf;
import be.autoservplus.legal.service.FormulaireRetractation;
import be.autoservplus.legal.service.NoticeGarantie;
import be.autoservplus.legal.service.VersionsDocumentsService;
import be.autoservplus.legal.service.dto.TexteArchiveVue;
import be.autoservplus.reservation.service.support.FormatageRdv;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.domain.LignePanier;
import be.autoservplus.vente.repository.CommandeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Confirmation du contrat d'une commande payée (VI.46, § 7)")
class ConfirmationCommandeTest {

    private static final Instant COMMANDE = Instant.parse("2026-09-14T09:00:00Z");
    private static final Instant PAIEMENT = Instant.parse("2026-09-14T09:04:00Z");
    private static final byte[] CGV = "%PDF-cgv".getBytes();
    private static final byte[] FORMULAIRE = "%PDF-formulaire".getBytes();

    @Mock private CommandeRepository commandes;
    @Mock private ConsentementRepository consentements;
    @Mock private VersionsDocumentsService versions;
    @Mock private ConditionsGeneralesPdf conditionsGenerales;
    @Mock private FormulaireRetractation formulaire;

    private ConfirmationCommande confirmation;
    private Utilisateur marie;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        confirmation = new ConfirmationCommande(commandes, consentements, versions,
                conditionsGenerales, formulaire, messages, new NoticeGarantie());
        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        marie.setLangue(Langue.nl);
    }

    private Commande commande(boolean renonciation) {
        Commande commande = new Commande("CMD-2026-0007", marie, new BigDecimal("66.29"),
                new BigDecimal("13.92"), new BigDecimal("80.21"), COMMANDE, renonciation);
        ReflectionTestUtils.setField(commande, "id", 42L);
        commande.confirmerPaiement(PAIEMENT);
        return commande;
    }

    private static LignePanier ligne(String libelle, int quantite, String totalTvac) {
        LignePanier ligne = mock(LignePanier.class);
        when(ligne.getLibelleFige()).thenReturn(libelle);
        when(ligne.getQuantite()).thenReturn((short) quantite);
        when(ligne.totalTvac()).thenReturn(new BigDecimal(totalTvac));
        when(ligne.getTauxTva()).thenReturn(new BigDecimal("21.00"));
        return ligne;
    }

    private void cgvAcceptees(Commande commande) {
        when(consentements.findFirstByCommandeIdAndTypeDocumentOrderByDateConsentementDesc(
                42L, TypeDocumentConsentement.CGV))
                .thenReturn(Optional.of(Consentement.acceptation(marie, TypeDocumentConsentement.CGV,
                        "CGV-2026-04", "127.0.0.1", COMMANDE).pourLaCommande(42L)));
        when(conditionsGenerales.pdf("CGV-2026-04", Locale.of("nl"), "CMD-2026-0007", COMMANDE))
                .thenReturn(Optional.of(CGV));
        when(formulaire.pdf(Locale.of("nl"))).thenReturn(FORMULAIRE);
    }

    @Test
    @DisplayName("commande de pièces : lignes, totaux, CGV de la preuve et formulaire, nommés et rédigés dans la langue du membre")
    void commandeDePieces() {
        Commande commande = commande(false);
        LignePanier ligne1 = ligne("Filtre à huile", 2, "21.78");
        when(commandes.lignesDe(commande)).thenReturn(List.of(ligne1));
        when(commandes.lignesServiceDe(commande)).thenReturn(List.of());
        cgvAcceptees(commande);

        DetailsConfirmationCommande details = confirmation.composer(commande);

        assertThat(details.adresseEmail()).isEqualTo("marie@exemple.be");
        assertThat(details.langue()).isEqualTo(Langue.nl);
        assertThat(details.conclueLe()).isEqualTo(COMMANDE);
        assertThat(details.payeeLe()).isEqualTo(PAIEMENT);
        assertThat(details.lignes()).containsExactly(
                new DetailsConfirmationCommande.LigneCommandee("Filtre à huile", 2,
                        FormatageRdv.euros(new BigDecimal("21.78")), "21", null));
        assertThat(details.totalHtva()).contains("66,29");
        assertThat(details.totalTva()).contains("13,92");
        assertThat(details.totalTvac()).contains("80,21");
        assertThat(details.pieces()).isTrue();
        assertThat(details.prestations()).isFalse();
        assertThat(details.versionCgv()).isEqualTo("CGV-2026-04");
        assertThat(details.renonciation()).isNull();
        assertThat(details.piecesJointes()).extracting(PieceJointeCourriel::nomFichier)
                .containsExactly("algemene-voorwaarden-CGV-2026-04.pdf", "modelformulier-herroeping.pdf",
                        "wettelijke-garantie.pdf");
        assertThat(details.piecesJointes().get(0).contenu()).isEqualTo(CGV);
        // VI.45 § 1er, 12° : la notice de la Commission, dans la langue du membre, telle quelle.
        assertThat(details.piecesJointes().get(2).contenu())
                .isEqualTo(new NoticeGarantie().pdf(Locale.of("nl")));
        // Aucune preuve de renonciation n'est cherchée quand la case n'a pas été cochée.
        verify(consentements, never()).findFirstByCommandeIdAndTypeDocumentOrderByDateConsentementDesc(
                42L, TypeDocumentConsentement.RENONCIATION_RETRACTATION);
    }

    @Test
    @DisplayName("prestation avec renonciation : date, version et texte coché, lus par la preuve et l'archive")
    void prestationAvecRenonciation() {
        Commande commande = commande(true);
        when(commandes.lignesDe(commande)).thenReturn(List.of());
        LignePanier ligne2 = ligne("Vidange", 1, "58.43");
        when(commandes.lignesServiceDe(commande)).thenReturn(List.of(ligne2));
        cgvAcceptees(commande);
        when(consentements.findFirstByCommandeIdAndTypeDocumentOrderByDateConsentementDesc(
                42L, TypeDocumentConsentement.RENONCIATION_RETRACTATION))
                .thenReturn(Optional.of(Consentement.decision(marie,
                        TypeDocumentConsentement.RENONCIATION_RETRACTATION, "VI53-2026-02", true,
                        "127.0.0.1", COMMANDE).pourLaCommande(42L)));
        when(versions.archive(TypeDocumentVersionne.RENONCIATION_RETRACTATION, "VI53-2026-02",
                Locale.of("nl")))
                .thenReturn(Optional.of(new TexteArchiveVue("VI53-2026-02", COMMANDE, "nl",
                        "Ik vraag de onmiddellijke uitvoering.\nFacultatief.", true, List.of("nl"))));

        DetailsConfirmationCommande details = confirmation.composer(commande);

        assertThat(details.prestations()).isTrue();
        assertThat(details.pieces()).isFalse();
        assertThat(details.renonciation()).isEqualTo(new DetailsConfirmationCommande.RenonciationConfirmee(
                COMMANDE, "VI53-2026-02", "Ik vraag de onmiddellijke uitvoering."));
        // Prestations seules : pas une vente de biens, donc pas de notice (VI.45 § 1er, 12°).
        assertThat(details.piecesJointes()).extracting(PieceJointeCourriel::nomFichier)
                .containsExactly("algemene-voorwaarden-CGV-2026-04.pdf", "modelformulier-herroeping.pdf");
    }

    @Test
    @DisplayName("les montants suivent la langue du membre : point décimal pour un compte anglophone")
    void montantsDansLaLangueDuMembre() {
        marie.setLangue(Langue.en);
        Commande commande = commande(false);
        LignePanier filtres = ligne("Filtre à huile", 2, "21.78");
        when(commandes.lignesDe(commande)).thenReturn(List.of(filtres));
        when(commandes.lignesServiceDe(commande)).thenReturn(List.of());
        when(consentements.findFirstByCommandeIdAndTypeDocumentOrderByDateConsentementDesc(
                42L, TypeDocumentConsentement.CGV)).thenReturn(Optional.empty());
        when(formulaire.pdf(any())).thenReturn(FORMULAIRE);

        DetailsConfirmationCommande details = confirmation.composer(commande);

        assertThat(details.lignes().get(0).totalTvac()).startsWith("21.78");
        assertThat(details.totalHtva()).startsWith("66.29");
        assertThat(details.totalTva()).startsWith("13.92");
        assertThat(details.totalTvac()).startsWith("80.21");
    }

    @Test
    @DisplayName("sans preuve CGV rattachée : aucun texte joint à sa place, le formulaire reste")
    void sansPreuveCgv() {
        Commande commande = commande(false);
        LignePanier ligne3 = ligne("Filtre à huile", 1, "10.89");
        when(commandes.lignesDe(commande)).thenReturn(List.of(ligne3));
        when(commandes.lignesServiceDe(commande)).thenReturn(List.of());
        when(consentements.findFirstByCommandeIdAndTypeDocumentOrderByDateConsentementDesc(
                42L, TypeDocumentConsentement.CGV)).thenReturn(Optional.empty());
        when(formulaire.pdf(any())).thenReturn(FORMULAIRE);

        DetailsConfirmationCommande details = confirmation.composer(commande);

        assertThat(details.versionCgv()).isNull();
        assertThat(details.piecesJointes()).extracting(PieceJointeCourriel::nomFichier)
                .containsExactly("modelformulier-herroeping.pdf", "wettelijke-garantie.pdf");
        verify(conditionsGenerales, never()).pdf(any(), any(), any(), any());
    }

    @Test
    @DisplayName("une renonciation refusée n'est pas confirmée comme accordée")
    void renonciationRefusee() {
        Commande commande = commande(true);
        when(commandes.lignesDe(commande)).thenReturn(List.of());
        LignePanier ligne4 = ligne("Vidange", 1, "58.43");
        when(commandes.lignesServiceDe(commande)).thenReturn(List.of(ligne4));
        cgvAcceptees(commande);
        when(consentements.findFirstByCommandeIdAndTypeDocumentOrderByDateConsentementDesc(
                42L, TypeDocumentConsentement.RENONCIATION_RETRACTATION))
                .thenReturn(Optional.of(Consentement.decision(marie,
                        TypeDocumentConsentement.RENONCIATION_RETRACTATION, "VI53-2026-02", false,
                        "127.0.0.1", COMMANDE).pourLaCommande(42L)));

        assertThat(confirmation.composer(commande).renonciation()).isNull();
    }
}
