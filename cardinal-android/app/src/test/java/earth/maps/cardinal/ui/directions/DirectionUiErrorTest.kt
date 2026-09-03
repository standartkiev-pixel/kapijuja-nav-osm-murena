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

package earth.maps.cardinal.ui.directions

import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.ferrostar.ParsingException

class DirectionUiErrorTest {

    @Test
    fun `status code errors map to user friendly categories`() {
        assertEquals(
            DirectionUiError.ServerUnavailable,
            InvalidStatusCodeException(500).toRouteError()
        )
        assertEquals(
            DirectionUiError.ConnectionUnavailable,
            InvalidStatusCodeException(503).toRouteError()
        )
        assertEquals(
            DirectionUiError.RequestTimedOut,
            InvalidStatusCodeException(504).toRouteError()
        )
    }

    @Test
    fun `valhalla route errors map to user friendly categories`() {
        assertEquals(
            DirectionUiError.DistanceExceeded,
            ParsingException.InvalidStatusCode(
                code = DirectionCodes.DISTANCE_EXCEEDED.value,
                description = "Distance exceeded"
            ).toRouteError()
        )
        assertEquals(
            DirectionUiError.RouteNotFound,
            ParsingException.InvalidStatusCode(
                code = "NoRoute",
                description = "No route found"
            ).toRouteError()
        )
    }
}
