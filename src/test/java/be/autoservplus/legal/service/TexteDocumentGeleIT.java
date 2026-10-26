package be.autoservplus.legal.service;

import be.autoservplus.identite.domain.Langue;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.domain.VersionDocument;
import be.autoservplus.legal.repository.VersionDocumentRepository;
import be.autoservplus.retractation.service.RetractationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import be.autoservplus.support.SocleIntegration;

/**
 * Garde de non-derive du texte gele (F24).
 *
 * <p><b>C est ce test qui fait le travail de conformite</b>, pas la table. Archiver un
 * texte ne sert a rien si le texte affiche peut s en ecarter en silence : les preuves
 * d acceptation continueraient de porter un numero de version qui ne correspondrait
 * plus a ce que les membres ont sous les yeux — c est-a-dire le defaut d avant F24,
 * deplace d un cran. Ici, modifier une clause sans publier de nouvelle version
 * <b>casse la build</b>, au moment ou l on peut encore choisir et non le jour ou
 * quelqu un demande a voir le texte qu il a accepte.</p>
 *
 * <p>Meme raisonnement que {@code fn_tables_traces_audit()} (V28) et
 * {@code SchemaIT.listeDesTracesExhaustive} : ce qui protege n est pas la declaration,
 * c est le test qui echoue quand elle cesse d etre vraie.</p>
 *
 * <p>Test d integration et non unitaire : il confronte deux artefacts qui n existent
 * qu ensemble — les messages reellement resolus par l application, et les lignes
 * reellement inserees par la migration. Une doublure de l un ou de l autre ne
 * comparerait que le test a lui-meme.</p>
 */
@SpringBootTest
@DisplayName("Texte gele des documents versionnes (F24)")
class TexteDocumentGeleIT extends SocleIntegration {

    /**
     * Seule cle a arguments des trois familles surveillees. Le delai vient de la
     * constante qui refuse effectivement une demande hors delai : changer la regle
     * change le texte des CGV et impose donc une nouvelle version — consequence voulue,
     * pas effet de bord.
     */
    private static final Map<String, Object[]> ARGUMENTS =
            Map.of("legal.cgv.art7.corps", new Object[]{RetractationService.DELAI_LEGAL.toDays()});

    /**
     * Cles de la famille {@code cookies.} qui ne font PAS partie du texte engageant.
     *
     * <p>Ce sont des commandes et des libelles de navigation. Les geler obligerait a
     * publier une nouvelle version — donc a redemander son consentement a chaque membre
     * — parce qu on aurait renomme un bouton. Une redemande sans cause est du bruit, et
     * le bruit finit par etre clique sans etre lu : le consentement y perdrait
     * precisement ce que la manoeuvre pretendait proteger.</p>
     */
    private static final Set<String> CLES_HORS_TEXTE = Set.of(
            "cookies.gerer",
            "cookies.bandeau.aria",
            "cookies.action.accepter",
            "cookies.action.refuser",
            "cookies.action.personnaliser",
            "cookies.action.enregistrer",
            "cookies.page.titre",
            "cookies.page.enregistre",
            "cookies.page.retour");

    @Autowired private MessageSource messages;
    @Autowired private VersionDocumentRepository versions;
    @Autowired private VersionsDocumentsService versionsDocuments;

