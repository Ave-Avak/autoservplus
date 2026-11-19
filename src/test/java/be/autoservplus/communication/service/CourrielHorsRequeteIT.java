package be.autoservplus.communication.service;

import be.autoservplus.common.web.IdentifiantRequeteFiltre;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.PiegeAntiBot;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Les courriels qui trahiraient l'existence d'un compte partent d'un autre fil que celui de
 * la requête, avec l'identifiant de cette requête.
 *
 * <p>Le transport est observé au moment où il remet le courriel : le fil qui l'exécute et
 * l'identifiant présent dans son MDC sont relevés, puis comparés à ceux de la requête. Un
 * envoi resté dans le fil de la requête ferait échouer le premier contrôle, un MDC non
 * recopié le second.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Courriels envoyés hors de la requête (intégration)")
class CourrielHorsRequeteIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test hors requete";
    private static final Duration ATTENTE = Duration.ofSeconds(10);

    private record Remise(String type, String adresse, String fil, String requete) { }

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
                    Thread.currentThread().getName(), MDC.get(IdentifiantRequeteFiltre.CLE)));
            return appel.callRealMethod();
        }).when(transport).transmettre(any());
    }

    private Utilisateur membre() {
        Utilisateur u = new Utilisateur("hors-requete-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom", TypeUtilisateur.MEMBRE);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.saveAndFlush(u);
    }

    private Remise remise(String type, String adresse) {
        await().atMost(ATTENTE).until(() -> remises.stream()
                .anyMatch(r -> r.type().equals(type) && r.adresse().equals(adresse)));
        return remises.stream().filter(r -> r.type().equals(type) && r.adresse().equals(adresse))
                .findFirst().orElseThrow();
    }

    private static String identifiant(MvcResult requete) {
        return (String) requete.getRequest().getAttribute(IdentifiantRequeteFiltre.ATTRIBUT);
    }

    @Test
    @DisplayName("mot de passe oublié : le lien part d'un fil de l'exécuteur, avec l'identifiant de la requête")
    void reinitialisation() throws Exception {
        Utilisateur membre = membre();

        MvcResult demande = mvc.perform(post("/mot-de-passe/oublie").with(csrf())
                .param("email", membre.getEmail())
                .param(PiegeAntiBot.CHAMP_HORODATAGE, String.valueOf(System.currentTimeMillis()
                        - PiegeAntiBot.DELAI_MINIMAL.plusSeconds(7).toMillis())))
                .andReturn();

        Remise remise = remise("reinitialisation", membre.getEmail());
        assertThat(remise.fil()).startsWith("courriel-");
        assertThat(identifiant(demande)).matches("[0-9a-f]{8}");
        assertThat(remise.requete()).isEqualTo(identifiant(demande));
    }

    @Test
    @DisplayName("cinquième mot de passe faux : l'avis de verrouillage part d'un fil de l'exécuteur")
    void avisDeVerrouillage() throws Exception {
        Utilisateur membre = membre();
        MvcResult cinquieme = null;
        for (int i = 0; i < 5; i++) {
            cinquieme = mvc.perform(post("/connexion").with(csrf())
                    .param("email", membre.getEmail()).param("password", "faux " + i)).andReturn();
        }

        Remise remise = remise("verrouillage-connexion", membre.getEmail());
        assertThat(remise.fil()).startsWith("courriel-");
        assertThat(remise.requete()).isEqualTo(identifiant(cinquieme));
    }
}
