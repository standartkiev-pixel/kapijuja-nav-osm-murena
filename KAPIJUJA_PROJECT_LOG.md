# Kapijuja Nav OSM Murena — project log

This repository is the clean product line for Kapijuja Nav based on the upstream Murena Maps/Cardinal application.

## Rules for this repository

1. Start from a clean upstream Murena Maps/Cardinal source baseline.
2. First goal: compile and test the upstream application essentially unchanged.
3. Do **not** add BUS/TRUCK profiles or custom Stadia/Valhalla routing modifications until the clean baseline APK has been built and tested on the target device.
4. Keep previous Kapijuja repositories only as references/test laboratories; do not mix their experimental code into this clean line.
5. Record every import, build-only fix, failed experiment, workflow run, artifact and later functional patch in this file.
6. Prefer high-level changes. Do not modify MapLibre/Ferrostar/native navigation internals unless evidence shows it is necessary.
7. Never commit API keys or signing secrets. Use GitHub Actions secrets.

## 2026-09-03 — clean restart

- New canonical repository: `standartkiev-pixel/kapijuja-nav-osm-murena`.
- Repository intentionally started empty.
- User requested a clean, testable copy of the full Murena Maps navigation application with its existing UI/menus/settings before any bus-specific work.
- Current upstream source of Murena Maps is the /e/OS GitLab project (`e/os/maps`, formerly Cardinal). Murena states publicly that Murena Maps is based on OpenStreetMap, MapLibre, WhosOnFirst, Transitous and other open components.
- Historical GitHub source is `ellenhp/cardinal`; development later moved to the /e/OS GitLab project.
- Next step: import a pinned clean upstream snapshot, record the exact upstream commit, add build CI only, and produce the first baseline APK.

### Deliberately postponed

- BUS costing.
- Truck profile.
- Vehicle dimensions/weight UI.
- Custom Stadia/Valhalla BUS integration.
- Branding changes beyond what is strictly required to build.
- Feature changes of any kind.

## 2026-09-03 — bootstrap experiment 1 failed before any source import

- GitHub Actions run: `33772893036`.
- Result: workflow failed before jobs were created; no Murena source was imported and no application code was changed.
- Cause: invalid YAML in the first bootstrap workflow. A shell heredoc embedded in a YAML block had unindented Markdown lines, so GitHub could not parse the job definition.
- Fix: replaced the heredoc with an indented shell `echo` block. This is an infrastructure-only correction; it does not modify Murena functionality.

## 2026-09-03 — bootstrap experiment 2 imported upstream locally but push was rejected

- GitHub Actions run: `33773010011`; job: `100707712860`.
- Official upstream clone completed successfully from `https://gitlab.e.foundation/e/os/maps`.
- Exact upstream commit cloned: `f9a061aff58ec7f11dc7fa19bd0138720fc99b01`.
- Upstream commit date: `2026-09-03T15:21:13Z`.
- Upstream commit subject: `Translated using Weblate (Portuguese (Brazil))`.
- Git LFS assets downloaded successfully, including the bundled map MBTiles assets.
- The runner created local import commit `6044b8e` containing 1,252 files and 150,013 inserted lines.
- The final push was rejected as non-fast-forward because this project log was updated on `main` while the import workflow was still running. This was a repository bookkeeping race, not a Murena source/build failure.
- No Murena source from that runner commit reached `main`.
- Fix for the next bootstrap: base the generated import commit on the latest `origin/main` immediately before committing/pushing, and do not make parallel repository writes during the import.

## 2026-09-03 — clean upstream snapshot imported

- Official upstream: `https://gitlab.e.foundation/e/os/maps`
- Upstream branch: `main`
- Exact upstream commit: `f9a061aff58ec7f11dc7fa19bd0138720fc99b01`
- Upstream commit date: `2026-09-03T15:21:13Z`
- Upstream commit subject: `Translated using Weblate (Portuguese (Brazil))`
- Kapijuja import commit: `0adf78dd4801e71494f1dc5001734589dc025069`.
- Bootstrap GitHub Actions run: `33773338841` — success.
- Import method: shallow clone in GitHub Actions followed by a source-tree snapshot copy; the upstream `.git` directory is intentionally not copied.
- Files retained in addition to upstream: this project log, the bootstrap workflow, and upstream provenance marker files.
- No BUS/TRUCK/custom routing/vehicle-profile functional modification is part of this import.

