# Ressources tierces

Ce fichier recense les ressources tierces embarquées dans le dépôt et leurs
licences. Les dépendances Maven ne figurent pas ici : elles sont déclarées dans
[`pom.xml`](pom.xml), avec leurs licences respectives.

## SecLists, liste de mots de passe courants

`src/main/resources/securite/mots-de-passe-courants.txt`

Extrait de [SecLists](https://github.com/danielmiessler/SecLists), fichier
`Passwords/Common-Credentials/xato-net-10-million-passwords-1000000.txt`, filtré aux
entrées de quinze caractères ou plus, le seuil minimal de la politique de mot de passe
du projet. Les entrées plus courtes sont déjà refusées par la longueur : les
embarquer n'aurait servi à rien. Le filtrage ramène 1 000 000 d'entrées (8,6 Mo) à
10 908 (188 Ko).

Usage : refuser à l'inscription et au changement les mots de passe connus pour être
fréquents, conformément aux recommandations du NIST sur les listes de refus.

> MIT License
>
> Copyright (c) 2025 Daniel Miessler
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

Mainteneurs du projet : Daniel Miessler, Jason Haddix, Ignacio Portal, g0tmi1k.

## HTMX

`src/main/resources/static/js/htmx.min.js`

[HTMX](https://htmx.org), servi localement et non depuis un CDN : la politique de
sécurité du contenu n'autorise `script-src` que sur `'self'`. Licence Zero-Clause
BSD (0BSD), qui n'impose aucune obligation d'attribution. La mention figure ici par
transparence.

## ZXing, génération du QR code d'enrôlement

Dépendance Maven `com.google.zxing:core` (voir [`pom.xml`](pom.xml)), mentionnée ici par
exception : elle touche au second facteur, et son périmètre d'usage mérite d'être lisible
sans ouvrir le code.

[ZXing](https://github.com/zxing/zxing), licence Apache 2.0. Seul le module `core`
est employé : il calcule la matrice du QR code, que le serveur rend en SVG inline.
Le module `javase` (AWT, ImageIO) n'est pas embarqué, et aucun service externe n'est
appelé. L'URI `otpauth://` porte le secret TOTP et ne quitte donc jamais le serveur
autrement que dans la page d'enrôlement elle-même.

## Notice harmonisée de l'UE sur la garantie légale de conformité

`src/main/resources/static/images/notice-garantie/notice-garantie-fr.svg`, `-nl.svg`, `-en.svg`
et `src/main/resources/notice-garantie/notice-garantie-fr.pdf`, `-nl.pdf`, `-en.pdf`

© Union européenne. Contenu publié par la Commission européenne (DG Justice et
consommateurs) sous licence [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/),
sur la page « Practical guidelines and high resolution vector files, EU notice and label
for product guarantees » :
<https://commission.europa.eu/publications/practical-guidelines-and-high-resolution-vector-files-eu-notice-and-label-product-guarantees_en>.
Maquette et contenu fixés par le règlement d'exécution (UE) 2025/1960 de la Commission
du 25 septembre 2025, annexe I. La notice reproduit l'emblème européen : il est affiché
parce que le règlement impose la notice telle quelle, et n'est employé à aucun autre titre.

Sources téléchargées le 2026-11-19 :

- `SVG.zip`, SHA-256 `6797b40b19af8c26906ef31b8aec3351685b9b6fb4d3d9a3bffa90f14bdf6b84` :
  fichiers `Legal guarantee_notice FR.svg`, `NL.svg` et `EN.svg` (couleur), repris sans
  modification sous les noms ci-dessus.
- `Harmonised notice in 24 languages colour and black and white_0.zip`, SHA-256
  `baae862aabc57e79ae28ebc2dcfb445f5c8ec768bb558b8518b1f2c3b928c992` : fichiers
  `Legal guarantee_notice FRN.pdf`, `NLN.pdf` et `ENN.pdf`. Chacun porte deux pages, la
  notice en couleur puis en noir et blanc, réservée aux magasins physiques. Seule la
  page 1 est conservée, copiée par `PdfCopy` d'OpenPDF sans être redessinée : son texte
  est identique à celui de la page 1 d'origine.

Changement apporté : extraction de la page 1 des PDF. Les SVG sont inchangés. Les
empreintes des six fichiers sont vérifiées par `NoticeGarantieFichiersTest`.

Usage : notice affichée sur la page « Garantie légale » et jointe au courriel de
confirmation d'une commande de pièces (art. VI.45 § 1er, 12° du Code de droit économique).

## Bootstrap 5.3.9

Sources Sass prises au build dans le webjar `org.webjars.npm:bootstrap` (Maven
Central), compilées avec le thème de la charte dans `static/css/site.css`. Le script
`static/js/bootstrap.bundle.min.js` est copié du même webjar au build. Aucun des deux
n'est versionné : ils sont produits par `src/build/java/CompilationFeuilleDeStyle.java`
à la phase `generate-resources`, et la version se lit dans `pom.xml`
(`bootstrap.version`).

Les fichiers de `src/main/resources/static/images/icones` reprennent les tracés SVG que
Bootstrap écrit en images `data:` dans `_variables.scss` et `_variables-dark.scss`, aux
couleurs de la charte. Licence MIT, Copyright (c) 2011-2026 The Bootstrap Authors,
https://github.com/twbs/bootstrap/blob/v5.3.9/LICENSE.

Le compilateur Dart Sass (licence MIT), livré par `de.larsgrefer.sass:sass-embedded-host`
(licence MIT), ne sert qu'au build et n'est pas dans l'archive.

## Inter 4.1

`src/main/resources/static/css/polices`, quatre graisses (400, 600, 700, 800) tirées de
la publication 4.1 de https://github.com/rsms/inter, réduites au jeu latin du français,
du néerlandais et de l'anglais (euro et espace fine compris), au format WOFF2. Licence
SIL Open Font License 1.1, texte complet dans `OFL.txt` à côté des fichiers. La licence
ne déclare aucun nom de police réservé : la version réduite garde le nom Inter.

| Fichier | SHA-256 |
|---|---|
| `inter-regular.woff2` | `ae7955503fd68d8c4e0fd3bc4c8d197ae4004fdd8bcf0bb57e2c0279e5ca5f30` |
| `inter-semibold.woff2` | `158818759adcbf2abe381d34a55de3fae9343a98e038df02d4b9f5582cb4bf37` |
| `inter-bold.woff2` | `ba1e692269013df135b7e944f6ac93f0ee7b5e928499c7176879a465b1c8b79b` |
| `inter-extrabold.woff2` | `89ddf2c94cc71fbcc1900edd9f53e3fc3b6f66b39e16fe1bf73f76aa5e6b829c` |

Les lettres du logo AutoServ+ (`src/main/resources/static/images/logo`) sont les contours
d'Inter ExtraBold (le A et le mot) et d'Inter Bold (le +), tirés des deux fichiers
ci-dessus et convertis en tracés. L'OFL laisse libre l'emploi des glyphes dans une image
produite avec la police, et le SVG ne dépend plus d'aucune police installée.

## Heroicons 2.2.0

`src/main/resources/templates/fragments/icones.html` : dix icônes (neuf en contour de 24 px,
une étoile pleine) recopiées telles quelles, tracés compris, depuis le paquet `heroicons`
2.2.0 de Tailwind Labs (https://github.com/tailwindlabs/heroicons), licence MIT. Archive
lue : `heroicons-2.2.0.tgz` du registre npm, SHA-256
`6c3e9d04b8495c9512a91b0b08804f2c3614a8e5d7df39bbfe02b6450fb7c640`. L'attribut `data-slot` est
retiré, `class` et `focusable` sont ajoutés. Les icônes sont écrites dans la page, aucune
requête ne part vers un service tiers.
