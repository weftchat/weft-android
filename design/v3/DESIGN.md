# Weft — design v3 handoff (for Claude Code)

`design/v3/weft-v3.html` is the **exact reference** for Weft's look, motion and behaviour.
Open it in a browser (it needs internet for fonts and the 3D background). The left column
("App screens", "Things to try") is a presenter's guide, **not part of the app**. Only what is
inside the phone frame is the app.

Reproduce it in Kotlin + Jetpack Compose with fidelity. Do not "improve" it. If something in
the mockup conflicts with `BRIEF.md` (security rules, permissions, zero Google), the BRIEF wins —
ask the owner before deviating.

This replaces the 7 screens of v2 (now archived in `design/v2/`). v3 keeps the "Vault"
direction and adds: 3D weave onboarding, key-forging sequence, set/confirm PIN, duress decoy
profile, group chat, new group, group call, emergency-wipe screen, bottom sheets, toasts and
a full motion system.

---

## 1. Tokens

Put these in `design-system/` as a single source (e.g. `WeftColors`, `WeftType`, `WeftMotion`).
Weft is **dark only** in v1.

### Colour
| Token | Hex | Use |
|---|---|---|
| bg | `#0A0A12` | Screen background |
| surface | `#12121C` | Cards, incoming bubbles |
| sheet | `#161623` | Bottom sheets |
| toast | `#20202F` | Toasts |
| glass | `#FFFFFF` @ 4 % | Pills, inputs, icon buttons (hover/pressed: 7–8 %) |
| line | `#FFFFFF` @ 8 % | 1 px borders, dividers |
| line2 | `#FFFFFF` @ 14 % | Stronger borders (sheet, toast, dashed notes) |
| text | `#F4F3FF` | Primary text |
| muted | `#A3A1BE` | Secondary text |
| faint | `#8886A8` | Timestamps, hints, mono captions |
| accent | `#A78BFA` | Active states, icons, switch ON track |
| accent2 | `#C4B5FD` | End of the accent gradient, links |
| gradient | `135°, accent → accent2` | Primary buttons, own bubbles, badges, FAB |
| onAccent | `#0B0B14` | Text/icons on the gradient (never white) |
| safe | `#5CF0B8` | **Only** encrypted / verified / "always on" / live relay |
| danger | `#FF6B81` | **Only** wipe / delete / blocked relay / leave call |
| warn | `#F7C86A` | Relay switching (pulsing dot) |
| switch OFF track | `#2C2B3E` | |
| empty dot / checkbox border | `#3A3950` | |

Tinted fills: safe @ 10–14 %, danger @ 8–12 %, accent @ 10–13 %.
Avatars: `hsl(h, 32 %, 19 %)` fill with `hsl(h, 80 %, 84 %)` initials. Hues in the mockup's `HUE` / `SWATCH` tables.

### Type (bundle the fonts — no downloadable fonts)
- **Manrope** 500/600/700/800 for all UI.
- **JetBrains Mono** 400/500/600 for keys, fingerprints, relay names, times, counters and UPPERCASE labels.
- Use tabular numbers for times, counters and fingerprints.

| Style | Size / weight / tracking |
|---|---|
| Display (onboarding) | 50 sp / 800 / −0.045 em / line-height 0.96 |
| Title XL | 30 sp / 800 / −0.03 em |
| Screen title | 30 sp / 800 / −0.03 em (New group: 22 sp) |
| PIN title | 26 sp / 800 / −0.02 em |
| Row name | 15 sp / 800 / −0.01 em |
| Body | 14–14.5 sp / 500–600 / line-height 1.45–1.6 |
| Secondary | 12.5–13 sp muted |
| Eyebrow | Mono 11 sp / 500 / +0.16 em / UPPERCASE / accent |
| Section header | Mono 11 sp / 500 / +0.14 em / UPPERCASE / faint |
| Mono caption | Mono 10.5–12 sp faint |

