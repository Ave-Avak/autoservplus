package be.autoservplus.importcsv.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LecteurCsv (BL-2)")
class LecteurCsvTest {

    @Test
    @DisplayName("decoupe sur le point-virgule et supprime les espaces de bord")
    void decoupageSimple() {
        var lignes = LecteurCsv.lire("a;b;c\r\n1; 2 ;3\r\n");

        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(0)).containsExactly("a", "b", "c");
        assertThat(lignes.get(1)).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("retire le BOM, sans quoi le premier en-tete ne serait jamais reconnu")
    void bomRetire() {
        var lignes = LecteurCsv.lire("﻿code;libelle\r\nVID;Vidange\r\n");

        assertThat(lignes.getFirst().getFirst())
                .as("un fichier reexporte depuis Excel porte un BOM")
                .isEqualTo("code");
    }

    @Test
    @DisplayName("respecte les guillemets et le point-virgule qu ils protegent")
    void guillemets() {
        var lignes = LecteurCsv.lire("a;b\r\n\"Dupont; Marie\";x\r\n");

        assertThat(lignes.get(1)).containsExactly("Dupont; Marie", "x");
    }

    @Test
    @DisplayName("interprete le guillemet double comme un guillemet litteral")
    void guillemetEchappe() {
        var lignes = LecteurCsv.lire("a\r\n\"Filtre \"\"sport\"\"\"\r\n");

        assertThat(lignes.get(1)).containsExactly("Filtre \"sport\"");
    }

    @Test
    @DisplayName("relit ce que RedacteurCsv ecrit")
    void allerRetour() {
        String ecrit = new be.autoservplus.comptabilite.service.RedacteurCsv("code", "libelle")
                .ligne("VID", "Vidange; complete")
                .texte();

        var lignes = LecteurCsv.lire(ecrit);

        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(1)).containsExactly("VID", "Vidange; complete");
    }

    @Test
    @DisplayName("ignore les lignes entierement vides")
    void lignesVides() {
        var lignes = LecteurCsv.lire("a;b\r\n1;2\r\n\r\n;\r\n");

        assertThat(lignes)
                .as("un tableur ajoute volontiers une ligne finale vide")
                .hasSize(2);
    }

    @Test
    @DisplayName("un contenu vide ou nul ne rend aucune ligne")
    void contenuVide() {
        assertThat(LecteurCsv.lire("")).isEmpty();
        assertThat(LecteurCsv.lire(null)).isEmpty();
    }

    @Test
    @DisplayName("accepte un fichier a fin de ligne Unix")
    void finDeLigneUnix() {
        var lignes = LecteurCsv.lire("a;b\n1;2");

        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(1)).containsExactly("1", "2");
    }
}
