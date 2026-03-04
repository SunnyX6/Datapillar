#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

fail() {
  echo "[FAIL] $1" >&2
  exit 1
}

check_no_match() {
  local description="$1"
  local pattern="$2"
  shift 2
  local paths=("$@")

  if rg -n --hidden -i "$pattern" "${paths[@]}" >/tmp/decoupling-check.log 2>&1; then
    echo "[FAIL] $description" >&2
    cat /tmp/decoupling-check.log >&2
    rm -f /tmp/decoupling-check.log
    exit 1
  fi
  rm -f /tmp/decoupling-check.log
  echo "[OK] $description"
}

check_match() {
  local description="$1"
  local pattern="$2"
  shift 2
  local paths=("$@")

  if ! rg -n --hidden -i "$pattern" "${paths[@]}" >/tmp/decoupling-check.log 2>&1; then
    echo "[FAIL] $description" >&2
    cat /tmp/decoupling-check.log >&2
    rm -f /tmp/decoupling-check.log
    exit 1
  fi
  rm -f /tmp/decoupling-check.log
  echo "[OK] $description"
}

echo "== 1/5 naming scan =="
check_no_match \
  "core/server-common/authorizations/openlineage-listener must not contain datapillar naming" \
  "datapillar" \
  "$ROOT_DIR/core/src/main/java" \
  "$ROOT_DIR/core/src/test/java" \
  "$ROOT_DIR/server-common/src/main/java" \
  "$ROOT_DIR/server-common/src/test/java" \
  "$ROOT_DIR/authorizations/authorization-ranger/src/main/java" \
  "$ROOT_DIR/authorizations/authorization-ranger/src/test/java" \
  "$ROOT_DIR/openlineage-listener/src/main/java" \
  "$ROOT_DIR/openlineage-listener/src/test/java"

echo "== 2/5 dependency direction scan =="
if rg -n "org\\.apache\\.gravitino\\.extensions\\." \
  "$ROOT_DIR/core/src/main/java/org/apache/gravitino" \
  --glob '!**/org/apache/gravitino/extensions/**' >/tmp/decoupling-check.log 2>&1; then
  echo "[FAIL] core mainline path must not directly depend on org.apache.gravitino.extensions.*" >&2
  cat /tmp/decoupling-check.log >&2
  rm -f /tmp/decoupling-check.log
  exit 1
fi
rm -f /tmp/decoupling-check.log
echo "[OK] core mainline dependency direction is correct"

if rg -n "org\\.apache\\.gravitino\\.extensions\\." \
  "$ROOT_DIR/server-common/src/main/java/org/apache/gravitino" \
  --glob '!**/org/apache/gravitino/extensions/**' >/tmp/decoupling-check.log 2>&1; then
  echo "[FAIL] server-common mainline path must not directly depend on org.apache.gravitino.extensions.*" >&2
  cat /tmp/decoupling-check.log >&2
  rm -f /tmp/decoupling-check.log
  exit 1
fi
rm -f /tmp/decoupling-check.log
echo "[OK] server-common mainline dependency direction is correct"

echo "== 3/5 default config runtime chain scan =="
DEFAULT_CONFIG_FILES=(
  "$ROOT_DIR/conf/gravitino.conf"
  "$ROOT_DIR/conf/gravitino.conf.template"
  "$ROOT_DIR/distribution/package/conf/gravitino.conf"
  "$ROOT_DIR/dev/charts/gravitino/values.yaml"
  "$ROOT_DIR/dev/charts/gravitino/resources/config/gravitino.conf"
)
check_match \
  "default config files must include TenantScopedEntityCache" \
  "TenantScopedEntityCache" \
  "${DEFAULT_CONFIG_FILES[@]}"
check_match \
  "default config files must include TenantAwareAuthorizer" \
  "TenantAwareAuthorizer" \
  "${DEFAULT_CONFIG_FILES[@]}"
check_match \
  "default config files must include sqlRewrite enabled" \
  "gravitino\\.extensions\\.multitenancy\\.sqlRewrite\\.enabled\\s*=\\s*true" \
  "${DEFAULT_CONFIG_FILES[@]}"
check_no_match \
  "default config files must not contain gravitino.onemeta.*" \
  "gravitino\\.onemeta\\." \
  "${DEFAULT_CONFIG_FILES[@]}"

echo "== 4/5 config merge verification =="
RUNTIME_BASE="/tmp/gravitino-conf-check-base-$$"
RUNTIME_OVERLAY="/tmp/gravitino-conf-check-overlay-$$"
bash "$ROOT_DIR/scripts/merge-overlay-conf.sh" \
  "$ROOT_DIR/conf" \
  "$ROOT_DIR/conf/not-exists.overlay.conf" \
  "$RUNTIME_BASE"
echo "[OK] baseline merge verification passed"

bash "$ROOT_DIR/scripts/merge-overlay-conf.sh" \
  "$ROOT_DIR/conf" \
  "$ROOT_DIR/conf/datapillar/gravitino.overlay.conf" \
  "$RUNTIME_OVERLAY"
if ! rg -n "gravitino\\.extensions\\.multitenancy\\.sqlRewrite\\.enabled\\s*=\\s*true" \
  "$RUNTIME_OVERLAY/gravitino.conf" >/dev/null 2>&1; then
  fail "overlay merged config is missing extension enable switch"
fi
echo "[OK] overlay merge verification passed"
rm -rf "$RUNTIME_BASE" "$RUNTIME_OVERLAY"

echo "== 5/5 mapper/provider consistency test =="
cd "$ROOT_DIR"
./gradlew :core:test --tests org.apache.gravitino.storage.relational.mapper.TestMapperProviderConsistency

echo "[OK] decoupling checks passed"
