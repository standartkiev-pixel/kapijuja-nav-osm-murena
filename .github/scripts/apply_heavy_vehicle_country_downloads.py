from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}\n--- OLD ---\n{old[:1000]}")
    p.write_text(text.replace(old, new, 1))
    print(f"patched {path}")


routing_options = "cardinal-android/app/src/main/java/earth/maps/cardinal/routing/RoutingOptions.kt"

# BUS axle count is useful/persisted in the profile UI, but current Valhalla auto/bus
# costing schemas do not accept axle_count. Keep it app-side instead of causing HTTP 400.
replace_once(
    routing_options,
    '            remove("line_bus")\n\n            // Stadia\'s current hosted Valhalla request schema requires these time costs',
    '            remove("line_bus")\n            if (this@RoutingOptions is BusRoutingOptions) {\n                remove("axle_count")\n            }\n\n            // Stadia\'s current hosted Valhalla request schema requires these time costs'
)

old_truck = '''data class TruckRoutingOptions(
    override val costingType: String = COSTING_TYPE_TRUCK,

    // Basic auto options
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

    // Truck-specific options
    val length: Double? = null, // meters
    val width: Double? = null,
    val height: Double? = null,
    val weight: Double? = null, // metric tons
    val axleCount: Int? = null,
    val hazmat: Boolean? = null,
    val useTruckRoute: Double? = null // 0-1 range
) : RoutingOptions(), AutoOptions {'''

new_truck = '''data class TruckRoutingOptions(
    override val costingType: String = COSTING_TYPE_TRUCK,

    // Heavy-vehicle defaults: prefer roads where a full-size articulated truck belongs.
    override val maneuverPenalty: Double? = 45.0,
    override val gateCost: Double? = DEFAULT_GATE_COST,
    override val tollBoothCost: Double? = DEFAULT_TOLL_BOOTH_COST,
    override val privateAccessPenalty: Double? = null,
    override val useHighways: Double? = 0.8,
    override val useTolls: Double? = null,
    override val useLivingStreets: Double? = 0.0,
    override val useTracks: Double? = 0.0,
    override val ignoreClosures: Boolean? = false,
    override val ignoreRestrictions: Boolean? = false,
    override val ignoreOneWays: Boolean? = false,
    override val ignoreAccess: Boolean? = false,
    override val excludeUnpaved: Boolean? = true,
    override val excludeCashOnlyTolls: Boolean? = null,

    // Generic/low-class road penalties are native Valhalla costing options. They do not
    // hard-ban the destination street, but make residential/service detours expensive.
    val servicePenalty: Double? = 300.0,
    val serviceFactor: Double? = 5.0,
    val lowClassPenalty: Double? = 300.0,
    val closureFactor: Double? = 10.0,

    // EU full-size articulated truck baseline. 16.5 m is the standard articulated
    // vehicle maximum used as the built-in default; users can override every value.
    val length: Double? = 16.5, // meters
    val width: Double? = 2.5,
    val height: Double? = 4.0,
    val weight: Double? = 45.0, // metric tons, Kapijuja operational default
    val axleCount: Int? = 3,
    val hazmat: Boolean? = false,
    val useTruckRoute: Double? = 1.0 // 0-1 range, prefer hgv=designated network
) : RoutingOptions(), AutoOptions {'''
replace_once(routing_options, old_truck, new_truck)

old_bus = '''data class BusRoutingOptions(
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
) : RoutingOptions(), AutoOptions {'''

new_bus = '''data class BusRoutingOptions(
    override val costingType: String = COSTING_TYPE_BUS,

    // Full-size coach defaults. Avoid living streets/tracks and heavily penalize service
    // roads/alleys while still allowing a genuine destination last mile when unavoidable.
    override val maneuverPenalty: Double? = 45.0,
    override val gateCost: Double? = DEFAULT_GATE_COST,
    override val tollBoothCost: Double? = DEFAULT_TOLL_BOOTH_COST,
    override val privateAccessPenalty: Double? = null,
    override val useHighways: Double? = 0.8,
    override val useTolls: Double? = null,
    override val useLivingStreets: Double? = 0.0,
    override val useTracks: Double? = 0.0,
    override val ignoreClosures: Boolean? = false,
    override val ignoreRestrictions: Boolean? = false,
    override val ignoreOneWays: Boolean? = false,
    override val ignoreAccess: Boolean? = false,
    override val excludeUnpaved: Boolean? = true,
    override val excludeCashOnlyTolls: Boolean? = null,

    val servicePenalty: Double? = 300.0,
    val serviceFactor: Double? = 5.0,
    val alleyFactor: Double? = 10.0,
    val closureFactor: Double? = 10.0,

    // Kapijuja tourist-coach baseline.
    val length: Double? = 13.5, // meters
    val width: Double? = 2.5,
    val height: Double? = 4.0,
    val weight: Double? = 18.0, // metric tons
    // Persisted for the profile and future toll/restriction integrations. Current Valhalla
    // auto/bus costing does not accept axle_count, so serialization removes it for BUS.
    val axleCount: Int? = 3,

    // App-only policy selector. Not serialized into Valhalla costing_options.
    val lineBus: Boolean = false
) : RoutingOptions(), AutoOptions {'''
replace_once(routing_options, old_bus, new_bus)

