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

package earth.maps.cardinal.ui.home

import earth.maps.cardinal.R.string
import earth.maps.cardinal.geocoding.FILTER_ACCOMMODATION
import earth.maps.cardinal.geocoding.FILTER_ATM
import earth.maps.cardinal.geocoding.FILTER_BAKERY
import earth.maps.cardinal.geocoding.FILTER_BANK
import earth.maps.cardinal.geocoding.FILTER_BAR
import earth.maps.cardinal.geocoding.FILTER_BOOKS
import earth.maps.cardinal.geocoding.FILTER_BUS_STATION
import earth.maps.cardinal.geocoding.FILTER_CAR_REPAIR
import earth.maps.cardinal.geocoding.FILTER_CAR_WASH
import earth.maps.cardinal.geocoding.FILTER_CINEMA
import earth.maps.cardinal.geocoding.FILTER_CLINIC
import earth.maps.cardinal.geocoding.FILTER_CLOTHES
import earth.maps.cardinal.geocoding.FILTER_COCKTAIL
import earth.maps.cardinal.geocoding.FILTER_COLLEGE
import earth.maps.cardinal.geocoding.FILTER_CONVENIENCE
import earth.maps.cardinal.geocoding.FILTER_COURTHOUSE
import earth.maps.cardinal.geocoding.FILTER_DENTIST
import earth.maps.cardinal.geocoding.FILTER_DOCTOR
import earth.maps.cardinal.geocoding.FILTER_ELECTRONICS
import earth.maps.cardinal.geocoding.FILTER_ENTERTAINMENT
import earth.maps.cardinal.geocoding.FILTER_FINANCE
import earth.maps.cardinal.geocoding.FILTER_FUEL
import earth.maps.cardinal.geocoding.FILTER_GOLF
import earth.maps.cardinal.geocoding.FILTER_GOVERNMENT
import earth.maps.cardinal.geocoding.FILTER_GUEST_HOUSE
import earth.maps.cardinal.geocoding.FILTER_GYM
import earth.maps.cardinal.geocoding.FILTER_HAIRDRESSER
import earth.maps.cardinal.geocoding.FILTER_HOSPITAL
import earth.maps.cardinal.geocoding.FILTER_HOSTEL
import earth.maps.cardinal.geocoding.FILTER_HOTEL
import earth.maps.cardinal.geocoding.FILTER_ICE_CREAM
import earth.maps.cardinal.geocoding.FILTER_LAUNDRY
import earth.maps.cardinal.geocoding.FILTER_LIBRARY
import earth.maps.cardinal.geocoding.FILTER_LOUNGE
import earth.maps.cardinal.geocoding.FILTER_MALL
import earth.maps.cardinal.geocoding.FILTER_MEDICAL_CENTRE
import earth.maps.cardinal.geocoding.FILTER_MUSEUM
import earth.maps.cardinal.geocoding.FILTER_PARKING
import earth.maps.cardinal.geocoding.FILTER_PHARMACY
import earth.maps.cardinal.geocoding.FILTER_POLICE
import earth.maps.cardinal.geocoding.FILTER_POST_OFFICE
import earth.maps.cardinal.geocoding.FILTER_PUB
import earth.maps.cardinal.geocoding.FILTER_RESTOBAR
import earth.maps.cardinal.geocoding.FILTER_SCHOOL
import earth.maps.cardinal.geocoding.FILTER_SERVICES
import earth.maps.cardinal.geocoding.FILTER_SHOPPING
import earth.maps.cardinal.geocoding.FILTER_SPORTS
import earth.maps.cardinal.geocoding.FILTER_STADIUM
import earth.maps.cardinal.geocoding.FILTER_SUBWAY
import earth.maps.cardinal.geocoding.FILTER_SUPERMARKET
import earth.maps.cardinal.geocoding.FILTER_SWIMMING_POOL
import earth.maps.cardinal.geocoding.FILTER_TAXI
import earth.maps.cardinal.geocoding.FILTER_THEATRE
import earth.maps.cardinal.geocoding.FILTER_TRAIN_STATION
import earth.maps.cardinal.geocoding.FILTER_TRANSPORTATION
import earth.maps.cardinal.geocoding.FILTER_UNIVERSITY
import earth.maps.cardinal.geocoding.FILTER_VETERINARY
import earth.maps.cardinal.geocoding.FILTER_ZOO
import javax.inject.Inject

