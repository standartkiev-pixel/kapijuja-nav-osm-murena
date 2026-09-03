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

package earth.maps.cardinal.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.UnknownHostException

class HttpRequestFailureTest {

    @Test
    fun `status codes map to generic http request failures`() {
        assertEquals(HttpRequestFailure.BAD_REQUEST, 400.toHttpRequestFailure())
        assertEquals(HttpRequestFailure.UNAUTHORIZED, 401.toHttpRequestFailure())
        assertEquals(HttpRequestFailure.FORBIDDEN, 403.toHttpRequestFailure())
        assertEquals(HttpRequestFailure.NOT_FOUND, 404.toHttpRequestFailure())
        assertEquals(HttpRequestFailure.REQUEST_TIMEOUT, 408.toHttpRequestFailure())
        assertEquals(HttpRequestFailure.TOO_MANY_REQUESTS, 429.toHttpRequestFailure())
        assertEquals(HttpRequestFailure.SERVICE_UNAVAILABLE, 503.toHttpRequestFailure())
        assertEquals(HttpRequestFailure.SERVER_ERROR, 500.toHttpRequestFailure())
        assertEquals(HttpRequestFailure.UNKNOWN, 418.toHttpRequestFailure())
    }

    @Test
    fun `network exceptions map to generic http request failures`() {
        assertEquals(
            HttpRequestFailure.NO_INTERNET,
            UnknownHostException().toHttpRequestFailure()
        )
        assertEquals(
            HttpRequestFailure.NOT_FOUND,
            HttpRequestException(HttpRequestFailure.NOT_FOUND).toHttpRequestFailure()
        )
    }
}
