#!/usr/bin/env bash
set -euo pipefail

MANIFEST_PATH="${EDUCATION_ASSET_UPLOAD_MANIFEST:-src/main/resources/education/backend_asset_upload_manifest.json}"
SOURCE_ROOT="${EDUCATION_ASSET_SOURCE_ROOT:-}"
BUCKET="${EDUCATION_ASSET_BUCKET:-}"
ENDPOINT_URL="${EDUCATION_ASSET_ENDPOINT_URL:-}"
PROFILE="${EDUCATION_ASSET_PROFILE:-}"
CACHE_CONTROL="${EDUCATION_ASSET_CACHE_CONTROL:-public, max-age=31536000, immutable}"
ACL="${EDUCATION_ASSET_ACL:-}"
DRY_RUN="${EDUCATION_ASSET_DRY_RUN:-0}"

if [[ -z "$SOURCE_ROOT" ]]; then
  echo "EDUCATION_ASSET_SOURCE_ROOT is required. Point it to KMP_DEV_HANDOFF_LIGHT_20260509." >&2
  exit 2
fi

if [[ -z "$BUCKET" ]]; then
  echo "EDUCATION_ASSET_BUCKET is required." >&2
  exit 2
fi

if [[ ! -f "$MANIFEST_PATH" ]]; then
  echo "Manifest not found: $MANIFEST_PATH" >&2
  exit 2
fi

command -v jq >/dev/null || {
  echo "jq is required." >&2
  exit 2
}

if [[ "$DRY_RUN" != "1" ]]; then
  command -v aws >/dev/null || {
    echo "aws CLI is required for S3/R2 compatible upload." >&2
    exit 2
  }
fi

expected_count="$(jq -r '.total_assets' "$MANIFEST_PATH")"
actual_count="$(jq -r '.assets | length' "$MANIFEST_PATH")"
if [[ "$expected_count" != "$actual_count" ]]; then
  echo "Asset count mismatch: total_assets=$expected_count assets.length=$actual_count" >&2
  exit 1
fi

aws_args=()
if [[ -n "$ENDPOINT_URL" ]]; then
  aws_args+=(--endpoint-url "$ENDPOINT_URL")
fi
if [[ -n "$PROFILE" ]]; then
  aws_args+=(--profile "$PROFILE")
fi

uploaded=0
missing=0

while IFS=$'\t' read -r local_path public_path content_type; do
  source_path="$SOURCE_ROOT/$local_path"
  target_key="${public_path#/}"
  target_uri="s3://$BUCKET/$target_key"

  if [[ ! -f "$source_path" ]]; then
    echo "Missing asset: $source_path" >&2
    missing=$((missing + 1))
    continue
  fi

  cmd=(aws)
  if [[ "${#aws_args[@]}" -gt 0 ]]; then
    cmd+=("${aws_args[@]}")
  fi
  cmd+=(s3 cp "$source_path" "$target_uri" --content-type "$content_type" --cache-control "$CACHE_CONTROL")
  if [[ -n "$ACL" ]]; then
    cmd+=(--acl "$ACL")
  fi

  if [[ "$DRY_RUN" == "1" ]]; then
    printf '[dry-run] '
    printf '%q ' "${cmd[@]}"
    printf '\n'
  else
    "${cmd[@]}"
  fi
  uploaded=$((uploaded + 1))
done < <(jq -r '.assets[] | [.local_source_path, .target_public_path, .content_type] | @tsv' "$MANIFEST_PATH")

if [[ "$missing" -gt 0 ]]; then
  echo "Upload failed: $missing assets were missing." >&2
  exit 1
fi

echo "Education asset upload complete: $uploaded assets processed."
