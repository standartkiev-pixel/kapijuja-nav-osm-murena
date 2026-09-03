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

package earth.maps.cardinal.data

/**
 * Data class for API configuration containing a base URL and optional API key.
 */
data class ApiConfiguration(
    val baseUrl: String,
    val apiKey: String? = null
)

internal fun ApiConfiguration.toDebugLogString(): String =
    "baseUrl=$baseUrl, apiKey=${apiKey.toMaskedApiKeyLogString()}"

internal fun String.maskApiKeyQueryParamsForLogs(): String =
    API_KEY_QUERY_PARAM_REGEX.replace(this) { matchResult ->
        "${matchResult.groupValues[1]}****"
    }

internal fun String.maskSensitiveQueryParamsForLogs(): String {
    return SENSITIVE_QUERY_PARAM_REGEX.replace(maskApiKeyQueryParamsForLogs()) { matchResult ->
        "${matchResult.groupValues[1]}****"
    }
}

private fun String?.toMaskedApiKeyLogString(): String {
    if (this == null) {
        return "<none>"
    }
    if (isBlank()) {
        return "<blank>"
    }
    if (length <= API_KEY_VISIBLE_PREFIX_LENGTH + API_KEY_VISIBLE_SUFFIX_LENGTH) {
        return "**** (len=$length)"
    }
    return "${take(API_KEY_VISIBLE_PREFIX_LENGTH)}...${takeLast(API_KEY_VISIBLE_SUFFIX_LENGTH)} " +
        "(len=$length)"
}

private const val API_KEY_VISIBLE_PREFIX_LENGTH = 4
private const val API_KEY_VISIBLE_SUFFIX_LENGTH = 4
private val API_KEY_QUERY_PARAM_REGEX = Regex("(?i)(api_key=)[^&\\s]+")
private val SENSITIVE_QUERY_PARAM_REGEX = Regex(
    "(?i)((?:text|point\\.lat|point\\.lon|focus\\.point\\.lat|focus\\.point\\.lon|" +
        "boundary\\.circle\\.lat|boundary\\.circle\\.lon)=)[^&\\s]+"
)
