package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.ChampCompte;
import be.autoservplus.identite.domain.ModificationCompte;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ModificationCompteRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Coordonnées d'un membre modifiées par l'administrateur, à la demande du membre (A12).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Modification d'un compte membre par l'administrateur (intégration)")
class AdminModificationCompteIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test";
    private static final String MOTIF = "Appel du membre le 20 novembre, déménagement.";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private ModificationCompteRepository modifications;
    @Autowired private PasswordEncoder encodeur;

    private Utilisateur membre;
    private Utilisateur admin;

    @BeforeEach
    void setUp() {
        int n = COMPTEUR.getAndIncrement();
        membre = creer("modif-membre-" + n + "@exemple.be", TypeUtilisateur.MEMBRE);
        admin = creer("modif-admin-" + n + "@exemple.be", TypeUtilisateur.ADMINISTRATEUR);
    }

    private Utilisateur creer(String email, TypeUtilisateur type) {
        Utilisateur u = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE), "Dupont", "Marie", type);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.saveAndFlush(u);
    }

    private ResultActions modifier(Utilisateur auteur, Utilisateur cible, String motif)
            throws Exception {
        return mvc.perform(post("/admin/comptes/{ref}/modification", cible.getReference())
                .with(user(auteur.getEmail()).roles(auteur.getTypeUtilisateur().name()))
                .with(csrf()).locale(Locale.FRENCH)
                .param("prenom", "Marie").param("nom", "Dupont")
                .param("rue", "Rue Haute").param("numeroRue", "4")
                .param("codePostal", "1000").param("localite", "Bruxelles")
                .param("pays", "Belgique").param("langue", "fr")
                .param("motif", motif));
    }

    @Test
    @DisplayName("la modification est enregistrée, historisée avec le motif, et visible sur la fiche")
    void modificationEnregistree() throws Exception {
        modifier(admin, membre, MOTIF)
                .andExpect(redirectedUrl("/admin/comptes/" + membre.getReference()));

        Utilisateur apres = utilisateurs.findById(membre.getId()).orElseThrow();
        assertThat(apres.getRue()).isEqualTo("Rue Haute");
        assertThat(apres.getLocalite()).isEqualTo("Bruxelles");
        assertThat(modifications.historiqueDe(membre.getId()))
                .extracting(ModificationCompte::getChamp, ModificationCompte::getMotif,
                        ModificationCompte::parTitulaire)
                .contains(tuple(ChampCompte.RUE, MOTIF, false), tuple(ChampCompte.LOCALITE, MOTIF, false));

        mvc.perform(get("/admin/comptes/{ref}", membre.getReference())
                        .with(user(admin.getEmail()).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rue Haute")))
                .andExpect(content().string(containsString(MOTIF)))
                .andExpect(content().string(containsString("/admin/comptes/"
                        + membre.getReference() + "/modification")));
    }

    @Test
    @DisplayName("le formulaire est prérempli avec les coordonnées du membre")
    void formulairePrerempli() throws Exception {
        mvc.perform(get("/admin/comptes/{ref}/modification", membre.getReference())
                        .with(user(admin.getEmail()).roles("ADMINISTRATEUR")).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Dupont\"")))
                .andExpect(content().string(containsString(membre.getEmail())));
    }

    @Test
    @DisplayName("sans motif : formulaire réaffiché, rien d'enregistré")
    void motifObligatoire() throws Exception {
        modifier(admin, membre, " ")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Indiquez la demande du membre")));

        assertThat(utilisateurs.findById(membre.getId()).orElseThrow().getRue()).isNull();
        assertThat(modifications.historiqueDe(membre.getId())).isEmpty();
    }

    @Test
    @DisplayName("un compte du back-office ne se modifie pas par ce formulaire")
    void compteDuBackOffice() throws Exception {
        Utilisateur autre = creer("modif-autre-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                TypeUtilisateur.ADMINISTRATEUR);

        modifier(admin, autre, MOTIF)
                .andExpect(redirectedUrl("/admin/comptes/" + autre.getReference()));

        assertThat(utilisateurs.findById(autre.getId()).orElseThrow().getRue()).isNull();
        assertThat(modifications.historiqueDe(autre.getId())).isEmpty();
    }

    @Test
    @DisplayName("un membre ne modifie pas un autre compte par cette route")
    void refuseAuMembre() throws Exception {
        Utilisateur autre = creer("modif-tiers-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                TypeUtilisateur.MEMBRE);

        modifier(membre, autre, MOTIF).andExpect(status().isForbidden());

        assertThat(modifications.historiqueDe(autre.getId())).isEmpty();
    }
}
