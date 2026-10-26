package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.repository.EvenementSecuriteCompteRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Connexion en plusieurs etapes, de bout en bout, par le VRAI formulaire de connexion.
 *
 * <p>Aucun cas n emploie {@code user(...)} pour etablir la connexion : ce raccourci
 * installe une authentification complete et sauterait precisement ce qui est a prouver —
 * que le mot de passe seul n ouvre rien. C est la lecon du registre sur les tests qui
 * court-circuitent une couche.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Connexion avec second facteur (integration)")
class ConnexionSecondFacteurIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test d integration";
    private static final byte[] SECRET = "secret de vingt octs".getBytes();

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private EvenementSecuriteCompteRepository evenements;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ChiffrementSecretMfa chiffrement;
    @Autowired private SessionRegistry registre;
    @Autowired private Clock horloge;
    @Autowired private JdbcTemplate jdbc;

    private Utilisateur creer(TypeUtilisateur type, boolean enrole, boolean doitChanger) {
        Utilisateur u = new Utilisateur("mfa-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode(MOT_DE_PASSE), "Nom", "Prenom", type);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        if (doitChanger) {
            u.exigerChangementMotDePasse();
        }
        u = utilisateurs.saveAndFlush(u);
        if (enrole) {
            ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(SECRET, u.getId());
            u.commencerEnrolement(chiffre.donnees(), chiffre.version());
            u.confirmerEnrolement(horloge.instant(), Totp.pasDe(horloge.instant()) - 10);
            u = utilisateurs.saveAndFlush(u);
        }
        return u;
    }

    private Utilisateur relire(Utilisateur u) {
        return utilisateurs.findById(u.getId()).orElseThrow();
    }

    private String codeCourant(byte[] secret) {
        return Totp.code(secret, Totp.pasDe(horloge.instant()));
    }

    private MvcResult connecter(Utilisateur u, String motDePasse) throws Exception {
        return mvc.perform(post("/connexion").with(csrf())
                        .param("email", u.getEmail()).param("password", motDePasse))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    private static MockHttpSession session(MvcResult resultat) {
        return (MockHttpSession) resultat.getRequest().getSession(false);
    }

    private MvcResult saisirCode(MockHttpSession session, String code) throws Exception {
        return mvc.perform(post("/connexion/etape/second-facteur").session(session).with(csrf())
                .locale(Locale.FRENCH).param("code", code)).andReturn();
    }

    @Test
    @DisplayName("connexion partielle : une route publique la voit anonyme, l'étape reste reprenable")
    void partielleAnonymeSurLesRoutesPubliques() throws Exception {
        Utilisateur membre = creer(TypeUtilisateur.MEMBRE, true, false);
        MockHttpSession session = session(connecter(membre, MOT_DE_PASSE));

        // Route publique MUTANTE : le choix des cookies ne s ecrit en base que pour un
        // compte connecte. Une connexion partielle ne doit pas agir au nom du compte.
        mvc.perform(post("/cookies/preferences").session(session).with(csrf())
                        .param("action", "accepter"))
                .andExpect(status().is3xxRedirection());
        assertThat(consentementsCookies(membre)).isZero();

        // Route publique LUE : l en-tete ne la traite pas comme une connexion.
        mvc.perform(get("/").session(session).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/mon-compte\""))));

        // Le contexte anonyme n a pas ete enregistre : l etape se reprend et aboutit.
        MvcResult fin = saisirCode(session, codeCourant(SECRET));
        assertThat(fin.getResponse().getRedirectedUrl()).isEqualTo("/mon-compte");

        // Contre-epreuve : connexion aboutie, le meme geste ecrit bien la preuve.
        mvc.perform(post("/cookies/preferences").session(session).with(csrf())
                        .param("action", "accepter"))
                .andExpect(status().is3xxRedirection());
        assertThat(consentementsCookies(membre)).isPositive();
    }

    private Integer consentementsCookies(Utilisateur membre) {
        return jdbc.queryForObject("SELECT count(*) FROM consentement WHERE utilisateur_id = ? "
                + "AND type_document LIKE 'COOKIES%'", Integer.class, membre.getId());
    }

    @Test
    @DisplayName("la date d'activation suit la locale du rendu, en NL comme en EN")
    void dateDActivationLocalisee() throws Exception {
        Utilisateur membre = creer(TypeUtilisateur.MEMBRE, true, false);
        // Meme fuseau que #temporals, qui formate un Instant dans le fuseau du systeme.
        var activeLe = relire(membre).getMfaActiveLe().atZone(ZoneId.systemDefault());

        for (Locale langue : List.of(Locale.of("nl"), Locale.ENGLISH)) {
            String attendu = DateTimeFormatter.ofPattern("d MMMM yyyy", langue).format(activeLe);
            mvc.perform(get("/mon-profil/securite").locale(langue)
                            .with(user(membre.getEmail()).roles("MEMBRE")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(attendu)));
        }
    }

    @Test
    @DisplayName("un membre sans second facteur se connecte comme avant, sans étape")
    void membreSansEtape() throws Exception {
        Utilisateur membre = creer(TypeUtilisateur.MEMBRE, false, false);

        assertThat(connecter(membre, MOT_DE_PASSE).getResponse().getRedirectedUrl())
                .isEqualTo("/mon-compte");
        assertThat(relire(membre).getDerniereConnexion()).isNotNull();
    }

    /**
     * Le coeur du lot : le mot de passe seul n ouvre ni le back-office, ni l espace
     * membre. Chaque refus renvoie a l etape, et aucun ne rend la page demandee.
     */
    @Test
    @DisplayName("en état partiel, /admin/** et l'espace membre sont refusés et renvoient à l'étape")
    void etatPartielRefuse() throws Exception {
        Utilisateur admin = creer(TypeUtilisateur.ADMINISTRATEUR, true, false);
        MvcResult apresMotDePasse = connecter(admin, MOT_DE_PASSE);
        assertThat(apresMotDePasse.getResponse().getRedirectedUrl())
                .isEqualTo("/connexion/etape/second-facteur");
        MockHttpSession session = session(apresMotDePasse);

        for (String route : List.of("/admin/comptes", "/admin/rendez-vous", "/mon-compte", "/panier",
                "/mon-profil/securite")) {
            mvc.perform(get(route).session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(EtapeConnexion.PREFIXE));
        }
        mvc.perform(get(EtapeConnexion.PREFIXE).session(session))
                .andExpect(redirectedUrl("/connexion/etape/second-facteur"));
        // La derniere connexion n est pas datee au mot de passe : elle ne l est qu a
        // l aboutissement.
        assertThat(relire(admin).getDerniereConnexion()).isNull();
    }

    @Test
    @DisplayName("le bon code fait aboutir la connexion, sous un nouvel identifiant de session")
    void aboutissement() throws Exception {
        Utilisateur admin = creer(TypeUtilisateur.ADMINISTRATEUR, true, false);
        MockHttpSession session = session(connecter(admin, MOT_DE_PASSE));
        String identifiantPartiel = session.getId();

        MvcResult resultat = saisirCode(session, codeCourant(SECRET));

        assertThat(resultat.getResponse().getRedirectedUrl()).isEqualTo("/admin");
        assertThat(session.getId()).isNotEqualTo(identifiantPartiel);
        mvc.perform(get("/admin/comptes").session(session)).andExpect(status().isOk());
        assertThat(relire(admin).getDerniereConnexion()).isNotNull();
    }

    @Test
    @DisplayName("un code déjà accepté est refusé à la connexion suivante")
    void rejeuRefuse() throws Exception {
        Utilisateur admin = creer(TypeUtilisateur.ADMINISTRATEUR, true, false);
        String code = codeCourant(SECRET);
        saisirCode(session(connecter(admin, MOT_DE_PASSE)), code);

        MockHttpSession seconde = session(connecter(admin, MOT_DE_PASSE));
        MvcResult rejeu = saisirCode(seconde, code);

        assertThat(rejeu.getResponse().getStatus()).isEqualTo(200);
        assertThat(rejeu.getResponse().getContentAsString()).contains("role=\"alert\"");
        mvc.perform(get("/admin/comptes").session(seconde)).andExpect(redirectedUrl(EtapeConnexion.PREFIXE));
    }

    @Test
    @DisplayName("cinq codes faux verrouillent le compte, journalisent, et le mot de passe ne suffit plus")
    void verrouillage() throws Exception {
        Utilisateur admin = creer(TypeUtilisateur.ADMINISTRATEUR, true, false);
        MockHttpSession session = session(connecter(admin, MOT_DE_PASSE));
        for (int i = 0; i < 4; i++) {
            assertThat(saisirCode(session, "000000").getResponse().getStatus()).isEqualTo(200);
        }

        assertThat(saisirCode(session, "000000").getResponse().getRedirectedUrl())
                .isEqualTo("/connexion?bloque");

        assertThat(relire(admin).estVerrouille(horloge.instant())).isTrue();
        assertThat(evenements.evenementsDe(admin.getId()))
                .extracting(e -> e.getType())
                .containsOnlyOnce(TypeEvenementSecurite.SECOND_FACTEUR_VERROUILLAGE)
                .filteredOn(t -> t == TypeEvenementSecurite.SECOND_FACTEUR_ECHEC).hasSize(5);
        assertThat(connecter(admin, MOT_DE_PASSE).getResponse().getRedirectedUrl())
                .isEqualTo("/connexion?bloque");
    }

    @Test
    @DisplayName("un administrateur non enrôlé est conduit à l'enrôlement, puis à ses codes de secours")
    void enrolementForce() throws Exception {
        Utilisateur admin = creer(TypeUtilisateur.ADMINISTRATEUR, false, false);
        MvcResult apresMotDePasse = connecter(admin, MOT_DE_PASSE);
        assertThat(apresMotDePasse.getResponse().getRedirectedUrl()).isEqualTo("/connexion/etape/enrolement");
        MockHttpSession session = session(apresMotDePasse);

        mvc.perform(get("/connexion/etape/enrolement").session(session).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<svg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("one-time-code")));
        mvc.perform(get("/admin/comptes").session(session)).andExpect(redirectedUrl(EtapeConnexion.PREFIXE));

        Utilisateur enAttente = relire(admin);
        byte[] secret = chiffrement.dechiffrer(enAttente.getMfaSecretChiffre(),
                enAttente.getMfaCleVersion(), enAttente.getId());
        MvcResult confirmation = mvc.perform(post("/connexion/etape/enrolement").session(session)
                .with(csrf()).param("code", codeCourant(secret))).andReturn();

        assertThat(confirmation.getResponse().getRedirectedUrl()).isEqualTo("/mon-profil/securite/codes");
        assertThat((List<?>) confirmation.getFlashMap().get("codesSecours")).hasSize(8);
        assertThat(relire(admin).secondFacteurActif()).isTrue();
        mvc.perform(get("/admin/comptes").session(session)).andExpect(status().isOk());
    }

    /**
     * Ordre impose : pour un compte deja enrole, le second facteur precede le changement.
     * Sinon, qui ne detient que le mot de passe remis pourrait le remplacer par le sien.
     */
    @Test
    @DisplayName("changement imposé : après le second facteur, et il bloque toutes les routes")
    void changementImposeApresSecondFacteur() throws Exception {
        Utilisateur chef = creer(TypeUtilisateur.SUPER_ADMINISTRATEUR, true, true);
        MockHttpSession session = session(connecter(chef, MOT_DE_PASSE));

        mvc.perform(get("/connexion/etape/mot-de-passe").session(session))
                .andExpect(redirectedUrl("/connexion/etape/second-facteur"));
        assertThat(saisirCode(session, codeCourant(SECRET)).getResponse().getRedirectedUrl())
                .isEqualTo("/connexion/etape/mot-de-passe");
        mvc.perform(get("/admin/comptes").session(session)).andExpect(redirectedUrl(EtapeConnexion.PREFIXE));
        mvc.perform(get("/connexion/etape/mot-de-passe").session(session).locale(Locale.FRENCH))
                .andExpect(status().isOk());

        // Le mot de passe remis ne leve pas l obligation.
        mvc.perform(post("/connexion/etape/mot-de-passe").session(session).with(csrf())
                        .locale(Locale.FRENCH)
                        .param("motDePasse", MOT_DE_PASSE).param("confirmation", MOT_DE_PASSE))
                .andExpect(status().isOk());
        assertThat(relire(chef).doitChangerMotDePasse()).isTrue();

        String nouveau = "une toute nouvelle phrase choisie";
        mvc.perform(post("/connexion/etape/mot-de-passe").session(session).with(csrf())
                        .param("motDePasse", nouveau).param("confirmation", nouveau))
                .andExpect(redirectedUrl("/admin"));
        assertThat(relire(chef).doitChangerMotDePasse()).isFalse();
        mvc.perform(get("/admin/comptes").session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("exploitant neuf : mot de passe d'abord, enrôlement ensuite")
    void exploitantNeuf() throws Exception {
        Utilisateur chef = creer(TypeUtilisateur.SUPER_ADMINISTRATEUR, false, true);
        MvcResult apresMotDePasse = connecter(chef, MOT_DE_PASSE);
        assertThat(apresMotDePasse.getResponse().getRedirectedUrl()).isEqualTo("/connexion/etape/mot-de-passe");

        String nouveau = "la phrase que le chef a choisie";
        mvc.perform(post("/connexion/etape/mot-de-passe").session(session(apresMotDePasse)).with(csrf())
                        .param("motDePasse", nouveau).param("confirmation", nouveau))
                .andExpect(redirectedUrl("/connexion/etape/enrolement"));
    }

    @Test
    @DisplayName("activer le second facteur ferme les autres sessions du compte, pas la sienne")
    void activationCoupeLesAutresSessions() throws Exception {
        Utilisateur membre = creer(TypeUtilisateur.MEMBRE, false, false);
        MockHttpSession ici = session(connecter(membre, MOT_DE_PASSE));
        MockHttpSession ailleurs = session(connecter(membre, MOT_DE_PASSE));

        mvc.perform(get("/mon-profil/securite/enrolement").session(ici)).andExpect(status().isOk());
        Utilisateur enAttente = relire(membre);
        byte[] secret = chiffrement.dechiffrer(enAttente.getMfaSecretChiffre(),
                enAttente.getMfaCleVersion(), enAttente.getId());
        mvc.perform(post("/mon-profil/securite/enrolement").session(ici).with(csrf())
                        .param("code", codeCourant(secret)))
                .andExpect(redirectedUrl("/mon-profil/securite/codes"));

        assertThat(registre.getSessionInformation(ailleurs.getId()).isExpired()).isTrue();
        assertThat(registre.getSessionInformation(ici.getId()).isExpired()).isFalse();
        mvc.perform(get("/mon-compte").session(ailleurs)).andExpect(redirectedUrl("/connexion"));
        mvc.perform(get("/mon-compte").session(ici)).andExpect(status().isOk());
    }

    /**
     * Le champ de secours est un second formulaire vers la meme route : le code y passe
     * par le meme service, donc le meme journal et le meme compteur d echecs.
     */
    @Test
    @DisplayName("code de secours par son champ distinct : accepté une fois, refus rouvrant le champ")
    void champDeSecours() throws Exception {
        Utilisateur admin = creer(TypeUtilisateur.ADMINISTRATEUR, false, false);
        MockHttpSession enrolement = session(connecter(admin, MOT_DE_PASSE));
        mvc.perform(get("/connexion/etape/enrolement").session(enrolement));
        Utilisateur enAttente = relire(admin);
        byte[] secret = chiffrement.dechiffrer(enAttente.getMfaSecretChiffre(),
                enAttente.getMfaCleVersion(), enAttente.getId());
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) mvc.perform(post("/connexion/etape/enrolement")
                        .session(enrolement).with(csrf()).param("code", codeCourant(secret)))
                .andReturn().getFlashMap().get("codesSecours");

        MockHttpSession session = session(connecter(admin, MOT_DE_PASSE));
        mvc.perform(get("/connexion/etape/second-facteur").session(session).locale(Locale.FRENCH))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("autocomplete=\"off\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<details open"))));

        MvcResult refus = mvc.perform(post("/connexion/etape/second-facteur").session(session).with(csrf())
                .locale(Locale.FRENCH).param("mode", "secours").param("code", "ZZZZ-ZZZZ")).andReturn();
        assertThat(refus.getResponse().getContentAsString()).contains("<details open");
        assertThat(relire(admin).getMfaEchecs()).isEqualTo((short) 1);

        MvcResult accepte = mvc.perform(post("/connexion/etape/second-facteur").session(session).with(csrf())
                .param("mode", "secours").param("code", codes.get(0).toLowerCase())).andReturn();
        assertThat(accepte.getResponse().getRedirectedUrl()).isEqualTo("/admin");
        assertThat(evenements.evenementsDe(admin.getId())).extracting(e -> e.getType())
                .contains(TypeEvenementSecurite.CODE_SECOURS_CONSOMME);

        MockHttpSession rejeu = session(connecter(admin, MOT_DE_PASSE));
        assertThat(mvc.perform(post("/connexion/etape/second-facteur").session(rejeu).with(csrf())
                        .param("mode", "secours").param("code", codes.get(0))).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("écran de sécurité : code de secours par son champ distinct, refus rouvrant le bon champ")
    void securiteChampDeSecours() throws Exception {
        Utilisateur membre = creer(TypeUtilisateur.MEMBRE, false, false);
        MockHttpSession session = session(connecter(membre, MOT_DE_PASSE));
        mvc.perform(get("/mon-profil/securite/enrolement").session(session));
        Utilisateur enAttente = relire(membre);
        byte[] secret = chiffrement.dechiffrer(enAttente.getMfaSecretChiffre(),
                enAttente.getMfaCleVersion(), enAttente.getId());
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) mvc.perform(post("/mon-profil/securite/enrolement")
                        .session(session).with(csrf()).param("code", codeCourant(secret)))
                .andReturn().getFlashMap().get("codesSecours");

        String page = mvc.perform(get("/mon-profil/securite").session(session).locale(Locale.FRENCH))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("id=\"secours-codes\"", "id=\"secours-retrait\"")
                .doesNotContain("<details open");

        MvcResult refus = mvc.perform(post("/mon-profil/securite/desactivation").session(session)
                        .with(csrf()).param("mode", "secours")
                        .param("motDePasse", MOT_DE_PASSE).param("code", "ZZZZ-ZZZZ"))
                .andExpect(redirectedUrl("/mon-profil/securite")).andReturn();
        assertThat(refus.getFlashMap().get("secoursOuvert")).isEqualTo("desactivation");
        assertThat(relire(membre).getMfaEchecs()).isEqualTo((short) 1);
        String rouverte = mvc.perform(get("/mon-profil/securite").session(session).locale(Locale.FRENCH)
                        .flashAttr("secoursOuvert", "desactivation"))
                .andReturn().getResponse().getContentAsString();
        assertThat(rouverte.split("<details open", -1)).hasSize(2);

        mvc.perform(post("/mon-profil/securite/codes").session(session).with(csrf())
                        .param("mode", "secours").param("motDePasse", MOT_DE_PASSE)
                        .param("code", codes.get(1)))
                .andExpect(redirectedUrl("/mon-profil/securite/codes"));
        assertThat(evenements.evenementsDe(membre.getId())).extracting(e -> e.getType())
                .contains(TypeEvenementSecurite.CODE_SECOURS_CONSOMME,
                        TypeEvenementSecurite.CODES_SECOURS_REGENERES);
    }

    @Test
    @DisplayName("le membre ne retire son second facteur qu'avec mot de passe et code valide")
    void desactivationParLeMembre() throws Exception {
        Utilisateur membre = creer(TypeUtilisateur.MEMBRE, true, false);
        MockHttpSession session = session(connecter(membre, MOT_DE_PASSE));
        saisirCode(session, codeCourant(SECRET));

        mvc.perform(post("/mon-profil/securite/desactivation").session(session).with(csrf())
                        .param("motDePasse", MOT_DE_PASSE).param("code", "000000"))
                .andExpect(redirectedUrl("/mon-profil/securite"));
        assertThat(relire(membre).secondFacteurActif()).isTrue();
        assertThat(relire(membre).getMfaEchecs()).isEqualTo((short) 1);

        String suivant = Totp.code(SECRET, Totp.pasDe(horloge.instant()) + 1);
        mvc.perform(post("/mon-profil/securite/desactivation").session(session).with(csrf())
                        .param("motDePasse", MOT_DE_PASSE).param("code", suivant))
                .andExpect(redirectedUrl("/mon-profil/securite"));
        assertThat(relire(membre).secondFacteurActif()).isFalse();
    }
}
