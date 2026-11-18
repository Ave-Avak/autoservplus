package be.autoservplus.communication.service;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.config.MentionsIdentiteGarage;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.reservation.domain.ParametreAtelier;
import be.autoservplus.reservation.repository.ParametreAtelierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rédaction des courriels : chaque type, dans chaque langue, sans transaction (le
 * courriel part donc aussitôt vers le transport, ici une liste).
 */
@DisplayName("Rédaction des courriels")
class CourrielSortantTest {

    private static final ZonedDateTime DIMANCHE =
            ZonedDateTime.of(2026, 9, 13, 10, 0, 0, 0, ZoneId.of("Europe/Brussels"));

    private final List<Courriel> partis = new ArrayList<>();
    private CourrielSortant courriel;
    private Utilisateur marie;
    private ResourceBundleMessageSource messages;

    @BeforeEach
    void setUp() {
        // Même configuration que spring.messages : UTF-8, aucun repli sur la langue du
        // système, le fichier sans suffixe tenant le français.
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        IdentiteGarage garage = mock(IdentiteGarage.class);
        when(garage.enseigne()).thenReturn("Garage Test");
        when(garage.courriel()).thenReturn("atelier@exemple.be");
        ParametreAtelierRepository parametres = mock(ParametreAtelierRepository.class);
        when(parametres.courants()).thenReturn(new ParametreAtelier());
        courriel = new CourrielSortant(messages, new EnvoiApresCommit(partis::add), garage,
                parametres, new LiensCourriel("http://localhost:8080/"),
                new MentionsIdentiteGarage(garage, messages));
        this.messages = messages;
        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
    }

