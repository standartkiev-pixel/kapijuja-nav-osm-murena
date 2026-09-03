# Routing

This document covers the generation of [Valhalla tiles](https://valhalla.github.io/valhalla/tiles/) that Cardinal Maps uses for its offline routing features and potentially more functionality in the future.

In comparison to the generation and preparation of [basemap tiles](./basemap.md), routing tiles are more straightforward to generate and serve. We do not require a fork of Valhalla, nor do we need to do any conversion of its output artifacts beyond extracting them from the tarball artifact.

Some of the setup steps are shared across both documents, and these steps are covered more explicitly in the basemap docs, so if anything is unclear here it may be helpful to follow that document first.

#### Warning!

Executing the following steps prior to the introduction of a tile versioning feature in the client may cause offline features in deployed clients to break.

## Generation

The easiest way to generate Valhalla tiles is by using the [Valhalla docker image](https://github.com/valhalla/valhalla/pkgs/container/valhalla).

[Documentation for tile generation](https://valhalla.github.io/valhalla/mjolnir/getting_started_guide/) is available from upstream and can be consulted should the steps in this guide ever become out-of-date.

## Step 1: Set Up the Artifact Directory

Set the environment variable `VALHALLA_DATA_PATH` to the directory into which you would like to put the Valhalla tiles. As in the [basemap documentation](./basemap.md), the author would recommend naming this directory based on the version/date code of the `planet.osm.pbf` extract that the tiles would be based on.

Put the OpenStreetMap extract into the artifact directory and note its path.

## Step 2: Start the Valhalla Container

Until instructed otherwise, run the following steps inside of the Valhalla OCI container. This is the podman command to start it, but you can use Docker by changing the command name to `docker`.

`podman run --rm -it -v $VALHALLA_DATA_PATH:/data:Z ghcr.io/valhalla/valhalla`

## Step 3: Generate the Valhalla Configuration

This step can be omitted if you re-run the tile generation process. Moreover, the configuration file it generates can be modified to include things like time zones, administrative boundaries (to avoid crossing borders unnecessarily) and more accurate road speeds. If you do modify the config, be careful not to overwrite your changes by executing this command a second time.

`valhalla_build_config > /data/valhalla.json`

## Step 4: Generate the Valhalla Tiles

Remembering to substitute in the name of your OpenStreetMap extract, run the following command:

`cd /data && valhalla_build_tiles --config  /data/valhalla.json /data/planet.osm.pbf`

This will take several hours to complete, but should only require 128 GB of RAM. Possibly less.

## Step 5: Un-tar the Tiles

The Valhalla tiles will be placed in the `/data` directory as one `.tar` archive and must be extracted before they can be copied to object storage.

## Step 6: Copy the Tiles To Object Storage

Use `rclone` or similar to copy the entire extracted directory hierarchy to object storage incorporating the OSM extract's date code into the key prefix.

## Step 7: Point a Reverse Proxy To the New Tiles

Set up a reverse proxy to point to the new tiles. The proxy can be re-deployed with a different destination prefix to update the underlying routing data.

Be careful not to typo the destination prefix, and make sure to test the app's offline download after a deploy to ensure that tiles are still downloaded correctly.

