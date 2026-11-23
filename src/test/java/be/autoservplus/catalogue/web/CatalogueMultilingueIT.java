package be.autoservplus.catalogue.web;

import be.autoservplus.catalogue.domain.Categorie;
import be.autoservplus.catalogue.domain.Piece;
import be.autoservplus.catalogue.domain.Prestation;
import be.autoservplus.catalogue.domain.Traductions;
import be.autoservplus.catalogue.domain.TypeCategorie;
import be.autoservplus.catalogue.repository.CategorieRepository;
import be.autoservplus.catalogue.repository.PieceRepository;
import be.autoservplus.catalogue.repository.PrestationRepository;
import be.autoservplus.catalogue.service.AdminCatalogueService;
import be.autoservplus.catalogue.service.dto.DonneesPiece;
import be.autoservplus.support.SocleIntegration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le catalogue dans les trois langues du site.
 *
 * <p>Deux pièces d'une même catégorie : l'une traduite en néerlandais et en anglais,
 * l'autre restée en français. Sur une page néerlandaise, la première s'affiche en
 * néerlandais, la seconde en français avec {@code lang="fr"} (WCAG 2.1, 3.1.2), et la
 * liste suit l'ordre alphabétique de ce qui est affiché. La recherche trouve un article
 * par son texte dans n'importe laquelle des trois langues.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Catalogue multilingue (intégration)")
class CatalogueMultilingueIT extends SocleIntegration {

    private static final Locale NL = Locale.forLanguageTag("nl");

    @Autowired private MockMvc mvc;
    @Autowired private CategorieRepository categories;
    @Autowired private PieceRepository pieces;
    @Autowired private PrestationRepository prestations;
    @Autowired private AdminCatalogueService administration;
    @Autowired private JdbcTemplate jdbc;

    private Piece traduite;
    private Piece francaise;

    @BeforeEach
    void semer() {
        Categorie zqx = new Categorie("ZQX_ML", "Filtration zqx", TypeCategorie.PIECE);
        zqx.traduire("Filtratie zqx", null);
        zqx = categories.save(zqx);

        // En français « Zeta » passe après « Alpha ». En néerlandais la pièce traduite
        // s'appelle « Aanbod » et passe devant.
        traduite = new Piece(zqx, "ZQX-ML-1", "Zeta filtre zqx", new BigDecimal("10.00"));
        traduite.setDescription("Cartouche pour moteur diesel");
        traduite.setQuantiteStock(5);
        traduite.traduire(new Traductions("Aanbod oliefilter zqx", "Oil filter zqx",
                "Patroon voor dieselmotor", "Cartridge for diesel engines"));
        traduite = pieces.save(traduite);

        francaise = new Piece(zqx, "ZQX-ML-2", "Alpha joint zqx", new BigDecimal("4.00"));
        francaise.setDescription("Joint de bouchon");
        francaise.setQuantiteStock(5);
        francaise = pieces.saveAndFlush(francaise);
    }

    @Test
    @DisplayName("liste néerlandaise : traduction affichée, repli français marqué lang=fr, ordre de la langue")
    void listeNeerlandaise() throws Exception {
        Document page = ouvrir("/pieces?categorie=ZQX_ML", NL);

        Element lienTraduit = page.selectFirst("a[href$=" + traduite.getReference() + "]");
        Element lienFrancais = page.selectFirst("a[href$=" + francaise.getReference() + "]");
        assertThat(lienTraduit.text()).isEqualTo("Aanbod oliefilter zqx");
        assertThat(lienTraduit.hasAttr("lang")).as("texte dans la langue de la page").isFalse();
        assertThat(lienFrancais.text()).isEqualTo("Alpha joint zqx");
        assertThat(lienFrancais.attr("lang")).isEqualTo("fr");

        assertThat(page.select(".carte-description").eachText())
                .containsExactly("Patroon voor dieselmotor", "Joint de bouchon");
        assertThat(page.select(".carte-description").get(1).attr("lang")).isEqualTo("fr");
        assertThat(page.html().indexOf("Aanbod oliefilter zqx"))
                .as("« Aanbod » avant « Alpha » : tri sur le libellé affiché")
                .isLessThan(page.html().indexOf("Alpha joint zqx"));

        Element filtre = page.selectFirst("a.filtre[href*=ZQX_ML]");
        assertThat(filtre.text()).isEqualTo("Filtratie zqx");
        assertThat(filtre.hasAttr("lang")).isFalse();
    }

