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

internal const val NEARBY_MAX_SIZE = 40
internal const val NEARBY_SUPPLEMENTAL_SIZE = 10
internal const val NEARBY_ATM_SUPPLEMENTAL_SIZE = NEARBY_MAX_SIZE
internal const val NEARBY_SYNTHETIC_FALLBACK_MIN_RESULTS = 3
internal const val NEARBY_SUPPLEMENTAL_RADIUS_KM = "5"
internal const val FILTER_ACCOMMODATION = "filter:accommodation"
internal const val FILTER_ATM = "filter:atm"
internal const val FILTER_BAR = "filter:bar"
internal const val FILTER_BAKERY = "filter:bakery"
internal const val FILTER_BANK = "filter:bank"
internal const val FILTER_BOOKS = "filter:books"
internal const val FILTER_BUS_STATION = "filter:bus_station"
internal const val FILTER_CAR_REPAIR = "filter:car_repair"
internal const val FILTER_CAR_WASH = "filter:car_wash"
internal const val FILTER_CINEMA = "filter:cinema"
internal const val FILTER_CLINIC = "filter:clinic"
internal const val FILTER_CLOTHES = "filter:clothes"
internal const val FILTER_COLLEGE = "filter:college"
internal const val FILTER_CONVENIENCE = "filter:convenience"
internal const val FILTER_COCKTAIL = "filter:cocktail"
internal const val FILTER_COURTHOUSE = "filter:courthouse"
internal const val FILTER_DENTIST = "filter:dentist"
internal const val FILTER_DOCTOR = "filter:doctor"
internal const val FILTER_ELECTRONICS = "filter:electronics"
internal const val FILTER_ENTERTAINMENT = "filter:entertainment"
internal const val FILTER_FINANCE = "filter:finance"
internal const val FILTER_FUEL = "filter:fuel"
internal const val FILTER_GOLF = "filter:golf"
internal const val FILTER_GOVERNMENT = "filter:government"
internal const val FILTER_GUEST_HOUSE = "filter:guest_house"
internal const val FILTER_GYM = "filter:gym"
internal const val FILTER_HAIRDRESSER = "filter:hairdresser"
internal const val FILTER_HOSPITAL = "filter:hospital"
internal const val FILTER_HOSTEL = "filter:hostel"
internal const val FILTER_HOTEL = "filter:hotel"
internal const val FILTER_ICE_CREAM = "filter:ice_cream"
internal const val FILTER_LAUNDRY = "filter:laundry"
internal const val FILTER_LIBRARY = "filter:library"
internal const val FILTER_LOUNGE = "filter:lounge"
internal const val FILTER_MALL = "filter:mall"
internal const val FILTER_MEDICAL_CENTRE = "filter:medical_centre"
internal const val FILTER_MUSEUM = "filter:museum"
internal const val FILTER_PARKING = "filter:parking"
internal const val FILTER_PHARMACY = "filter:pharmacy"
internal const val FILTER_POLICE = "filter:police"
internal const val FILTER_POST_OFFICE = "filter:post_office"
internal const val FILTER_PUB = "filter:pub"
internal const val FILTER_RESTOBAR = "filter:restobar"
internal const val FILTER_SCHOOL = "filter:school"
internal const val FILTER_SERVICES = "filter:services"
internal const val FILTER_SHOPPING = "filter:shopping"
internal const val FILTER_SPORTS = "filter:sports"
internal const val FILTER_STADIUM = "filter:stadium"
internal const val FILTER_SUBWAY = "filter:subway"
internal const val FILTER_SUPERMARKET = "filter:supermarket"
internal const val FILTER_SWIMMING_POOL = "filter:swimming_pool"
internal const val FILTER_TAXI = "filter:taxi"
internal const val FILTER_THEATRE = "filter:theatre"
internal const val FILTER_TRAIN_STATION = "filter:train_station"
internal const val FILTER_TRANSPORTATION = "filter:transportation"
internal const val FILTER_UNIVERSITY = "filter:university"
internal const val FILTER_VETERINARY = "filter:veterinary"
internal const val FILTER_ZOO = "filter:zoo"
internal val SYNTHETIC_CATEGORY_FILTERS = setOf(
    FILTER_ACCOMMODATION,
    FILTER_ATM,
    FILTER_BAR,
    FILTER_BAKERY,
    FILTER_BANK,
    FILTER_BOOKS,
    FILTER_BUS_STATION,
    FILTER_CAR_REPAIR,
    FILTER_CAR_WASH,
    FILTER_CINEMA,
    FILTER_CLINIC,
    FILTER_CLOTHES,
    FILTER_COLLEGE,
    FILTER_CONVENIENCE,
    FILTER_COCKTAIL,
    FILTER_COURTHOUSE,
    FILTER_DENTIST,
    FILTER_DOCTOR,
    FILTER_ELECTRONICS,
    FILTER_ENTERTAINMENT,
    FILTER_FINANCE,
    FILTER_FUEL,
    FILTER_GOLF,
    FILTER_GOVERNMENT,
    FILTER_GUEST_HOUSE,
    FILTER_GYM,
    FILTER_HAIRDRESSER,
    FILTER_HOSPITAL,
    FILTER_HOSTEL,
    FILTER_HOTEL,
    FILTER_ICE_CREAM,
    FILTER_LAUNDRY,
    FILTER_LIBRARY,
    FILTER_LOUNGE,
    FILTER_MALL,
    FILTER_MEDICAL_CENTRE,
    FILTER_MUSEUM,
    FILTER_PARKING,
    FILTER_PHARMACY,
    FILTER_POLICE,
    FILTER_POST_OFFICE,
    FILTER_PUB,
    FILTER_RESTOBAR,
    FILTER_SCHOOL,
    FILTER_SERVICES,
    FILTER_SHOPPING,
    FILTER_SPORTS,
    FILTER_STADIUM,
    FILTER_SUBWAY,
    FILTER_SUPERMARKET,
    FILTER_SWIMMING_POOL,
    FILTER_TAXI,
    FILTER_THEATRE,
    FILTER_TRAIN_STATION,
    FILTER_TRANSPORTATION,
    FILTER_UNIVERSITY,
    FILTER_VETERINARY,
    FILTER_ZOO
)

