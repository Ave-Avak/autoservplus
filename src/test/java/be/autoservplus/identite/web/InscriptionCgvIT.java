package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.Consentement;
import be.autoservplus.identite.domain.TypeDocumentConsentement;
import be.autoservplus.identite.repository.ConsentementRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.identite.service.PiegeAntiBot;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.VersionsDocumentsService;
import be.autoservplus.rgpd.service.ExportDonneesService;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Acceptation des CGV à l'inscription (F1) : case obligatoire, jamais cochée par le
 * serveur, preuve versionnée en table {@code consentement}, politique de confidentialité
 * portée par un lien sans case.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Acceptation des CGV à l'inscription (intégration)")
class InscriptionCgvIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);
    private static final String MOT_DE_PASSE = "phrase de passe de la case des conditions";
    private static final Pattern CASE_CGV = Pattern.compile("<input [^>]*type=\"checkbox\" id=\"cgv\"[^>]*>");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private ConsentementRepository consentements;
    @Autowired private VersionsDocumentsService versionsDocuments;
    @Autowired private ExportDonneesService export;

    private static String adresse() {
        return "inscription-cgv-" + COMPTEUR.getAndIncrement() + "@exemple.be";
    }

    private static MockHttpServletRequestBuilder envoi(String email, String ip) {
        return post("/inscription").with(anonymous()).with(csrf())
                .header("Accept-Language", "fr")
                .with(requete -> {
                    requete.setRemoteAddr(ip);
                    return requete;
                })
                .param(PiegeAntiBot.CHAMP_HORODATAGE, String.valueOf(System.currentTimeMillis()
                        - PiegeAntiBot.DELAI_MINIMAL.plusSeconds(7).toMillis()))
                .param("email", email).param("motDePasse", MOT_DE_PASSE)
                .param("confirmationMotDePasse", MOT_DE_PASSE)
                .param("nom", "Dupont").param("prenom", "Marie").param("langue", "fr");
    }

    private String rendu(MockHttpServletRequestBuilder requete) throws Exception {
        return mvc.perform(requete).andReturn().getResponse().getContentAsString();
    }

    private static String caseCgv(String page) {
        Matcher balise = CASE_CGV.matcher(page);
        assertThat(balise.find()).as("case des CGV rendue").isTrue();
        return balise.group();
    }

    private List<Consentement> preuvesCgv(String email) {
        return consentements.findByUtilisateurEmailAndTypeDocument(email, TypeDocumentConsentement.CGV);
    }

    @Test
    @DisplayName("formulaire : case non cochée, version en vigueur affichée, confidentialité par un lien sans case")
    void formulaire() throws Exception {
        String page = rendu(get("/inscription").with(anonymous()).header("Accept-Language", "fr"));

        assertThat(caseCgv(page)).doesNotContain("checked");
        assertThat(page)
                .contains("(version " + versionsDocuments.versionCourante(TypeDocumentVersionne.CGV) + ").")
                .contains("href=\"/cgv\"")
                // Un lien suivi dans le même onglet ferait perdre la saisie.
                .contains("href=\"/cgv\" target=\"_blank\" rel=\"noopener\"")
                .contains("href=\"/confidentialite\" target=\"_blank\" rel=\"noopener\"");
        // Le bandeau des cookies porte ses propres cases : seul le formulaire compte.
        int debut = page.indexOf("action=\"/inscription\"");
        String formulaire = page.substring(debut, page.indexOf("</form>", debut));
        assertThat(formulaire.split("type=\"checkbox\"", -1)).hasSize(2);
    }

    @Test
    @DisplayName("prénom, nom et adresse en erreur : chaque message est relié à son champ")
    void erreursRelieesAuxChamps() throws Exception {
        String page = rendu(post("/inscription").with(anonymous()).with(csrf())
                .header("Accept-Language", "fr")
                .param(PiegeAntiBot.CHAMP_HORODATAGE, String.valueOf(System.currentTimeMillis()
                        - PiegeAntiBot.DELAI_MINIMAL.plusSeconds(7).toMillis()))
                .param("email", "pas-une-adresse").param("motDePasse", MOT_DE_PASSE)
                .param("confirmationMotDePasse", MOT_DE_PASSE)
                .param("nom", "").param("prenom", "").param("langue", "fr").param("cgv", "true"));

        for (String champ : List.of("prenom", "nom", "email")) {
            assertThat(page).as(champ)
                    .contains("aria-describedby=\"erreur-" + champ + "\"")
                    .contains("id=\"erreur-" + champ + "\"");
        }
    }

    @Test
    @DisplayName("case non cochée : refus sous la case, aucun compte, case toujours décochée")
    void refusSansCase() throws Exception {
        String email = adresse();

        String page = rendu(envoi(email, "203.0.113.180"));

        assertThat(page).contains("Vous devez accepter les conditions générales de vente pour créer un compte.");
        assertThat(caseCgv(page)).doesNotContain("checked").contains("aria-describedby=\"cgv-erreur\"");
        assertThat(utilisateurs.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("case forgée à faux : même refus")
    void caseForgeeAFaux() throws Exception {
        String email = adresse();

        String page = rendu(envoi(email, "203.0.113.181").param("cgv", "false"));

        assertThat(page).contains("Vous devez accepter les conditions générales de vente");
        assertThat(utilisateurs.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("case cochée : preuve de la version en vigueur, adresse IP, sans commande, reprise par l'export F22")
    void preuveEcrite() throws Exception {
        String email = adresse();

        rendu(envoi(email, "203.0.113.182").param("cgv", "true"));

        String version = versionsDocuments.versionCourante(TypeDocumentVersionne.CGV);
        List<Consentement> preuves = preuvesCgv(email);
        assertThat(preuves).singleElement().satisfies(preuve -> {
            assertThat(preuve.getVersionAcceptee()).isEqualTo(version);
            assertThat(preuve.isAccorde()).isTrue();
            assertThat(preuve.getAdresseIp()).isEqualTo("203.0.113.182");
            assertThat(preuve.getCommandeId()).isNull();
        });
        // L'export est réservé au membre lui-même : il est appelé en son nom.
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(email, null, "ROLE_MEMBRE"));
        try {
            assertThat(export.assembler(email).donneesPersonnelles().consentements())
                    .anySatisfy(ligne -> {
                        assertThat(ligne.typeDocument()).isEqualTo("CGV");
                        assertThat(ligne.versionAcceptee()).isEqualTo(version);
                    });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("adresse qui a déjà un compte : aucune preuve de plus")
    void adresseConnue() throws Exception {
        String email = adresse();
        rendu(envoi(email, "203.0.113.183").param("cgv", "true"));

        rendu(envoi(email, "203.0.113.184").param("cgv", "true"));

        assertThat(preuvesCgv(email)).hasSize(1);
    }
}
