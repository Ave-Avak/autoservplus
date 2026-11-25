package be.autoservplus.devis.service;

import be.autoservplus.common.montant.CalculTva;
import be.autoservplus.config.IdentiteGarage;
import be.autoservplus.devis.domain.Devis;
import be.autoservplus.devis.domain.LigneDevis;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

import static be.autoservplus.facturation.service.StylePdf.BLEU_MARINE;
import static be.autoservplus.facturation.service.StylePdf.GRIS_TEXTE;
import static be.autoservplus.facturation.service.StylePdf.cellule;
import static be.autoservplus.facturation.service.StylePdf.celluleEntete;
import static be.autoservplus.facturation.service.StylePdf.celluleInvisible;
import static be.autoservplus.facturation.service.StylePdf.celluleTotal;
import static be.autoservplus.facturation.service.StylePdf.montant;
import static be.autoservplus.facturation.service.StylePdf.nonNul;
import static be.autoservplus.facturation.service.StylePdf.police;
import static be.autoservplus.facturation.service.StylePdf.pourcentage;
import static be.autoservplus.facturation.service.StylePdf.tableauInvisible;

/**
 * Composition du PDF d'un devis (F15), sur le modèle de la facture : même bandeau, même
 * tableau, même pied.
 *
 * <p>Le document est recomposé à chaque demande depuis l'instantané du devis, qui ne
 * change plus. Rien n'est archivé : contrairement à une facture, aucune obligation de
 * conservation ne vise un devis, et l'instantané suffit à le reproduire.</p>
 *
 * <p>Les lignes portent le prix unitaire taxe comprise, comme le panier dont le devis
 * est tiré : le membre y retrouve les prix qu'il a vus. La base hors TVA n'apparaît qu'à
 * la ventilation par taux.</p>
 *
 * <p>Les libellés communs à la facture (identifiants de l'émetteur, colonnes, totaux)
 * reprennent ses clés : les deux documents doivent employer les mêmes mots.</p>
 */
@Component
public class GenerateurPdfDevis {

    private final MessageSource messages;
    private final IdentiteGarage garage;

    public GenerateurPdfDevis(MessageSource messages, IdentiteGarage garage) {
        this.messages = messages;
        this.garage = garage;
    }

