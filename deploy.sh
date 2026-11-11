#!/usr/bin/env bash
# Updates the application on the server (VPS): pulls the latest code from git, rebuilds the image,
# waits until Spring Boot has started and checks that the page answers over HTTPS.
#
# Usage - on the server, in the repository directory:
#   bash deploy.sh
#
# The database, uploaded photos and certificates live in Docker volumes, so they are not touched.
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE=(docker compose -f docker-compose.prod.yml)
START_TIMEOUT=180   # how many seconds to wait for the application to start

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m%s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m%s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m%s\033[0m\n' "$*" >&2; exit 1; }

[[ -f .env ]] || fail "Brak pliku .env - skopiuj .env.production.example jako .env i uzupełnij go."

# ---------- 1. New code ----------
branch=$(git rev-parse --abbrev-ref HEAD)
[[ "$branch" != "HEAD" ]] || fail "Repozytorium jest ustawione na konkretnym commicie (np. po wycofaniu zmian).
Wróć na gałąź poleceniem: git checkout develop   (albo master) i uruchom skrypt ponownie."

step "Pobieranie kodu z gałęzi $branch"
old_commit=$(git rev-parse HEAD)
git pull --ff-only || fail "git pull się nie powiódł. Czy ktoś zmieniał pliki na serwerze? Sprawdź: git status"
new_commit=$(git rev-parse HEAD)

if [[ "$old_commit" == "$new_commit" ]]; then
  warn "Brak nowych commitów."
else
  git --no-pager log --oneline "$old_commit..$new_commit"
fi

# ---------- 2. Configuration ----------
# Variables that exist in the template but are missing from .env (e.g. added in a new version of the application).
# INITIAL_ADMIN_* are skipped - they may be removed on purpose after the first start.
missing=$(comm -23 \
  <(grep -oE '^[A-Z0-9_]+' .env.production.example | sort -u) \
  <(grep -oE '^[A-Z0-9_]+' .env | sort -u) | grep -v '^INITIAL_ADMIN_' || true)
if [[ -n "$missing" ]]; then
  warn "W .env brakuje zmiennych z .env.production.example:"
  echo "$missing"
  warn "Dopisz je (nano .env), jeśli są wymagane - inaczej docker compose odmówi startu."
fi

# ---------- 3. Build and start ----------
step "Budowanie i uruchamianie kontenerów"
old_container=$("${COMPOSE[@]}" ps -q app 2>/dev/null || true)
"${COMPOSE[@]}" up -d --build

# Caddy reads the Caddyfile only at start - "up" does not notice a change of the file's content
if ! git diff --quiet "$old_commit" "$new_commit" -- Caddyfile; then
  step "Caddyfile się zmienił - restart Caddy"
  "${COMPOSE[@]}" restart caddy
fi

# ---------- 4. Did the application start? ----------
new_container=$("${COMPOSE[@]}" ps -q app)
[[ -n "$new_container" ]] || fail "Kontener aplikacji nie działa. Logi: ${COMPOSE[*]} logs app --tail 50"

if [[ "$new_container" == "$old_container" ]]; then
  ok "Kontener aplikacji się nie zmienił (brak zmian w kodzie) - działa dalej."
else
  step "Czekam na start aplikacji (maks. ${START_TIMEOUT} s)"
  started=false
  for ((waited = 0; waited < START_TIMEOUT; waited += 5)); do
    logs=$(docker logs "$new_container" 2>&1 || true)
    if grep -q "Started AnimalAdoptionApplication" <<<"$logs"; then started=true; break; fi
    if grep -q "APPLICATION FAILED TO START" <<<"$logs"; then break; fi
    sleep 5
  done

  if [[ "$started" != true ]]; then
    docker logs --tail 40 "$new_container" 2>&1 || true
    fail "
Aplikacja nie wystartowała - powyżej ostatnie linie logów.
Powrót do poprzedniej wersji:
  git checkout $old_commit && ${COMPOSE[*]} up -d --build"
  fi
  ok "Aplikacja wystartowała."
fi

# ---------- 5. Is the page reachable from outside? ----------
domain=$(grep -E '^DOMAIN=' .env | cut -d= -f2- | tr -d '[:space:]')
if [[ -n "$domain" ]]; then
  step "Sprawdzanie https://$domain"
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "https://$domain/" || true)
  if [[ "$code" == "200" ]]; then
    ok "Strona odpowiada (HTTP 200)."
  elif [[ "$(curl -sk -o /dev/null -w '%{http_code}' --max-time 15 "https://$domain/" || true)" == "200" ]]; then
    warn "Strona działa, ale certyfikat HTTPS jest nieprawidłowy. Logi: ${COMPOSE[*]} logs caddy --tail 30"
  else
    warn "Strona nie odpowiada poprawnie (kod: ${code:-brak}). Logi: ${COMPOSE[*]} logs caddy app --tail 30"
  fi
fi

# ---------- 6. Cleanup ----------
# Every build leaves the previous application image without a name, and the build cache (compiled classes,
# Maven dependencies) grows with every build - remove what is no longer needed, so they do not fill the disk.
# The cache from the last 3 days stays, so the next build is still fast.
docker image prune -f >/dev/null
docker builder prune -f --filter until=72h >/dev/null

step "Miejsce na dysku"
df -h / | tail -1 | awk '{print "zajęte " $3 " z " $2 " (" $5 "), wolne " $4}'

ok "
Gotowe. Wersja: $(git --no-pager log -1 --format='%h %s')"
