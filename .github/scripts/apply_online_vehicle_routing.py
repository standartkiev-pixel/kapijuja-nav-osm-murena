from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(
            f"{path}: expected exactly one match, found {count}\n--- OLD ---\n{old[:500]}"
        )
    p.write_text(text.replace(old, new, 1))
    print(f"patched {path}")


# 1) Vehicle routing mode: add a real driver-controlled BUS mode.
replace_once(
    "cardinal-android/app/src/main/java/earth/maps/cardinal/data/RoutingMode.kt",
    '    AUTO("auto", "Driving", drawable.mode_car),\n'
    '    TRUCK("truck", "Truck", drawable.mode_truck),\n'
    "    MOTOR_SCOOTER",
    '    AUTO("auto", "Driving", drawable.mode_car),\n'
    '    TRUCK("truck", "Truck", drawable.mode_truck),\n'
    '    BUS("bus", "Bus", drawable.ic_bus_railway),\n'
    "    MOTOR_SCOOTER",
)

# 2) Bus options. Current Valhalla AutoCost/BusCost supports physical dimensions/weight.
# lineBus is persisted in the app profile, but is never sent as a Valhalla option.
options_path = "cardinal-android/app/src/main/java/earth/maps/cardinal/routing/RoutingOptions.kt"
replace_once(
    options_path,
    '        val options = gson.toJsonTree(this@RoutingOptions).asJsonObject.apply {\n'
    '            remove("costing_type")\n'
    "        }",
    '        val options = gson.toJsonTree(this@RoutingOptions).asJsonObject.apply {\n'
    '            remove("costing_type")\n'
    "            // App-only selector: it chooses coach (auto access) vs line bus (bus access).\n"
    "            // It is not a Valhalla costing option and must never be sent to the API.\n"
    '            remove("line_bus")\n'
    "        }",
)

bus_options = '''/**
 * Routing options for a driver-controlled bus/coach.
 *
 * Current Valhalla BusCost uses AutoCostingOptions, including physical vehicle
 * height, width, length and weight. lineBus is an app-side selector:
 * false = tourist coach (auto access semantics with bus dimensions),
 * true = line/service bus (Valhalla bus access semantics).
 */
data class BusRoutingOptions(
    override val costingType: String = COSTING_TYPE_BUS,

    // Basic motor-vehicle options
    override val maneuverPenalty: Double? = null,
    override val gateCost: Double? = DEFAULT_GATE_COST,
    override val tollBoothCost: Double? = DEFAULT_TOLL_BOOTH_COST,
    override val privateAccessPenalty: Double? = null,
    override val useHighways: Double? = null,
    override val useTolls: Double? = null,
    override val useLivingStreets: Double? = null,
    override val useTracks: Double? = null,
    override val ignoreClosures: Boolean? = null,
    override val ignoreRestrictions: Boolean? = null,
    override val ignoreOneWays: Boolean? = null,
    override val ignoreAccess: Boolean? = null,
    override val excludeUnpaved: Boolean? = null,
    override val excludeCashOnlyTolls: Boolean? = null,

    // Physical vehicle restrictions supported by Valhalla AutoCost/BusCost
    val length: Double? = null, // meters
    val width: Double? = null,
    val height: Double? = null,
    val weight: Double? = null, // metric tons

    // App-only policy selector. Not serialized into Valhalla costing_options.
    val lineBus: Boolean = false
) : RoutingOptions(), AutoOptions {
    companion object {
        const val COSTING_TYPE_BUS = "bus"
        const val DEFAULT_GATE_COST = 45.0
        const val DEFAULT_TOLL_BOOTH_COST = 30.0
    }
}

'''
replace_once(
    options_path,
    "/**\n * Routing options for motor scooter mode.\n */",
    bus_options + "/**\n * Routing options for motor scooter mode.\n */",
)

