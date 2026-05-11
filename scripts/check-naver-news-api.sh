#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

if [[ -z "${NAVER_NEWS_CLIENT_ID:-}" || -z "${NAVER_NEWS_CLIENT_SECRET:-}" ]]; then
  echo "NAVER_NEWS_CLIENT_ID and NAVER_NEWS_CLIENT_SECRET are required."
  echo "Copy .env.example to .env and fill the Naver developer app credentials."
  exit 1
fi

curl -fsS \
  -H "X-Naver-Client-Id: $NAVER_NEWS_CLIENT_ID" \
  -H "X-Naver-Client-Secret: $NAVER_NEWS_CLIENT_SECRET" \
  "https://openapi.naver.com/v1/search/news.json?query=%EC%BD%94%EC%8A%A4%ED%94%BC%20%EC%8B%9C%ED%99%A9&display=3&start=1&sort=date"
