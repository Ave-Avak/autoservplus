package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recette par defaut des ecrans GET du lot : chacun repond 200 dans sa configuration la
 * plus nue, aucun parametre, un principal du role attendu, une base seulement migree.
 *
 * <p>Motif du registre §4 : six defauts trouves par la seule recette manuelle portaient
 * tous sur un chemin par defaut qu aucun test n ouvrait. Un test choisit ses parametres,
 * et choisit donc rarement « rien du tout ».</p>
 *
 * <p><b>Exclusion nommee</b> : {@code /mon-profil/securite/codes} n est pas un ecran
 * autonome mais l affichage d un attribut flash. Sans lui, il renvoie a l ecran de
 * securite, et ce renvoi est lui-meme verifie ici. Les ecrans d etape de connexion sont
 * ouverts par {@code ConnexionSecondFacteurIT}, qui seul sait produire une connexion
 * partielle.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Écrans du second facteur dans leur configuration par défaut")
class EcransSecuriteParDefautIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    private Utilisateur compte(String email, TypeUtilisateur type) {
        Utilisateur u = new Utilisateur(email, encodeur.encode("phrase de passe par defaut"),
                "Nom", "Prenom", type);
        u.confirmerAdresseEmail();
        return utilisateurs.saveAndFlush(u);
    }

    @Test
    @DisplayName("sécurité du compte : membre et administrateur, sans rien d'enrôlé")
    void securiteDuCompte() throws Exception {
        compte("defaut-membre@exemple.be", TypeUtilisateur.MEMBRE);
        compte("defaut-admin@exemple.be", TypeUtilisateur.ADMINISTRATEUR);

        for (Locale langue : new Locale[]{Locale.FRENCH, Locale.forLanguageTag("nl"), Locale.ENGLISH}) {
            mvc.perform(get("/mon-profil/securite").locale(langue)
                            .with(user("defaut-membre@exemple.be").roles("MEMBRE")))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/mon-profil/securite").with(user("defaut-admin@exemple.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk());
        mvc.perform(get("/mon-profil/securite/enrolement")
                        .with(user("defaut-membre@exemple.be").roles("MEMBRE")))
                .andExpect(status().isOk());
        mvc.perform(get("/mon-profil/securite/codes")
                        .with(user("defaut-membre@exemple.be").roles("MEMBRE")))
                .andExpect(redirectedUrl("/mon-profil/securite"));
    }

    @Test
    @DisplayName("création d'un administrateur : formulaire vide")
    void formulaireDeCreation() throws Exception {
        compte("defaut-chef@exemple.be", TypeUtilisateur.SUPER_ADMINISTRATEUR);

        mvc.perform(get("/admin/comptes/administrateurs/nouveau")
                        .with(user("defaut-chef@exemple.be").roles("SUPER_ADMINISTRATEUR")))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/comptes/administrateurs")
                        .with(user("defaut-chef@exemple.be").roles("SUPER_ADMINISTRATEUR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("lien de définition sans jeton : page d'explication, pas d'erreur")
    void definitionSansJeton() throws Exception {
        mvc.perform(get("/compte/definition")).andExpect(status().isOk());
        mvc.perform(get("/compte/definition").param("jeton", "inconnu")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("détail d'un compte sans aucun événement de sécurité")
    void detailSansEvenement() throws Exception {
        Utilisateur chef = compte("defaut-chef-detail@exemple.be", TypeUtilisateur.SUPER_ADMINISTRATEUR);
        Utilisateur membre = compte("defaut-membre-detail@exemple.be", TypeUtilisateur.MEMBRE);

        mvc.perform(get("/admin/comptes/{ref}", membre.getReference())
                        .with(user(chef.getEmail()).roles("SUPER_ADMINISTRATEUR")))
                .andExpect(status().isOk());
    }
}
