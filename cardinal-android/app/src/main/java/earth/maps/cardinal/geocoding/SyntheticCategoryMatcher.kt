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

package earth.maps.cardinal.geocoding

import earth.maps.cardinal.data.GeocodeResult
import javax.inject.Inject

private val BAR_MATCH_WORDS = listOf("bar", "bars")
private val BAKERY_MATCH_TERMS = listOf(
    "bakery",
    "bakeries",
    "boulangerie",
    "cake",
    "cakes",
    "patisserie",
    "pastry"
)
private val BOOKS_MATCH_WORDS = listOf("book", "books", "stationery")
private val BOOKS_MATCH_TERMS = listOf("book store", "bookstore", "bookshop")
private val BUS_STATION_MATCH_TERMS = listOf("bus depot", "bus stand", "bus station", "bus stop", "bus terminal")
private val CLINIC_MATCH_WORDS = listOf("clinic", "clinics")
private val CLINIC_MATCH_TERMS = listOf("medical clinic", "polyclinic")
private val CLOTHES_MATCH_WORDS = listOf("clothes", "clothing")
private val CLOTHES_MATCH_TERMS = listOf("apparel", "boutique", "fashion", "garment", "garments", "readymade", "saree")
private val COCKTAIL_MATCH_WORDS = listOf("cocktail", "cocktails")
private val CONVENIENCE_MATCH_TERMS = listOf(
    "convenience",
    "departmental store",
    "general store",
    "kirana",
    "mini market",
    "minimarket",
    "provision store"
)
private val DENTIST_MATCH_WORDS = listOf("dentist", "dentists", "dental", "orthodontist", "orthodontists")
private val DENTIST_MATCH_TERMS = listOf("dental clinic", "dental centre", "dental center")
private val DOCTOR_MATCH_WORDS = listOf("doctor", "doctors", "dr", "physician", "physicians")
private val DOCTOR_MATCH_TERMS = listOf("doctor clinic", "general practitioner")
private val ELECTRONICS_MATCH_WORDS = listOf("electronics")
private val ELECTRONICS_MATCH_TERMS = listOf(
    "appliance",
    "appliances",
    "computer store",
    "electronic",
    "mobile",
    "phone store",
    "mobile store"
)
private val ENTERTAINMENT_MATCH_WORDS = listOf("museum", "museums", "zoo", "zoos")
private val ENTERTAINMENT_MATCH_TERMS = listOf("art gallery", "gallery", "zoological garden", "zoological park")
private val FUEL_MATCH_WORDS = listOf("fuel", "diesel", "cng")
private val FUEL_MATCH_TERMS = listOf("gas station", "petrol pump", "petrol station")
private val HOSPITAL_MATCH_WORDS = listOf("hospital", "hospitals")
private val HOSTEL_MATCH_WORDS = listOf("hostel", "hostels")
private val HOSTEL_MATCH_TERMS = listOf("dormitory", "youth hostel")
private val ICE_CREAM_MATCH_TERMS = listOf(
    "apsara",
    "baskin",
    "cream",
    "frozen dessert",
    "icecream",
    "ice cream",
    "ice_cream",
    "gelato",
    "gelati",
    "havmor",
    "ibaco",
    "kulfi",
    "naturals",
    "scoop",
    "sundae"
)
private val LIBRARY_MATCH_WORDS = listOf("library", "libraries")
private val LIBRARY_MATCH_TERMS = listOf("public library", "reading room")
private val LOUNGE_MATCH_WORDS = listOf("lounge", "lounges")
private val MALL_MATCH_WORDS = listOf("mall", "malls")
private val MALL_MATCH_TERMS = listOf("galleria", "plaza", "shopping center", "shopping centre", "shopping mall")
private val MEDICAL_CENTRE_MATCH_TERMS = listOf("medical cent")
private val MUSEUM_MATCH_WORDS = listOf("museum", "museums")
private val MUSEUM_MATCH_TERMS = listOf("art gallery", "gallery")
private val PARKING_MATCH_WORDS = listOf("parking")
private val PARKING_MATCH_TERMS = listOf("car park", "parking garage", "parking lot")
private val PHARMACY_MATCH_WORDS = listOf("chemist", "chemists", "pharmacy", "pharmacies", "drugstore", "drugstores")
private val PHARMACY_MATCH_TERMS = listOf("medical store")
private val PUB_MATCH_WORDS = listOf("pub", "pubs")
private val PUB_MATCH_TERMS = listOf("alehouse", "brewery", "brewpub", "tavern")
private val RESTOBAR_MATCH_WORDS = listOf("restobar", "restobars")
private val SUBWAY_MATCH_TERMS = listOf("metro station", "subway station", "underground station")
private val SUPERMARKET_MATCH_WORDS = listOf("supermarket", "supermarkets")
private val SUPERMARKET_MATCH_TERMS = listOf(
    "big bazaar",
    "d mart",
    "d-mart",
    "grocery",
    "grocery store",
    "hypermarket",
    "kirana",
    "mart",
    "market"
)
private val TAXI_MATCH_WORDS = listOf("taxi", "cab")
private val TAXI_MATCH_TERMS = listOf("cab stand", "taxi rank", "taxi stand")
private val TRAIN_STATION_MATCH_WORDS = listOf("railway")
private val TRAIN_STATION_MATCH_TERMS = listOf("rail station", "railway station", "train station")
private val UNIVERSITY_MATCH_WORDS = listOf("university", "universities")
private val UNIVERSITY_MATCH_TERMS = listOf("university campus")
private val ZOO_MATCH_WORDS = listOf("zoo", "zoos")
private val ZOO_MATCH_TERMS = listOf("zoological garden", "zoological park")

