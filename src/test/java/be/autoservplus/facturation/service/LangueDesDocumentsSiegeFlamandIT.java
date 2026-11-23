package be.autoservplus.facturation.service;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.identite.domain.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Langue des documents d'un garage dont le siège est en Flandre : le néerlandais seul,
 * quel que soit le client (décret flamand du 19 juillet 1973, art. 2).
 */
@SpringBootTest(properties = "autoservplus.garage.langues-documents=nl")
@AutoConfigureMockMvc
@DisplayName("Langue des documents, siège en Flandre (intégration)")
class LangueDesDocumentsSiegeFlamandIT extends ParcoursLangueDesDocuments {

    @Test
    @DisplayName("membre francophone : sa commande, ses lignes et sa facture sont en néerlandais")
    void membreFrancophone() throws Exception {
        Utilisateur membre = membre(Langue.fr);

        UUID commande = commander(membre);
        payer(membre, commande);

        assertThat(langueDeLaCommande(commande)).isEqualTo("nl");
        assertThat(libellesFiges(commande)).containsExactly("Oliefilter", "Joint de bouchon");
        assertThat(langueDeLaFacture(commande)).isEqualTo("nl");
        assertThat(texteDeLaFacture(commande)).contains("Oliefilter").doesNotContain("Filtre à huile");
    }

    @Test
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    @DisplayName("export comptable : en-têtes en néerlandais, même pour un administrateur à l'écran en français")
    void exportComptable() throws Exception {
        String neerlandais = mvc.perform(get("/admin/export/factures.csv?depuis=2026-01-01&jusqua=2026-01-31")
                        .locale(Locale.forLanguageTag("nl")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String francais = mvc.perform(get("/admin/export/factures.csv?depuis=2026-01-01&jusqua=2026-01-31")
                        .locale(Locale.FRENCH))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(francais).as("la langue de l'écran ne change pas le fichier").isEqualTo(neerlandais);
    }
}