data class FilterChipSpec(
    val category: String,
    val labelResource: Int,
    val queryCategory: String = category
)

data class NearbyCategorySpec(
    val category: String,
    val labelResource: Int,
    val subcategories: List<FilterChipSpec>,
    val queryCategory: String = category
)

data class NearbyAppliedFilterState(
    val selectedCategories: Set<String>,
    val searchQuery: String,
    val effectiveCategories: List<String>,
    val isFilterApplied: Boolean
)

internal sealed interface NearbyFilterQuery {
    data class Categories(val categories: List<String>) : NearbyFilterQuery
    data class TextSearch(val query: String) : NearbyFilterQuery
}

class NearbyFilterPolicy @Inject constructor() {
    val allCategories = listOf(
        FilterChipSpec("food", string.category_food),
        FilterChipSpec("food:coffee_shop", string.category_coffee_shop),
        FilterChipSpec("recreation", string.category_recreation),
        FilterChipSpec("health", string.category_health),
        FilterChipSpec("transportation", string.category_transportation, queryCategory = FILTER_TRANSPORTATION),
        FilterChipSpec("entertainment", string.category_entertainment, queryCategory = FILTER_ENTERTAINMENT),
        FilterChipSpec("nightlife", string.category_nightlife),
        FilterChipSpec("accommodation", string.category_accommodation, queryCategory = FILTER_ACCOMMODATION),
    )

