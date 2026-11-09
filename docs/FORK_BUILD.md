# Fork builds, signing and self-updates

This fork publishes its own signed APKs. The in-app updater and Obtainium both
follow the newest GitHub release in this repository.

## What is different from upstream

| Change | Where |
| --- | --- |
| `versionCode` / `versionName` can be overridden per build (`-ParvioVersionCode`, `-ParvioVersionName`); defaults keep upstream parity | `app/build.gradle.kts` `defaultConfig` |
| The self-updater reads releases from `d1dac0/ARVIO` | `app/build.gradle.kts` `GITHUB_OWNER` / `GITHUB_REPO` |
| Push to `main` builds a signed `sideloadRelease` APK and publishes it as a GitHub release (`v<versionName>`) | `.github/workflows/fork-release.yml` |

Every release uses `versionName = <upstream base>-fork.<run number>` and
`versionCode = <upstream base> + <run number>`, so each run is strictly newer
than the last and clears `VersionUtils.isRemoteNewer`. When a merge brings in
new upstream defaults, the workflow picks them up from `app/build.gradle.kts`
automatically.

## Signing key

The fork signs with its own key, **not** the upstream release key. Upstream's key
only exists as the `KEYSTORE_BASE64` Actions secret in `ProdigyV21/ARVIO`; it is
not in the repository, not in any commit in its history (`.gitignore` blocks
`*.jks`, `*.keystore` and `keystore.properties`), and cannot be recovered from a
published APK, which carries only the public certificate.

| Item | Location |
| --- | --- |
| Keystore (`PKCS12`, alias `arvio_release`, RSA 4096, valid ~27 years) | `release.keystore` (git-ignored) |
| Passwords | `keystore.properties` (git-ignored) |
| CI copy | `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` repository secrets |

**Back the keystore and its passwords up somewhere safe.** If they are lost, no
future build can update an installed fork APK; the app has to be uninstalled and
reinstalled, which wipes app data.

Fork certificate, for checking a build:

```
keytool -list -v -keystore release.keystore -alias arvio_release | findstr SHA256
```

The upstream signing certificate (for comparison) is
`SHA-256 9778d7533d4bc1aee80c1d2d7043fb22cba3b79cd11911d3b131c1316d5f17c1`.

## Installing over another build

Android only accepts an update with the same signing key, so:

- **Upstream sideload APK or Google Play install → fork APK**: uninstall the
  installed app first (`adb uninstall com.arvio.tv`). App data and settings are
  deleted with it. After that, every fork release installs in place.
- **Fork APK → fork APK**: updates install normally, in place, keeping data.
- Play Store installs are signed by Google, so they can never be replaced by
  these builds without an uninstall/reinstall either.

## Local signed build

```powershell
./gradlew assembleSideloadRelease -ParvioAllowMissingIntegrations=true
```

`keystore.properties` points at the keystore, so `release` / `staging` builds are
signed automatically; without it Gradle silently falls back to the debug key.
Output: `app/build/outputs/apk/sideload/release/`.

Release builds normally refuse to run without real Telegram and Trakt
credentials (`validateReleaseCloudSecrets` in `app/build.gradle.kts`).
`-ParvioAllowMissingIntegrations=true` downgrades that gate to a warning, which
is what the fork workflow uses. The APK then falls back to the placeholders in
`secrets.defaults.properties`: Telegram sign-in and Trakt syncing stay disabled,
everything else works.

## Optional integration credentials

Adding these repository secrets makes the corresponding features real; the
workflow forwards them as environment variables, and they take priority over
`secrets.defaults.properties`:

| Secret | Effect when set |
| --- | --- |
| `TELEGRAM_API_ID`, `TELEGRAM_API_HASH` | Telegram sign-in and streaming work |
| `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET` | Trakt connection and scrobbling work |

Without a local `secrets.properties`, builds fall back to the public defaults,
including upstream's Supabase project URL and anon key — i.e. cloud sync talks to
upstream's backend. Point `SUPABASE_URL`/`APP_ANON_KEY` elsewhere if you want
your own.

The private Discord partner SDK (`app/libs/discord_partner_sdk.aar`, fetched in
upstream CI through a deploy key) is not available to forks, so Discord partner
features are absent.

## Cutting a release

Push to `main`, or run the *Fork release* workflow manually. It resolves the
version, builds `sideloadRelease`, prints the signing certificate, and publishes
`ARVIO-<versionName>-sideload-release.apk` as a non-draft, non-prerelease GitHub
release tagged `v<versionName>` — the shapes the in-app updater
(`AppUpdateRepository` + `AbiSelector`) and Obtainium both accept.
