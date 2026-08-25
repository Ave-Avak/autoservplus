# Déployer AutoServ+

Ce document décrit deux choses : comment répéter un déploiement complet sur le poste
de développement, et comment déployer sur un serveur. Les deux emploient **les mêmes
fichiers** — même `Dockerfile`, même `docker-compose.prod.yml`, même `Caddyfile`. Seule
la variable `DOMAINE` diffère.

C'est délibéré. Une répétition qui s'appuierait sur une configuration approchante ne
prouverait rien de celle qui part en production : elle validerait la répétition.

| Mode | `DOMAINE` | Qui termine le HTTPS |
|---|---|---|
| Répétition locale | `:80` | le tunnel `cloudflared` |
| Serveur | `1-2-3-4.sslip.io` | Caddy, par ACME / Let's Encrypt |

---

## A. Prérequis

- **Docker Engine et le greffon `compose`** — sous Windows, Docker Desktop les fournit
  tous les deux.
- **git**.
- **Un compte Mollie en mode test**, pour éprouver le paiement réel : un jeton
  `test_…`, ou un jeton d'organisation `access_…` accompagné de son identifiant de
  profil `pfl_…`.

Le compte Mollie n'est **pas** indispensable. Sans `MOLLIE_API_KEY`, l'application se
rabat sur une passerelle bouchonnée qui simule le parcours de bout en bout : on peut
payer une commande, obtenir sa facture et demander un remboursement sans compte chez
le prestataire. La page de paiement s'annonce alors comme simulée. Le choix se fait
sur la **présence de la clé**, pas sur le profil actif.

Ni Java ni Maven n'ont à être installés : la compilation a lieu dans l'image.

---

## B. Répétition locale par tunnel Cloudflare

L'objectif est d'obtenir une adresse **publique en HTTPS** pointant vers la pile qui
tourne sur le poste. Cela permet d'éprouver ce qu'aucune URL en `localhost` ne permet :
le retour de paiement depuis Mollie, et surtout **l'appel du webhook**, que Mollie émet
depuis ses propres serveurs et qui ne peut donc pas viser une machine privée.

### 1. Configuration

```bash
cp .env.example .env
```

Puis, dans `.env` :

```
DOMAINE=:80
COOKIE_SECURE=true
DB_PASSWORD=<un mot de passe quelconque, sans caractère dollar>
```

`COOKIE_SECURE=true` alors que Caddy sert en clair n'est pas une erreur : ce que voit
le navigateur, c'est le tunnel, et le tunnel est en HTTPS.

`URL_PUBLIQUE` reste vide pour l'instant — l'adresse n'existe pas encore.

> **Une variable présente mais vide n'est pas une variable absente.** `application.yml`
> écrit ses valeurs par défaut sous la forme `${GARAGE_RUE:Rue de l'Atelier}` : Spring
> ne prend le repli que si la variable est **introuvable**. Or `cp .env.example .env`
> recopie douze lignes `GARAGE_*=` vides, et Docker Compose les transmet comme des
> variables définies à la chaîne vide. Les laisser telles quelles **n'applique pas** les
> valeurs de démonstration — cela vide l'identité du garage sur la facture, les mentions
> légales et la page de contact, sans lever la moindre erreur.
>
> **Pour chaque variable : la renseigner, ou supprimer la ligne.** Ne jamais laisser une
> ligne vide en comptant sur le défaut.
>
> `MOLLIE_PROFILE_ID` mérite la même vigilance, avec une conséquence plus brutale : avec
> un jeton d'accès, un profil vide fait **refuser le démarrage** — ce qui vaut mieux que
> ses deux alternatives, rompre devant un client ou simuler en silence alors qu'un
> identifiant réel a été fourni.

Sur le choix de l'identifiant Mollie lui-même — clé API `test_`/`live_`, jeton
d'organisation `access_`, ou jeton d'accès avancé, et lequel des trois oblige à
renseigner `MOLLIE_PROFILE_ID` — le commentaire de `.env.example` fait référence.

### 2. Démarrage et chargement des données

```bash
docker compose -f docker-compose.prod.yml up -d postgres
docker compose -f docker-compose.prod.yml exec -T postgres \
    psql -U autoservplus -d autoservplus < docs/dump_autoservplus.sql
docker compose -f docker-compose.prod.yml up -d --build
```

L'ordre compte : le dump porte le schéma **et** l'historique Flyway. Le charger avant
le premier démarrage évite que Flyway applique les migrations sur une base vide pour
les retrouver ensuite en double.