    /** Les trente-trois envois du contrat, pour un destinataire de la langue donnée. */
    private Map<String, Consumer<Langue>> tousLesEnvois() {
        DetailsRdvCourriel rdv = new DetailsRdvCourriel("RDV-2026-0001", DIMANCHE);
        DetailsRdvCourriel deplace = new DetailsRdvCourriel("RDV-2026-0001", DIMANCHE.plusDays(2));
        PieceJointeCourriel agenda = new PieceJointeCourriel("rdv.ics", "text/calendar",
                "BEGIN:VCALENDAR".getBytes());
        Instant fin = DIMANCHE.toInstant();
        return Map.ofEntries(
                Map.entry("verification-adresse", l -> courriel.envoyerVerificationAdresse(marie, "http://x/v")),
                Map.entry("reinitialisation", l -> courriel.envoyerReinitialisationMotDePasse(marie, "http://x/r")),
                Map.entry("rappel-verification", l -> courriel.envoyerRappelVerification(marie, "http://x/v")),
                Map.entry("rdv-confirme", l -> courriel.envoyerConfirmationRdv(marie, rdv, agenda)),
                Map.entry("rdv-refuse", l -> courriel.envoyerRefusRdv(marie, rdv, "Pièce indisponible")),
                Map.entry("rdv-annule", l -> courriel.envoyerAnnulationParLeGarage(marie, rdv, "Panne")),
                Map.entry("rdv-recu", l -> courriel.envoyerAccuseDemandeRdv(marie, rdv, DIMANCHE.minusDays(1).withHour(12))),
                Map.entry("rdv-recu-confirme", l -> courriel.envoyerAccuseRdvConfirme(marie, rdv, agenda)),
                Map.entry("rdv-avis-garage", l -> courriel.envoyerAvisNouvelleDemandeAuGarage(
                        rdv, "Marie Dupont", false, "/admin/rendez-vous/r")),
                Map.entry("rdv-avis-garage-confirme", l -> courriel.envoyerAvisNouvelleDemandeAuGarage(
                        rdv, "Marie Dupont", true, "/admin/rendez-vous/r")),
                Map.entry("rdv-rappel", l -> courriel.envoyerRappelRdv(marie, rdv)),
                Map.entry("rdv-annule-membre", l -> courriel.envoyerAnnulationParLeMembre(marie, rdv)),
                Map.entry("rdv-expire", l -> courriel.envoyerExpirationRdv(marie, rdv)),
                Map.entry("rdv-modifie", l -> courriel.envoyerModificationRdvConfirmee(marie, rdv, deplace, agenda)),
                Map.entry("rdv-modifie-en-attente", l -> courriel.envoyerModificationRdvEnAttente(
                        marie, rdv, deplace, DIMANCHE.plusDays(1).withHour(12))),
                Map.entry("rdv-deplace-garage", l -> courriel.envoyerModificationParLeGarage(
                        marie, rdv, deplace, "Pont en panne", "+32 2 000 00 00", agenda)),
                Map.entry("rdv-modifie-garage", l -> courriel.envoyerAvisModificationAuGarage(
                        rdv, deplace, "Marie Dupont", false, "/admin/rendez-vous/r")),
                Map.entry("rdv-modifie-garage-confirme", l -> courriel.envoyerAvisModificationAuGarage(
                        rdv, deplace, "Marie Dupont", true, "/admin/rendez-vous/r")),
                Map.entry("rdv-relance-garage", l -> courriel.envoyerRelanceAuGarage(
                        rdv, "Marie Dupont", DIMANCHE.minusDays(1).withHour(12), "/admin/rendez-vous/r")),
                Map.entry("depassement", l -> courriel.envoyerDemandeValidationDepassement(marie, rdv,
                        new DetailsDepassementCourriel("INT-2026-0001", "100,00 €", "150,00 €",
                                List.of("- Filtre x1 : 50,00 €"), "/mes-interventions/r/validation"))),
                Map.entry("intervention-terminee", l -> courriel.envoyerInterventionTerminee(
                        new DetailsInterventionTerminee("marie@exemple.be", "Marie", l,
                                "INT-2026-0001", "VW Golf", "1-ABC-123", "/mes-avis/r/nouveau"))),
                Map.entry("confirmation-commande", l -> courriel.envoyerConfirmationCommande(
                        confirmation(l, true, false, "CGV-2026-04", null))),
                Map.entry("commande-a-preparer", l -> courriel.envoyerAvisCommandeAuGarage(
                        new DetailsCommandeGarage("CMD-2026-0001", "49,00 €", "Marie Dupont",
                                false, true))),
                Map.entry("retractation-acceptee", l -> courriel.envoyerDecisionRetractation(
                        new DetailsRetractationCourriel("marie@exemple.be", "Marie", l,
                                "CMD-2026-0001", "49,00 €", true, "AV-2026-0001", null))),
                Map.entry("retractation-refusee", l -> courriel.envoyerDecisionRetractation(
                        new DetailsRetractationCourriel("marie@exemple.be", "Marie", l,
                                "CMD-2026-0001", null, false, null, "Pièce montée"))),
                Map.entry("retractation-recue", l -> courriel.envoyerAccuseRetractation(
                        new DetailsAccuseRetractationCourriel("marie@exemple.be", "Marie", l,
                                "Marie Dupont", "CMD-2026-0001", null, fin))),
                Map.entry("nouvelle-adresse", l -> courriel.envoyerConfirmationNouvelleAdresse(
                        new DetailsChangementEmailCourriel("neuve@exemple.be", "Marie", l,
                                "marie@exemple.be", "neuve@exemple.be"), "http://x/c")),
                Map.entry("avis-changement-adresse", l -> courriel.envoyerAvisChangementAdresse(
                        new DetailsChangementEmailCourriel("marie@exemple.be", "Marie", l,
                                "marie@exemple.be", "neuve@exemple.be"))),
                Map.entry("compte-supprime", l -> courriel.envoyerConfirmationSuppressionCompte(
                        new DetailsSuppressionCompteCourriel("marie@exemple.be", "Marie", l))),
                Map.entry("definition-mot-de-passe", l -> courriel.envoyerLienDefinitionMotDePasse(marie, "http://x/d")),
                Map.entry("verrouillage-second-facteur", l -> courriel.envoyerAvisVerrouillageSecondFacteur(marie, fin)),
                Map.entry("verrouillage-connexion", l -> courriel.envoyerAvisVerrouillageConnexion(marie, fin)),
                Map.entry("second-facteur-reinitialise", l -> courriel.envoyerAvisReinitialisationSecondFacteur(marie)),
                Map.entry("mot-de-passe-modifie", l -> courriel.envoyerAvisMotDePasseModifie(
                        marie, OrigineMotDePasse.REINITIALISATION)));
    }

    @ParameterizedTest
    @EnumSource(Langue.class)
    @DisplayName("chaque courriel se rédige entièrement, dans chaque langue")
    void tousLesTypes(Langue langue) {
        marie.setLangue(langue);
        Map<String, Consumer<Langue>> envois = tousLesEnvois();
        assertThat(envois).hasSize(34);

        envois.forEach((type, envoi) -> {
            partis.clear();
            envoi.accept(langue);
            assertThat(partis).as(type).hasSize(1);
            Courriel parti = partis.get(0);
            assertThat(parti.type()).isEqualTo(type);
            assertThat(parti.sujet()).as(type).isNotBlank();
            assertThat(parti.corps()).as(type)
                    .contains("Marie")
                    .contains("Garage Test")
                    // Ni argument laissé en place, ni apostrophe doublée restée visible.
                    .doesNotContainPattern("\\{\\d}")
                    .doesNotContain("''");
        });
    }

