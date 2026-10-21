# Security

This page lists the risks of using Navee Offline with a NAVEE scooter: what the app does about each one, and what it cannot fix.

## Risks the app cannot fix

### The scooter's Bluetooth protocol is weak
- The handshake uses one fixed AES key. It is the same in every scooter and every app, and it is publicly known.
- BLE traffic is not encrypted.
- The 6-byte id that identifies your phone is sent in clear text at every connect.

**What this means:** someone within Bluetooth range with a sniffer can capture the id and then lock or unlock your scooter, or change its settings. This is a weakness of the scooter's firmware. No app can fix it.

**The electronic lock is not theft protection.** It is a convenience immobiliser. Use a physical lock.

### An unbound scooter binds to the first app that connects
- A scooter that has not been paired yet binds to the first handshake id it sees.
- After that, it refuses every other id (error 2) and hangs up after about 10 seconds.

The app creates its id once per install and keeps it, so it does not bind the scooter to a throwaway id. Even so, connecting to a new scooter with this app pairs it to this app. To use the manufacturer's app again, you may have to reset the pairing there.

## Risks the app reduces

### The pairing file is a key
Settings → Pairing exports `navee-pairing.json`. It contains the id your scooter is bound to, so anyone with the file can control the scooter like you can.
- Export shows a warning first.
- Import asks for confirmation and validates the file strictly: the format and version, a 12-digit hex id, and the MAC address format. It also enforces a size limit.
- Share the file only with your own phones, over a channel you trust. Delete it after importing.

### The id never appears in logs
- Debug builds log Bluetooth frames, but the payload of the `0x30` handshake (which holds the id) and the serial number in `0x74` replies are masked.
- Release builds remove `Log.v` and `Log.d` calls entirely (R8 rule in `app/proguard-rules.pro`).
- The id is stored in app-private storage, which is excluded from cloud backup and device transfer.

### Auto-lock can misfire
Bluetooth signal strength is a rough measure of distance. Walls, pockets and bodies change it a lot.
- Auto-lock is **off by default**.
- It never fires while the scooter reports any speed, or while the speed is unknown.
- It fires only after 30 seconds at standstill, and only on the average of three readings.
- It fires once, then waits until the scooter has been locked and unlocked again.
- Its notification has an Unlock button.

### Location is personal data
- Location features are **off by default**. The app does not ask for location permission until you turn them on.
- With them off, no GPS updates are registered and no positions are stored.
- With them on, trip tracks, the parking spot, backups and exported GPX files show where you have been. They stay on the phone unless you export them yourself, so treat exported files with care.

### Supply chain
- The app has no `INTERNET` permission, and the manifest strips any that a library tries to add. Check it with `aapt2 dump permissions`.
- The Gradle wrapper pins the distribution's SHA-256 (`distributionSha256Sum`). The wrapper jar matches Gradle's published checksum.

## Release signing
Release APKs must be signed with a key only you control. Keep the keystore and its passwords out of the repository (`*.jks`, `*.keystore` and `keystore.properties` are git-ignored) and back them up offline. If the key is lost, users cannot update. If it leaks, someone else can publish updates that look like yours.

## Reporting a problem
Open an issue on the project's Forgejo, or email the maintainer for anything that should not be public.
