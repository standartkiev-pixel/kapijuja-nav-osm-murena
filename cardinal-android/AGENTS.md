# Cardinal Maps AGENTS.md

Cardinal Maps is an Android mapping application built with a focus on usability. Material 3 and Material 3 Expressive components are used throughout. The app is based on a model-view-viewmodel architecture (MVVM) and is designed with the principle of separation of concerns in mind to ensure code quality and testability. Hilt is used for dependency injection, Room is used for data persistence. Robolectric, JUnit and mockk are the technologies we use for testing.

## Project Structure

```
cardinal-android/    - Main Android application with Kotlin/Compose
├── app/             - Android app module with UI and business logic
├── context/         - Shared context module
└── gradle/          - Build configuration

cardinal-geocoder/  - Rust-based geocoding engine with UniFFI bindings
├── src/             - Rust source code
├── dictionaries/     - Language-specific geocoding dictionaries
└── bin/             - UniFFI bindings generator
```

## Architecture

### MVVM Implementation
- **View**: Jetpack Compose UI components in `ui/` directory
- **ViewModel**: Business logic and state management, annotated with `@HiltViewModel`
- **Model**: Data layer with repositories, Room database, and external API integrations

### Key Architectural Patterns
- **Repository Pattern**: Abstracted data access through repository classes
- **Dependency Injection**: Hilt for managing dependencies and scoping
- **State Management**: StateFlow and MutableStateFlow for reactive UI updates
- **Navigation**: Jetpack Navigation with Compose for screen management

### Data Flow
1. UI components observe StateFlow from ViewModels
2. ViewModels interact with repositories for data operations
3. Repositories handle local (Room) and remote (API) data sources
4. Geocoding requests are handled by the Rust-based geocoding service

## Key Features

### Mapping
- Interactive maps using MapLibre GL Native
- Custom map rendering with vector tiles
- Offline map capabilities
- Location tracking and compass integration

### Geocoding & Search
- Offline geocoding using Rust-based engine
- Multi-language support with extensive dictionaries
- Place search with autocomplete
- Address formatting and parsing

### Routing & Navigation
- Valhalla integration for routing calculations
- Ferrostar for turn-by-turn navigation
- Multiple routing profiles (driving, walking, cycling, transit)
- Offline routing capabilities

### Place Management
- Save and organize places into lists
- Import/export functionality
- Custom place categorization
- Quick suggestions and favorites

### Transit
- Public transportation directions
- Transit stop information
- Nearby transit stations
- Transit schedule integration

## Technology Stack

### Core Technologies
- **Kotlin**: Primary programming language
- **Jetpack Compose**: Modern UI toolkit
- **Material 3 & Material 3 Expressive**: Design system
- **Hilt**: Dependency injection
- **Room**: Database persistence
- **MapLibre GL Native**: Map rendering
- **Rust**: High-performance geocoding engine

### External Services
- **Valhalla**: Open-source routing engine
- **Ferrostar**: Navigation framework
- **Pelias**: Geocoding service (configurable)

### Build Tools
- **Gradle (Kotlin DSL)**: Build system
- **Cargo NDK**: Rust compilation for Android
- **UniFFI**: Rust-Kotlin bindings
- **KSP**: Kotlin Symbol Processing

## Development Setup

### Prerequisites
- Android Studio (latest version)
- JDK 8 or later
- Rust (for building geocoding component)
- Android SDK and NDK

### Building the Project
1. Clone the repository
2. Run `./gradlew build` to build the Android application
3. The Rust geocoding component will be automatically compiled

### Running the Application
- Debug build: `./gradlew installDebug`
- Release build: `./gradlew assembleRelease`

### Debugging Tips
- Use Android Studio's debugger for Kotlin code
- Logcat for runtime logs
- Rust debugger for geocoding component issues
- Chrome DevTools for web-based components

## Development Tips

* Maps applications are inherently complex, and despite our best efforts, some of the logic is very tricky. When writing tests, write only one test at a time, then run it with `./gradlew test` to ensure it passes before moving on.
* The Android ecosystem moves fast, and some of the patterns, components and libraries we use may be from after your knowledge cutoff date. Fortunately, this is not a green-field project. Use the surrounding context to see how things work.
* Run `./gradlew check` regularly.
* We have a comprehensive linting and CI process, so don't concern yourself with unused imports or other trivial tasks.

## Testing

### Test Structure
- **Unit Tests**: Located in `src/test/` for business logic
- **Integration Tests**: Located in `src/androidTest/` for UI components
- **Robolectric**: For testing Android components without device
- **Mockk**: For mocking dependencies in tests

### Running Tests
- Run all tests: `./gradlew test`
- Run specific test: `./gradlew test`
- Run integration tests: `./gradlew connectedAndroidTest`

### Testing Guidelines
- Write focused, single-purpose tests
- Mock external dependencies (geocoding services, APIs)
- Test both success and failure scenarios
- Use test fixtures for consistent test data

## Key UI Components

### Navigation
- **Bottom Navigation**: Main app navigation with search, favorites, nearby, transit, and offline areas
- **Bottom Sheets**: Expandable panels for place details, search results, and directions
- **Place Cards**: Detailed information display for locations
- **Search Interface**: Expandable search with autocomplete suggestions

### Main Screens
- **Home Search**: Main search interface with map
- **Place Card**: Detailed place information
- **Directions**: Route planning and display
- **Nearby Points of Interest**: Discover nearby places
- **Transit**: Public transportation information
- **Offline Areas**: Manage downloaded map regions
- **Settings**: App configuration and preferences

## Data Models

### Core Entities
- **Place**: Represents a location with coordinates, name, and address.
- **SavedPlace**: User-saved places with custom metadata.
- **Route**: Navigation route with turn-by-turn instructions.
- **Area**: Offline map region with bounding box.
- **GeocodeResult**: Geocoding search results. Prefer using `Place` when possible.

### Database Schema
- Room database with DAOs for data access
- Relationships between places, lists, and user data
- Migration helpers for schema updates

## External Configuration

### API Endpoints
- **Pelias Geocoding**: Configurable base URL and API key
- **Valhalla Routing**: Configurable base URL and API key
- **Fallback to offline geocoding** when APIs are unavailable

### Build Configuration
- Architecture-specific builds (arm64, x86_64)
- Debug and release variants
- Custom build types for different scenarios