# 3) Profile persistence/defaults/deserialization.
profile_repo = (
    "cardinal-android/app/src/main/java/earth/maps/cardinal/data/room/"
    "RoutingProfileRepository.kt"
)
replace_once(
    profile_repo,
    "import earth.maps.cardinal.routing.AutoRoutingOptions\n",
    "import earth.maps.cardinal.routing.AutoRoutingOptions\n"
    "import earth.maps.cardinal.routing.BusRoutingOptions\n",
)
replace_once(
    profile_repo,
    "            RoutingMode.AUTO -> AutoRoutingOptions()\n"
    "            RoutingMode.TRUCK -> TruckRoutingOptions()\n"
    "            RoutingMode.MOTOR_SCOOTER",
    "            RoutingMode.AUTO -> AutoRoutingOptions()\n"
    "            RoutingMode.TRUCK -> TruckRoutingOptions()\n"
    "            RoutingMode.BUS -> BusRoutingOptions()\n"
    "            RoutingMode.MOTOR_SCOOTER",
)
replace_once(
    profile_repo,
    '                "auto" -> gson.fromJson(optionsJson, AutoRoutingOptions::class.java)\n'
    '                "truck" -> gson.fromJson(optionsJson, TruckRoutingOptions::class.java)\n'
    '                "motor_scooter"',
    '                "auto" -> gson.fromJson(optionsJson, AutoRoutingOptions::class.java)\n'
    '                "truck" -> gson.fromJson(optionsJson, TruckRoutingOptions::class.java)\n'
    '                "bus" -> gson.fromJson(optionsJson, BusRoutingOptions::class.java)\n'
    '                "motor_scooter"',
)

# 4) Profile editor ViewModel.
editor_vm = (
    "cardinal-android/app/src/main/java/earth/maps/cardinal/ui/settings/"
    "ProfileEditorViewModel.kt"
)
replace_once(
    editor_vm,
    "import earth.maps.cardinal.routing.AutoRoutingOptions\n",
    "import earth.maps.cardinal.routing.AutoRoutingOptions\n"
    "import earth.maps.cardinal.routing.BusRoutingOptions\n",
)
replace_once(
    editor_vm,
    "            RoutingMode.AUTO -> AutoRoutingOptions()\n"
    "            RoutingMode.TRUCK -> TruckRoutingOptions()\n"
    "            RoutingMode.MOTOR_SCOOTER",
    "            RoutingMode.AUTO -> AutoRoutingOptions()\n"
    "            RoutingMode.TRUCK -> TruckRoutingOptions()\n"
    "            RoutingMode.BUS -> BusRoutingOptions()\n"
    "            RoutingMode.MOTOR_SCOOTER",
)

# 5) Existing profile editor UI: common vehicle settings + dimensions + Line Bus switch.
editor_ui = (
    "cardinal-android/app/src/main/java/earth/maps/cardinal/ui/settings/"
    "ProfileEditorScreen.kt"
)
replace_once(
    editor_ui,
    "import earth.maps.cardinal.routing.AutoRoutingOptions\n",
    "import earth.maps.cardinal.routing.AutoRoutingOptions\n"
    "import earth.maps.cardinal.routing.BusRoutingOptions\n",
)