    @Test
    @DisplayName("l'accusé de rétractation porte le contenu de la déclaration, sa date et son heure")
    void accuseDeRetractation() {
        courriel.envoyerAccuseRetractation(new DetailsAccuseRetractationCourriel("marie@exemple.be",
                "Marie", Langue.fr, "Marie Dupont", "CMD-2026-0007", null, DIMANCHE.toInstant()));

        assertThat(partis.get(0).corps())
                .contains("Nous avons reçu votre rétractation du contrat de la commande CMD-2026-0007, "
                        + "soumise le " + DIMANCHE.getDayOfMonth() + " septembre 2026 à "
                        + String.format("%02d:%02d", DIMANCHE.getHour(), DIMANCHE.getMinute()) + ".")
                .contains("Nom : Marie Dupont")
                .contains("Adresse de confirmation : marie@exemple.be")
                .contains("Motif : non précisé");
    }

    private static final PieceJointeCourriel CGV_JOINTES =
            new PieceJointeCourriel("conditions-generales-CGV-2026-04.pdf", "application/pdf", new byte[8]);
    private static final PieceJointeCourriel FORMULAIRE =
            new PieceJointeCourriel("formulaire-retractation.pdf", "application/pdf", new byte[8]);

    private static DetailsConfirmationCommande confirmation(Langue langue, boolean pieces,
            boolean prestations, String versionCgv, DetailsConfirmationCommande.RenonciationConfirmee renonciation) {
        List<DetailsConfirmationCommande.LigneCommandee> lignes = new ArrayList<>();
        if (pieces) {
            lignes.add(new DetailsConfirmationCommande.LigneCommandee("Filtre à huile", 2, "21,78 €", "21"));
        }
        if (prestations) {
            lignes.add(new DetailsConfirmationCommande.LigneCommandee("Vidange", 1, "58,43 €", "21"));
        }
        return new DetailsConfirmationCommande("marie@exemple.be", "Marie", langue, "CMD-2026-0007",
                DIMANCHE.toInstant(), DIMANCHE.plusMinutes(5).toInstant(), lignes,
                "66,29 €", "13,92 €", "80,21 €", pieces, prestations, versionCgv, renonciation,
                versionCgv == null ? List.of(FORMULAIRE) : List.of(CGV_JOINTES, FORMULAIRE));
    }

    /** Identité réelle plutôt que simulée : la confirmation l'imprime rubrique par rubrique. */
    private void avecIdentiteComplete() {
        IdentiteGarage identite = new IdentiteGarage("Garage Test", "Garage de démonstration", "SRL",
                "Rue de l'Atelier", "12", "1000", "Bruxelles", "Belgique", "0123.456.789",
                "RPM Bruxelles", "BE0123456789", "BE68 5390 0754 7034", "+32 2 000 00 00",
                "atelier@exemple.be");
        ParametreAtelierRepository parametres = mock(ParametreAtelierRepository.class);
        when(parametres.courants()).thenReturn(new ParametreAtelier());
        courriel = new CourrielSortant(messages, new EnvoiApresCommit(partis::add), identite,
                parametres, new LiensCourriel("http://localhost:8080/"),
                new MentionsIdentiteGarage(identite, messages));
    }