    @Test
    @DisplayName("liste française : libellés d'origine, aucun attribut de langue, ordre français")
    void listeFrancaise() throws Exception {
        Document page = ouvrir("/pieces?categorie=ZQX_ML", Locale.FRENCH);

        assertThat(page.select(".carte a[href*=/pieces/]").eachText())
                .containsExactly("Alpha joint zqx", "Zeta filtre zqx");
        assertThat(page.select(".carte [lang]")).isEmpty();
        assertThat(page.selectFirst("a.filtre[href*=ZQX_ML]").text()).isEqualTo("Filtration zqx");
    }

    @Test
    @DisplayName("fiche anglaise : titre et description traduits, catégorie non traduite marquée lang=fr")
    void ficheAnglaise() throws Exception {
        Document fiche = ouvrir("/pieces/" + traduite.getReference(), Locale.ENGLISH);

        assertThat(fiche.title()).isEqualTo("Oil filter zqx | AutoServ+");
        assertThat(fiche.selectFirst("h1").text()).isEqualTo("Oil filter zqx");
        assertThat(fiche.selectFirst("h1").hasAttr("lang")).isFalse();
        assertThat(fiche.selectFirst(".fiche-description").text()).isEqualTo("Cartridge for diesel engines");
        Element categorie = fiche.selectFirst(".fiche-categorie");
        assertThat(categorie.text()).isEqualTo("Filtration zqx");
        assertThat(categorie.attr("lang")).isEqualTo("fr");
        assertThat(fiche.selectFirst("script[type=application/ld+json]").data()).contains("Oil filter zqx");

        Document nonTraduite = ouvrir("/pieces/" + francaise.getReference(), Locale.ENGLISH);
        assertThat(nonTraduite.selectFirst("h1").text()).isEqualTo("Alpha joint zqx");
        assertThat(nonTraduite.selectFirst("h1").attr("lang")).isEqualTo("fr");
    }

    @Test
    @DisplayName("recherche : un terme d'une langue trouve l'article, affiché dans la langue de l'écran")
    void rechercheDansLesTroisLangues() throws Exception {
        // Terme néerlandais sur une page française : l'article est trouvé, en français.
        Document enFrancais = ouvrir("/recherche?q=oliefilter zqx", Locale.FRENCH);
        assertThat(enFrancais.select("#pieces .carte a[href*=/pieces/]").eachText()).containsExactly("Zeta filtre zqx");

        // Terme français sur une page néerlandaise : trouvé, en néerlandais.
        Document enNeerlandais = ouvrir("/recherche?q=zeta filtre", NL);
        assertThat(enNeerlandais.select("#pieces .carte a[href*=/pieces/]").eachText())
                .containsExactly("Aanbod oliefilter zqx");

        // Description anglaise et libellé traduit de la catégorie sont cherchés aussi.
        assertThat(ouvrir("/recherche?q=cartridge for diesel", Locale.FRENCH)
                .select("#pieces .carte a[href*=/pieces/]").eachText()).containsExactly("Zeta filtre zqx");
        assertThat(ouvrir("/recherche?q=filtratie zqx", NL)
                .select("#pieces .carte a[href*=/pieces/]").eachText())
                .containsExactly("Aanbod oliefilter zqx", "Alpha joint zqx");
    }

