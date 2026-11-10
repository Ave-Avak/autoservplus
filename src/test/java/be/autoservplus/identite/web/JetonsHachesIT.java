package be.autoservplus.identite.web;

import be.autoservplus.communication.service.OrigineMotDePasse;
import be.autoservplus.communication.service.ServiceCourriel;
import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.InscriptionService;
import be.autoservplus.identite.service.JetonLien;
import be.autoservplus.identite.service.MotDePasseService;
import be.autoservplus.support.SocleIntegration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Jetons des liens de courriel stockés par empreinte (V46), de bout en bout.
 *
 * <p>Le jeton en clair ne se lit que dans le lien envoyé : les cas le capturent sur
 * {@link ServiceCourriel}, jamais en base, comme le ferait le titulaire.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Jetons des liens de courriel (intégration)")
class JetonsHachesIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe du test";
    private static final String NOUVEAU = "nouvelle phrase choisie par marie";

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SessionRegistry registre;
    @Autowired private InscriptionService inscription;
    @Autowired private MotDePasseService motDePasse;
    @MockitoSpyBean private ServiceCourriel courriel;
    @Value("${spring.datasource.url}") private String urlBase;
    @Value("${spring.datasource.username}") private String identifiant;
    @Value("${spring.datasource.password}") private String secret;

    private static String adresse() {
        return "jeton-" + COMPTEUR.getAndIncrement() + "@exemple.be";
    }

    private static String jetonDuLien(String lien) {
        return lien.substring(lien.indexOf("jeton=") + "jeton=".length());
    }

    private Utilisateur membreVerifie(String email) {
        Utilisateur membre = new Utilisateur(email, encodeur.encode(MOT_DE_PASSE),
                "Dupont", "Marie", TypeUtilisateur.MEMBRE);
        membre.confirmerAdresseEmail();
        return utilisateurs.saveAndFlush(membre);
    }

    private Utilisateur relire(String email) {
        return utilisateurs.findByEmailIgnoreCase(email).orElseThrow();
    }

    private String lienDeReinitialisation() {
        ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
        verify(courriel, atLeastOnce()).envoyerReinitialisationMotDePasse(any(), lien.capture());
        return lien.getValue();
    }

    private String dernierLienDeVerification() {
        ArgumentCaptor<String> lien = ArgumentCaptor.forClass(String.class);
        verify(courriel, atLeastOnce()).envoyerVerificationAdresse(any(), lien.capture());
        return lien.getAllValues().get(lien.getAllValues().size() - 1);
    }

    @Nested
    @DisplayName("réinitialisation du mot de passe")
    class Reinitialisation {

        @Test
        @DisplayName("lien suivi : mot de passe changé, sessions fermées, journal et avis, lien consommé")
        void parcoursComplet() throws Exception {
            String email = adresse();
            membreVerifie(email);
            registre.registerNewSession("session-ouverte-ailleurs",
                    User.withUsername(email).password("x").roles("MEMBRE").build());

            motDePasse.demanderReinitialisation(email);
            String lien = lienDeReinitialisation();
            String jeton = jetonDuLien(lien);

            assertThat(lien).startsWith("http").contains("/mot-de-passe/nouveau?jeton=");
            assertThat(relire(email).getJetonReinitialisationHache())
                    .isEqualTo(JetonLien.empreinte(jeton)).isNotEqualTo(jeton);
            assertThat(relire(email).getJetonVerificationHache()).isNull();

            mvc.perform(get("/mot-de-passe/nouveau").param("jeton", jeton))
                    .andExpect(view().name("identite/mot-de-passe-nouveau"));
            mvc.perform(post("/mot-de-passe/nouveau").with(csrf())
                            .param("jeton", jeton).param("motDePasse", NOUVEAU)
                            .param("confirmation", NOUVEAU).locale(Locale.FRENCH))
                    .andExpect(view().name("identite/mot-de-passe-modifie"))
                    // La réinitialisation n'active plus aucun compte : la page ne le dit plus.
                    .andExpect(content().string(allOf(
                            containsString("Vous pouvez vous connecter avec votre nouveau mot de passe."),
                            not(containsString("Votre compte est actif")))));

            Utilisateur apres = relire(email);
            assertThat(encodeur.matches(NOUVEAU, apres.getMotDePasseHache())).isTrue();
            assertThat(apres.getJetonReinitialisationHache()).isNull();

            SessionInformation ailleurs = registre.getSessionInformation("session-ouverte-ailleurs");
            assertThat(ailleurs.isExpired()).as("la session ouverte ailleurs est fermée").isTrue();

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM evenement_securite_compte "
                            + "WHERE utilisateur_id = ? AND type = 'MOT_DE_PASSE_REINITIALISE' "
                            + "AND auteur_id = utilisateur_id",
                    Integer.class, apres.getId())).isEqualTo(1);
            verify(courriel).envoyerAvisMotDePasseModifie(
                    any(), eq(OrigineMotDePasse.REINITIALISATION));

            // Usage unique : le même lien ne sert plus.
            mvc.perform(get("/mot-de-passe/nouveau").param("jeton", jeton))
                    .andExpect(view().name("identite/mot-de-passe-lien-invalide"));
        }

        @Test
        @DisplayName("un lien de vérification n'ouvre pas le formulaire de nouveau mot de passe")
        void verificationNOuvrePasLaReinitialisation() throws Exception {
            String email = adresse();
            inscription.inscrire(email, MOT_DE_PASSE, "Dupont", "Marie", Langue.fr);
            String jeton = jetonDuLien(dernierLienDeVerification());

            mvc.perform(get("/mot-de-passe/nouveau").param("jeton", jeton))
                    .andExpect(view().name("identite/mot-de-passe-lien-invalide"));
            mvc.perform(post("/mot-de-passe/nouveau").with(csrf())
                            .param("jeton", jeton).param("motDePasse", NOUVEAU)
                            .param("confirmation", NOUVEAU))
                    .andExpect(view().name("identite/mot-de-passe-lien-invalide"));
            assertThat(encodeur.matches(NOUVEAU, relire(email).getMotDePasseHache())).isFalse();
        }

        @Test
        @DisplayName("un compte non vérifié reçoit un rappel de vérification, sans jeton de réinitialisation")
        void compteNonVerifie() {
            String email = adresse();
            inscription.inscrire(email, MOT_DE_PASSE, "Dupont", "Marie", Langue.fr);

            motDePasse.demanderReinitialisation(email);

            verify(courriel).envoyerRappelVerification(any(), any());
            assertThat(relire(email).getJetonReinitialisationHache()).isNull();
            assertThat(relire(email).getJetonVerificationHache()).isNotNull();
        }
    }

    @Nested
    @DisplayName("renvoi de la vérification")
    class Renvoi {

        @Test
        @DisplayName("le renvoi émet un jeton neuf et l'ancien lien cesse de valoir")
        void ancienLienInvalide() throws Exception {
            String email = adresse();
            inscription.inscrire(email, MOT_DE_PASSE, "Dupont", "Marie", Langue.fr);
            String ancien = jetonDuLien(dernierLienDeVerification());

            inscription.renvoyerVerification(email);
            String nouveau = jetonDuLien(dernierLienDeVerification());
            assertThat(nouveau).isNotEqualTo(ancien);

            mvc.perform(get("/inscription/verification").param("jeton", ancien))
                    .andExpect(model().attribute("succes", false));
            assertThat(relire(email).isEmailVerifie()).isFalse();

            mvc.perform(get("/inscription/verification").param("jeton", nouveau))
                    .andExpect(model().attribute("succes", true));
            assertThat(relire(email).isEmailVerifie()).isTrue();
        }
    }

    /**
     * Un lien refusé s'explique par un message traduit, jamais par celui de l'exception,
     * technique, en français, et qui nommait le jeton refusé.
     */
    @Nested
    @DisplayName("rendu d'un lien refusé")
    class Rendu {

        private static final String INTROUVABLE = "introuvable";

        @Test
        @DisplayName("lien de vérification inconnu : message traduit en FR, NL et EN")
        void verificationInconnue() throws Exception {
            Map<Locale, String> attendus = Map.of(
                    Locale.FRENCH, "Ce lien n'est pas valable.",
                    Locale.forLanguageTag("nl"), "Deze link is niet geldig.",
                    Locale.ENGLISH, "This link is not valid.");
            for (Map.Entry<Locale, String> cas : attendus.entrySet()) {
                mvc.perform(get("/inscription/verification").param("jeton", "inconnu")
                                .locale(cas.getKey()))
                        .andExpect(content().string(allOf(
                                containsString(cas.getValue().replace("'", "&#39;")),
                                not(containsString(INTROUVABLE)))));
            }
        }

        @Test
        @DisplayName("lien de réinitialisation inconnu : message traduit en FR, NL et EN")
        void reinitialisationInconnue() throws Exception {
            Map<Locale, String> attendus = Map.of(
                    Locale.FRENCH, "Ce lien n'est pas valable.",
                    Locale.forLanguageTag("nl"), "Deze link is niet geldig.",
                    Locale.ENGLISH, "This link is not valid.");
            for (Map.Entry<Locale, String> cas : attendus.entrySet()) {
                mvc.perform(get("/mot-de-passe/nouveau").param("jeton", "inconnu")
                                .locale(cas.getKey()))
                        .andExpect(view().name("identite/mot-de-passe-lien-invalide"))
                        .andExpect(content().string(allOf(
                                // Thymeleaf échappe l'apostrophe à l'affichage.
                                containsString(cas.getValue().replace("'", "&#39;")),
                                not(containsString(INTROUVABLE)))));
            }
        }

        @Test
        @DisplayName("lien de réinitialisation expiré : message d'expiration traduit")
        void reinitialisationExpiree() throws Exception {
            String email = adresse();
            Utilisateur membre = membreVerifie(email);
            JetonLien jeton = JetonLien.tirer();
            membre.preparerReinitialisation(jeton.empreinte(), Instant.now().minusSeconds(1));
            utilisateurs.saveAndFlush(membre);

            mvc.perform(get("/mot-de-passe/nouveau").param("jeton", jeton.clair())
                            .locale(Locale.ENGLISH))
                    .andExpect(content().string(allOf(
                            containsString("This link has expired. Request a new one."),
                            not(containsString("expire. Demandez")))));
        }
    }

    /**
     * Les jetons déjà en base au déploiement. Une base séparée, dans le même conteneur,
     * est amenée à V45 par Flyway. Elle reçoit des jetons tels que l'ancien code les
     * écrivait, en clair, puis la vraie V46 s'applique. Les valeurs converties sont
     * ensuite reportées dans la base de l'application, où les contrôleurs réels
     * rejouent les liens avec les jetons d'origine.
     */
    @Nested
    @DisplayName("conversion V46 des jetons en attente")
    class Conversion {

        /** Ce que produisait l'ancien code : 32 octets aléatoires en hexadécimal, stockés tels quels. */
        private static String jetonAncienCode() {
            byte[] octets = new byte[32];
            new SecureRandom().nextBytes(octets);
            return HexFormat.of().formatHex(octets);
        }

        private JdbcTemplate baseAuStadeV45() {
            String nom = "conversion_v46_" + COMPTEUR.getAndIncrement();
            jdbc.execute("CREATE DATABASE " + nom);
            String url = urlBase.replaceFirst("/[^/?]+(\\?|$)", "/" + nom + "$1");
            DriverManagerDataSource source = new DriverManagerDataSource(url, identifiant, secret);
            Flyway.configure().dataSource(source).locations("classpath:db/migration")
                    .target("45").load().migrate();
            return new JdbcTemplate(source);
        }

        private void migrerJusquAuBout(JdbcTemplate base) {
            Flyway.configure().dataSource(base.getDataSource())
                    .locations("classpath:db/migration").load().migrate();
        }

        private void inserer(JdbcTemplate base, String email, boolean verifie, String colonne,
                             String jeton, Instant expiration) {
            String enAttente = colonne.equals("jeton_changement_email")
                    ? ", email_en_attente, jeton_changement_expiration" : ", jeton_expiration";
            String valeurs = colonne.equals("jeton_changement_email") ? ", ?, ?" : ", ?";
            Object[] parametres = colonne.equals("jeton_changement_email")
                    ? new Object[]{email, verifie, jeton, "nouvelle-" + email, Timestamp.from(expiration)}
                    : new Object[]{email, verifie, jeton, expiration == null ? null : Timestamp.from(expiration)};
            base.update("INSERT INTO utilisateur (type_utilisateur, email, mot_de_passe_hache, "
                    + "nom, prenom, statut, email_verifie, " + colonne + enAttente + ") "
                    + "VALUES ('MEMBRE', ?, 'x', 'Dupont', 'Marie', "
                    + (verifie ? "'ACTIF'" : "'EN_ATTENTE_VALIDATION'") + ", ?, ?" + valeurs + ")",
                    parametres);
        }

        private Map<String, Object> ligne(JdbcTemplate base, String email) {
            return base.queryForMap("SELECT * FROM utilisateur WHERE email = ?", email);
        }

        @Test
        @DisplayName("réinitialisation déplacée, vérification et changement hachés en place, liens encore acceptés")
        void conversionPuisLiensAcceptes() throws Exception {
            JdbcTemplate base = baseAuStadeV45();
            Instant echeance = Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
            String reinit = jetonAncienCode();
            String verif = jetonAncienCode();
            String changement = jetonAncienCode();

            inserer(base, "verifie@exemple.be", true, "jeton_verification", reinit, echeance);
            inserer(base, "non-verifie@exemple.be", false, "jeton_verification", verif, echeance);
            inserer(base, "changement@exemple.be", true, "jeton_changement_email", changement, echeance);
            inserer(base, "sans-echeance@exemple.be", true, "jeton_verification", jetonAncienCode(), null);

            migrerJusquAuBout(base);

            // Compte vérifié : réinitialisation, déplacée avec son échéance.
            Map<String, Object> l1 = ligne(base, "verifie@exemple.be");
            assertThat(l1.get("jeton_reinitialisation_hache")).isEqualTo(JetonLien.empreinte(reinit));
            assertThat(((Timestamp) l1.get("jeton_reinitialisation_expiration")).toInstant()).isEqualTo(echeance);
            assertThat(l1.get("jeton_verification_hache")).isNull();
            assertThat(l1.get("jeton_expiration")).isNull();

            // Compte non vérifié : vérification, hachée en place.
            Map<String, Object> l2 = ligne(base, "non-verifie@exemple.be");
            assertThat(l2.get("jeton_verification_hache")).isEqualTo(JetonLien.empreinte(verif));
            assertThat(((Timestamp) l2.get("jeton_expiration")).toInstant()).isEqualTo(echeance);
            assertThat(l2.get("jeton_reinitialisation_hache")).isNull();

            Map<String, Object> l3 = ligne(base, "changement@exemple.be");
            assertThat(l3.get("jeton_changement_email_hache")).isEqualTo(JetonLien.empreinte(changement));

            // Un jeton sans échéance était déjà tenu pour expiré : il est effacé.
            Map<String, Object> l4 = ligne(base, "sans-echeance@exemple.be");
            assertThat(l4.get("jeton_verification_hache")).isNull();
            assertThat(l4.get("jeton_reinitialisation_hache")).isNull();

            // Les valeurs converties, reportées dans la base de l'application.
            String a = adresse();
            Utilisateur avecReinit = membreVerifie(a);
            avecReinit.preparerReinitialisation((String) l1.get("jeton_reinitialisation_hache"), echeance);
            utilisateurs.saveAndFlush(avecReinit);

            String b = adresse();
            Utilisateur enAttente = new Utilisateur(b, encodeur.encode(MOT_DE_PASSE),
                    "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            enAttente.enregistrerJetonVerification((String) l2.get("jeton_verification_hache"), echeance);
            utilisateurs.saveAndFlush(enAttente);

            String c = adresse();
            String cible = "nouvelle-" + c;
            Utilisateur changeant = membreVerifie(c);
            changeant.demanderChangementEmail(cible,
                    (String) l3.get("jeton_changement_email_hache"), echeance);
            utilisateurs.saveAndFlush(changeant);

            mvc.perform(get("/mot-de-passe/nouveau").param("jeton", reinit))
                    .andExpect(view().name("identite/mot-de-passe-nouveau"));
            mvc.perform(get("/inscription/verification").param("jeton", verif))
                    .andExpect(model().attribute("succes", true));
            assertThat(relire(b).isEmailVerifie()).isTrue();
            mvc.perform(get("/changement-adresse/confirmation").param("jeton", changement)
                            .locale(Locale.FRENCH))
                    .andExpect(model().attribute("echec", (Object) null));
            assertThat(utilisateurs.findByEmailIgnoreCase(cible)).isPresent();
        }
    }
}
