# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

`AGENTS.md` (imported above) is the source of truth for the fork setup, the Windows sandbox build
environment, git and GitHub quirks, the Android code map and conventions. Claude Code stops reading
it on its own once a `CLAUDE.md` exists, so keep the import. This file only adds what `AGENTS.md`
does not cover.

## Commands

### Android (`app/`)

Set `GRADLE_USER_HOME` first, as `AGENTS.md` shows. Unit tests run against the `sideloadDebug`
variant. The credential gate only runs for release and staging variants, so debug builds and tests
do not need `-ParvioAllowMissingIntegrations`.

```powershell
# Debug APK -> app/build/outputs/apk/sideload/debug/
./gradlew :app:assembleSideloadDebug --no-daemon

# One test class
./gradlew :app:testSideloadDebugUnitTest --tests "com.arflix.tv.updater.AppUpdateCheckTest" --no-daemon

# One test method (names are backticked sentences, so match with a wildcard)
./gradlew :app:testSideloadDebugUnitTest --tests "*AppUpdateCheckTest.first*" --no-daemon

# Whole unit suite, the way PR CI runs it
./gradlew testSideloadDebugUnitTest --no-daemon "-PpartnerManagement=true"

# Tests that exist only for the play flavor (app/src/testPlay)
./gradlew :app:testPlayDebugUnitTest --no-daemon

# Detekt (config/detekt/detekt.yml; reports findings, never fails the build)
./gradlew :app:detekt --no-daemon
```

- Partner tests call `assumeTrue(BuildConfig.PARTNER_MANAGEMENT_ENABLED)`: without
  `-PpartnerManagement=true` they are skipped, not passed.
- Add `-PincludeX86Abis=true` to build for an x86 emulator; APKs are ARM-only otherwise.
- A debug build uses the real application ID `com.arvio.tv` with the debug key, so it cannot install
  over a release build. Use an emulator or a spare device; never uninstall the installed app to make
  a debug build fit, because that deletes its data.
- `app/src/androidTest` (`*DeviceTest`) needs a connected device and is not run by CI.
- Tests that touch `settingsDataStore` should use `IsolatedSettingsStoreRule`
  (`app/src/test/kotlin/com/arflix/tv/testing/`). On Windows it swaps in an in-memory store, because
  DataStore 1.0 cannot replace its file there.

### Web (`web/`, Node 22.19+)

```powershell
npm ci
npm run dev -- --hostname 127.0.0.1
npm test                                 # node --test tests/*.test.cjs
node --test tests/addon-sync.test.cjs    # one file
npx tsc --noEmit                         # the type check CI runs
npm run build                            # regenerates translations, then next build
```

- `npm run lint` has no ESLint config behind it; rely on `npx tsc --noEmit`.
- Self-hosted mode reads `.env.local`: `npm run setup:selfhost` creates it and
  `npm run check:selfhost` validates it.
- `scripts/check-*-ui.cjs` (also exposed as the `test:*-ui` scripts) are Playwright browser checks
  run one at a time; `npm test` does not include them.

### Backend, worker and site

```powershell
# from netlify-auth-site/
npm ci --ignore-scripts
npm test                                       # node --test tests/*.test.js
node --test tests/account-sync-merge.test.js   # one file

# from resolver-worker/
npm run typecheck
```

- CI installs `web/` dependencies before the backend tests (`npm ci --ignore-scripts --prefix ../web`);
  the partner dashboard tests build from `web/`.
- The marketing site's checks are `node --test` files under `netlify-arvio-tv-site/scripts/`; CI runs
  them from `web/`.

PR CI is `.github/workflows/build-check.yml`: `npm test` in `netlify-auth-site` and `web`, the site
checks, `npx tsc --noEmit` in `web`, and the Gradle unit suite. Run the part that matches what you
changed.

## What is in this repository

