#!/usr/bin/env bash
# Fails if the app depends on Google Play Services, Firebase, Play Billing, ML Kit or Play Core.
# AndroidX is allowed: it is Google-published but does not need Google services on the device.
#
#   ci/check-no-google.sh [variant]     variant defaults to "release"
set -euo pipefail
cd "$(dirname "$0")/.."

variant="${1:-release}"
banned='com\.google\.android\.gms|com\.google\.firebase|com\.android\.billingclient|com\.google\.mlkit|com\.google\.android\.play|com\.google\.android\.datatransport'

fail=0

echo "1) Dependency graph (${variant}RuntimeClasspath)"
deps="$(./gradlew -q --console=plain :app:dependencies --configuration "${variant}RuntimeClasspath")"
if grep -E "$banned" <<<"$deps"; then
  echo "FAIL: banned dependency in the Gradle graph" >&2; fail=1
else
  echo "   ok"
fi

apk="$(ls app/build/outputs/apk/"$variant"/*.apk 2>/dev/null | head -1 || true)"
if [ -n "$apk" ]; then
  echo "2) Compiled APK: $apk"
  tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
  unzip -q -o "$apk" 'classes*.dex' 'AndroidManifest.xml' -d "$tmp" || true
  path_pattern="$(sed 's/\\\././g; s/\./\//g' <<<"$banned")"
  if strings "$tmp"/classes*.dex | grep -E "L(${path_pattern})/" | head -5 | grep .; then
    echo "FAIL: banned classes inside the APK" >&2; fail=1
  else
    echo "   ok"
  fi
else
  echo "2) No APK for '$variant' built yet — skipped (build it first for the full check)"
fi

exit $fail
