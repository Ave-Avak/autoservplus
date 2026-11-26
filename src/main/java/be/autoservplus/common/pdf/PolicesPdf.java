package be.autoservplus.common.pdf;

import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Polices des documents PDF : Inter, la police de la charte et du site, incorporée dans
 * chaque document.
 *
 * <p>Helvetica, l'une des quatorze polices que tout lecteur PDF possède, évitait de livrer
 * un fichier de police, mais elle s'écrit en WinAnsi : l'espace fine insécable (U+202F) que
 * Java place entre les milliers d'un montant en français n'y existe pas. Inter la porte, et
 * OpenPDF n'incorpore dans chaque document que les glyphes employés.</p>
 *
 * <p>Les fichiers sous {@code polices/pdf} sont réduits au jeu du français, du néerlandais et
 * de l'anglais, en trois styles seulement (normal, gras, italique). Leurs chiffres pointent
 * vers les glyphes tabulaires d'Inter : OpenPDF lit le glyphe que le cmap donne à un
 * caractère sans appliquer la fonction OpenType {@code tnum}, et les montants d'une colonne
 * ne s'aligneraient pas avec les chiffres proportionnels.</p>
 */
public final class PolicesPdf {

    private static final String DOSSIER = "/polices/pdf/";

    // Chargées une fois : le fichier est lu du classpath, chaque document n'en garde que ses
    // glyphes, et BaseFont se partage entre documents.
    static final BaseFont NORMALE = charger("inter-regular.ttf");
    static final BaseFont GRASSE = charger("inter-bold.ttf");
    static final BaseFont ITALIQUE = charger("inter-italic.ttf");

    private PolicesPdf() {
    }

    /**
     * Police à la taille, au style ({@link Font#NORMAL}, {@link Font#BOLD} ou
     * {@link Font#ITALIC}) et à la couleur donnés. Le style choisit le fichier : un gras
     * simulé par OpenPDF à partir du normal serait plus épais et moins net.
     */
    public static Font police(float taille, int style, Color couleur) {
        BaseFont base = (style & Font.BOLD) != 0 ? GRASSE
                : (style & Font.ITALIC) != 0 ? ITALIQUE
                : NORMALE;
        return new Font(base, taille, Font.NORMAL, couleur);
    }

    private static BaseFont charger(String fichier) {
        try (InputStream flux = PolicesPdf.class.getResourceAsStream(DOSSIER + fichier)) {
            if (flux == null) {
                throw new IllegalStateException("Police absente du classpath : " + DOSSIER + fichier);
            }
            return BaseFont.createFont(fichier, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true,
                    flux.readAllBytes(), null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
