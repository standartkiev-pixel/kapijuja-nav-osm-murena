## Cardinal Maps Architecture

This project is composed of a frontend and a backend. The frontend is located in this repository. The backend is a bit more nebulous, and it can be broken up into a few main pieces.

1. Tiles. Serves the data required for the client to render the map and perform offline area downloads This piece can be implemented without any code whatsoever using modern serverless tools. Data processing steps requried to run the backend are described in [BASEMAP_GENERATION.md](./BASEMAP_GENERATION.md) and [ROUTING_TILE_GEN.md](./ROUTING_TILE_GEN.md).
2. Searching. The search and autocomplete endpoints need to be compatible with the Pelias API. geocode.earth and stadia maps (v1 only) provide such API endpoints.
3. Routing. The routing endpoint must be valhalla or valhalla-compatible. Many providers offer such APIs like Mapbox, Stadia Maps, and others.
4. Trip planning (public transportation). Cardinal Maps requires a MOTIS-compatible API, like the one provided by Transitous. Cardinal maps has explicit permission to call the Transitous API. If you fork the app, you must get permission to call this API, self-host MOTIS yourself, or remove the public transit feature. Additionally, please change [the user agent](../cardinal-android/app/src/main/java/earth/maps/cardinal/transit/TransitousService.kt) providing your own contact information.

### Frontend Architecture

The frontend is composed of two main pieces of code. There's [an offline geocoder](../cardinal-geocoder/) responsible for allowing the user to search for POIs offline, and [the main app](../cardinal-android/) which is a Kotlin codebase using Jetpack/Android Compose, Hilt, and Room. The app is built using the Material 3 Expressive design system.