    val categoryFilters = listOf(
        NearbyCategorySpec(
            category = "food",
            labelResource = string.category_food,
            subcategories = listOf(
                FilterChipSpec("food:restaurant", string.category_food_restaurant, queryCategory = "food"),
                FilterChipSpec("food:cafe", string.category_food_cafe, queryCategory = "food:coffee_shop"),
                FilterChipSpec("food:coffee_shop", string.category_food_coffee_shop),
                FilterChipSpec("food:bakery", string.category_food_bakery, queryCategory = FILTER_BAKERY),
                FilterChipSpec("food:ice_cream", string.category_food_ice_cream, queryCategory = FILTER_ICE_CREAM),
                FilterChipSpec("food:pizza", string.category_food_pizza),
                FilterChipSpec("food:chicken", string.category_food_chicken),
            )
        ),
        NearbyCategorySpec(
            category = "nightlife",
            labelResource = string.category_nightlife,
            subcategories = listOf(
                FilterChipSpec("nightlife:bar", string.category_food_bar, queryCategory = FILTER_BAR),
                FilterChipSpec("nightlife:pub", string.category_food_pub, queryCategory = FILTER_PUB),
                FilterChipSpec("nightlife:cocktail", string.category_food_cocktail, queryCategory = FILTER_COCKTAIL),
                FilterChipSpec("nightlife:lounge", string.category_food_lounge, queryCategory = FILTER_LOUNGE),
                FilterChipSpec("nightlife:restobar", string.category_food_restobar, queryCategory = FILTER_RESTOBAR),
            )
        ),
        NearbyCategorySpec(
            category = "health",
            labelResource = string.category_health,
            subcategories = listOf(
                FilterChipSpec("health:hospital", string.category_health_hospital, queryCategory = FILTER_HOSPITAL),
                FilterChipSpec(
                    "health:medical_centre",
                    string.category_health_medical_centre,
                    queryCategory = FILTER_MEDICAL_CENTRE
                ),
                FilterChipSpec("health:clinic", string.category_health_clinic, queryCategory = FILTER_CLINIC),
                FilterChipSpec("health:doctor", string.category_health_doctor, queryCategory = FILTER_DOCTOR),
                FilterChipSpec("health:dentist", string.category_health_dentist, queryCategory = FILTER_DENTIST),
                FilterChipSpec("health:pharmacy", string.category_health_pharmacy, queryCategory = FILTER_PHARMACY),
                FilterChipSpec("health:veterinary", string.category_health_veterinary, queryCategory = FILTER_VETERINARY),
            )
        ),
        NearbyCategorySpec(
            category = "shopping",
            labelResource = string.category_shopping,
            queryCategory = FILTER_SHOPPING,
            subcategories = listOf(
                FilterChipSpec(
                    "shopping:supermarket",
                    string.category_shopping_supermarket,
                    queryCategory = FILTER_SUPERMARKET
                ),
                FilterChipSpec("shopping:mall", string.category_shopping_mall, queryCategory = FILTER_MALL),
                FilterChipSpec(
                    "shopping:convenience",
                    string.category_shopping_convenience,
                    queryCategory = FILTER_CONVENIENCE
                ),
                FilterChipSpec(
                    "shopping:clothes",
                    string.category_shopping_clothes,
                    queryCategory = FILTER_CLOTHES
                ),
                FilterChipSpec(
                    "shopping:electronics",
                    string.category_shopping_electronics,
                    queryCategory = FILTER_ELECTRONICS
                ),
                FilterChipSpec("shopping:books", string.category_shopping_books, queryCategory = FILTER_BOOKS),
            )
        ),
        NearbyCategorySpec(
            category = "transport",
            labelResource = string.category_transportation,
            queryCategory = FILTER_TRANSPORTATION,
            subcategories = listOf(
                FilterChipSpec(
                    "transport:bus_station",
                    string.category_transport_bus_station,
                    queryCategory = FILTER_BUS_STATION
                ),
                FilterChipSpec(
                    "transport:train_station",
                    string.category_transport_train_station,
                    queryCategory = FILTER_TRAIN_STATION
                ),
                FilterChipSpec("transport:subway", string.category_transport_subway, queryCategory = FILTER_SUBWAY),
                FilterChipSpec("transport:taxi", string.category_transport_taxi, queryCategory = FILTER_TAXI),
                FilterChipSpec("transport:parking", string.category_transport_parking, queryCategory = FILTER_PARKING),
                FilterChipSpec("transport:fuel", string.category_transport_fuel, queryCategory = FILTER_FUEL),
            )
        ),
        NearbyCategorySpec(
            category = "education",
            labelResource = string.category_education,
            subcategories = listOf(
                FilterChipSpec("education:school", string.category_education_school, queryCategory = FILTER_SCHOOL),
                FilterChipSpec("education:college", string.category_education_college, queryCategory = FILTER_COLLEGE),
                FilterChipSpec(
                    "education:university",
                    string.category_education_university,
                    queryCategory = FILTER_UNIVERSITY
                ),
                FilterChipSpec("education:library", string.category_education_library, queryCategory = FILTER_LIBRARY),
            )
        ),
        NearbyCategorySpec(
            category = "accommodation",
            labelResource = string.category_accommodation,
            queryCategory = FILTER_ACCOMMODATION,
            subcategories = listOf(
                FilterChipSpec(
                    "accommodation:hotel",
                    string.category_accommodation_hotel,
                    queryCategory = FILTER_HOTEL
                ),
                FilterChipSpec(
                    "accommodation:hostel",
                    string.category_accommodation_hostel,
                    queryCategory = FILTER_HOSTEL
                ),
                FilterChipSpec(
                    "accommodation:guest_house",
                    string.category_accommodation_guest_house,
                    queryCategory = FILTER_GUEST_HOUSE
                ),
            )
        ),
        NearbyCategorySpec(
            category = "entertainment",
            labelResource = string.category_entertainment,
            queryCategory = FILTER_ENTERTAINMENT,
            subcategories = listOf(
                FilterChipSpec(
                    "entertainment:cinema",
                    string.category_entertainment_cinema,
                    queryCategory = FILTER_CINEMA
                ),
                FilterChipSpec(
                    "entertainment:theatre",
                    string.category_entertainment_theatre,
                    queryCategory = FILTER_THEATRE
                ),
                FilterChipSpec(
                    "entertainment:museum",
                    string.category_entertainment_museum,
                    queryCategory = FILTER_MUSEUM
                ),
                FilterChipSpec("entertainment:zoo", string.category_entertainment_zoo, queryCategory = FILTER_ZOO),
            )
        ),
        NearbyCategorySpec(
            category = "sports",
            labelResource = string.category_sports,
            queryCategory = FILTER_SPORTS,
            subcategories = listOf(
                FilterChipSpec("sports:gym", string.category_sports_gym, queryCategory = FILTER_GYM),
                FilterChipSpec("sports:stadium", string.category_sports_stadium, queryCategory = FILTER_STADIUM),
                FilterChipSpec(
                    "sports:swimming_pool",
                    string.category_sports_swimming_pool,
                    queryCategory = FILTER_SWIMMING_POOL
                ),
                FilterChipSpec("sports:golf", string.category_sports_golf, queryCategory = FILTER_GOLF),
            )
        ),
        NearbyCategorySpec(
            category = "finance",
            labelResource = string.category_finance,
            queryCategory = FILTER_FINANCE,
            subcategories = listOf(
                FilterChipSpec("finance:bank", string.category_finance_bank, queryCategory = FILTER_BANK),
                FilterChipSpec("finance:atm", string.category_finance_atm, queryCategory = FILTER_ATM),
            )
        ),
        NearbyCategorySpec(
            category = "government",
            labelResource = string.category_government,
            queryCategory = FILTER_GOVERNMENT,
            subcategories = listOf(
                FilterChipSpec(
                    "government:police",
                    string.category_government_police,
                    queryCategory = FILTER_POLICE
                ),
                FilterChipSpec(
                    "government:post_office",
                    string.category_government_post_office,
                    queryCategory = FILTER_POST_OFFICE
                ),
                FilterChipSpec(
                    "government:courthouse",
                    string.category_government_courthouse,
                    queryCategory = FILTER_COURTHOUSE
                ),
            )
        ),
        NearbyCategorySpec(
            category = "services",
            labelResource = string.category_services,
            queryCategory = FILTER_SERVICES,
            subcategories = listOf(
                FilterChipSpec("services:laundry", string.category_services_laundry, queryCategory = FILTER_LAUNDRY),
                FilterChipSpec(
                    "services:hairdresser",
                    string.category_services_hairdresser,
                    queryCategory = FILTER_HAIRDRESSER
                ),
                FilterChipSpec(
                    "services:car_repair",
                    string.category_services_car_repair,
                    queryCategory = FILTER_CAR_REPAIR
                ),
                FilterChipSpec("services:car_wash", string.category_services_car_wash, queryCategory = FILTER_CAR_WASH),
            )
        ),
    )