### Shape and spacing
- Pills (buttons, chips, inputs in composer): fully rounded.
- Cards 22 dp · list rows 18 dp · inputs 16 dp · avatars 16 dp (40 dp avatar → 14 dp) · sheet 34 dp · QR card 26 dp · call tiles 24 dp.
- Bubbles 20 dp with the tail corner at 6 dp (bottom-left for incoming, bottom-right for own).
- Screen side padding 20–24 dp. Top bars start at 56 dp from the top (respect real insets instead of hard-coding).
- Primary button height 56 dp · icon buttons 44 dp · PIN keys 78 dp · call controls 60 dp · switch 50×30 dp.
- **Touch targets ≥ 44 dp everywhere.**

---

## 2. Motion system (the important part)

Principles: fast, ease-out, interruptible, never from scale 0, only transform + opacity.
Respect the system's "Remove animations" setting: when the animator duration scale is 0, swap
every movement for an instant change or a short fade.

| Name | Curve | Compose |
|---|---|---|
| `EaseNav` | cubic-bezier(0.32, 0.72, 0, 1) | `CubicBezierEasing(0.32f, 0.72f, 0f, 1f)` — screen push/pop, sheets, tab bar in/out |
| `EaseOut` | cubic-bezier(0.23, 1, 0.32, 1) | `CubicBezierEasing(0.23f, 1f, 0.32f, 1f)` — everything else |

| Interaction | Spec |
|---|---|
| Push screen | New screen slides in from the right, 100 % → 0, **460 ms EaseNav**. Old screen moves to −28 % and dims to 50 % brightness. Pop = exact reverse. System back and predictive back must drive the same animation. |
| Tab switch / root change | Fade + scale 0.985 → 1, 280 ms EaseOut. Old screen fades out in ~200 ms. |
| Tab bar | Floating, 12 dp from the sides, 14 dp from the bottom, radius 26, translucent (`#14141F` @ 88 %) with a blur where available. Hides by sliding down (140 %) when leaving a tab screen, 420 ms EaseNav. The selected-tab highlight **slides** between tabs, 380 ms EaseOut. |
| Segmented filter (All / Direct / Groups / Timed) | Pill indicator slides, 340 ms EaseOut. List re-staggers. |
| List stagger (chat list, call tiles) | Each item fades in and rises 10 dp, 420 ms EaseOut, 30 ms apart (call tiles: scale 0.94 → 1, 40 ms apart). Only when the screen opens or the filter changes, not on every recomposition. |
| Button press | Scale 0.96 (full-width rows 0.985), 160 ms EaseOut. PIN keys: scale 0.93 and accent @ 24 % fill, 60 ms down. |
| Switch | Knob slides 20 dp, 280 ms EaseOut. **While pressed, the knob stretches** from 24 to 29 dp wide (towards the centre). |
| Checkbox / radio | Fill 200 ms; tick scales in from 0.6, 260 ms EaseOut. Radio dot scales 0 → 1, 240 ms. |
| PIN dot filled | Pops 0.6 → 1.15 → 1, 260 ms. Wrong PIN: dots turn danger and the row shakes (−10, +9, −6, +4, 0 dp over 420 ms), then clears. |
| New message (sent or received) | From translateY 10 dp + scale 0.96 + alpha 0, 340 ms EaseOut, origin at the tail corner. List scrolls smoothly to bottom. Own message shows ✓ (sent) then ✓✓ (delivered). |
| Typing indicator | 3 dots, 1.1 s loop, staggered 150 ms. |
| Bottom sheet | Scrim fades to black @ 62 % in 300 ms. Sheet slides up from 115 %, 480 ms EaseNav; closes in 300 ms. **Drag to dismiss** from the handle: follows the finger; dragging up resists (square-root). Dismiss if moved > 90 dp or released faster than 0.5 dp/ms; otherwise springs back in 360 ms EaseOut. Tap the scrim or press back to close. |
| Toast | Bottom-centre, above the tab bar when it is visible (above the composer in chats). In: rise 10 dp + scale 0.96 → 1, 320 ms EaseOut. Stays 2.3 s. Out: 200 ms. One at a time; a new one replaces the old. |
| Relay rotate | Dot turns warn and pulses (800 ms), text "switching relay…", then the new relay text fades up. |
| Onboarding steps | Old step fades out and rises 8 dp (180 ms); the new step's children rise 12 dp and fade in, 420 ms EaseOut, 45 ms apart. |
| Key forging | Fingerprint groups scramble random hex and settle left to right over ~1.9 s. Three steps turn spinner → green tick one after another (~640 ms each). |
| Hold to wipe | Red fill wipes left → right over **2000 ms linear** while held (use a clip, so the label turns dark-on-red as the fill passes). Releasing early rewinds 4× faster. Completion: content blurs 8 dp, scales to 0.97 and fades (520 ms), then the "This phone is clean" view rises in. Works with a long press and with keyboard/accessibility actions. |
| Call "speaking" | Speaking tile's avatar gets a 3 dp ring in its hue plus a soft glow and scales to 1.04, 220 ms EaseOut. Real audio level drives it in the app (the mockup fakes it). |

