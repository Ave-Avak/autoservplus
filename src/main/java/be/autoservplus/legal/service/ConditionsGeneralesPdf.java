package be.autoservplus.legal.service;

import be.autoservplus.config.MentionsIdentiteGarage;
import be.autoservplus.legal.domain.TypeDocumentVersionne;
import be.autoservplus.legal.service.dto.TexteArchiveVue;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Conditions générales d'une version donnée, en PDF, jointes à la confirmation de
 * commande (art. VI.46, § 7, CDE) : le support durable du texte que le membre a accepté
 * pour cette commande, et non de la version du jour.
 *
 * <p>Le texte est celui gelé en base pour cette version, à l'octet près. L'identité du
 * garage s'insère sous l'article 1, qui la désigne « ci-dessous », comme sur la page
 * publique et sur la page d'archive. Elle vient de la configuration au moment de la
 * production du document, et non du jour de l'acceptation : limite V1 inscrite au
 * registre, sans effet tant qu'un seul garage garde la même identité.</p>
 */
@Service
public class ConditionsGeneralesPdf {

    private static final Color BLEU_MARINE = new Color(0x1F, 0x38, 0x64);
    private static final Pattern TITRE_ARTICLE = Pattern.compile("^(Article|Artikel) \\d+\\..*");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final VersionsDocumentsService versions;
    private final MentionsIdentiteGarage identite;
    private final MessageSource messages;
    private final Clock horloge;

    public ConditionsGeneralesPdf(VersionsDocumentsService versions, MentionsIdentiteGarage identite,
                                  MessageSource messages, Clock horloge) {
        this.versions = versions;
        this.identite = identite;
        this.messages = messages;
        this.horloge = horloge;
    }

    /** Vide si la version est inconnue : aucun texte approchant n'est produit à sa place. */
    public Optional<byte[]> pdf(String version, Locale langue, String numeroCommande, Instant accepteesLe) {
        return versions.archive(TypeDocumentVersionne.CGV, version, langue)
                .map(archive -> produire(archive, numeroCommande, accepteesLe));
    }

    private byte[] produire(TexteArchiveVue archive, String numeroCommande, Instant accepteesLe) {
        // Langue du texte réellement servi, qui peut différer de celle demandée : les
        // mentions du document suivent le texte, pour que le PDF tienne en une langue.
        Locale langue = Locale.of(archive.langue());
        String[] lignes = archive.contenu().split("\n", -1);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 56, 56, 56, 56);
        try {
            PdfWriter.getInstance(document, sortie);
            document.addTitle(lignes[0] + " " + archive.version());
            document.open();
            Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLEU_MARINE);
            Font article = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BLEU_MARINE);
            Font corps = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font mention = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9);
            document.add(paragraphe(lignes[0], titre, 4));
            document.add(paragraphe(messages.getMessage("legal.version.mention", new Object[]{
                    archive.version(), DATE.format(archive.dateEffet().atZone(horloge.getZone()))},
                    langue), mention, 2));
            document.add(paragraphe(messages.getMessage("legal.pdf.acceptation", new Object[]{
                    DATE.format(accepteesLe.atZone(horloge.getZone())), numeroCommande},
                    langue), mention, 12));
            for (int i = 1; i < lignes.length; i++) {
                boolean estTitre = TITRE_ARTICLE.matcher(lignes[i]).matches();
                document.add(paragraphe(lignes[i], estTitre ? article : corps, estTitre ? 4 : 8));
                if (i == TypeDocumentVersionne.LIGNES_AVANT_IDENTITE_CGV - 1) {
                    for (String ligne : identite.lignes(langue)) {
                        document.add(paragraphe(ligne, corps, 0));
                    }
                    document.add(paragraphe("", corps, 8));
                }
            }
            document.close();
        } catch (DocumentException echec) {
            throw new IllegalStateException("Conditions générales non produites en PDF.", echec);
        }
        return sortie.toByteArray();
    }

    private static Paragraph paragraphe(String texte, Font police, float espaceApres) {
        Paragraph paragraphe = new Paragraph(texte, police);
        paragraphe.setSpacingAfter(espaceApres);
        return paragraphe;
    }
}