    /**
     * @param jourEmission jour de l'émission au fuseau du garage, imprimé sur le document
     */
    public byte[] engendrer(Devis devis, LocalDate jourEmission) {
        Locale langue = Locale.forLanguageTag(devis.getLangueDocument().name());
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document pdf = new Document(PageSize.A4, 42, 42, 42, 54);
        try {
            PdfWriter.getInstance(pdf, sortie);
            pdf.addTitle(libelle(langue, "devis.pdf.titre") + " " + devis.getNumero());
            pdf.addCreator(garage.raisonSociale());
            pdf.open();
            pdf.add(enTete(devis, jourEmission, langue));
            pdf.add(destinataire(devis, langue));
            pdf.add(tableauDesLignes(devis, langue));
            pdf.add(totaux(devis, langue));
            pdf.add(mentions(langue));
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "Composition du PDF du devis %s impossible.".formatted(devis.getNumero()), e);
        } finally {
            if (pdf.isOpen()) {
                pdf.close();
            }
        }
        return sortie.toByteArray();
    }

    /** Émetteur à gauche, identité du document et validité à droite. */
    private PdfPTable enTete(Devis devis, LocalDate jourEmission, Locale langue) {
        IdentiteGarage emetteur = garage.pour(langue);
        PdfPTable entete = tableauInvisible(new float[]{55, 45});
        entete.setSpacingAfter(24);

        Paragraph gauche = new Paragraph();
        gauche.add(new Phrase(emetteur.denomination() + "\n", police(15, Font.BOLD, BLEU_MARINE)));
        gauche.add(new Phrase(emetteur.adresseLisible() + "\n" + emetteur.pays() + "\n",
                police(9, Font.NORMAL, GRIS_TEXTE)));
        gauche.add(new Phrase("%s %s%s\n%s %s".formatted(
                        libelle(langue, "facture.emetteur.bce"), garage.numeroBce(),
                        emetteur.aUnRpm() ? "\n" + emetteur.rpm() : "",
                        libelle(langue, "facture.emetteur.tva"), garage.numeroTva()),
                police(9, Font.NORMAL, GRIS_TEXTE)));
        entete.addCell(celluleInvisible(gauche, Element.ALIGN_LEFT));

        Paragraph droite = new Paragraph();
        droite.add(new Phrase(libelle(langue, "devis.pdf.titre").toUpperCase(langue) + "\n",
                police(22, Font.BOLD, BLEU_MARINE)));
        droite.add(new Phrase(devis.getNumero() + "\n", police(13, Font.BOLD, GRIS_TEXTE)));
        droite.add(new Phrase("%s %s\n%s %s".formatted(
                        libelle(langue, "devis.pdf.date-emission"), date(jourEmission, langue),
                        libelle(langue, "devis.pdf.valable-jusqu-au"),
                        date(devis.getValableJusquAu(), langue)),
                police(9, Font.NORMAL, GRIS_TEXTE)));
        entete.addCell(celluleInvisible(droite, Element.ALIGN_RIGHT));
        return entete;
    }

    /** Client à gauche, véhicule à droite. */
    private PdfPTable destinataire(Devis devis, Locale langue) {
        PdfPTable bloc = tableauInvisible(new float[]{55, 45});
        bloc.setSpacingAfter(20);

        Paragraph client = new Paragraph();
        client.add(new Phrase(libelle(langue, "devis.pdf.client") + "\n", police(9, Font.BOLD, BLEU_MARINE)));
        client.add(new Phrase(devis.getClientPrenom() + " " + devis.getClientNom() + "\n", police(10, Font.NORMAL, GRIS_TEXTE)));
        if (devis.getClientRue() != null && devis.getClientLocalite() != null) {
            client.add(new Phrase("%s %s, %s %s\n%s\n".formatted(devis.getClientRue(),
                            nonNul(devis.getClientNumeroRue()), nonNul(devis.getClientCodePostal()),
                            devis.getClientLocalite(), nonNul(devis.getClientPays())),
                    police(9, Font.NORMAL, GRIS_TEXTE)));
        }
        bloc.addCell(celluleInvisible(client, Element.ALIGN_LEFT));

        Paragraph vehicule = new Paragraph();
        vehicule.add(new Phrase(libelle(langue, "devis.pdf.vehicule") + "\n", police(9, Font.BOLD, BLEU_MARINE)));
        vehicule.add(new Phrase(devis.getVehiculeMarque() + " " + devis.getVehiculeModele() + "\n",
                police(10, Font.NORMAL, GRIS_TEXTE)));
        vehicule.add(new Phrase(libelle(langue, "devis.pdf.plaque") + " " + devis.getVehiculePlaque(),
                police(9, Font.NORMAL, GRIS_TEXTE)));
        bloc.addCell(celluleInvisible(vehicule, Element.ALIGN_RIGHT));
        return bloc;
    }

    /** Description, quantité, prix unitaire taxe comprise, taux, total taxe comprise. */
    private PdfPTable tableauDesLignes(Devis devis, Locale langue) {
        PdfPTable table = new PdfPTable(new float[]{46, 10, 16, 12, 16});
        table.setWidthPercentage(100);
        table.setSpacingAfter(16);
        for (String cle : List.of("facture.colonne.description", "facture.colonne.quantite",
                "devis.pdf.colonne.prix-unitaire", "facture.colonne.taux", "facture.colonne.total-tvac")) {
            table.addCell(celluleEntete(libelle(langue, cle),
                    cle.endsWith("description") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT));
        }
        for (LigneDevis ligne : devis.getLignes()) {
            table.addCell(cellule(ligne.getLibelle(), Element.ALIGN_LEFT));
            table.addCell(cellule(String.valueOf(ligne.getQuantite()), Element.ALIGN_RIGHT));
            table.addCell(cellule(montant(ligne.prixUnitaireTvac(), langue), Element.ALIGN_RIGHT));
            table.addCell(cellule(pourcentage(ligne.getTauxTva()), Element.ALIGN_RIGHT));
            table.addCell(cellule(montant(ligne.totalTvac(), langue), Element.ALIGN_RIGHT));
        }
        return table;
    }

    /** Ventilation par taux à gauche, totaux à droite. */
    private PdfPTable totaux(Devis devis, Locale langue) {
        PdfPTable conteneur = tableauInvisible(new float[]{50, 50});
        conteneur.setSpacingAfter(24);

        PdfPTable ventilation = new PdfPTable(new float[]{34, 33, 33});
        ventilation.setWidthPercentage(100);
        ventilation.addCell(celluleEntete(libelle(langue, "facture.ventilation.taux"), Element.ALIGN_LEFT));
        ventilation.addCell(celluleEntete(libelle(langue, "facture.ventilation.base"), Element.ALIGN_RIGHT));
        ventilation.addCell(celluleEntete(libelle(langue, "facture.ventilation.tva"), Element.ALIGN_RIGHT));
        for (CalculTva.Tranche tranche : devis.ventilation()) {
            ventilation.addCell(cellule(pourcentage(tranche.taux()), Element.ALIGN_LEFT));
            ventilation.addCell(cellule(montant(tranche.base(), langue), Element.ALIGN_RIGHT));
            ventilation.addCell(cellule(montant(tranche.tva(), langue), Element.ALIGN_RIGHT));
        }
        conteneur.addCell(celluleInvisible(ventilation, Element.ALIGN_LEFT));

        PdfPTable synthese = new PdfPTable(new float[]{60, 40});
        synthese.setWidthPercentage(100);
        synthese.addCell(celluleTotal(libelle(langue, "facture.total.htva"), false));
        synthese.addCell(celluleTotal(montant(devis.getMontantHtva(), langue), false));
        synthese.addCell(celluleTotal(libelle(langue, "facture.total.tva"), false));
        synthese.addCell(celluleTotal(montant(devis.getMontantTva(), langue), false));
        synthese.addCell(celluleTotal(libelle(langue, "facture.total.tvac"), true));
        synthese.addCell(celluleTotal(montant(devis.getMontantTvac(), langue), true));
        conteneur.addCell(celluleInvisible(synthese, Element.ALIGN_RIGHT));
        return conteneur;
    }

    /** Validité et conditions, puis l'identité et les coordonnées du garage. */
    private Paragraph mentions(Locale langue) {
        IdentiteGarage emetteur = garage.pour(langue);
        Paragraph mentions = new Paragraph();
        mentions.add(new Phrase(libelle(langue, "devis.pdf.mention.validite", Devis.JOURS_DE_VALIDITE) + "\n",
                police(9, Font.BOLD, GRIS_TEXTE)));
        mentions.add(new Phrase(libelle(langue, "devis.pdf.mention.engagement") + "\n\n",
                police(8, Font.NORMAL, GRIS_TEXTE)));
        mentions.add(new Phrase("%s, %s %s, %s %s, %s, %s".formatted(
                        emetteur.denomination(),
                        libelle(langue, "facture.emetteur.bce"), garage.numeroBce(),
                        libelle(langue, "facture.emetteur.tva"), garage.numeroTva(),
                        garage.telephone(), garage.courriel()),
                police(8, Font.NORMAL, GRIS_TEXTE)));
        return mentions;
    }

    private static String date(LocalDate jour, Locale langue) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(langue).format(jour);
    }

    private String libelle(Locale langue, String cle, Object... arguments) {
        return messages.getMessage(cle, arguments, langue);
    }
}
