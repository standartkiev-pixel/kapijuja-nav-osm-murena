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

package earth.maps.cardinal.ui.core

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardinalNavigatorTest {
    @Test
    fun navigate_addsRouteToBackStack() {
        val navigator = navigator()

        navigator.navigate(CardinalRoute.Settings)

        assertEquals(
            listOf(CardinalRoute.HomeSearch, CardinalRoute.Settings),
            navigator.backStack
        )
        assertEquals(CardinalRoute.Settings, navigator.currentRoute)
        assertEquals(CardinalRoute.HomeSearch, navigator.previousRoute)
    }

    @Test
    fun navigate_avoidCycles_removesPreviousRouteOfSameType() {
        val navigator = navigator()

        navigator.navigate(CardinalRoute.Settings)
        navigator.navigate(CardinalRoute.RoutingProfiles)
        navigator.navigate(CardinalRoute.Settings)

        assertEquals(
            listOf(CardinalRoute.HomeSearch, CardinalRoute.Settings),
            navigator.backStack
        )
    }

    @Test
    fun navigate_withoutAvoidCycles_allowsNestedSavedLists() {
        val navigator = navigator()

        navigator.navigate(CardinalRoute.ManagePlaces(listId = "root"))
        navigator.navigate(
            CardinalRoute.ManagePlaces(listId = "child", parents = listOf("Root")),
            avoidCycles = false
        )

        assertEquals(
            listOf(
                CardinalRoute.HomeSearch,
                CardinalRoute.ManagePlaces(listId = "root"),
                CardinalRoute.ManagePlaces(listId = "child", parents = listOf("Root"))
            ),
            navigator.backStack
        )
    }

    @Test
    fun navigate_popUpToHome_resetsStackBeforeOpeningRoute() {
        val navigator = navigator()

        navigator.navigate(CardinalRoute.Settings)
        navigator.navigate(CardinalRoute.NearbyPoi, popUpToHome = true)

        assertEquals(
            listOf(CardinalRoute.HomeSearch, CardinalRoute.NearbyPoi),
            navigator.backStack
        )
    }

    @Test
    fun navigate_popUpToHomeToHome_leavesOnlyHome() {
        val navigator = navigator()

        navigator.navigate(CardinalRoute.Settings)
        navigator.navigate(CardinalRoute.HomeSearch, popUpToHome = true)

        assertEquals(listOf(CardinalRoute.HomeSearch), navigator.backStack)
    }

    @Test
    fun goBack_multipleSteps_removesRequestedEntries() {
        val navigator = navigator()

        navigator.navigate(CardinalRoute.ManagePlaces(listId = "root"))
        navigator.navigate(
            CardinalRoute.ManagePlaces(listId = "child", parents = listOf("Root")),
            avoidCycles = false
        )

        assertTrue(navigator.goBack(2))

        assertEquals(listOf(CardinalRoute.HomeSearch), navigator.backStack)
    }

    @Test
    fun goBack_atRoot_returnsFalse() {
        val navigator = navigator()

        assertFalse(navigator.goBack())
        assertEquals(listOf(CardinalRoute.HomeSearch), navigator.backStack)
    }

    private fun navigator(): CardinalNavigator =
        CardinalNavigator(mutableListOf<NavKey>(CardinalRoute.HomeSearch))
}