internal data class NearbyCategoryRequestSplit(
    val nativeCategories: List<String>,
    val syntheticCategories: List<String>
)

internal fun splitNearbyCategoriesForProviderRequests(
    selectedCategories: List<String>
): NearbyCategoryRequestSplit {
    return NearbyCategoryRequestSplit(
        nativeCategories = selectedCategories.filterNot { category ->
            category in SYNTHETIC_CATEGORY_FILTERS
        },
        syntheticCategories = selectedCategories.filter { category ->
            category in SYNTHETIC_CATEGORY_FILTERS
        }
    )
}

// TODO: Move supplemental fallback search terms to a locale-agnostic, data-driven taxonomy.
internal val ACCOMMODATION_SEARCH_TERMS = listOf(
    "accommodation",
    "hotel",
    "hostel",
    "guest house",
    "guesthouse",
    "lodge",
    "lodging",
    "motel",
    "resort",
    "homestay"
)

internal val ATM_SEARCH_TERMS = listOf(
    "atm",
    "atm center",
    "atm centre",
    "bank atm",
    "cash deposit machine",
    "cash dispenser",
    "cash machine",
    "cashpoint",
    "cdm",
    "automated teller"
)
internal val BAKERY_SEARCH_TERMS = listOf("bakery", "cake", "pastry")
internal val BAR_SEARCH_TERMS = listOf("bar")
internal val BANK_SEARCH_TERMS = listOf("bank", "banks", "bank branch", "credit union")
internal val BOOKS_SEARCH_TERMS = listOf("bookstore", "book store", "bookshop", "books", "stationery")
internal val BUS_STATION_SEARCH_TERMS = listOf("bus station", "bus stop", "bus stand", "bus depot", "bus terminal")
internal val CAR_REPAIR_SEARCH_TERMS = listOf(
    "car repair",
    "auto repair",
    "automobile repair",
    "vehicle repair",
    "mechanic",
    "garage"
)
internal val CAR_WASH_SEARCH_TERMS = listOf("car wash", "carwash", "auto wash", "vehicle wash")
internal val CINEMA_SEARCH_TERMS = listOf(
    "cinema",
    "cinemas",
    "movie theater",
    "movie theatre",
    "multiplex",
    "movie hall",
    "picture hall",
    "talkies",
    "inox",
    "pvr",
    "cinepolis",
    "cinemax",
    "moviemax",
    "miraj",
    "mukta",
    "carnival",
    "devgan"
)
internal val CLINIC_SEARCH_TERMS = listOf("clinic", "clinics", "medical clinic", "polyclinic")
internal val CLOTHES_SEARCH_TERMS = listOf(
    "clothes",
    "clothing",
    "apparel",
    "fashion",
    "garments",
    "boutique",
    "saree",
    "readymade"
)
internal val COCKTAIL_SEARCH_TERMS = listOf("cocktail")
internal val COLLEGE_SEARCH_TERMS = listOf(
    "college",
    "colleges",
    "degree college",
    "junior college",
    "community college",
    "institute of technology"
)
internal val CONVENIENCE_SEARCH_TERMS = listOf(
    "convenience store",
    "general store",
    "provision store",
    "departmental store",
    "kirana",
    "minimarket"
)
internal val COURTHOUSE_SEARCH_TERMS = listOf(
    "courthouse",
    "court house",
    "district court",
    "high court",
    "sessions court"
)
internal val DENTIST_SEARCH_TERMS = listOf("dentist", "dentists", "dental", "dental clinic", "orthodontist")
internal val DOCTOR_EXCLUDED_TERMS = listOf("auditorium", "cinema", "natyagruha", "theater", "theatre")
internal val DOCTOR_SEARCH_TERMS = listOf("doctor", "doctors", "doctor clinic", "physician", "general practitioner")
internal val ELECTRONICS_SEARCH_TERMS = listOf(
    "electronics",
    "mobile store",
    "phone store",
    "computer store",
    "appliances"
)
internal val ENTERTAINMENT_SEARCH_TERMS = listOf(
    "cinema",
    "movie theater",
    "movie theatre",
    "multiplex",
    "theatre",
    "theater",
    "museum",
    "zoo"
)
internal val FINANCE_SEARCH_TERMS = listOf("bank", "banks", "bank branch", "atm", "bank atm", "cash machine", "cashpoint")
internal val FUEL_SEARCH_TERMS = listOf("fuel", "gas station", "petrol pump", "petrol station", "diesel", "cng")
internal val GOLF_SEARCH_TERMS = listOf("golf", "golf course", "golf club", "driving range")
internal val GOVERNMENT_SEARCH_TERMS = listOf(
    "government office",
    "municipal office",
    "municipality",
    "police station",
    "post office",
    "courthouse"
)
internal val GUEST_HOUSE_SEARCH_TERMS = listOf(
    "guest house",
    "guesthouse",
    "guest houses",
    "homestay",
    "bed and breakfast",
    "bnb"
)
internal val GYM_SEARCH_TERMS = listOf(
    "gym",
    "gyms",
    "fitness",
    "fitness center",
    "fitness centre",
    "fitness studio",
    "gymnasium",
    "health club"
)
internal val HAIRDRESSER_SEARCH_TERMS = listOf(
    "hairdresser",
    "hair salon",
    "barber",
    "beauty salon",
    "salon"
)
internal val HOSPITAL_SEARCH_TERMS = listOf("hospital")
internal val HOSTEL_SEARCH_TERMS = listOf("hostel", "hostels", "youth hostel", "dormitory")
internal val HOTEL_SEARCH_TERMS = listOf("hotel", "hotels", "motel", "resort", "inn", "lodge", "lodging")
internal val ICE_CREAM_SEARCH_TERMS = listOf("ice cream", "icecream", "gelato", "kulfi")
internal val LAUNDRY_SEARCH_TERMS = listOf("laundry", "laundromat", "dry cleaner", "dry cleaning", "wash and fold")
internal val LIBRARY_SEARCH_TERMS = listOf("library", "libraries", "public library", "reading room")
internal val LOUNGE_SEARCH_TERMS = listOf("lounge")
internal val MALL_SEARCH_TERMS = listOf("mall", "shopping mall", "shopping centre", "shopping center", "plaza", "galleria")
internal val MEDICAL_CENTRE_SEARCH_TERMS = listOf("medical cent")
internal val MUSEUM_SEARCH_TERMS = listOf("museum", "museums", "gallery", "art gallery")
internal val PARKING_SEARCH_TERMS = listOf("parking", "parking lot", "car park", "parking garage")
internal val PHARMACY_SEARCH_TERMS = listOf("chemist", "chemists", "pharmacy", "medical store", "drugstore")
internal val POLICE_SEARCH_TERMS = listOf("police", "police station", "police chowki", "police thana", "thana")
internal val POST_OFFICE_SEARCH_TERMS = listOf("post office", "post_office", "postal office", "india post")
internal val PUB_SEARCH_TERMS = listOf("pub", "pubs", "brewpub", "brewery", "tavern", "alehouse")
internal val RESTOBAR_SEARCH_TERMS = listOf("restobar")
internal val SCHOOL_SEARCH_TERMS = listOf(
    "school",
    "schools",
    "primary school",
    "secondary school",
    "high school",
    "public school",
    "international school",
    "kindergarten",
    "preschool"
)
internal val SERVICES_SEARCH_TERMS = listOf(
    "laundry",
    "dry cleaner",
    "hairdresser",
    "hair salon",
    "barber",
    "car repair",
    "mechanic",
    "car wash"
)
internal val SHOPPING_SEARCH_TERMS = listOf(
    "shopping",
    "shop",
    "store",
    "market",
    "mart",
    "retail",
    "bazaar"
)
internal val SPORTS_SEARCH_TERMS = listOf(
    "sports",
    "sports club",
    "sports complex",
    "sports ground",
    "gym",
    "fitness",
    "stadium",
    "swimming pool",
    "aquatic center",
    "aquatic centre",
    "golf",
    "golf course",
    "golf club"
)
internal val STADIUM_SEARCH_TERMS = listOf("stadium", "stadiums", "arena", "sports ground", "sports complex")
internal val SUPERMARKET_SEARCH_TERMS = listOf(
    "supermarket",
    "supermarkets",
    "grocery",
    "grocery store",
    "hypermarket",
    "mart",
    "market",
    "kirana"
)
internal val SWIMMING_POOL_SEARCH_TERMS = listOf(
    "swimming pool",
    "swimming pools",
    "aquatic center",
    "aquatic centre",
    "natatorium"
)
internal val SUBWAY_SEARCH_TERMS = listOf("subway station", "metro station", "underground station")
internal val TAXI_SEARCH_TERMS = listOf("taxi", "taxi stand", "taxi rank", "cab stand", "cab")
internal val THEATRE_SEARCH_TERMS = listOf("theatre", "theater", "auditorium", "performing arts", "playhouse")
internal val TRAIN_STATION_SEARCH_TERMS = listOf("train station", "railway station", "rail station", "railway")
internal val TRANSPORTATION_SEARCH_TERMS = listOf(
    "transport",
    "transit",
    "station",
    "bus station",
    "bus stop",
    "train station",
    "railway station",
    "metro station",
    "subway station",
    "taxi stand",
    "parking",
    "fuel",
    "petrol pump"
)
internal val UNIVERSITY_SEARCH_TERMS = listOf("university", "universities", "university campus")
internal val VETERINARY_SEARCH_TERMS = listOf("veterinary", "veterinarian", "vet", "pet clinic", "animal clinic", "animal hospital")
internal val ZOO_SEARCH_TERMS = listOf("zoo", "zoos", "zoological garden", "zoological park")


