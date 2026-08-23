package be.autoservplus.cookies.web;

import be.autoservplus.cookies.domain.PreferencesCookies;
import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bandeau de consentement aux cookies de bout en bout (F25), sur un PostgreSQL reel.
 *
 * <p>Ce que seule une chaine complete peut etablir : que la decision d afficher le
 * bandeau se prend bien au rendu serveur, que le CHECK de la base accepte les deux
 * nouvelles finalites, et surtout que modifier son choix <b>ajoute</b> des preuves
 * sans toucher aux precedentes.</p>
 *
 * <p>Les attributs du cookie sont verifies sur l en-tete {@code Set-Cookie} lui-meme
 * plutot que sur l objet {@code Cookie} reconstitue : c est cette chaine que le
 * navigateur recoit, et donc elle qui fait foi.</p>
 *
 * <p>Sans {@code @Transactional} de classe : les cookies et l enchainement de deux
 * requetes ne se constatent qu apres de vrais cycles. D ou des adresses uniques par
 * test, les donnees restant dans le conteneur.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Consentement aux cookies (integration)")
class ConsentementCookiesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String CLASSE_DU_BANDEAU = "bandeau-cookies";
    private static final Pattern MAX_AGE = Pattern.compile("Max-Age=(\\d+)");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private ConsentementRepository consentements;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private TransactionTemplate transactions;

    private Utilisateur membre() {
        String email = "cookies-" + COMPTEUR.getAndIncrement() + "@exemple.be";
        return transactions.execute(statut -> {
            Utilisateur nouveau = new Utilisateur(email, encodeur.encode("MonMotDePasse2026!"),
                    "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            nouveau.confirmerAdresseEmail();
            return utilisateurs.saveAndFlush(nouveau);
        });
    }

    /** Preuves d une finalite, de la plus ancienne a la plus recente. */
    private List<Consentement> preuves(String email, TypeDocumentConsentement finalite) {
        return transactions.execute(statut ->
                consentements.findByUtilisateurEmailIgnoreCaseAndTypeDocument(email, finalite)
                        .stream()
                        .sorted(Comparator.comparing(Consentement::getId))
                        .toList());
    }

    private Cookie choixDejaFait(PreferencesCookies choix) {
        return new Cookie(PreferencesCookies.NOM_COOKIE, choix.versValeurCookie());
    }

    private String pageAccueil(Cookie... cookies) throws Exception {
        // MockMvc refuse un tableau de cookies vide : la premiere visite se joue donc
        // sans appel a .cookie(), exactement comme un navigateur qui n en a aucun.
        MockHttpServletRequestBuilder requete = get("/");
        if (cookies.length > 0) {
            requete = requete.cookie(cookies);
        }
        return mvc.perform(requete)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String enTeteSetCookie(MvcResult resultat) {
        String enTete = resultat.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(enTete).as("en-tete Set-Cookie").isNotNull();
        return enTete;
    }

    /** Etat coche, lu sur la balise de la finalite quel que soit le rendu de l attribut. */
    private boolean caseCochee(String page, String finalite) {
        Matcher balise = Pattern.compile("<input[^>]*name=\"" + finalite + "\"[^>]*>").matcher(page);
        assertThat(balise.find()).as("case %s presente dans la page", finalite).isTrue();
        return balise.group().contains("checked");
    }

    @Nested
    @DisplayName("affichage du bandeau")
    class Affichage {

        @Test
        @DisplayName("premiere visite : le bandeau est present dans la page servie")
        void premiereVisite() throws Exception {
            assertThat(pageAccueil()).contains(CLASSE_DU_BANDEAU);
        }

        /**
         * Le bandeau est absent du HTML lui-meme, pas seulement masque : c est ce qui
         * distingue une decision prise au serveur d un masquage fait apres coup, et ce
         * qui evite qu il clignote a chaque page pour qui a deja repondu.
         */
        @Test
        @DisplayName("visite suivante : le bandeau n'est meme pas envoye")
        void visiteSuivante() throws Exception {
            assertThat(pageAccueil(choixDejaFait(PreferencesCookies.refusTotal())))
                    .doesNotContain(CLASSE_DU_BANDEAU);
        }

        @Test
        @DisplayName("un cookie illisible fait reposer la question")
        void cookieIllisible() throws Exception {
            Cookie bricole = new Cookie(PreferencesCookies.NOM_COOKIE, "v9-oui");

            assertThat(pageAccueil(bricole)).contains(CLASSE_DU_BANDEAU);
        }

        @Test
        @DisplayName("le lien de gestion reste accessible une fois le choix fait")
        void lienDeGestionPermanent() throws Exception {
            String page = pageAccueil(choixDejaFait(PreferencesCookies.acceptationTotale()));

            assertThat(page).doesNotContain(CLASSE_DU_BANDEAU);
            assertThat(page).contains("href=\"/cookies\"");
        }
    }

    @Nested
    @DisplayName("visiteur non connecte")
    class VisiteurNonConnecte {

        @Test
        @DisplayName("« Tout refuser » memorise le refus et ramene le visiteur ou il etait")
        void refusSansCompte() throws Exception {
            MvcResult resultat = mvc.perform(post("/cookies/preferences")
                            .param("action", "refuser")
                            .param("retour", "/services?page=2")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/services?page=2"))
                    .andReturn();

            assertThat(enTeteSetCookie(resultat))
                    .contains(PreferencesCookies.NOM_COOKIE + "=v1-00");
        }

        @Test
        @DisplayName("le cookie de preference est cloisonne et memorise six mois")
        void attributsDuCookie() throws Exception {
            MvcResult resultat = mvc.perform(post("/cookies/preferences")
                            .param("action", "accepter").with(csrf()))
                    .andReturn();

            String enTete = enTeteSetCookie(resultat);
            assertThat(enTete).contains(PreferencesCookies.NOM_COOKIE + "=v1-11");
            assertThat(enTete).contains("Path=/");
            // Inaccessible au JavaScript : aucun script ne le lit, c est le serveur qui
            // decide du rendu du bandeau.
            assertThat(enTete).contains("HttpOnly");
            // Lax et non Strict : Strict ne serait pas envoye sur une arrivee depuis un
            // site tiers, et le bandeau reviendrait harceler qui a deja refuse.
            assertThat(enTete).contains("SameSite=Lax");

            Matcher maxAge = MAX_AGE.matcher(enTete);
            assertThat(maxAge.find()).as("Max-Age present").isTrue();
            // Six mois calendaires : entre 181 et 184 jours selon le mois de depart.
            assertThat(Long.parseLong(maxAge.group(1)))
                    .isBetween(181L * 86_400, 185L * 86_400);
        }

        /**
         * Le champ « retour » est un parametre de requete : il peut etre fabrique. Sans
         * filtre, il offrirait une redirection ouverte depuis une adresse AutoServ+,
         * point de depart classique d un hameconnage.
         */
        @Test
        @DisplayName("une adresse de retour externe est ignoree")
        void redirectionOuverteRefusee() throws Exception {
            mvc.perform(post("/cookies/preferences")
                            .param("action", "refuser")
                            .param("retour", "//evil.example.com/piege")
                            .with(csrf()))
                    .andExpect(redirectedUrl("/"));
        }
    }

    @Nested
    @DisplayName("membre connecte : la preuve")
    class PreuveDuMembre {

        @Test
        @DisplayName("« Personnaliser » ecrit une preuve par finalite, a l'etat exact des cases")
        void preuveParFinalite() throws Exception {
            Utilisateur marie = membre();

            mvc.perform(post("/cookies/preferences")
                            .param("action", "personnaliser")
                            .param("analytique", "true")
                            .with(user(marie.getEmail()).roles("MEMBRE"))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection());

            List<Consentement> analytique =
                    preuves(marie.getEmail(), TypeDocumentConsentement.COOKIES_ANALYTIQUE);
            List<Consentement> marketing =
                    preuves(marie.getEmail(), TypeDocumentConsentement.COOKIES_MARKETING);

            assertThat(analytique).hasSize(1);
            assertThat(analytique.get(0).isAccorde()).isTrue();
            assertThat(analytique.get(0).getAdresseIp()).isNotBlank();
            assertThat(analytique.get(0).getDateConsentement()).isNotNull();
            assertThat(analytique.get(0).getVersionAcceptee())
                    .isEqualTo(Consentement.COOKIES_VERSION_COURANTE);

            assertThat(marketing).hasSize(1);
            assertThat(marketing.get(0).isAccorde()).isFalse();
        }

        /**
         * Le coeur du modele : une preuve qu on ecrase cesse d etre une preuve. Deux
         * passages successifs par « Gerer mes cookies » doivent laisser deux lignes par
         * finalite, la premiere inchangee.
         */
        @Test
        @DisplayName("changer d'avis ajoute des preuves sans modifier les precedentes")
        void appendEtNonUpdate() throws Exception {
            Utilisateur marie = membre();

            mvc.perform(post("/cookies/preferences").param("action", "accepter")
                    .with(user(marie.getEmail()).roles("MEMBRE")).with(csrf()));

            List<Consentement> apresPremierChoix =
                    preuves(marie.getEmail(), TypeDocumentConsentement.COOKIES_ANALYTIQUE);
            assertThat(apresPremierChoix).hasSize(1);
            Long idInitial = apresPremierChoix.get(0).getId();

            mvc.perform(post("/cookies/preferences").param("action", "refuser")
                    .with(user(marie.getEmail()).roles("MEMBRE")).with(csrf()));

            List<Consentement> apresSecondChoix =
                    preuves(marie.getEmail(), TypeDocumentConsentement.COOKIES_ANALYTIQUE);
            assertThat(apresSecondChoix).hasSize(2);
            assertThat(apresSecondChoix.get(0).getId()).isEqualTo(idInitial);
            assertThat(apresSecondChoix.get(0).isAccorde())
                    .as("la preuve initiale reste intacte")
                    .isTrue();
            assertThat(apresSecondChoix.get(1).isAccorde())
                    .as("le nouveau choix s'ajoute")
                    .isFalse();
        }

        @Test
        @DisplayName("aucune preuve n'est ecrite pour les cookies strictement necessaires")
        void aucunePreuvePourLesNecessaires() throws Exception {
            Utilisateur marie = membre();

            mvc.perform(post("/cookies/preferences").param("action", "accepter")
                    .with(user(marie.getEmail()).roles("MEMBRE")).with(csrf()));

            assertThat(preuves(marie.getEmail(), TypeDocumentConsentement.COOKIES)).isEmpty();
        }
    }

    @Nested
    @DisplayName("ecran de gestion permanent")
    class EcranDeGestion {

        @Test
        @DisplayName("s'ouvre sans connexion et presente les trois actions au meme niveau")
        void ecranPublic() throws Exception {
            String page = mvc.perform(get("/cookies"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(page).contains("value=\"accepter\"", "value=\"refuser\"", "value=\"personnaliser\"");
            // Les trois actions portent la meme classe, donc les memes dimensions et le
            // meme contraste : refuser doit etre aussi facile qu accepter.
            assertThat(page.split("btn-cookies", -1)).hasSizeGreaterThanOrEqualTo(4);
        }

        @Test
        @DisplayName("les finalites optionnelles sont decochees tant qu'aucun choix n'est fait")
        void aucuneCasePreCochee() throws Exception {
            String page = mvc.perform(get("/cookies"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(caseCochee(page, "analytique")).isFalse();
            assertThat(caseCochee(page, "marketing")).isFalse();
        }

        @Test
        @DisplayName("les cases refletent le choix deja exprime")
        void casesRefletentLeChoix() throws Exception {
            String page = mvc.perform(get("/cookies")
                            .cookie(choixDejaFait(new PreferencesCookies(true, false))))
                    .andReturn().getResponse().getContentAsString();

            assertThat(caseCochee(page, "analytique")).isTrue();
            assertThat(caseCochee(page, "marketing")).isFalse();
        }

        @Test
        @DisplayName("les cookies necessaires sont presentes comme non desactivables")
        void necessairesVerrouilles() throws Exception {
            String page = mvc.perform(get("/cookies"))
                    .andReturn().getResponse().getContentAsString();

            // Case cochee ET desactivee, et aucun parametre correspondant : l ecran ne
            // peut pas laisser croire a un choix que le serveur ne lit pas.
            assertThat(page).contains("disabled");
            assertThat(page).doesNotContain("name=\"necessaires\"");
        }
    }
}
