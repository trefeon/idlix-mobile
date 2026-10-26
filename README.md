# idlix-mobile

Mobile (phone/tablet) streaming app for IDLIX.

## Source

Canonical catalog source is the official site `https://idlix.net/` (redirect-follow
resolver in `:core:data`: no-redirect HEAD→GET `Location` capture, host allowlist,
cached fallback, fail-closed). No self-hosted backend.

## Setup

Copy `local.properties.sample` to `local.properties` and fill in values:

- `storeFile` / `storePassword` / `keyAlias` / `keyPassword` — release signing
  (optional; absent → unsigned release APK).
- `subdlApiKey` — SubDL subtitle API key (third-party, direct).

## Build

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Install on a device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Releases

Download signed APKs from
[GitHub Releases](https://github.com/trefeon/idlix-mobile/releases).
