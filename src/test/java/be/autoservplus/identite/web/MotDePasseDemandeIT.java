package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import be.autoservplus.support.SocleIntegration;

/**
 * Neutralite du formulaire public de reinitialisation de mot de passe.
 *
 * <p>Ce formulaire est anonyme : tout ce qui distingue une adresse servie d une
 * adresse inconnue en fait un oracle d existence de compte, donc un moyen d enumerer
 * les membres. Le service portait deja cette neutralite ; le controleur la rompait en
 * renvoyant l adresse saisie au modele.</p>
 *
 * <p>Test d integration et non {@code @WebMvcTest} : c est le HTML reellement rendu
 * qui doit taire l adresse, et un test a doublures ne rendrait aucun HTML. Meme
 * raisonnement que {@link RenvoiVerificationIT}, dont ce parcours est le jumeau.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Demande de reinitialisation de mot de passe (integration)")
class MotDePasseDemandeIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "MotDePasseSolide2026!";

    /**
     * IP fixe et propre a cette classe. Le plafond de debit des formulaires publics
     * compte par adresse IP ; sans cette epingle, tous les cas de tous les tests
     * partageraient le budget de {@code 127.0.0.1}, que MockMvc attribue par defaut,
     * et un cas ajoute plus tard ferait echouer un cas ecrit avant lui.
     * {@code 203.0.113.0/24} est la plage reservee a la documentation (RFC 5737).
     */
    private static final String IP = "203.0.113.41";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    @Test
    @DisplayName("l'adresse soumise n'est jamais reaffichee")
    void adresseJamaisReaffichee() throws Exception {
        String email = creerMembreActif();

        assertThat(corpsApresDemande(email)).doesNotContain(email);
        // Une adresse inconnue non plus : sinon un lien forge menant ici donnerait a
        // n importe quelle saisie l apparence d une confirmation emise par le site.
        String inconnue = adresseInconnue();
        assertThat(corpsApresDemande(inconnue)).doesNotContain(inconnue);
    }

    /**
     * Le cas qui fait le travail. Comparer les deux corps, et pas seulement verifier
     * que chacun tait son adresse : une page qui dirait « compte introuvable » sans
     * citer l adresse passerait le cas precedent tout en restant un oracle parfait.
     */
    @Test
    @DisplayName("adresse connue et adresse inconnue rendent exactement la meme page")
    void memePageDansLesDeuxCas() throws Exception {
        String email = creerMembreActif();

        assertThat(corpsApresDemande(email)).isEqualTo(corpsApresDemande(adresseInconnue()));
    }

    /**
     * Le pendant : la neutralite ne doit pas etre obtenue en ne faisant rien. Une
     * adresse servie voit son jeton pose, une inconnue ne cree evidemment rien — la
     * difference existe en base, elle ne sort simplement pas par la reponse.
     */
    @Test
    @DisplayName("le compte existant recoit bien un jeton, la neutralite n'est pas de l'inaction")
    void laNeutraliteNEstPasDeLInaction() throws Exception {
        String email = creerMembreActif();
        assertThat(utilisateurs.findByEmailIgnoreCase(email).orElseThrow()
                .getJetonVerification()).isNull();

        corpsApresDemande(email);

        assertThat(utilisateurs.findByEmailIgnoreCase(email).orElseThrow()
                .getJetonVerification()).isNotNull();
    }

    /**
     * Corps de la reponse, <b>jetons CSRF neutralises</b> : ils changent a chaque
     * requete pour des raisons qui n ont rien a voir avec la neutralite, et les
     * comparer ferait echouer le test pour la seule difference legitime entre deux
     * reponses. La normalisation est ecrite ici, en clair, plutot que cachee dans un
     * utilitaire : ce qu un test de neutralite choisit d ignorer fait partie de ce
     * qu il affirme.
     */
    private String corpsApresDemande(String email) throws Exception {
        String corps = mvc.perform(post("/mot-de-passe/oublie").param("email", email)
                        .with(anonymous()).with(csrf()).header("Accept-Language", "fr")
                        .with(brute -> {
                            brute.setRemoteAddr(IP);
                            return brute;
                        }))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return corps.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\" value=\"X\"");
    }

    /**
     * Adresse inconnue NEUVE a chaque appel. Une constante partagee verrait son quota
     * de debit consomme par les cas precedents : le dernier a s executer recevrait la
     * page de plafond quand son terme de comparaison recevrait celle du succes, et la
     * comparaison de neutralite echouerait pour une raison etrangere a la neutralite.
     */
    private String adresseInconnue() {
        return "personne-sans-compte-" + COMPTEUR.getAndIncrement() + "@exemple.be";
    }

    private String creerMembreActif() {
        String email = "reinit-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        Utilisateur membre = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                "Test", "Alex", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.save(membre).getEmail();
    }
}
