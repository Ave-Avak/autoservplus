package be.autoservplus.reservation.web;

import be.autoservplus.identite.repository.UtilisateurRepository;
import be.autoservplus.support.MembresDeTest;
import be.autoservplus.support.SocleIntegration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Détour en plein parcours : le membre ajoute son véhicule depuis le formulaire de
 * rendez-vous, ou complète son adresse depuis le récapitulatif, puis revient où il était.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Retour au parcours après un détour (intégration)")
class RetourApresDetourIT extends SocleIntegration {

    @Autowired private MockMvc mvc;
    @Autowired private UtilisateurRepository utilisateurs;

    private String membre;

    @BeforeEach
    void setUp() {
        membre = "detour-" + UUID.randomUUID().toString().substring(0, 8) + "@exemple.be";
        utilisateurs.save(MembresDeTest.membre(membre, "$2a$12$h", "Dupont", "Marie"));
    }

    private static String plaque() {
        return "9-DT" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    @Test
    @DisplayName("premier rendez-vous sans véhicule : ajout, puis retour au formulaire avec la prestation choisie")
    void vehiculeDepuisLeRendezVous() throws Exception {
        String prestation = UUID.randomUUID().toString();
        Document formulaireRdv = Jsoup.parse(mvc.perform(get("/mes-rendez-vous/nouveau")
                        .param("prestation", prestation).with(user(membre).roles("MEMBRE")).locale(Locale.FRENCH))
                .andReturn().getResponse().getContentAsString());
        String lien = formulaireRdv.select("a[href^=/mes-vehicules/nouveau]").attr("href");
        assertThat(lien).contains("retour=");

        Document formulaireVehicule = Jsoup.parse(mvc.perform(get(URI.create(lien)).with(user(membre).roles("MEMBRE")))
                .andReturn().getResponse().getContentAsString());
        String retour = formulaireVehicule.select("input[name=retour]").val();
        assertThat(retour).isEqualTo("/mes-rendez-vous/nouveau?prestation=" + prestation);

        mvc.perform(post("/mes-vehicules/nouveau").with(user(membre).roles("MEMBRE")).with(csrf())
                        .param("plaque", plaque()).param("marque", "Volkswagen").param("modele", "Golf")
                        .param("motorisation", "DIESEL").param("retour", retour))
                .andExpect(redirectedUrl(retour));
    }

    @Test
    @DisplayName("retour hors de la liste fermée : ignoré, liste des véhicules comme avant")
    void retourExterneIgnore() throws Exception {
        mvc.perform(post("/mes-vehicules/nouveau").with(user(membre).roles("MEMBRE")).with(csrf())
                        .param("plaque", plaque()).param("marque", "Volkswagen").param("modele", "Golf")
                        .param("motorisation", "DIESEL").param("retour", "//exemple.org/panier"))
                .andExpect(redirectedUrl("/mes-vehicules"));
    }

    @Test
    @DisplayName("adresse complétée depuis le récapitulatif : retour à la commande")
    void adresseDepuisLaCommande() throws Exception {
        mvc.perform(post("/mon-profil").with(user(membre).roles("MEMBRE")).with(csrf())
                        .param("prenom", "Marie").param("nom", "Dupont").param("rue", "Rue de la Loi")
                        .param("numeroRue", "16").param("codePostal", "1000").param("localite", "Bruxelles")
                        .param("pays", "BE").param("langue", "fr").param("retour", "/commande"))
                .andExpect(redirectedUrl("/commande"));
    }
}
