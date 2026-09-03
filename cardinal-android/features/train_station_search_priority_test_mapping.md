# Train Station Search Priority - Scenario To Unit Test Mapping

This document maps the reviewed Gherkin scenarios in
`train_station_search_priority.feature` to the generated unit-test contracts.
It is an ATDD traceability artifact only; it does not define implementation
details beyond the behavior required by the scenarios.

## Generated Test Targets

| Generated test class | Responsibility |
| --- | --- |
| `TrainStationResultClassifierTest` | Classifies `GeocodeResult` instances as train stations or non-train results from language-neutral metadata only. |
| `TrainStationSearchResultPrioritizerTest` | Reorders provider `GeocodeResult` lists so train stations come before other provider results while preserving stable order. |
| `PinnedPlacesSearchResultPrioritizerTest` | Extends the existing saved/pinned prioritizer tests to confirm saved and pinned places remain above train-station provider results after both prioritizers are composed. |
| `SearchResultPriorityPipelineTest` | Verifies user-entered text search surfaces use the priority order saved/pinned, train stations, then other provider results. |
| `NearbyCategorySearchCoordinatorTest` | Verifies category-only Nearby searches and internal synthetic fallback searches do not apply train-station priority. |

## Scenario Mapping

| Gherkin scenario | Generated unit test coverage |
| --- | --- |
| Saved and pinned places appear before train stations and other results | `PinnedPlacesSearchResultPrioritizerTest.matching saved and pinned places remain before train station provider results`; `SearchResultPriorityPipelineTest.saved and pinned places appear before train stations and other results`. |
| Train stations are moved before non-train provider results | `TrainStationSearchResultPrioritizerTest.train stations are moved before non-train provider results`. |
| Train station prioritisation is stable | `TrainStationSearchResultPrioritizerTest.train station prioritisation is stable`. |
| Language-neutral metadata identifies train stations in France regardless of display language | `TrainStationResultClassifierTest.language-neutral metadata identifies train stations in France regardless of display language`. |
| Display names alone do not identify train stations when metadata is missing | `TrainStationResultClassifierTest.display names alone do not identify train stations when metadata is missing`. |
| Accented display names without metadata are not enough | `TrainStationResultClassifierTest.display names alone do not identify train stations when metadata is missing`. |
| Broad transport signals do not identify train stations by themselves | `TrainStationResultClassifierTest.broad transport signals do not identify train stations by themselves`; `TrainStationResultClassifierTest.explicit non-train metadata vetoes ambiguous station names`. |
| User-entered text search surfaces apply saved, train station, then other priority | `SearchResultPriorityPipelineTest.user-entered text search surfaces apply saved train station then other priority`. |
| Category-only nearby search does not apply train station priority | `NearbyCategorySearchCoordinatorTest.category-only nearby search does not apply train station priority`. |

## Review Rules For This ATDD Contract

- Every generated test name must reference the Gherkin behavior it covers.
- Use `GeocodeResult` fixtures for provider results and `Place` fixtures for saved or pinned places.
- Treat metadata examples as normalized app-level fixture properties, not a guarantee that Stadia Maps returns those exact wire fields.
- Do not add UI tests for this feature unless unit tests cannot cover a search-surface contract.
- Do not test real Stadia network calls; provider results should be deterministic fixtures.
- The business-logic tests must pass in this MR.
- Presentation-layer wiring into Home, Directions, and Nearby typed text search is deferred to a later MR.
