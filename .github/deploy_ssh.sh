#!/usr/bin/env bash
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/var/www/Diary-project/tg-chat}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-master}"

cd "$DEPLOY_PATH"

if [[ ! -f docker-compose.yml ]]; then
  echo "docker-compose.yml not found in DEPLOY_PATH=$DEPLOY_PATH" >&2
  exit 2
fi

echo "==> Updating repo in $DEPLOY_PATH (branch: $DEPLOY_BRANCH)"
git fetch --prune origin
git reset --hard "origin/${DEPLOY_BRANCH}"

mkdir -p deploy/apk data

echo "==> Ensuring docker network extra_services exists"
docker network create extra_services 2>/dev/null || true

echo "==> Deploying with docker compose"
docker compose up -d --build

echo "==> Showing status"
docker compose ps
