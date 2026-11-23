package be.autoservplus.facturation.service;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.Color;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;

/**
 * Primitives de composition partagées par les documents comptables PDF : couleurs de
 * la charte, cellules, formatage des montants et des dates.
 *
 * <p>Extraites de {@code GenerateurPdfFacture} quand la note de crédit est arrivée.
 * Une facture et un avoir sont deux documents différents (titre, mentions légales,
 * blocs de référence) mais ils doivent se ressembler au pixel près : même bandeau,
 * même tableau, même pied. Dupliquer ces trente lignes aurait garanti qu'elles
 * divergent à la première retouche de charte, et un client qui reçoit une facture et
 * son avoir les compare côte à côte.</p>
 *
 * <p>Seules les <b>primitives</b> sont mises en commun, pas la structure des
 * documents : factoriser plus haut aurait produit un générateur unique paramétré par
 * des drapeaux, moins lisible que deux compositions explicites.</p>
 *
 * <p><b>Écart de charte assumé</b> : les couleurs sont celles de la charte
 * (bleu marine {@code #1F3864}, gris), mais la typographie est Helvetica et non
 * Inter. Inter est une police web. L'embarquer dans un PDF suppose de livrer le
 * fichier de fonte dans le dépôt. Helvetica est une des quatorze polices garanties
 * par le format PDF, donc lisible partout sans embarquement.</p>
 */
final class StylePdf {

    static final Color BLEU_MARINE = new Color(0x1F, 0x38, 0x64);
    static final Color GRIS_TEXTE = new Color(0x37, 0x41, 0x51);
    static final Color GRIS_FOND = new Color(0xF9, 0xFA, 0xFB);
    static final Color GRIS_BORDURE = new Color(0xE5, 0xE7, 0xEB);

    /**
     * Fuseau des dates imprimées. En dur et non issu de l'horloge : un document
     * comptable belge daté en heure belge, quel que soit le fuseau du serveur qui
     * le compose.
     */
    static final ZoneId BRUXELLES = ZoneId.of("Europe/Brussels");

    private StylePdf() {
    }

    static Font police(float taille, int style, Color couleur) {
        return FontFactory.getFont(FontFactory.HELVETICA, taille, style, couleur);
    }

    static PdfPTable tableauInvisible(float[] largeurs) {
        PdfPTable table = new PdfPTable(largeurs);
        table.setWidthPercentage(100);
        return table;
    }

    static PdfPCell celluleInvisible(Element contenu, int alignement) {
        PdfPCell cellule = new PdfPCell();
        cellule.addElement(contenu);
        cellule.setBorder(Rectangle.NO_BORDER);
        cellule.setHorizontalAlignment(alignement);
        cellule.setPadding(0);
        return cellule;
    }

    static PdfPCell celluleEntete(String texte, int alignement) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, police(8, Font.BOLD, Color.WHITE)));
        cellule.setBackgroundColor(BLEU_MARINE);
        cellule.setHorizontalAlignment(alignement);
        cellule.setPadding(6);
        cellule.setBorderColor(BLEU_MARINE);
        return cellule;
    }

    static PdfPCell cellule(String texte, int alignement) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, police(9, Font.NORMAL, GRIS_TEXTE)));
        cellule.setHorizontalAlignment(alignement);
        cellule.setPadding(6);
        cellule.setBorderColor(GRIS_BORDURE);
        return cellule;
    }

    static PdfPCell celluleTotal(String texte, boolean accentue) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte,
                police(accentue ? 11 : 9, accentue ? Font.BOLD : Font.NORMAL,
                        accentue ? BLEU_MARINE : GRIS_TEXTE)));
        cellule.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cellule.setPadding(6);
        cellule.setBorderColor(GRIS_BORDURE);
        if (accentue) {
            cellule.setBackgroundColor(GRIS_FOND);
        }
        return cellule;
    }

    /**
     * Montant dans la locale du membre, devise forcée à l'euro : sans cette
     * contrainte, un document en anglais s'afficherait en dollars.
     */
    static String montant(BigDecimal valeur, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(Currency.getInstance("EUR"));
        return format.format(valeur);
    }

    static String pourcentage(BigDecimal taux) {
        return taux.stripTrailingZeros().toPlainString() + " %";
    }

    static String date(Instant instant, Locale locale) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale)
                .format(instant.atZone(BRUXELLES));
    }

    static String nonNul(String valeur) {
        return valeur == null ? "" : valeur;
    }
}
