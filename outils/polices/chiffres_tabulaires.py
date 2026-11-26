"""Fait pointer les chiffres 0 à 9 du cmap d'Inter vers leurs glyphes tabulaires.

OpenPDF lit le glyphe que le cmap donne à un caractère et n'applique pas la fonction
OpenType tnum. Les chiffres par défaut d'Inter sont proportionnels (« 1 » plus étroit que
« 0 »), et les montants d'une colonne de facture ne s'aligneraient pas. Après ce script, les
chiffres U+0030 à U+0039 pointent vers zero.tf à nine.tf, de même largeur.

Usage, avant la réduction décrite dans README.md :
    python -E chiffres_tabulaires.py Inter-Regular.ttf tnum-regular.ttf
"""
import sys

from fontTools.ttLib import TTFont

NOMS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]


def main(entree, sortie):
    # La date de la table head reste celle de la publication : sans cela, chaque passage
    # produirait un fichier différent, et le dépôt ne serait pas reproductible.
    police = TTFont(entree, recalcTimestamp=False)
    glyphes = set(police.getGlyphOrder())
    absents = [nom + ".tf" for nom in NOMS if nom + ".tf" not in glyphes]
    if absents:
        raise SystemExit("glyphes tabulaires absents : " + ", ".join(absents))
    for table in police["cmap"].tables:
        if table.isUnicode():
            for rang, nom in enumerate(NOMS):
                if 0x30 + rang in table.cmap:
                    table.cmap[0x30 + rang] = nom + ".tf"
    police.save(sortie)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage : python -E chiffres_tabulaires.py entree.ttf sortie.ttf")
    main(sys.argv[1], sys.argv[2])
