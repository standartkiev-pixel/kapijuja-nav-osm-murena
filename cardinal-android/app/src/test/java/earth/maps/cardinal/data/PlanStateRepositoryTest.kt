package earth.maps.cardinal.data

import earth.maps.cardinal.transit.Itinerary
import earth.maps.cardinal.transit.Leg
import earth.maps.cardinal.transit.Mode
import earth.maps.cardinal.transit.PlanResponse
import earth.maps.cardinal.transit.TransitPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanStateRepositoryTest {

    @Test
    fun setPlanResponse_selectsEarliestItineraryByDefault() {
        val repository = PlanStateRepository()
        val laterItinerary = itinerary(endTime = "2026-08-04T10:30:00Z")
        val earliestItinerary = itinerary(endTime = "2026-08-04T10:15:00Z")

        repository.setPlanResponse(
            planResponse(
                itineraries = listOf(laterItinerary),
                direct = listOf(earliestItinerary)
            )
        )

        assertEquals(0, repository.planState.value.selectedItineraryIndex)
        assertEquals(listOf(earliestItinerary, laterItinerary), repository.planState.value.itineraries)
        assertEquals(earliestItinerary, repository.planState.value.selectedItinerary)
    }

    @Test
    fun selectItinerary_updatesSelectedItineraryWhenIndexIsValid() {
        val repository = PlanStateRepository()
        val earliestItinerary = itinerary(endTime = "2026-08-04T10:15:00Z")
        val laterItinerary = itinerary(endTime = "2026-08-04T10:30:00Z")
        repository.setPlanResponse(
            planResponse(
                itineraries = listOf(laterItinerary, earliestItinerary)
            )
        )

        repository.selectItinerary(1)

        assertEquals(1, repository.planState.value.selectedItineraryIndex)
        assertEquals(laterItinerary, repository.planState.value.selectedItinerary)
    }

    @Test
    fun setPlanResponse_defaultsToFirstItineraryWhenPreviousSelectionIsNoLongerValid() {
        val repository = PlanStateRepository()
        repository.setPlanResponse(
            planResponse(
                itineraries = listOf(
                    itinerary(endTime = "2026-08-04T10:15:00Z"),
                    itinerary(endTime = "2026-08-04T10:30:00Z"),
                    itinerary(endTime = "2026-08-04T10:45:00Z")
                )
            )
        )
        repository.selectItinerary(2)

        val onlyItinerary = itinerary(
            startTime = "2026-08-04T11:00:00Z",
            endTime = "2026-08-04T11:30:00Z"
        )
        repository.setPlanResponse(planResponse(itineraries = listOf(onlyItinerary)))

        assertEquals(0, repository.planState.value.selectedItineraryIndex)
        assertEquals(onlyItinerary, repository.planState.value.selectedItinerary)
    }

    @Test
    fun setError_clearsSelectedItinerary() {
        val repository = PlanStateRepository()
        repository.setPlanResponse(planResponse(itineraries = listOf(itinerary())))

        repository.setError("Transit unavailable")

        assertNull(repository.planState.value.selectedItineraryIndex)
        assertEquals(emptyList<Itinerary>(), repository.planState.value.itineraries)
        assertNull(repository.planState.value.selectedItinerary)
    }

    private fun planResponse(
        itineraries: List<Itinerary> = emptyList(),
        direct: List<Itinerary> = emptyList()
    ) = PlanResponse(
        from = TransitPlace("From", null, 0.0, 0.0, 0.0),
        to = TransitPlace("To", null, 1.0, 1.0, 0.0),
        direct = direct,
        itineraries = itineraries,
        previousPageCursor = "",
        nextPageCursor = ""
    )

    private fun itinerary(
        startTime: String = "2026-08-04T10:00:00Z",
        endTime: String = "2026-08-04T10:15:00Z",
        legs: List<Leg> = emptyList()
    ) = Itinerary(
        duration = 900,
        startTime = startTime,
        endTime = endTime,
        transfers = 0,
        legs = legs
    )

    private fun transitLeg(realTime: Boolean): Leg = Leg(
        mode = Mode.BUS,
        fromTransitPlace = TransitPlace("From Stop", "from-stop", 0.0, 0.0, 0.0),
        toTransitPlace = TransitPlace("To Stop", "to-stop", 1.0, 1.0, 0.0),
        duration = 900,
        startTime = "2026-08-04T10:20:00Z",
        endTime = "2026-08-04T10:45:00Z",
        scheduledStartTime = "2026-08-04T10:20:00Z",
        scheduledEndTime = "2026-08-04T10:45:00Z",
        realTime = realTime,
        scheduled = !realTime,
        tripId = "trip-1",
        routeShortName = "10",
        headsign = "Downtown",
        agencyId = "agency-1",
        routeType = "bus"
    )
}