La première construction dure plusieurs minutes — elle télécharge les dépendances
Maven. Les suivantes réemploient la couche mise en cache tant que `pom.xml` ne change
pas.

> **Un avertissement Flyway au démarrage est normal et attendu :**
>
> ```
> Schema "public" has a version (900) that is newer than the latest available migration (35) !
> ```
>
> Le dump a été pris sur une base où le profil `demo` était actif, donc avec la
> migration **V900** du jeu de démonstration appliquée. Hors de ce profil, elle n'est
> pas sur le chemin de Flyway, qui la classe parmi les migrations « futures », l'ignore
> et démarre — comportement vérifié, pas supposé.
>
> **C'est pour cela que la graine porte le numéro 900** et non le suivant de la série.
> Flyway ne tolère une migration appliquée mais non résolue que si son numéro dépasse
> toutes les migrations connues ; en dessous, elle devient « missing » et fait échouer
> le démarrage. Numérotée 34, la graine a cessé d'être tolérée dès l'arrivée de V35 :
> le dump restauré ne démarrait plus. À 900, elle reste hors d'atteinte quel que soit
> le nombre de migrations ajoutées ensuite.
>
> L'avertissement ne doit donc désigner **que** V900. S'il en nomme une autre, c'est
> qu'une migration de schéma manque réellement au dépôt.

Vérification :

```bash
curl -s http://localhost/actuator/health     # {"status":"UP"}
docker compose -f docker-compose.prod.yml ps # trois services, app et postgres « healthy »
```

### 3. Ouverture du tunnel

Installation, une seule fois :

```powershell
winget install --id Cloudflare.cloudflared
```

Puis, dans un terminal **qui doit rester ouvert** :

```bash
cloudflared tunnel --url http://localhost:80
```

`cloudflared` affiche une adresse de la forme
`https://mot-mot-mot-mot.trycloudflare.com`. Aucun compte Cloudflare n'est requis.

> **Cette adresse change à chaque relance du tunnel.** C'est la principale gêne de ce
> mode : toute interruption impose de reprendre l'étape suivante.

### 4. Report de l'adresse

Dans `.env` :

```
URL_PUBLIQUE=https://mot-mot-mot-mot.trycloudflare.com
```

Puis recharger **le seul conteneur applicatif** — ni la base ni le proxy n'ont à
redémarrer :

```bash
docker compose -f docker-compose.prod.yml up -d app
```

Cette variable ne sert pas à l'affichage. Elle construit l'adresse de retour de
paiement et celle du webhook (`URL_PUBLIQUE/webhooks/paiement`). Une valeur périmée
laisse le paiement aboutir **chez Mollie** sans que la commande soit jamais mise à jour
ici : le membre paie et sa commande reste en attente.

### 5. Parcours à éprouver

Depuis l'adresse `trycloudflare.com`, dans un navigateur :

1. **Page d'accueil et page de contact** — l'identité et les horaires proviennent
   respectivement des variables `GARAGE_*` et de la table `plage_ouverture`.
2. **Connexion** avec un compte du jeu de démonstration :

   | Rôle | Adresse | Mot de passe |
   |---|---|---|
   | Administrateur | `admin@autoservplus.be` | `ChangezMoi2026!` |
   | Membre | `marie.dupont@demo.test` | `DemoMembre2026!` |

3. **Commande et paiement** — ajouter une pièce au panier, valider, payer.
4. **Retour sur le site** après paiement, puis **téléchargement de la facture**.
5. **Retour du membre et réception du webhook** :

   ```bash
   docker compose -f docker-compose.prod.yml logs -f app
   ```

Le retour du membre et le webhook empruntent le même chemin idempotent : le statut est
relu chez Mollie, jamais déduit de ce que la requête affirme. Voir les deux arriver
tour à tour, sans double facture, est précisément ce que cette répétition sert à
montrer — et c'est **deux lignes de journal** qui le montrent, une par déclencheur :

```
Notification du prestataire pour le paiement tr_XXXXXXXX : statut relu = REUSSI, commande passee PAYEE, facture emise.
Retour du membre pour la commande CMD-2026-0002 : statut relu chez le prestataire = REUSSI, deja traite, aucune ecriture.
```

L'ordre des deux dépend de qui arrive le premier — Mollie notifie parfois avant que le
navigateur n'ait fini de revenir. **Ce qui compte est ailleurs** : une seule des deux
porte « facture emise », l'autre dit « deja traite, aucune ecriture ». C'est cela,
l'idempotence, rendue lisible : le second passage relit le même statut et n'écrit rien.
Deux lignes portant toutes deux « facture emise » signaleraient une double émission.