| Path | What it is | Ships from this fork? |
| --- | --- | --- |
| `app/`, `benchmark/` | Android app, one APK for TV, phone and tablet (namespace `com.arflix.tv`, application ID `com.arvio.tv`), plus its baseline-profile module | Yes, through `fork-release.yml` |
| `web/` | Next.js 15 / React 19 browser app | No (upstream's `web.arvio.tv`, or self-hosted) |
| `netlify-auth-site/` | ARVIO Cloud backend as Netlify Functions: sign-in, TV device-code auth, account sync, TMDB/SIMKL/Trakt proxies, entitlements. Files starting with `_` are shared modules | No (upstream's `auth.arvio.tv`) |
| `supabase/` | Earlier Supabase edge functions and SQL migrations. The app now uses the Netlify backend and keeps the Supabase path behind `Constants.USE_NETLIFY_CLOUD_SYNC` | No |
| `resolver-worker/` | Cloudflare Worker the web app uses for server-side addon stream lookup and SRT to WebVTT | No (upstream's `resolve.arvio.tv`) |
| `netlify-arvio-tv-site/` | Static marketing and guides site | No (upstream's `arvio.tv`) |
| `docs/` | Dated investigation and validation notes per feature; `docs/FORK_BUILD.md` covers fork signing and releases | |

**This fork only ships the APK.** It has no Netlify or Cloudflare credentials, so nothing under
`web/`, `netlify-auth-site/`, `supabase/` or `resolver-worker/` is deployed from here, and fork APKs
call upstream's deployed backend (`NETLIFY_BACKEND_URL` and `SUPABASE_URL` in
`secrets.defaults.properties`). An app change that needs new backend behavior will not work until
upstream ships it, so app-side work has to fit the backend as it is deployed. A push to `main` that
touches `web/**` still runs the `ARVIO Web container preview` workflow.

## Android architecture

**Shape.** Single-activity Compose app: `ArflixApplication` -> `MainActivity` ->
`navigation/AppNavigation.kt`, where the `Screen` sealed class defines every route and its
`createRoute(...)` builder. Each destination is `ui/screens/<area>/<Area>Screen.kt` plus a
`@HiltViewModel`. ViewModels inject the `@Singleton` repositories in `data/repository/` directly
(there is no use-case layer; `domain/` only holds plugin models), and repositories call the Retrofit
interfaces in `data/api/` that `di/AppModule.kt` provides. The central files are very large
(`SettingsScreen.kt` ~14k lines, `IptvRepository.kt` ~12k, `PlayerViewModel.kt` ~8.6k,
`PlayerScreen.kt` ~7.8k): search for the symbol and read around it instead of reading whole files.

**Storage.** There is no Room database. State lives in Preferences DataStores (five are declared in
`util/DataStores.kt`), mostly as JSON strings; SQLite is used only for the IPTV channel store and EPG
index. Per-profile values share those stores under `profile_<id>_` keys built by
`ProfileManager.profileStringKey(...)` and its siblings. A few things are account-wide on purpose
(installed addons are stored under `shared_installed_addons_v1`).

**One APK, three form factors.** `detectDeviceType()` in `util/DeviceType.kt` picks TV, TABLET or
PHONE (with a user override) and publishes it as `LocalDeviceType`. Screens branch on it instead of
existing twice. TV screens drive focus themselves: they handle D-pad keys in `onPreviewKeyEvent`,
often keep their own focus state (section and index), and move real focus with `FocusRequester`.
The navigation rules that are pure and unit-tested live in `ui/focus/`.

**Flavors and build flags.** `play` and `sideload` differ in two ways: `BuildConfig` flags
(`SELF_UPDATE_ENABLED`, `FEATURE_PLUGINS_ENABLED`, `AUDIO_SYNC_AVAILABLE`) and same-named classes in
`app/src/play/kotlin` and `app/src/sideload/kotlin` (`PluginManager`, `PluginRuntimeHooks`,
`ProfileDataStoreFactory`, `NetworkPlatform`, `SherpaSpeechToText`, `AudioSyncFfmpegDecoder`). The
`play` copies are stubs (its `PluginManager` does nothing, its `SherpaSpeechToText` throws); `sideload`
has the real plugin system (QuickJS scrapers and Cloudstream extensions) and Sync by Hearing.
Changing a signature that `main` calls means editing both copies, and only a play build
(`:app:assemblePlayDebug`) shows a break there, because CI and the fork release build sideload.
`-PpartnerManagement=true` turns on `PARTNER_MANAGEMENT_ENABLED`, an experimental managed-delivery
path guarded inside `StreamRepository`, `IptvRepository`, `HomeServerRepository` and
`CatalogRepository`; it is off in normal and fork builds.

**Sources pipeline.** `StreamRepository` answers "what can play this title". Installed
Stremio-protocol addons are queried in parallel through `AddonRuntime` and emitted as each one
answers (`resolveMovieStreamsProgressive` and `resolveEpisodeStreamsProgressive` return a
`Flow<ProgressiveStreamResult>`), behind a per-profile memory and DataStore cache keyed by the addon
set. Home-server (Plex, Emby, Jellyfin) and IPTV VOD sources come from separate `resolve...HomeServerSources` and `resolve...VodSources`
calls that `DetailsViewModel` and `PlayerViewModel` merge in. `resolveStreamForPlayback` then turns
the chosen source into a playable URL (redirect chains, torrents through TorrServer, YouTube).

**Cloud sync.** The whole account state is one JSON snapshot. A repository that changes synced state
calls `CloudSyncInvalidationBus.markDirty(scope)`; `CloudSyncCoordinator` debounces that and calls
`CloudSyncRepository.pushToCloud()`, which builds the snapshot (`buildCloudSnapshotJson`), merges it
with the remote copy (per-field last-writer-wins timestamps, merged profile list) and uploads it.
Pulls apply remote state (`applyCloudPayload`) inside `suppressDuringRemoteApply`, so applying it
does not mark things dirty again.

**Networking.** Every Retrofit API shares the process-wide `OkHttpProvider.client`. It is initialized
in `ArflixApplication.attachBaseContext` because Hilt builds the repositories before `onCreate` runs.
`ApiProxyInterceptor` on that client reroutes TMDB calls through the backend proxy when
`ENABLE_TMDB_EDGE_PROXY` is set, and SIMKL calls when no client ID is built in.

**Processes.** The manifest runs seek-preview extraction in `:seek_preview`
(`docs/SEEK-KEYFRAME-WORKER.md`) and the crash screen in `:crash`. `ArflixApplication.onCreate`
returns early for both, so initialization added there has to respect those process checks.

**Playback.** `PlayerScreen.kt` and `PlayerViewModel.kt` hold almost all playback logic on Media3
ExoPlayer, wrapped by `player/engine/PlayerEngine` (the MPV and VLC engine types fall back to
ExoPlayer). The D-pad player UI is written inline in `PlayerScreen.kt`, and touch devices get the
`player/mobile/ArvioMobilePlayer` overlay on top. `player/tv/ArvioTvPlayer.kt` and the controls and
panels it uses are not called from anywhere; only `TvSkipIntroButton` from that package is wired in,
so a TV player change belongs in `PlayerScreen.kt`. Two Media3 extractors are vendored with local
changes: `app/src/main/dvmkv-java` (Matroska with Dolby Vision hooks) and `app/src/main/iptvts-java`
(MPEG-TS that keeps every DVB subtitle stream). Each has a `package-info.java` with the steps to
re-vendor it on a Media3 bump.

**Live TV.** The `tv` route renders `ui/screens/tv/live/LiveTvScreen.kt` with `TvViewModel` on top
of `IptvRepository` (M3U, Xtream, Stalker). The `TvScreen` composable in `ui/screens/tv/TvScreen.kt`
has no call sites; check for callers before editing anything in that file. Kids profiles are
redirected away from this route in `AppNavigation`.

**Pinned dependencies.** Several versions are pinned to avoid specific crashes: R8 in
`settings.gradle.kts`; Hilt, the Compose BOM, Ktor and `kotlin-metadata-jvm` in the Gradle build
files. Each pin has a comment saying what breaks. Read it before bumping anything.

## Web architecture

- `app/page.tsx` is a static page that mounts `components/shell/AppShell` inside the `lib/store`
  provider, so the app is a client-side SPA. The `app/api/*` routes are server-side proxies (TMDB,
  Trakt, SIMKL, MDBList, subtitles, cloud auth) that keep API credentials on the server.
- Feature logic sits in flat `lib/*.ts` modules that parallel the Android repositories (`cloud.ts`,
  `iptv.ts`, `homeserver.ts`, `trakt.ts`, `addons.ts`, `player.ts`); UI is in
  `components/<feature>/`.
- The deployment mode is fixed at build time by `NEXT_PUBLIC_SELF_HOSTED` (`lib/config.ts`): hosted
  builds use ARVIO Cloud and the paywall, self-hosted builds keep state in the browser and switch
  both off.
- Tests are plain `node --test` files. `tests/load.cjs` transpiles one `lib` module and runs it in
  a `vm` sandbox where every import must be passed in as a mock or be on its allowlist. Adding an
  import to a `lib` module can therefore fail its tests with `Unmocked dependency <name>` until the
  test or `load.cjs` covers it.

## Where Android and web depend on each other

- **Cloud snapshot.** The snapshot built by `CloudSyncRepository.buildCloudSnapshotJson` is also read
  and merged by `web/lib/cloud.ts` and `netlify-auth-site/netlify/functions/_sync-merge.js`, and the
  backend tests copy the Android shapes. A change to a synced field touches all three, and the
  backend half only takes effect once upstream deploys it.
- **Translations.** `web/scripts/generate-translations.mjs` builds the web interface languages at
  build time from Android's `app/src/main/res/values*/strings.xml`, keyed by the English wording, so
  rewording an English Android string drops the web translation for that phrase.
  `web/scripts/translation-sources.mjs` also regex-parses `val TMDB_LANGUAGES = listOf(` in
  `SettingsScreen.kt` and fails the web build if that declaration changes shape.
- **Default catalogs.** `web/lib/defaultCatalogs.json` is a snapshot of Android's default catalogs,
  checked by `WebDefaultCatalogParityTest`. After changing the Android defaults, regenerate it:

```powershell
$env:ARVIO_EXPORT_WEB_DEFAULTS = '1'
./gradlew :app:testSideloadDebugUnitTest --tests "*WebDefaultCatalogParityTest" --rerun-tasks --no-daemon
Remove-Item Env:ARVIO_EXPORT_WEB_DEFAULTS
```
