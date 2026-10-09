package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.EvenementSecuriteCompte;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.EvenementSecuriteCompteRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verrouillage par mot de passe, par le vrai formulaire de connexion, jusqu a la fiche de
 * l exploitant.
 *
 * <p>La recette l a montre : le verrouillage existait en base sans apparaitre nulle part.
 * Le test unitaire du service prouvait le compteur, pas que l evenement atteint l ecran ;
 * c est ce trajet entier que ce cas tient.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Verrouillage de la connexion (integration)")
class VerrouillageConnexionIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test de verrouillage";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private EvenementSecuriteCompteRepository evenements;
    @Autowired private PasswordEncoder encodeur;

    private Utilisateur creer(TypeUtilisateur type) {
        Utilisateur u = new Utilisateur("verrou-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom", type);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.saveAndFlush(u);
    }

    @Test
    @DisplayName("cinq mots de passe faux : un seul événement, visible sur la fiche")
    void unSeulEvenementVisibleSurLaFiche() throws Exception {
        Utilisateur membre = creer(TypeUtilisateur.MEMBRE);
        Utilisateur chef = creer(TypeUtilisateur.SUPER_ADMINISTRATEUR);

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/connexion").with(csrf())
                            .param("email", membre.getEmail()).param("password", "mauvais " + i))
                    .andExpect(status().is3xxRedirection());
        }

        assertThat(evenements.evenementsDe(membre.getId()))
                .extracting(EvenementSecuriteCompte::getType)
                .containsExactly(TypeEvenementSecurite.CONNEXION_VERROUILLAGE);

        mvc.perform(get("/admin/comptes/{ref}", membre.getReference())
                        .with(user(chef.getEmail()).roles("SUPER_ADMINISTRATEUR"))
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Compte verrouillé après des mots de passe incorrects")));
    }

    @Test
    @DisplayName("une tentative refusée sur verrou n'ajoute aucun événement")
    void tentativeSurVerrouSansEvenement() throws Exception {
        Utilisateur membre = creer(TypeUtilisateur.MEMBRE);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/connexion").with(csrf())
                    .param("email", membre.getEmail()).param("password", "mauvais " + i));
        }

        mvc.perform(post("/connexion").with(csrf())
                        .param("email", membre.getEmail()).param("password", MOT_DE_PASSE))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .contains("bloque"));

        assertThat(evenements.evenementsDe(membre.getId())).hasSize(1);
    }
}