## 2026-09-03 — stock baseline build preparation

- Read upstream `cardinal-android/AGENTS.md` and `.gitlab-ci.yml` before creating our CI.
- Upstream application is a full Kotlin/Compose navigation app with MapLibre, Ferrostar, Valhalla, settings, routing profiles, saved places, transit, offline areas, TTS and a Rust/UniFFI geocoder.
- Current Android configuration: application ID `com.murena.maps` (`.debug` suffix for debug), compileSdk 37, minSdk 26, targetSdk 36, Java 17, ARM64 and x86_64 architecture flavors.
- Upstream's own CI builds ARM64 debug by installing Rust + Cargo NDK + Android NDK 29.0.14206865 + CMake 3.22.1, running the upstream `hideSecret` task, generating UniFFI bindings, then `assembleArm64Debug`.
- Upstream itself uses Stadia endpoints for its standard Pelias geocoder and Valhalla router. Therefore the repository secret `STADIA_API_KEY` will be passed to the unmodified upstream `hideSecret` task only to reproduce normal Murena functionality. This is **not** the postponed custom BUS/Stadia integration.
- No secret value is committed to the repository or printed intentionally.

## 2026-09-03 — baseline build experiment 1: GitHub SDK image too old

- GitHub Actions run: `33773704410`; job: `100710040047`.
- Checkout including Git LFS succeeded; Java 17 setup succeeded.
- Failure occurred before Rust, secret generation, Gradle configuration or application compilation.
- Exact failure: the generic GitHub Ubuntu Android command-line tools could not find `platforms;android-37` (`Warning: Failed to find package 'platforms;android-37'`).
- This is an Android SDK runner-environment mismatch, not a Murena source-code failure.
- Upstream Murena avoids this mismatch by building inside `cimg/android:2026.07.1-ndk`.
- Next experiment: run our baseline job inside the same `cimg/android:2026.07.1-ndk` container and follow the upstream GitLab build steps directly instead of reconstructing the SDK environment manually.

## 2026-09-03 — baseline build experiment 2: our diagnostic used the wrong API-37 directory name

- GitHub Actions run: `33773986540`; job: `100710980348`.
- The exact upstream container `cimg/android:2026.07.1-ndk` downloaded and started successfully.
- Checkout succeeded and Git LFS assets were pulled successfully.
- Container Android SDK reported `sdkmanager 20.0` and had platforms through API 37.
- Important detail: this image stores the API-37 platform directory as `platforms/android-37.0`, not `platforms/android-37`.
- Our extra diagnostic command `test -d "$ANDROID_HOME/platforms/android-37"` therefore failed even though API 37 is present. The workflow stopped before secret generation, Rust/UniFFI generation and application compilation.
- This failure was introduced by our CI diagnostic, not by Murena source code.
- The container also reports `NDK_STABLE_VERSION=29.0.14206865`, matching upstream Murena's requested NDK.
- Fix: remove our hard-coded platform-directory assertion and follow Murena's own `.gitlab-ci.yml` build sequence without inventing additional SDK-name assumptions.

## 2026-09-03 — verification after the earlier bootstrap race

- A second AI inspected this repository while the long bootstrap/import work was still in progress and correctly noticed that one earlier import push had lost a non-fast-forward race.
- That observation described the intermediate state, not the final repository state.
- The final successful bootstrap run `33773338841`, job `100708822209`, subsequently imported the full source snapshot.
- The successful bootstrap log records `1252 files changed, 150014 insertions(+)`.
- Git LFS then uploaded all three tracked LFS objects successfully: `3/3`, approximately 79 MB total.
- The final push succeeded: `61d3514..0adf78d HEAD -> main`.
- The current repository tree contains the complete `cardinal-android`, `cardinal-geocoder`, Cargo workspace, assets/resources and other upstream source trees expected from the pinned Murena snapshot.
- Conclusion: the race was real in an earlier failed attempt, but it did **not** leave the current Murena source snapshot incomplete.

## 2026-09-03 — baseline build experiment 3: SUCCESS, full clean Murena ARM64 debug APK

