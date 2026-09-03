/*
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
