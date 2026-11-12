# AGENTS.md — working in this repository

This is **d1dac0/ARVIO**, a fork of [ProdigyV21/ARVIO](https://github.com/ProdigyV21/ARVIO): an
open-source media hub with an Android TV/phone app (`app/`, Kotlin + Compose + Hilt), a self-hosted
Next.js web app (`web/`), a resolver worker, Supabase migrations, and Netlify marketing/auth sites.
Upstream is Apache-2.0 (see `LICENSE`); keep upstream files as close to theirs as possible unless
the change is fork-specific.

## Fork-specific setup (do not break these)

| Thing | Where | Why |
| --- | --- | --- |
| Own release keystore | `release.keystore` + `keystore.properties` (both git-ignored) | Upstream's key is only a GitHub secret; a fork cannot reuse it |
| Signing secrets | `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` on the fork | `.github/workflows/fork-release.yml` |
| Auto-release | push to `main` → builds signed `sideloadRelease`, publishes `v<versionName>` | This is the app's update channel |
| Self-updater target | `GITHUB_OWNER`/`GITHUB_REPO` in `app/build.gradle.kts` → `d1dac0`/`ARVIO` | Otherwise the app follows upstream releases |
| Version overrides | `-ParvioVersionCode=…`, `-ParvioVersionName=…` | `versionCode = 322 + run number`, `versionName = <base>-fork.<run>` |
| Credential gate waiver | `-ParvioAllowMissingIntegrations=true` | Fork has no Telegram/Trakt credentials; those features stay off until repository secrets are set or, for Trakt, a Client ID is entered in Settings |
| TMDB fallback | `ENABLE_TMDB_EDGE_PROXY` in `app/build.gradle.kts` | Fork builds without `TMDB_API_KEY` use `auth.arvio.tv/tmdb-proxy`; otherwise Home's hero and rows are empty |

Every push to `main` publishes a release, so a merge is a ship. Use `[skip ci]` in the commit
message for docs-only or CI-irrelevant commits.

Never commit `release.keystore`, `keystore.properties`, `secrets.properties`, or any API key.
Losing the keystore means installed builds can never be updated again — it must be backed up.

## Building and testing (Windows, this machine)

The DSH file sandbox cannot write `%USERPROFILE%\.gradle`, so point Gradle at a workspace-local
home or every build dies with `AccessDeniedException`:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle\user-home'
./gradlew assembleSideloadRelease "-ParvioAllowMissingIntegrations=true" --no-daemon
./gradlew :app:testSideloadDebugUnitTest --tests "com.arflix.tv.ui.components.*" `
  "-ParvioAllowMissingIntegrations=true" --no-daemon
```

- Builds are slow: ~25 min for a release APK, 10–20 min for a debug compile even when incremental.
  Run them as background jobs and keep working; do not poll in a tight loop.
- Expected noise: `Unable to initialize metrics` (sandboxed `~/.android`), `Couldn't open current
  thread` from Gradle's file watcher, and Kotlin daemon failures that fall back to in-process
  compilation. None of these fail the build.
- `DragReorderableListTest` fails in this sandbox with "Could not self-attach to current VM"
  (Byte Buddy agent). It is an environment limitation, not a regression.
- Verify a built APK with `$env:ANDROID_HOME\build-tools\35.0.1\apksigner.bat verify --print-certs
  <apk>`; the fork certificate DN is `CN=ARVIO Diego Fork, OU=Self-hosted, O=d1dac0`.

## Git and GitHub on this machine

- schannel TLS is broken here (`SEC_E_NO_CREDENTIALS`). The repo is configured with
  `git config http.sslBackend openssl`; keep it or clones/fetches fail.
- The `gh` credential helper cannot spawn a shell under the sandbox, so `git push` prompts and
  fails. Push with a token URL built from `gh auth token` and never store it:
  `git -c credential.helper= push "https://d1dac0:$t@github.com/d1dac0/ARVIO.git" main:main`.
  Do not echo the token, and do not write it into config or files.
- Issues live on the fork (upstream issues are for upstream). Reference them with `Closes #N` in
  commits pushed to `main` so they close automatically.
- `Deploy Web App` is disabled on the fork: it triggers on `strings.xml` changes and would fail
  without Netlify credentials. Leave it disabled.
- The `Build Check` workflow's `signed-apk` job needs upstream's private Discord SDK deploy key and
  cannot run here; the compile/test job still works.
- `Actionlint` runs shellcheck over workflow run blocks with `fail-on-error: true`, so new
  workflows must be shellcheck-clean.

## Code map for common work

| Area | File |
| --- | --- |
| Source picker ("Sources" list after choosing a title) | `app/src/main/kotlin/com/arflix/tv/ui/components/StreamSelector.kt` (~3.2k lines) |
| TV text entry (native `EditText`, TV IME) | `app/src/main/kotlin/com/arflix/tv/ui/components/TextInputModal.kt` |
| QR rendering | `ui/components/QrCodeImage.kt`, `util/QrCodeGenerator.kt` |
| Activation-style flows to copy (Trakt, Telegram) | `ui/screens/settings/*`, `SettingsScreen.kt` |
| Streams, addons, debrid heuristics | `data/repository/StreamRepository.kt`, `data/repository/AddonRuntime.kt` |
| Self-updater | `updater/AppUpdateRepository.kt`, `updater/VersionUtils.kt`, `updater/AbiSelector.kt` |
| Settings + DataStore persistence | `ui/screens/settings/SettingsViewModel.kt` |
| Playback | `ui/screens/player/PlayerScreen.kt`, `ui/screens/player/PlayerViewModel.kt` |

Notes on `StreamSelector`: it owns its own D-pad focus model (`focusZone` = `streams` / `addons` /
`filter`) through `onPreviewKeyEvent`, so do not drop a Compose `TextField` into the TV layout —
use `TextInputModal`. The list pipeline is `orderedPresentations` → `filteredPresentations`
(addon tab → quality/debrid chip → name query) → `flatPresentations`; navigation and rendering both
read the last stage. Pure helpers there are `internal` and unit-tested directly.

## Conventions

- Conventional commit messages (`feat(sources): …`, `fix(kids): …`, `ci: …`), matching upstream.
- Unit tests are JVM tests under `app/src/test/kotlin/...`, colocated by package with the code.
  Assert with Truth (`com.google.common.truth.Truth.assertThat`).
- UI strings go in `app/src/main/res/values/strings.xml`; add Spanish to `values-es/`. Missing
  locales fall back to English, so translating all ~50 locales is not expected.
- Anything touching the sources list must still work with the addon tabs and the filter chips, and
  must handle zero results (show a message, not a blank list).
- Prefer extending existing components (`QrCodeImage`, `TextInputModal`, `AppUpdateRepository`)
  over adding parallel implementations.
