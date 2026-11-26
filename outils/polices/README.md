# Polices des documents PDF

Ce dossier reproduit les trois fichiers de `src/main/resources/polices/pdf`. Il est hors de la
build Maven : les commandes se lancent à la main, une fois, quand la police change.

## Provenance

Inter 4.1, publication officielle du projet : https://github.com/rsms/inter/releases/tag/v4.1.
Archive `https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip`, 33 707 794 octets,
SHA-256 `9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e`. L'archive ne va pas
dans le dépôt. Les fichiers de départ sont `extras/ttf/Inter-Regular.ttf`, `Inter-Bold.ttf` et
`Inter-Italic.ttf`. Licence SIL Open Font License 1.1, texte dans
`src/main/resources/polices/pdf/OFL.txt`.

## Outil

fontTools 4.57.0, installé pour l'utilisateur (`python -m pip install --user fonttools`) ou dans
un environnement virtuel, jamais dans le `pom.xml`.

## Commandes

Pour chaque style, `Regular`, `Bold` et `Italic`, le nom de sortie en minuscules :

```
python -E chiffres_tabulaires.py Inter-Regular.ttf tnum-regular.ttf
python -E -m fontTools.subset tnum-regular.ttf "--unicodes=U+0020-007E,U+00A0-017F,U+2019,U+201C-201E,U+2026,U+202F,U+20AC" "--layout-features=*" "--name-IDs=*" --notdef-outline --output-file=inter-regular.ttf
```

Le premier script fait pointer les chiffres vers leurs glyphes tabulaires, qu'OpenPDF
n'applique pas de lui-même. La réduction garde le jeu du français, du néerlandais et de
l'anglais, l'espace fine insécable des milliers et l'euro, et toutes les fonctions de mise en
page. Seuls ces trois styles sont gardés : aucun document n'emploie une autre graisse.

## Résultat

| Fichier | Octets | SHA-256 |
|---|---|---|
| `inter-regular.ttf` | 111 208 | `2257dc9a11b29a83c0b322e018b8c56f439a4b8d191a586dfa5c7a3aa952133d` |
| `inter-bold.ttf` | 113 240 | `ce754f69d05710b2eafb7aea0eaf72f12c47be1c81da98742bcee63cccdf4e40` |
| `inter-italic.ttf` | 112 280 | `ed76a524d01b71748dbf4f641ac6a5f5bbc9ad2e8372b9bdee5d0c8620b9b3ce` |

`PolicesPdfTest` vérifie sur la police chargée que les dix chiffres ont la même largeur et que
les caractères des trois langues existent.
