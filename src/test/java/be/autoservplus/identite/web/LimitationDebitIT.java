package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import be.autoservplus.support.SocleIntegration;

/**
 * Plafond des formulaires publics declenchant un envoi de courriel.
 *
 * <p><b>Chaque cas fixe sa propre adresse IP.</b> MockMvc ne traverse pas Tomcat :
 * sans cela toutes les requetes viendraient de {@code 127.0.0.1} et se partageraient
 * un unique budget d IP, ce qui coupleraient des cas qui n ont rien a voir — et
 * ferait echouer le dernier ecrit plutot que le fautif. Les adresses employees sont
 * dans {@code 203.0.113.0/24}, plage reservee a la documentation (RFC 5737).</p>
 *
 * <p>Aucune doublure : l observable de l envoi est le <b>jeton de verification</b>,
 * pose en base par le service. Une demande servie le change, une demande refusee le
 * laisse intact. Verifier l effet plutot que l appel suit la regle du projet, et
 * resiste a une reecriture du service.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Limitation de debit des formulaires anonymes (integration)")
class LimitationDebitIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "MotDePasseSolide2026!";

    /** Valeur par defaut de {@code demandes-max-par-adresse}. */
    private static final int MAX_PAR_ADRESSE = 5;

    private static final String RENVOI = "/inscription/renvoyer-verification";
    private static final String OUBLI = "/mot-de-passe/oublie";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;

    @Nested
    @DisplayName("le plafond mord")
    class Plafond {

        @Test
        @DisplayName("renvoi de verification : la demande au-dela du plafond n'envoie rien")
        void renvoiPlafonne() throws Exception {
            String email = creerMembreNonVerifie();
            String ip = "203.0.113.11";

            for (int i = 0; i < MAX_PAR_ADRESSE; i++) {
                demander(RENVOI, email, ip);
            }
            String jetonApresLePlafond = jetonDe(email);

            demander(RENVOI, email, ip);

            // Le jeton n a pas bouge : aucun courriel n est parti. C est l effet qui
            // repond, pas une doublure qui compterait les appels.
            assertThat(jetonDe(email)).isEqualTo(jetonApresLePlafond);
        }

        @Test
        @DisplayName("les demandes sous le plafond sont bien servies")
        void sousLePlafondRienNeChange() throws Exception {
            String email = creerMembreNonVerifie();
            String jetonInitial = jetonDe(email);

            demander(RENVOI, email, "203.0.113.12");

            assertThat(jetonDe(email)).isNotEqualTo(jetonInitial);
        }

        @Test
        @DisplayName("mot de passe oublié : même plafond, même effet")
        void oubliPlafonne() throws Exception {
            String email = creerMembreActif();
            String ip = "203.0.113.13";

            for (int i = 0; i < MAX_PAR_ADRESSE; i++) {
                demander(OUBLI, email, ip);
            }
            String jetonApresLePlafond = jetonDe(email);

            demander(OUBLI, email, ip);

            assertThat(jetonDe(email)).isEqualTo(jetonApresLePlafond);
        }

        /**
         * Le plafond porte sur l adresse, pas sur le formulaire : une adresse epuisee
         * par un parcours ne doit pas se recharger en passant par l autre. Sans ce
         * cas, deux limiteurs independants passeraient les trois precedents.
         */
        @Test
        @DisplayName("le quota d'une adresse est commun aux deux formulaires")
        void quotaCommunAuxDeuxFormulaires() throws Exception {
            String email = creerMembreNonVerifie();
            String ip = "203.0.113.14";

            for (int i = 0; i < MAX_PAR_ADRESSE; i++) {
                demander(RENVOI, email, ip);
            }
            String jetonApresLePlafond = jetonDe(email);

            demander(OUBLI, email, ip);

            assertThat(jetonDe(email)).isEqualTo(jetonApresLePlafond);
        }

        @Test
        @DisplayName("une autre adresse depuis la même IP reste servie")
        void leQuotaEstBienParAdresse() throws Exception {
            String epuisee = creerMembreNonVerifie();
            String intacte = creerMembreNonVerifie();
            String ip = "203.0.113.15";

            for (int i = 0; i <= MAX_PAR_ADRESSE; i++) {
                demander(RENVOI, epuisee, ip);
            }
            String jetonInitial = jetonDe(intacte);

            demander(RENVOI, intacte, ip);

            // Le plafond par IP est bien plus haut : la seconde adresse passe.
            assertThat(jetonDe(intacte)).isNotEqualTo(jetonInitial);
        }
    }

    @Nested
    @DisplayName("neutralité anti-oracle")
    class Neutralite {

        /**
         * <b>Le cas central du lot.</b> Une adresse inconnue doit consommer son quota
         * exactement comme une adresse servie : si le decompte se faisait apres la
         * recherche du compte, l inconnue ne serait jamais plafonnee et la difference
         * de comportement revelerait l existence du compte.
         *
         * <p>Les deux corps sont compares apres normalisation — voir
         * {@link #corpsNormalise}. Comparer les octets bruts ferait echouer le test
         * sur le seul jeton CSRF, qui varie legitimement a chaque requete.</p>
         */
        @Test
        @DisplayName("connue et inconnue : même page, y compris une fois le plafond atteint")
        void memePageAuPlafond() throws Exception {
            String connue = creerMembreNonVerifie();
            String inconnue = "personne-" + COMPTEUR.getAndIncrement() + "@exemple.be";

            for (int i = 0; i < MAX_PAR_ADRESSE; i++) {
                demander(RENVOI, connue, "203.0.113.21");
                demander(RENVOI, inconnue, "203.0.113.22");
            }

            String corpsConnue = corpsNormalise(RENVOI, connue, "203.0.113.21");
            String corpsInconnue = corpsNormalise(RENVOI, inconnue, "203.0.113.22");

            assertThat(corpsConnue).isEqualTo(corpsInconnue);
            // Et la page est bien celle du plafond, pas celle du succes : sans cette
            // assertion, deux pages de succes identiques passeraient le cas.
            assertThat(corpsConnue).contains("Trop de demandes");
        }

        @Test
        @DisplayName("le message de plafond ne cite ni l'adresse ni le compte")
        void messageNeutre() throws Exception {
            String email = creerMembreNonVerifie();
            String ip = "203.0.113.23";
            for (int i = 0; i <= MAX_PAR_ADRESSE; i++) {
                demander(RENVOI, email, ip);
            }

            String corps = corpsNormalise(RENVOI, email, ip);

            assertThat(corps).doesNotContain(email);
            // Le message parle du demandeur, pas du titulaire : « ce compte » y ferait
            // entrer l existence du compte par la porte du texte. Assertion portee sur
            // le texte SERVI, commentaires retires — voir sansCommentaires : un
            // commentaire de gabarit s adresse au developpeur, pas au visiteur, et la
            // premiere version de ce cas echouait sur le commentaire qui explique
            // precisement que le message ne dit pas « ce compte ».
            assertThat(sansCommentaires(corps)).doesNotContain("ce compte");
        }

        @Test
        @DisplayName("le statut HTTP reste 200, sans 429 qui trahirait le plafond")
        void statutIdentique() throws Exception {
            String inconnue = "personne-" + COMPTEUR.getAndIncrement() + "@exemple.be";
            for (int i = 0; i <= MAX_PAR_ADRESSE; i++) {
                // Chaque appel affirme deja isOk() ; le depassement ne fait pas exception.
                demander(RENVOI, inconnue, "203.0.113.24");
            }
        }
    }

    @Nested
    @DisplayName("adresse du client derrière le proxy")
    class DerriereLeProxy {

        /**
         * Fixe le comportement retenu pour {@code server.forward-headers-strategy:
         * native} : c est l adresse du VISITEUR qui compte, pas celle de Caddy. Sans
         * ce reglage, toutes les requetes viendraient du proxy et le plafond par IP
         * deviendrait un plafond global pour tout le site.
         *
         * <p>MockMvc ne traverse pas la vanne RemoteIp de Tomcat : on ne peut pas y
         * eprouver l extraction de {@code X-Forwarded-For}. Ce que ce cas verrouille
         * est l autre moitie, et c est celle dont le limiteur depend — le compteur est
         * bien tenu par {@code getRemoteAddr()}, donc deux visiteurs distincts ont des
         * budgets distincts, et un en-tete que Tomcat n a pas valide ne vient pas s y
         * substituer.</p>
         */
        @Test
        @DisplayName("deux IP distinctes ont des budgets distincts, un en-tête brut n'en crée pas")
        void budgetParIpReelle() throws Exception {
            String premier = creerMembreNonVerifie();
            String second = creerMembreNonVerifie();

            for (int i = 0; i <= MAX_PAR_ADRESSE; i++) {
                demander(RENVOI, premier, "203.0.113.31");
            }

            // Un X-Forwarded-For pose a la main ne doit pas servir de cle : MockMvc ne
            // passe pas par RemoteIpValve, donc l en-tete n est pas honore et c est
            // getRemoteAddr() qui tranche. Le budget de .32 est donc intact.
            String jetonInitial = jetonDe(second);
            mvc.perform(requete(RENVOI, second, "203.0.113.32")
                            .header("X-Forwarded-For", "203.0.113.31"))
                    .andExpect(status().isOk());

            assertThat(jetonDe(second)).isNotEqualTo(jetonInitial);
        }
    }

    // --- outillage ------------------------------------------------------------------

    private MockHttpServletRequestBuilder requete(String route, String email, String ip) {
        return post(route).param("email", email)
                .with(anonymous()).with(csrf()).header("Accept-Language", "fr")
                .with(brute -> {
                    brute.setRemoteAddr(ip);
                    return brute;
                });
    }

    private void demander(String route, String email, String ip) throws Exception {
        mvc.perform(requete(route, email, ip)).andExpect(status().isOk());
    }

    /**
     * Corps de la reponse, <b>jeton CSRF neutralise</b>. La normalisation est ecrite
     * ici en clair plutot que cachee dans un utilitaire : ce qu une comparaison de
     * neutralite choisit d ignorer fait partie de ce qu elle affirme. Seul le jeton
     * CSRF varie d une reponse a l autre — la politique de securite du projet ne pose
     * aucun nonce, {@code script-src} valant {@code 'self'} sans nonce ni hachage.
     */
    private String corpsNormalise(String route, String email, String ip) throws Exception {
        String corps = mvc.perform(requete(route, email, ip))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return corps.replaceAll("name=\"_csrf\" value=\"[^\"]*\"", "name=\"_csrf\" value=\"X\"");
    }

    /**
     * Retire les commentaires HTML. Ils sont rendus au visiteur par Thymeleaf mais ne
     * font pas partie de ce qu il LIT : une assertion sur la formulation du message ne
     * doit etre ni satisfaite ni mise en defaut par eux. Normalisation distincte de
     * celle de {@link #corpsNormalise}, et appliquee au seul cas qui parle du texte —
     * l egalite de deux pages, elle, doit continuer de voir les commentaires, au cas
     * ou l un d eux deviendrait un jour conditionnel.
     */
    private static String sansCommentaires(String html) {
        return html.replaceAll("(?s)<!--.*?-->", "");
    }

    private String jetonDe(String email) {
        return utilisateurs.findByEmailIgnoreCase(email).orElseThrow().getJetonVerification();
    }

    private String creerMembreNonVerifie() {
        String email = "debit-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        Utilisateur membre = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                "Test", "Alex", TypeUtilisateur.MEMBRE);
        membre.enregistrerJetonVerification("jeton-initial-" + COMPTEUR.getAndIncrement(),
                Instant.now().plusSeconds(3600));
        return utilisateurs.save(membre).getEmail();
    }

    private String creerMembreActif() {
        String email = "debit-actif-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        Utilisateur membre = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                "Test", "Alex", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        membre.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.save(membre).getEmail();
    }
}