# Show the BUS axle baseline in the existing profile editor.
profile_editor = "cardinal-android/app/src/main/java/earth/maps/cardinal/ui/settings/ProfileEditorScreen.kt"
replace_once(
    profile_editor,
    '    OptionsSection("Bus Type") {\n',
    '''    OptionsSection("Bus Axles") {
        SliderOption(
            "Axle Count",
            options.axleCount?.toDouble(),
            2f..8f,
            valueFormatter = { it.format(0) }) { value ->
            onOptionsChanged(options.copy(axleCount = value?.toInt()))
        }
    }

    OptionsSection("Bus Type") {
'''
)

# A dedicated European country catalog keeps the country-download UI independent from the
# reverse-geocoding bounds. Bounds are deliberately European road-navigation extents (e.g.
# mainland ES/PT/FR) to avoid enormous ocean/overseas tile rectangles.
europe_file = Path("cardinal-android/app/src/main/java/earth/maps/cardinal/data/EuropeanCountryDownloads.kt")
europe_file.write_text('''/*
 *     Cardinal Maps / Kapijuja country download catalog
 *     GPL-3.0-or-later
 */

package earth.maps.cardinal.data

import java.util.Locale

data class EuropeanCountryDownloadRegion(
    val countryCode: String,
    val name: String,
    val boundingBox: BoundingBox
)

object EuropeanCountryDownloads {
    private data class Bounds(val south: Double, val north: Double, val west: Double, val east: Double)

    private val countries = linkedMapOf(
        "AL" to Bounds(39.6, 42.7, 19.1, 21.1),
        "AD" to Bounds(42.42, 42.66, 1.41, 1.79),
        "AT" to Bounds(46.3, 49.1, 9.5, 17.2),
        "BE" to Bounds(49.5, 51.6, 2.5, 6.5),
        "BA" to Bounds(42.5, 45.3, 15.7, 19.7),
        "BG" to Bounds(41.2, 44.3, 22.3, 28.7),
        "BY" to Bounds(51.2, 56.2, 23.1, 32.8),
        "CH" to Bounds(45.8, 47.9, 5.9, 10.5),
        "CY" to Bounds(34.5, 35.8, 32.2, 34.7),
        "CZ" to Bounds(48.5, 51.1, 12.0, 18.9),
        "DE" to Bounds(47.2, 55.1, 5.8, 15.1),
        "DK" to Bounds(54.5, 57.8, 8.0, 15.3),
        "EE" to Bounds(57.5, 59.8, 21.7, 28.2),
        "ES" to Bounds(36.0, 43.8, -9.4, 4.4),
        "FI" to Bounds(59.7, 70.1, 20.5, 31.6),
        "FR" to Bounds(41.3, 51.2, -5.2, 9.7),
        "GB" to Bounds(49.8, 60.9, -8.7, 1.8),
        "GR" to Bounds(34.7, 41.8, 19.2, 29.7),
        "HR" to Bounds(42.3, 46.6, 13.4, 19.5),
        "HU" to Bounds(45.7, 48.6, 16.1, 22.9),
        "IE" to Bounds(51.3, 55.5, -10.8, -5.9),
        "IS" to Bounds(63.3, 66.6, -24.6, -13.4),
        "IT" to Bounds(35.4, 47.1, 6.6, 18.6),
        "LI" to Bounds(47.05, 47.28, 9.47, 9.64),
        "LT" to Bounds(53.9, 56.5, 20.9, 26.9),
        "LU" to Bounds(49.4, 50.2, 5.7, 6.6),
        "LV" to Bounds(55.7, 58.1, 20.9, 28.3),
        "MC" to Bounds(43.72, 43.76, 7.40, 7.44),
        "MD" to Bounds(45.4, 48.5, 26.6, 30.2),
        "ME" to Bounds(41.8, 43.6, 18.4, 20.4),
        "MK" to Bounds(40.8, 42.4, 20.4, 23.1),
        "MT" to Bounds(35.8, 36.1, 14.1, 14.6),
        "NL" to Bounds(50.7, 53.7, 3.3, 7.3),
        "NO" to Bounds(57.8, 71.4, 4.5, 31.2),
        "PL" to Bounds(49.0, 54.9, 14.1, 24.2),
        "PT" to Bounds(36.9, 42.2, -9.6, -6.1),
        "RO" to Bounds(43.6, 48.3, 20.2, 29.8),
        "RS" to Bounds(42.2, 46.2, 18.8, 23.0),
        "SE" to Bounds(55.0, 69.1, 10.6, 24.2),
        "SI" to Bounds(45.4, 46.9, 13.4, 16.7),
        "SK" to Bounds(47.7, 49.7, 16.8, 22.6),
        "SM" to Bounds(43.89, 43.99, 12.40, 12.52),
        "TR" to Bounds(35.8, 42.2, 25.6, 44.9),
        "UA" to Bounds(44.2, 52.4, 22.1, 40.3),
        "VA" to Bounds(41.89, 41.91, 12.44, 12.47),
        "XK" to Bounds(41.85, 43.27, 20.0, 21.8)
    )

    fun regions(locale: Locale = Locale.getDefault()): List<EuropeanCountryDownloadRegion> =
        countries.map { (code, bounds) ->
            val localizedName = Locale.Builder()
                .setRegion(code)
                .build()
                .getDisplayCountry(locale)
                .takeIf { it.isNotBlank() }
                ?: code
            EuropeanCountryDownloadRegion(
                countryCode = code,
                name = localizedName,
                boundingBox = BoundingBox(
                    north = bounds.north,
                    south = bounds.south,
                    east = bounds.east,
                    west = bounds.west
                )
            )
        }.sortedBy { it.name.lowercase(locale) }
}
''')
print(f"wrote {europe_file}")

