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
- Next experiment: reproduce the upstream ARM64 debug build in GitHub Actions without changing application source code.
