Feature: Prioritise train stations in multilingual search
  Cardinal Maps should prioritise train stations in user-entered text search
  results without filtering out other useful results. Saved and pinned places
  should remain the highest priority.

  Background:
    Given the geocoding provider returns results in relevance order
    And each result may include a display name, provider categories, and OSM metadata
    And saved or pinned places may also match the user search

  @smoke @regression @train_station_search
  Scenario: Saved and pinned places appear before train stations and other results
    Given the user searches for "lyon"
    And the user has a pinned place named "Café Lyonnais"
    And the user has a saved place named "Hôtel de Lyon"
    And the provider returns these results:
      | position | name                 | metadata        |
      | 1        | Centre-ville de Lyon | place=locality  |
      | 2        | Gare de Lyon         | railway=station |
      | 3        | Musée de Lyon        | tourism=museum  |
    When the app prepares the search results
    Then "Café Lyonnais" should appear before "Gare de Lyon"
    And "Hôtel de Lyon" should appear before "Gare de Lyon"
    And "Gare de Lyon" should appear before "Centre-ville de Lyon"
    And "Centre-ville de Lyon" should remain visible
    And "Musée de Lyon" should remain visible

  @regression @train_station_search
  Scenario: Train stations are moved before non-train provider results
    Given the user searches for "montparnasse"
    And the provider returns these results:
      | position | name                  | metadata            |
      | 1        | Quartier Montparnasse | place=neighbourhood |
      | 2        | Gare Montparnasse     | railway=station     |
      | 3        | Arrêt Montparnasse    | highway=bus_stop    |
    When the app prepares the search results
    Then "Gare Montparnasse" should appear before "Quartier Montparnasse"
    And "Quartier Montparnasse" should remain visible
    And "Arrêt Montparnasse" should remain visible

  @regression @train_station_search
  Scenario: Train station prioritisation is stable
    Given the user searches for "saint lazare"
    And the provider returns these results:
      | position | name                          | metadata        |
      | 1        | Quartier Saint-Lazare         | place=suburb    |
      | 2        | Gare Saint-Lazare             | railway=station |
      | 3        | Gare Paris Saint-Lazare       | station=train   |
      | 4        | Galerie Saint-Lazare          | shop=mall       |
    When the app prepares the search results
    Then the train station results should keep this order:
      | name                    |
      | Gare Saint-Lazare       |
      | Gare Paris Saint-Lazare |
    And the non-train provider results should keep this order:
      | name                  |
      | Quartier Saint-Lazare |
      | Galerie Saint-Lazare  |

  @regression @train_station_search
  Scenario Outline: Language-neutral metadata identifies train stations in France regardless of display language
    Given the provider returns a result named "<name>" with metadata "<metadata>"
    When the app checks whether the result is a train station
    Then the result should be treated as a train station

    Examples:
      | name                         | metadata                              |
      | Gare du Nord                 | railway=station                       |
      | Gare de l'Est                | railway=halt                          |
      | Gare de Strasbourg           | public_transport=station;station=train |
      | Gare de Bordeaux             | station=train                         |
      | Gare de Lille Europe         | category=transportation:train_station |
      | Lyon Part-Dieu Railway Station | railway=station                     |
      | Bahnhof Lyon-Part-Dieu       | station=train                         |

  @regression @train_station_search
  Scenario Outline: Display names alone do not identify train stations when metadata is missing
    Given the provider returns a result named "<name>" without train station metadata
    When the app checks whether the result is a train station
    Then the result should not be treated as a train station

    Examples:
      | name                           |
      | Gare du Nord                   |
      | Lyon Part-Dieu Railway Station |
      | Bahnhof Lyon-Part-Dieu         |
      | Station ferroviaire de Nice    |
      | Gare de Béziers                |

  @regression @train_station_search
  Scenario: Accented display names without metadata are not enough
    Given the provider returns a result named "Gare de Beziers" without train station metadata
    And the provider also returns a result named "Gare de Béziers" without train station metadata
    When the app checks whether each result is a train station
    Then neither result should be treated as a train station

  @regression @train_station_search
  Scenario Outline: Broad transport signals do not identify train stations by themselves
    Given the provider returns a result named "<name>" with metadata "<metadata>"
    When the app checks whether the result is a train station
    Then the result should not be treated as a train station

    Examples:
      | name                    | metadata                |
      | Gare Routière de Lyon   | amenity=bus_station     |
      | Station de Métro Nation | railway=subway          |
      | Station de Taxi Opéra   | amenity=taxi            |
      | Station-service Total   | amenity=fuel            |
      | Maison des Mobilités    | category=transportation |

  @regression @train_station_search
  Scenario Outline: User-entered text search surfaces apply saved, train station, then other priority
    Given the user is using "<surface>"
    And the user has a pinned place named "Café de Paris"
    And the provider returns a train station after a non-train result
    When the app prepares the search results
    Then "Café de Paris" should appear before the train station
    And the train station should appear before the non-train result
    And the non-train result should remain visible

    Examples:
      | surface            |
      | Home search        |
      | Directions search  |
      | Nearby text search |

  @regression @train_station_search
  Scenario: Category-only nearby search does not apply train station priority
    Given the user opens Nearby filters in Paris
    And the user selects the Hospital category
    And an internal synthetic fallback search returns "Gare d'Austerlitz" and "Hôpital Saint-Louis"
    When the app prepares nearby category results
    Then the hospital category matching rules should decide the result order
    And train station priority should not be applied
