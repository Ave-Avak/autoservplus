package be.autoservplus.cookies.service;

import be.autoservplus.cookies.domain.PreferencesCookies;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preuve de consentement aux cookies contre un PostgreSQL reel (F25).
 *
 * <p>Ce test existe pour que le commit qui livre le modele porte lui-meme sa preuve
 * base, sans dependre du bandeau : ce qu il etablit, aucun test a doublure ne le
 * peut. Les deux finalites {@code COOKIES_ANALYTIQUE} et {@code COOKIES_MARKETING}
 * n existaient pas avant la migration V29 ; une insertion les portant aurait ete
 * rejetee par le CHECK {@code ck_consentement_type}. Qu elles s ecrivent et se
 * relisent <b>est</b> la verification que V29 a bien elargi la contrainte — et si
 * quelqu un revenait dessus, ce test tomberait, la ou le service en doublure
 * continuerait de passer.</p>
 *
 * <p>Le cas choisi est asymetrique — accorde d un cote, refuse de l autre — parce
 * qu il est le seul qu un modele a ligne unique ne saurait pas restituer : c est
 * lui qui justifie la granularite par finalite.</p>
 *
 * <p>Le comportement append-only et le parcours HTTP complet relevent de
 * {@code ConsentementCookiesIT}, livre avec le bandeau.</p>
 */
@SpringBootTest
@Testcontainers
@DisplayName("Preuve de consentement aux cookies (integration)")
class PreuveConsentementCookiesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String IP = "81.246.0.12";

    @Autowired private PreferencesCookiesService service;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private ConsentementRepository consentements;
    @Autowired private TransactionTemplate transactions;

    private Utilisateur membre() {
        String email = "preuve-cookies-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        return transactions.execute(statut -> {
            Utilisateur nouveau = new Utilisateur(email, "$2a$04$peu.importe.pour.ce.test",
                    "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            nouveau.confirmerAdresseEmail();
            return utilisateurs.saveAndFlush(nouveau);
        });
    }

    private Consentement preuveUnique(String email, TypeDocumentConsentement finalite) {
        List<Consentement> trouvees = transactions.execute(statut ->
                consentements.findByUtilisateurEmailIgnoreCaseAndTypeDocument(email, finalite));
        assertThat(trouvees).as("preuve pour %s", finalite).hasSize(1);
        return trouvees.get(0);
    }

    @Test
    @DisplayName("les deux finalites passent le CHECK de V29 et se relisent a leur etat exact")
    void preuveParFinalitePersistee() {
        Utilisateur marie = membre();

        service.enregistrer(marie.getEmail(), new PreferencesCookies(true, false), IP);

        Consentement analytique =
                preuveUnique(marie.getEmail(), TypeDocumentConsentement.COOKIES_ANALYTIQUE);
        assertThat(analytique.isAccorde()).isTrue();
        assertThat(analytique.getVersionAcceptee())
                .isEqualTo(Consentement.COOKIES_VERSION_COURANTE);
        assertThat(analytique.getAdresseIp()).isEqualTo(IP);
        assertThat(analytique.getDateConsentement()).isNotNull();

        // Le refus est ecrit, pas omis : sans cette ligne, une absence resterait
        // ambigue entre « a refuse » et « n a jamais ete interroge ».
        Consentement marketing =
                preuveUnique(marie.getEmail(), TypeDocumentConsentement.COOKIES_MARKETING);
        assertThat(marketing.isAccorde()).isFalse();

        // Un seul geste, donc un seul horodatage.
        assertThat(marketing.getDateConsentement()).isEqualTo(analytique.getDateConsentement());
    }

    @Test
    @DisplayName("les cookies strictement necessaires ne laissent aucune ligne")
    void aucunePreuvePourLesNecessaires() {
        Utilisateur marie = membre();

        service.enregistrer(marie.getEmail(), PreferencesCookies.acceptationTotale(), IP);

        List<Consentement> generiques = transactions.execute(statut -> consentements
                .findByUtilisateurEmailIgnoreCaseAndTypeDocument(marie.getEmail(),
                        TypeDocumentConsentement.COOKIES));

        assertThat(generiques).isEmpty();
    }
}
