package be.autoservplus.identite.web;

import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.repository.EvenementSecuriteCompteRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creation d un administrateur, lien de definition, reinitialisation du second facteur.
 *
 * <p>Le lien est lu sur le port de notification, la ou il part reellement, et non
 * fabrique par le test : c est le defaut du 404 du paiement fictif, ou le test produisait
 * lui-meme l etape suivante (registre §4).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Création d'un administrateur et lien de définition (intégration)")
class DefinitionMotDePasseIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String PREFIXE_LIEN = "/compte/definition?jeton=";
    private static final String CHOISI = "phrase choisie par le nouvel administrateur";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private EvenementSecuriteCompteRepository evenements;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ChiffrementSecretMfa chiffrement;
    @Autowired private Clock horloge;
    @MockitoSpyBean private ServiceCourriel courriel;

    private Utilisateur chef;

    @BeforeEach
    void setUp() {
        chef = new Utilisateur("chef-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode("phrase de passe du chef"), "Garage", "Chef",
                TypeUtilisateur.SUPER_ADMINISTRATEUR);
        chef.confirmerAdresseEmail();
        chef = utilisateurs.saveAndFlush(chef);
    }

    private RequestPostProcessor commeChef() {
        return user(chef.getEmail()).roles("SUPER_ADMINISTRATEUR");
    }

    /** Cree un administrateur par l ecran et rend le jeton tel qu il est parti. */
    private String creerAdministrateur(String email) throws Exception {
        mvc.perform(post("/admin/comptes/administrateurs/nouveau").with(commeChef()).with(csrf())
                        .param("email", email).param("prenom", "Ana").param("nom", "Lopez"))
                .andExpect(status().is3xxRedirection());
        return dernierJetonEnvoye(email);
    }

    private String dernierJetonEnvoye(String email) {
        ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
        verify(courriel, atLeastOnce()).envoyerLienDefinitionMotDePasse(
                argThat(u -> u.getEmail().equals(email)), lien.capture());
        return lien.getValue().substring(PREFIXE_LIEN.length());
    }

    private Utilisateur parEmail(String email) {
        return utilisateurs.findByEmailIgnoreCase(email).orElseThrow();
    }

    @Test
    @DisplayName("le compte créé ne peut pas se connecter avant d'avoir suivi son lien")
    void compteInutilisableAvantLeLien() throws Exception {
        String email = "nouvel-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        String jeton = creerAdministrateur(email);

        Utilisateur cree = parEmail(email);
        assertThat(cree.getStatut()).isEqualTo(StatutUtilisateur.EN_ATTENTE_VALIDATION);
        assertThat(cree.getJetonDefinitionHache()).isNotEqualTo(jeton).hasSize(64);
        assertThat(evenements.evenementsDe(cree.getId())).extracting(e -> e.getType())
                .contains(TypeEvenementSecurite.ADMINISTRATEUR_CREE, TypeEvenementSecurite.LIEN_DEFINITION_ENVOYE);
    }

    @Test
    @DisplayName("le lien affiche le formulaire sans se consommer, avec Referrer-Policy: no-referrer")
    void getNeConsommePas() throws Exception {
        String email = "nouvel-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        String jeton = creerAdministrateur(email);

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/compte/definition").param("jeton", jeton).locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(content().string(containsString("name=\"jeton\" value=\"" + jeton + "\"")));
        }
        assertThat(parEmail(email).getJetonDefinitionHache()).isNotNull();
    }

    @Test
    @DisplayName("le POST définit le mot de passe, active le compte, puis le lien est mort")
    void definitionPuisRejeu() throws Exception {
        String email = "nouvel-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        String jeton = creerAdministrateur(email);

        mvc.perform(post("/compte/definition").with(csrf()).locale(Locale.FRENCH)
                        .param("jeton", jeton).param("motDePasse", CHOISI).param("confirmation", CHOISI))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(containsString("/connexion")));

        Utilisateur defini = parEmail(email);
        assertThat(defini.getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
        assertThat(defini.doitChangerMotDePasse()).isFalse();
        assertThat(encodeur.matches(CHOISI, defini.getMotDePasseHache())).isTrue();

        // Rejoue : meme page que pour un lien inconnu ou expire.
        mvc.perform(post("/compte/definition").with(csrf()).locale(Locale.FRENCH)
                        .param("jeton", jeton).param("motDePasse", "autre phrase encore plus longue")
                        .param("confirmation", "autre phrase encore plus longue"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"motDePasse\""))));
        assertThat(encodeur.matches(CHOISI, parEmail(email).getMotDePasseHache())).isTrue();

        // Premiere connexion : le second facteur s impose aussitot.
        mvc.perform(post("/connexion").with(csrf()).param("email", email).param("password", CHOISI))
                .andExpect(redirectedUrl("/connexion/etape/enrolement"));
    }

    @Test
    @DisplayName("un lien expiré est refusé")
    void lienExpire() throws Exception {
        String email = "nouvel-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        String jeton = creerAdministrateur(email);
        Utilisateur cree = parEmail(email);
        cree.preparerDefinitionMotDePasse(cree.getJetonDefinitionHache(),
                horloge.instant().minus(Duration.ofMinutes(1)));
        utilisateurs.saveAndFlush(cree);

        mvc.perform(get("/compte/definition").param("jeton", jeton).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"motDePasse\""))));
    }

    @Test
    @DisplayName("renvoyer le lien invalide le précédent")
    void renvoi() throws Exception {
        String email = "nouvel-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        String premier = creerAdministrateur(email);
        Utilisateur cree = parEmail(email);

        mvc.perform(post("/admin/comptes/{ref}/lien-definition", cree.getReference())
                        .with(commeChef()).with(csrf()))
                .andExpect(redirectedUrl("/admin/comptes/" + cree.getReference()));
        String second = dernierJetonEnvoye(email);

        assertThat(second).isNotEqualTo(premier);
        mvc.perform(get("/compte/definition").param("jeton", premier))
                .andExpect(content().string(not(containsString("name=\"motDePasse\""))));
        mvc.perform(get("/compte/definition").param("jeton", second))
                .andExpect(content().string(containsString("name=\"motDePasse\"")));
    }

    @Test
    @DisplayName("un administrateur ordinaire ne crée pas d'administrateur")
    void reserveAuSuperAdministrateur() throws Exception {
        mvc.perform(get("/admin/comptes/administrateurs/nouveau")
                        .with(user("admin-ordinaire@exemple.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/comptes/administrateurs/nouveau")
                        .with(user("admin-ordinaire@exemple.be").roles("ADMINISTRATEUR")).with(csrf())
                        .param("email", "x@exemple.be").param("prenom", "A").param("nom", "B"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la réinitialisation retire le second facteur, journalise et avertit le titulaire")
    void reinitialisationDuSecondFacteur() throws Exception {
        Utilisateur admin = new Utilisateur("enrole-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode("phrase de passe de l admin"), "Nom", "Prenom", TypeUtilisateur.ADMINISTRATEUR);
        admin.confirmerAdresseEmail();
        admin = utilisateurs.saveAndFlush(admin);
        ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(new byte[20], admin.getId());
        admin.commencerEnrolement(chiffre.donnees(), chiffre.version());
        admin.confirmerEnrolement(horloge.instant(), 1L);
        admin = utilisateurs.saveAndFlush(admin);

        mvc.perform(post("/admin/comptes/{ref}/second-facteur/reinitialisation", admin.getReference())
                        .with(commeChef()).with(csrf()))
                .andExpect(redirectedUrl("/admin/comptes/" + admin.getReference()));

        Utilisateur apres = utilisateurs.findById(admin.getId()).orElseThrow();
        assertThat(apres.secondFacteurActif()).isFalse();
        assertThat(evenements.evenementsDe(admin.getId())).extracting(e -> e.getType())
                .containsExactly(TypeEvenementSecurite.MFA_REINITIALISE);
        verify(courriel).envoyerAvisReinitialisationSecondFacteur(argThat(u -> u.getId().equals(apres.getId())));

        mvc.perform(get("/admin/comptes/{ref}", admin.getReference()).with(commeChef())
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vérification en deux étapes réinitialisée")));
    }
}
