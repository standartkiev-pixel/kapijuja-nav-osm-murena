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

package earth.maps.cardinal.routing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object ValhallaRequestLogRedactor {
    fun summarize(requestBody: String): String =
        runCatching {
            val jsonObject = Json.parseToJsonElement(requestBody) as? JsonObject
                ?: return@runCatching "<non-object request body>"
            val costing = jsonObject["costing"]?.jsonPrimitive?.content
            val costingProfile = costing?.let(ValhallaCostingProfile::fromRouteProviderProfile)
            val summary = buildMap {
                jsonObject["alternates"]?.jsonPrimitive?.intOrNull?.let { alternates ->
                    put("alternates", JsonPrimitive(alternates))
                }
                put(
                    "profile_class",
                    JsonPrimitive(costingProfile?.safeLogProfileClass ?: "custom")
                )
                put("uses_traffic_profile", JsonPrimitive(costingProfile?.usesTraffic == true))
            }
            JsonObject(summary).toString()
        }.getOrElse {
            "<malformed request body>"
        }
}
