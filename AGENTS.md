# AGENTS.md

Notes for coding agents working on Navee Offline. User-facing docs are in [README.md](README.md), and the threat model is in [SECURITY.md](SECURITY.md).

## Project

- An Android-only companion app for NAVEE e-scooters. It uses Kotlin, Jetpack Compose and Material 3.
- It talks to the scooter directly over BLE.
- The package layout is described in README → Project layout.
- `protocol/` and `pairing/` are pure Kotlin and unit-tested. Keep Android types out of them.

## Hard rules

- **100% offline.** Never add `INTERNET` or any network permission, or a library that needs one. The manifest strips network permissions with `tools:node="remove"`, so keep that.
- **Never lock a moving scooter.** Every lock path checks `speedKmh > 1.0`: the Ride screen, the widget, auto-lock and the notification.
- **Not included on purpose:** speed-limit removal, region changes, firmware patching or flashing. Don't add them.
- **Every user-facing string in both languages:** `res/values/strings.xml` (English) and `res/values-nb/strings.xml` (Norwegian bokmål).
- **Show what the scooter reports, not what the app assumes.** After a write, the app re-reads the parameters (`ScooterConnection.write`).
- **Minimal dependencies.** Prefer platform APIs. For example, the widget uses `RemoteViews` rather than Glance.

## Build

- Requires a JDK with `javac`. On Fedora 44 that is `java-25-openjdk-devel`, because Fedora no longer ships JDK 21.
- If Gradle says the toolchain "does not provide JAVA_COMPILER" after installing it, run `./gradlew --stop` and try again.

```sh
./gradlew testDebugUnitTest   # unit tests (there is no testReleaseUnitTest task)
./gradlew assembleDebug       # dev app: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # minified, unsigned: app/build/outputs/apk/release/app-release-unsigned.apk
```

## Phone: dev app vs released app (adb)

`adb` is not on PATH. Use `~/Android/Sdk/platform-tools/adb` (the SDK path is in `local.properties`).

| Build | applicationId | Name on phone | Signed with |
|---|---|---|---|
| release | `com.xlabsx27.naveeoffline` | Navee Offline | release key |
| debug | `com.xlabsx27.naveeoffline.dev` | Navee Dev | debug key |

Debug builds are a **separate app**, so they install next to the released one and never touch its data.

Before installing, check what is on the phone:

```sh
adb devices
adb shell pm list packages com.xlabsx27.naveeoffline   # lists both the release and .dev if installed
```

- **Always install debug builds** (`adb install -r app/build/outputs/apk/debug/app-debug.apk`). They go to `….dev`.
- **Never** run `adb uninstall com.xlabsx27.naveeoffline`, and never install over the released app. Its trips and scooter pairing would be lost. Ask the user first.
- **The scooter binds to one client id.** A fresh dev app has a new id, and the scooter rejects it ("bound to another phone").
  - To test against the real scooter, the user exports the pairing file from the released app (Settings → Pairing) and imports it in Navee Dev.
- **Only one app can hold the BLE link at a time.** Disconnect the other app, or turn off its auto-reconnect, while testing.
- **Installs from before the `.dev` suffix:** a debug build installed back then sits under `com.xlabsx27.naveeoffline` with the debug key. That blocks installing a release. The user must back up (Settings → Backup and Pairing), uninstall it, then install the release.

## Releases

Publish only when the user asks.

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` on `main`. Commit, tag `vX.Y.Z`, and push.
2. Run `./gradlew assembleRelease testDebugUnitTest`.
3. Sign outside the repo. The key never goes in git: `.gitignore` excludes `*.jks`.
   - Keystore: `~/.android/keystores/navee-offline-release.jks`, alias `navee-offline`.
   - Password: `~/.android/keystores/navee-offline-release.pass`.
   - Certificate SHA-256: `66:17:C0:79:…:2F:FE`. Every release must use this key, or updates won't install.
   ```sh
   bt=$(ls -d ~/Android/Sdk/build-tools/* | sort -V | tail -1)
   out=app/build/outputs/apk/release
   $bt/zipalign -f -p 4 $out/app-release-unsigned.apk $out/aligned.apk
   $bt/apksigner sign --ks ~/.android/keystores/navee-offline-release.jks --ks-key-alias navee-offline \
     --ks-pass file:$HOME/.android/keystores/navee-offline-release.pass \
     --out $out/navee-offline-X.Y.Z.apk $out/aligned.apk
   $bt/apksigner verify --print-certs $out/navee-offline-X.Y.Z.apk
   $bt/aapt2 dump permissions $out/navee-offline-X.Y.Z.apk   # must not list INTERNET
   ```
4. Create the Forgejo release through the API:
   - `POST https://git.xlabsx27.com/api/v1/repos/X27/navee-offline/releases`
   - Token: `~/.config/forgejo-token`, sent as `Authorization: token …`. It has no `read:user` scope, so `/user` returns 403. That is expected.
   - Upload `navee-offline-X.Y.Z.apk` and a `.sha256` file to `…/releases/{id}/assets?name=…`.
   - Put the APK checksum and the certificate fingerprint in the notes.
5. Download the public asset again and compare its SHA-256 with the local file.

## Git

- Remote: `xlab-git:X27/navee-offline.git` (Forgejo, SSH). Default branch: `main`.
- Commit or push only when asked. Branch off `main` for non-trivial work.