### The 3D weave (onboarding background)
The mockup uses three.js. **In the app, do not use a WebView or three.js.** Draw it with Compose
`Canvas` (or an AGSL shader on Android 13+, with a Canvas fallback), keeping the maths:

- 24 vertical threads (accent → `#E4DCFF`) and 24 horizontal threads (safe, 62 % brightness), each 160 points, over a square of ±7.2 units, drawn additively. Fade to transparent at the ends and edges (`sin(π·u)·sin(π·across)`, power 1.2).
- Over/under weave: `z = 0.26·sin(π·along/cell + i·π + (dir ? π : 0))`.
- Slow swell: `+ 0.6·sin(0.33x + 0.55t)·cos(0.29y − 0.42t) + 0.25·sin(0.5(x+y) + 0.8t)`.
- Camera: perspective FOV 36°, at (0, 0.4, 14.5). Cloth rotated −1.05 rad on X, 0.5 rad on Z.
- A glowing "shuttle" dot runs along one horizontal thread per 2.6 s, alternating direction.
- **Knot** (while keys are being made, k → 1; afterwards k → 0.35): pull points towards the centre `r·(1 − 0.62·k·e^(−r²/38))`, twist `+1.9·k·e^(−r/5.5)`, add a centre glow and a slow spin. Ease k with factor 2.6/s.
- Top 540 dp of the screen, faded into the background by a gradient from 260 dp down.
- Pause when the screen is not visible. With "Remove animations" on, draw one still frame.
- Budget: must not hurt the < 1 s cold start. Start the animation after the first frame.

---

## 3. Screens and behaviour

Every string is English, exactly as in the mockup, and goes in `strings.xml`.
The chats, names and messages in the mockup are **sample data** for the pilot demo only — the
real app shows real data from the SimpleX core.

1. **Create identity** — weave, eyebrow "No phone number · no email", display "Your keys. / Your words.", 3 green-tick benefits, "Create identity". → Forging step (real key generation happens here; the animation lasts at least as long as the work). → "What should your contacts call you?": fingerprint card ("local only"), optional nickname (max 24), "Choose a PIN".
2. **PIN** — three modes: *Choose a PIN* → *Repeat your PIN* (mismatch: shake + toast "PINs didn't match — try again") → *Enter your PIN*. Numeric keypad in-app (never the system keyboard). A hardware keyboard also works. Refuse the duress PIN as the real PIN.
   - Real PIN → real profile. **Duress PIN → decoy profile, with no visual difference at all** (same screen, same animation, same timing). No toast, no hint.
   - If "Wipe after 10 wrong PINs" is on, the subtitle shows "wrong PIN 10× wipes this phone · n/10".
