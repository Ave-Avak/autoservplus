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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Testcontainers
@DisplayName("Texte gele des documents versionnes (F24)")
class TexteDocumentGeleIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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

    @Test
    @DisplayName("le texte archive est EXACTEMENT celui que l application presente, dans les trois langues")
    void aucuneDerive() {
        for (TypeDocumentVersionne type : TypeDocumentVersionne.values()) {
            String version = versionAmorcee(type);
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
    @DisplayName("les identifiants de version amorces sont ceux des constantes remplacees")
    void continuiteDesIdentifiants() {
        // Sans cette egalite, F24 rendrait orphelines toutes les preuves de consentement
        // anterieures a sa propre livraison : elles designeraient une version absente de
        // la table. Les valeurs sont donc recopiees en dur ici, volontairement — c est un
        // engagement de compatibilite, pas une donnee de configuration.
        assertThat(versionAmorcee(TypeDocumentVersionne.CGV)).isEqualTo("CGV-2026-01");
        assertThat(versionAmorcee(TypeDocumentVersionne.COOKIES)).isEqualTo("COOKIES-2026-01");
        assertThat(versionAmorcee(TypeDocumentVersionne.RENONCIATION_RETRACTATION))
                .isEqualTo("VI53-2026-01");
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

    private String versionAmorcee(TypeDocumentVersionne type) {
        List<VersionDocument> toutes = versions.findAll().stream()
                .filter(ligne -> ligne.getTypeDocument() == type)
                .toList();
        assertThat(toutes)
                .as("La migration V33 doit amorcer %s dans les trois langues", type)
                .hasSize(Langue.values().length);
        return toutes.getFirst().getVersion();
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
