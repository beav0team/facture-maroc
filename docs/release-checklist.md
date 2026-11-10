# Release checklist

Every release, without skipping a line. A release is not done when the APK
builds; it is done when the repository and the memory both describe it.

## 1. Code

- [ ] All JVM tests pass (`./gradlew testDebugUnitTest`).
- [ ] Instrumented tests pass (`./gradlew connectedDebugAndroidTest` or
      `adb shell am instrument`).
- [ ] R8 release build installed and walked through on the device/Waydroid.
- [ ] **Do not build R8 or publish without Yassine’s approval.**
- [ ] Version bumped in `app/build.gradle.kts` (versionCode + versionName).

## 2. Compliance and content

- [ ] `docs/status.md` updated: version, commit, feature table, test counts,
      Play state, open gaps.
- [ ] `docs/decisions.md` updated if a decision was made or reversed.
- [ ] `README.md` feature list and test counts updated.
- [ ] `play_store/listing-fr.md` updated if user-facing features changed
      (templates, payments, stock, notes…).
- [ ] Play screenshots refreshed if the UI changed.
- [ ] `play_store/UPLOAD.md` updated: version, versionCode, AAB size, SHA-256,
      permissions.
- [ ] Privacy policy and Data Safety still accurate (no new permission, no
      network access).

## 3. Release

- [ ] Commit with a message that says what changed and why.
- [ ] Tag `vX.Y.Z` (lightweight, matching existing tags).
- [ ] Push `master` and the tag.
- [ ] Build the AAB; check 16 KB page-size support when targeting API 36.
- [ ] Add `mapping.txt` and the AAB to the Play upload.
- [ ] Publish the GitHub release with APK + AAB and a short French summary.
- [ ] For Play production: confirm the 12-tester / 14-day requirement is met.

## 4. Memory

- [ ] Update the `tyre-finder` skill with only new **traps** and the new
      version stamp. Do not restate feature lists — point at `docs/status.md`.
- [ ] Run `~/.local/share/opencode-memory/sync.sh`.
- [ ] Verify the memory repo pushed cleanly.
