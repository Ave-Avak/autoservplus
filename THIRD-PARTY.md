# Ressources tierces

Ce fichier recense les ressources tierces **embarquées dans le dépôt** et leurs
licences. Les dépendances Maven ne figurent pas ici : elles sont déclarées dans
[`pom.xml`](pom.xml), avec leurs licences respectives.

## SecLists — liste de mots de passe courants

`src/main/resources/securite/mots-de-passe-courants.txt`

Extrait de [SecLists](https://github.com/danielmiessler/SecLists), fichier
`Passwords/Common-Credentials/xato-net-10-million-passwords-1000000.txt`, **filtré aux
entrées de quinze caractères ou plus** — le seuil minimal de la politique de mot de
passe du projet. Les entrées plus courtes sont déjà refusées par la longueur : les
embarquer n'aurait servi à rien. Le filtrage ramène 1 000 000 d'entrées (8,6 Mo) à
**10 908 (188 Ko)**.

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
sécurité du contenu n'autorise `script-src` que sur `'self'`. Licence **Zero-Clause
BSD (0BSD)**, qui n'impose aucune obligation d'attribution — la mention figure ici par
transparence.
