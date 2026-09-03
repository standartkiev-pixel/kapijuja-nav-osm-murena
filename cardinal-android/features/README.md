# Cardinal Maps - Acceptance Tests

Gherkin acceptance tests describing expected Cardinal Maps behaviour from a user
and integrator point of view. These scenarios are the source of truth for ATDD:
Gherkin first, then unit tests generated from the scenarios, then implementation.

## Organisation

| File | Scope |
| --- | --- |
| `train_station_search_priority.feature` | Train station prioritisation in multilingual user-entered text search |
| `train_station_search_priority_test_mapping.md` | Traceability from train station Gherkin scenarios to planned unit tests |

## Conventions

- `@smoke` marks the minimal set to run on every build.
- `@regression` marks the full feature set.
- `@train_station_search` marks train station prioritisation scenarios.
- "User-entered text search" means Home search, Directions search, and Nearby typed text search.
- Saved and pinned places are the highest-priority results when they match the search.
- Train station provider results are prioritised after saved and pinned places.
- Other provider results remain visible after train stations.
- Category-only Nearby searches and internal synthetic fallback searches are outside this feature.
- Acceptance scenarios must be reviewed before generating unit tests.
- Generated unit tests must map back to one or more scenario names.