3. **Chats** — title, padlock (locks → PIN), gradient "+" (opens the "Start something new" sheet: New contact / New group). Relay pill: `relay r2 · via Tor · 38 ms` + "Rotate". Segmented filter. Rows: avatar (groups get a small people badge), name + green shield if verified, time (accent when unread), last message, timer chip, unread badge. Tab bar: Chats · Add · Security · Profile.
4. **Conversation (1:1)** — back, avatar, name, "E2E · verified in person" (safe) or "E2E · not verified yet" (muted), timer pill → "Disappearing messages" sheet (Off / 5 min / 1 h / 24 h / 1 week, counted from read, on both phones). Dashed system note at the top. Photo bubble shows "metadata stripped · GPS · device · time removed". Composer: camera button, field with a green padlock and "Encrypted message…", gradient send button.
5. **Group chat** — like 1:1, sender name coloured by hue on the first bubble of each run, header "N members · E2E", phone button → group call. System note "group keys rotate when someone leaves".
6. **Group call** — minimize (chevron down), group name, "E2E encrypted · mm:ss" (safe), 2-column grid of up to 6 tiles, controls Mute / Camera / Speaker / Leave (danger). Pressed toggles invert (light fill, dark icon). Leaving shows toast "Call ended · mm:ss". (BRIEF: WebRTC mesh ≤ 6, Phase 4.)
7. **New group** — name field, "Add people you've met" checklist (only existing contacts), note about nicknames, sticky "Create group · N people" button (disabled until a name and ≥ 1 member).
8. **Add contact** — one-time QR on a white card with an accent glow, live countdown "expires in mm:ss" (new code at 0 or with the refresh button, toast "New one-time code · the old one no longer works"), "Scan their code", "Copy one-time link".
9. **Security** — sections "If someone takes your phone" (Duress PIN + masked PIN row with "Change", Emergency wipe, Wipe after 10 wrong PINs), "Privacy" (E2E and Strip photo metadata as "always on" chips, Block screenshots, Default disappearing timer → sheet), "Relays" (each relay: in use / standby / blocked, "Switch when blocked" switch, "Add your own relay"), and "Wipe everything now" (danger) → Emergency wipe. Every toggle shows a toast.
10. **Emergency wipe** — red-tinted top, what gets erased (keys and fingerprint · chats, photos, files · contacts and groups · relay list and settings), "Hold to wipe" (2 s). After: "This phone is clean" + "Start over" → onboarding. No way back.
11. **Profile** — big avatar (initial of nickname) + 6 colour swatches, nickname field, fingerprint card (Show as QR → sheet, Copy), "This phone" list (Linked devices, Change PIN, Security and duress), "Delete identity from this phone" → Emergency wipe.

### Mockup-only things (do not ship)
- The left-hand guide column and the phone frame.
- Sample chats, auto-replies, fake "speaking" in calls, the fake QR pattern (use a real QR of the real invitation link).
- Copy-link text `weft://invite#…` is a placeholder: use the real SimpleX invitation link.

### Security rules that override the mockup
- FLAG_SECURE as defined in `BRIEF.md`.

### Proposed, not yet decided (ask the owner before building)
- Incognito keyboard (no personalised learning) on every text field.
- Clipboard copies marked sensitive and cleared after 60 s.
- Notifications that never show sender or text.
- Reacting to GrapheneOS's own duress PIN and to panic-button apps.

---

## 4. Build order (fits the BRIEF phases)
- **Now (end of Phase 0 / start of Phase 1):** `design-system/` tokens, fonts, motion constants and base components (buttons, pill, switch, segmented, row, bubble, sheet, toast, tab bar, top bars), plus navigation with the push/pop/fade transitions. Then screens 1–4 and 8 wired to the core.
- Phase 2: screens 2 (duress), 9, 10 and the Profile actions.
- Phase 3: relay pill and relay list with real data.
- Phase 4: screens 5, 6, 7.

Each step: build, install, and compare side by side with the mockup at 390×844 dp. Fix differences before moving on.
