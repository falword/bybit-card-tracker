# Release signing

Do not generate or commit a keystore in this repository. Unsigned `assembleRelease` still succeeds when `CARDTRACK_STORE_FILE` is unset.

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
