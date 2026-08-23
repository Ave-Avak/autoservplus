package be.autoservplus.facturation.service;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.facturation.service.dto.DocumentFacture;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static be.autoservplus.facturation.service.StylePdf.BLEU_MARINE;
import static be.autoservplus.facturation.service.StylePdf.GRIS_TEXTE;
import static be.autoservplus.facturation.service.StylePdf.cellule;
import static be.autoservplus.facturation.service.StylePdf.celluleEntete;
import static be.autoservplus.facturation.service.StylePdf.celluleInvisible;
import static be.autoservplus.facturation.service.StylePdf.celluleTotal;
import static be.autoservplus.facturation.service.StylePdf.date;
import static be.autoservplus.facturation.service.StylePdf.montant;
import static be.autoservplus.facturation.service.StylePdf.nonNul;
import static be.autoservplus.facturation.service.StylePdf.police;
import static be.autoservplus.facturation.service.StylePdf.pourcentage;
import static be.autoservplus.facturation.service.StylePdf.tableauInvisible;

/**
 * Composition du PDF d une facture (F31), avec OpenPDF.
 *
 * <p>Toutes les mentions obligatoires d une facture belge y figurent : numero et
 * date d emission, identification complete de l emetteur (denomination, siege, BCE,
 * TVA), identification du client, detail par ligne avec taux applique, et
 * <b>ventilation de la base imposable par taux</b> — c est cette derniere qui rend
 * une facture multi-taux verifiable par l administration.</p>
 *
 * <p>Aucune chaine en dur : chaque libelle passe par le {@code MessageSource}, dans
 * la langue du membre. Les montants et les dates suivent la meme locale, la devise
 * etant forcee a l euro (un {@code Locale.ENGLISH} non contraint imprimerait des
 * dollars).</p>
 *
 * <p>Les primitives de composition (couleurs, cellules, formatage) vivent dans
 * {@link StylePdf}, ou elles attendent le generateur de note de credit : une facture
 * et son avoir doivent se ressembler au pixel pres, le client les compare cote a
 * cote.</p>
 */
@Component
public class GenerateurPdfFacture {

    private final MessageSource messages;
    private final IdentiteGarage garage;

    public GenerateurPdfFacture(MessageSource messages, IdentiteGarage garage) {
        this.messages = messages;
        this.garage = garage;
    }

