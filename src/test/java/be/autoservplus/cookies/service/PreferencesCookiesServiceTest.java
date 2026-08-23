package be.autoservplus.cookies.service;

import be.autoservplus.cookies.domain.PreferencesCookies;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ecriture de la preuve de consentement aux cookies (F25). Le comportement
 * append-only de bout en bout, contre une vraie base, est couvert par
 * {@code ConsentementCookiesIT} ; ici on verifie la regle de granularite.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PreferencesCookiesService")
class PreferencesCookiesServiceTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-23T10:00:00Z");
    private static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");
    private static final String EMAIL = "marie@exemple.be";
    private static final String IP = "81.246.0.12";

    @Mock private UtilisateurRepository utilisateurs;
    @Mock private ConsentementRepository consentements;

    private PreferencesCookiesService service;
    private Utilisateur marie;

    @BeforeEach
    void setUp() {
        service = new PreferencesCookiesService(utilisateurs, consentements,
                Clock.fixed(MAINTENANT, BRUXELLES));
        marie = new Utilisateur(EMAIL, "peu-importe", "Dupont", "Marie", TypeUtilisateur.MEMBRE);
    }

    private void compteExiste() {
        when(utilisateurs.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(marie));
    }

    private List<Consentement> preuvesEcrites() {
        ArgumentCaptor<Consentement> capture = ArgumentCaptor.forClass(Consentement.class);
        verify(consentements, org.mockito.Mockito.atLeastOnce()).save(capture.capture());
        return capture.getAllValues();
    }

    private Consentement preuve(List<Consentement> preuves, TypeDocumentConsentement finalite) {
        return preuves.stream()
                .filter(preuve -> preuve.getTypeDocument() == finalite)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aucune preuve pour " + finalite));
    }

    @Nested
    @DisplayName("granularite de la preuve")
    class Granularite {

        @Test
        @DisplayName("« Tout accepter » accorde les deux finalites")
        void toutAccepter() {
            compteExiste();

            service.enregistrer(EMAIL, PreferencesCookies.acceptationTotale(), IP);

            List<Consentement> preuves = preuvesEcrites();
            assertThat(preuves).hasSize(2);
            assertThat(preuve(preuves, TypeDocumentConsentement.COOKIES_ANALYTIQUE).isAccorde()).isTrue();
            assertThat(preuve(preuves, TypeDocumentConsentement.COOKIES_MARKETING).isAccorde()).isTrue();
        }

        @Test
        @DisplayName("« Tout refuser » ecrit bien deux preuves, refusees — un refus se prouve")
        void toutRefuser() {
            compteExiste();

            service.enregistrer(EMAIL, PreferencesCookies.refusTotal(), IP);

            List<Consentement> preuves = preuvesEcrites();
            assertThat(preuves).hasSize(2);
            assertThat(preuve(preuves, TypeDocumentConsentement.COOKIES_ANALYTIQUE).isAccorde()).isFalse();
            assertThat(preuve(preuves, TypeDocumentConsentement.COOKIES_MARKETING).isAccorde()).isFalse();
        }

        /**
         * Le cas qui justifie a lui seul une preuve par finalite : un booleen unique
         * ne saurait pas restituer ce choix la.
         */
        @Test
        @DisplayName("« Personnaliser » restitue l'etat exact de chaque case")
        void personnaliser() {
            compteExiste();

            service.enregistrer(EMAIL, new PreferencesCookies(true, false), IP);

            List<Consentement> preuves = preuvesEcrites();
            assertThat(preuve(preuves, TypeDocumentConsentement.COOKIES_ANALYTIQUE).isAccorde()).isTrue();
            assertThat(preuve(preuves, TypeDocumentConsentement.COOKIES_MARKETING).isAccorde()).isFalse();
        }

        @Test
        @DisplayName("aucune preuve n'est ecrite pour les cookies strictement necessaires")
        void aucunePreuvePourLesNecessaires() {
            compteExiste();

            service.enregistrer(EMAIL, PreferencesCookies.acceptationTotale(), IP);

            assertThat(preuvesEcrites())
                    .extracting(Consentement::getTypeDocument)
                    .containsExactlyInAnyOrder(TypeDocumentConsentement.COOKIES_ANALYTIQUE,
                            TypeDocumentConsentement.COOKIES_MARKETING);
        }
    }

    @Nested
    @DisplayName("contenu de la preuve")
    class Contenu {

        @Test
        @DisplayName("horodate les deux lignes au meme instant : c'est un seul geste")
        void memeInstantPourLesDeuxLignes() {
            compteExiste();

            service.enregistrer(EMAIL, PreferencesCookies.acceptationTotale(), IP);

            assertThat(preuvesEcrites())
                    .extracting(Consentement::getDateConsentement)
                    .containsOnly(MAINTENANT);
        }

        @Test
        @DisplayName("conserve l'adresse IP et la version de la politique cookies")
        void adresseIpEtVersion() {
            compteExiste();

            service.enregistrer(EMAIL, PreferencesCookies.refusTotal(), IP);

            assertThat(preuvesEcrites()).allSatisfy(preuve -> {
                assertThat(preuve.getAdresseIp()).isEqualTo(IP);
                assertThat(preuve.getVersionAcceptee())
                        .isEqualTo(Consentement.COOKIES_VERSION_COURANTE);
                assertThat(preuve.getUtilisateur()).isSameAs(marie);
            });
        }
    }

    @Nested
    @DisplayName("visiteur sans compte")
    class VisiteurSansCompte {

        /**
         * Rattacher un anonyme a une ligne supposerait de l identifier, c est-a-dire
         * de collecter une donnee personnelle pour prouver qu il refuse d etre suivi.
         */
        @Test
        @DisplayName("un visiteur non connecte ne laisse aucune trace en base")
        void visiteurNonConnecte() {
            service.enregistrer(null, PreferencesCookies.refusTotal(), IP);

            verify(consentements, never()).save(any());
            verify(utilisateurs, never()).findByEmailIgnoreCase(any());
        }

        @Test
        @DisplayName("un compte disparu entre-temps n'echoue pas : le cookie a deja ete pose")
        void compteDisparu() {
            when(utilisateurs.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

            service.enregistrer(EMAIL, PreferencesCookies.refusTotal(), IP);

            verify(consentements, never()).save(any());
        }
    }

    @Nested
    @DisplayName("duree de memorisation")
    class DureeDeMemorisation {

        /**
         * Six mois calendaires depuis l horloge injectee, et non un nombre de jours
         * approche : du 23 aout au 23 fevrier, annee bissextile comprise.
         */
        @Test
        @DisplayName("memorise le choix six mois, calcules sur l'horloge injectee")
        void sixMoisCalendaires() {
            Duration attendue = Duration.between(MAINTENANT,
                    MAINTENANT.atZone(BRUXELLES).plusMonths(6).toInstant());

            assertThat(service.dureeDeMemorisation()).isEqualTo(attendue);
            assertThat(service.dureeDeMemorisation().toDays()).isEqualTo(184);
        }
    }
}
