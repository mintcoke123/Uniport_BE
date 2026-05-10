#!/usr/bin/env bash
set -euo pipefail

RENDER_MANIFEST="${EDUCATION_RENDER_MANIFEST:-src/main/resources/education/kmp_render_manifest.json}"
UPLOAD_MANIFEST="${EDUCATION_ASSET_UPLOAD_MANIFEST:-src/main/resources/education/backend_asset_upload_manifest.json}"
CHECK_REMOTE_URLS="${CHECK_REMOTE_URLS:-0}"
ASSET_BASE_URL="${EDUCATION_ASSET_PUBLIC_BASE_URL:-${UNIPORT_EDU_ASSET_BASE_URL:-}}"
SOURCE_ROOT="${EDUCATION_ASSET_SOURCE_ROOT:-}"

command -v jq >/dev/null || {
  echo "jq is required." >&2
  exit 2
}

if [[ ! -f "$RENDER_MANIFEST" ]]; then
  echo "Render manifest not found: $RENDER_MANIFEST" >&2
  exit 2
fi

if [[ ! -f "$UPLOAD_MANIFEST" ]]; then
  echo "Upload manifest not found: $UPLOAD_MANIFEST" >&2
  exit 2
fi

cards_count="$(jq -r '.cards | length' "$RENDER_MANIFEST")"
raster_count="$(jq -r '[.cards[] | select(.renderer_type == "raster_asset")] | length' "$RENDER_MANIFEST")"
component_count="$(jq -r '[.cards[] | select(.renderer_type == "component")] | length' "$RENDER_MANIFEST")"
none_count="$(jq -r '[.cards[] | select(.renderer_type == "none")] | length' "$RENDER_MANIFEST")"
upload_count="$(jq -r '.assets | length' "$UPLOAD_MANIFEST")"
missing_upload_count="$(jq -r '.missing_assets | length' "$UPLOAD_MANIFEST")"

[[ "$cards_count" == "608" ]] || { echo "Expected 608 cards, got $cards_count" >&2; exit 1; }
[[ "$raster_count" == "127" ]] || { echo "Expected 127 raster_asset cards, got $raster_count" >&2; exit 1; }
[[ "$component_count" == "474" ]] || { echo "Expected 474 component cards, got $component_count" >&2; exit 1; }
[[ "$none_count" == "7" ]] || { echo "Expected 7 none cards, got $none_count" >&2; exit 1; }
[[ "$upload_count" == "127" ]] || { echo "Expected 127 upload assets, got $upload_count" >&2; exit 1; }
[[ "$missing_upload_count" == "0" ]] || { echo "Expected 0 missing upload assets, got $missing_upload_count" >&2; exit 1; }

if [[ -n "$SOURCE_ROOT" ]]; then
  missing_local_assets=0
  while IFS= read -r local_path; do
    if [[ ! -f "$SOURCE_ROOT/$local_path" ]]; then
      echo "Missing local upload asset: $SOURCE_ROOT/$local_path" >&2
      missing_local_assets=$((missing_local_assets + 1))
    fi
  done < <(jq -r '.assets[] | .local_source_path' "$UPLOAD_MANIFEST")

  [[ "$missing_local_assets" == "0" ]] || {
    echo "Expected all upload assets to exist locally, got $missing_local_assets missing." >&2
    exit 1
  }
fi

jq -e '
  def card($idx): .cards[] | select(.idx == $idx);
  (card(20).renderer_type == "raster_asset" and (card(20).image_url | type == "string") and (card(20).image_url | length > 0)) and
  (card(21).renderer_type == "raster_asset" and (card(21).image_url | type == "string") and (card(21).image_url | length > 0)) and
  (card(22).renderer_type == "component" and card(22).component_key == "template_comparison") and
  (card(23).renderer_type == "component" and card(23).component_key == "template_checklist") and
  (card(24).renderer_type == "raster_asset" and (card(24).image_url | type == "string") and (card(24).image_url | length > 0)) and
  (card(25).renderer_type == "component" and card(25).component_key == "template_flow") and
  (card(26).renderer_type == "raster_asset" and (card(26).image_url | type == "string") and (card(26).image_url | length > 0)) and
  (card(27).renderer_type == "component" and card(27).component_key == "template_diagram") and
  (card(28).renderer_type == "component" and card(28).component_key == "template_flow") and
  (card(29).renderer_type == "component" and card(29).component_key == "template_comparison") and
  (card(30).renderer_type == "component" and card(30).component_key == "template_flow")
' "$RENDER_MANIFEST" >/dev/null

jq -e '
  all(.cards[];
    if .renderer_type == "raster_asset" then
      (.asset_key | type == "string") and (.image_delivery == "remote_url") and (.image_url | type == "string") and (.visual_payload == null) and (.component_key == null)
    elif .renderer_type == "component" then
      (.component_key | type == "string") and (.image_delivery == "none") and (.image_url == null) and (.asset_key == null) and (.visual_payload != null)
    elif .renderer_type == "none" then
      (.component_key == null) and (.image_url == null) and (.visual_payload == null)
    else
      false
    end
  )
' "$RENDER_MANIFEST" >/dev/null

if [[ "$CHECK_REMOTE_URLS" == "1" ]]; then
  command -v curl >/dev/null || {
    echo "curl is required for remote URL checks." >&2
    exit 2
  }

  while IFS= read -r url; do
    if [[ -n "$ASSET_BASE_URL" ]]; then
      path="/${url#*://*/education-assets/}"
      url="${ASSET_BASE_URL%/}$path"
    fi
    curl -fsSIL "$url" >/dev/null
  done < <(jq -r '.cards[] | select(.renderer_type == "raster_asset") | .image_url' "$RENDER_MANIFEST")
fi

echo "Education render contract verified: cards=$cards_count raster_asset=$raster_count component=$component_count none=$none_count upload_assets=$upload_count."