class StableTransitItineraryIdentityPolicyTest {

    private val identityPolicy = StableTransitItineraryIdentityPolicy

    @Test
    fun identityOf_usesStopTimeIdentityWhenTripIdIsBlankOrMissing() {
        val missingTripIdIdentity = identityPolicy.identityOf(
            itinerary(leg = transitLeg(tripId = null))
        )
        val blankTripIdIdentity = identityPolicy.identityOf(
            itinerary(leg = transitLeg(tripId = ""))
        )

        assertEquals(missingTripIdIdentity, blankTripIdIdentity)
    }

    @Test
    fun identityOf_distinguishesSameTripIdWhenStopTimeChanges() {
        val originalIdentity = identityPolicy.identityOf(
            itinerary(leg = transitLeg(tripId = "shared-trip"))
        )
        val changedStopTimeIdentity = identityPolicy.identityOf(
            itinerary(
                leg = transitLeg(
                    tripId = "shared-trip",
                    scheduledStartTime = "2026-08-04T10:25:00Z",
                    scheduledEndTime = "2026-08-04T10:50:00Z"
                )
            )
        )

        assertNotEquals(originalIdentity, changedStopTimeIdentity)
    }

    @Test
    fun identityOf_distinguishesSameTripIdWhenRouteChanges() {
        val originalIdentity = identityPolicy.identityOf(
            itinerary(leg = transitLeg(tripId = "shared-trip"))
        )
        val changedRouteIdentity = identityPolicy.identityOf(
            itinerary(
                leg = transitLeg(
                    tripId = "shared-trip",
                    routeShortName = "11"
                )
            )
        )

        assertNotEquals(originalIdentity, changedRouteIdentity)
    }

    @Test
    fun identityOf_distinguishesItinerariesThatShareFirstTransitLegButDifferLater() {
        val sharedFirstLeg = transitLeg(tripId = "shared-first-trip")
        val routeAIdentity = identityPolicy.identityOf(
            itinerary(
                legs = listOf(
                    sharedFirstLeg,
                    transitLeg(
                        tripId = "second-trip-a",
                        routeShortName = "20",
                        scheduledStartTime = "2026-08-04T10:50:00Z",
                        scheduledEndTime = "2026-08-04T11:15:00Z"
                    )
                )
            )
        )
        val routeBIdentity = identityPolicy.identityOf(
            itinerary(
                legs = listOf(
                    sharedFirstLeg,
                    transitLeg(
                        tripId = "second-trip-b",
                        routeShortName = "30",
                        scheduledStartTime = "2026-08-04T10:55:00Z",
                        scheduledEndTime = "2026-08-04T11:20:00Z"
                    )
                )
            )
        )

        assertNotEquals(routeAIdentity, routeBIdentity)
    }

    private fun itinerary(leg: Leg) = Itinerary(
        duration = 900,
        startTime = leg.startTime,
        endTime = leg.endTime,
        transfers = 0,
        legs = listOf(leg)
    )

    private fun itinerary(legs: List<Leg>) = Itinerary(
        duration = 1_800,
        startTime = legs.first().startTime,
        endTime = legs.last().endTime,
        transfers = legs.size - 1,
        legs = legs
    )

    private fun transitLeg(
        tripId: String?,
        routeShortName: String = "10",
        scheduledStartTime: String = "2026-08-04T10:20:00Z",
        scheduledEndTime: String = "2026-08-04T10:45:00Z"
    ): Leg = Leg(
        mode = Mode.BUS,
        fromTransitPlace = TransitPlace("From Stop", "from-stop", 0.0, 0.0, 0.0),
        toTransitPlace = TransitPlace("To Stop", "to-stop", 1.0, 1.0, 0.0),
        duration = 900,
        startTime = "2026-08-04T10:20:00Z",
        endTime = "2026-08-04T10:45:00Z",
        scheduledStartTime = scheduledStartTime,
        scheduledEndTime = scheduledEndTime,
        realTime = false,
        scheduled = true,
        tripId = tripId,
        routeShortName = routeShortName,
        agencyId = "agency-1",
        routeType = "bus"
    )
}
