#!/usr/bin/env bash
# =====================================================================================
# Regenere docs/dump_autoservplus.sql : base vierge, migrations de schema ET graine de
# demonstration, puis pg_dump. Le gel du code se resume a une commande :
#
#     MFA_CLE_MAITRE='<cle du poste>' docs/regenerer-dump.sh --detruire-la-base-locale
#
# A lancer depuis Git Bash ou un shell POSIX, Docker demarre.
#
# CE QUE LE SCRIPT DETRUIT : le volume de la base locale (docker compose down -v). Le
# drapeau est exige pour cette raison, et non par formalite.
#
# Pourquoi passer par l application et non par psql : la graine db/demo et les
# migrations Java eventuelles ne s appliquent que par Flyway, au demarrage, sous le
# profil demo, exactement comme le documente l en-tete du dump.
#
# Le secret TOTP de l admin de demonstration est retire AVANT le dump : chiffre sous la
# cle de CE poste, il serait illisible sur toute autre base. Sous le profil demo,
# SecondFacteurDemonstration le repose. Sans ce profil, l enrolement est demande.
#
# L en-tete du dump actuel est conserve, ses mentions datees recalculees (versions,
# nombre de tables) et le paragraphe d avertissement « produit AVANT » retire.
# =====================================================================================
set -euo pipefail

if [[ "${1:-}" != "--detruire-la-base-locale" ]]; then
    echo "Ce script DETRUIT la base locale (docker compose down -v)." >&2
    echo "Relancer avec : $0 --detruire-la-base-locale" >&2
    exit 2
fi
if [[ -z "${MFA_CLE_MAITRE:-}" ]]; then
    echo "MFA_CLE_MAITRE requise : l application refuse de demarrer sans elle." >&2
    exit 2
fi

cd "$(git rev-parse --show-toplevel)"
DUMP=docs/dump_autoservplus.sql
PORT=${PORT_REGENERATION:-8089}
TRAVAIL=$(mktemp -d)
BASE=(docker exec autoservplus-db psql -U autoservplus -d autoservplus -v ON_ERROR_STOP=1 -tAc)

# 1. Base vierge. -p autoservplus : depuis un worktree, docker compose deduirait du
#    repertoire un projet fantome, et down -v ne detruirait rien.
docker compose -p autoservplus down -v
docker compose -p autoservplus up -d
until docker exec autoservplus-db pg_isready -U autoservplus -d autoservplus >/dev/null 2>&1; do
    sleep 1
done

# 2. Migrations et graine : l application les applique au demarrage, sous demo.
./mvnw -q -DskipTests package
JAR=$(ls target/autoservplus-*.jar | grep -v plain | head -n 1)
java -jar "$JAR" --spring.profiles.active=demo --server.port="$PORT" \
    > "$TRAVAIL/application.log" 2>&1 &
APPLICATION=$!
trap 'kill "$APPLICATION" 2>/dev/null || true; rm -rf "$TRAVAIL"' EXIT

PRETE=non
for _ in $(seq 1 120); do
    if curl -fsS "http://localhost:$PORT/actuator/health" >/dev/null 2>&1; then
        PRETE=oui
        break
    fi
    sleep 2
done
if [[ "$PRETE" != oui ]]; then
    echo "L application n a pas demarre ; journal : $TRAVAIL/application.log" >&2
    tail -n 40 "$TRAVAIL/application.log" >&2
    exit 1
fi
kill "$APPLICATION"
wait "$APPLICATION" 2>/dev/null || true

# 3. Aucun secret TOTP dans un fichier publie : voir l en-tete de ce script.
"${BASE[@]}" "UPDATE utilisateur SET mfa_secret_chiffre = NULL, mfa_cle_version = NULL,
              mfa_active_le = NULL, mfa_dernier_pas = NULL"

# 4. Mentions datees de l en-tete, lues dans la base qui vient d etre produite.
SCHEMA=$("${BASE[@]}" "SELECT max(version::int) FROM flyway_schema_history
                       WHERE version::int < 900")
GRAINE=$("${BASE[@]}" "SELECT max(version::int) FROM flyway_schema_history")
TABLES=$("${BASE[@]}" "SELECT count(*) FROM information_schema.tables
                       WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                         AND table_name <> 'flyway_schema_history'")

# L en-tete s arrete avant le bloc « PostgreSQL database dump » de pg_dump. Les trois
# dernieres lignes retenues sont la ligne vide et le « -- » qui le precedent.
sed -n '1,/^-- PostgreSQL database dump$/p' "$DUMP" | head -n -3 \
    | sed -E \
        -e "s/migrations de schema V1 a V[0-9]+ ont ete/migrations de schema V1 a V$SCHEMA ont ete/" \
        -e "s/graine de demonstration (?V900[^,]*,/graine de demonstration (V900 a V$GRAINE),/" \
        -e "s/^-- [0-9]+ tables metier/-- $TABLES tables metier/" \
        -e "s/has a version \([0-9]+\)/has a version ($GRAINE)/" \
        -e "s/migration \([0-9]+\) !/migration ($SCHEMA) !/" \
        -e "s/une autre que [0-9]+,/une autre que $GRAINE,/" \
    | sed '/^-- ATTENTION : ce dump a ete produit AVANT/,/au gel du code\.$/d' \
    > "$TRAVAIL/entete.sql"

docker exec autoservplus-db pg_dump -U autoservplus -d autoservplus \
    --clean --if-exists --no-owner > "$TRAVAIL/corps.sql"
{ cat "$TRAVAIL/entete.sql"; printf '\n'; cat "$TRAVAIL/corps.sql"; } > "$DUMP"

echo "Dump regenere : schema V1..V$SCHEMA, graine jusqu a V$GRAINE, $TABLES tables."
echo "Relire le diff de l en-tete avant de committer : git diff -- $DUMP | head -n 80"