Deux autres fins possibles pour la ligne de retour :

- `statut relu chez le prestataire = aucun, aucune tentative n a quitte le site` — le
  membre est revenu sur une commande dont le paiement n'a jamais été initié ;
- rien du tout, précédé d'un `WARN Prestataire de paiement indisponible pour la
  commande …` — Mollie n'a pas pu être relu. C'est la ligne à chercher quand le
  paiement échoue : elle porte le code HTTP renvoyé par le prestataire.

> **Si aucune ligne « Notification du prestataire » n'apparaît**, Mollie n'a pas appelé
> le webhook. La cause la plus fréquente est une `URL_PUBLIQUE` périmée — l'adresse du
> tunnel change à chaque relance. Le parcours aboutit malgré tout : c'est le retour du
> membre qui réconcilie, et sa ligne porte alors « facture emise » puisqu'il est le
> premier à constater. Voir « Limites connues ».

**Reprendre un paiement sans refaire le panier.** Un paiement qui échoue ou qu'on
abandonne **ne détruit pas la commande** : le panier a été converti, la commande existe,
elle reste `EN_ATTENTE_PAIEMENT` et repayable pendant le délai RM-21 de **30 minutes**,
après quoi le job d'expiration l'annule. Le bouton « Procéder au paiement » se trouve
sur **la page de confirmation de cette commande** :

```
https://<adresse-du-tunnel>/commande/<reference>/confirmation
```

C'est ce qui a permis de relancer le parcours après le blocage CSP, sans reconstituer le
panier — la référence figure dans l'historique du navigateur, qui est le chemin le plus
court pour y revenir.

> **« Mes commandes » ne renvoie pas vers cette page.** `/commandes` et
> `/commandes/{référence}` proposent le détail, la facture, la note de crédit et la
> demande d'annulation, mais aucun lien de reprise de paiement. Une commande impayée s'y
> voit donc sans pouvoir s'y payer : il faut passer par l'historique ou retaper l'adresse
> ci-dessus. Dette d'ergonomie inscrite au registre, hors périmètre de ce lot.

### 6. Arrêt

```bash
docker compose -f docker-compose.prod.yml down     # conserve les données
docker compose -f docker-compose.prod.yml down -v  # détruit aussi les volumes
```

> Le nom du projet est **fixé** dans `docker-compose.prod.yml` (`autoservplus-prod`) et
> non déduit du répertoire courant. Sans cela, un `down -v` lancé depuis un autre
> répertoire viserait un projet fantôme, ne détruirait rien, et l'on croirait repartir
> d'une base neuve alors qu'elle porte l'état précédent.

---

## C. Serveur Hetzner CX22

### 1. Création

Dans la console Hetzner Cloud :

- **Image** : Ubuntu 24.04
- **Type** : CX22 (2 vCPU, 4 Go, 40 Go)
- **Localisation** : Falkenstein ou Nuremberg — l'Allemagne est dans l'Union, ce que le
  registre des traitements affirme des sous-traitants d'hébergement.
- **Clé SSH** : en déposer une, et ne pas retenir l'authentification par mot de passe.
- **Pare-feu** : n'ouvrir en entrée que **22, 80 et 443**.

Le pare-feu Hetzner agit en amont de la machine. C'est lui qui rend inoffensif l'oubli
le plus courant, celui d'un port de base de données resté ouvert — ici, le compose n'en
publie de toute façon aucun.

### 2. Première connexion et compte de travail

```bash
ssh root@<ip>

adduser autoserv
usermod -aG sudo autoserv
rsync --archive --chown=autoserv:autoserv ~/.ssh /home/autoserv/
```

Travailler en `root` fonctionnerait. Un compte dédié évite qu'une commande distraite
s'exécute avec tous les droits, et laisse une trace de qui a agi.

### 3. Docker

Depuis le dépôt officiel, et non depuis celui d'Ubuntu, dont la version est plus
ancienne et ne fournit pas le greffon `compose` :

```bash
sudo apt update && sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
     -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io \
                    docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker autoserv
