package be.autoservplus.identite.web;

import be.autoservplus.communication.service.Courriel;
import be.autoservplus.communication.service.TransportCourriel;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.PiegeAntiBot;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * L'inscription ne dit pas si l'adresse a déjà un compte (F1, RM-01).
 *
 * <p>Même statut et même redirection pour une adresse libre et pour l'adresse d'un
 * compte, seule l'adresse saisie passant en attribut flash. Le titulaire existant reçoit
 * un courriel selon le statut de son compte, parti d'un fil de l'exécuteur comme le lien
 * d'une inscription neuve.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Inscription sans oracle d'énumération (intégration)")
class InscriptionSansOracleIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe de l inscription neutre";
    private static final Duration ATTENTE = Duration.ofSeconds(10);

    private record Remise(String type, String adresse, String fil) { }

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;
    @MockitoSpyBean private TransportCourriel transport;

    private final List<Remise> remises = new CopyOnWriteArrayList<>();

    @BeforeEach
    void observer() {
        doAnswer(appel -> {
            Courriel courriel = appel.getArgument(0);
            remises.add(new Remise(courriel.type(), courriel.adresse(),
                    Thread.currentThread().getName()));
            return appel.callRealMethod();
        }).when(transport).transmettre(any());
    }

    private static String adresse() {
        return "inscription-neutre-" + COMPTEUR.getAndIncrement() + "@exemple.be";
    }

    private Utilisateur compte(StatutUtilisateur statut) {
        Utilisateur u = new Utilisateur(adresse(), encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom",
                TypeUtilisateur.MEMBRE);
        if (statut != StatutUtilisateur.EN_ATTENTE_VALIDATION) {
            u.confirmerAdresseEmail();
        }
        u.setStatut(statut);
        return utilisateurs.saveAndFlush(u);
    }

    private MvcResult inscrire(String email, String ip) throws Exception {
        return mvc.perform(post("/inscription").with(anonymous()).with(csrf())
                .header("Accept-Language", "fr")
                .with(requete -> {
                    requete.setRemoteAddr(ip);
                    return requete;
                })
                .param(PiegeAntiBot.CHAMP_HORODATAGE, String.valueOf(System.currentTimeMillis()
                        - PiegeAntiBot.DELAI_MINIMAL.plusSeconds(7).toMillis()))
                .param("email", email).param("motDePasse", MOT_DE_PASSE)
                .param("confirmationMotDePasse", MOT_DE_PASSE)
                .param("nom", "Dupont").param("prenom", "Marie").param("langue", "fr").param("cgv", "true"))
                .andReturn();
    }

    /** La page rendue, sans l'adresse saisie ni le jeton CSRF. */
    private static String page(MvcResult resultat, String email) throws Exception {
        return resultat.getResponse().getContentAsString()
                .replace(email, "ADRESSE")
                .replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\"");
    }

    private Remise remise(String type, String email) {
        await().atMost(ATTENTE).until(() -> remises.stream()
                .anyMatch(r -> r.type().equals(type) && r.adresse().equals(email)));
        return remises.stream().filter(r -> r.type().equals(type) && r.adresse().equals(email))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("adresse libre et compte actif : même statut, même page, aucune erreur sous le champ")
    void memePage() throws Exception {
        String libre = adresse();
        Utilisateur actif = compte(StatutUtilisateur.ACTIF);

        MvcResult neuve = inscrire(libre, "203.0.113.70");
        MvcResult existante = inscrire(actif.getEmail(), "203.0.113.71");

        assertThat(existante.getResponse().getStatus()).isEqualTo(neuve.getResponse().getStatus());
        assertThat(existante.getResponse().getRedirectedUrl())
                .isEqualTo(neuve.getResponse().getRedirectedUrl())
                .isEqualTo("/inscription/confirmee");
        assertThat(page(existante, actif.getEmail())).isEqualTo(page(neuve, libre));
        assertThat((Map<String, Object>) existante.getFlashMap()).containsEntry("adresse", actif.getEmail());
        assertThat((Map<String, Object>) neuve.getFlashMap()).containsEntry("adresse", libre);
        assertThat(utilisateurs.findByEmail(libre)).isPresent();
    }

    @Test
    @DisplayName("compte actif : son titulaire est averti, hors de la requête, sans lien à jeton")
    void titulaireAverti() throws Exception {
        Utilisateur actif = compte(StatutUtilisateur.ACTIF);

        inscrire(actif.getEmail(), "203.0.113.72");

        Remise avis = remise("tentative-inscription", actif.getEmail());
        assertThat(avis.fil()).startsWith("courriel-");
        assertThat(remises).noneMatch(r -> r.adresse().equals(actif.getEmail())
                && !r.type().equals("tentative-inscription"));
    }

    @Test
    @DisplayName("compte en attente : un nouveau lien d'activation, hors de la requête")
    void compteEnAttente() throws Exception {
        Utilisateur enAttente = compte(StatutUtilisateur.EN_ATTENTE_VALIDATION);

        inscrire(enAttente.getEmail(), "203.0.113.73");

        assertThat(remise("rappel-verification", enAttente.getEmail()).fil()).startsWith("courriel-");
    }

    @Test
    @DisplayName("adresse libre : le lien de vérification part lui aussi d'un fil de l'exécuteur")
    void adresseLibre() throws Exception {
        String libre = adresse();

        inscrire(libre, "203.0.113.74");

        assertThat(remise("verification-adresse", libre).fil()).startsWith("courriel-");
    }
}
