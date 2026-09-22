# Weft — architecture (Phase 0)

## How the SimpleX core is used
The official SimpleX Android app loads two native libraries built from Haskell:
`libsimplex.so` (the chat core) and `libsupport.so` (Android glue). A small C file (`simplex-api.c`)
starts the Haskell runtime and exposes: `initHS`, `chatMigrateInit(dbPath, dbKey, confirm)`,
`chatSendCmdRetry`, `chatRecvMsg(Wait)`, file encrypt/decrypt helpers and a few parsers.
Everything crosses the boundary as JSON strings. The database key passed to `chatMigrateInit`
is the SQLCipher key — SQLCipher is inside the core.

Weft does the same with its own package name:

```
Compose UI (app/) ──► ChatController (core/) ──JNI──► libweft-jni.so ──► libsimplex.so + libsupport.so
                          │                               (our C glue,        (built from third_party/
                          └─ JSON commands / events        adapted)           simplex-chat @ v7.0.2)
```

- Pinned to tag **v7.0.2** (commit `4df04bdb3ff94059734ad2da7d2766de6dba2cc7`). Updating is a deliberate commit.
- At that tag the Nix outputs are `hydraJobs.x86_64-linux.aarch64-android:lib:simplex-chat` and `...:lib:support`.
- The glue links with `-Wl,-z,max-page-size=16384` (16 KB pages, required on current Android/GrapheneOS).
- Only `arm64-v8a` is built.

## Where things are built
| Piece | Built where | Why |
|---|---|---|
| libsimplex.so, libsupport.so | GitHub Actions, Linux x86_64 + Nix (`native/build-native.sh`) | Haskell cross-compile does not run on macOS |
| libweft-jni.so, Kotlin, APK | Gradle (Mac or CI) | needs Android NDK + CMake |
| Signed APK + SHA-256 | CI | reproducible, published with the release |

Trust check (planned): SimpleX publishes reproducible builds of their own release libraries. Our
`libsimplex.so` should hash-match theirs for the same tag; a mismatch blocks the release.

## Modules
`app/` screens · `design-system/` tokens + components · `core/` wrapper + JNI · `security/` PIN, Keystore,
wipe (Phase 2) · `native/` native build · `ci/` checks (no-Google, hashes).

## Known risks (measured in Phase 0)
1. **Cold start < 1 s**: the Haskell runtime starts with `-A64m -H64m`. Measure on a real Pixel; tune RTS flags if needed.
2. **Native build time**: haskell.nix builds GHC 9.6.3 cross toolchains. `.github/workflows/native.yml` configures
   the IOG binary cache (`cache.iog.io`) so it downloads prebuilt GHC instead of building it — run #2 (2026-09-22)
   spent 2+ hours rebuilding GHC from scratch without this and was cancelled; confirmed the cache config was
   simply missing (`flake.nix` declares no `nixConfig`, and Nix does not apply a flake input's own `nixConfig`
   non-interactively). If a run is still very slow with the cache configured, that's a different problem —
   check the step log for actual cache hits (`copying path ... from 'https://cache.iog.io'`) before assuming
   the cache itself is at fault.
3. **No GrapheneOS device yet** for validation (open question to the owner).