    @Test
    @DisplayName("confirmation du contrat (VI.46, § 7) : vendeur, commande, retrait, rétractation, garantie, recours")
    void confirmationDuContrat() {
        avecIdentiteComplete();
        courriel.envoyerConfirmationCommande(confirmation(Langue.fr, true, false, "CGV-2026-04", null));

        Courriel parti = partis.get(0);
        assertThat(parti.type()).isEqualTo("confirmation-commande");
        assertThat(parti.sujet()).isEqualTo("Confirmation de votre commande");
        assertThat(parti.corps())
                .contains("Votre paiement de 80,21 € a été reçu le 13 septembre 2026. Ce courriel "
                        + "confirme, sur un support durable, le contrat conclu le 13 septembre 2026 pour "
                        + "la commande CMD-2026-0007 (article VI.46, § 7, du Code de droit économique).")
                .contains("Vendeur\nDénomination : Garage de démonstration\nForme juridique : SRL\n"
                        + "Nom commercial : Garage Test\n"
                        + "Adresse : Rue de l'Atelier 12, 1000 Bruxelles, Belgique\n"
                        + "Numéro d'entreprise (BCE) : 0123.456.789\n"
                        + "Registre des personnes morales : RPM Bruxelles\n"
                        + "Numéro de TVA : BE0123456789\nTéléphone : +32 2 000 00 00\n"
                        + "Courriel : atelier@exemple.be")
                .contains("Filtre à huile, quantité 2 : 21,78 € TVAC, TVA 21 %")
                .contains("Total hors TVA : 66,29 €\nTVA : 13,92 €\nTotal TVA comprise : 80,21 €")
                .contains("Les pièces sont à retirer au garage, à l'adresse ci-dessus.")
                .doesNotContain("ne réserve pas de créneau")
                .contains("figurent à l'article 7 des conditions générales de vente jointes, version "
                        + "CGV-2026-04.")
                .contains("sur la page « Mes commandes » de votre espace membre, au moyen du lien "
                        + "« Renoncer au contrat ici ».")
                .contains("garantie légale de conformité de deux ans")
                .contains("Service de médiation pour le consommateur")
                .contains("Pièces jointes : les conditions générales de vente acceptées pour cette commande")
                .doesNotContain("cochée", "coché")
                .doesNotContain("''");
        assertThat(parti.piecesJointes()).extracting(PieceJointeCourriel::nomFichier)
                .containsExactly("conditions-generales-CGV-2026-04.pdf", "formulaire-retractation.pdf");
    }

    @Test
    @DisplayName("prestation : date à convenir, renonciation VI.53 confirmée avec son texte, sans garantie des biens")
    void confirmationAvecRenonciation() {
        avecIdentiteComplete();
        courriel.envoyerConfirmationCommande(confirmation(Langue.fr, false, true, "CGV-2026-04",
                new DetailsConfirmationCommande.RenonciationConfirmee(DIMANCHE.toInstant(),
                        "VI53-2026-02", "Je demande l'exécution immédiate.")));

        assertThat(partis.get(0).corps())
                .contains("Le garage vous contacte pour convenir de la date d'exécution, dans son atelier.")
                .contains("Le 13 septembre 2026, vous avez coché la mention suivante (version "
                        + "VI53-2026-02) : « Je demande l'exécution immédiate. »")
                .doesNotContain("garantie légale de conformité");
    }

    @Test
    @DisplayName("sans CGV rattachées, le courriel ne les annonce pas jointes")
    void confirmationSansCgv() {
        courriel.envoyerConfirmationCommande(confirmation(Langue.fr, true, false, null, null));

        assertThat(partis.get(0).corps())
                .contains("conditions générales de vente publiées sur le site du garage")
                .contains("Pièce jointe : le modèle de formulaire de rétractation.")
                .doesNotContain("acceptées pour cette commande");
        assertThat(partis.get(0).piecesJointes()).hasSize(1);
    }

    @Test
    @DisplayName("néerlandais et anglais : libellés de l'écran entre guillemets de la langue")
    void confirmationTraduite() {
        courriel.envoyerConfirmationCommande(confirmation(Langue.nl, true, false, "CGV-2026-04", null));
        assertThat(partis.get(0).corps())
                .contains("op de pagina \u201cMijn bestellingen\u201d van uw ledenruimte, via de link "
                        + "\u201cHier de overeenkomst herroepen\u201d.")
                .contains("Consumentenombudsdienst");

        partis.clear();
        courriel.envoyerConfirmationCommande(confirmation(Langue.en, true, false, "CGV-2026-04", null));
        assertThat(partis.get(0).corps())
                .contains("on the \u201cMy orders\u201d page of your member area, using the "
                        + "\u201cWithdraw from contract here\u201d link.")
                .contains("Consumer Mediation Service");
    }

    @Test
    @DisplayName("l'avis au garage d'une commande payée dit ce qu'elle porte, et la rupture à honorer")
    void avisCommandeAuGarage() {
        courriel.envoyerAvisCommandeAuGarage(
                new DetailsCommandeGarage("CMD-2026-0007", "80,21 €", "Marie Dupont", true, true));

        Courriel parti = partis.get(0);
        assertThat(parti.corps())
                .contains("Marie Dupont a payé la commande CMD-2026-0007, 80,21 € TVAC. "
                        + "Elle porte des prestations à planifier avec le client.\n"
                        + "Rupture à honorer : le stock ne couvrait pas toute la commande au moment du paiement.");

        partis.clear();
        courriel.envoyerAvisCommandeAuGarage(
                new DetailsCommandeGarage("CMD-2026-0008", "12,50 €", "Marie Dupont", false, false));
        assertThat(partis.get(0).corps())
                .contains("Elle porte des pièces à préparer pour leur retrait à l'atelier.")
                .doesNotContain("Rupture");
    }

