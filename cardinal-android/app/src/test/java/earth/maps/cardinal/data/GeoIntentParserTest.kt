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

package earth.maps.cardinal.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeoIntentParserTest {

    @Test
    fun `parse handles opaque geo coordinates`() {
        val result = GeoIntentParser.parse(Uri.parse("geo:37.786971,-122.399677"))

        assertNotNull(result)
        assertEquals(37.786971, result!!.latLng.latitude, 0.0)
        assertEquals(-122.399677, result.latLng.longitude, 0.0)
        assertNull(result.name)
    }

    @Test
    fun `parse handles opaque geo query coordinates with label`() {
        val result = GeoIntentParser.parse(
            Uri.parse("geo:0,0?q=37.786971,-122.399677(Some%20Place)")
        )

        assertNotNull(result)
        assertEquals(37.786971, result!!.latLng.latitude, 0.0)
        assertEquals(-122.399677, result.latLng.longitude, 0.0)
        assertEquals("Some Place", result.name)
    }

    @Test
    fun `parse handles hierarchical geo coordinates`() {
        val result = GeoIntentParser.parse(Uri.parse("geo://12.9716,77.5946"))

        assertNotNull(result)
        assertEquals(12.9716, result!!.latLng.latitude, 0.0)
        assertEquals(77.5946, result.latLng.longitude, 0.0)
    }

    @Test
    fun `parse keeps text query as name when coordinates are explicit`() {
        val result = GeoIntentParser.parse(Uri.parse("geo:12.9716,77.5946?q=Bengaluru"))

        assertNotNull(result)
        assertEquals(12.9716, result!!.latLng.latitude, 0.0)
        assertEquals(77.5946, result.latLng.longitude, 0.0)
        assertEquals("Bengaluru", result.name)
    }

    @Test
    fun `parse ignores unsupported text-only search placeholder`() {
        val result = GeoIntentParser.parse(Uri.parse("geo:0,0?q=1600%20Amphitheatre%20Parkway"))

        assertNull(result)
    }

    @Test
    fun `parse rejects out of range coordinates`() {
        val result = GeoIntentParser.parse(Uri.parse("geo:123.0,-122.399677"))

        assertNull(result)
    }
}
