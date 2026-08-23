package be.autoservplus.retractation.web;

import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.retractation.domain.DemandeAnnulation;
import be.autoservplus.retractation.repository.DemandeAnnulationRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendu reel des ecrans de retractation (F30) contre le contexte Spring complet.
 *
 * <p>C est ici qu une cle i18n manquante se voit — pas dans un {@code @WebMvcTest},
 * qui court-circuite le gabarit. Le projet a deja perdu un bloc entier de cles a un
 * merge sans que rien ne le signale avant l execution : ces trois ecrans sont neufs
 * et entierement traduits, ils doivent etre rendus au moins une fois dans chaque
 * langue par la build.</p>
 *
 * <p>Les requetes fixent la locale : MockMvc est anglophone par defaut, et un test
 * qui ne la fixerait pas ne prouverait rien sur les fichiers FR et NL.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Ecrans de retractation (integration)")
class RetractationTemplatesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CommandeRepository commandes;
    @Autowired private DemandeAnnulationRepository demandes;

    private Utilisateur marie;

    @BeforeEach
    void setUp() {
        marie = utilisateurs.save(new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
    }

    private Commande commandePayee(String numero) {
        Commande commande = new Commande(numero, marie, new BigDecimal("39.98"),
                new BigDecimal("8.40"), new BigDecimal("48.38"),
                Instant.parse("2026-08-22T09:00:00Z"));
        commande.confirmerPaiement(Instant.parse("2026-08-22T09:05:00Z"));
        return commandes.saveAndFlush(commande);
    }

    // --- ecran membre -------------------------------------------------------------------

    @Test
    @DisplayName("le formulaire de demande se rend en francais, avec ses mentions legales")
    void formulaireEnFrancais() throws Exception {
        Commande commande = commandePayee("CMD-IT-RETT-0001");

        mvc.perform(get("/commandes/{ref}/annulation", commande.getReference())
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CMD-IT-RETT-0001")))
                .andExpect(content().string(containsString("48,38")))
                .andExpect(content().string(containsString("rétractation")))
                // Le membre doit savoir qu il n a pas a se justifier.
                .andExpect(content().string(containsString("facultatif")))
                // Aucune cle non resolue ne doit passer.
                .andExpect(content().string(not(containsString("??retractation"))));
    }

    @Test
    @DisplayName("le formulaire se rend aussi en neerlandais et en anglais")
    void formulaireDansLesTroisLangues() throws Exception {
        Commande commande = commandePayee("CMD-IT-RETT-0002");

        mvc.perform(get("/commandes/{ref}/annulation", commande.getReference())
                        .locale(new Locale("nl")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("herroepingsrecht")))
                .andExpect(content().string(not(containsString("??retractation"))));

        mvc.perform(get("/commandes/{ref}/annulation", commande.getReference())
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("right of withdrawal")))
                .andExpect(content().string(not(containsString("??retractation"))));
    }

    @Test
    @DisplayName("sans la case de confirmation, le formulaire revient avec son erreur traduite")
    void confirmationObligatoire() throws Exception {
        Commande commande = commandePayee("CMD-IT-RETT-0003");

        mvc.perform(post("/commandes/{ref}/annulation", commande.getReference())
                        .with(csrf()).locale(Locale.FRENCH).param("motif", "trop cher"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cochez la case")));

        // Un POST forge sans la case n ecrit rien : la garde est cote serveur.
        org.assertj.core.api.Assertions.assertThat(demandes.historiqueDe(commande)).isEmpty();
    }

    @Test
    @DisplayName("la commande d'un autre membre rend la page « introuvable », pas une erreur brute")
    void commandeIntrouvable() throws Exception {
        // 404 fonctionnel plutot que 500 : c est l asymetrie relevee sur RdvController,
        // qu on ne reproduit pas dans du code neuf.
        mvc.perform(get("/commandes/{ref}/annulation", UUID.randomUUID()).locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("introuvable")))
                .andExpect(content().string(not(containsString("??retractation"))));
    }

    @Test
    @DisplayName("le parcours nominal redirige vers la liste avec un message de confirmation")
    void demandeAcceptee() throws Exception {
        Commande commande = commandePayee("CMD-IT-RETT-0004");

        mvc.perform(post("/commandes/{ref}/annulation", commande.getReference())
                        .with(csrf()).locale(Locale.FRENCH)
                        .param("motif", "piece non compatible")
                        .param("confirmation", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commandes"));

        org.assertj.core.api.Assertions.assertThat(demandes.historiqueDe(commande)).hasSize(1);
    }

    // --- ecran admin --------------------------------------------------------------------

    @Test
    @DisplayName("la file du garage affiche la demande, son anciennete et ses deux actions")
    void fileAdmin() throws Exception {
        Commande commande = commandePayee("CMD-IT-RETT-0005");
        demandes.saveAndFlush(new DemandeAnnulation(commande, "piece non compatible",
                Instant.parse("2026-08-23T09:00:00Z")));

        mvc.perform(get("/admin/retractations").locale(Locale.FRENCH)
                        .with(user("admin@autoservplus.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CMD-IT-RETT-0005")))
                .andExpect(content().string(containsString("Marie")))
                // Le montant est sur le bouton : l administrateur voit ce qu il engage.
                .andExpect(content().string(containsString("48,38")))
                .andExpect(content().string(containsString("Refuser")))
                .andExpect(content().string(not(containsString("??admin.retractations"))));
    }

    @Test
    @DisplayName("l'ecran de refus exige un motif, et le dit dans la langue de la session")
    void refusMotifObligatoire() throws Exception {
        Commande commande = commandePayee("CMD-IT-RETT-0006");
        DemandeAnnulation demande = demandes.saveAndFlush(new DemandeAnnulation(
                commande, null, Instant.parse("2026-08-23T09:00:00Z")));

        mvc.perform(get("/admin/retractations/{ref}/refuser", demande.getReference())
                        .locale(Locale.FRENCH)
                        .with(user("admin@autoservplus.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Motif du refus")))
                .andExpect(content().string(not(containsString("??admin.retractations"))));

        mvc.perform(post("/admin/retractations/{ref}/refuser", demande.getReference())
                        .with(csrf()).locale(Locale.FRENCH).param("motif", "  ")
                        .with(user("admin@autoservplus.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("obligatoire")));
    }

    @Test
    @DisplayName("la file du garage se rend aussi en neerlandais")
    void fileAdminEnNeerlandais() throws Exception {
        Commande commande = commandePayee("CMD-IT-RETT-0007");
        demandes.saveAndFlush(new DemandeAnnulation(commande, null,
                Instant.parse("2026-08-23T09:00:00Z")));

        mvc.perform(get("/admin/retractations").locale(new Locale("nl"))
                        .with(user("admin@autoservplus.be").roles("ADMINISTRATEUR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Annuleringsaanvragen")))
                .andExpect(content().string(not(containsString("??admin.retractations"))));
    }
}