    @Test
    @DisplayName("l'accusé d'une demande en attente annonce l'échéance, dans la langue du membre")
    void accuseAnnonceLEcheance() {
        marie.setLangue(Langue.nl);
        courriel.envoyerAccuseDemandeRdv(marie, new DetailsRdvCourriel("RDV-1", DIMANCHE),
                DIMANCHE.minusDays(1).withHour(12));

        assertThat(partis.get(0).corps()).contains("uiterlijk op zaterdag 12 september 2026 om 12:00");
    }

    @Test
    @DisplayName("l'accusé d'un rendez-vous confirmé d'office porte le fichier d'agenda")
    void accuseConfirmeAvecAgenda() {
        PieceJointeCourriel agenda = new PieceJointeCourriel("rdv.ics", "text/calendar",
                "BEGIN:VCALENDAR".getBytes());
        courriel.envoyerAccuseRdvConfirme(marie, new DetailsRdvCourriel("RDV-1", DIMANCHE), agenda);

        Courriel parti = partis.get(0);
        assertThat(parti.piecesJointes()).containsExactly(agenda);
        assertThat(parti.corps()).contains("Le fichier joint l'ajoute à votre agenda.");
    }

    @Test
    @DisplayName("l'avis au garage part à l'adresse de l'atelier, en français, avec le lien du détail")
    void avisAuGarage() {
        marie.setLangue(Langue.nl);
        courriel.envoyerAvisNouvelleDemandeAuGarage(new DetailsRdvCourriel("RDV-1", DIMANCHE),
                "Marie Dupont", false, "/admin/rendez-vous/abc");

        Courriel parti = partis.get(0);
        assertThat(parti.adresse()).isEqualTo("atelier@exemple.be");
        assertThat(parti.sujet()).isEqualTo("Nouvelle demande de rendez-vous à traiter");
        assertThat(parti.corps()).contains("dimanche 13 septembre 2026")
                .contains("Marie Dupont").contains("http://localhost:8080/admin/rendez-vous/abc");
    }

    @Test
    @DisplayName("la langue du compte décide, jusqu'au nom du jour")
    void langueDuCompte() {
        marie.setLangue(Langue.nl);
        courriel.envoyerConfirmationRdv(marie, new DetailsRdvCourriel("RDV-1", DIMANCHE), null);

        Courriel parti = partis.get(0);
        assertThat(parti.sujet()).isEqualTo("Uw afspraak is bevestigd");
        assertThat(parti.corps()).startsWith("Beste Marie,")
                .contains("zondag 13 september 2026").contains("10:00")
                .doesNotContain("bijgevoegde");
    }

    @Test
    @DisplayName("la pièce jointe d'agenda est annoncée et transmise")
    void agendaJoint() {
        PieceJointeCourriel agenda = new PieceJointeCourriel("rdv.ics", "text/calendar",
                "BEGIN:VCALENDAR".getBytes());
        courriel.envoyerConfirmationRdv(marie, new DetailsRdvCourriel("RDV-1", DIMANCHE), agenda);

        assertThat(partis.get(0).piecesJointes()).containsExactly(agenda);
        assertThat(partis.get(0).corps()).contains("dimanche 13 septembre 2026")
                .contains("Le fichier joint l'ajoute à votre agenda.");
    }

    @Test
    @DisplayName("les chemins internes deviennent des liens absolus")
    void cheminsAbsolus() {
        courriel.envoyerDemandeValidationDepassement(marie,
                new DetailsRdvCourriel("RDV-1", DIMANCHE),
                new DetailsDepassementCourriel("INT-1", "1 €", "2 €", List.of("- x"),
                        "/mes-interventions/r/validation"));

        assertThat(partis.get(0).corps())
                .contains("http://localhost:8080/mes-interventions/r/validation");
    }

    @Test
    @DisplayName("l'avis de mot de passe modifié ne porte aucun lien")
    void avisSansLien() {
        courriel.envoyerAvisMotDePasseModifie(marie, OrigineMotDePasse.CHANGEMENT_IMPOSE);

        assertThat(partis.get(0).corps()).doesNotContain("http").contains("première connexion");
    }

    @Test
    @DisplayName("l'adresse visée est celle du message, pas celle du compte")
    void adresseDuMessage() {
        courriel.envoyerConfirmationNouvelleAdresse(new DetailsChangementEmailCourriel(
                "neuve@exemple.be", "Marie", Langue.fr, "marie@exemple.be", "neuve@exemple.be"),
                "http://x/c");

        assertThat(partis.get(0).adresse()).isEqualTo("neuve@exemple.be");
    }
}
