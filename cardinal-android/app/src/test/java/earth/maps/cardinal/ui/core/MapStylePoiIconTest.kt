/*
 *     Cardinal Maps
 *     Copyright (C) 2026 Cardinal Maps Authors
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

package earth.maps.cardinal.ui.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapStylePoiIconTest {

    private val styles = listOf(
        StyleSpec(
            fileName = "style_light.json",
            expectedSprite = "https://tiles.maps.murena.com/styles/sprites/v4/light"
        ),
        StyleSpec(
            fileName = "style_dark.json",
            expectedSprite = "https://tiles.maps.murena.com/styles/sprites/v4/dark"
        )
    )

    @Test
    fun `poi layers use remote sprite icon names`() {
        styles.forEach { style ->
            val styleText = styleFile(style.fileName).readText()
            assertFalse(
                "${style.fileName} should not request nonexistent sprite ids",
                styleText.contains("\"{class}_11\"")
            )

            val styleJson = Json.parseToJsonElement(styleText).jsonObject
            assertEquals(style.expectedSprite, styleJson["sprite"]?.jsonPrimitive?.content)

            val layers = styleJson["layers"]!!.jsonArray.map { it.jsonObject }
            listOf("poi_z14", "poi_z15", "poi_z16", "poi_transit").forEach { layerId ->
                val layout = requireLayer(layers, layerId)["layout"]!!.jsonObject

                assertEquals(expectedPoiIconExpression, layout["icon-image"])
                assertEquals(JsonPrimitive(true), layout["icon-optional"])
                assertEquals(JsonPrimitive(true), layout["text-optional"])
            }
        }
    }

    @Test
    fun `parking layer renders text fallback without local sprite assets`() {
        styles.forEach { style ->
            val styleJson = Json.parseToJsonElement(styleFile(style.fileName).readText()).jsonObject
            val layers = styleJson["layers"]!!.jsonArray.map { it.jsonObject }
            val parkingLayer = requireLayer(layers, "poi_parking")

            assertEquals("symbol", parkingLayer["type"]?.jsonPrimitive?.content)
            assertEquals("openmaptiles", parkingLayer["source"]?.jsonPrimitive?.content)
            assertEquals("poi", parkingLayer["source-layer"]?.jsonPrimitive?.content)
            assertEquals(14, parkingLayer["minzoom"]?.jsonPrimitive?.int)
            assertEquals(expectedParkingFilter, parkingLayer["filter"])

            val layout = parkingLayer["layout"]!!.jsonObject
            assertEquals("P", layout["text-field"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `parking layer is included in map tap query layers`() {
        assertTrue(MAP_POI_CLICKABLE_LAYER_IDS.contains("poi_parking"))
    }

    private fun requireLayer(layers: List<JsonObject>, layerId: String): JsonObject {
        return layers.firstOrNull { layer ->
            layer["id"]?.jsonPrimitive?.content == layerId
        } ?: error("Missing layer $layerId")
    }

    private fun styleFile(fileName: String): File {
        val candidates = listOf(
            File("src/main/assets/$fileName"),
            File("app/src/main/assets/$fileName")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Could not find style asset $fileName from ${File(".").absolutePath}")
    }

    private data class StyleSpec(
        val fileName: String,
        val expectedSprite: String
    )

    private companion object {
        val expectedPoiIconExpression = Json.parseToJsonElement(
            """
            [
              "match",
              [
                "get",
                "class"
              ],
              "bus",
              "bus_stop",
              "rail",
              "train_station",
              "airport",
              "aerodrome",
              "restaurant",
              "restaurant",
              "bar",
              "bar",
              "fast_food",
              "fast_food",
              "school",
              "school",
              "park",
              "park",
              "toilets",
              "toilets",
              "drinking_water",
              "drinking_water",
              ""
            ]
            """.trimIndent()
        )

        val expectedParkingFilter = Json.parseToJsonElement(
            """
            [
              "==",
              "class",
              "parking"
            ]
            """.trimIndent()
        )
    }
}
