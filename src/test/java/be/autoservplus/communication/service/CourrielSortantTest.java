package be.autoservplus.communication.service;

import be.autoservplus.config.IdentiteGarage;
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
                parametres, new LiensCourriel("http://localhost:8080/"));
        marie = new Utilisateur("marie@exemple.be", "$2a$12$h", "Dupont", "Marie",
                TypeUtilisateur.MEMBRE);
    }

    /** Les vingt-sept envois du contrat, pour un destinataire de la langue donnée. */
    private Map<String, Consumer<Langue>> tousLesEnvois() {
        DetailsRdvCourriel rdv = new DetailsRdvCourriel("RDV-2026-0001", DIMANCHE);
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
                Map.entry("rdv-relance-garage", l -> courriel.envoyerRelanceAuGarage(
                        rdv, "Marie Dupont", DIMANCHE.minusDays(1).withHour(12), "/admin/rendez-vous/r")),
                Map.entry("depassement", l -> courriel.envoyerDemandeValidationDepassement(marie, rdv,
                        new DetailsDepassementCourriel("INT-2026-0001", "100,00 €", "150,00 €",
                                List.of("- Filtre x1 : 50,00 €"), "/mes-interventions/r/validation"))),
                Map.entry("intervention-terminee", l -> courriel.envoyerInterventionTerminee(
                        new DetailsInterventionTerminee("marie@exemple.be", "Marie", l,
                                "INT-2026-0001", "VW Golf", "1-ABC-123", "/mes-avis/r/nouveau"))),
                Map.entry("paiement-confirme", l -> courriel.envoyerConfirmationPaiement(
                        new DetailsPaiementCourriel("marie@exemple.be", "Marie", l,
                                "CMD-2026-0001", "49,00 €"))),
                Map.entry("retractation-acceptee", l -> courriel.envoyerDecisionRetractation(
                        new DetailsRetractationCourriel("marie@exemple.be", "Marie", l,
                                "CMD-2026-0001", "49,00 €", true, "AV-2026-0001", null))),
                Map.entry("retractation-refusee", l -> courriel.envoyerDecisionRetractation(
                        new DetailsRetractationCourriel("marie@exemple.be", "Marie", l,
                                "CMD-2026-0001", null, false, null, "Pièce montée"))),
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
        assertThat(envois).hasSize(27);

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
        assertThat(parti.pieceJointe()).isSameAs(agenda);
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

        assertThat(partis.get(0).pieceJointe()).isSameAs(agenda);
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
