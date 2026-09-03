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