    @Test
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    @DisplayName("back-office : traductions saisies, champ vide rangé nul, marque « Non traduit » dans la liste")
    void saisieAuBackOffice() throws Exception {
        mvc.perform(post("/admin/catalogue/pieces/{ref}/modifier", francaise.getReference())
                        .with(csrf()).locale(Locale.FRENCH)
                        .param("codeCategorie", "ZQX_ML")
                        .param("referenceFabricant", "ZQX-ML-2")
                        .param("libelle", "Alpha joint zqx")
                        .param("description", "Joint de bouchon")
                        .param("libelleNl", "  Afdichting zqx ")
                        .param("descriptionNl", "Plugafdichting")
                        .param("libelleEn", "   ")
                        .param("descriptionEn", "")
                        .param("prixHtva", "4.00")
                        .param("tauxTva", "21.00")
                        .param("quantiteStock", "5")
                        .param("seuilAlerte", "1")
                        .param("actif", "true"))
                .andExpect(status().is3xxRedirection());

        // La transaction du test n'a encore rien écrit : la lecture SQL exige un flush.
        pieces.flush();
        assertThat(jdbc.queryForMap(
                "SELECT libelle_nl, description_nl, libelle_en, description_en FROM piece WHERE reference = ?",
                francaise.getReference()))
                .containsEntry("libelle_nl", "Afdichting zqx")
                .containsEntry("description_nl", "Plugafdichting")
                .containsEntry("libelle_en", null)
                .containsEntry("description_en", null);

        Document formulaire = ouvrir("/admin/catalogue/pieces/" + francaise.getReference() + "/modifier",
                Locale.FRENCH);
        assertThat(formulaire.selectFirst("#libelleNl").val()).isEqualTo("Afdichting zqx");
        assertThat(formulaire.selectFirst("#libelleNl").attr("lang")).isEqualTo("nl");
        assertThat(formulaire.selectFirst("#libelleEn").val()).isEmpty();

        Document liste = ouvrir("/admin/catalogue/pieces", Locale.FRENCH);
        assertThat(liste.select("tr:contains(Alpha joint zqx) .badge").eachText()).contains("Non traduit");
        assertThat(liste.select("tr:contains(Zeta filtre zqx) .badge").eachText()).doesNotContain("Non traduit");
    }

    @Test
    @WithMockUser(username = "admin@garage.be", roles = "ADMINISTRATEUR")
    @DisplayName("une modification qui ne connaît pas les traductions les laisse intactes")
    void modificationSansTraductions() {
        administration.modifierPiece(traduite.getReference(), new DonneesPiece("ZQX_ML", "ZQX-ML-1",
                "Zeta filtre zqx", null, "Cartouche pour moteur diesel", new BigDecimal("11.00"),
                new BigDecimal("21.00"), 5, 1, true));

        Piece relue = pieces.findByReference(traduite.getReference()).orElseThrow();
        assertThat(relue.getPrixHtva()).isEqualByComparingTo("11.00");
        assertThat(relue.getTraductions().getLibelleNl()).isEqualTo("Aanbod oliefilter zqx");
        assertThat(relue.getTraductions().getDescriptionEn()).isEqualTo("Cartridge for diesel engines");
    }

    @Test
    @DisplayName("une prestation sans traduction s'affiche en français avec lang=fr")
    void prestationNonTraduite() throws Exception {
        // Prestation propre au test : celles de la graine sont traduites depuis V67.
        Categorie atelier = categories.save(new Categorie("ZQX_ML_S", "Atelier zqx", TypeCategorie.SERVICE));
        Prestation prestation = prestations.saveAndFlush(new Prestation(atelier, "ZQX-ML-S1",
                "Réglage des phares zqx", new BigDecimal("25.00"), 30));

        Document fiche = ouvrir("/services/" + prestation.getReference(), NL);

        assertThat(fiche.selectFirst("h1").text()).isEqualTo("Réglage des phares zqx");
        assertThat(fiche.selectFirst("h1").attr("lang")).isEqualTo("fr");
        assertThat(fiche.selectFirst("html").attr("lang")).isEqualTo("nl");
    }

    private Document ouvrir(String adresse, Locale langue) throws Exception {
        return Jsoup.parse(mvc.perform(get(adresse).locale(langue))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
