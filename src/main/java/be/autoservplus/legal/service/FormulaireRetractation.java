package be.autoservplus.legal.service;

import be.autoservplus.config.IdentiteGarage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modèle de formulaire de rétractation (CDE, livre VI, annexe 2), prérempli de
 * l'identité du garage comme le modèle le demande : nom, adresse géographique et
 * adresse électronique.
 *
 * <p>Le texte est celui de l'annexe 2 en français et en néerlandais (Justel, texte
 * consolidé), et de l'annexe I, B, de la directive 2011/83/UE en anglais (EUR-Lex,
 * consolidation du 27-09-2026), repris mot pour mot dans les clés
 * {@code retractation.formulaire.*}, sans les marques d'édition des deux sites. Seule
 * la mention entre crochets que l'entreprise doit compléter est remplacée par
 * l'identité du garage.</p>
 *
 * <p>La page HTML et le PDF lisent les mêmes lignes, dans le même ordre : un
 * consommateur qui imprime le PDF remplit exactement le formulaire affiché.</p>
 */
@Service
public class FormulaireRetractation {

    /** Lignes du formulaire, dans l'ordre de l'annexe, après l'en-tête et le destinataire. */
    private static final List<String> RUBRIQUES = List.of(
            "notification", "commande", "nom", "adresse", "signature", "date");

    private static final Color BLEU_MARINE = new Color(0x1F, 0x38, 0x64);

    private final IdentiteGarage garage;
    private final MessageSource messages;

    public FormulaireRetractation(IdentiteGarage garage, MessageSource messages) {
        this.garage = garage;
        this.messages = messages;
    }

    /**
     * Le formulaire dans une langue.
     *
     * @param titre        intitulé du modèle
     * @param entete       consigne d'usage, entre parenthèses dans l'annexe
     * @param destinataire ligne d'adresse, préremplie de l'identité du garage
     * @param rubriques    rubriques à compléter par le consommateur
     * @param note         renvoi de l'astérisque
     */
    public record Formulaire(String titre, String entete, String destinataire,
                             List<String> rubriques, String note) {
    }

    public Formulaire formulaire(Locale langue) {
        List<String> rubriques = new ArrayList<>();
        for (String rubrique : RUBRIQUES) {
            rubriques.add(texte("retractation.formulaire." + rubrique, langue));
        }
        return new Formulaire(
                texte("retractation.formulaire.titre", langue),
                texte("retractation.formulaire.entete", langue),
                messages.getMessage("retractation.formulaire.destinataire",
                        new Object[]{identiteDuGarage(langue)}, langue),
                List.copyOf(rubriques),
                texte("retractation.formulaire.note", langue));
    }

    /** Nom, adresse géographique et adresse électronique du garage, sur une ligne. */
    String identiteDuGarage(Locale langue) {
        IdentiteGarage garage = this.garage.pour(langue);
        String adresse = garage.adresseLisible();
        if (garage.pays() != null && !garage.pays().isBlank()) {
            adresse = adresse + ", " + garage.pays();
        }
        return "%s, %s, %s".formatted(garage.denomination(), adresse, garage.courriel());
    }

    /** Le même formulaire en PDF, support durable joint ou téléchargé. */
    public byte[] pdf(Locale langue) {
        Formulaire formulaire = formulaire(langue);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 56, 56, 56, 56);
        try {
            PdfWriter.getInstance(document, sortie);
            document.addTitle(formulaire.titre());
            document.open();
            Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BLEU_MARINE);
            Font corps = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font petit = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10);
            document.add(paragraphe(formulaire.titre(), titre, 12));
            document.add(paragraphe(formulaire.entete(), petit, 14));
            document.add(paragraphe(formulaire.destinataire(), corps, 14));
            for (String rubrique : formulaire.rubriques()) {
                // Un blanc sous chaque rubrique : le consommateur la complète à la main.
                document.add(paragraphe(rubrique, corps, 28));
            }
            document.add(paragraphe(formulaire.note(), petit, 0));
            document.close();
        } catch (DocumentException echec) {
            throw new IllegalStateException("Formulaire de rétractation non produit.", echec);
        }
        return sortie.toByteArray();
    }

    private static Paragraph paragraphe(String texte, Font police, float espaceApres) {
        Paragraph paragraphe = new Paragraph(texte, police);
        paragraphe.setSpacingAfter(espaceApres);
        return paragraphe;
    }

    private String texte(String cle, Locale langue) {
        return messages.getMessage(cle, null, langue);
    }
}
