package be.autoservplus.comptabilite.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedacteurCsv (BL-3)")
class RedacteurCsvTest {

    private static final String BOM = "﻿";

    @Nested
    @DisplayName("Format attendu par un tableur belge")
    class Format {

        @Test
        @DisplayName("commence par le BOM UTF-8, sans quoi Excel casse les accents")
        void bom() {
            assertThat(new RedacteurCsv("Numéro").texte()).startsWith(BOM);
        }

        @Test
        @DisplayName("separe par point-virgule et termine par CRLF")
        void separateurEtFinDeLigne() {
            String csv = new RedacteurCsv("a", "b").ligne("1", "2").texte();

            assertThat(csv).isEqualTo(BOM + "a;b\r\n1;2\r\n");
        }

        @Test
        @DisplayName("ecrit les montants a la virgule decimale")
        void virguleDecimale() {
            String csv = new RedacteurCsv("montant").ligne(new BigDecimal("45.50")).texte();

            assertThat(csv)
                    .as("un tableur belge lirait 45.50 comme du texte et ne totaliserait pas")
                    .contains("45,50");
        }

        @Test
        @DisplayName("une cellule nulle devient une cellule vide, pas la chaine null")
        void celluleNulle() {
            assertThat(new RedacteurCsv("a", "b").ligne("x", null).texte())
                    .endsWith("x;\r\n");
        }
    }

    @Nested
    @DisplayName("Echappement")
    class Echappement {

        @Test
        @DisplayName("entoure de guillemets une valeur portant le separateur")
        void separateurDansLaValeur() {
            assertThat(new RedacteurCsv("a").ligne("Dupont; Marie").texte())
                    .contains("\"Dupont; Marie\"");
        }

        @Test
        @DisplayName("double les guillemets internes")
        void guillemetsInternes() {
            assertThat(new RedacteurCsv("a").ligne("Filtre \"sport\"").texte())
                    .contains("\"Filtre \"\"sport\"\"\"");
        }

        @Test
        @DisplayName("preserve un retour a la ligne en le mettant entre guillemets")
        void retourLigne() {
            assertThat(new RedacteurCsv("a").ligne("ligne1\nligne2").texte())
                    .contains("\"ligne1\nligne2\"");
        }
    }

    @Nested
    @DisplayName("Neutralisation des formules")
    class InjectionCsv {

        @ParameterizedTest
        @ValueSource(strings = {
                "=HYPERLINK(\"http://mechant\")",
                "+SOMME(A1:A9)",
                "-2+3",
                "@SUM(1)"
        })
        @DisplayName("prefixe d une apostrophe toute cellule qu un tableur executerait")
        void amorcesNeutralisees(String dangereux) {
            String csv = new RedacteurCsv("libelle").ligne(dangereux).texte();

            assertThat(csv)
                    .as("un libelle saisi au back-office finit dans ce fichier : sans "
                            + "neutralisation, il s executerait sur le poste du comptable")
                    .contains("'" + dangereux.charAt(0));
        }

        @Test
        @DisplayName("ne touche pas une valeur inoffensive")
        void valeurNormaleIntacte() {
            assertThat(new RedacteurCsv("a").ligne("Vidange").texte())
                    .contains("Vidange")
                    .doesNotContain("'Vidange");
        }

        @Test
        @DisplayName("un montant negatif est neutralise puis reste lisible")
        void montantNegatif() {
            String csv = new RedacteurCsv("a").ligne(new BigDecimal("-150.00")).texte();

            assertThat(csv).contains("'-150,00");
        }
    }
}
