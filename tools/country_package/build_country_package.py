#!/usr/bin/env python3
"""
Build one downloadable country package from the same Murena sources used by the app.

Experiment goal:
- keep MapLibre/Valhalla data formats unchanged;
- move thousands of HTTP requests from the phone to CI;
- ship one ZIP to the phone later;
- prove size/time before wiring an installer into Android.

Current experiment supports Poland minimal mode:
  basemap z5-z12 + complete Valhalla graph for the existing Poland bounding box.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import math
import os
import sqlite3
import sys
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
from typing import Iterable, List, Sequence, Tuple

ROOT = Path(__file__).resolve().parents[2]

COUNTRIES = {
    "PL": {
        "name": "Poland",
        "bbox": {"south": 49.0, "north": 54.9, "west": 14.1, "east": 24.2},
        "min_zoom": 5,
        "max_zoom": 12,
        "buffer_km": 20.0,
    }
}

BASEMAP_URL = "https://tiles.maps.murena.com/tiles/planet-260112/planet/{z}/{x}/{y}.mvt"
VALHALLA_BASE_URL = "https://tiles.maps.murena.com/valhalla-250825"
BOUNDARY_ASSET = ROOT / "cardinal-android/app/src/main/assets/europe_country_boundaries.json"

BASEMAP_WORKERS = 32
VALHALLA_WORKERS = 16
HTTP_ATTEMPTS = 6
USER_AGENT = "Kapijuja-Country-Package-Experiment/1.0"


def lon_to_x(lon: float, zoom: int) -> int:
    return int(math.floor((lon + 180.0) / 360.0 * (1 << zoom)))


def lat_to_y(lat: float, zoom: int) -> int:
    lat_rad = math.radians(lat)
    n = float(1 << zoom)
    return int(math.floor((1.0 - math.log(math.tan(lat_rad) + 1.0 / math.cos(lat_rad)) / math.pi) / 2.0 * n))


def tile_center(zoom: int, x: int, y: int) -> Tuple[float, float]:
    n = 2.0 ** zoom
    lon = (x + 0.5) / n * 360.0 - 180.0
    mercator = math.pi * (1.0 - 2.0 * (y + 0.5) / n)
    lat = math.degrees(math.atan(math.sinh(mercator)))
    return lon, lat


def tile_half_diagonal_km(zoom: int, lat: float) -> float:
    n = 2.0 ** zoom
    c = math.cos(math.radians(lat))
    width_km = 40075.016686 * c / n
    height_km = 40007.863 * c / n
    return 0.5 * math.hypot(width_km, height_km)


def point_in_ring(point: Tuple[float, float], ring: Sequence[Sequence[float]]) -> bool:
    lon, lat = point
    inside = False
    j = len(ring) - 1
    for i in range(len(ring)):
        ax, ay = ring[i][0], ring[i][1]
        bx, by = ring[j][0], ring[j][1]
        crosses = ((ay > lat) != (by > lat)) and (
            lon < (bx - ax) * (lat - ay) / ((by - ay) if abs(by - ay) > 1e-12 else 1e-12) + ax
        )
        if crosses:
            inside = not inside
        j = i
    return inside


def point_segment_distance_km(
    point: Tuple[float, float],
    a: Sequence[float],
    b: Sequence[float],
) -> float:
    lon, lat = point
    cos_lat = math.cos(math.radians(lat))
    kx = 111.320 * cos_lat
    ky = 110.574
    ax = (a[0] - lon) * kx
    ay = (a[1] - lat) * ky
    bx = (b[0] - lon) * kx
    by = (b[1] - lat) * ky
    dx = bx - ax
    dy = by - ay
    length_sq = dx * dx + dy * dy
    if length_sq <= 1e-12:
        return math.hypot(ax, ay)
    t = max(0.0, min(1.0, (-ax * dx - ay * dy) / length_sq))
    return math.hypot(ax + t * dx, ay + t * dy)


def distance_to_ring_km(point: Tuple[float, float], ring: Sequence[Sequence[float]]) -> float:
    best = float("inf")
    previous = ring[-1]
    for current in ring:
        best = min(best, point_segment_distance_km(point, previous, current))
        previous = current
    return best


def load_country_rings(country_code: str) -> List[Sequence[Sequence[float]]]:
    root = json.loads(BOUNDARY_ASSET.read_text(encoding="utf-8"))
    geometry = root["geometries"].get(country_code)
    if not geometry:
        raise RuntimeError(f"No boundary geometry for {country_code}")
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"][0]]
    if geometry["type"] == "MultiPolygon":
        return [polygon[0] for polygon in geometry["coordinates"]]
    raise RuntimeError(f"Unsupported geometry type: {geometry['type']}")


def tile_in_country_buffer(
    zoom: int,
    x: int,
    y: int,
    rings: Sequence[Sequence[Sequence[float]]],
    buffer_km: float,
) -> bool:
    # Match Android behaviour: low zooms are kept as the complete rectangle.
    if zoom < 10:
        return True
    center = tile_center(zoom, x, y)
    effective_buffer = buffer_km + tile_half_diagonal_km(zoom, center[1])
    for ring in rings:
        if point_in_ring(center, ring):
            return True
        if distance_to_ring_km(center, ring) <= effective_buffer:
            return True
    return False


def basemap_tiles(config: dict, rings: Sequence[Sequence[Sequence[float]]]) -> List[Tuple[int, int, int]]:
    bbox = config["bbox"]
    result: List[Tuple[int, int, int]] = []
    for zoom in range(config["min_zoom"], config["max_zoom"] + 1):
        min_x = lon_to_x(bbox["west"], zoom)
        max_x = lon_to_x(bbox["east"], zoom)
        min_y = lat_to_y(bbox["north"], zoom)
        max_y = lat_to_y(bbox["south"], zoom)
        for x in range(min_x, max_x + 1):
            for y in range(min_y, max_y + 1):
                if tile_in_country_buffer(zoom, x, y, rings, config["buffer_km"]):
                    result.append((zoom, x, y))
    return result


def valhalla_tiles(config: dict) -> List[Tuple[int, int]]:
    bbox = config["bbox"]
    level_to_size = {0: 4.0, 1: 1.0, 2: 0.25}
    adjusted_left = bbox["west"] + 180.0
    adjusted_right = bbox["east"] + 180.0
    adjusted_bottom = bbox["south"] + 90.0
    adjusted_top = bbox["north"] + 90.0
    result: List[Tuple[int, int]] = []
    for level, size in level_to_size.items():
        for x in range(int(adjusted_left / size), int(adjusted_right / size) + 1):
            for y in range(int(adjusted_bottom / size), int(adjusted_top / size) + 1):
                tile_index = int(y * (360.0 / size) + x)
                result.append((level, tile_index))
    return result


def valhalla_relative_path(level: int, tile_index: int) -> Path:
    if level == 2:
        group1 = tile_index // 1_000_000
        group2 = (tile_index // 1000) % 1000
        ident = tile_index % 1000
        return Path(str(level)) / f"{group1:03d}" / f"{group2:03d}" / f"{ident:03d}.gph"
    group = tile_index // 1000
    ident = tile_index % 1000
    return Path(str(level)) / f"{group:03d}" / f"{ident:03d}.gph"


def fetch_bytes(url: str) -> bytes:
    last_error: Exception | None = None
    for attempt in range(1, HTTP_ATTEMPTS + 1):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=60) as response:
                if response.status != 200:
                    raise RuntimeError(f"HTTP {response.status}")
                data = response.read()
                if not data:
                    raise RuntimeError("empty response")
                return data
        except Exception as exc:
            last_error = exc
            if attempt == HTTP_ATTEMPTS:
                break
            time.sleep(min(8.0, 0.5 * (2 ** (attempt - 1))))
    raise RuntimeError(f"Failed after {HTTP_ATTEMPTS} attempts: {url}: {last_error}")


def fetch_optional_bytes(url: str) -> tuple[bytes | None, int]:
    """
    Fetch an object that may legitimately not exist in Murena object storage.
    Some storage/CDN configurations return 403 instead of 404 for a missing key.
    We only treat 403/404 as 'not published'; transport/5xx errors still retry/fail.
    """
    last_error: Exception | None = None
    for attempt in range(1, HTTP_ATTEMPTS + 1):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=60) as response:
                if response.status != 200:
                    raise RuntimeError(f"HTTP {response.status}")
                data = response.read()
                if not data:
                    raise RuntimeError("empty response")
                return data, response.status
        except urllib.error.HTTPError as exc:
            if exc.code in (403, 404):
                return None, exc.code
            last_error = exc
        except Exception as exc:
            last_error = exc

        if attempt < HTTP_ATTEMPTS:
            time.sleep(min(8.0, 0.5 * (2 ** (attempt - 1))))

    raise RuntimeError(f"Failed after {HTTP_ATTEMPTS} attempts: {url}: {last_error}")


def initialize_mbtiles(path: Path, country_name: str, min_zoom: int, max_zoom: int) -> sqlite3.Connection:
    connection = sqlite3.connect(path)
    connection.execute("PRAGMA journal_mode=WAL")
    connection.execute("PRAGMA synchronous=NORMAL")
    connection.execute(
        "CREATE TABLE IF NOT EXISTS metadata (name TEXT PRIMARY KEY, value TEXT)"
    )
    connection.execute(
        "CREATE TABLE IF NOT EXISTS tiles ("
        "zoom_level INTEGER NOT NULL, tile_column INTEGER NOT NULL, tile_row INTEGER NOT NULL, tile_data BLOB NOT NULL,"
        "PRIMARY KEY (zoom_level, tile_column, tile_row))"
    )
    metadata = {
        "name": f"Kapijuja {country_name} minimal package",
        "type": "baselayer",
        "version": "1.0",
        "description": "Prebuilt country basemap for Kapijuja Nav",
        "format": "pbf",
        "minzoom": str(min_zoom),
        "maxzoom": str(max_zoom),
        "scheme": "tms",
    }
    connection.executemany(
        "INSERT OR REPLACE INTO metadata(name, value) VALUES(?, ?)",
        metadata.items(),
    )
    connection.commit()
    return connection


def existing_mbtiles_keys(connection: sqlite3.Connection) -> set[Tuple[int, int, int]]:
    return {
        (int(z), int(x), int(y))
        for z, x, y in connection.execute(
            "SELECT zoom_level, tile_column, tile_row FROM tiles"
        )
    }


def build_basemap(
    workdir: Path,
    country_name: str,
    tiles: Sequence[Tuple[int, int, int]],
    min_zoom: int,
    max_zoom: int,
) -> Path:
    mbtiles = workdir / "basemap.mbtiles"
    connection = initialize_mbtiles(mbtiles, country_name, min_zoom, max_zoom)
    existing_tms = existing_mbtiles_keys(connection)

    pending = []
    for z, x, y in tiles:
        tms_y = (1 << z) - 1 - y
        if (z, x, tms_y) not in existing_tms:
            pending.append((z, x, y))

    print(f"Basemap: {len(tiles)} expected, {len(tiles)-len(pending)} already present, {len(pending)} pending", flush=True)

    completed = len(tiles) - len(pending)
    batch: List[Tuple[int, int, int, bytes]] = []

    def worker(tile: Tuple[int, int, int]):
        z, x, y = tile
        url = BASEMAP_URL.format(z=z, x=x, y=y)
        return z, x, y, fetch_bytes(url)

    with concurrent.futures.ThreadPoolExecutor(max_workers=BASEMAP_WORKERS) as executor:
        for z, x, y, data in executor.map(worker, pending, chunksize=1):
            tms_y = (1 << z) - 1 - y
            batch.append((z, x, tms_y, data))
            completed += 1
            if len(batch) >= 128:
                connection.executemany(
                    "INSERT OR REPLACE INTO tiles(zoom_level, tile_column, tile_row, tile_data) VALUES(?, ?, ?, ?)",
                    batch,
                )
                connection.commit()
                batch.clear()
            if completed % 250 == 0 or completed == len(tiles):
                print(f"Basemap progress: {completed}/{len(tiles)}", flush=True)

    if batch:
        connection.executemany(
            "INSERT OR REPLACE INTO tiles(zoom_level, tile_column, tile_row, tile_data) VALUES(?, ?, ?, ?)",
            batch,
        )
        connection.commit()

    connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    connection.execute("VACUUM")
    connection.close()
    return mbtiles


def build_valhalla(
    workdir: Path,
    tiles: Sequence[Tuple[int, int]],
) -> tuple[Path, int, int, dict[int, int]]:
    root = workdir / "valhalla_tiles"
    root.mkdir(parents=True, exist_ok=True)

    pending: List[Tuple[int, int]] = []
    installed = 0
    for level, index in tiles:
        target = root / valhalla_relative_path(level, index)
        if target.is_file() and target.stat().st_size > 0:
            installed += 1
        else:
            pending.append((level, index))

    print(
        f"Valhalla candidates: {len(tiles)}, already installed: {installed}, "
        f"to probe/download: {len(pending)}",
        flush=True,
    )

    def worker(tile: Tuple[int, int]):
        level, index = tile
        relative = valhalla_relative_path(level, index)
        target = root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = Path(str(target) + ".part")
        url = f"{VALHALLA_BASE_URL}/{relative.as_posix()}"

        data, status = fetch_optional_bytes(url)
        if data is None:
            partial.unlink(missing_ok=True)
            target.unlink(missing_ok=True)
            return level, index, 0, status

        partial.write_bytes(data)
        os.replace(partial, target)
        return level, index, len(data), 200

    completed_probes = 0
    missing = 0
    missing_by_status: dict[int, int] = {}
    downloaded = 0

    with concurrent.futures.ThreadPoolExecutor(max_workers=VALHALLA_WORKERS) as executor:
        for _level, _index, size, status in executor.map(worker, pending, chunksize=1):
            completed_probes += 1
            if status == 200:
                downloaded += 1
                installed += 1
            else:
                missing += 1
                missing_by_status[status] = missing_by_status.get(status, 0) + 1

            if completed_probes % 50 == 0 or completed_probes == len(pending):
                print(
                    f"Valhalla probe progress: {completed_probes}/{len(pending)}; "
                    f"installed={installed}; absent={missing}; "
                    f"statuses={missing_by_status}",
                    flush=True,
                )

    print(
        f"Valhalla published graph tiles: {installed}/{len(tiles)} candidates; "
        f"absent objects: {missing}; status breakdown: {missing_by_status}",
        flush=True,
    )
    return root, installed, missing, missing_by_status


def directory_size(path: Path) -> int:
    return sum(p.stat().st_size for p in path.rglob("*") if p.is_file())


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def create_package(
    output_dir: Path,
    country_code: str,
    config: dict,
    mbtiles: Path,
    valhalla_root: Path,
    basemap_count: int,
    valhalla_candidate_count: int,
    valhalla_installed_count: int,
    valhalla_absent_count: int,
    valhalla_absent_statuses: dict[int, int],
) -> Path:
    package_dir = output_dir / f"{country_code}-minimal-work"
    manifest_path = package_dir / "manifest.json"
    manifest = {
        "schema_version": 1,
        "country_code": country_code,
        "country_name": config["name"],
        "mode": "minimal",
        "basemap": {
            "file": "basemap.mbtiles",
            "min_zoom": config["min_zoom"],
            "max_zoom": config["max_zoom"],
            "tile_count": basemap_count,
            "bytes": mbtiles.stat().st_size,
            "source": BASEMAP_URL,
        },
        "routing": {
            "directory": "valhalla_tiles",
            "candidate_tile_count": valhalla_candidate_count,
            "installed_tile_count": valhalla_installed_count,
            "absent_object_count": valhalla_absent_count,
            "absent_http_statuses": {
                str(code): count for code, count in sorted(valhalla_absent_statuses.items())
            },
            "bytes": directory_size(valhalla_root),
            "source": VALHALLA_BASE_URL,
        },
        "coverage": {
            "bbox": config["bbox"],
            "country_buffer_km": config["buffer_km"],
        },
        "generated_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True), encoding="utf-8")

    zip_path = output_dir / f"Kapijuja-{country_code}-minimal-country-package.zip"
    with zipfile.ZipFile(
        zip_path,
        "w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=6,
        allowZip64=True,
    ) as archive:
        archive.write(manifest_path, "manifest.json")
        archive.write(mbtiles, "basemap.mbtiles")
        for file in sorted(valhalla_root.rglob("*")):
            if file.is_file():
                archive.write(file, str(Path("valhalla_tiles") / file.relative_to(valhalla_root)))

    sha = sha256_file(zip_path)
    (output_dir / (zip_path.name + ".sha256")).write_text(
        f"{sha}  {zip_path.name}\n",
        encoding="utf-8",
    )
    print(json.dumps(manifest, indent=2), flush=True)
    print(f"PACKAGE={zip_path}", flush=True)
    print(f"PACKAGE_BYTES={zip_path.stat().st_size}", flush=True)
    print(f"PACKAGE_SHA256={sha}", flush=True)
    return zip_path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--country", default="PL", choices=sorted(COUNTRIES))
    parser.add_argument("--output-dir", default="country-package-output")
    args = parser.parse_args()

    config = COUNTRIES[args.country]
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    workdir = output_dir / f"{args.country}-minimal-work"
    workdir.mkdir(parents=True, exist_ok=True)

    rings = load_country_rings(args.country)
    map_tiles = basemap_tiles(config, rings)
    routing_tiles = valhalla_tiles(config)

    print(
        f"Building {args.country}: {len(map_tiles)} basemap tiles, "
        f"{len(routing_tiles)} Valhalla tiles",
        flush=True,
    )

    mbtiles = build_basemap(
        workdir,
        config["name"],
        map_tiles,
        config["min_zoom"],
        config["max_zoom"],
    )
    (
        valhalla_root,
        valhalla_installed_count,
        valhalla_absent_count,
        valhalla_absent_statuses,
    ) = build_valhalla(workdir, routing_tiles)
    create_package(
        output_dir,
        args.country,
        config,
        mbtiles,
        valhalla_root,
        len(map_tiles),
        len(routing_tiles),
        valhalla_installed_count,
        valhalla_absent_count,
        valhalla_absent_statuses,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