- GitHub Actions run: `33774447697`; job: `100712528955`.
- Build commit: `1b5cbefb81b8cf5055fc769028bdc310b9aaf9e5`.
- Result: **SUCCESS**.
- The build used the same `cimg/android:2026.07.1-ndk` family and upstream build sequence as Murena's GitLab CI.
- All important stages completed successfully: checkout, Android/Rust environment, upstream provenance verification, upstream `hideSecret`, Rust/UniFFI generation, `assembleArm64Debug`, APK metadata generation and artifact upload.
- No BUS profile, TRUCK profile, vehicle dimensions, branding patch or custom routing code was added to the application for this baseline.
- GitHub artifact ID: `9901556940`.
- Artifact name: `Kapijuja-Murena-stock-arm64-debug`.
- APK filename inside the artifact: `app-arm64-debug.apk`.
- Downloaded test copy name: `Kapijuja-Murena-stock-arm64-debug.apk`.
- Exact APK size: `117,596,817` bytes (about 112 MiB / 118 MB decimal).
- SHA-256: `2f51565267081910e3d2720e89570041dddf623b0bed8e308a646524e306362f`.
- This is the ARM64 build intended for modern ARM64 Android phones/tablets, including the current Samsung test device.

### Android compatibility note

- `compileSdk = 37` only selects the Android API used to compile the source; it does **not** mean the APK requires Android 37.
- `targetSdk = 36` controls the behavior/policy target for modern Android; it is not the minimum supported Android version.
- `minSdk = 26` is the actual lower installation boundary of this clean upstream build: Android 8.0 / API 26 and newer.
- There is no generic Gradle flag that truthfully makes an arbitrary modern Android app run on “all Android versions”. Supporting anything below API 26 would require lowering `minSdk` and then auditing Kotlin/Compose, libraries, native code and runtime API usage. That would be a separate compatibility port rather than a clean upstream baseline.
- Therefore the baseline intentionally keeps upstream `minSdk 26`, `targetSdk 36`, `compileSdk 37`. Do not lower them before the clean application has been tested.
- The previous separate MapLibre demo failure caused by a deprecated `targetSdk 23` is not applicable here: this Murena baseline already targets API 36.

## NEXT CHAT / NEXT GATE — what to do after installing the clean baseline

Do not begin BUS/TRUCK modifications until the following baseline test is completed and observations are recorded.

1. Install `Kapijuja-Murena-stock-arm64-debug.apk` on the target ARM64 Android device.
2. Confirm the app launches and the map renders.
3. Check location permission/current-position behavior.
4. Inspect the existing Settings screen and every routing-profile/profile-editor screen that is visible in the stock app.
5. Test normal address/place search and saved places/favorites if available.
6. Build an ordinary stock driving route without changing the profile model.
7. Start turn-by-turn navigation and verify map following, maneuver banners, rerouting and TTS/voice.
8. Note which existing controls/profile choices are already exposed by Murena. Screenshots are useful because they determine where BUS/TRUCK and vehicle parameters can later be inserted at the highest/safest UI/request layer.
9. If installation or launch fails, collect a fresh Android bugreport immediately after reproducing the problem and diagnose the exact `PackageManager`/runtime failure before changing source code.
10. Only after the clean baseline works, inspect the existing routing-profile model and Valhalla request builder to design BUS/TRUCK support. Prefer a high-level profile/request change; do not modify MapLibre/Ferrostar/native internals unless evidence proves it is required.

### Later BUS phase, deliberately not started yet

- Determine how stock Murena represents `driving`, walking, cycling and other costing profiles.
- Add BUS/TRUCK only where those existing profiles naturally live.
- For BUS, use true Valhalla `bus` costing rather than renaming `auto`/truck.
- Add weight/height/width/length only after verifying the exact Valhalla/Stadia fields supported by the backend.
- Preserve the clean baseline commit and APK as a permanent regression reference.

## 2026-09-03 — DEVICE BASELINE PASSED; stock navigation is a stable reference

The clean Murena/Cardinal ARM64 debug build has now been installed and exercised on the real Samsung test device.

### Confirmed working on device