private val ATM_MATCH_WORDS = listOf("atm", "cdm")
private val ATM_MATCH_TERMS = listOf(
    "atm center",
    "atm centre",
    "automated teller",
    "cash deposit machine",
    "cash dispenser",
    "cash machine",
    "cashpoint"
)
private val BANK_MATCH_WORDS = listOf("bank", "banks")
private val BANK_MATCH_TERMS = listOf("bank branch", "credit union")
private val CAR_REPAIR_CONTEXT_TERMS = listOf("auto", "car", "motor", "vehicle")
private val CAR_REPAIR_CONTEXT_WORDS = listOf("garage")
private val CAR_REPAIR_EXCLUDED_TERMS = listOf("parking garage")
private val CAR_REPAIR_MATCH_WORDS = listOf("mechanic")
private val CAR_REPAIR_MATCH_TERMS = listOf("auto repair", "automobile repair", "car repair", "car service", "vehicle repair")
private val CAR_WASH_MATCH_TERMS = listOf("auto wash", "car wash", "carwash", "vehicle wash")
private val CINEMA_MATCH_WORDS = listOf("cinema", "cinemas", "multiplex")
private val CINEMA_MATCH_TERMS = listOf(
    "carnival",
    "cinemax",
    "cinepolis",
    "devgan",
    "inox",
    "miraj",
    "movie hall",
    "movie theater",
    "movie theatre",
    "moviemax",
    "mukta",
    "picture hall",
    "pvr",
    "talkies"
)
private val COURTHOUSE_EXCLUDED_TERMS = listOf("basketball court", "food court", "tennis court")
private val COURTHOUSE_MATCH_WORDS = listOf("courthouse")
private val COURTHOUSE_MATCH_TERMS = listOf("court house", "district court", "high court", "sessions court")
private val GOLF_MATCH_WORDS = listOf("golf")
private val GOLF_MATCH_TERMS = listOf("driving range", "golf club", "golf course")
private val GOVERNMENT_MATCH_TERMS = listOf(
    "civic center",
    "civic centre",
    "government office",
    "municipal office",
    "municipality"
)
private val GUEST_HOUSE_MATCH_TERMS = listOf(
    "bed and breakfast",
    "bnb",
    "guest house",
    "guest houses",
    "guest_house",
    "guesthouse",
    "homestay"
)
private val GYM_MATCH_WORDS = listOf("gym", "gyms", "gymnasium")
private val GYM_MATCH_TERMS = listOf("fitness", "fitness center", "fitness centre", "fitness studio", "health club")
private val HAIRDRESSER_CONTEXT_TERMS = listOf("beauty", "hair")
private val HAIRDRESSER_CONTEXT_WORDS = listOf("salon")
private val HAIRDRESSER_MATCH_WORDS = listOf("barber", "hairdresser")
private val HAIRDRESSER_MATCH_TERMS = listOf("beauty salon", "hair salon")
private val HOTEL_MATCH_WORDS = listOf(
    "hotel",
    "hotels",
    "inn",
    "inns",
    "lodge",
    "lodges",
    "motel",
    "motels",
    "resort",
    "resorts"
)
private val HOTEL_MATCH_TERMS = listOf("lodging", "tourist home")
private val LAUNDRY_MATCH_WORDS = listOf("laundry", "laundromat")
private val LAUNDRY_MATCH_TERMS = listOf("dry cleaner", "dry cleaning", "wash and fold")
private val POLICE_MATCH_WORDS = listOf("police", "thana")
private val POLICE_MATCH_TERMS = listOf("police chowki", "police station", "police thana")
private val POST_OFFICE_MATCH_TERMS = listOf("india post", "post office", "post_office", "postal office")
private val SCHOOL_MATCH_WORDS = listOf("school", "schools", "kindergarten", "preschool")
private val SCHOOL_MATCH_TERMS = listOf(
    "high school",
    "international school",
    "primary school",
    "public school",
    "secondary school",
    "vidyalaya"
)
private val SHOPPING_MATCH_WORDS = listOf("shop", "shops", "shopping", "store", "stores", "mart", "marts", "market", "markets")
private val SHOPPING_MATCH_TERMS = listOf(
    "bazaar",
    "boutique",
    "departmental store",
    "electronics",
    "fashion",
    "galleria",
    "general store",
    "grocery",
    "mall",
    "plaza",
    "retail",
    "shopping center",
    "shopping centre",
    "supermarket"
)
private val SPORTS_MATCH_WORDS = listOf("sport", "sports")
private val SPORTS_MATCH_TERMS = listOf("sports club", "sports complex", "sports ground")
private val STADIUM_MATCH_WORDS = listOf("arena", "stadium", "stadiums")
private val STADIUM_MATCH_TERMS = listOf("sports complex", "sports ground")
private val SWIMMING_POOL_CONTEXT_TERMS = listOf("aquatic", "swim", "swimming")
private val SWIMMING_POOL_CONTEXT_WORDS = listOf("pool")
private val SWIMMING_POOL_MATCH_TERMS = listOf(
    "aquatic center",
    "aquatic centre",
    "natatorium",
    "swimming pool",
    "swimming pools"
)
private val THEATRE_MATCH_WORDS = listOf("theatre", "theatres", "theater", "theaters")
private val THEATRE_MATCH_TERMS = listOf("auditorium", "performing arts", "playhouse")
private val TRANSPORTATION_MATCH_WORDS = listOf("cab", "cng", "fuel", "railway", "taxi")
private val TRANSPORTATION_MATCH_TERMS = listOf(
    "bus depot",
    "bus stand",
    "bus station",
    "bus stop",
    "bus terminal",
    "cab stand",
    "car park",
    "gas station",
    "metro station",
    "parking",
    "petrol pump",
    "petrol station",
    "rail station",
    "railway station",
    "subway station",
    "taxi rank",
    "taxi stand",
    "train station",
    "transit",
    "transport"
)
private val ACCOMMODATION_MATCH_TERMS = listOf("accommodation", "dormitory", "lodging", "youth hostel")
private val COLLEGE_MATCH_WORDS = listOf("college", "colleges")
private val COLLEGE_MATCH_TERMS = listOf("community college", "degree college", "institute of technology", "junior college")
private val VETERINARY_ANIMAL_TERMS = listOf("pet", "pets", "animal", "animals")
private val VETERINARY_CARE_WORDS = listOf("clinic", "hospital")
private val VETERINARY_MATCH_WORDS = listOf("veterinary", "veterinarian", "veterinarians", "vet", "vets")
private val VETERINARY_MATCH_TERMS = listOf("speciality clinic", "specialty clinic")