```

Se déconnecter puis se reconnecter pour que l'appartenance au groupe prenne effet.

> Appartenir au groupe `docker` équivaut à disposer des droits `root` : le démon
> s'exécute avec eux. Ce n'est pas une élévation déguisée, c'est un fait à connaître
> avant d'y ajouter quelqu'un.

### 4. Dépôt et configuration

```bash
git clone https://github.com/Ave-Avak/autoservplus.git
cd autoservplus
cp .env.example .env
nano .env
```

Pour une machine d'adresse `203.0.113.7` :

```
DOMAINE=203-0-113-7.sslip.io
URL_PUBLIQUE=https://203-0-113-7.sslip.io
COOKIE_SECURE=true
DB_PASSWORD=<mot de passe long, propre à cette machine>
```

`sslip.io` résout un nom en l'adresse IP qu'il contient. Il n'y a donc **aucun
enregistrement DNS à créer** pour obtenir un certificat Let's Encrypt valable, ce qui
permet de servir en HTTPS avant d'avoir acheté un domaine. Le jour où un vrai domaine
existera, il suffira de faire pointer son enregistrement `A` vers la machine et de
changer ces deux lignes.

Renseigner également les variables `GARAGE_*` : elles impriment l'identité légale de
l'émetteur sur les factures. Les valeurs livrées sont des valeurs de démonstration —
une facture portant un faux numéro de TVA n'est pas une facture.

### 5. Démarrage

Même séquence qu'en local :

```bash
docker compose -f docker-compose.prod.yml up -d postgres
docker compose -f docker-compose.prod.yml exec -T postgres \
    psql -U autoservplus -d autoservplus < docs/dump_autoservplus.sql
docker compose -f docker-compose.prod.yml up -d --build
```

Charger le dump reste un choix de **démonstration**. Pour une mise en service réelle,
sauter cette étape : Flyway crée alors le schéma et ses données de référence, sans
aucun compte membre ni mot de passe publié.

> La construction compile l'application **sur le serveur**. Sur un CX22, comptez
> plusieurs minutes, l'essentiel étant le téléchargement des dépendances Maven. C'est
> le prix d'un déploiement sans registre d'images ; il n'est payé qu'une fois, les
> constructions suivantes réemployant la couche de dépendances.

### 6. Vérification

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs caddy | grep -i certificate
curl -I https://203-0-113-7.sslip.io
```

L'obtention du certificat prend quelques secondes après le premier démarrage. Tant
qu'elle n'a pas abouti, le site répond en erreur TLS — ce n'est pas un échec, c'est une
attente. Si elle se prolonge, la cause est presque toujours l'une des deux suivantes :
le port 80 n'est pas joignable depuis l'extérieur, alors qu'ACME s'en sert pour la
vérification, ou `DOMAINE` ne résout pas vers cette machine.

**Liste de contrôle avant de considérer le déploiement fait :**

- [ ] `docker compose -f docker-compose.prod.yml ps` : trois services, `app` et
      `postgres` marqués `healthy`.
- [ ] `curl -I https://<domaine>` : réponse `200`, certificat accepté sans avertissement.
- [ ] `http://<domaine>` redirige vers `https://`.
- [ ] La page de contact affiche l'identité et les horaires attendus.
- [ ] Connexion avec un compte, puis téléchargement d'une facture.
- [ ] En-tête `Strict-Transport-Security` présent dans la réponse.
- [ ] Le paiement aboutit et la commande passe à « payée » — donc le webhook a été reçu.
- [ ] `.env` n'est pas versionné : `git status` ne le mentionne pas.
- [ ] Le profil `demo` n'est **pas** actif si la mise en service est réelle.

---

## D. Exploitation

### Mettre à jour

```bash
cd ~/autoservplus
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

L'application est reconstruite et redémarrée ; la base et le proxy ne bougent pas. Les
volumes survivent. L'interruption dure le temps du démarrage — une trentaine de
secondes — et l'arrêt est **propre** : l'ordre d'arrêt atteint bien la machine virtuelle
Java, qui laisse ses requêtes en cours se terminer et referme proprement son pool de
connexions.

### Sauvegarder

La base **et** l'archive des factures sont à sauvegarder. La seconde est une obligation
légale de conservation de dix ans (Code TVA art. 60, tel que modifié par la loi du
20 novembre 2022) : elle ne se reconstruit pas.

```bash
sudo mkdir -p /var/backups/autoservplus
sudo chown autoserv:autoserv /var/backups/autoservplus
```

`/home/autoserv/sauvegarde.sh` :

```bash
#!/bin/bash
set -euo pipefail

DEST=/var/backups/autoservplus
HORODATAGE=$(date +%Y%m%d-%H%M%S)
cd /home/autoserv/autoservplus

# --clean --if-exists : le fichier se restaure sur une base non vide sans intervention.
docker compose -f docker-compose.prod.yml exec -T postgres \
    pg_dump -U autoservplus --clean --if-exists autoservplus \
    | gzip > "$DEST/base-$HORODATAGE.sql.gz"