bus_editor = '''@Composable
private fun BusOptionsEditor(
    options: BusRoutingOptions,
    onOptionsChanged: (BusRoutingOptions) -> Unit
) {
    CommonAutoOptionsEditor(
        options = options,
        onUseHighwaysChanged = { value -> onOptionsChanged(options.copy(useHighways = value)) },
        onUseTollsChanged = { value -> onOptionsChanged(options.copy(useTolls = value)) },
        onUseLivingStreetsChanged = { value -> onOptionsChanged(options.copy(useLivingStreets = value)) },
        onUseTracksChanged = { value -> onOptionsChanged(options.copy(useTracks = value)) },
        onExcludeUnpavedChanged = { value -> onOptionsChanged(options.copy(excludeUnpaved = value)) },
        onExcludeCashOnlyTollsChanged = { value ->
            onOptionsChanged(options.copy(excludeCashOnlyTolls = value))
        },
        onManeuverPenaltyChanged = { value -> onOptionsChanged(options.copy(maneuverPenalty = value)) },
        onGateCostChanged = { value -> onOptionsChanged(options.copy(gateCost = value)) },
        onPrivateAccessPenaltyChanged = { value ->
            onOptionsChanged(options.copy(privateAccessPenalty = value))
        },
        onIgnoreClosuresChanged = { value -> onOptionsChanged(options.copy(ignoreClosures = value)) },
        onIgnoreRestrictionsChanged = { value -> onOptionsChanged(options.copy(ignoreRestrictions = value)) },
        onIgnoreOneWaysChanged = { value -> onOptionsChanged(options.copy(ignoreOneWays = value)) },
        onIgnoreAccessChanged = { value -> onOptionsChanged(options.copy(ignoreAccess = value)) }
    )

    OptionsSection("Vehicle Dimensions") {
        SliderOption(
            "Length (m)",
            options.length,
            1f..50f,
            valueFormatter = { it.format(1) }) { value ->
            onOptionsChanged(options.copy(length = value))
        }
        SliderOption(
            "Width (m)",
            options.width,
            1f..5f,
            valueFormatter = { it.format(1) }) { value ->
            onOptionsChanged(options.copy(width = value))
        }
        SliderOption(
            "Height (m)",
            options.height,
            1f..10f,
            valueFormatter = { it.format(1) }) { value ->
            onOptionsChanged(options.copy(height = value))
        }
        SliderOption(
            "Weight (tons)",
            options.weight,
            0.1f..100f,
            valueFormatter = { it.format(1) }) { value ->
            onOptionsChanged(options.copy(weight = value))
        }
    }

    OptionsSection("Bus Type") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Line Bus")
            Switch(
                checked = options.lineBus,
                onCheckedChange = { enabled ->
                    onOptionsChanged(options.copy(lineBus = enabled))
                }
            )
        }
    }
}

'''
replace_once(
    editor_ui,
    "@Composable\nprivate fun MotorScooterOptionsEditor(",
    bus_editor + "@Composable\nprivate fun MotorScooterOptionsEditor(",
)
replace_once(
    editor_ui,
    "            RoutingMode.AUTO -> AutoOptionsEditor(routingOptions as AutoRoutingOptions, onRoutingOptionsChange)\n"
    "            RoutingMode.TRUCK -> TruckOptionsEditor(routingOptions as TruckRoutingOptions, onRoutingOptionsChange)\n"
    "            RoutingMode.MOTOR_SCOOTER",
    "            RoutingMode.AUTO -> AutoOptionsEditor(routingOptions as AutoRoutingOptions, onRoutingOptionsChange)\n"
    "            RoutingMode.TRUCK -> TruckOptionsEditor(routingOptions as TruckRoutingOptions, onRoutingOptionsChange)\n"
    "            RoutingMode.BUS -> BusOptionsEditor(routingOptions as BusRoutingOptions, onRoutingOptionsChange)\n"
    "            RoutingMode.MOTOR_SCOOTER",
)

# 6) Explicit Valhalla bus profile.
costing = (
    "cardinal-android/app/src/main/java/earth/maps/cardinal/routing/"
    "ValhallaCostingProfile.kt"
)
replace_once(
    costing,
    "    object Truck : ValhallaCostingProfile(\n"
    "        routeProviderProfile = TruckRoutingOptions.COSTING_TYPE_TRUCK,\n"
    "        costingOptionsKey = TruckRoutingOptions.COSTING_TYPE_TRUCK,\n"
    "        safeLogProfileClass = TruckRoutingOptions.COSTING_TYPE_TRUCK\n"
    "    )\n\n"
    "    class TruckTraffic",
    "    object Truck : ValhallaCostingProfile(\n"
    "        routeProviderProfile = TruckRoutingOptions.COSTING_TYPE_TRUCK,\n"
    "        costingOptionsKey = TruckRoutingOptions.COSTING_TYPE_TRUCK,\n"
    "        safeLogProfileClass = TruckRoutingOptions.COSTING_TYPE_TRUCK\n"
    "    )\n\n"
    "    object Bus : ValhallaCostingProfile(\n"
    "        routeProviderProfile = BusRoutingOptions.COSTING_TYPE_BUS,\n"
    "        costingOptionsKey = BusRoutingOptions.COSTING_TYPE_BUS,\n"
    "        safeLogProfileClass = BusRoutingOptions.COSTING_TYPE_BUS\n"
    "    )\n\n"
    "    class TruckTraffic",
)
replace_once(
    costing,
    "            routeProviderProfile == Truck.routeProviderProfile -> Truck\n"
    "            routeProviderProfile == MotorScooter.routeProviderProfile",
    "            routeProviderProfile == Truck.routeProviderProfile -> Truck\n"
    "            routeProviderProfile == Bus.routeProviderProfile -> Bus\n"
    "            routeProviderProfile == MotorScooter.routeProviderProfile",
)

