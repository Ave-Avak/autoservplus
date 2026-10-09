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

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Colonne « Auteur » du journal de securite sur la fiche : titulaire, systeme, ou autre
 * compte nomme. Un {@code auteur_id} nul servait aux deux premiers cas, que l ecran
 * confondait en « Titulaire ou systeme ».
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Journal de sécurité sur la fiche (intégration)")
class JournalSecuriteFicheIT extends SocleIntegration {

    private static final AtomicInteger COMPTEUR = new AtomicInteger(1);

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private EvenementSecuriteCompteRepository evenements;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private Clock horloge;

    private Utilisateur creer(TypeUtilisateur type, String prenom, String nom) {
        Utilisateur u = new Utilisateur("journal-" + COMPTEUR.getAndIncrement() + "@exemple.be",
                encodeur.encode("phrase de passe du journal de securite"), nom, prenom, type);
        u.confirmerAdresseEmail();
        u.setStatut(StatutUtilisateur.ACTIF);
        return utilisateurs.saveAndFlush(u);
    }

    @Test
    @DisplayName("titulaire, système et autre compte : trois libellés distincts")
    void troisCasDAffichage() throws Exception {
        Utilisateur admin = creer(TypeUtilisateur.ADMINISTRATEUR, "Ada", "Titulaire");
        Utilisateur chef = creer(TypeUtilisateur.SUPER_ADMINISTRATEUR, "Camille", "Exploitante");
        evenements.saveAndFlush(new EvenementSecuriteCompte(
                admin, TypeEvenementSecurite.MFA_ACTIVE, horloge.instant(), admin));
        evenements.saveAndFlush(new EvenementSecuriteCompte(
                admin, TypeEvenementSecurite.CONNEXION_VERROUILLAGE, horloge.instant(), null));
        evenements.saveAndFlush(new EvenementSecuriteCompte(
                admin, TypeEvenementSecurite.MFA_REINITIALISE, horloge.instant(), chef));

        mvc.perform(get("/admin/comptes/{ref}", admin.getReference())
                        .with(user(chef.getEmail()).roles("SUPER_ADMINISTRATEUR"))
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<td>Titulaire</td>")))
                .andExpect(content().string(containsString("<td>Système</td>")))
                .andExpect(content().string(containsString("<td>Camille Exploitante</td>")))
                .andExpect(content().string(not(containsString("Titulaire ou système"))));
    }

    @Test
    @DisplayName("les libellés suivent la locale")
    void libellesTraduits() throws Exception {
        Utilisateur admin = creer(TypeUtilisateur.ADMINISTRATEUR, "Ada", "Titulaire");
        Utilisateur chef = creer(TypeUtilisateur.SUPER_ADMINISTRATEUR, "Camille", "Exploitante");
        evenements.saveAndFlush(new EvenementSecuriteCompte(
                admin, TypeEvenementSecurite.MFA_ACTIVE, horloge.instant(), admin));
        evenements.saveAndFlush(new EvenementSecuriteCompte(
                admin, TypeEvenementSecurite.CONNEXION_VERROUILLAGE, horloge.instant(), null));

        mvc.perform(get("/admin/comptes/{ref}", admin.getReference())
                        .with(user(chef.getEmail()).roles("SUPER_ADMINISTRATEUR"))
                        .locale(Locale.of("nl")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<td>Accounthouder</td>")))
                .andExpect(content().string(containsString("<td>Systeem</td>")));
    }
}
