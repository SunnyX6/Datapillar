#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

BASE_CONF_DIR="${1:-$ROOT_DIR/conf}"
OVERLAY_CONF_FILE="${2:-$BASE_CONF_DIR/datapillar/gravitino.overlay.conf}"
OUTPUT_CONF_DIR="${3:-/tmp/gravitino-conf-overlay}"

if [ ! -f "$BASE_CONF_DIR/gravitino.conf" ]; then
  echo "missing base config: $BASE_CONF_DIR/gravitino.conf" >&2
  exit 1
fi

rm -rf "$OUTPUT_CONF_DIR"
mkdir -p "$OUTPUT_CONF_DIR"
if [ -f "$BASE_CONF_DIR/log4j2.properties" ]; then
  cp "$BASE_CONF_DIR/log4j2.properties" "$OUTPUT_CONF_DIR"/
elif [ -f "$BASE_CONF_DIR/log4j2.properties.template" ]; then
  cp "$BASE_CONF_DIR/log4j2.properties.template" "$OUTPUT_CONF_DIR/log4j2.properties"
fi
cp -R "$BASE_CONF_DIR/security" "$OUTPUT_CONF_DIR"/ 2>/dev/null || true

if [ -f "$OVERLAY_CONF_FILE" ]; then
  cat "$BASE_CONF_DIR/gravitino.conf" "$OVERLAY_CONF_FILE" >"$OUTPUT_CONF_DIR/gravitino.conf"
  echo "merged base + overlay into: $OUTPUT_CONF_DIR/gravitino.conf"
else
  cp "$BASE_CONF_DIR/gravitino.conf" "$OUTPUT_CONF_DIR/gravitino.conf"
  echo "overlay missing, copied base config to: $OUTPUT_CONF_DIR/gravitino.conf"
fi
