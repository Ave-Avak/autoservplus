package be.autoservplus.comptabilite.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ecriture de CSV destines a un tableur (BL-3).
 *
 * <p><b>Point-virgule et non virgule</b> : le comptable ouvrira le fichier dans un
 * Excel configure en locale belge, ou la virgule est le separateur decimal. Un CSV a
 * la virgule y arrive sur une seule colonne.</p>
 *
 * <p><b>BOM UTF-8 en tete</b> : sans lui, Excel lit le fichier en ANSI et les accents
 * deviennent illisibles. Ce n est pas requis par la norme CSV, c est requis par le
 * logiciel qui va reellement l ouvrir.</p>
 *
 * <p><b>Neutralisation des formules.</b> Une cellule commencant par {@code =}, {@code +},
 * {@code -}, {@code @}, une tabulation ou un retour chariot est interpretee comme une
 * formule par Excel et LibreOffice. Un libelle d article saisi au back-office finit
 * dans ce fichier : sans precaution, un article nomme
 * {@code =HYPERLINK("http://...")} s executerait sur le poste du comptable. Chaque
 * valeur a risque est donc prefixee d une apostrophe, qui force le mode texte.</p>
 */
public final class RedacteurCsv {

    /** Excel attend CRLF, y compris sous Linux. */
    private static final String FIN_DE_LIGNE = "\r\n";
    private static final char SEPARATEUR = ';';
    private static final String BOM = "﻿";
    private static final String AMORCES_DE_FORMULE = "=+-@\t\r";

    private final StringBuilder tampon = new StringBuilder(BOM);

    public RedacteurCsv(String... entetes) {
        ligne((Object[]) entetes);
    }

    /** Ajoute une ligne ; {@code null} devient une cellule vide. */
    public RedacteurCsv ligne(Object... cellules) {
        for (int i = 0; i < cellules.length; i++) {
            if (i > 0) {
                tampon.append(SEPARATEUR);
            }
            tampon.append(echapper(cellules[i]));
        }
        tampon.append(FIN_DE_LIGNE);
        return this;
    }

    public RedacteurCsv lignes(List<Object[]> lignes) {
        lignes.forEach(this::ligne);
        return this;
    }

    public String texte() {
        return tampon.toString();
    }

    /**
     * Rend une cellule sure : virgule decimale pour les montants, neutralisation des
     * formules, et guillemets doubles si le contenu porte un caractere de structure.
     */
    private static String echapper(Object valeur) {
        if (valeur == null) {
            return "";
        }
        String texte = valeur instanceof BigDecimal montant
                // Virgule decimale : le tableur belge ne reconnait pas « 45.00 » comme
                // un nombre, il l affiche comme du texte et le total ne se fait pas.
                ? montant.toPlainString().replace('.', ',')
                : valeur.toString();

        if (!texte.isEmpty() && AMORCES_DE_FORMULE.indexOf(texte.charAt(0)) >= 0) {
            texte = "'" + texte;
        }
        if (texte.indexOf(SEPARATEUR) >= 0 || texte.indexOf('"') >= 0
                || texte.indexOf('\n') >= 0 || texte.indexOf('\r') >= 0) {
            return '"' + texte.replace("\"", "\"\"") + '"';
        }
        return texte;
    }
}
