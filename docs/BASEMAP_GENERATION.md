# Basemap

## Goal and Motivation

This document covers the backend requirements of the Cardinal Maps basemap tile server. This system serves map data to Cardinal for display on the screen and other critical functions.

Deployment strategies, suggested production environments and data artifact generation steps are given.

### Overview of Generation

To generate basemap tiles for Cardinal Maps, we use a fork of _planetiler-openmaptiles_ that includes extra information about each point of interest and address on the map. This extra information is used by Cardinal to enable users to tap on icons on the map and get information about a place without relying on a round-trip to the server. We learned from Atlas that users want quick performance for these UI transitions even while offline or in degraded cellular conditions.

This extra POI information is also used by the app's offline mode to let the user search for points of interest without access to the internet.

## System Components

The recommended serving strategy covered in this document involves putting a large map tile archive in object storage and deploying horizontally-scalable tileservers either co-located with the object storage system or at the edge close to users. Tileservers don't have copies of the entire planet archive, instead they make HTTP range requests to object storage to retrieve bits and pieces of the world as necessary.

This approach is covered in detail by [the PMTiles documentation](https://docs.protomaps.com/).

Map tiles are served by a Go server embedded into the PMTiles project. This serving strategy is described by [the official documentation](https://docs.protomaps.com/deploy/) as `pmtiles serve`.

In order to minimize or eliminate downtime during data updates, Blue-Green deploys or similar techniques are recommended. Deployment is covered in more detail later in this document.

## Hardware Requirements

Planetiler is extremely memory-efficient compared to other tile generation software, but due to the scale of the data we're working with, it still requires quite a bit of memory to generate tiles for the whole planet. Official documentation lists the following requirements:

> 10x as much disk space and at least 0.5x as much RAM as the planet.osm.pbf file you start from

Empirically, the fork seems to require about 1.5x-2x as much storage and around 3x as much physical memory as mainline for the same input data.

As of December 2025, `planet-latest.osm.pbf` is about 84 GB, so to be on the safe side I'd recommend a machine with about 2 TB of fast SSD storage and about 192 GB of RAM. OOM errors have occurred with as much as 128 GB of RAM. An additional thing to keep in mind is that the OpenStreetMap database is constantly growing, so these hardware requirements will increase slowly over time.

## Generation Steps

### Step 1: Obtain OpenStreetMap Data

In a web browser, navigate to https://planet.openstreetmap.org/ and download the latest **planet.osm.pbf**, either via an HTTP download or BitTorrent. In either case, verify the hash to ensure the download completed successfully.

It is recommended to note the date/version of the OpenStreetMap extract used, and to incorporate it into the file paths of downstream artifacts to avoid accidentally overwriting previous artifacts or losing track of which database version generated which tile data. Because of this recommendation, it is a good idea to understand and carefully modify the generation commands provided in this document.

### Step 2: Preparation

Clone our fork of _planetiler-openmaptiles_:

```bash
git clone https://gitlab.e.foundation/ellenhp/planetiler-openmaptiles.git
```

Install Java 21 or later.

### Step 3: Compilation

In the top-level directory of the _planetiler-openmaptiles_ repo, run `./mvnw clean package` to perform a clean build of the project. When the build completes, artifacts will be placed in the `build/` directory.

### Step 4: Verify Planetiler Functionality (Optional)

To ensure that Planetiler is functioning correctly you can run the following command. This will download Natural Earth data, a global map of bodies of water and an OpenStreetMap extract of Monaco, then it will run the tile generation operation.

```bash
java -jar target/*with-deps.jar --download --area=monaco
```

Planetiler refuses to overwrite `.mbtiles` files by default, so you will need to remove the `data/output.mbtiles` file if you run this more than once.

### Step 5: Generate Tiles for the Planet

Using the OpenStreetMap file downloaded in step 1, run planetiler on the entire planet.

You can refer to the [Planetiler documentation on full-planet builds](https://github.com/onthegomap/planetiler/blob/main/PLANET.md) for tips and tricks, but keep in mind that the hardware recommendations will not apply to our fork because we are including quite a bit more information in the `poi` and `house_number` layers of our generated tiles, and this information takes up extra space in physical memory.

As of late 2025, the following command is recommended in the documentation for full-planet builds, and should work unmodified on sufficiently powerful hardware:

```bash
java -Xmx40g \
  -jar target/*with-deps.jar \
  `# Use the planet.osm.pbf file downloaded earlier here.` \
  --osm_path=$OSM_PATH --download \
  `# Accelerate necessary downloads by fetching the 10 1GB chunks at a time in parallel` \
  --download-threads=10 --download-chunk-size-mb=1000 \
  `# Also download name translations from wikidata` \
  --fetch-wikidata \
  --output=./data/planet.mbtiles \
  `# Store temporary node locations at fixed positions in a memory-mapped file` \
  --nodemap-type=array --storage=mmap
```

This command will take several hours to complete depending on hardware specifications. Plan on 1-8 hours depending on CPU count. You can refer to the [benchmarks on mainline planetiler's README](https://github.com/onthegomap/planetiler/tree/main?tab=readme-ov-file#benchmarks) for estimates, keeping in mind that our fork is performing additional work compared to upstream.

The generated MBTiles will be located at `data/planet.mbtiles`.

### Step 6: Convert the Generated MBTiles to PMTiles

PMTiles is a cloud-native format for collections of map tiles that allows a tile serving system to scale horizontally without requiring large SSD volumes to be attached to each tileserver instance. The essence of the technology is to move data from local storage to an object store and require each tileserver instance to keep an in-memory index of which sections of the PMTiles contain various parts of the world.

Latency between the tileserver instances and the Object Store should be minimized for good performance, and the Object Store must support HTTP range requests.

To perform the conversion, ensure a sufficiently modern version of **Go** is installed, and install the pmtiles utilities according to the [documentation](https://docs.protomaps.com/guide/getting-started)

In the root of the PMTiles repo, run the following: `pmtiles convert $MBTILES_PATH planet.pmtiles`

This is generally considered to be a quick operation, but in the author's experience it may take considerable time to complete, or appear to hang.

### Step 7: Upload PMTiles to Object Storage

The commands to execute in this step depend on the production environment used, but the goal is to make the PMTiles archive available to all tileservers.

### Step 8: Point Tileservers to New Artifact

It is recommended to use a Blue-Green deployement strategy for tileserver data updates, so previous versions should not be overwritten in this step. Instead, use a new key for the new PMTiles archive, ideally incorporating the original date/version code of the `planet.osm.pbf` used for generation.

Tileserver configuration should be updated on a rolling basis to point to the new artifact. Health checks can be implemented to detect configuration issues such as typos in the path/key of the PMTiles artifact.

Once it has been determined that deployment has succeded and a rollback is not necessary, the old artifact can be removed. It would be prudent to wait for some time to do this, or implement a retention policy of several days or more.

## Additional Considerations

### CDNs and Caching

A CDN can be put in front of the tileservers to reduce latency. As the tileservers are serving mostly static data, a TTL of hours not minutes may be appropriate. There's a tradeoff here though, as an excessive cache TTL increases the risk of multiple tile versions existing in a Cardinal Maps client's own cache. This can lead to things like visually misaligned roads or other artifacts.

