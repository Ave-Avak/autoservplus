package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.HistoriqueStatutUtilisateurRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Gestion des comptes depuis le back-office (CdC 5.2.3), de bout en bout.
 *
 * <p>Le point que seul un test d integration etablit : une suspension ecrite par
 * l ecran <b>refuse effectivement la connexion suivante</b>. Le service ne fait
 * qu ecrire un statut ; c est {@code UtilisateurDetailsService} qui en tire
 * {@code disabled}, et rien dans le service ne le prouve.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Back-office des comptes (integration)")
class AdminComptesIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private HistoriqueStatutUtilisateurRepository historique;
    @Autowired private PasswordEncoder encodeur;

    private Utilisateur membre;
    private Utilisateur admin;
    private Utilisateur chef;

    @BeforeEach
    void setUp() {
        int n = COMPTEUR.getAndIncrement();
        membre = creer("membre-" + n + "@exemple.be", TypeUtilisateur.MEMBRE);
        admin = creer("admin-" + n + "@exemple.be", TypeUtilisateur.ADMINISTRATEUR);
        chef = creer("chef-" + n + "@exemple.be", TypeUtilisateur.SUPER_ADMINISTRATEUR);
    }

    private Utilisateur creer(String email, TypeUtilisateur type) {
        Utilisateur u = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                "Nom", "Prenom", type);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.saveAndFlush(u);
    }

    private Utilisateur relire(Utilisateur u) {
        return utilisateurs.findById(u.getId()).orElseThrow();
    }

    private org.springframework.test.web.servlet.ResultActions suspendre(
            Utilisateur auteur, Utilisateur cible, String motif) throws Exception {
        return mvc.perform(post("/admin/comptes/{ref}/suspendre", cible.getReference())
                .with(user(auteur.getEmail()).roles(auteur.getTypeUtilisateur().name()))
                .with(csrf()).locale(Locale.FRENCH)
                .param("motif", motif));
    }

    @Nested
    @DisplayName("suspension")
    class Suspension {

        /**
         * <b>Le cas que seul un test d integration etablit.</b> Le service ecrit un
         * statut ; c est {@code UtilisateurDetailsService} qui en tire {@code disabled}.
         * Verifier l un sans l autre laisserait croire qu une suspension protege, alors
         * que le lien entre les deux n est atteste nulle part ailleurs.
         */
        @Test
        @DisplayName("le compte suspendu ne peut plus se connecter")
        void suspensionRefuseLaConnexion() throws Exception {
            // La connexion fonctionne AVANT, sans quoi le cas ne prouverait rien.
            mvc.perform(post("/connexion").with(csrf())
                            .param("email", membre.getEmail()).param("password", MOT_DE_PASSE))
                    .andExpect(status().is3xxRedirection());

            suspendre(admin, membre, "Impayes repetes.")
                    .andExpect(status().is3xxRedirection());

            assertThat(relire(membre).getStatut()).isEqualTo(StatutUtilisateur.SUSPENDU);

            mvc.perform(post("/connexion").with(csrf())
                            .param("email", membre.getEmail()).param("password", MOT_DE_PASSE))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                            .contains("erreur"));
        }

        @Test
        @DisplayName("le journal porte l'auteur et le motif, et l'écran les montre")
        void journalVisible() throws Exception {
            suspendre(chef, membre, "Motif consigne au journal.");

            assertThat(historique.historiqueDe(membre.getId())).hasSize(1);

            mvc.perform(get("/admin/comptes/{ref}", membre.getReference())
                            .with(user(chef.getEmail()).roles("SUPER_ADMINISTRATEUR"))
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Motif consigne au journal.")));
        }

        @Test
        @DisplayName("sans motif : refus affiché, statut inchangé, journal vide")
        void motifObligatoire() throws Exception {
            suspendre(admin, membre, "   ").andExpect(status().is3xxRedirection());

            assertThat(relire(membre).getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            assertThat(historique.historiqueDe(membre.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("liste des membres")
    class Liste {

        /**
         * <b>Le cas qui manquait, et que la recette a trouve a ma place.</b> Aucun test
         * n ouvrait cet ecran : la requete testait {@code :filtre is null} pour rendre
         * tout le monde, et PostgreSQL, incapable d inferer le type d un parametre nul,
         * repondait {@code function lower(bytea) does not exist} — 500 des l ouverture.
         * Meme piege que celui deja consigne pour le journal d audit (BL-7), et meme
         * motif que le 404 du paiement : ce qu aucun test ne traverse, seule
         * l exploitation le traverse.
         */
        @Test
        @DisplayName("s'ouvre sans recherche et rend les membres")
        void listeSansRecherche() throws Exception {
            mvc.perform(get("/admin/comptes")
                            .with(user(admin.getEmail()).roles("ADMINISTRATEUR"))
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(membre.getEmail())));
        }

        @Test
        @DisplayName("la recherche filtre, et ne rend pas les autres")
        void rechercheFiltre() throws Exception {
            mvc.perform(get("/admin/comptes").param("recherche", membre.getEmail())
                            .with(user(admin.getEmail()).roles("ADMINISTRATEUR"))
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(membre.getEmail())));

            // Une recherche qui ne correspond a rien rend la page, pas une erreur.
            mvc.perform(get("/admin/comptes").param("recherche", "zzz-aucun-membre-zzz")
                            .with(user(admin.getEmail()).roles("ADMINISTRATEUR"))
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            containsString(membre.getEmail()))));
        }

        /** Les comptes du back-office ne figurent PAS dans la liste des membres. */
        @Test
        @DisplayName("les comptes privilégiés n'y figurent pas")
        void privilegiesExclus() throws Exception {
            mvc.perform(get("/admin/comptes")
                            .with(user(chef.getEmail()).roles("SUPER_ADMINISTRATEUR"))
                            .locale(Locale.FRENCH))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            containsString(chef.getEmail()))));
        }
    }

    @Nested
    @DisplayName("pouvoirs exclusifs")
    class PouvoirsExclusifs {

        @Test
        @DisplayName("un administrateur ne suspend pas un autre compte du back-office")
        void administrateurSurAdministrateur() throws Exception {
            suspendre(admin, chef, "Tentative.").andExpect(status().is3xxRedirection());

            assertThat(relire(chef).getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            assertThat(historique.historiqueDe(chef.getId())).isEmpty();
        }

        /**
         * L ecran des comptes privilegies est garde par le SERVICE et non par le
         * matcher d URL : {@code /admin/**} admet tout administrateur.
         */
        @Test
        @DisplayName("l'écran des comptes du back-office est refusé à un administrateur ordinaire")
        void ecranReserve() throws Exception {
            mvc.perform(get("/admin/comptes/administrateurs")
                            .with(user(admin.getEmail()).roles("ADMINISTRATEUR")))
                    .andExpect(status().isForbidden());

            mvc.perform(get("/admin/comptes/administrateurs")
                            .with(user(chef.getEmail()).roles("SUPER_ADMINISTRATEUR")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("réactivation")
    class Reactivation {

        @Test
        @DisplayName("réactiver rend la connexion possible et ajoute une ligne au journal")
        void reactivation() throws Exception {
            suspendre(admin, membre, "Suspension temporaire.");

            mvc.perform(post("/admin/comptes/{ref}/reactiver", membre.getReference())
                            .with(user(admin.getEmail()).roles("ADMINISTRATEUR"))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());

            assertThat(relire(membre).getStatut()).isEqualTo(StatutUtilisateur.ACTIF);
            assertThat(historique.historiqueDe(membre.getId())).hasSize(2);
        }
    }
}
