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

import earth.maps.cardinal.data.AppPreferences
import earth.maps.cardinal.transit.Itinerary
import earth.maps.cardinal.transit.Leg
import earth.maps.cardinal.transit.Mode
import earth.maps.cardinal.transit.TransitPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransitItineraryDetailPresenterTest {

    private val presenter = TransitItineraryDetailPresenter(
        text = FakeTransitDetailTextProvider
    )

    @Test
    fun `present creates keyed summary and timeline items for leg synchronization`() {
        val itinerary = Itinerary(
            duration = 900,
            startTime = START_TIME,
            endTime = "2026-08-07T10:15:00Z",
            transfers = 1,
            legs = listOf(
                transitLeg(mode = Mode.WALK, duration = 300, distance = 100.0),
                transitLeg(mode = Mode.BUS, duration = 600, routeShortName = "10")
            )
        )

        val ui = presenter.present(
            itinerary = itinerary,
            use24HourFormat = true,
            distanceUnit = AppPreferences.DISTANCE_UNIT_METRIC
        )

        assertTrue(ui.summaryItems.first() is TransitSummaryItemUi.Overview)
        assertEquals(2, ui.summaryItems.indexOfLeg(1))
        assertEquals(1, ui.timelineItems.indexOfTimelineLeg(1))
        assertEquals("10", ui.timelineItems[1].summaryItem.labelText)
        assertTrue(ui.overview.timeAndTransfersText.endsWith("transferCount(1)"))
    }

    @Test
    fun `presentOverview maps current speed without rebuilding timeline items`() {
        val itinerary = Itinerary(
            duration = 900,
            startTime = START_TIME,
            endTime = "2026-08-07T10:15:00Z",
            transfers = 1,
            legs = listOf(transitLeg(mode = Mode.WALK, duration = 300, distance = 100.0))
        )

        val overview = presenter.presentOverview(
            itinerary = itinerary,
            use24HourFormat = true,
            distanceUnit = AppPreferences.DISTANCE_UNIT_METRIC,
            currentSpeedText = "4 km/h"
        )

        assertTrue(overview.timeAndTransfersText.endsWith("transferCount(1)"))
        assertTrue(overview.walkingText.contains("speed= · 4 km/h"))
    }

    @Test
    fun `present maps wait platform and realtime status outside compose`() {
        val busFrom = place(name = "Station", scheduledTrack = "2")
        val itinerary = Itinerary(
            duration = 900,
            startTime = START_TIME,
            endTime = "2026-08-07T10:15:00Z",
            transfers = 0,
            legs = listOf(
                transitLeg(
                    mode = Mode.WALK,
                    duration = 300,
                    endTime = "2026-08-07T10:05:00Z"
                ),
                transitLeg(
                    mode = Mode.BUS,
                    from = busFrom,
                    duration = 600,
                    startTime = "2026-08-07T10:10:00Z",
                    endTime = "2026-08-07T10:20:00Z",
                    realTime = true,
                    intermediateStops = listOf(place("Midtown")),
                    headsign = "Downtown"
                )
            )
        )

        val ui = presenter.present(
            itinerary = itinerary,
            use24HourFormat = true,
            distanceUnit = AppPreferences.DISTANCE_UNIT_METRIC
        )

        assertTrue(ui.timelineItems[0].walkingSegment?.text.orEmpty().contains("waitUpTo("))
        assertEquals("platform(2)", ui.timelineItems[1].fromStop.platformText)
        assertEquals("to(Downtown)", ui.timelineItems[1].transitSegment?.headsignText)
        assertEquals(TransitStatusKind.ON_TIME, ui.timelineItems[1].transitSegment?.status?.kind)
        assertEquals("onTime", ui.timelineItems[1].transitSegment?.status?.text)
    }

    @Test
    fun `present maps delayed realtime leg status`() {
        val itinerary = Itinerary(
            duration = 600,
            startTime = START_TIME,
            endTime = "2026-08-07T10:10:00Z",
            transfers = 0,
            legs = listOf(
                transitLeg(
                    mode = Mode.BUS,
                    duration = 600,
                    startTime = "2026-08-07T10:05:00Z",
                    scheduledStartTime = START_TIME,
                    realTime = true
                )
            )
        )

        val ui = presenter.present(
            itinerary = itinerary,
            use24HourFormat = true,
            distanceUnit = AppPreferences.DISTANCE_UNIT_METRIC
        )

        assertEquals(TransitStatusKind.DELAYED, ui.timelineItems[0].transitSegment?.status?.kind)
        assertTrue(ui.timelineItems[0].transitSegment?.status?.text.orEmpty().startsWith("delayed("))
    }

    @Test
    fun `present does not mark realtime leg with invalid schedule as on time`() {
        val itinerary = Itinerary(
            duration = 600,
            startTime = START_TIME,
            endTime = "2026-08-07T10:10:00Z",
            transfers = 0,
            legs = listOf(
                transitLeg(
                    mode = Mode.BUS,
                    duration = 600,
                    scheduledStartTime = "invalid",
                    realTime = true
                )
            )
        )

        val ui = presenter.present(
            itinerary = itinerary,
            use24HourFormat = true,
            distanceUnit = AppPreferences.DISTANCE_UNIT_METRIC
        )

        assertNull(ui.timelineItems[0].transitSegment?.status)
    }

    @Test
    fun `present uses localized mode name fallback for missing route and agency names`() {
        val itinerary = Itinerary(
            duration = 600,
            startTime = START_TIME,
            endTime = "2026-08-07T10:10:00Z",
            transfers = 0,
            legs = listOf(
                transitLeg(
                    mode = Mode.RAIL,
                    duration = 600
                )
            )
        )

        val ui = presenter.present(
            itinerary = itinerary,
            use24HourFormat = true,
            distanceUnit = AppPreferences.DISTANCE_UNIT_METRIC
        )

        assertEquals("modeName(RAIL)", ui.timelineItems[0].transitSegment?.badgeText)
        assertEquals("modeName(RAIL)", ui.timelineItems[0].transitSegment?.agencyText)
    }

    private fun transitLeg(
        mode: Mode,
        from: TransitPlace = place("Start"),
        to: TransitPlace = place("End"),
        duration: Int,
        startTime: String = START_TIME,
        endTime: String = "2026-08-07T10:10:00Z",
        scheduledStartTime: String = startTime,
        distance: Double? = null,
        realTime: Boolean = false,
        routeShortName: String? = null,
        intermediateStops: List<TransitPlace>? = null,
        headsign: String? = null
    ): Leg {
        return Leg(
            mode = mode,
            fromTransitPlace = from,
            toTransitPlace = to,
            duration = duration,
            startTime = startTime,
            endTime = endTime,
            scheduledStartTime = scheduledStartTime,
            scheduledEndTime = endTime,
            realTime = realTime,
            scheduled = !realTime,
            distance = distance,
            routeShortName = routeShortName,
            intermediateStops = intermediateStops,
            headsign = headsign
        )
    }

    private fun place(name: String, scheduledTrack: String? = null): TransitPlace {
        return TransitPlace(
            name = name,
            lat = 0.0,
            lon = 0.0,
            level = 0.0,
            scheduledTrack = scheduledTrack
        )
    }

    private object FakeTransitDetailTextProvider : TransitItineraryDetailTextProvider {
        override fun distanceNotAvailable(): String = "distanceNotAvailable"
        override fun transferCount(count: Int): String = "transferCount($count)"
        override fun walkOverview(walkingDistanceText: String, speedSuffix: String): String =
            "walkOverview(distance=$walkingDistanceText,speed=$speedSuffix)"

        override fun destination(): String = "destination"
        override fun stopFallback(): String = "stop"
        override fun platform(platform: String): String = "platform($platform)"
        override fun walkSegment(durationText: String, distanceSuffix: String, waitSuffix: String): String =
            "walkSegment(duration=$durationText,distance=$distanceSuffix,wait=$waitSuffix)"

        override fun headsign(headsign: String): String = "to($headsign)"
        override fun rideSummary(stopCount: Int, durationText: String, distanceSuffix: String): String =
            "rideSummary(stops=$stopCount,duration=$durationText,distance=$distanceSuffix)"

        override fun waitUpTo(durationText: String): String = "waitUpTo($durationText)"
        override fun cancelled(): String = "cancelled"
        override fun onTime(): String = "onTime"
        override fun delayed(durationText: String): String = "delayed($durationText)"
        override fun modeName(mode: Mode): String = "modeName(${mode.name})"
    }

    private companion object {
        const val START_TIME = "2026-08-07T10:00:00Z"
    }
}