# 7) Repository owns a dedicated BUS wrapper exactly like the existing TRUCK wrapper.
wrapper_repo = (
    "cardinal-android/app/src/main/java/earth/maps/cardinal/routing/"
    "FerrostarWrapperRepository.kt"
)
replace_once(
    wrapper_repo,
    "    private var _driving: FerrostarWrapper? = null\n"
    "    private var _truck: FerrostarWrapper? = null\n"
    "    private var _motorScooter",
    "    private var _driving: FerrostarWrapper? = null\n"
    "    private var _truck: FerrostarWrapper? = null\n"
    "    private var _bus: FerrostarWrapper? = null\n"
    "    private var _motorScooter",
)
replace_once(
    wrapper_repo,
    '    val driving: FerrostarWrapper get() = _driving ?: throw IllegalStateException("Driving wrapper not initialized")\n'
    '    val truck: FerrostarWrapper get() = _truck ?: throw IllegalStateException("Truck wrapper not initialized")\n'
    "    val motorScooter",
    '    val driving: FerrostarWrapper get() = _driving ?: throw IllegalStateException("Driving wrapper not initialized")\n'
    '    val truck: FerrostarWrapper get() = _truck ?: throw IllegalStateException("Truck wrapper not initialized")\n'
    '    val bus: FerrostarWrapper get() = _bus ?: throw IllegalStateException("Bus wrapper not initialized")\n'
    "    val motorScooter",
)
replace_once(
    wrapper_repo,
    "        _truck = factory.create(\n"
    "            mode = RoutingMode.TRUCK,\n"
    "            endpoint = endpoint\n"
    "        )\n"
    "        _motorScooter",
    "        _truck = factory.create(\n"
    "            mode = RoutingMode.TRUCK,\n"
    "            endpoint = endpoint\n"
    "        )\n"
    "        _bus = factory.create(\n"
    "            mode = RoutingMode.BUS,\n"
    "            endpoint = endpoint\n"
    "        )\n"
    "        _motorScooter",
)
replace_once(
    wrapper_repo,
    "        RoutingMode.AUTO -> _driving\n"
    "        RoutingMode.TRUCK -> _truck\n"
    "        RoutingMode.MOTOR_SCOOTER",
    "        RoutingMode.AUTO -> _driving\n"
    "        RoutingMode.TRUCK -> _truck\n"
    "        RoutingMode.BUS -> _bus\n"
    "        RoutingMode.MOTOR_SCOOTER",
)

# 8) Route-provider selection. For this correctness build, Truck and Bus are deliberately
# non-traffic to isolate vehicle/access semantics from the observed truck_traffic HTTP 400 path.
wrapper = (
    "cardinal-android/app/src/main/java/earth/maps/cardinal/routing/FerrostarWrapper.kt"
)
replace_once(
    wrapper,
    "    private val currentValhallaCostingProfile: ValhallaCostingProfile\n"
    "        get() = mode.valhallaCostingProfile(isTrafficEnabled)",
    "    private val currentValhallaCostingProfile: ValhallaCostingProfile\n"
    "        get() = mode.valhallaCostingProfile(isTrafficEnabled, previousRouteOptions)",
)
replace_once(
    wrapper,
    "        val costingProfile = mode.valhallaCostingProfile(trafficEnabled)",
    "        val costingProfile = mode.valhallaCostingProfile(trafficEnabled, routingOptions)",
)
replace_once(
    wrapper,
    "fun RoutingMode.supportsTraffic(): Boolean = this == RoutingMode.AUTO || this == RoutingMode.TRUCK\n\n"
    "fun RoutingMode.valhallaCostingProfile(useTraffic: Boolean): ValhallaCostingProfile = when {\n"
    "    useTraffic && this == RoutingMode.AUTO -> ValhallaCostingProfile.AutoTraffic.Premium\n"
    "    useTraffic && this == RoutingMode.TRUCK -> ValhallaCostingProfile.TruckTraffic.Standard\n"
    "    else -> ValhallaCostingProfile.fromRouteProviderProfile(value)\n"
    "}\n\n"
    "fun RoutingMode.valhallaProfile(useTraffic: Boolean): String =\n"
    "    valhallaCostingProfile(useTraffic).routeProviderProfile",
    "fun RoutingMode.supportsTraffic(): Boolean = this == RoutingMode.AUTO\n\n"
    "fun RoutingMode.valhallaCostingProfile(\n"
    "    useTraffic: Boolean,\n"
    "    routingOptions: RoutingOptions? = null\n"
    "): ValhallaCostingProfile = when {\n"
    "    useTraffic && this == RoutingMode.AUTO -> ValhallaCostingProfile.AutoTraffic.Premium\n"
    "    this == RoutingMode.BUS && (routingOptions as? BusRoutingOptions)?.lineBus == true ->\n"
    "        ValhallaCostingProfile.Bus\n"
    "    this == RoutingMode.BUS -> ValhallaCostingProfile.Auto\n"
    "    else -> ValhallaCostingProfile.fromRouteProviderProfile(value)\n"
    "}\n\n"
    "fun RoutingMode.valhallaProfile(\n"
    "    useTraffic: Boolean,\n"
    "    routingOptions: RoutingOptions? = null\n"
    "): String = valhallaCostingProfile(useTraffic, routingOptions).routeProviderProfile",
)