    public byte[] engendrer(DocumentFacture document) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4, 42, 42, 42, 54);
        try {
            PdfWriter.getInstance(pdf, sortie);
            pdf.addTitle(libelle(document, "facture.titre") + " " + document.numero());
            pdf.addCreator(garage.raisonSociale());
            pdf.open();
            pdf.add(enTete(document));
            pdf.add(blocsIdentite(document));
            pdf.add(tableauDesLignes(document));
            pdf.add(totaux(document));
            pdf.add(mentionsLegales(document));
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "Composition du PDF de la facture %s impossible.".formatted(document.numero()), e);
        } finally {
            if (pdf.isOpen()) {
                pdf.close();
            }
        }
        return sortie.toByteArray();
    }

    // --- blocs du document -------------------------------------------------------------

    /** Bandeau : emetteur a gauche, identite du document a droite. */
    private PdfPTable enTete(DocumentFacture document) {
        PdfPTable entete = tableauInvisible(new float[]{55, 45});
        entete.setSpacingAfter(24);

        Paragraph emetteur = new Paragraph();
        emetteur.add(new Phrase(garage.raisonSociale() + "\n", police(15, Font.BOLD, BLEU_MARINE)));
        emetteur.add(new Phrase(garage.adresseLisible() + "\n" + garage.pays() + "\n",
                police(9, Font.NORMAL, GRIS_TEXTE)));
        emetteur.add(new Phrase("%s %s\n%s %s".formatted(
                        libelle(document, "facture.emetteur.bce"), garage.numeroBce(),
                        libelle(document, "facture.emetteur.tva"), garage.numeroTva()),
                police(9, Font.NORMAL, GRIS_TEXTE)));
        entete.addCell(celluleInvisible(emetteur, Element.ALIGN_LEFT));

        Paragraph identite = new Paragraph();
        identite.add(new Phrase(libelle(document, "facture.titre").toUpperCase(document.locale()) + "\n",
                police(22, Font.BOLD, BLEU_MARINE)));
        identite.add(new Phrase(document.numero() + "\n", police(13, Font.BOLD, GRIS_TEXTE)));
        identite.add(new Phrase("%s %s\n".formatted(
                        libelle(document, "facture.date-emission"),
                        date(document.dateEmission(), document.locale())),
                police(9, Font.NORMAL, GRIS_TEXTE)));
        identite.add(new Phrase("%s %s".formatted(
                        libelle(document, "facture.commande"), document.numeroCommande()),
                police(9, Font.NORMAL, GRIS_TEXTE)));
        entete.addCell(celluleInvisible(identite, Element.ALIGN_RIGHT));
        return entete;
    }

    /** Destinataire de la facture. */
    private PdfPTable blocsIdentite(DocumentFacture document) {
        PdfPTable bloc = tableauInvisible(new float[]{55, 45});
        bloc.setSpacingAfter(20);

        Paragraph client = new Paragraph();
        client.add(new Phrase(libelle(document, "facture.client") + "\n",
                police(9, Font.BOLD, BLEU_MARINE)));
        client.add(new Phrase(document.client().nomComplet() + "\n",
                police(10, Font.NORMAL, GRIS_TEXTE)));
        String adresse = document.client().adresseLisible();
        if (adresse != null) {
            client.add(new Phrase(adresse + "\n" + nonNul(document.client().pays()) + "\n",
                    police(9, Font.NORMAL, GRIS_TEXTE)));
        }
        client.add(new Phrase(nonNul(document.client().courriel()),
                police(9, Font.NORMAL, GRIS_TEXTE)));
        bloc.addCell(celluleInvisible(client, Element.ALIGN_LEFT));
        bloc.addCell(celluleInvisible(new Paragraph(), Element.ALIGN_RIGHT));
        return bloc;
    }

    /** Detail par ligne : description, quantite, prix unitaire HTVA, taux, montant. */
    private PdfPTable tableauDesLignes(DocumentFacture document) {
        PdfPTable table = new PdfPTable(new float[]{46, 10, 16, 12, 16});
        table.setWidthPercentage(100);
        table.setSpacingAfter(16);
        for (String cle : List.of("facture.colonne.description", "facture.colonne.quantite",
                "facture.colonne.prix-unitaire", "facture.colonne.taux",
                "facture.colonne.total-htva")) {
            table.addCell(celluleEntete(libelle(document, cle),
                    cle.endsWith("description") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT));
        }
        for (DocumentFacture.LigneFacture ligne : document.lignes()) {
            table.addCell(cellule(ligne.libelle(), Element.ALIGN_LEFT));
            table.addCell(cellule(String.valueOf(ligne.quantite()), Element.ALIGN_RIGHT));
            table.addCell(cellule(montant(ligne.prixUnitaireHtva(), document.locale()),
                    Element.ALIGN_RIGHT));
            table.addCell(cellule(pourcentage(ligne.tauxTva()), Element.ALIGN_RIGHT));
            table.addCell(cellule(montant(ligne.totalHtva(), document.locale()),
                    Element.ALIGN_RIGHT));
        }
        return table;
    }

    /** Totaux et ventilation de la TVA par taux (mention obligatoire). */
    private PdfPTable totaux(DocumentFacture document) {
        PdfPTable conteneur = tableauInvisible(new float[]{50, 50});
        conteneur.setSpacingAfter(24);
        conteneur.addCell(celluleInvisible(ventilation(document), Element.ALIGN_LEFT));

        PdfPTable synthese = new PdfPTable(new float[]{60, 40});
        synthese.setWidthPercentage(100);
        synthese.addCell(celluleTotal(libelle(document, "facture.total.htva"), false));
        synthese.addCell(celluleTotal(montant(document.totalHtva(), document.locale()), false));
        synthese.addCell(celluleTotal(libelle(document, "facture.total.tva"), false));
        synthese.addCell(celluleTotal(montant(document.totalTva(), document.locale()), false));
        synthese.addCell(celluleTotal(libelle(document, "facture.total.tvac"), true));
        synthese.addCell(celluleTotal(montant(document.totalTvac(), document.locale()), true));
        conteneur.addCell(celluleInvisible(synthese, Element.ALIGN_RIGHT));
        return conteneur;
    }

    private PdfPTable ventilation(DocumentFacture document) {
        PdfPTable table = new PdfPTable(new float[]{34, 33, 33});
        table.setWidthPercentage(100);
        table.addCell(celluleEntete(libelle(document, "facture.ventilation.taux"), Element.ALIGN_LEFT));
        table.addCell(celluleEntete(libelle(document, "facture.ventilation.base"), Element.ALIGN_RIGHT));
        table.addCell(celluleEntete(libelle(document, "facture.ventilation.tva"), Element.ALIGN_RIGHT));
        for (VentilationTva.TrancheTva tranche : document.ventilation().tranches()) {
            table.addCell(cellule(pourcentage(tranche.taux()), Element.ALIGN_LEFT));
            table.addCell(cellule(montant(tranche.baseHtva(), document.locale()), Element.ALIGN_RIGHT));
            table.addCell(cellule(montant(tranche.montantTva(), document.locale()), Element.ALIGN_RIGHT));
        }
        return table;
    }

    /** Conditions de paiement et mentions legales de pied de facture. */
    private Paragraph mentionsLegales(DocumentFacture document) {
        Paragraph mentions = new Paragraph();
        mentions.add(new Phrase(libelle(document, "facture.mention.payee",
                        date(document.datePaiement(), document.locale())) + "\n",
                police(9, Font.BOLD, GRIS_TEXTE)));
        mentions.add(new Phrase(libelle(document, "facture.mention.conditions") + "\n",
                police(8, Font.NORMAL, GRIS_TEXTE)));
        mentions.add(new Phrase(libelle(document, "facture.mention.tva") + "\n",
                police(8, Font.NORMAL, GRIS_TEXTE)));
        mentions.add(new Phrase(libelle(document, "facture.mention.conservation") + "\n\n",
                police(8, Font.NORMAL, GRIS_TEXTE)));
        mentions.add(new Phrase("%s — %s %s — %s %s — %s %s".formatted(
                        garage.raisonSociale(),
                        libelle(document, "facture.emetteur.bce"), garage.numeroBce(),
                        libelle(document, "facture.emetteur.tva"), garage.numeroTva(),
                        libelle(document, "facture.emetteur.iban"), garage.iban()),
                police(8, Font.NORMAL, GRIS_TEXTE)));
        return mentions;
    }

    private String libelle(DocumentFacture document, String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, document.locale());
    }

}
