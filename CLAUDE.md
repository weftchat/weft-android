# Weft — project memory for Claude Code

Read `BRIEF.md` first: it is the single source of truth. Do not invent what it does not say;
if something is undecided, ask before coding. The 7 screens in `design/*.dc.html` are the exact
reference for layout, spacing, type and colour — reproduce them in Compose, do not "improve" them.

## What this is
Android-native (Kotlin + Jetpack Compose) messenger with total metadata privacy, on the SimpleX
protocol. Target OS: GrapheneOS (no Google Play Services). AGPL-3.0 — the code will be public.

## Decisions taken in Phase 0 (confirmed by the owner)
1. **Own app, SimpleX core as a library** — not a fork of the official app. The Haskell core is
   vendored as a git submodule at `third_party/simplex-chat`, pinned to tag **v7.0.2** (never a beta).
   JNI glue and command/event models are adapted from the official app (AGPL-compatible).
2. **Native libs are built in GitHub Actions** (Linux + Nix), never on the Mac. Repo name/org: pending.
3. **arm64-v8a only.** No armv7.

## Rules
- The owner does not use Terminal or git: run commands and make commits yourself; explain in plain language.
- Do not add a dependency without a one-line justification of what it provides.
- Zero Google: no `com.google.android.gms`, `com.google.firebase`, `com.android.billingclient`,
  `com.google.mlkit`, `com.google.android.play`. Bundle fonts (no downloadable fonts). AndroidX is allowed.
- Never use the SimpleX name or logo in Weft's branding (trademark policy). "Compatible with SimpleX network" is fine.
- Secrets never enter git: signing keystore, relay addresses (`~/Developer/weft-infra/ADDRESSES.txt`, the
  `smp://` one contains a password).
- Each phase ends with an installable APK and a manual test list.

## Layout
`app/` screens · `design-system/` tokens + components · `core/` Kotlin wrapper + JNI over SimpleX ·
`security/` PIN, Keystore, wipe · `native/` reproducible build of libsimplex.so · `ci/` checks · `docs/`.
