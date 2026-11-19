#!/usr/bin/env bash
# Rolls the services CI rebuilt on the deploy host. The deploy job pipes it over ssh:
#   ssh host "DEPLOY_DIR=... bash -s -- <tag> <registry/namespace> <service>..." < ci/deploy.sh
# Same steps as the old hand deploy: dump the DB, set TAG, compose up, log. Never prints .env.
set -euo pipefail
umask 027

TAG=$1 PREFIX=$2
shift 2
BUILT=("$@")
: "${DEPLOY_DIR:?}"
DB_CONTAINER=${DB_CONTAINER:-postgres}
DB_USER=${DB_USER:-infernokun}
DB_NAME=${DB_NAME:-amaterasu}
LAB_WAIT_SECONDS=${LAB_WAIT_SECONDS:-900}
SKIP_LAB_CHECK=${SKIP_LAB_CHECK:-false}
APP_SERVICES=(amaterasu-rest amaterasu-web amaterasu-proxmox-mock)

[[ $TAG =~ ^[0-9a-f]{7,40}$ ]] || { echo "bad tag '$TAG'"; exit 1; }
cd "$DEPLOY_DIR"
{ [ -r .env ] && [ -w .env ]; } || { echo "$DEPLOY_DIR/.env must be readable and writable by $(id -un)"; exit 1; }
[ "$(grep -c '^TAG=' .env)" = 1 ] || { echo ".env needs exactly one TAG= line"; exit 1; }

# Shell TAG beats .env, so every call before the switch resolves the new images
compose() { TAG=$TAG docker compose --profile mock "$@"; }
defined=$(compose config --services)
has() { grep -qxF "$1" <<<"$defined"; }
in_list() { local x=$1; shift; for y in "$@"; do [ "$x" = "$y" ] && return 0; done; return 1; }

ROLL=()
for s in "${BUILT[@]}"; do
  if has "$s"; then ROLL+=("$s")
  elif [ "$s" = amaterasu-proxmox-mock ]; then echo "$s is not in the server's compose; built, not deployed"
  else echo "$s is not in $DEPLOY_DIR/docker-compose.yml"; exit 1
  fi