# 9) Directions preview must choose the actual wrapper instead of falling through to car.
directions = (
    "cardinal-android/app/src/main/java/earth/maps/cardinal/ui/directions/"
    "DirectionsViewModel.kt"
)
replace_once(
    directions,
    "    private fun getFerrostarWrapper() = when (selectedRoutingMode) {\n"
    "        RoutingMode.AUTO -> ferrostarWrapperRepository.driving\n"
    "        RoutingMode.PEDESTRIAN -> ferrostarWrapperRepository.walking\n"
    "        RoutingMode.BICYCLE -> ferrostarWrapperRepository.cycling\n"
    "        else -> ferrostarWrapperRepository.driving\n"
    "    }",
    "    private fun getFerrostarWrapper() = when (selectedRoutingMode) {\n"
    "        RoutingMode.AUTO -> ferrostarWrapperRepository.driving\n"
    "        RoutingMode.TRUCK -> ferrostarWrapperRepository.truck\n"
    "        RoutingMode.BUS -> ferrostarWrapperRepository.bus\n"
    "        RoutingMode.MOTOR_SCOOTER -> ferrostarWrapperRepository.motorScooter\n"
    "        RoutingMode.MOTORCYCLE -> ferrostarWrapperRepository.motorcycle\n"
    "        RoutingMode.PEDESTRIAN -> ferrostarWrapperRepository.walking\n"
    "        RoutingMode.BICYCLE -> ferrostarWrapperRepository.cycling\n"
    "        RoutingMode.PUBLIC_TRANSPORT -> ferrostarWrapperRepository.driving\n"
    "    }",
)
replace_once(
    directions,
    "    fun getAvailableRoutingModes() = combine(\n"
    "        routingProfileRepository.getProfilesForMode(RoutingMode.TRUCK),\n"
    "        routingProfileRepository.getProfilesForMode(RoutingMode.MOTOR_SCOOTER),\n"
    "        routingProfileRepository.getProfilesForMode(RoutingMode.MOTORCYCLE)\n"
    "    ) { truckProfiles, motorScooterProfiles, motorcycleProfiles ->",
    "    fun getAvailableRoutingModes() = combine(\n"
    "        routingProfileRepository.getProfilesForMode(RoutingMode.TRUCK),\n"
    "        routingProfileRepository.getProfilesForMode(RoutingMode.BUS),\n"
    "        routingProfileRepository.getProfilesForMode(RoutingMode.MOTOR_SCOOTER),\n"
    "        routingProfileRepository.getProfilesForMode(RoutingMode.MOTORCYCLE)\n"
    "    ) { truckProfiles, busProfiles, motorScooterProfiles, motorcycleProfiles ->",
)
replace_once(
    directions,
    "        if (truckProfiles.isNotEmpty()) {\n"
    "            modes.add(RoutingMode.TRUCK)\n"
    "        }\n"
    "        if (motorScooterProfiles.isNotEmpty()) {",
    "        if (truckProfiles.isNotEmpty()) {\n"
    "            modes.add(RoutingMode.TRUCK)\n"
    "        }\n"
    "        if (busProfiles.isNotEmpty()) {\n"
    "            modes.add(RoutingMode.BUS)\n"
    "        }\n"
    "        if (motorScooterProfiles.isNotEmpty()) {",
)

# 10) Turn-by-turn navigation continues with the BUS wrapper selected during planning.
nav_screen = (
    "cardinal-android/app/src/main/java/earth/maps/cardinal/ui/navigation/"
    "TurnByTurnNavigationScreen.kt"
)
replace_once(
    nav_screen,
    "        RoutingMode.TRUCK -> ferrostarWrapperRepository.truck\n"
    "        RoutingMode.MOTOR_SCOOTER",
    "        RoutingMode.TRUCK -> ferrostarWrapperRepository.truck\n"
    "        RoutingMode.BUS -> ferrostarWrapperRepository.bus\n"
    "        RoutingMode.MOTOR_SCOOTER",
)

# Record exact semantics in the project handoff log.
log = Path("KAPIJUJA_PROJECT_LOG.md")
log.write_text(
    log.read_text()
    + '''

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
'''
)

print("All requested source substitutions completed.")