- App installs and launches normally.
- No crashes or visible stability problems were observed during the test session.
- Map rendering works.
- Ordinary car routing works well on the user's real test route.
- Turn-by-turn navigation starts and runs.
- Maneuver banner/arrow UI works.
- Spoken TTS/voice guidance works.
- Current position and route display work.
- The stock Routing Profiles UI works and allows creation/editing of profiles.
- A Truck profile can be created from the existing UI.
- Existing truck editor exposes useful vehicle/profile controls, including vehicle length, width, height, weight and other Valhalla truck options.

This state is now the permanent regression baseline. Do not replace large parts of the app or alter low-level MapLibre/Ferrostar/native code merely to add BUS/TRUCK behavior. Make the smallest possible high-level routing/profile changes and compare every later build against this stable state.

### User-observed functional gap

At the known Poznań restriction/control location, selecting the newly-created Truck profile did not produce the expected truck-specific path. The route behaved essentially like ordinary car routing. The user also observed that a failed route attempt could show the message `We received route data we couldn’t read. Please try again.`

### Exact source-level Truck selection bug found

`cardinal-android/app/src/main/java/earth/maps/cardinal/ui/directions/DirectionsViewModel.kt`

The current `getFerrostarWrapper()` selector explicitly handles only:

- `RoutingMode.AUTO` -> `ferrostarWrapperRepository.driving`
- `RoutingMode.PEDESTRIAN` -> walking
- `RoutingMode.BICYCLE` -> cycling

and then uses:

- `else -> ferrostarWrapperRepository.driving`

Therefore `RoutingMode.TRUCK` falls through to the **driving/car wrapper** instead of `ferrostarWrapperRepository.truck`. This is a high-level bug and is the first thing to fix in the next functional build. The truck wrapper already exists elsewhere in the repository, so do not redesign the navigation engine.

### Truck profile/options plumbing is already present

Relevant existing files:

- `cardinal-android/app/src/main/java/earth/maps/cardinal/data/RoutingMode.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/data/room/RoutingProfile.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/data/room/RoutingProfileRepository.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/routing/RoutingOptions.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/routing/FerrostarWrapper.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/routing/FerrostarWrapperRepository.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/routing/ValhallaCostingProfile.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/ui/settings/ProfileEditorScreen.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/ui/settings/ProfileEditorViewModel.kt`

`TruckRoutingOptions` already contains truck-specific fields such as length, width, height, weight (metric tons), axle count, hazmat and truck-route preference. The profile repository serializes/deserializes Truck options rather than merely relabeling a car profile.

### Traffic-profile caveat discovered from bugreport/source inspection

The routing layer also contains special traffic profile names:

- car traffic family: `auto_traffic...`
- truck traffic family: `truck_traffic...`

`FerrostarWrapper` enables traffic automatically for both AUTO and TRUCK, and `ValhallaCostingProfile` can therefore request `truck_traffic` instead of plain `truck`.

A device bugreport from the Truck test showed a Truck-related request carrying real vehicle values (approximately 12.93 m length, 2.50 m width, 3.83 m height and 15.23 metric tons) and an HTTP 400 response on a `truck_traffic` path. This demonstrates that at least one routing path preserves truck dimensions, but the traffic-profile compatibility/fallback must be retested after the primary Directions wrapper-selection bug is fixed.

Do not combine these two observations into one guessed fix. First make Directions use the actual truck wrapper. Then capture a fresh request/log and determine whether Stadia accepts `truck_traffic`; if not, use/fallback to ordinary Valhalla `truck` while preserving truck `costing_options`.

## 2026-09-03 — offline download architecture findings; no country UI patch yet

No application code has been changed for country downloads yet. Source inspection found that the existing offline system is already much more than a simple visual-map cache.

### Existing stock behavior

`OfflineAreasScreen.kt` currently creates a download from the **current visible map viewport**. The UI refuses to start a new area download while zoomed out below level 8.

`OfflineAreasViewModel.kt` sends an arbitrary `BoundingBox` to `TileDownloadForegroundService` with configured offline zoom levels 5 through 14.

The offline area status model explicitly has separate stages:

1. `DOWNLOADING_BASEMAP`
2. `DOWNLOADING_VALHALLA`
3. `PROCESSING_GEOCODER`
4. `COMPLETED`

Therefore a completed offline area is intended to contain data for:

- map rendering,
- local Valhalla routing,
- offline geocoding/search processing,

not only visible map tiles.

