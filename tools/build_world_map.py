"""Builds android/app/src/main/assets/world_map.json for the profile's visited-countries map.

Source: Natural Earth admin-0 countries (public domain), https://www.naturalearthdata.com
  - outlines: the 1:50m file. 1:110m is 5x smaller but draws Italy as a blob
    and drops islands and microstates; 1:50m is ~470 KB after quantizing.
  - continents: the 1:50m file too, which covers every ISO code we might see.
    (Separate arguments so the outlines can go coarser without losing the table.)

Usage:
  curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson
  python tools/build_world_map.py ne_50m_admin_0_countries.geojson ne_50m_admin_0_countries.geojson

Coordinates are pre-projected to Equal Earth and quantized to an integer grid,
so the app only scales and draws. The app projects visit dots with the same
formula (core/map/WorldMap.kt) - keep the constants in sync, and update
MAP_ASPECT_RATIO in VisitedCountriesCard.kt if the printed grid size changes.
"""
import json
import math
import sys
from pathlib import Path

# Fine enough that a single country zoomed to phone width (the country sheet)
# keeps smooth coastlines; 2000 was fine for the world view but stair-stepped.
GRID_WIDTH = 16000

# Equal Earth projection (Savric, Patterson & Jenny, 2018).
A1, A2, A3, A4 = 1.340264, -0.081106, 0.000893, 0.003796
M = math.sqrt(3) / 2


def equal_earth(lon_deg, lat_deg):
    lam, phi = math.radians(lon_deg), math.radians(lat_deg)
    theta = math.asin(M * math.sin(phi))
    t2 = theta * theta
    t6 = t2 * t2 * t2
    x = lam * math.cos(theta) / (M * (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2)))
    y = theta * (A1 + A2 * t2 + t6 * (A3 + A4 * t2))
    return x, y


CONTINENTS = {
    "Africa": "AF", "Asia": "AS", "Europe": "EU", "North America": "NA",
    "South America": "SA", "Oceania": "OC",
}

# Disputed regions Natural Earth draws separately but reverse geocoders report
# as their parent country - fold them in so the parent's fill covers them.
PARENT = {"N. Cyprus": "CY", "Somaliland": "SO"}


def iso(props):
    if props["NAME"] in PARENT:
        return PARENT[props["NAME"]]
    code = props.get("ISO_A2_EH") or props.get("ISO_A2")
    return None if code in (None, "-99") else code


def rings(geometry):
    polys = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
    for poly in polys:
        yield from poly


def main(outlines_path, continents_path, out_path):
    outlines = json.loads(Path(outlines_path).read_text(encoding="utf-8"))["features"]
    metadata = json.loads(Path(continents_path).read_text(encoding="utf-8"))["features"]

    continent_of = {}
    for f in metadata:
        code, continent = iso(f["properties"]), CONTINENTS.get(f["properties"]["CONTINENT"])
        if code and continent:
            continent_of[code] = continent

    projected = {}  # code -> list of projected rings
    for f in outlines:
        props = f["properties"]
        if props["CONTINENT"] == "Antarctica":
            continue  # Stippl-style: drop it, it's a third of the height for no visits
        code = iso(props)
        if not code:
            continue
        projected.setdefault(code, []).extend(
            [equal_earth(lon, lat) for lon, lat in ring] for ring in rings(f["geometry"])
        )

    xs = [x for rs in projected.values() for r in rs for x, _ in r]
    ys = [y for rs in projected.values() for r in rs for _, y in r]
    x_min, x_max, y_min, y_max = min(xs), max(xs), min(ys), max(ys)
    scale = GRID_WIDTH / (x_max - x_min)
    grid_height = round((y_max - y_min) * scale)

    def to_grid(x, y):  # y flipped: screen y grows downward
        return round((x - x_min) * scale), round((y_max - y) * scale)

    countries = []
    for code, rs in sorted(projected.items()):
        flat_rings = []
        for ring in rs:
            pts, last = [], None
            for x, y in ring:
                p = to_grid(x, y)
                if p != last:
                    pts.extend(p)
                    last = p
            if len(pts) >= 6:  # at least a triangle
                flat_rings.append(pts)
        countries.append({"c": code, "k": continent_of.get(code, ""), "p": flat_rings})

    out = {
        "source": "Natural Earth 1:50m admin-0 countries (public domain); built by tools/build_world_map.py",
        "w": GRID_WIDTH,
        "h": grid_height,
        # Projected-space bounds, so the app can place visit dots on the same grid.
        "bounds": [x_min, y_min, x_max, y_max],
        "countries": countries,
        # Continents for every ISO code, including ones too small to have an outline.
        "continents": dict(sorted(continent_of.items())),
    }
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    Path(out_path).write_text(json.dumps(out, separators=(",", ":")), encoding="utf-8")
    print(f"{len(countries)} outlines, {len(continent_of)} continent entries, "
          f"grid {GRID_WIDTH}x{grid_height}, {Path(out_path).stat().st_size // 1024} KB -> {out_path}")


if __name__ == "__main__":
    here = Path(__file__).resolve().parent.parent
    main(sys.argv[1], sys.argv[2], here / "android/app/src/main/assets/world_map.json")