internal fun List<String>.toProviderNearbyCategories(): List<String> {
    return flatMap { category ->
        when (category) {
            FILTER_ATM,
            FILTER_BANK,
            FILTER_FINANCE -> listOf("finance")

            FILTER_BAKERY, FILTER_ICE_CREAM -> listOf("food")
            FILTER_BAR,
            FILTER_COCKTAIL,
            FILTER_LOUNGE,
            FILTER_PUB,
            FILTER_RESTOBAR -> listOf("nightlife")
            FILTER_BOOKS,
            FILTER_CLOTHES,
            FILTER_CONVENIENCE,
            FILTER_ELECTRONICS,
            FILTER_MALL,
            FILTER_SHOPPING,
            FILTER_SUPERMARKET -> listOf("shopping")

            FILTER_CLINIC,
            FILTER_DENTIST,
            FILTER_DOCTOR,
            FILTER_HOSPITAL,
            FILTER_MEDICAL_CENTRE,
            FILTER_PHARMACY,
            FILTER_VETERINARY -> listOf("health")

            FILTER_BUS_STATION,
            FILTER_FUEL,
            FILTER_PARKING,
            FILTER_SUBWAY,
            FILTER_TAXI,
            FILTER_TRAIN_STATION,
            FILTER_TRANSPORTATION -> listOf("transportation")

            FILTER_COLLEGE,
            FILTER_LIBRARY,
            FILTER_SCHOOL,
            FILTER_UNIVERSITY -> listOf("education")

            FILTER_ACCOMMODATION,
            FILTER_GUEST_HOUSE,
            FILTER_HOSTEL,
            FILTER_HOTEL -> listOf("accommodation")

            FILTER_CINEMA,
            FILTER_ENTERTAINMENT,
            FILTER_MUSEUM,
            FILTER_THEATRE,
            FILTER_ZOO -> listOf("entertainment")

            FILTER_GOLF,
            FILTER_GYM,
            FILTER_SPORTS,
            FILTER_STADIUM,
            FILTER_SWIMMING_POOL -> listOf("sports")

            FILTER_COURTHOUSE,
            FILTER_GOVERNMENT,
            FILTER_POLICE,
            FILTER_POST_OFFICE -> listOf("government")

            FILTER_CAR_REPAIR,
            FILTER_CAR_WASH,
            FILTER_HAIRDRESSER,
            FILTER_LAUNDRY,
            FILTER_SERVICES -> listOf("services")

            else -> listOf(category)
        }
    }.distinct()
}