### Country-level implementation direction

The repository already contains:

- `cardinal-android/app/src/main/data/country_bounds.json`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/data/CountryCoordinateResolver.kt`

This makes the requested UI concept **Europe -> Country -> Download** feasible without redesigning the downloader: the country selector can resolve a country to a bounding box and feed that box into the existing download service.

However, before building this UI, inspect the actual country bounds dataset, tile-count/storage implications (especially Germany/France/Italy/Spain/Poland), provider/server limits, and whether downloading a simple rectangular country bounding box is acceptable or whether exact country-shaped tile selection is needed. The user's desired UX is country-level, not administrative regions/voivodeships/Länder.

### Files to inspect next for offline work

- `cardinal-android/app/src/main/java/earth/maps/cardinal/ui/home/OfflineAreasScreen.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/ui/home/OfflineAreasViewModel.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/data/room/OfflineArea.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/tileserver/TileDownloadForegroundService.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/tileserver/TileDownloadManager.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/tileserver/ValhallaTileUtils.kt`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/routing/OfflineRoutingService.kt`
- `cardinal-android/app/src/main/data/country_bounds.json`
- `cardinal-android/app/src/main/java/earth/maps/cardinal/data/CountryCoordinateResolver.kt`

The user reported that the offline area he tried did not successfully calculate the desired offline route, so the next chat must distinguish: (a) basemap tiles present, (b) Valhalla routing tiles present and recognized, and (c) geocoder data present. Do not assume that seeing the offline map proves offline routing coverage.

## NEXT IMPLEMENTATION ORDER AFTER THIS HANDOFF

1. Preserve the current stable stock baseline and do not change it destructively.
2. Read this project log, root `README.md`, and especially `cardinal-android/AGENTS.md` before coding.
3. Fix the high-level `DirectionsViewModel.getFerrostarWrapper()` mapping so `RoutingMode.TRUCK` uses `ferrostarWrapperRepository.truck`.
4. Build a minimal Truck-fix APK and retest the known Poznań restriction point with the existing Truck profile and real dimensions. Do not add BUS in the same build; isolate the Truck fix first.
5. Capture fresh routing logs/bugreport if needed and resolve the `truck_traffic` versus plain `truck` compatibility only if the corrected Truck wrapper still fails.
6. Separately design the offline selector as `Europe -> country` using the existing country bounds + downloader architecture; do not subdivide into voivodeships/Länder unless forced by technical limits. First calculate practical storage/download sizes and verify that Valhalla tiles are downloaded for the same country coverage.
7. Only after Truck is proven, add a new true BUS routing mode at the same high-level profile/request layer. Do not implement BUS as a renamed Truck or Car.
8. Before coding BUS, verify from current Valhalla/Stadia/Ferrostar source/docs exactly which BUS costing/profile and vehicle fields are supported by the hosted endpoint.
9. User requirement for BUS UI: normal tourist/coach BUS by default plus a simple `Line Bus` switch. `Line Bus` means city/route-service bus semantics that may use bus-only/bus-lane access when supported; switch off means tourist coach semantics that must not automatically inherit city line-bus privileges. Verify backend semantics rather than inventing parameter names.
10. Add BUS support through the existing architecture: `RoutingMode`, routing options, profile repository serialization, Ferrostar wrapper repository, Valhalla costing profile, Directions selector, and existing Profile Editor UI. Preserve working navigation UI, voice, arrows, rerouting and current map behavior.


## 2026-09-03 — online professional vehicle routing patch

- Offline routing/download code is deliberately untouched in this stage.
- Fixed Directions wrapper selection so `TRUCK` uses the already-existing dedicated Truck Ferrostar wrapper instead of falling through to Driving.
- Added driver-controlled `BUS` routing mode, profile persistence, editor support, dedicated Ferrostar wrapper and turn-by-turn wrapper selection.
- `BusRoutingOptions` exposes the shared motor-vehicle controls plus physical length/width/height/weight. Truck-only Hazmat / truck-route controls are not copied into Bus.
- Added `Line Bus` switch, default OFF.
- `Line Bus = ON` selects real Valhalla `bus` costing (bus/PSV access semantics).
- `Line Bus = OFF` selects Valhalla `auto` access semantics while retaining the configured coach dimensions/weight; this prevents a tourist coach from automatically gaining bus-only/busway access in the stock hosted Valhalla model.
- `lineBus` is application policy only and is removed from outgoing Valhalla `costing_options`; no invented backend JSON field is used.
- Current upstream Valhalla OpenAPI documents `bus` as using `AutoCostingOptions`, which include height, width, length and weight.
- For this first correctness build, live traffic aliases remain enabled only for normal Driving. Truck and Bus use plain `truck` / `bus` or coach `auto` profiles to isolate vehicle/access semantics from the previously observed `truck_traffic` HTTP 400 path.


