# Privacy

Navee Offline collects nothing and sends nothing.

- **No network access.** The app does not request Android's `INTERNET` permission, so it cannot reach any server.
- **No accounts, analytics, ads or crash reporting.**
- **Bluetooth** is only used to talk to your scooter. Scanning is flagged `neverForLocation`.
- **Location is off by default.** Until you turn on Settings → Location → *Location features*, the app does not ask for location permission, does not register for GPS updates and stores no positions. Trips are still logged from the scooter's own odometer, without a map track.
- **With location features on**, GPS is used for trip tracks, your position on the map and the parking spot. Positions come from the phone's satellite receiver and are stored only in the app's private database.
- **Wi-Fi/cell positioning** (optional, off by default, only with location features on) lets the phone's own location service stand in when GPS has no fix, for example indoors. It is used only for the map position and the parking spot, never for trip tracks. The phone's location service may contact Google to compute it; the app itself still cannot.
- **The pairing file** (Settings → Pairing) contains the id your scooter is bound to. It is only created when you export it. See [SECURITY.md](SECURITY.md).
- **Cloud backup and device transfer are disabled.** Your data leaves the phone only when you export a GPX file, a backup or a pairing file yourself, to a place you choose.
- **Uninstalling** the app deletes all of its data.

## Permissions

| Permission | When it is asked for | Why |
|---|---|---|
| `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (`BLUETOOTH`, `BLUETOOTH_ADMIN` on Android ≤ 11) | First start | Find and connect to the scooter |
| `POST_NOTIFICATIONS` | First start (optional) | The status notification with Lock/Unlock and Stop actions, and the auto-lock notice |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | When you turn on location features. On Android 10 and 11 also at first start, because Bluetooth scanning needs it there | Trip tracks, map position, parking spot |
| `FOREGROUND_SERVICE`, `…_CONNECTED_DEVICE`, `…_LOCATION` | Not a prompt | Keep the scooter link, and with location features on also GPS, running with the screen off. The location type is only used while location features are on |
