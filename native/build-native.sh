#!/usr/bin/env bash
# Builds libsimplex.so and libsupport.so (arm64-v8a) from the pinned SimpleX core.
# Runs on Linux x86_64 with Nix (flakes enabled). It does NOT run on macOS: use CI.
#
#   native/build-native.sh            -> native/out/arm64-v8a/{libsimplex.so,libsupport.so} + SHA256SUMS
#
# Targets are the ones SimpleX's own scripts/android/build-android.sh uses at tag v7.0.2.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
core="$root/third_party/simplex-chat"
out="$root/native/out/arm64-v8a"

# Pinned by commit, not `git describe --tags`: a CI checkout of the submodule is shallow and
# does not fetch tag refs, so `describe` fails there even though the right commit is checked out.
want_commit="4df04bdb3ff94059734ad2da7d2766de6dba2cc7"  # tag v7.0.2
have_commit="$(git -C "$core" rev-parse HEAD)"
if [ "$have_commit" != "$want_commit" ]; then
  echo "third_party/simplex-chat is at '$have_commit', expected '$want_commit' (v7.0.2)" >&2; exit 1
fi

mkdir -p "$out"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

for lib in simplex-chat support; do
  nix build --out-link "$tmp/$lib" \
    "${core}#hydraJobs.x86_64-linux.aarch64-android:lib:${lib}"
done

# Each output holds a zip named pkg-aarch64-android-lib{simplex,support}.zip
unzip -oj "$tmp/simplex-chat/pkg-aarch64-android-libsimplex.zip" -d "$out"
unzip -oj "$tmp/support/pkg-aarch64-android-libsupport.zip" -d "$out"

( cd "$out" && sha256sum libsimplex.so libsupport.so | tee SHA256SUMS )
