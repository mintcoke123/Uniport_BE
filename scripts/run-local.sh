#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
else
  echo "[env] .env not found: $ENV_FILE"
  echo "[env] copy .env.example to .env and fill NAVER_NEWS_CLIENT_ID / NAVER_NEWS_CLIENT_SECRET for live news"
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

if [[ -n "${NAVER_NEWS_CLIENT_ID:-}" && -n "${NAVER_NEWS_CLIENT_SECRET:-}" ]]; then
  echo "[news] Naver News API credentials: configured"
else
  echo "[news] Naver News API credentials: missing, /api/news will use DB or fallback news"
fi

cd "$ROOT_DIR"
exec ./gradlew bootRun
