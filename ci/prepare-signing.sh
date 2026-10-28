#!/usr/bin/env bash
# Uses the permanent release key from repository secrets when present. Otherwise creates a
# one-off key for this run so the APK is still installable (updates then need a reinstall).
set -euo pipefail
KS="$RUNNER_TEMP/release.keystore"

if [ -n "${KEYSTORE_BASE64:-}" ]; then
  echo "$KEYSTORE_BASE64" | base64 -d > "$KS"
  MODE=permanent
else
  STORE_PASSWORD="$(openssl rand -hex 24)"
  KEY_PASSWORD="$STORE_PASSWORD"
  KEY_ALIAS=ephemeral
  keytool -genkeypair -noprompt -keystore "$KS" -storetype PKCS12 \
    -storepass "$STORE_PASSWORD" -keypass "$KEY_PASSWORD" -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=Independent Browser (temporary CI key)"
  MODE=ephemeral
  echo "::warning::No SIGNING_KEYSTORE_BASE64 secret - release APK is signed with a temporary key."
fi

echo "::add-mask::$STORE_PASSWORD"
echo "::add-mask::$KEY_PASSWORD"
{
  echo "SIGNING_KEYSTORE_PATH=$KS"
  echo "SIGNING_STORE_PASSWORD=$STORE_PASSWORD"
  echo "SIGNING_KEY_ALIAS=$KEY_ALIAS"
  echo "SIGNING_KEY_PASSWORD=$KEY_PASSWORD"
} >> "$GITHUB_ENV"
echo "mode=$MODE" >> "$GITHUB_OUTPUT"
