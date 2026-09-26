# Release signing

Every push to `main` builds a signed APK and replaces `CardTrack.apk` on the [continuous release](https://github.com/falword/bybit-card-tracker/releases/tag/continuous). That release is marked latest, so the download link stays the same. Pull requests only run tests. They do not publish an APK.

GitHub Actions signs with the same keystore that lives on the maintainer's machine. The keystore is stored as Actions secrets, not in git. The job checks that the APK certificate SHA-256 is `0cb93e71dab399fd75d9c2d235dce2eceb8e53a0c74422e9f5116d32fc8817ff` before uploading. A different key would not update phones that already installed CardTrack.

CI sets `versionCode` to `10000 + GITHUB_RUN_NUMBER` and `versionName` to `1.0.5+<commit>`. The higher code is what lets Android install the new file over the previous one. A local build without those variables stays at versionCode 6 and versionName 1.0.5.

## Generate a keystore (local only)

Do not generate a second keystore and do not commit any keystore.

```bash
keytool -genkeypair -v \
  -keystore ~/cardtrack-release.jks \
  -alias cardtrack \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

## Gradle properties

Set these names in `~/.gradle/gradle.properties` (or the same names as environment variables). Values stay on your machine:

- `CARDTRACK_STORE_FILE`
- `CARDTRACK_STORE_PASSWORD`
- `CARDTRACK_KEY_ALIAS`
- `CARDTRACK_KEY_PASSWORD`

The current keystore is `~/cardtrack-release.jks`, alias `cardtrack`. Passwords stay in `~/.gradle/gradle.properties` on the machine that signs releases.

Actions reads the same passwords from repository secrets:

- `CARDTRACK_KEYSTORE_BASE64` — base64 of `~/cardtrack-release.jks`, one line
- `CARDTRACK_STORE_PASSWORD`
- `CARDTRACK_KEY_PASSWORD`

Unsigned `assembleRelease` still succeeds when `CARDTRACK_STORE_FILE` is unset, and that APK will not install on a phone.

## Publish

Push to `main`. The `publish` job in `.github/workflows/ci.yml` runs after tests, signs the APK, and uploads it. Keep the keystore. Without it, the next APK cannot update phones that already installed this one.
