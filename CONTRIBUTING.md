# Conventions de développement — AutoServ+

## Rythme de dépôt

L'historique Git fait partie du dossier remis. Le règlement de l'épreuve intégrée prévoit
qu'un défaut de dépôt régulier du code sur une plateforme de versioning empêche la
présentation devant le jury.

- Un commit minimum par jour travaillé.
- Un commit par unité de travail achevée : une fonctionnalité, une correction, un test.
- Jamais de commit fourre-tout mêlant plusieurs sujets sans rapport.

## Format des messages

Convention *Conventional Commits*, rédigée en français.

    <type>(<portée>): <description à l'infinitif, sans majuscule ni point final>

    [corps facultatif : le pourquoi, pas le comment]

    [pied facultatif : Refs F14, RM-19]

### Types

| Type | Usage |
| --- | --- |
| `feat` | Nouvelle fonctionnalité |
| `fix` | Correction d'anomalie |
| `test` | Ajout ou modification de tests |
| `refactor` | Réécriture sans changement de comportement |
| `docs` | Documentation, Javadoc, README |
| `chore` | Configuration, dépendances, outillage |
| `perf` | Amélioration de performance |
| `ci` | Chaîne d'intégration continue |

### Portées

Les sept modules métier — `identite`, `catalogue`, `reservation`, `vente`, `atelier`,
`communication`, `annexes` — plus les portées transverses `securite`, `bd`, `i18n`, `config`.

### Exemple

    feat(identite): ajouter l'inscription d'un membre avec vérification par courriel

    Le jeton de vérification expire après vingt-quatre heures.

    Refs F1

À proscrire : `maj`, `wip`, `divers`, `ça marche`. Un message doit permettre de comprendre le
contenu du commit sans ouvrir le diff.

## Étiquettes de version

| Étiquette | Contenu |
| --- | --- |
| `v0.1.0` | Socle technique |
| `v0.2.0` | Comptes et authentification |
| `v0.3.0` | Catalogue et espace membre |
| `v0.4.0` | Panier, commande, paiement, facture |
| `v0.5.0` | Rendez-vous et interventions |
| `v0.6.0` | Back-office |
| `v0.7.0` | Conformité RGPD et communication |
| `v1.0.0` | Version complète déployée |

## Règle sur les tests

Aucune méthode de la couche service n'est poussée sans test unitaire.

- `*Test.java` : tests unitaires, sans base de données, exécutés par `mvnw test`
- `*IT.java` : tests d'intégration sur PostgreSQL via Testcontainers, exécutés par `mvnw verify`

## Avant chaque commit

1. `mvnw test` passe
2. Aucun `System.out.println` ni code mort
3. Aucune chaîne destinée à l'utilisateur en dur : tout passe par `i18n/messages*.properties`
4. Aucun secret dans le code : tout par variable d'environnement