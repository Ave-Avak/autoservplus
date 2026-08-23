package be.autoservplus.importcsv.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyse d un CSV simple (BL-2).
 *
 * <p><b>Ecrit a la main plutot qu avec OpenCSV ou Commons CSV.</b> Le format attendu
 * est celui que produit un tableur belge — point-virgule, guillemets doubles, CRLF —
 * et il est deja ecrit en sortie par {@code RedacteurCsv} : ajouter une dependance
 * pour relire ce que nous ecrivons nous-memes ferait entrer une bibliotheque, sa
 * configuration et ses conventions pour un besoin de trente lignes. Le jour ou le
 * format se complique (encodages multiples, separateurs variables), la question se
 * reposera.</p>
 *
 * <p><b>Le BOM est retire</b> s il est present : un fichier reexporte depuis Excel en
 * porte un, et sans ce retrait le premier en-tete s appellerait
 * {@code ﻿code} et ne serait jamais reconnu.</p>
 */
public final class LecteurCsv {

    private static final char SEPARATEUR = ';';
    private static final char GUILLEMET = '"';
    private static final char BOM = '﻿';

    private LecteurCsv() {
    }

    /**
     * Decoupe un contenu en lignes de cellules.
     *
     * <p>Les lignes entierement vides sont ignorees : un tableur ajoute volontiers une
     * ligne finale vide, qui ne doit pas compter comme une erreur d import.</p>
     */
    public static List<List<String>> lire(String contenu) {
        List<List<String>> lignes = new ArrayList<>();
        if (contenu == null || contenu.isEmpty()) {
            return lignes;
        }
        String texte = contenu.charAt(0) == BOM ? contenu.substring(1) : contenu;

        List<String> cellules = new ArrayList<>();
        StringBuilder cellule = new StringBuilder();
        boolean entreGuillemets = false;

        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (entreGuillemets) {
                if (c == GUILLEMET) {
                    // Deux guillemets consecutifs : un guillemet litteral, pas une fin
                    // de champ. C est l echappement du format, celui que nous ecrivons.
                    if (i + 1 < texte.length() && texte.charAt(i + 1) == GUILLEMET) {
                        cellule.append(GUILLEMET);
                        i++;
                    } else {
                        entreGuillemets = false;
                    }
                } else {
                    cellule.append(c);
                }
            } else if (c == GUILLEMET) {
                entreGuillemets = true;
            } else if (c == SEPARATEUR) {
                cellules.add(cellule.toString().strip());
                cellule.setLength(0);
            } else if (c == '\n') {
                cellules.add(cellule.toString().strip());
                cellule.setLength(0);
                ajouter(lignes, cellules);
                cellules = new ArrayList<>();
            } else if (c != '\r') {
                cellule.append(c);
            }
        }
        cellules.add(cellule.toString().strip());
        ajouter(lignes, cellules);
        return lignes;
    }

    private static void ajouter(List<List<String>> lignes, List<String> cellules) {
        if (cellules.stream().anyMatch(cellule -> !cellule.isEmpty())) {
            lignes.add(List.copyOf(cellules));
        }
    }
}