done
[ ${#ROLL[@]} -gt 0 ] || { echo "nothing to roll"; exit 0; }
echo "rolling ${ROLL[*]} to $TAG"

# Fail before touching anything if .env's REGISTRY/NAMESPACE would not run what CI pushed
for s in "${APP_SERVICES[@]}"; do
  has "$s" || continue
  compose config --images "$s" | grep -qxF "$PREFIX/$s:$TAG" \
    || { echo "$s would not run $PREFIX/$s:$TAG; set REGISTRY and NAMESPACE in .env to match"; exit 1; }
done

compose pull --quiet "${ROLL[@]}"

# TAG is shared, so give the services not rebuilt the new tag too: every TAG then exists for
# every service, in the registry, and a later `up` or a rollback finds its images
for s in "${APP_SERVICES[@]}"; do
  if ! has "$s" || in_list "$s" "${ROLL[@]}"; then continue; fi
  cid=$(docker compose ps -q "$s")
  if [ -z "$cid" ]; then
    echo "$s is not running and was not rebuilt; run the pipeline with force_run to build it"
    continue
  fi
  docker tag "$(docker inspect -f '{{.Image}}' "$cid")" "$PREFIX/$s:$TAG"
  docker push --quiet "$PREFIX/$s:$TAG" >/dev/null
  echo "tagged running $s as $TAG"
done

psql() { docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -XAtq -v ON_ERROR_STOP=1 -c "$1"; }

# Lab actions (start, resume, pause, stop, move) run inside one @Transactional call that holds
# a DB transaction while it talks to Proxmox or Docker; a copy whose VMs' guest agents have not
# answered yet is what the app shows as STARTING (EnvironmentStatuses.of)
busy() {
  psql "select 'a transaction open for ' || date_trunc('second', now() - xact_start)
          from pg_stat_activity
         where datname = current_database() and backend_type = 'client backend'
           and pid <> pg_backend_pid() and state <> 'idle' and xact_start < now() - interval '10 seconds'
        union all
        select 'lab tracker ' || t.id || ': VM ' || (vm->>'vmid') || ' is waiting for its guest agent'
          from lab_tracker t
         cross join json_array_elements(coalesce(nullif(t.vms, ''), '[]')::json) vm
         where t.lab_status in ('ACTIVE', 'DEGRADED', 'ACTIVE_FAILED', 'FAILED_ACTIVE')
           and vm->>'status' = 'running' and vm->>'guestAgent' = 'true' and vm->>'ready' = 'false'"
}

if [ "$SKIP_LAB_CHECK" = true ]; then
  echo "SKIP_LAB_CHECK=true: not waiting for starting labs"
elif in_list amaterasu-rest "${ROLL[@]}" || in_list amaterasu-proxmox-mock "${ROLL[@]}"; then
  deadline=$((SECONDS + LAB_WAIT_SECONDS)) clean=0
  # Two clean reads 15 s apart, so an action that began just before the first is old enough to show
  while :; do
    found=$(busy) || { echo "could not ask $DB_NAME whether a lab is starting; not deploying"; exit 1; }
    if [ -z "$found" ]; then
      clean=$((clean + 1))
      [ $clean -ge 2 ] && break
    else
      clean=0
      echo "waiting, a lab is busy: $found"
    fi
    [ $SECONDS -lt $deadline ] || { echo "a lab was still busy after ${LAB_WAIT_SECONDS}s; not deploying"; exit 1; }
    sleep 15
  done
  echo "no lab is starting"
fi

# Rollback past 647d304 needs the dump, so no dump, no deploy
mkdir -p backups
dump=backups/amaterasu-before-$TAG.dump
[ -e "$dump" ] && dump=backups/amaterasu-before-$TAG-$(date -u +%Y%m%dT%H%M%SZ).dump
if ! docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -Fc "$DB_NAME" > "$dump.part" \
  || [ ! -s "$dump.part" ] \
  || ! docker exec -i "$DB_CONTAINER" pg_restore -l < "$dump.part" > /dev/null; then
  rm -f "$dump.part"
  echo "pg_dump of $DB_NAME failed; not deploying"
  exit 1
fi
mv "$dump.part" "$dump"
echo "dump: $DEPLOY_DIR/$dump ($(stat -c %s "$dump") bytes)"

PREV=$(grep '^TAG=' .env | cut -d= -f2)
echo "previous TAG $PREV"
# Rewrite in place so .env keeps its owner and mode
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
sed "s/^TAG=.*/TAG=$TAG/" .env > "$tmp"
cat "$tmp" > .env
grep -qx "TAG=$TAG" .env || { echo "TAG was not set"; exit 1; }

docker compose --profile mock up -d --no-deps "${ROLL[@]}"
echo "$(date -u +%FT%TZ) $PREV -> $TAG (ci pipeline ${CI_PIPELINE_ID:-?}: ${ROLL[*]})" >> deploys.log

status=0
for s in "${ROLL[@]}"; do
  cid=$(docker compose ps -q "$s")
  h=none
  for _ in $(seq 1 60); do
    h=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid")
    [ "$h" = healthy ] || [ "$h" = running ] && break
    sleep 5
  done
  echo "$s: $h"
  [ "$h" = healthy ] || [ "$h" = running ] || status=1
done
if [ $status -ne 0 ]; then
  echo "unhealthy after deploy. Roll back: TAG=$PREV in .env, docker compose up -d --no-deps ${ROLL[*]}, and restore $dump if REST changed the schema"
  exit 1
fi

# Keep this and the previous tag's images locally; older ones stay pullable from the registry
for s in "${APP_SERVICES[@]}"; do
  docker images --format '{{.Repository}}:{{.Tag}}' "$PREFIX/$s" \
    | grep -vxF -e "$PREFIX/$s:$TAG" -e "$PREFIX/$s:$PREV" | xargs -r docker rmi >/dev/null 2>&1 || true
done