internal class SyntheticCategoryMatcher @Inject constructor() {
    private val syntheticCategoryMatchers: Map<String, GeocodeResult.() -> Boolean> = mapOf(
        FILTER_ACCOMMODATION to { matchesAccommodation() },
        FILTER_ATM to { matchesAtm() },
        FILTER_BAR to { matchesAnyWholeWord(BAR_MATCH_WORDS) },
        FILTER_BAKERY to { matchesAny(BAKERY_MATCH_TERMS) },
        FILTER_BANK to { matchesBank() },
        FILTER_BOOKS to {
            matchesAnyWholeWord(BOOKS_MATCH_WORDS) || matchesAny(BOOKS_MATCH_TERMS)
        },
        FILTER_BUS_STATION to { matchesAny(BUS_STATION_MATCH_TERMS) },
        FILTER_CAR_REPAIR to { matchesCarRepair() },
        FILTER_CAR_WASH to { matchesCarWash() },
        FILTER_CINEMA to { matchesCinema() },
        FILTER_CLINIC to {
            matchesAnyWholeWord(CLINIC_MATCH_WORDS) || matchesAny(CLINIC_MATCH_TERMS)
        },
        FILTER_CLOTHES to {
            matchesAnyWholeWord(CLOTHES_MATCH_WORDS) || matchesAny(CLOTHES_MATCH_TERMS)
        },
        FILTER_COCKTAIL to { matchesAnyWholeWord(COCKTAIL_MATCH_WORDS) },
        FILTER_COLLEGE to { matchesCollege() },
        FILTER_CONVENIENCE to { matchesAny(CONVENIENCE_MATCH_TERMS) },
        FILTER_COURTHOUSE to { matchesCourthouse() },
        FILTER_DENTIST to {
            matchesAnyWholeWord(DENTIST_MATCH_WORDS) || matchesAny(DENTIST_MATCH_TERMS)
        },
        FILTER_DOCTOR to { matchesDoctor() },
        FILTER_ELECTRONICS to {
            matchesAnyWholeWord(ELECTRONICS_MATCH_WORDS) || matchesAny(ELECTRONICS_MATCH_TERMS)
        },
        FILTER_ENTERTAINMENT to { matchesEntertainment() },
        FILTER_FINANCE to { matchesFinance() },
        FILTER_FUEL to {
            matchesAnyWholeWord(FUEL_MATCH_WORDS) || matchesAny(FUEL_MATCH_TERMS)
        },
        FILTER_GOLF to { matchesGolf() },
        FILTER_GOVERNMENT to { matchesGovernment() },
        FILTER_GUEST_HOUSE to { matchesGuestHouse() },
        FILTER_GYM to { matchesGym() },
        FILTER_HAIRDRESSER to { matchesHairdresser() },
        FILTER_HOSPITAL to { matchesAnyWholeWord(HOSPITAL_MATCH_WORDS) },
        FILTER_HOSTEL to {
            matchesAnyWholeWord(HOSTEL_MATCH_WORDS) || matchesAny(HOSTEL_MATCH_TERMS)
        },
        FILTER_HOTEL to { matchesHotel() },
        FILTER_ICE_CREAM to { matchesAny(ICE_CREAM_MATCH_TERMS) },
        FILTER_LAUNDRY to { matchesLaundry() },
        FILTER_LIBRARY to {
            matchesAnyWholeWord(LIBRARY_MATCH_WORDS) || matchesAny(LIBRARY_MATCH_TERMS)
        },
        FILTER_LOUNGE to { matchesAnyWholeWord(LOUNGE_MATCH_WORDS) },
        FILTER_MALL to {
            matchesAnyWholeWord(MALL_MATCH_WORDS) || matchesAny(MALL_MATCH_TERMS)
        },
        FILTER_MEDICAL_CENTRE to { matchesAny(MEDICAL_CENTRE_MATCH_TERMS) },
        FILTER_MUSEUM to {
            matchesAnyWholeWord(MUSEUM_MATCH_WORDS) || matchesAny(MUSEUM_MATCH_TERMS)
        },
        FILTER_PARKING to {
            matchesAnyWholeWord(PARKING_MATCH_WORDS) || matchesAny(PARKING_MATCH_TERMS)
        },
        FILTER_PHARMACY to {
            matchesAnyWholeWord(PHARMACY_MATCH_WORDS) || matchesAny(PHARMACY_MATCH_TERMS)
        },
        FILTER_POLICE to { matchesPolice() },
        FILTER_POST_OFFICE to { matchesPostOffice() },
        FILTER_PUB to {
            matchesAnyWholeWord(PUB_MATCH_WORDS) || matchesAny(PUB_MATCH_TERMS)
        },
        FILTER_RESTOBAR to { matchesAnyWholeWord(RESTOBAR_MATCH_WORDS) },
        FILTER_SCHOOL to { matchesSchool() },
        FILTER_SERVICES to { matchesServices() },
        FILTER_SHOPPING to { matchesShopping() },
        FILTER_SPORTS to { matchesSports() },
        FILTER_STADIUM to { matchesStadium() },
        FILTER_SUBWAY to { matchesAny(SUBWAY_MATCH_TERMS) },
        FILTER_SUPERMARKET to {
            matchesAnyWholeWord(SUPERMARKET_MATCH_WORDS) || matchesAny(SUPERMARKET_MATCH_TERMS)
        },
        FILTER_SWIMMING_POOL to { matchesSwimmingPool() },
        FILTER_TAXI to {
            matchesAnyWholeWord(TAXI_MATCH_WORDS) || matchesAny(TAXI_MATCH_TERMS)
        },
        FILTER_THEATRE to { matchesTheatre() },
        FILTER_TRAIN_STATION to {
            matchesAnyWholeWord(TRAIN_STATION_MATCH_WORDS) || matchesAny(TRAIN_STATION_MATCH_TERMS)
        },
        FILTER_TRANSPORTATION to { matchesTransportation() },
        FILTER_UNIVERSITY to {
            matchesAnyWholeWord(UNIVERSITY_MATCH_WORDS) || matchesAny(UNIVERSITY_MATCH_TERMS)
        },
        FILTER_VETERINARY to { matchesVeterinary() },
        FILTER_ZOO to {
            matchesAnyWholeWord(ZOO_MATCH_WORDS) || matchesAny(ZOO_MATCH_TERMS)
        },
    )

