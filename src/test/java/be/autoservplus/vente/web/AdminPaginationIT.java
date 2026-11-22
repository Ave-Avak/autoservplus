package be.autoservplus.vente.web;

import be.autoservplus.identite.domain.StatutUtilisateur;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.SocleIntegration;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pagination des listes du back-office, sur une taille de page ramenée à deux. Contexte
 * propre, la propriété changeant la configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "autoservplus.back-office.taille-page=2")
@DisplayName("Pagination des commandes au back-office (intégration)")
class AdminPaginationIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CommandeRepository commandes;
    @Autowired private PasswordEncoder encodeur;
    @Autowired private TransactionTemplate transactions;

    @Test
    @DisplayName("trois commandes, deux par page : lien vers la suivante, filtres conservés, puis retour")
    void pageSuivanteEtLiens() throws Exception {
        String email = "pagination-" + UUID.randomUUID() + "@exemple.be";
        transactions.executeWithoutResult(statut -> {
            Utilisateur membre = new Utilisateur(email, encodeur.encode("phrase de passe du test"),
                    "Dupont", "Marie", TypeUtilisateur.MEMBRE);
            membre.confirmerAdresseEmail();
            membre.setStatut(StatutUtilisateur.ACTIF);
            Utilisateur enregistre = utilisateurs.saveAndFlush(membre);
            for (int i = 1; i <= 3; i++) {
                commandes.save(new Commande("PAG-" + UUID.randomUUID().toString().substring(0, 12),
                        enregistre, new BigDecimal("10.00"), new BigDecimal("2.10"),
                        new BigDecimal("12.10"), Instant.now().minus(i, ChronoUnit.HOURS)));
            }
        });

        mvc.perform(get("/admin/commandes").param("recherche", email)
                        .with(user("pagination-admin@exemple.be").roles("ADMINISTRATEUR"))
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Page 1 sur 2")))
                .andExpect(content().string(containsString("page=2")))
                .andExpect(content().string(containsString("recherche=pagination-")))
                .andExpect(content().string(not(containsString("rel=\"prev\""))));

        mvc.perform(get("/admin/commandes").param("recherche", email).param("page", "2")
                        .with(user("pagination-admin@exemple.be").roles("ADMINISTRATEUR"))
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Page 2 sur 2")))
                .andExpect(content().string(containsString("rel=\"prev\"")))
                .andExpect(content().string(not(containsString("rel=\"next\""))));
    }

    @Test
    @DisplayName("numéro de page démesuré : les trois listes répondent, sans erreur")
    void pageDemesuree() throws Exception {
        for (String liste : new String[]{"/admin/commandes", "/admin/factures", "/admin/avoirs"}) {
            for (String page : new String[]{"2147483647", "-2147483648", "0"}) {
                mvc.perform(get(liste).param("page", page)
                                .with(user("pagination-admin@exemple.be").roles("ADMINISTRATEUR"))
                                .locale(Locale.FRENCH))
                        .andExpect(status().isOk());
            }
        }
    }
}
