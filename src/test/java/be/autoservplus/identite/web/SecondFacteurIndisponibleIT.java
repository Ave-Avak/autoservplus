package be.autoservplus.identite.web;

import be.autoservplus.identite.domain.CodeSecours;
import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeEvenementSecurite;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.mfa.ChiffrementSecretMfa;
import be.autoservplus.identite.mfa.Totp;
import be.autoservplus.identite.repository.CodeSecoursRepository;
import be.autoservplus.identite.repository.EvenementSecuriteCompteRepository;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Secret TOTP indechiffrable, de bout en bout par le formulaire de connexion.
 *
 * <p>Classe a part : l espion sur le depot des codes de secours change le contexte
 * Spring, et le reste des cas de connexion n en a pas besoin. L espion est la seule
 * preuve que la recherche des codes n a pas eu lieu : un code non consomme le serait
 * aussi apres une comparaison sans succes.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Second facteur indisponible (intégration)")
class SecondFacteurIndisponibleIT extends SocleIntegration {

    private static final String MOT_DE_PASSE = "phrase de passe du test indisponible";
    private static final byte[] SECRET = "secret de vingt octs".getBytes();
    private static final String CODE_SECOURS = "ABCD-2345";
    /** Aucune cle maitre de cette version : le cas d une cle perdue. */
    private static final short VERSION_INCONNUE = 99;

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private EvenementSecuriteCompteRepository evenements;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private ChiffrementSecretMfa chiffrement;
    @Autowired private MessageSource messages;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock horloge;
    @MockitoSpyBean private CodeSecoursRepository codes;

    @Test
    @DisplayName("secret indéchiffrable : message dédié, ERROR au journal, ni code de secours lu ni échec compté")
    void indisponibleParLeFormulaire(CapturedOutput sortie) throws Exception {
        Utilisateur admin = new Utilisateur("indisponible@exemple.be", encodeur.encode(MOT_DE_PASSE),
                "Nom", "Prenom", TypeUtilisateur.ADMINISTRATEUR);
        admin.confirmerAdresseEmail();
        admin.setStatut(StatutUtilisateur.ACTIF);
        admin = utilisateurs.saveAndFlush(admin);
        ChiffrementSecretMfa.SecretChiffre chiffre = chiffrement.chiffrer(SECRET, admin.getId());
        admin.commencerEnrolement(chiffre.donnees(), chiffre.version());
        admin.confirmerEnrolement(horloge.instant(), Totp.pasDe(horloge.instant()) - 10);
        admin = utilisateurs.saveAndFlush(admin);
        codes.saveAndFlush(new CodeSecours(admin,
                new BCryptPasswordEncoder(10).encode(CODE_SECOURS.replace("-", ""))));
        jdbc.update("UPDATE utilisateur SET mfa_cle_version = ? WHERE id = ?", VERSION_INCONNUE, admin.getId());

        MvcResult connexion = mvc.perform(post("/connexion").with(csrf())
                .param("email", admin.getEmail()).param("password", MOT_DE_PASSE)).andReturn();
        assertThat(connexion.getResponse().getRedirectedUrl()).isEqualTo("/connexion/etape/second-facteur");
        MockHttpSession session = (MockHttpSession) connexion.getRequest().getSession(false);

        // Le code de secours VALIDE, par son champ : s il etait essaye, il serait accepte.
        MvcResult reponse = mvc.perform(post("/connexion/etape/second-facteur").session(session)
                .with(csrf()).locale(Locale.FRENCH)
                .param("mode", "secours").param("code", CODE_SECOURS)).andReturn();

        assertThat(reponse.getResponse().getStatus()).isEqualTo(200);
        String attendu = messages.getMessage("connexion.etape.second-facteur.indisponible", null, Locale.FRENCH);
        assertThat(reponse.getResponse().getContentAsString())
                .contains(HtmlUtils.htmlEscape(attendu, "UTF-8"));

        String reference = admin.getReference().toString();
        assertThat(sortie.getOut().lines()
                .filter(ligne -> ligne.contains("Secret TOTP indechiffrable"))
                .filter(ligne -> ligne.contains("ERROR"))
                .filter(ligne -> ligne.contains(reference)))
                .hasSize(1);

        verify(codes, never()).utilisablesDe(anyLong());
        Utilisateur relu = utilisateurs.findById(admin.getId()).orElseThrow();
        assertThat(relu.getMfaEchecs()).isZero();
        assertThat(relu.estVerrouille(horloge.instant())).isFalse();
        assertThat(evenements.evenementsDe(admin.getId())).extracting(e -> e.getType())
                .doesNotContain(TypeEvenementSecurite.SECOND_FACTEUR_ECHEC,
                        TypeEvenementSecurite.CODE_SECOURS_CONSOMME);
        // La connexion reste partielle : rien n a ete ouvert.
        assertThat(mvc.perform(get("/mon-compte").session(session))
                .andReturn().getResponse().getRedirectedUrl()).isEqualTo(EtapeConnexion.PREFIXE);
    }
}
