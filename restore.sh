#!/usr/bin/env bash
# Restores the database (and optionally the photos) from a backup made by backup.sh.
#
# Usage - on the server, in the repository directory:
#   bash restore.sh backups/db_2026-11-12_0300.bak.gz                                       # database only
#   bash restore.sh backups/db_2026-11-12_0300.bak.gz backups/uploads_2026-11-12_0300.tar.gz # database + photos
#
# WARNING: the current data is REPLACED by the backup. The application is stopped while restoring.
# The .env file must contain the same CRYPTO_KEY as when the backup was made.
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE=(docker compose -f docker-compose.prod.yml)
DB_NAME=$(grep -E '^DB_NAME=' .env | cut -d= -f2- | tr -d '[:space:]' || true)
DB_NAME=${DB_NAME:-animaladoption}
CONTAINER_DIR=/var/opt/mssql/backup
SQLCMD=(/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b)

db_file=${1:-}
uploads_file=${2:-}

if [[ ! -f "$db_file" ]] || { [[ -n "$uploads_file" ]] && [[ ! -f "$uploads_file" ]]; }; then
  echo "Użycie: bash restore.sh backups/db_<data>.bak.gz [backups/uploads_<data>.tar.gz]"
  echo "Dostępne kopie:"
  ls -1t backups/*.gz 2>/dev/null || echo "  (brak)"
  exit 1
fi

echo "Baza '$DB_NAME' zostanie ZASTĄPIONA kopią: $db_file"
[[ -n "$uploads_file" ]] && echo "Zdjęcia zostaną ZASTĄPIONE kopią: $uploads_file"
read -r -p "Wpisz TAK, aby kontynuować: " answer
[[ "$answer" == "TAK" ]] || { echo "Przerwano."; exit 1; }

echo "==> Zatrzymywanie aplikacji"
"${COMPOSE[@]}" stop app

# ---------- Database ----------
echo "==> Odtwarzanie bazy"
gunzip -c "$db_file" > backups/.restore.bak
"${COMPOSE[@]}" exec -T mssql mkdir -p "$CONTAINER_DIR"
"${COMPOSE[@]}" cp backups/.restore.bak "mssql:$CONTAINER_DIR/restore.bak"
rm -f backups/.restore.bak

# SINGLE_USER WITH ROLLBACK IMMEDIATE closes any open connections (e.g. a DBeaver session through the tunnel),
# because RESTORE needs exclusive access. MULTI_USER is set back even if the restore fails.
restore_ok=true
"${COMPOSE[@]}" exec -T mssql "${SQLCMD[@]}" -Q "
  IF DB_ID('$DB_NAME') IS NOT NULL ALTER DATABASE [$DB_NAME] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
  RESTORE DATABASE [$DB_NAME] FROM DISK = N'$CONTAINER_DIR/restore.bak' WITH REPLACE;" || restore_ok=false
"${COMPOSE[@]}" exec -T mssql "${SQLCMD[@]}" -Q "ALTER DATABASE [$DB_NAME] SET MULTI_USER;" || true
"${COMPOSE[@]}" exec -T mssql rm -f "$CONTAINER_DIR/restore.bak"

if [[ "$restore_ok" != true ]]; then
  echo "Odtwarzanie bazy NIE powiodło się (komunikat powyżej). Aplikacja jest zatrzymana."
  echo "Uruchomienie bez zmian: ${COMPOSE[*]} start app"
  exit 1
fi

# ---------- Photos ----------
if [[ -n "$uploads_file" ]]; then
  echo "==> Odtwarzanie zdjęć"
  # The application is stopped, so a temporary container with the same uploads volume does the work
  "${COMPOSE[@]}" run --rm --no-deps -T --entrypoint sh app \
    -c 'find /app/uploads -mindepth 1 -delete && tar xzf - -C /app' < "$uploads_file"
fi

echo "==> Uruchamianie aplikacji"
"${COMPOSE[@]}" start app
echo "Gotowe. Start aplikacji trwa ok. 30 s - podgląd: ${COMPOSE[*]} logs -f app"
