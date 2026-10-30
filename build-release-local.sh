#!/usr/bin/env bash
set -euo pipefail

ANDROID_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ANDROID_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  printf 'Missing %s; copy .env.example to .env and fill in the GitHub build variables.\n' "$ENV_FILE" >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

VERSION_NAME="${VERSION_NAME:-v0.0.1}"
KEYSTORE_PATH="${KEYSTORE_PATH:-$HOME/.android/release.jks}"
if [[ "$KEYSTORE_PATH" != /* ]]; then
  KEYSTORE_PATH="$ANDROID_DIR/$KEYSTORE_PATH"
fi

if [[ -n "${KEYSTORE_BASE64:-}" ]]; then
  mkdir -p "$(dirname -- "$KEYSTORE_PATH")"
  (umask 077; printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$KEYSTORE_PATH")
fi

if [[ -n "${GOOGLE_SERVICES_BASE64:-}" ]]; then
  (umask 077; printf '%s' "$GOOGLE_SERVICES_BASE64" | base64 --decode > "$ANDROID_DIR/app/google-services.json")
fi

for required_var in KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  if [[ -z "${!required_var:-}" ]]; then
    printf 'Set %s in %s.\n' "$required_var" "$ENV_FILE" >&2
    exit 1
  fi
done

for required_file in "$KEYSTORE_PATH" "$ANDROID_DIR/app/google-services.json"; do
  if [[ ! -f "$required_file" ]]; then
    printf 'Missing required build file: %s\n' "$required_file" >&2
    exit 1
  fi
done

export VERSION_NAME KEYSTORE_PATH KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD
cd "$ANDROID_DIR"
./gradlew assembleRelease
