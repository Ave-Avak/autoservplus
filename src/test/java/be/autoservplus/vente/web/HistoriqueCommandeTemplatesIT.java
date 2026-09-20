package be.autoservplus.vente.web;

import be.autoservplus.facturation.domain.Facture;
import be.autoservplus.facturation.service.FactureService;
import be.autoservplus.identite.domain.TypeUtilisateur;
import be.autoservplus.identite.domain.Utilisateur;
import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.vente.domain.Commande;
import be.autoservplus.vente.repository.CommandeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import be.autoservplus.support.SocleIntegration;

/**
 * Rendu reel de l ecran d historique contre le contexte Spring complet : c est ici
 * qu une cle i18n manquante ou une expression Thymeleaf fautive se voit, pas dans
 * le {@code @WebMvcTest} qui court-circuite le gabarit. Les requetes fixent la
 * locale — MockMvc est anglophone par defaut.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "marie@exemple.be")
@DisplayName("Ecran historique des commandes (integration)")
class HistoriqueCommandeTemplatesIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;
    @Autowired private CommandeRepository commandes;
    @Autowired private FactureService factures;
    /**
     * Meme horloge que les services. Les dates de ces jeux d essai en derivent au
     * lieu d etre ecrites en dur : une commande datee d un jour fixe finit par sortir
     * de la fenetre de retractation de quatorze jours, et le test devient rouge a une
     * date qui n a rien a voir avec le code qu il verifie.
     */
    @Autowired private Clock horloge;

    private Utilisateur marie;

    /** Conclusion d une commande assez recente pour rester retractable (F30). */
    private Instant hier() {
        return horloge.instant().minus(Duration.ofDays(1));
    }

    @BeforeEach
    void setUp() {
        marie = utilisateurs.save(new Utilisateur(
                "marie@exemple.be", "$2a$12$h", "Dupont", "Marie", TypeUtilisateur.MEMBRE));
    }

    private Commande commandePayee(String numero) {
        Instant conclusion = hier();
        Commande commande = new Commande(numero, marie, new BigDecimal("39.98"),
                new BigDecimal("8.40"), new BigDecimal("48.38"), conclusion);
        commande.confirmerPaiement(conclusion.plus(Duration.ofMinutes(5)));
        return commandes.saveAndFlush(commande);
    }

    @Test
    @DisplayName("une commande facturee affiche son lien de telechargement")
    void afficheLeLienDeTelechargement() throws Exception {
        Commande commande = commandePayee("CMD-IT-HIST-0001");
        Facture facture = factures.emettrePourCommande(commande.getReference());

        mvc.perform(get("/commandes").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mes commandes")))
                .andExpect(content().string(containsString("CMD-IT-HIST-0001")))
                .andExpect(content().string(containsString("Payée")))
                .andExpect(content().string(containsString(
                        "/factures/" + facture.getReference() + "/pdf")))
                .andExpect(content().string(containsString(
                        "Télécharger la facture " + facture.getNumero())));
    }

    @Test
    @DisplayName("une commande non facturee n'affiche aucun lien")
    void aucunLienSansFacture() throws Exception {
        Commande enAttente = commandes.saveAndFlush(new Commande("CMD-IT-HIST-0002", marie,
                new BigDecimal("10.00"), new BigDecimal("2.10"), new BigDecimal("12.10"),
                hier()));

        mvc.perform(get("/commandes").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(enAttente.getNumero())))
                .andExpect(content().string(containsString("En attente de paiement")))
                .andExpect(content().string(not(containsString("/factures/"))));
    }

    @Test
    @DisplayName("l'ecran est traduit : rien n'est en dur dans le gabarit")
    void ecranTraduit() throws Exception {
        commandePayee("CMD-IT-HIST-0003");

        mvc.perform(get("/commandes").locale(Locale.forLanguageTag("nl")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mijn bestellingen")))
                .andExpect(content().string(containsString("Bedrag incl. btw")))
                .andExpect(content().string(not(containsString("Mes commandes"))));
    }

    @Test
    @DisplayName("une commande payee et recente propose la demande d'annulation (F30)")
    void proposeLaDemandeDAnnulation() throws Exception {
        // La colonne d annulation vient du module retractation, assemblee par le
        // controleur : ce test verifie que la jointure du gabarit fonctionne, et que
        // le bouton n apparait que la ou l eligibilite est reelle.
        Commande commande = commandePayee("CMD-IT-HIST-0004");

        mvc.perform(get("/commandes").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Annulation")))
                .andExpect(content().string(containsString(
                        "/commandes/" + commande.getReference() + "/annulation")))
                .andExpect(content().string(containsString("Demander l")));
    }

    @Test
    @DisplayName("une commande non payee ne propose pas la demande d'annulation")
    void pasDAnnulationSansPaiement() throws Exception {
        // Rien a rembourser : proposer le bouton serait une promesse fausse.
        Commande enAttente = commandes.saveAndFlush(new Commande("CMD-IT-HIST-0005", marie,
                new BigDecimal("10.00"), new BigDecimal("2.10"), new BigDecimal("12.10"),
                hier()));

        mvc.perform(get("/commandes").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(
                        "/commandes/" + enAttente.getReference() + "/annulation"))));
    }

    @Test
    @DisplayName("une commande en attente propose de reprendre son paiement")
    void proposeLaRepriseDuPaiement() throws Exception {
        // Le bouton de la page de confirmation ne sert qu une fois : sans celui-ci,
        // un membre qui a ferme l onglet ne pouvait plus payer nulle part.
        Commande enAttente = commandes.saveAndFlush(new Commande("CMD-IT-HIST-0006", marie,
                new BigDecimal("10.00"), new BigDecimal("2.10"), new BigDecimal("12.10"),
                hier()));

        mvc.perform(get("/commandes").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/commande/" + enAttente.getReference() + "/payer")))
                .andExpect(content().string(containsString("Procéder au paiement")))
                // Plusieurs lignes peuvent attendre leur paiement : le bouton nomme
                // la commande qu il paie, sinon les boutons sont indistinguables.
                .andExpect(content().string(containsString(
                        "Procéder au paiement de la commande " + enAttente.getNumero())));
    }

    @Test
    @DisplayName("une commande payee ne propose plus de la payer")
    void pasDeRepriseUneFoisPayee() throws Exception {
        // Reproposer le paiement a qui vient de payer invite a payer deux fois.
        Commande payee = commandePayee("CMD-IT-HIST-0007");

        mvc.perform(get("/commandes").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(
                        "/commande/" + payee.getReference() + "/payer"))));
    }

    @Test
    @DisplayName("sans commande, l'ecran le dit au lieu d'afficher un tableau vide")
    void historiqueVide() throws Exception {
        mvc.perform(get("/commandes").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aucune commande")));
    }
}