    @Test
    @DisplayName("le texte archive est EXACTEMENT celui que l application presente, dans les trois langues")
    void aucuneDerive() {
        for (TypeDocumentVersionne type : TypeDocumentVersionne.values()) {
            String version = versionEnVigueur(type);
            for (Langue langue : Langue.values()) {
                String presente = texteReellementPresente(type, langue);

                VersionDocument gele = versions.findByTypeDocumentAndVersionOrderByLangue(type, version)
                        .stream()
                        .filter(ligne -> ligne.getLangue() == langue)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "Aucun texte gele pour " + type + " en " + langue
                                        + " : les trois langues se publient ensemble, une version "
                                        + "incomplete laisse un membre sans texte opposable."));

                assertThat(gele.getContenu())
                        .as("Le texte affiche pour %s en %s ne correspond plus au texte gele de la "
                                        + "version %s. Ce n est pas au test de suivre : publier une "
                                        + "NOUVELLE version dans une migration, ou annuler la "
                                        + "modification du message.",
                                type, langue, version)
                        .isEqualTo(presente);

                assertThat(gele.getEmpreinte())
                        .as("Empreinte incoherente avec le contenu archive de %s en %s", type, langue)
                        .isEqualTo(empreinte(presente));
            }
        }
    }

    @Test
    @DisplayName("toute cle des familles surveillees est classee : engageante ou explicitement hors texte")
    void aucuneCleNonClassee() {
        Properties fichier = messagesParDefaut();

        for (TypeDocumentVersionne type : TypeDocumentVersionne.values()) {
            Set<String> nonClassees = new TreeSet<>();
            for (String cle : fichier.stringPropertyNames()) {
                if (cle.startsWith(type.famille())
                        && !type.cles().contains(cle)
                        && !CLES_HORS_TEXTE.contains(cle)) {
                    nonClassees.add(cle);
                }
            }

            assertThat(nonClassees)
                    .as("Cles de la famille %s ni gelees ni declarees hors texte. Une clause "
                                    + "ajoutee au document sans etre inscrite dans "
                                    + "TypeDocumentVersionne ne serait jamais archivee : trancher "
                                    + "ici, dans un sens ou dans l autre.",
                            type.famille())
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("les versions en vigueur sont celles publiees par V43")
    void versionsEnVigueur() {
        // Valeurs recopiees en dur volontairement : c est un engagement de compatibilite,
        // pas une donnee de configuration. V43 publie les trois documents corriges pour la
        // typographie.
        assertThat(versionEnVigueur(TypeDocumentVersionne.CGV)).isEqualTo("CGV-2026-03");
        assertThat(versionEnVigueur(TypeDocumentVersionne.COOKIES)).isEqualTo("COOKIES-2026-02");
        assertThat(versionEnVigueur(TypeDocumentVersionne.RENONCIATION_RETRACTATION))
                .isEqualTo("VI53-2026-02");
    }

    /**
     * Empreintes publiees par V33 et V35 pour chaque version retiree, recopiees en dur. Une
     * preuve d acceptation designe l une de ces versions : son texte doit se relire a
     * l octet pres, quelle que soit la publication qui l a remplacee.
     */
    private static final Map<String, Map<Langue, String>> VERSIONS_RETIREES = Map.of(
            "CGV-2026-01", Map.of(
                    Langue.fr, "5fb51347674b397f16267f2e0588a6c7f54634aa182c7e62ecf49b52f91d92af",
                    Langue.nl, "80420a435c1da0c51056cae257bfcfa8219ae5a0060107f51f96be65df91700a",
                    Langue.en, "e457e56ba594020e648d77ad0e5a552afd598209452dd3df71665794cd81efe6"),
            "CGV-2026-02", Map.of(
                    Langue.fr, "5f850ad3f585526cbcf7a00dd96a4be439d0e588c34eb6ca837537f0d6e0163c",
                    Langue.nl, "70a5805d6834e303916ca12bb3f4281319b37e067946b124d3892dfb00413313",
                    Langue.en, "a91a1c38867fd7ac316d04da60cc3c5474ceec17a00e7d40d3a5088ccbbb064c"),
            "COOKIES-2026-01", Map.of(
                    Langue.fr, "bf9ba233cb33e346f06cc59f0eb2a169ad2c9ca0fce85fcb0ba82185e8348250",
                    Langue.nl, "8d30d7dae769d62d7be3449ea54410b0c8f3a3cdd79ce2e3681bf2c94800a9e4",
                    Langue.en, "7b17cc85e719fd0f6f3b3a945ed974b00367dfb8706bab0be903746007fb17bf"),
            "VI53-2026-01", Map.of(
                    Langue.fr, "85a677cadae8fa4198e9336fb308e088bff97310af6a28670a504a983839e09e",
                    Langue.nl, "2601dce86f0dc13f6116af44ce49dfb30732c4d43978d6a210c832566f20d4db",
                    Langue.en, "98bf89cbbc6e3814e644d10f7dce189df303e171c8d4fc2ffbbf6da694516c2b"));

    private static final Map<String, TypeDocumentVersionne> TYPE_DES_VERSIONS_RETIREES = Map.of(
            "CGV-2026-01", TypeDocumentVersionne.CGV,
            "CGV-2026-02", TypeDocumentVersionne.CGV,
            "COOKIES-2026-01", TypeDocumentVersionne.COOKIES,
            "VI53-2026-01", TypeDocumentVersionne.RENONCIATION_RETRACTATION);

    @Test
    @DisplayName("chaque version remplacee reste archivee, inactive, avec son texte d origine a l octet pres")
    void versionsRemplaceesIntactes() {
        // Sans cette garantie, F24 rendrait orphelines les preuves anterieures a une
        // publication : elles designeraient une version absente ou reecrite. Publier retire
        // du jeu resolvable, cela n efface rien.
        VERSIONS_RETIREES.forEach((version, empreintes) -> {
            List<VersionDocument> lignes = versions.findByTypeDocumentAndVersionOrderByLangue(
                    TYPE_DES_VERSIONS_RETIREES.get(version), version);

            assertThat(lignes).as("trois langues archivees pour %s", version)
                    .hasSize(Langue.values().length);
            assertThat(lignes).allSatisfy(ligne -> {
                assertThat(ligne.isActif()).as("%s retiree du jeu resolvable", version).isFalse();
                assertThat(ligne.getEmpreinte())
                        .as("empreinte publiee de %s en %s", version, ligne.getLangue())
                        .isEqualTo(empreintes.get(ligne.getLangue()));
                assertThat(empreinte(ligne.getContenu()))
                        .as("contenu de %s en %s identique a celui publie", version, ligne.getLangue())
                        .isEqualTo(empreintes.get(ligne.getLangue()));
            });
        });

        // Le texte accepte avant V35 annoncait sept ans. Ce controle porte sur le texte
        // archive de CGV-2026-01, pas sur la regle de conservation en vigueur (dix ans).
        assertThat(versions.findByTypeDocumentAndVersionOrderByLangue(TypeDocumentVersionne.CGV, "CGV-2026-01"))
                .filteredOn(ligne -> ligne.getLangue() == Langue.fr)
                .singleElement()
                .satisfies(ligne -> assertThat(ligne.getContenu()).contains("conservées sept ans"));
    }

    /**
     * Assemble le texte tel que l application le sert : les memes cles, dans le meme
     * ordre, resolues par le MEME {@code MessageSource} que les gabarits. Passer par le
     * MessageSource plutot que par le fichier brut n est pas un detail — sans lui les
     * apostrophes doublees des messages a arguments ne seraient pas reduites, et le test
     * comparerait un texte que personne ne voit jamais.
     */
    private String texteReellementPresente(TypeDocumentVersionne type, Langue langue) {
        Locale locale = Locale.forLanguageTag(langue.name());
        return type.cles().stream()
                .map(cle -> messages.getMessage(cle, ARGUMENTS.get(cle), locale))
                .reduce((debut, suite) -> debut + "\n" + suite)
                .orElseThrow();
    }

    /**
     * Version <b>en vigueur</b>, resolue par le service que l application emploie
     * elle-meme, et non « la seule version amorcee ».
     *
     * <p>La distinction est devenue reelle avec CGV-2026-02 (V35) : le type CGV porte
     * desormais deux versions, dont une retiree du jeu resolvable. Confronter le texte
     * affiche a une version choisie arbitrairement parmi les lignes de la table ferait
     * echouer ce test au premier changement de redaction — c est-a-dire precisement
     * quand F24 fonctionne. Ce qui doit etre vrai n a jamais ete « il n existe qu une
     * version » mais « le texte servi est celui de la version en vigueur ».</p>
     */
    private String versionEnVigueur(TypeDocumentVersionne type) {
        String version = versionsDocuments.versionCourante(type);
        assertThat(versions.findByTypeDocumentAndVersionOrderByLangue(type, version))
                .as("La version en vigueur de %s doit exister dans les trois langues : les "
                        + "trois se publient ensemble, une version incomplete laisse un membre "
                        + "sans texte opposable.", type)
                .hasSize(Langue.values().length);
        return version;
    }

    private static String empreinte(String texte) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(texte.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception echec) {
            throw new IllegalStateException("SHA-256 indisponible", echec);
        }
    }

    private static Properties messagesParDefaut() {
        Properties fichier = new Properties();
        try (Reader lecteur = new InputStreamReader(
                new ClassPathResource("i18n/messages.properties").getInputStream(),
                StandardCharsets.UTF_8)) {
            fichier.load(lecteur);
        } catch (Exception echec) {
            throw new IllegalStateException("i18n/messages.properties illisible", echec);
        }
        return fichier;
    }
}
