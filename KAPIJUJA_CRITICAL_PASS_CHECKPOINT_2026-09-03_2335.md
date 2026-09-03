# Kapijuja critical pass checkpoint — 2026-09-03 23:35 CEST

Canonical repository: `standartkiev-pixel/kapijuja-nav-osm-murena`
Working branch: `critical-pass-2026-09-03`
Do not treat older Kapijuja/TomTom repositories as canonical.

## Proven traffic/key path

There is one Valhalla/Stadia configuration in `AppPreferences` / `AppPreferenceRepository` and the existing Advanced Settings UI already exposes Valhalla Base URL and Valhalla API Key.

`ValhallaRoutingService` reads `appPreferenceRepository.valhallaApiConfig.value` for every online routing request and appends the configured API key to the configured Valhalla URL. No second traffic-specific key setting is needed.

The real GitHub Actions secret `STADIA_API_KEY` was safely probed without printing the secret. Premium traffic access is confirmed:

- `auto_traffic_premium` — HTTP 200
- `bus_traffic_premium` — HTTP 200
- `truck_traffic_premium` on a known routable highway segment — HTTP 200, `weight_name=truck`

Therefore the current key is entitled to all three premium traffic profile families. A long 45 t Truck test returning `NoRoute` is a routing/endpoint feasibility issue, not a missing traffic entitlement.

## Traffic and closures behavior

`RoutingMode.AUTO`, `TRUCK`, and `BUS` support traffic in `FerrostarWrapper`.

Online profiles:
- Auto -> `auto_traffic_premium`
- Truck -> `truck_traffic_premium`
- Tourist Coach (`BUS`, Line Bus OFF) -> `auto_traffic_premium` with coach physical dimensions
- Line Bus (`BUS`, Line Bus ON) -> `bus_traffic_premium`

Traffic requests include `date_time.type=0` (depart now). `ignore_closures=false` is part of built-in heavy-vehicle defaults, so live/static closure restrictions are not intentionally bypassed.

If a traffic profile is rejected with an entitlement/profile-compatible 400/402/403/422, route calculation can retry without traffic rather than completely failing. Network failures are not disguised as profile fallback.

## Strict Stadia JSON integer boundary

Real Stadia probing showed that second-based cost/penalty fields such as `service_penalty` are strict JSON i32 fields. Sending `300.0` is rejected before routing.

`RoutingOptions.toValhallaOptionsJson()` now normalizes common second-based costs/penalties to JSON integers only at the API boundary while keeping Double values in profile/UI state.

## Heavy vehicle built-in defaults

Truck baseline:
- length 16.5 m
- width 2.5 m
- height 4.0 m
- weight 45 t
- axles 3
- `use_truck_route=1.0`
- `use_highways=0.8`
- `use_living_streets=0.0`
- `use_tracks=0.0`
- avoid unpaved
- `service_penalty=300 s`
- `service_factor=5`
- `low_class_penalty=300 s`
- `closure_factor=10`
- do not ignore closures/restrictions/one-ways/access

The 16.5 m built-in length represents a standard articulated tractor/semitrailer baseline; it is user-editable.

Tourist Coach baseline:
- length 13.5 m
- width 2.5 m
- height 4.0 m
- weight 18 t
- axles 3
- Line Bus OFF by default
- `use_highways=0.8`
- `use_living_streets=0.0`
- `use_tracks=0.0`
- avoid unpaved
- `service_penalty=300 s`
- `service_factor=5`
- `alley_factor=10`
- `closure_factor=10`
- do not ignore closures/restrictions/one-ways/access

Bus axle count is persisted/displayed in the profile but currently removed from Valhalla Auto/Bus costing JSON because that hosted costing schema does not accept Truck-style `axle_count`.

## Narrow residential roads

Do not invent a fake `minimum lane count` Valhalla option. Current Valhalla does not expose a clean standard `lanes >= N` routing constraint for these costings.

Truck has a genuine `low_class_penalty`, which penalizes residential/service/unclassified transitions. Truck also has `use_truck_route` for the designated HGV network.

Tourist Coach cannot safely receive Truck-only `low_class_penalty` without lying about the costing. It instead uses valid Auto/Bus options: service penalty/factor, alley factor, living-street and track avoidance, maneuver penalty, highway preference and physical dimensions.

If real-device testing still selects a normal `highway=residential` road that is physically unsuitable for a 13.5 m coach, inspect the exact OSM tags for that road and then implement a high-level coach-specific preference/last-mile mechanism. Do not convert the coach back into Truck.

Parked cars and swept-path intersection geometry are not inherently modeled by Valhalla unless represented by map restrictions or live incidents/closures.

## Truck traffic waypoint correlation fix

`ValhallaRoutingService.prepareVehicleRouteRequest()` previously added the truck waypoint radius only when `costing == "truck"`.

Traffic Truck uses `truck_traffic_premium`, so that safeguard was silently skipped. The current branch now recognizes the whole typed Truck traffic family and applies the same waypoint candidate radius to `truck`, `truck_traffic`, and `truck_traffic_*` profiles without relaxing HGV restrictions.

## Europe whole-country downloads

A country-selection page is implemented at the high UI layer:

`Offline areas -> Europe -> country -> confirmation -> Download`

The selected country is handed to the existing `OfflineAreasViewModel.startDownload(boundingBox, countryName)` pipeline, preserving the stock three-stage offline system:
1. basemap
2. Valhalla routing tiles
3. offline geocoder processing

No subdivisions into voivodeships/Länder/regions are exposed. The old arbitrary viewport-area downloader remains as a secondary tool.

Current first implementation uses practical European country bounding rectangles. Future optimization remains:
- physical tile dedup when neighboring country downloads overlap
- polygon/country masking if bounding rectangles prove too wasteful
- re-check very large/non-contiguous countries before production rollout

## Profile UX already implemented

- Routing mode icons are large and horizontally scrollable instead of being compressed into one row.
- Last selected custom profile is remembered per routing mode.
- If a mode has custom profiles, the fake/synthetic Default profile button is not shown.
- If no custom profile exists, built-in options are used.

## Debug install/update chain

The CI debug app now uses a stable dedicated public debug keystore and monotonically increasing GitHub Actions `versionCode`. Release signing remains separate.

A one-time clean install may be needed to migrate away from an APK signed with the older ephemeral CI debug key. After that, later test APKs using the stable debug key should update in place.

## Immediate next gates

1. Complete a fresh ARM64 debug compile from the current source state.
2. Run/check targeted unit tests for heavy defaults, integer API normalization and Truck traffic profile recognition.
3. If build fails, fix compilation before adding more routing behavior.
4. Device-test Truck and Tourist Coach against a known narrow-residential-vs-wide-arterial case.
5. If Coach still takes an unsuitable ordinary residential street, inspect its OSM tags and implement the narrowest safe upper-layer large-coach preference/last-mile exception.
6. Do not merge to `main` until the branch build is green and the device-test candidate is ready.
