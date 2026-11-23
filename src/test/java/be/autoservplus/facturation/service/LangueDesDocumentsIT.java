package be.autoservplus.facturation.service;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Langue des documents d'un garage dont le siège est à Bruxelles : français ou
 * néerlandais, le français par défaut. C'est le siège des tests et de la démonstration.
 *
 * <p>La langue se décide à la conclusion de la commande, et les lignes s'y figent : une
 * facture a ainsi ses lignes dans sa propre langue. Le prix et le taux, eux, restent
 * ceux de l'ajout au panier.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Langue des documents, siège à Bruxelles (intégration)")
class LangueDesDocumentsIT extends ParcoursLangueDesDocuments {

    @Test
    @DisplayName("membre néerlandophone : commande, lignes et facture en néerlandais, repli français pour l'article non traduit")
    void membreNeerlandophone() throws Exception {
        Utilisateur membre = membre(Langue.nl);

        UUID commande = commander(membre);
        payer(membre, commande);

        assertThat(langueDeLaCommande(commande)).isEqualTo("nl");
        assertThat(libellesFiges(commande)).containsExactly("Oliefilter", "Joint de bouchon");
        assertThat(langueDeLaFacture(commande)).isEqualTo("nl");
        assertThat(texteDeLaFacture(commande)).contains("Oliefilter", "Joint de bouchon").doesNotContain("Filtre à huile");
        assertThat(ligne(commande, "Oliefilter"))
                .as("le prix et le taux restent ceux de l'ajout au panier")
                .containsEntry("prix_unitaire_htva", new java.math.BigDecimal("12.40"))
                .containsEntry("taux_tva", new java.math.BigDecimal("21.00"));
    }

    @Test
    @DisplayName("membre anglophone : l'anglais n'est pas admis, tout est émis en français, langue par défaut")
    void membreAnglophone() throws Exception {
        Utilisateur membre = membre(Langue.en);

        UUID commande = commander(membre);
        payer(membre, commande);

        assertThat(langueDeLaCommande(commande)).isEqualTo("fr");
        assertThat(libellesFiges(commande)).containsExactly("Filtre à huile", "Joint de bouchon");
        assertThat(langueDeLaFacture(commande)).isEqualTo("fr");
        assertThat(texteDeLaFacture(commande)).contains("Filtre à huile").doesNotContain("Oil filter");
    }

    @Test
    @DisplayName("membre francophone : français")
    void membreFrancophone() throws Exception {
        Utilisateur membre = membre(Langue.fr);

        UUID commande = commander(membre);

        assertThat(langueDeLaCommande(commande)).isEqualTo("fr");
        assertThat(libellesFiges(commande)).containsExactly("Filtre à huile", "Joint de bouchon");
    }

    @Test
    @DisplayName("la langue est figée : un membre qui change de langue après la commande garde sa facture")
    void figeeALaCommande() throws Exception {
        Utilisateur membre = membre(Langue.nl);
        UUID commande = commander(membre);

        jdbc.update("UPDATE utilisateur SET langue = 'fr' WHERE id = ?", membre.getId());
        payer(membre, commande);

        assertThat(langueDeLaFacture(commande)).isEqualTo("nl");
        assertThat(libellesFiges(commande)).containsExactly("Oliefilter", "Joint de bouchon");
    }

    @Test
    @DisplayName("avant la commande, le panier suit la langue de l'écran et non celle des documents")
    void panierDansLaLangueDeLEcran() throws Exception {
        Utilisateur membre = membre(Langue.nl);
        UUID commande = commander(membre);
        // Un second membre remplit son panier sans commander.
        Utilisateur autre = membre(Langue.fr);
        remplirLePanier(autre);

        String panierAnglais = mvc.perform(get("/panier").with(user(autre.getEmail())).locale(Locale.ENGLISH))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(panierAnglais).text()).contains("Oil filter").doesNotContain("Oliefilter");

        // Le détail de la commande conclue montre le libellé figé, quelle que soit la langue de l'écran.
        String detail = mvc.perform(get("/commandes/{ref}", commande).with(user(membre.getEmail()))
                        .locale(Locale.ENGLISH))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(detail).text()).contains("Oliefilter").doesNotContain("Oil filter");
    }
}
