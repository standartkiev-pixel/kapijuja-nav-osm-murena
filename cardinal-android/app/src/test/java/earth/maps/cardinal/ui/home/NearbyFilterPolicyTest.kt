package earth.maps.cardinal.ui.home

import earth.maps.cardinal.geocoding.FILTER_ACCOMMODATION
import earth.maps.cardinal.geocoding.FILTER_ATM
import earth.maps.cardinal.geocoding.FILTER_BAKERY
import earth.maps.cardinal.geocoding.FILTER_BANK
import earth.maps.cardinal.geocoding.FILTER_BAR
import earth.maps.cardinal.geocoding.FILTER_CAR_WASH
import earth.maps.cardinal.geocoding.FILTER_ENTERTAINMENT
import earth.maps.cardinal.geocoding.FILTER_FINANCE
import earth.maps.cardinal.geocoding.FILTER_GOVERNMENT
import earth.maps.cardinal.geocoding.FILTER_HOSPITAL
import earth.maps.cardinal.geocoding.FILTER_ICE_CREAM
import earth.maps.cardinal.geocoding.FILTER_LAUNDRY
import earth.maps.cardinal.geocoding.FILTER_LIBRARY
import earth.maps.cardinal.geocoding.FILTER_MEDICAL_CENTRE
import earth.maps.cardinal.geocoding.FILTER_PUB
import earth.maps.cardinal.geocoding.FILTER_SCHOOL
import earth.maps.cardinal.geocoding.FILTER_SHOPPING
import earth.maps.cardinal.geocoding.FILTER_SPORTS
import earth.maps.cardinal.geocoding.FILTER_TRANSPORTATION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyFilterPolicyTest {
    private val policy = NearbyFilterPolicy()

    @Test
    fun `appliedFilterState keeps typed search only when no categories are selected`() {
        val state = policy.appliedFilterState(
            draftSelectedCategories = emptySet(),
            draftCategorySearchQuery = "  cafe near me  "
        )

        assertEquals(emptySet<String>(), state.selectedCategories)
        assertEquals("cafe near me", state.searchQuery)
        assertEquals(emptyList<String>(), state.effectiveCategories)
        assertTrue(state.isFilterApplied)
    }

    @Test
    fun `appliedFilterState clears typed search when categories are selected`() {
        val state = policy.appliedFilterState(
            draftSelectedCategories = setOf("health:hospital"),
            draftCategorySearchQuery = "cafe near me"
        )

        assertEquals(setOf("health:hospital"), state.selectedCategories)
        assertEquals("", state.searchQuery)
        assertEquals(listOf(FILTER_HOSPITAL), state.effectiveCategories)
        assertTrue(state.isFilterApplied)
    }

    @Test
    fun `queryForAppliedFilters chooses text search only for non-empty query without categories`() {
        val query = policy.queryForAppliedFilters(
            selectedCategories = emptySet(),
            searchQuery = "  cafe near me  "
        )

        assertEquals(NearbyFilterQuery.TextSearch("cafe near me"), query)
    }

    @Test
    fun `queryForAppliedFilters chooses category search when categories are selected`() {
        val query = policy.queryForAppliedFilters(
            selectedCategories = setOf("food:cafe", "food:coffee_shop"),
            searchQuery = "ignored text"
        )

        assertEquals(NearbyFilterQuery.Categories(listOf("food:coffee_shop")), query)
    }

    @Test
    fun `queryForAppliedFilters chooses empty category search when no filters are applied`() {
        val query = policy.queryForAppliedFilters(
            selectedCategories = emptySet(),
            searchQuery = " "
        )

        assertEquals(NearbyFilterQuery.Categories(emptyList()), query)
    }

    @Test
    fun `isFilterApplied is true only when categories or text query are present`() {
        assertFalse(policy.isFilterApplied(emptySet(), " "))
        assertTrue(policy.isFilterApplied(setOf("food"), ""))
        assertTrue(policy.isFilterApplied(emptySet(), "coffee"))
    }

    @Test
    fun `effectiveCategories maps UI selections to provider query categories`() {
        val cases = mapOf(
            setOf("food:restaurant") to listOf("food"),
            setOf("food:cafe", "food:coffee_shop") to listOf("food:coffee_shop"),
            setOf("food:bakery", "food:ice_cream") to listOf(FILTER_BAKERY, FILTER_ICE_CREAM),
            setOf("nightlife:bar", "nightlife:pub") to listOf(FILTER_BAR, FILTER_PUB),
            setOf("health:hospital", "health:medical_centre") to listOf(
                FILTER_HOSPITAL,
                FILTER_MEDICAL_CENTRE
            ),
            setOf("shopping") to listOf(FILTER_SHOPPING),
            setOf("transport") to listOf(FILTER_TRANSPORTATION),
            setOf("education:school", "education:library") to listOf(FILTER_SCHOOL, FILTER_LIBRARY),
            setOf("accommodation") to listOf(FILTER_ACCOMMODATION),
            setOf("entertainment") to listOf(FILTER_ENTERTAINMENT),
            setOf("sports") to listOf(FILTER_SPORTS),
            setOf("finance:bank", "finance:atm") to listOf(FILTER_BANK, FILTER_ATM),
            setOf("government") to listOf(FILTER_GOVERNMENT),
            setOf("services:laundry", "services:car_wash") to listOf(FILTER_LAUNDRY, FILTER_CAR_WASH),
            setOf("food:pizza", "food:chicken") to listOf("food:pizza", "food:chicken")
        )

        cases.forEach { (selectedCategories, expectedEffectiveCategories) ->
            assertEquals(expectedEffectiveCategories, policy.effectiveCategories(selectedCategories))
        }
    }
}