    private val categoryQueryBySelection = buildMap {
        allCategories.forEach { chipSpec ->
            put(chipSpec.category, chipSpec.queryCategory)
        }
        categoryFilters.forEach { categorySpec ->
            put(categorySpec.category, categorySpec.queryCategory)
            categorySpec.subcategories.forEach { chipSpec ->
                put(chipSpec.category, chipSpec.queryCategory)
            }
        }
    }

    fun appliedFilterState(
        draftSelectedCategories: Set<String>,
        draftCategorySearchQuery: String
    ): NearbyAppliedFilterState {
        val selectedCategories = draftSelectedCategories.toSet()
        val searchQuery = if (selectedCategories.isEmpty()) {
            draftCategorySearchQuery.trim()
        } else {
            ""
        }
        return NearbyAppliedFilterState(
            selectedCategories = selectedCategories,
            searchQuery = searchQuery,
            effectiveCategories = effectiveCategories(selectedCategories),
            isFilterApplied = isFilterApplied(selectedCategories, searchQuery)
        )
    }

    fun effectiveCategories(selectedCategories: Set<String>): List<String> {
        if (selectedCategories.isEmpty()) {
            return emptyList()
        }
        return selectedCategories
            .map { category -> categoryQueryBySelection[category] ?: category }
            .distinct()
    }

    fun isFilterApplied(
        selectedCategories: Set<String>,
        searchQuery: String
    ): Boolean {
        return selectedCategories.isNotEmpty() || searchQuery.isNotBlank()
    }

    internal fun queryForAppliedFilters(
        selectedCategories: Set<String>,
        searchQuery: String
    ): NearbyFilterQuery {
        val trimmedSearchQuery = searchQuery.trim()
        return if (selectedCategories.isEmpty() && trimmedSearchQuery.isNotEmpty()) {
            NearbyFilterQuery.TextSearch(trimmedSearchQuery)
        } else {
            NearbyFilterQuery.Categories(effectiveCategories(selectedCategories))
        }
    }
}
