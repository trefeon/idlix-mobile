# IDLIX for Android Mobile 📱

[![Release](https://img.shields.io/github/v/release/trefeon/idlix-mobile)](https://github.com/trefeon/idlix-mobile/releases)
[![CI](https://github.com/trefeon/idlix-mobile/actions/workflows/android-ci.yml/badge.svg)](https://github.com/trefeon/idlix-mobile/actions/workflows/android-ci.yml)
[![License](https://img.shields.io/github/license/trefeon/idlix-mobile)](./LICENSE)

A modern, **100% native Android streaming client** for movies and TV series from IDLIX — Netflix-style phone UI, adaptive tablet layouts, gesture player with PiP, and in-app auto-updates. No backend to run, no account needed.

> **New here?** Grab the latest APK from [Releases](https://github.com/trefeon/idlix-mobile/releases), install it, and you're watching.

---

## ✨ Features

| Area | What you get |
|---|---|
| **Phone UI** | Hero billboard with Play / My List, Top-10 rail with oversized numerals, Continue Watching, genre swimlanes, bottom navigation |
| **Tablet UI** | Same codebase, adaptive at 600/840dp breakpoints: wider grids, bigger hero, two-pane master-detail — no separate app |
| **Player** | Inline-to-fullscreen dual mode, double-tap ±10s, vertical-drag brightness/volume, Picture-in-Picture, token-preserving HLS, aspect-aware subtitles (transparent background, outlined white text) |
| **Subtitles** | Built-in IDLIX subtitles + SubDL search and download (bring your own free API key) |
| **Library** | Local Continue Watching and history — fully offline, no account |
| **Auto-update** | Checks GitHub releases, downloads and installs new versions in-app |

**Catalog source:** the app resolves the live IDLIX mirror at runtime from the canonical `https://idlix.net/` (redirect-follow, allowlisted hosts, fail-closed). No self-hosted server, no VPS, nothing to deploy.

---

## 📥 Install

1. Download `idlix-mobile-v*.apk` from the [releases page](https://github.com/trefeon/idlix-mobile/releases).
2. Open the file on your phone/tablet and allow *Install unknown apps* when prompted (Xiaomi/MIUI: enable **Install via USB** in Developer Options for ADB installs).
3. On first launch the app resolves the current mirror automatically.

> ⚠️ **Upgrading from v1.0.0?** Uninstall the old app first — v1.0.0 was unsigned and Android refuses to install the signed v1.0.1+ over it. Local history is cleared on uninstall.

---

## 🛠️ Build from source

**Prerequisites:** JDK 21, Android SDK (API 35), ~4 GB free disk.

```bash
git clone https://github.com/trefeon/idlix-mobile.git
cd idlix-mobile
cp local.properties.sample local.properties   # then fill in your values
./gradlew :app:assembleDebug                   # debug APK
./gradlew :app:assembleRelease                 # release APK (signed if a keystore is configured, else unsigned)
```

### `local.properties` keys

| Key | Required for | Notes |
|---|---|---|
| `sdk.dir` | all builds | Android SDK location (or set `ANDROID_HOME`) |
| `storeFile` / `storePassword` / `keyAlias` / `keyPassword` | signed releases | Without these, `assembleRelease` emits an **unsigned** APK and warns |
| `subdlApiKey` | SubDL subtitles | Personal key from [subdl.com/api-docs](https://subdl.com/api-docs); built-in IDLIX subtitles work without it |

APK outputs: `app/build/outputs/apk/{debug,release}/`. Install via ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the checks before pushing: `./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest`.

---

## 📁 Project structure

```
idlix-mobile/
├── core/data/        # Shared library: HTTP client, idlix.net resolver, models,
│                      # repositories, SubDL client, watch-history store
├── app/              # Mobile app: adaptive UI, gesture player, settings, updater
├── gradle/           # Version catalog (libs.versions.toml) + wrapper
└── .github/workflows/# android-ci.yml (build+test) · android-release.yml (signed APKs + GitHub release)
```

---

## 🤝 Contributing

Issues and pull requests are welcome. Please: keep phone layouts one-hand friendly, keep tablet breakpoints (600/840dp) intact, add unit tests for new logic in `core/data`, and never commit `local.properties`, keystores, or API keys.

---

## ⚖️ License & disclaimer

MIT — see [LICENSE](./LICENSE). This is an **unofficial third-party client**, not affiliated with or endorsed by IDLIX. All content, artwork, and trademarks belong to their respective owners. Use it in compliance with your local laws and your streaming providers' terms.