docker run --rm \
    -v autoservplus-prod_factures:/data:ro \
    -v "$DEST":/sauvegarde \
    alpine tar czf "/sauvegarde/factures-$HORODATAGE.tar.gz" -C /data .

# Rétention de quatorze jours. Le disque du CX22 fait 40 Go ; sans purge, il se
# remplit sans bruit et c'est PostgreSQL qui s'arrête en premier.
find "$DEST" -name '*.gz' -mtime +14 -delete
```

```bash
chmod +x /home/autoserv/sauvegarde.sh
crontab -e
```

```
30 3 * * * /home/autoserv/sauvegarde.sh >> /var/log/autoserv-sauvegarde.log 2>&1
```

**Restaurer :**

```bash
gunzip -c /var/backups/autoservplus/base-20260825-033000.sql.gz \
  | docker compose -f docker-compose.prod.yml exec -T postgres \
        psql -U autoservplus -d autoservplus
```

> Une sauvegarde dont la restauration n'a jamais été essayée n'est pas une sauvegarde.
> L'essayer une fois, sur la répétition locale plutôt que sur le serveur.

### Où vivent les données

Rien de ce qui compte n'est dans le répertoire de déploiement. Tout est dans des
volumes nommés, qui survivent à `down`, à `git pull` et à la reconstruction de l'image :

| Volume | Contenu | Perte si supprimé |
|---|---|---|
| `autoservplus-prod_pgdata` | base de données | tout le métier |
| `autoservplus-prod_factures` | factures et notes de crédit PDF | archive légale de dix ans |
| `autoservplus-prod_uploads` | photographies déposées | galerie |
| `autoservplus-prod_caddy_data` | certificat TLS et sa clé | nouvelle émission, dans la limite des quotas Let's Encrypt |

Seul `docker compose down -v` les détruit — jamais `down` seul.

### Journaux

```bash
docker compose -f docker-compose.prod.yml logs -f app
docker compose -f docker-compose.prod.yml logs --since 1h caddy
```

Docker ne purge rien par défaut : sur un disque de 40 Go, les journaux d'un service qui
tourne des mois finissent par le remplir. Poser une limite dans
`/etc/docker/daemon.json` :

```json
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
```

```bash
sudo systemctl restart docker
```

La limite ne vaut que pour les conteneurs **créés ensuite** : recréer la pile après ce
changement.

---

## E. Limites connues

Elles sont énumérées ici pour être opposables, non pour être découvertes en
démonstration.

- **Aucun courriel ne part.** `CourrielConsole` est la seule implémentation du service
  d'envoi : elle journalise le message et annonce qu'il n'est pas expédié. Vérification
  d'adresse, réinitialisation de mot de passe et confirmations de rendez-vous restent
  donc dans les journaux du conteneur. Le branchement de Brevo est la seule intégration
  externe encore à faire.

- **Mollie fonctionne en mode test.** `MOLLIE_MODE_TEST=true` : aucun mouvement d'argent
  réel. Clé absente, une passerelle bouchonnée prend le relais et la chaîne marchande
  reste parcourable de bout en bout.

- **Aucun webhook de remboursement.** Un remboursement Mollie naît `pending` et n'est
  `refunded` qu'après exécution bancaire. Le contrat retenu est synchrone : le
  remboursement est considéré comme acquis dès que le prestataire l'accepte.

- **Aucune sauvegarde hors site.** Les archives décrites plus haut vivent sur le disque
  de la machine qu'elles sauvegardent. Un serveur perdu les emporte. Les recopier
  ailleurs — Hetzner Storage Box, ou tout autre stockage distant — est le complément
  qui manque, et il manque volontairement : il suppose des identifiants à gérer.

- **Aucun instantané de machine.** Hetzner en propose ; aucun n'est programmé.

- **`sslip.io` est provisoire.** Il rend le HTTPS possible sans domaine, mais il fait
  dépendre le site d'un service tiers pour la résolution, et l'adresse contient l'IP —
  donc changer de machine change l'adresse publique. À remplacer par un domaine.

- **Le contenu légal n'a pas été relu par un juriste.** Les trois pages le signalent par
  une bannière. Des conditions générales mal rédigées sont inopposables, ce qui est pire
  qu'une absence de conditions générales.

- **Mono-tenant.** Une pile sert un garage. Deux garages demandent deux déploiements.

- **Aucune supervision.** Ni métrique, ni alerte, ni identifiant de corrélation dans les
  journaux. Une panne se constate en consultant le site.