internal data class SyntheticFallbackSearch(
    val syntheticFilter: String,
    val searchTerms: List<String>,
    val resultSize: Int = NEARBY_SUPPLEMENTAL_SIZE
)

internal fun String.isSyntheticCategoryFilter(): Boolean {
    return this in SYNTHETIC_CATEGORY_FILTERS
}

private val SYNTHETIC_FALLBACK_SEARCHES = mapOf(
    FILTER_ACCOMMODATION to SyntheticFallbackSearch(FILTER_ACCOMMODATION, ACCOMMODATION_SEARCH_TERMS),
    FILTER_ATM to SyntheticFallbackSearch(FILTER_ATM, ATM_SEARCH_TERMS, NEARBY_ATM_SUPPLEMENTAL_SIZE),
    FILTER_BAR to SyntheticFallbackSearch(FILTER_BAR, BAR_SEARCH_TERMS),
    FILTER_BAKERY to SyntheticFallbackSearch(FILTER_BAKERY, BAKERY_SEARCH_TERMS),
    FILTER_BANK to SyntheticFallbackSearch(FILTER_BANK, BANK_SEARCH_TERMS),
    FILTER_BOOKS to SyntheticFallbackSearch(FILTER_BOOKS, BOOKS_SEARCH_TERMS),
    FILTER_BUS_STATION to SyntheticFallbackSearch(FILTER_BUS_STATION, BUS_STATION_SEARCH_TERMS),
    FILTER_CAR_REPAIR to SyntheticFallbackSearch(FILTER_CAR_REPAIR, CAR_REPAIR_SEARCH_TERMS),
    FILTER_CAR_WASH to SyntheticFallbackSearch(FILTER_CAR_WASH, CAR_WASH_SEARCH_TERMS),
    FILTER_CINEMA to SyntheticFallbackSearch(FILTER_CINEMA, CINEMA_SEARCH_TERMS),
    FILTER_CLINIC to SyntheticFallbackSearch(FILTER_CLINIC, CLINIC_SEARCH_TERMS),
    FILTER_COLLEGE to SyntheticFallbackSearch(FILTER_COLLEGE, COLLEGE_SEARCH_TERMS),
    FILTER_CLOTHES to SyntheticFallbackSearch(FILTER_CLOTHES, CLOTHES_SEARCH_TERMS),
    FILTER_COCKTAIL to SyntheticFallbackSearch(FILTER_COCKTAIL, COCKTAIL_SEARCH_TERMS),
    FILTER_CONVENIENCE to SyntheticFallbackSearch(FILTER_CONVENIENCE, CONVENIENCE_SEARCH_TERMS),
    FILTER_COURTHOUSE to SyntheticFallbackSearch(FILTER_COURTHOUSE, COURTHOUSE_SEARCH_TERMS),
    FILTER_DENTIST to SyntheticFallbackSearch(FILTER_DENTIST, DENTIST_SEARCH_TERMS),
    FILTER_DOCTOR to SyntheticFallbackSearch(FILTER_DOCTOR, DOCTOR_SEARCH_TERMS),
    FILTER_HOSPITAL to SyntheticFallbackSearch(FILTER_HOSPITAL, HOSPITAL_SEARCH_TERMS),
    FILTER_MEDICAL_CENTRE to SyntheticFallbackSearch(FILTER_MEDICAL_CENTRE, MEDICAL_CENTRE_SEARCH_TERMS),
    FILTER_ELECTRONICS to SyntheticFallbackSearch(FILTER_ELECTRONICS, ELECTRONICS_SEARCH_TERMS),
    FILTER_ENTERTAINMENT to SyntheticFallbackSearch(FILTER_ENTERTAINMENT, ENTERTAINMENT_SEARCH_TERMS),
    FILTER_FINANCE to SyntheticFallbackSearch(FILTER_FINANCE, FINANCE_SEARCH_TERMS, NEARBY_ATM_SUPPLEMENTAL_SIZE),
    FILTER_FUEL to SyntheticFallbackSearch(FILTER_FUEL, FUEL_SEARCH_TERMS),
    FILTER_GOLF to SyntheticFallbackSearch(FILTER_GOLF, GOLF_SEARCH_TERMS),
    FILTER_GOVERNMENT to SyntheticFallbackSearch(FILTER_GOVERNMENT, GOVERNMENT_SEARCH_TERMS),
    FILTER_GUEST_HOUSE to SyntheticFallbackSearch(FILTER_GUEST_HOUSE, GUEST_HOUSE_SEARCH_TERMS),
    FILTER_GYM to SyntheticFallbackSearch(FILTER_GYM, GYM_SEARCH_TERMS),
    FILTER_HAIRDRESSER to SyntheticFallbackSearch(FILTER_HAIRDRESSER, HAIRDRESSER_SEARCH_TERMS),
    FILTER_ICE_CREAM to SyntheticFallbackSearch(FILTER_ICE_CREAM, ICE_CREAM_SEARCH_TERMS),
    FILTER_HOSTEL to SyntheticFallbackSearch(FILTER_HOSTEL, HOSTEL_SEARCH_TERMS),
    FILTER_HOTEL to SyntheticFallbackSearch(FILTER_HOTEL, HOTEL_SEARCH_TERMS),
    FILTER_LAUNDRY to SyntheticFallbackSearch(FILTER_LAUNDRY, LAUNDRY_SEARCH_TERMS),
    FILTER_LIBRARY to SyntheticFallbackSearch(FILTER_LIBRARY, LIBRARY_SEARCH_TERMS),
    FILTER_LOUNGE to SyntheticFallbackSearch(FILTER_LOUNGE, LOUNGE_SEARCH_TERMS),
    FILTER_MALL to SyntheticFallbackSearch(FILTER_MALL, MALL_SEARCH_TERMS),
    FILTER_MUSEUM to SyntheticFallbackSearch(FILTER_MUSEUM, MUSEUM_SEARCH_TERMS),
    FILTER_PARKING to SyntheticFallbackSearch(FILTER_PARKING, PARKING_SEARCH_TERMS),
    FILTER_PHARMACY to SyntheticFallbackSearch(FILTER_PHARMACY, PHARMACY_SEARCH_TERMS),
    FILTER_POLICE to SyntheticFallbackSearch(FILTER_POLICE, POLICE_SEARCH_TERMS),
    FILTER_POST_OFFICE to SyntheticFallbackSearch(FILTER_POST_OFFICE, POST_OFFICE_SEARCH_TERMS),
    FILTER_PUB to SyntheticFallbackSearch(FILTER_PUB, PUB_SEARCH_TERMS),
    FILTER_RESTOBAR to SyntheticFallbackSearch(FILTER_RESTOBAR, RESTOBAR_SEARCH_TERMS),
    FILTER_SCHOOL to SyntheticFallbackSearch(FILTER_SCHOOL, SCHOOL_SEARCH_TERMS),
    FILTER_SERVICES to SyntheticFallbackSearch(FILTER_SERVICES, SERVICES_SEARCH_TERMS),
    FILTER_SHOPPING to SyntheticFallbackSearch(FILTER_SHOPPING, SHOPPING_SEARCH_TERMS),
    FILTER_SPORTS to SyntheticFallbackSearch(FILTER_SPORTS, SPORTS_SEARCH_TERMS),
    FILTER_STADIUM to SyntheticFallbackSearch(FILTER_STADIUM, STADIUM_SEARCH_TERMS),
    FILTER_SUBWAY to SyntheticFallbackSearch(FILTER_SUBWAY, SUBWAY_SEARCH_TERMS),
    FILTER_SWIMMING_POOL to SyntheticFallbackSearch(FILTER_SWIMMING_POOL, SWIMMING_POOL_SEARCH_TERMS),
    FILTER_SUPERMARKET to SyntheticFallbackSearch(FILTER_SUPERMARKET, SUPERMARKET_SEARCH_TERMS),
    FILTER_TAXI to SyntheticFallbackSearch(FILTER_TAXI, TAXI_SEARCH_TERMS),
    FILTER_THEATRE to SyntheticFallbackSearch(FILTER_THEATRE, THEATRE_SEARCH_TERMS),
    FILTER_TRAIN_STATION to SyntheticFallbackSearch(FILTER_TRAIN_STATION, TRAIN_STATION_SEARCH_TERMS),
    FILTER_TRANSPORTATION to SyntheticFallbackSearch(FILTER_TRANSPORTATION, TRANSPORTATION_SEARCH_TERMS),
    FILTER_UNIVERSITY to SyntheticFallbackSearch(FILTER_UNIVERSITY, UNIVERSITY_SEARCH_TERMS),
    FILTER_VETERINARY to SyntheticFallbackSearch(FILTER_VETERINARY, VETERINARY_SEARCH_TERMS),
    FILTER_ZOO to SyntheticFallbackSearch(FILTER_ZOO, ZOO_SEARCH_TERMS)
)

internal fun syntheticFallbackSearchFor(selectedCategory: String): SyntheticFallbackSearch? {
    return SYNTHETIC_FALLBACK_SEARCHES[selectedCategory]
}
