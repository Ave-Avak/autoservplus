# AutoServ+

Plateforme de gestion pour garage automobile indépendant : prise de rendez-vous en
ligne, suivi des interventions d'atelier, vente de pièces et facturation.

Application web monolithique, mono-tenant, destinée à un seul garage.

## Stack

| Domaine | Technologie |
|---|---|
| Langage | Java 21 (Temurin) |
| Cadre applicatif | Spring Boot 3.5.3 (Web, Data JPA, Security, Validation, Actuator) |
| Base de données | PostgreSQL 16, migrations Flyway |
| Vues | Thymeleaf, HTMX, CSS écrit pour le projet (pas de Bootstrap) |
| Documents PDF | OpenPDF 2.0.3 |
| Internationalisation | FR / NL / EN |
| Tests | JUnit 5, Mockito, Testcontainers |

OpenPDF est employé à la place d'iText : iText 5 et supérieur est sous AGPL, dont les
obligations sont incompatibles avec une distribution propriétaire. OpenPDF dérive
d'iText 4, antérieur à ce changement de licence.

## Prérequis

- **Java 21** (Temurin recommandé)
- **Docker**, pour la base de données et les tests d'intégration
- **PostgreSQL 16**, si vous préférez une instance locale au conteneur

Maven n'a pas à être installé : le dépôt fournit le wrapper (`mvnw`, `mvnw.cmd`).

## Démarrage

```bash
git clone https://github.com/Ave-Avak/autoservplus.git
cd autoservplus
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Sous Windows, employer `mvnw.cmd` et encadrer le paramètre :
`mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"`

L'application écoute sur <http://localhost:8080>.

Le profil `demo` charge un jeu de données couvrant l'ensemble des parcours :
rendez-vous dans leurs six statuts, dossiers d'atelier dans leurs six états, une
commande payée et sa facture, avis, messagerie et notifications.

| Rôle | Adresse | Mot de passe |
|---|---|---|
| Administrateur | `admin@autoservplus.be` | `ChangezMoi2026!` |
| Membre | `marie.dupont@demo.test` | `DemoMembre2026!` |

> **Le profil `demo` ne doit jamais être activé en production.** Il crée des comptes
> dont les mots de passe sont publiés dans ce dépôt, et des données fictives.