    fun matches(result: GeocodeResult, selectedCategory: String): Boolean {
        return syntheticCategoryMatchers[selectedCategory]?.invoke(result) ?: false
    }

    fun filterBySyntheticCategories(
        results: List<GeocodeResult>,
        selectedCategories: List<String>
    ): List<GeocodeResult> {
        val syntheticFilters = selectedCategories.filter { it.isSyntheticCategoryFilter() }
        if (syntheticFilters.isEmpty()) {
            return results
        }

        val matchers = syntheticFilters.mapNotNull { syntheticCategoryMatchers[it] }
        if (matchers.isEmpty()) {
            return emptyList()
        }

        return results.filter { result ->
            matchers.any { matcher -> matcher.invoke(result) }
        }
    }

    private fun GeocodeResult.matchesEntertainment(): Boolean {
        return matchesCinema() ||
            matchesTheatre() ||
            matchesAnyWholeWord(ENTERTAINMENT_MATCH_WORDS) ||
            matchesAny(ENTERTAINMENT_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesCinema(): Boolean {
        return matchesAnyWholeWord(CINEMA_MATCH_WORDS) ||
            matchesCinemaMetadata() ||
            matchesAny(CINEMA_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesTheatre(): Boolean {
        return matchesAnyWholeWord(THEATRE_MATCH_WORDS) || matchesAny(THEATRE_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesShopping(): Boolean {
        return matchesAnyWholeWord(SHOPPING_MATCH_WORDS) || matchesAny(SHOPPING_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesFinance(): Boolean {
        return matchesCategory("finance", includeSubcategories = true) ||
            matchesBank() ||
            matchesAtm()
    }

    private fun GeocodeResult.matchesBank(): Boolean {
        val hasBankMetadata = matchesCategory("finance:bank") ||
            hasPropertyValue("amenity", "bank")
        if (hasBankMetadata) {
            return true
        }
        if (matchesAtm()) {
            return false
        }

        return matchesAnyWholeWord(BANK_MATCH_WORDS) || matchesAny(BANK_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesAtm(): Boolean {
        return matchesCategory("finance:atm") ||
            hasPropertyValue("amenity", "atm") ||
            matchesAnyWholeWord(ATM_MATCH_WORDS) ||
            matchesAny(ATM_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesGovernment(): Boolean {
        return matchesCategory("government", includeSubcategories = true) ||
            hasPropertyValue("office", "government") ||
            hasAnyPropertyKey("government") ||
            matchesPolice() ||
            matchesPostOffice() ||
            matchesCourthouse() ||
            matchesAny(GOVERNMENT_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesPolice(): Boolean {
        return matchesCategory("government:police") ||
            hasPropertyValue("amenity", "police") ||
            matchesAnyWholeWord(POLICE_MATCH_WORDS) ||
            matchesAny(POLICE_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesPostOffice(): Boolean {
        return matchesCategory("government:post_office") ||
            hasPropertyValue("amenity", "post_office") ||
            matchesAny(POST_OFFICE_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesCourthouse(): Boolean {
        if (matchesAny(COURTHOUSE_EXCLUDED_TERMS)) {
            return false
        }

        return matchesCategory("government:courthouse") ||
            hasPropertyValue("amenity", "courthouse") ||
            matchesAnyWholeWord(COURTHOUSE_MATCH_WORDS) ||
            matchesAny(COURTHOUSE_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesServices(): Boolean {
        return matchesCategory("services", includeSubcategories = true) ||
            matchesLaundry() ||
            matchesHairdresser() ||
            matchesCarRepair() ||
            matchesCarWash()
    }

    private fun GeocodeResult.matchesLaundry(): Boolean {
        return matchesCategory("services:laundry") ||
            hasPropertyValue("shop", "dry_cleaning", "laundry") ||
            matchesAnyWholeWord(LAUNDRY_MATCH_WORDS) ||
            matchesAny(LAUNDRY_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesHairdresser(): Boolean {
        return matchesCategory("services:hairdresser") ||
            hasPropertyValue("shop", "barber", "beauty", "hairdresser") ||
            matchesAnyWholeWord(HAIRDRESSER_MATCH_WORDS) ||
            matchesAny(HAIRDRESSER_MATCH_TERMS) ||
            (
                matchesAnyWholeWord(HAIRDRESSER_CONTEXT_WORDS) &&
                    matchesAny(HAIRDRESSER_CONTEXT_TERMS)
                )
    }

    private fun GeocodeResult.matchesCarRepair(): Boolean {
        if (matchesAny(CAR_REPAIR_EXCLUDED_TERMS)) {
            return false
        }

        return matchesCategory("services:car_repair") ||
            hasPropertyValue("shop", "car_repair") ||
            matchesAnyWholeWord(CAR_REPAIR_MATCH_WORDS) ||
            matchesAny(CAR_REPAIR_MATCH_TERMS) ||
            (
                matchesAnyWholeWord(CAR_REPAIR_CONTEXT_WORDS) &&
                    matchesAny(CAR_REPAIR_CONTEXT_TERMS)
                )
    }

    private fun GeocodeResult.matchesCarWash(): Boolean {
        return matchesCategory("services:car_wash") ||
            hasPropertyValue("amenity", "car_wash") ||
            matchesAny(CAR_WASH_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesSports(): Boolean {
        return matchesSportsMetadata() ||
            matchesGym() ||
            matchesStadium() ||
            matchesSwimmingPool() ||
            matchesGolf() ||
            matchesAnyWholeWord(SPORTS_MATCH_WORDS) ||
            matchesAny(SPORTS_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesGym(): Boolean {
        return matchesCategory("sports:gym") ||
            hasPropertyValue("leisure", "fitness_centre") ||
            hasPropertyValue("sport", "fitness") ||
            matchesAnyWholeWord(GYM_MATCH_WORDS) ||
            matchesAny(GYM_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesStadium(): Boolean {
        return matchesCategory("sports:stadium") ||
            hasPropertyValue("leisure", "stadium") ||
            matchesAnyWholeWord(STADIUM_MATCH_WORDS) ||
            matchesAny(STADIUM_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesSwimmingPool(): Boolean {
        return matchesCategory("sports:swimming_pool") ||
            hasPropertyValue("leisure", "swimming_pool") ||
            hasPropertyValue("sport", "swimming") ||
            matchesAny(SWIMMING_POOL_MATCH_TERMS) ||
            (
                matchesAnyWholeWord(SWIMMING_POOL_CONTEXT_WORDS) &&
                    matchesAny(SWIMMING_POOL_CONTEXT_TERMS)
                )
    }

    private fun GeocodeResult.matchesGolf(): Boolean {
        return matchesCategory("sports:golf") ||
            hasPropertyValue("leisure", "golf_course") ||
            hasPropertyValue("sport", "golf") ||
            matchesAnyWholeWord(GOLF_MATCH_WORDS) ||
            matchesAny(GOLF_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesTransportation(): Boolean {
        return matchesAny(TRANSPORTATION_MATCH_TERMS) ||
            matchesAnyWholeWord(TRANSPORTATION_MATCH_WORDS)
    }

    private fun GeocodeResult.matchesSchool(): Boolean {
        return matchesAnyWholeWord(SCHOOL_MATCH_WORDS) || matchesAny(SCHOOL_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesCollege(): Boolean {
        return matchesAnyWholeWord(COLLEGE_MATCH_WORDS) || matchesAny(COLLEGE_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesAccommodation(): Boolean {
        return matchesHotel() ||
            matchesGuestHouse() ||
            matchesAnyWholeWord(HOSTEL_MATCH_WORDS) ||
            matchesAny(ACCOMMODATION_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesHotel(): Boolean {
        return matchesAnyWholeWord(HOTEL_MATCH_WORDS) ||
            matchesAny(HOTEL_MATCH_TERMS) ||
            (
                matchesAccommodationMetadata() &&
                    !matchesGuestHouse() &&
                    !matchesAnyWholeWord(HOSTEL_MATCH_WORDS) &&
                    !matchesAny(HOSTEL_MATCH_TERMS)
                )
    }

    private fun GeocodeResult.matchesGuestHouse(): Boolean {
        return matchesAny(GUEST_HOUSE_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesCinemaMetadata(): Boolean {
        return properties.any { (key, value) ->
            val lowerKey = key.lowercase()
            val lowerValue = value.lowercase()
            (
                lowerKey == "amenity" &&
                    lowerValue == "cinema"
                ) ||
                (
                    lowerKey == "category" &&
                        lowerValue.split(',')
                            .map { category -> category.trim() }
                            .any { category ->
                                category == "entertainment:cinema" ||
                                    category == "cinema"
                            }
                    )
        }
    }

    private fun GeocodeResult.matchesAccommodationMetadata(): Boolean {
        return properties.any { (key, value) ->
            val lowerKey = key.lowercase()
            val lowerValue = value.lowercase()
            (
                lowerKey == "tourism" &&
                    lowerValue in setOf("hotel", "motel", "resort", "guest_house", "hostel", "apartment")
                ) ||
                (
                    lowerKey == "category" &&
                        lowerValue.split(',')
                            .map { category -> category.trim() }
                            .any { category ->
                                category == "accommodation" ||
                                    category.startsWith("accommodation:")
                            }
                    )
        }
    }

    private fun GeocodeResult.matchesSportsMetadata(): Boolean {
        return matchesCategory("sports", includeSubcategories = true) ||
            hasPropertyValue("leisure", "fitness_centre", "golf_course", "sports_centre", "stadium", "swimming_pool") ||
            properties.any { (key, value) ->
                key.lowercase() == "sport" && value.isNotBlank()
            }
    }

    private fun GeocodeResult.matchesCategory(
        category: String,
        includeSubcategories: Boolean = false
    ): Boolean {
        val lowercaseCategory = category.lowercase()
        return properties.any { (key, value) ->
            key.lowercase() == "category" &&
                value.lowercase()
                    .split(',')
                    .map { categoryValue -> categoryValue.trim() }
                    .any { categoryValue ->
                        categoryValue == lowercaseCategory ||
                            (includeSubcategories && categoryValue.startsWith("$lowercaseCategory:"))
                    }
        }
    }

    private fun GeocodeResult.hasPropertyValue(key: String, vararg values: String): Boolean {
        val lowercaseKey = key.lowercase()
        val lowercaseValues = values.map { value -> value.lowercase() }.toSet()
        return properties.any { (propertyKey, propertyValue) ->
            propertyKey.lowercase() == lowercaseKey &&
                propertyValue.lowercase() in lowercaseValues
        }
    }

    private fun GeocodeResult.hasAnyPropertyKey(key: String): Boolean {
        val lowercaseKey = key.lowercase()
        return properties.any { (propertyKey, propertyValue) ->
            propertyKey.lowercase() == lowercaseKey && propertyValue.isNotBlank()
        }
    }

    private fun GeocodeResult.matchesDoctor(): Boolean {
        val searchableText = searchableText()
        if (DOCTOR_EXCLUDED_TERMS.any { excludedTerm -> searchableText.contains(excludedTerm) }) {
            return false
        }

        return matchesAnyWholeWord(DOCTOR_MATCH_WORDS) || matchesAny(DOCTOR_MATCH_TERMS)
    }

    private fun GeocodeResult.matchesVeterinary(): Boolean {
        return matchesAnyWholeWord(VETERINARY_MATCH_WORDS) ||
            (
                matchesAny(VETERINARY_ANIMAL_TERMS) &&
                    (
                        matchesAnyWholeWord(VETERINARY_CARE_WORDS) ||
                            matchesAny(VETERINARY_MATCH_TERMS)
                        )
                )
    }

    private fun GeocodeResult.matchesAny(needles: Collection<String>): Boolean {
        val searchableText = searchableText()

        return needles.any { needle -> searchableText.contains(needle.lowercase()) }
    }

    private fun GeocodeResult.matchesAnyWholeWord(words: Collection<String>): Boolean {
        return words.any { word -> matchesWholeWord(word) }
    }

    private fun GeocodeResult.matchesWholeWord(word: String): Boolean {
        return Regex("(^|[^a-z0-9])${Regex.escape(word.lowercase())}([^a-z0-9]|$)")
            .containsMatchIn(searchableText())
    }

    private fun GeocodeResult.searchableText(): String {
        return buildString {
            append(displayName)
            append(' ')
            properties.forEach { (key, value) ->
                append(key)
                append(' ')
                append(value)
                append(' ')
            }
        }.lowercase()
    }

}
