# Navee Offline

An open-source companion app for NAVEE e-scooters. It is Android only, uses Material You, and works **100% offline**.

The app talks to the scooter directly over Bluetooth LE. It records your rides on an offline OpenStreetMap map and remembers where you parked. It has no account, no cloud and no analytics, and it does not request the `INTERNET` permission, so you can check the offline promise in the manifest yourself.

> Independent community project, not affiliated with NAVEE. No warranty: changing scooter settings is at your own risk.

## Features

Three tabs: **Ride**, **Map** and **Settings**. Norwegian (bokmål) and English, following the phone or picked with 🇳🇴/🇬🇧 in Settings.

**Scooter (Bluetooth LE)**
- Scan and connect. The app remembers the last scooter and reconnects automatically with backoff.
- Ride screen:
  - speed (only while connected) and a battery ring
  - a big Lock/Unlock button, which refuses to lock while the scooter is moving
  - quick toggles: light, cruise, turbo, eco
  - range left, trip and odometer
- Fault codes decoded against the NAVEE table, with texts in both languages. Unknown codes are shown raw, never guessed.
- **Auto-lock** (opt-in): locks once when the scooter has stood still for 30 s and the Bluetooth signal shows you walked away (Near/Medium/Far). It never fires while moving, and the notification has an Unlock button.
- **Home-screen widget**: one button that locks or unlocks. If the app isn't connected, it connects to the last scooter first. Like the Ride screen, it won't lock a moving scooter.
- **Pairing file**: move this phone's pairing to another phone (Settings → Pairing). See [SECURITY.md](SECURITY.md).
- Controls. Every switch shows what the scooter reports back, not what the app assumes:
  - cruise control, traction control, eco mode
  - drive mode (Normal/Turbo) and kick-start speed (region-aware)
  - lights: automatic headlight, daytime running light, tail, logo and ambient
  - km/h or mph on the scooter's display
- Scooter info: voltage, current, power, temperature, SOH, cycles, serial, detected model, region, five firmware versions.
- A foreground service with a notification and Lock/Unlock, Stop trip and Disconnect actions keeps the link alive with the screen off.

**Trips and maps (offline)**
- **Location features are off by default.** Without them, trips are still logged from the scooter's odometer, with no track and no location permission. Turn them on in Settings → Location for tracks, the map position and the parking spot.
- Trips start automatically when you ride while connected, or you can record by hand.
- Each trip stores:
  - distance, moving time, average and maximum speed
  - **energy used (Wh) and Wh/km**, from the pack voltage and current
  - battery used
- **Timeline map** (offline, Mapsforge): pick a day with ‹ › or the calendar to see every trip that day in its own colour, plus the live track. Tap a trip to highlight it; the arrow opens its details. "All" shows every track.
- **Parked position**: saved when you lock the scooter or walk out of range. The map shows it with distance, a compass arrow, and a Bluetooth signal-strength finder for when the scooter is close.
- Trip details with a map, speed, battery and altitude charts, and **GPX export**.
- Backup and restore of all trips as a single file.

**Deliberately not included:** speed-limit removal, region changes, firmware patching or flashing.

## Offline maps

The app cannot download anything, so you bring the map yourself:

1. On any device, download a Mapsforge `.map` file for your region. For example:
   - <https://download.mapsforge.org/maps/v5/>, under `europe/norway.map` and similar
   - <https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/v5/>
2. Copy it to the phone.
3. In the app, go to **Settings → Offline maps → Import .map file**.

You can import several regions. Without a map, trips still record, and the track is drawn on a blank background.

## Building

Requirements: JDK 21 and the Android SDK (platform 37, build-tools 36+).

```sh
./gradlew testDebugUnitTest   # protocol, pairing-file and auto-lock unit tests
./gradlew assembleDebug       # "Navee Dev" (….dev), installs next to the released app
./gradlew assembleRelease     # minified, unsigned
```

Check that the APK has no network permission:

```sh
$ANDROID_HOME/build-tools/<version>/aapt2 dump permissions app/build/outputs/apk/release/app-release-unsigned.apk
```

Release builds strip `Log.v`/`Log.d`. Debug builds log Bluetooth frames with the client id and serial number masked.

## Project layout

| Package | What |
|---|---|
| `protocol/` | Pure Kotlin, unit-tested: frame builder/checksum, frame reassembly, session handshake, telemetry decoder, setting commands, fault and model tables |
| `ble/` | Scanner and the GATT connection (MTU, write queue, handshake, polling, auto-reconnect) |
| `service/` | Foreground service, trip recorder, parking tracker and auto-lock |
| `pairing/` | Pairing file encode/decode (pure Kotlin, unit-tested) |
| `location/` | Platform GPS (no Google Play Services) and the compass |
| `data/` | Room database (trips, track points, parked position) and DataStore settings |
| `map/` | Map-file import and the Compose wrapper around the Mapsforge map view |
| `export/` | GPX writer and database backup |
| `ui/` | Jetpack Compose + Material 3 screens; texts in `res/values` (English) and `res/values-nb` (Norwegian) |

## Protocol notes

- GATT service `0000d0ff-3c17-d293-8e48-14fe2e4da212`, write characteristic `b002`, notify characteristic `b003`. The scooter does not advertise the service, so it is found by its name (`NAVEE…`).
- Frames:
  - outgoing: `55 AA 00 <cmd> [<len> <payload>] <ck> FE FD`
  - incoming: `55 AA <flag> <cmd> <len> <err> <data> <ck> FE FD`
  - `ck` is the low byte of the sum of all bytes before it.
- After connecting, the app runs a one-round 0x30/0x31 challenge-response with a 6-byte client id. This arms the scooter's live report stream (0x90/0x92). The app **never sends a second 0x30**.
- An unpaired scooter binds itself to the first client id it sees and then refuses any other (err 2, then it hangs up after about 10 s). The app therefore creates its id once per install and keeps it. The pairing file moves it to another phone.
- Status reads: `0x70` parameters, `0x72` battery, `0x73` firmware, `0x74` serial. Settings are single-byte writes, for example `0x51` lock.

The protocol was documented by the [nv-lb-edition](https://github.com/Laufbursche42/nv-lb-edition) project. This app is an independent implementation and contains none of its code.

## Roadmap ideas

- Proximity unlock
- Home-screen widget (battery and parked spot) and a Quick Settings lock tile
- Notifications for charge complete and low battery
- CSV export, monthly statistics, a heatmap of all rides
- Several scooters, more languages

## License

[GPL-3.0-or-later](LICENSE). Map rendering by [Mapsforge](https://github.com/mapsforge/mapsforge) (LGPL-3.0). Map data © OpenStreetMap contributors (ODbL). See also [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md).

"NAVEE" is a trademark of its owner and is used only to say which scooters this app works with.
