# Release signing

The installable APK is attached to [GitHub Releases](https://github.com/falword/bybit-card-tracker/releases/latest) and stored at `dist/CardTrack-<version>.apk`. GitHub Actions builds an unsigned release APK for tests. It does not sign the file people download.

Do not generate a second keystore and do not commit any keystore. Updates install over the published app only when they are signed with the same key. Unsigned `assembleRelease` still succeeds when `CARDTRACK_STORE_FILE` is unset, and that APK will not install on a phone.

## Generate a keystore (local only)

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

The current keystore is `~/cardtrack-release.jks`, alias `cardtrack`. Passwords stay in `~/.gradle/gradle.properties` on the machine that signs releases. The certificate SHA-256 is `0cb93e71dab399fd75d9c2d235dce2eceb8e53a0c74422e9f5116d32fc8817ff`. Confirm the next APK prints the same digest before publishing it.

## Publish a signed APK

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleRelease
cp app/build/outputs/apk/release/app-release.apk dist/CardTrack-<version>.apk
```

Commit that APK, tag `v<version>`, and push the tag. `.github/workflows/release.yml` attaches `dist/CardTrack-*.apk` to the GitHub Release. Keep the keystore. Without it, the next APK cannot update phones that already installed this one.