## 2026-09-04 — current functional main, heavy-vehicle routing, traffic, access fallback and country UI

Current canonical main after the critical pass:

- Commit: `de51ac7fd93083914c316510cd449096e4022ae2`
- GitHub Actions ARM64 run: `33834518093` — **SUCCESS**
- Artifact: `Kapijuja-Murena-Final-arm64-debug`, ID `9923120947`
- User report on the current device build: stable in normal use and not crashing.

### Implemented since the earlier online-vehicle patch

- Stable, horizontally scrollable large routing-mode icons.
- Last selected saved profile remembered per routing mode.
- Synthetic Default Profile hidden when saved profiles exist; built-in defaults used silently when none exist.
- Truck wrapper selection fixed in Directions.
- BUS mode integrated into persistence, editor, Ferrostar wrapper and navigation.
- Traffic enabled for Auto/Truck/Bus with premium Stadia profile support when the configured key is entitled.
- Same user-configured Valhalla API key path is used by normal and traffic routing; there is no separate traffic key.
- User key is no longer required to be embedded in the APK. Advanced Settings has Reset API settings to defaults.
- Stadia strict integer normalization added for second-based penalty/cost JSON fields.
- Heavy defaults:
  - Truck 16.5 x 2.5 x 4.0 m, 45 t, 3 axles.
  - Bus 13.5 x 2.5 x 4.0 m, 18 t, 3 axles.
- Narrow-road avoidance strengthened through valid Valhalla preferences/penalties without inventing a lane-count restriction.
- Truck legal-edge correlation radius remains 100 m.
- Bus/native Bus traffic/coach-Auto heavy routing uses 600 m legal-edge correlation; normal passenger Auto does not.
- A separately stored/rendered cautionary final approach is available when strict heavy routing stops short of the requested point.
- Truck final access fallback remains conservative and short.
- Bus final approach can extend to 600 m routed distance and tries three tiers:
  1. access-only relaxation, retaining length/width/height/weight;
  2. weight relaxed, retaining length/width/height;
  3. last-resort weight+length relaxed, retaining width+height.
- The fallback never globally turns on `ignore_restrictions`; one-way and closure handling remain enabled.
- Preview/navigation uses different dashed colors/styles for fallback risk level and presents a driver warning.
- Active navigation returns to Guidance after about 15 seconds of no screen interaction.
- CI debug signing is stable and versionCode increases across builds to support in-place test APK updates.
- Europe -> country download UI is implemented on top of the existing basemap + Valhalla + offline-geocoder pipeline.

### Important Bus switch follow-up for the next chat

The visible switch is now **Car**:
- Car ON = Auto/passenger-car access semantics with Bus dimensions.
- Car OFF = native Valhalla Bus/PSV access semantics.

The persisted compatibility field is still `lineBus`, and the UI uses `Car = !lineBus`.

Current `BusRoutingOptions` still defaults `lineBus=false`, which means the new/in-memory default currently implies **Car ON**. The user explicitly wants the future default Bus to be a real Bus, therefore **Car must be OFF by default**.

Next chat should:
1. Change only the default for new/built-in Bus options to native Bus / Car OFF.
2. Preserve already-saved profile values; do not silently invert existing profiles.
3. Add explanatory UI copy near the switch, e.g. “Car access rules with bus dimensions”.
4. Keep internal `lineBus` compatibility unless a deliberate data migration is implemented.

### Offline limitation still open

Country downloads currently use country bounding rectangles and the stock offline storage remains area-oriented. Neighboring-country overlap is therefore not yet a finished physical dedup solution. Do not document exact-border or shared-payload dedup as complete until the storage/reference model is changed and tested.