# Add a real nested Europe -> country selection page while preserving the old arbitrary viewport
# download as a secondary tool.
offline_screen = "cardinal-android/app/src/main/java/earth/maps/cardinal/ui/home/OfflineAreasScreen.kt"
replace_once(
    offline_screen,
    'import earth.maps.cardinal.data.BoundingBox\n',
    'import earth.maps.cardinal.data.BoundingBox\nimport earth.maps.cardinal.data.EuropeanCountryDownloadRegion\nimport earth.maps.cardinal.data.EuropeanCountryDownloads\n'
)
replace_once(
    offline_screen,
    '    var selectedArea by remember { mutableStateOf<OfflineArea?>(null) }\n',
    '    var selectedArea by remember { mutableStateOf<OfflineArea?>(null) }\n    var showEuropeCountries by remember { mutableStateOf(false) }\n    var countryToDownload by remember { mutableStateOf<EuropeanCountryDownloadRegion?>(null) }\n'
)

# Insert nested country page before rendering the original offline-area page.
replace_once(
    offline_screen,
    '    val coroutineScope = rememberCoroutineScope()\n\n    Column(\n',
    '''    val coroutineScope = rememberCoroutineScope()

    if (showEuropeCountries) {
        EuropeCountryDownloadScreen(
            viewModel = viewModel,
            isDownloading = isDownloading,
            onBack = { showEuropeCountries = false },
            onCountrySelected = { countryToDownload = it }
        )

        countryToDownload?.let { country ->
            CountryDownloadConfirmationDialog(
                country = country,
                estimatedTileCount = viewModel.estimateTileCount(
                    country.boundingBox,
                    OfflineAreasViewModel.OFFLINE_AREA_MIN_ZOOM,
                    OfflineAreasViewModel.OFFLINE_AREA_MAX_ZOOM
                ),
                onDismiss = { countryToDownload = null },
                onDownload = {
                    viewModel.startDownload(country.boundingBox, country.name)
                    countryToDownload = null
                    showEuropeCountries = false
                }
            )
        }
        return
    }

    Column(
'''
)

# Country download is primary; arbitrary viewport area remains available below it.
replace_once(
    offline_screen,
    '        // Download button\n        Button(\n',
    '''        Button(
            onClick = { showEuropeCountries = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensionResource(dimen.padding_minor)),
            enabled = !isDownloading
        ) {
            Icon(
                painter = painterResource(drawable.cloud_download_24dp),
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Europe - download entire country")
        }

        // Existing viewport-area download is deliberately preserved as a secondary tool.
        Button(
'''
)

