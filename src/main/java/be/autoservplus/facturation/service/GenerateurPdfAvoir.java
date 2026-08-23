package be.autoservplus.facturation.service;

import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.facturation.service.dto.DocumentAvoir;
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
 * Composition du PDF d une note de credit (F30), avec OpenPDF — jamais iText, dont
 * la licence AGPL est incompatible avec un logiciel proprietaire.
 *
 * <p><b>Mentions obligatoires du document rectificatif</b> (AR n°1, art. 12) : outre
 * celles d une facture — numero, date, identification complete de l emetteur et du
 * client, detail par ligne, ventilation de la base imposable par taux — la note de
 * credit porte la <b>reference precise de la facture qu elle corrige</b> (numero et
 * date) et la mention de restitution de TVA. Sans ce rattachement, elle ne corrige
 * rien de verifiable.</p>
 *
 * <p>Meme charte et memes primitives que la facture ({@link StylePdf}) : le client
 * recoit les deux documents et les compare. Seuls changent le titre, le bloc de
 * reference a l original et les mentions de pied — c est-a-dire exactement ce qui
 * distingue les deux documents, et rien d autre.</p>
 *
 * <p>Aucune chaine en dur : chaque libelle passe par le {@code MessageSource}, dans
 * la langue du membre — la meme que celle de la facture corrigee.</p>
 */
@Component
public class GenerateurPdfAvoir {

    private final MessageSource messages;
    private final IdentiteGarage garage;

    public GenerateurPdfAvoir(MessageSource messages, IdentiteGarage garage) {
        this.messages = messages;
        this.garage = garage;
    }

    public byte[] engendrer(DocumentAvoir document) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4, 42, 42, 42, 54);
        try {
            PdfWriter.getInstance(pdf, sortie);
            pdf.addTitle(libelle(document, "avoir.titre") + " " + document.numero());
            pdf.addCreator(garage.raisonSociale());
            pdf.open();
            pdf.add(enTete(document));
            pdf.add(blocsIdentite(document));
            pdf.add(tableauDesLignes(document));
            pdf.add(totaux(document));
            pdf.add(mentionsLegales(document));
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "Composition du PDF de l avoir %s impossible.".formatted(document.numero()), e);
        } finally {
            if (pdf.isOpen()) {
                pdf.close();
            }
        }
        return sortie.toByteArray();
    }

    // --- blocs du document -------------------------------------------------------------

    /**
     * Bandeau : emetteur a gauche, identite du document a droite — avec la reference
     * a la facture corrigee, qui est la mention distinctive de l avoir.
     */
    private PdfPTable enTete(DocumentAvoir document) {
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
        identite.add(new Phrase(libelle(document, "avoir.titre").toUpperCase(document.locale()) + "\n",
                police(22, Font.BOLD, BLEU_MARINE)));
        identite.add(new Phrase(document.numero() + "\n", police(13, Font.BOLD, GRIS_TEXTE)));
        identite.add(new Phrase("%s %s\n".formatted(
                        libelle(document, "facture.date-emission"),
                        date(document.dateEmission(), document.locale())),
                police(9, Font.NORMAL, GRIS_TEXTE)));
        // Rattachement au document corrige : mention obligatoire (AR n°1, art. 12).
        identite.add(new Phrase("%s\n".formatted(libelle(document, "avoir.facture-origine",
                        document.numeroFactureOrigine(),
                        date(document.dateFactureOrigine(), document.locale()))),
                police(9, Font.BOLD, GRIS_TEXTE)));
        identite.add(new Phrase("%s %s".formatted(
                        libelle(document, "facture.commande"), document.numeroCommande()),
                police(9, Font.NORMAL, GRIS_TEXTE)));
        entete.addCell(celluleInvisible(identite, Element.ALIGN_RIGHT));
        return entete;
    }

    /** Destinataire de la note de credit : le titulaire de la facture corrigee. */
    private PdfPTable blocsIdentite(DocumentAvoir document) {
        PdfPTable bloc = tableauInvisible(new float[]{55, 45});
        bloc.setSpacingAfter(20);

        Paragraph client = new Paragraph();
        client.add(new Phrase(libelle(document, "avoir.client") + "\n",
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

    /**
     * Detail par ligne, identique a celui de la facture : c est le meme achat qui est
     * annule. Les montants restent positifs — le sens du document est porte par son
     * titre et par sa mention de pied, pas par un signe que le lecteur devrait
     * interpreter.
     */
    private PdfPTable tableauDesLignes(DocumentAvoir document) {
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

    /** Totaux credites et ventilation de la TVA restituee par taux. */
    private PdfPTable totaux(DocumentAvoir document) {
        PdfPTable conteneur = tableauInvisible(new float[]{50, 50});
        conteneur.setSpacingAfter(24);
        conteneur.addCell(celluleInvisible(ventilation(document), Element.ALIGN_LEFT));

        PdfPTable synthese = new PdfPTable(new float[]{60, 40});
        synthese.setWidthPercentage(100);
        synthese.addCell(celluleTotal(libelle(document, "facture.total.htva"), false));
        synthese.addCell(celluleTotal(montant(document.totalHtva(), document.locale()), false));
        synthese.addCell(celluleTotal(libelle(document, "facture.total.tva"), false));
        synthese.addCell(celluleTotal(montant(document.totalTva(), document.locale()), false));
        synthese.addCell(celluleTotal(libelle(document, "avoir.total.credite"), true));
        synthese.addCell(celluleTotal(montant(document.totalTvac(), document.locale()), true));
        conteneur.addCell(celluleInvisible(synthese, Element.ALIGN_RIGHT));
        return conteneur;
    }

    private PdfPTable ventilation(DocumentAvoir document) {
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

    /** Motif de la correction, restitution de TVA et mentions legales de pied. */
    private Paragraph mentionsLegales(DocumentAvoir document) {
        Paragraph mentions = new Paragraph();
        mentions.add(new Phrase(libelle(document, "avoir.mention.motif",
                        libelle(document, "avoir.motif." + document.cleMotif())) + "\n",
                police(9, Font.BOLD, GRIS_TEXTE)));
        mentions.add(new Phrase(libelle(document, "avoir.mention.remboursement") + "\n",
                police(8, Font.NORMAL, GRIS_TEXTE)));
        // Mention de restitution : c est elle qui autorise l emetteur a recuperer la
        // TVA deja declaree sur la facture d origine.
        mentions.add(new Phrase(libelle(document, "avoir.mention.tva") + "\n",
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

    private String libelle(DocumentAvoir document, String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, document.locale());
    }
}
