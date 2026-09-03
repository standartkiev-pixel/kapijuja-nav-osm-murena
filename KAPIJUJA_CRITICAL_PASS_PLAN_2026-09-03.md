# Kapijuja Nav critical pass — 2026-09-03

This pass deliberately groups only changes needed for immediate vehicle-routing and offline-country testing.

Planned changes:

1. Routing profile selection
   - If saved profiles exist for the selected routing mode, hide the synthetic Default Profile choice.
   - If no saved profile exists, silently use built-in defaults.
   - Persist the last selected saved profile as the default for its mode.
   - Preserve support for multiple saved profiles per mode.

2. Routing mode selector UX
   - Keep routing mode buttons large and horizontally scrollable instead of shrinking all icons to fit one row.

3. Bus profile semantics
   - Rename the visible Line Bus switch to Car.
   - Car OFF = native Valhalla bus costing.
   - Car ON = auto costing with bus dimensions/weight.
   - Preserve stored compatibility by reusing the existing persisted internal field and inverting only UI meaning where possible.

4. Restricted-destination last mile
   - Keep strict Truck/Bus routing for the main route.
   - Do not globally enable ignore_access.
   - Add a distinct last-mile fallback for a destination that strict commercial routing cannot reach.
   - Preserve dimensional/weight restrictions while softening only HGV/access restrictions for the fallback segment.
   - Expose the fallback segment distinctly so UI can render it dashed and warn the driver that access must be verified.

5. Offline country downloads
   - Replace current-viewport-only UX with Europe -> country downloads while preserving existing viewport download internally for diagnostics if useful.
   - One logical country download includes basemap + Valhalla routing tiles + offline geocoder processing.
   - Use coarse country geometry/bounds with a routing border margin.
   - Avoid duplicate storage for overlapping country downloads by reusing already-downloaded basemap/Valhalla tile payloads and maintaining area references separately.
   - Start with Europe; other continents are later work.

6. Offline Valhalla initialization
   - Write/verify valhalla.json before constructing ValhallaActor.
   - Ensure the local actor can be refreshed after country graph downloads.

7. Update-install regression
   - Keep package/application id stable.
   - Verify versionCode progression and signing identity in the build pipeline so APK can update an installed Kapijuja build instead of requiring uninstall.

Deferred from this pass:
- national live-closure/NAP adapters;
- live traffic ingestion for truck/bus;
- compressed Valhalla tile-extract packaging;
- detailed country polygons and continent expansion beyond Europe.
