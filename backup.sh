#!/usr/bin/env bash
# Backup of the server data: the SQL Server database and the uploaded photos.
#
# Usage - on the server, in the repository directory:
#   bash backup.sh
#
# The files land in backups/ (db_<date>.bak.gz and uploads_<date>.tar.gz). Kept are the newest BACKUP_KEEP
# (default 7) database backups and BACKUP_KEEP_UPLOADS (default 3) photo backups - the photo archive is as big
# as all the photos together, and photos are only added or deleted, so fewer copies are enough.
# If less than BACKUP_MIN_FREE_MB (default 2048) MB is free on the disk, the backup is not made at all.
# Automatically every day at 3:00 - add a line with "crontab -e":
#   0 3 * * * bash /home/azureuser/lapka/backup.sh >> /home/azureuser/lapka/backups/backup.log 2>&1
#
# Restoring: bash restore.sh (see the description in that script).
# NOTE: the backups contain personal data - keep them safe. Without CRYPTO_KEY from .env the encrypted
# columns (phone numbers, chat messages) in a restored database cannot be read.
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE=(docker compose -f docker-compose.prod.yml)
KEEP_DB=${BACKUP_KEEP:-7}
KEEP_UPLOADS=${BACKUP_KEEP_UPLOADS:-3}
MIN_FREE_MB=${BACKUP_MIN_FREE_MB:-2048}
DB_NAME=$(grep -E '^DB_NAME=' .env | cut -d= -f2- | tr -d '[:space:]' || true)
DB_NAME=${DB_NAME:-animaladoption}
STAMP=$(date +%Y-%m-%d_%H%M)
# Folder inside the SQL Server container (in its data volume) where the .bak file is created first
CONTAINER_DIR=/var/opt/mssql/backup

mkdir -p backups
chmod 700 backups   # only the owner of the server account can read the backups

echo "[$(date '+%F %T')] Kopia zapasowa - start"

# A full disk stops SQL Server and the application - better to skip the backup than to cause that
free_mb=$(df -Pm backups | awk 'NR == 2 {print $4}')
if (( free_mb < MIN_FREE_MB )); then
  echo "BŁĄD: na dysku zostało tylko ${free_mb} MB (minimum ${MIN_FREE_MB} MB) - kopia NIE została wykonana." >&2
  echo "Usuń stare kopie z backups/ albo powiększ dysk maszyny." >&2
  exit 1
fi

# ---------- 1. Database ----------
# BACKUP DATABASE writes a full backup to a file inside the container, then we copy it out.
# The Express edition has no built-in backup compression, so the file is compressed with gzip afterwards.
# The password for sqlcmd comes from the SQLCMDPASSWORD variable of the container (docker-compose.prod.yml).
"${COMPOSE[@]}" exec -T mssql mkdir -p "$CONTAINER_DIR"
"${COMPOSE[@]}" exec -T mssql /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -b \
  -Q "BACKUP DATABASE [$DB_NAME] TO DISK = N'$CONTAINER_DIR/$DB_NAME.bak' WITH INIT, CHECKSUM"
"${COMPOSE[@]}" cp "mssql:$CONTAINER_DIR/$DB_NAME.bak" "backups/db_$STAMP.bak"
"${COMPOSE[@]}" exec -T mssql rm -f "$CONTAINER_DIR/$DB_NAME.bak"
gzip -f "backups/db_$STAMP.bak"

# ---------- 2. Uploaded photos ----------
# Packed by the application container, which has the uploads volume mounted at /app/uploads.
# Written to a .part file first, so an interrupted backup never looks like a complete one.
"${COMPOSE[@]}" exec -T app tar czf - -C /app uploads > "backups/uploads_$STAMP.tar.gz.part"
mv "backups/uploads_$STAMP.tar.gz.part" "backups/uploads_$STAMP.tar.gz"

# ---------- 3. Remove old backups ----------
remove_old() {   # $1 = file prefix, $2 = how many newest files to keep
  { ls -1t backups/"$1"_*.gz 2>/dev/null || true; } | tail -n +$(($2 + 1)) | xargs -r rm --
}
remove_old db "$KEEP_DB"
remove_old uploads "$KEEP_UPLOADS"

echo "[$(date '+%F %T')] Gotowe:"
ls -lh backups/*_"$STAMP".*
echo "Wszystkie kopie: $(du -sh backups | cut -f1), wolne miejsce na dysku: $(df -h backups | awk 'NR == 2 {print $4}')"