# Append country UI helpers before formatFileSize.
replace_once(
    offline_screen,
    'fun formatFileSize(bytes: Long): String {\n',
    '''@Composable
private fun EuropeCountryDownloadScreen(
    viewModel: OfflineAreasViewModel,
    isDownloading: Boolean,
    onBack: () -> Unit,
    onCountrySelected: (EuropeanCountryDownloadRegion) -> Unit
) {
    val countries = remember { EuropeanCountryDownloads.regions() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(dimen.padding_minor))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensionResource(dimen.padding)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back)
                )
            }
            Text(
                text = "Europe",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "Choose a country. The complete country package includes basemap, offline Valhalla routing and offline geocoder data.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = dimensionResource(dimen.padding))
        )
        LazyColumn {
            items(countries, key = { it.countryCode }) { country ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable(enabled = !isDownloading) { onCountrySelected(country) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensionResource(dimen.padding)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(country.name, style = MaterialTheme.typography.titleMedium)
                            Text(country.countryCode, style = MaterialTheme.typography.bodySmall)
                        }
                        val count = viewModel.estimateTileCount(
                            country.boundingBox,
                            OfflineAreasViewModel.OFFLINE_AREA_MIN_ZOOM,
                            OfflineAreasViewModel.OFFLINE_AREA_MAX_ZOOM
                        )
                        Text("~$count tiles", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryDownloadConfirmationDialog(
    country: EuropeanCountryDownloadRegion,
    estimatedTileCount: Int,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(country.name) },
        text = {
            Column {
                Text("Download the entire country as one offline area?")
                Text(
                    "Estimated basemap tiles: $estimatedTileCount",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Routing tiles and geocoder data are downloaded by the existing offline pipeline after the basemap stage.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

fun formatFileSize(bytes: Long): String {
'''
)

# Add focused tests so future edits cannot silently erase the operational defaults or leak
# app-only bus axle_count into hosted Valhalla requests.
test_file = Path("cardinal-android/app/src/test/java/earth/maps/cardinal/routing/HeavyVehicleDefaultsTest.kt")
test_file.write_text('''package earth.maps.cardinal.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeavyVehicleDefaultsTest {
    @Test
    fun `truck defaults describe full size articulated vehicle and avoid low class roads`() {
        val options = TruckRoutingOptions()
        assertEquals(16.5, options.length!!, 0.0)
        assertEquals(2.5, options.width!!, 0.0)
        assertEquals(4.0, options.height!!, 0.0)
        assertEquals(45.0, options.weight!!, 0.0)
        assertEquals(3, options.axleCount)
        assertEquals(1.0, options.useTruckRoute!!, 0.0)
        assertEquals(0.0, options.useLivingStreets!!, 0.0)
        assertTrue(options.excludeUnpaved == true)
        assertEquals(300.0, options.lowClassPenalty!!, 0.0)
    }

    @Test
    fun `coach defaults describe three axle 13 point 5 metre bus`() {
        val options = BusRoutingOptions()
        assertEquals(13.5, options.length!!, 0.0)
        assertEquals(2.5, options.width!!, 0.0)
        assertEquals(4.0, options.height!!, 0.0)
        assertEquals(18.0, options.weight!!, 0.0)
        assertEquals(3, options.axleCount)
        assertFalse(options.lineBus)
        assertEquals(0.0, options.useLivingStreets!!, 0.0)
        assertTrue(options.excludeUnpaved == true)
    }

    @Test
    fun `bus axle count stays app side while physical dimensions reach Valhalla`() {
        val json = BusRoutingOptions().toValhallaOptionsJson(
            costingProfileOverride = ValhallaCostingProfile.Auto
        )
        assertFalse(json.contains("axle_count"))
        assertTrue(json.contains("\\\"length\\\":13.5"))
        assertTrue(json.contains("\\\"width\\\":2.5"))
        assertTrue(json.contains("\\\"height\\\":4.0"))
        assertTrue(json.contains("\\\"weight\\\":18.0"))
        assertTrue(json.contains("\\\"service_penalty\\\":300.0"))
    }

    @Test
    fun `truck low class and truck route preferences reach Valhalla`() {
        val json = TruckRoutingOptions().toValhallaOptionsJson(
            costingProfileOverride = ValhallaCostingProfile.Truck
        )
        assertTrue(json.contains("\\\"low_class_penalty\\\":300.0"))
        assertTrue(json.contains("\\\"use_truck_route\\\":1.0"))
        assertTrue(json.contains("\\\"axle_count\\\":3"))
        assertTrue(json.contains("\\\"closure_factor\\\":10.0"))
    }
}
''')
print(f"wrote {test_file}")
