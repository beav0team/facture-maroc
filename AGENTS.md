# Facture Maroc — agent instructions

## Read before planning

1. `docs/status.md` — what actually exists, current version, open gaps.
2. `docs/decisions.md` — settled and open product decisions.
3. `README.md` — user-facing documentation.
4. `play_store/UPLOAD.md` — Play state and signing.

If `docs/status.md` is older than `app/build.gradle.kts`, **audit the code
before answering**. Do not describe a feature from memory.

## The app

Offline, Moroccan-market invoicing and inventory app. One activity, Compose
Material 3, `StateFlow` stores, JSON files, no database, no network permission.
French UI and French documents. MAD only.

## Commands

```bash
./gradlew testDebugUnitTest          # JVM suite
./gradlew assembleDebug              # debug APK
./gradlew assembleDebugAndroidTest   # instrumented test APK
./gradlew assembleRelease            # R8 APK — only with Yassine's approval
./gradlew bundleRelease              # AAB for Play — only with approval
```

Waydroid installs: uninstall first when switching between debug and release
signatures (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). If the container is FROZEN,
restart it before retrying `adb install`.

## Conventions

- UI strings, README and release notes in **French**. Code comments in English.
- Any behaviour change needs a test. The arithmetic (`InvoiceData`) and the
  stock rules are tested on the JVM; PDF rendering is tested on a device.
- One source of truth for amounts: `InvoiceData`. Templates only draw.
- Never write stock directly; every movement goes through the stock code.
- Never delete an issued document; archive it and correct with a credit note.
- Keep the app offline: no `INTERNET` permission, no analytics, no accounts.

## Release discipline

Follow `docs/release-checklist.md`. It requires updating `docs/status.md`,
`README.md`, the Play material and the skill **before** tagging.

**Do not run `assembleRelease` / `bundleRelease` or publish anything until
Yassine explicitly approves the R8 build.**

## Secrets

`keystore.properties`, `keystore.properties.example` values, `local.properties`
and any signing material must never be committed. The keystore lives outside the
repo (`~/.android/tyre-inventory.jks`).
