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

class CardinalNavigator(
    val backStack: MutableList<NavKey>
) {
    val currentRoute: CardinalRoute
        get() = backStack.lastOrNull() as? CardinalRoute ?: CardinalRoute.HomeSearch

    val previousRoute: CardinalRoute?
        get() = backStack.dropLast(1).lastOrNull() as? CardinalRoute

    fun navigate(
        route: CardinalRoute,
        avoidCycles: Boolean = true,
        popUpToHome: Boolean = false,
    ) {
        if (backStack.isEmpty()) {
            backStack.add(CardinalRoute.HomeSearch)
        }

        if (popUpToHome) {
            clearToHome()
            if (route != CardinalRoute.HomeSearch) {
                backStack.add(route)
            }
            return
        }

        if (avoidCycles) {
            val cycleStart = backStack.indexOfLast {
                it is CardinalRoute && it::class == route::class
            }
            if (cycleStart >= 0) {
                removeFrom(cycleStart)
            }
        }

        if (route == CardinalRoute.HomeSearch) {
            clearToHome()
        } else {
            backStack.add(route)
        }
    }

    fun goBack(steps: Int = 1): Boolean {
        var didGoBack = false
        repeat(steps.coerceAtLeast(0)) {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
                didGoBack = true
            }
        }
        return didGoBack
    }

    private fun clearToHome() {
        backStack.clear()
        backStack.add(CardinalRoute.HomeSearch)
    }

    private fun removeFrom(index: Int) {
        while (backStack.lastIndex >= index) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}
