/*
 *     Cardinal Maps
 *     Copyright (C) 2025 Cardinal Maps Authors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package earth.maps.cardinal.ui.map

import android.location.Location
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import earth.maps.cardinal.R.drawable
import kotlinx.serialization.json.Json
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.Source
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position


@Composable
fun LocationPuckLayers(idPrefix: String, locationSource: Source, headingDegrees: Float?) {
    val puckDrawable = if (headingDegrees == null) {
        painterResource(drawable.location_puck)
    } else {
        painterResource(drawable.location_puck_with_arrow)
    }
    SymbolLayer(
        id = "${idPrefix}-puck",
        source = locationSource,
        iconAllowOverlap = const(true),
        iconImage = image(puckDrawable),
        iconRotate = const(headingDegrees ?: 0f),
    )
}

@Composable
fun LocationPuck(location: Location, sensorHeading: Float? = null) {
    Log.d("Location", "$location")

    val locationSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(
            Json.encodeToString(Point(
                coordinates = Position(
                    location.longitude,
                    location.latitude
                ),
            ))
        )
    )

    // Prefer sensor-based heading over GPS bearing
    // Sensor heading is more accurate and updates faster, especially when stationary
    val headingDegrees = sensorHeading
        ?: if (location.hasBearing()) location.bearing else null

    LocationPuckLayers(
        idPrefix = "user-location",
        locationSource = locationSource,
        headingDegrees = headingDegrees
    )
}
