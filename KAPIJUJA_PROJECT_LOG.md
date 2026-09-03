# Kapijuja Nav OSM Murena — project log

This repository is the clean product line for Kapijuja Nav based on the upstream Murena Maps/Cardinal application.

## Rules for this repository

1. Start from a clean upstream Murena Maps/Cardinal source baseline.
2. First goal: compile and test the upstream application essentially unchanged.
3. Do **not** add BUS/TRUCK profiles or Stadia/Valhalla modifications until the clean baseline APK has been built and tested on the target device.
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
- `STADIA_API_KEY` integration.
- Branding changes beyond what is strictly required to build.
- Feature changes of any kind.
