#!/bin/bash
# 서버에서 코드 받기, 빌드, 컨테이너 재시작을 한 번에 한다. 사용법: ./deploy.sh [브랜치]
set -euo pipefail

BRANCH="${1:-main}"
COMPOSE="docker compose -f docker-compose.prod.yml"

cd "$(dirname "$0")"

git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"

# 메모리가 작아서 한 번에 하나씩 순서대로 빌드한다.
$COMPOSE build backend
$COMPOSE build frontend

$COMPOSE up -d
$COMPOSE ps