Sans ce profil, l'application démarre sur une base ne contenant que les données de
référence (catalogue, postes d'atelier, horaires) et aucun compte membre.

## Restaurer la base de démonstration

Alternative à la commande précédente, sans passer par Maven ni Flyway :

```bash
docker compose up -d
docker exec -i autoservplus-db psql -U autoservplus -d autoservplus < docs/dump_autoservplus.sql
./mvnw spring-boot:run
```

Le dump contient le schéma et les données. L'application démarre ensuite **sans** le
profil `demo`, les migrations étant déjà enregistrées. L'en-tête de
[`docs/dump_autoservplus.sql`](docs/dump_autoservplus.sql) détaille la procédure et
ses limites.

## Déploiement

`docker-compose.yml` ne démarre qu'une base de données : c'est le compose de
**développement**, et il reste tel quel. Le déploiement s'appuie sur des fichiers
distincts — `Dockerfile`, `docker-compose.prod.yml` et `deploy/Caddyfile` — qui
démarrent la base, l'application et un proxy Caddy terminant le HTTPS.

```bash
cp .env.example .env    # puis renseigner DOMAINE, URL_PUBLIQUE, DB_PASSWORD
docker compose -f docker-compose.prod.yml up -d --build
```

Les mêmes fichiers servent à répéter le déploiement sur le poste de développement,
derrière un tunnel Cloudflare, et à déployer sur un serveur : seule la variable
`DOMAINE` change. La procédure complète — chargement du jeu de démonstration, mise en
service sur un serveur Hetzner, sauvegardes, limites connues — figure dans
[`docs/deploiement.md`](docs/deploiement.md).

## Tests

```bash
./mvnw verify
```

Exécute les tests unitaires (Surefire, suffixe `Test`), les tests d'intégration
(Failsafe, suffixe `IT`) et la vérification de couverture.

- Les tests d'intégration démarrent un PostgreSQL 16 via **Testcontainers** : Docker
  doit être disponible et démarré.
- **JaCoCo** échoue sous **60 %** d'instructions couvertes sur l'ensemble du projet.

L'intégration continue (GitHub Actions, `.github/workflows/ci.yml`) exécute
`./mvnw -B compile` puis `./mvnw -B verify` sur Ubuntu avec Java 21, à chaque
poussée et à chaque demande de tirage visant `main`.

## Structure

```
src/main/java/be/autoservplus/
├── identite/        comptes, authentification, vérification d'adresse
├── reservation/     véhicules, disponibilités, rendez-vous, export iCalendar
├── intervention/    dossiers d'atelier, machine à six états, accord sur devis
├── catalogue/       prestations, pièces, stock, back-office
├── vente/           panier, commande, paiement
├── facturation/     factures, notes de crédit, PDF, archivage
├── retractation/    demandes d'annulation et remboursements
├── legal/           documents contractuels et versionnage du texte
├── rgpd/            export des données, suppression de compte, anonymisation
├── cookies/         consentement par finalité
├── vitrine/         pages publiques (contact, horaires)
├── communication/   courriels transactionnels
├── notification/    notifications applicatives
├── messagerie/      fils de discussion membre ↔ garage
├── avis/            avis et modération
├── galerie/         photographies de prestations et d'interventions
├── pilotage/        tableau de bord
├── journal/         journal d'audit
├── comptabilite/    exports comptables
├── importcsv/       import de catalogue
├── api/             API REST publique en lecture seule
├── stockage/        stockage des fichiers déposés
├── i18n/            résolution de la langue
├── common/          types et exceptions partagés
└── config/          sécurité, horloge, propriétés

src/main/resources/db/
├── migration/       schéma et données de référence — appliquées toujours (V1 à V33)
└── demo/            jeu de démonstration — appliqué sous le profil `demo` seulement
```

La séparation entre `migration` et `demo` n'est pas cosmétique : les tests
d'intégration partent d'une base dépourvue de données transactionnelles, et plusieurs
vérifient des états initiaux qu'un jeu de démonstration rendrait faux.

## Configuration

Toutes les valeurs sont surchargeables par variable d'environnement. Les valeurs par
défaut conviennent à un poste de développement et **ne doivent pas être employées en
production**.

| Variable | Défaut | Rôle |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/autoservplus` | Connexion à la base |
| `DB_USER` / `DB_PASSWORD` | `autoservplus` | Identifiants de la base |
| `SERVER_PORT` | `8080` | Port d'écoute |
| `SPRING_PROFILES_ACTIVE` | `dev` | Profil actif |
| `DOMAINE` | — | Lu par Caddy au déploiement. `:80` sert en HTTP derrière un tunnel ; un nom de domaine déclenche l'obtention d'un certificat Let's Encrypt |
| `COOKIE_SECURE` | `false` | Pose l'attribut `Secure` sur le cookie de session. À mettre à `true` dès que le site est joint en HTTPS |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75` | Options de la machine virtuelle Java dans le conteneur |
| `URL_PUBLIQUE` | `http://localhost:8080` | Adresse publique, employée là où une URL absolue est indispensable |
| `FACTURES_ARCHIVE` | `./data/factures` | Répertoire d'archivage des factures (conservation sept ans) |
| `MEDIAS_RACINE` | `./data/uploads` | Répertoire des fichiers déposés |
| `GARAGE_RAISON_SOCIALE`, `GARAGE_TVA`, `GARAGE_BCE`, `GARAGE_IBAN`, … | valeurs de démonstration | Identité légale imprimée sur les factures et les mentions légales |
| `MOLLIE_API_KEY` | *vide* | Clé ou jeton Mollie. **Vide, le paiement passe par une passerelle bouchonnée** |
| `MOLLIE_PROFILE_ID` | *vide* | Identifiant de profil Mollie, requis avec un jeton d'organisation |
| `MOLLIE_MODE_TEST` | `true` | Emploie le mode test de Mollie plutôt que des paiements réels |

Les valeurs `GARAGE_*` livrées sont des valeurs de démonstration explicites : une
facture portant un faux numéro de TVA n'est pas une facture. La liste complète figure
dans [`src/main/resources/application.yml`](src/main/resources/application.yml).

Le prestataire de paiement est choisi sur la **présence d'un identifiant**, non sur le
profil actif : sans `MOLLIE_API_KEY`, une passerelle bouchonnée simule le parcours de
bout en bout, ce qui permet de payer une commande et d'obtenir sa facture sans compte
Mollie. Avec un identifiant, les appels partent chez le prestataire.

Aucun secret réel n'est versionné dans ce dépôt.

## Contribution

Les conventions de commit, de branches et de tests sont décrites dans
[CONTRIBUTING.md](CONTRIBUTING.md).

## Contexte et licence

Projet réalisé dans le cadre d'une épreuve intégrée de bachelier en informatique
(ICC Bruxelles, 2025-2026).

Aucune licence n'est actuellement déclarée : en l'absence de fichier `LICENSE`, le
code reste sous droit d'auteur exclusif de son auteur, et aucun droit de réutilisation
n'est accordé.
