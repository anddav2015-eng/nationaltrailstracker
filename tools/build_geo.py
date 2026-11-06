#!/usr/bin/env python3
"""Builds app/src/main/assets/geo/<slug>.json route lines from official open data.

Output format (see CLAUDE.md): [[lat, lon, cumulativeMiles], ...], simplified with
Douglas-Peucker (~10 m) and scaled so the line's mileage runs from the start landmark's
mileage to the end landmark's mileage in trail-data. The trail-data table stays the source
of truth for stages; the line only answers "how far along am I".

Source data is downloaded by hand into trail-data/geo-source/ (it isn't committed):
  natural-england: National_Trails.geojson.zip (or the extracted .geojson) from
                   https://www.data.gov.uk/dataset/ac8c851c-99a0-4488-8973-6c8863529c45/national-trails-england3
                   Open Government Licence; attribution in ATTRIBUTION.md.

The source is a network of line pieces in no particular order, sometimes with alternative
routes (e.g. both banks of the Thames in London). This joins pieces that touch into a graph,
then takes the shortest path from start, through each 'via' point in tools/geo_config.json,
to the end - so the via points choose which variant matches the trail-data mileages.

Usage: python tools/build_geo.py [slug ...]     (default: every trail in geo_config.json)
After building, re-run tools/convert_trails.py so each trail's gpxAsset points at its line.
"""
import heapq
import json
import math
import sys
import zipfile
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

sys.path.insert(0, str(Path(__file__).resolve().parent))
from convert_trails import parse_trail_file  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = REPO_ROOT / "tools" / "geo_config.json"
SOURCE_DIR = REPO_ROOT / "trail-data" / "geo-source"
TRAIL_DATA_DIR = REPO_ROOT / "trail-data"
GEO_ASSETS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "geo"

SIMPLIFY_TOLERANCE_M = 10.0
JOIN_TOLERANCE_M = 3.0      # line ends closer than this are the same node
BRIDGE_GAP_M = 30.0         # small gaps between pieces are bridged (and reported)
EARTH_RADIUS_M = 6_371_008.8
METRES_PER_MILE = 1609.344


# ---------------------------------------------------------------------------
# Geometry helpers
# ---------------------------------------------------------------------------

def haversine_m(a, b):
    lat1, lon1 = map(math.radians, a)
    lat2, lon2 = map(math.radians, b)
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(h))


def polyline_length_m(points):
    return sum(haversine_m(points[i], points[i + 1]) for i in range(len(points) - 1))


def to_local_xy(points, origin):
    """Equirectangular projection to metres around origin - plenty for 10 m simplification."""
    k = math.cos(math.radians(origin[0]))
    deg = math.pi / 180 * EARTH_RADIUS_M
    return [((p[1] - origin[1]) * deg * k, (p[0] - origin[0]) * deg) for p in points]


def point_segment_distance(p, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    length_sq = dx * dx + dy * dy
    t = 0.0 if length_sq == 0 else max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length_sq))
    return math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy))


def douglas_peucker(points, tolerance_m):
    """Iterative Douglas-Peucker on [lat, lon] points (no recursion-depth limit on long trails)."""
    if len(points) < 3:
        return list(points)
    xy = to_local_xy(points, points[len(points) // 2])
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        best_index, best_distance = -1, tolerance_m
        for i in range(first + 1, last):
            d = point_segment_distance(xy[i], xy[first], xy[last])
            if d > best_distance:
                best_index, best_distance = i, d
        if best_index != -1:
            keep[best_index] = True
            stack.append((first, best_index))
            stack.append((best_index, last))
    return [p for p, k in zip(points, keep) if k]


def osgb36_to_wgs84(easting, northing):
    """British National Grid (EPSG:27700) to WGS84 lat/lon via a Helmert transform (~5 m accuracy).
    Only used if the download turns out to be in BNG rather than WGS84."""
    a, b = 6377563.396, 6356256.909
    f0, lat0, lon0 = 0.9996012717, math.radians(49), math.radians(-2)
    n0, e0 = -100000, 400000
    e2 = 1 - (b * b) / (a * a)
    n = (a - b) / (a + b)
    lat, m = lat0, 0.0
    while northing - n0 - m >= 0.00001:
        lat = (northing - n0 - m) / (a * f0) + lat
        ma = (1 + n + 1.25 * n**2 + 1.25 * n**3) * (lat - lat0)
        mb = (3 * n + 3 * n**2 + 2.625 * n**3) * math.sin(lat - lat0) * math.cos(lat + lat0)
        mc = (1.875 * n**2 + 1.875 * n**3) * math.sin(2 * (lat - lat0)) * math.cos(2 * (lat + lat0))
        md = 35 / 24 * n**3 * math.sin(3 * (lat - lat0)) * math.cos(3 * (lat + lat0))
        m = b * f0 * (ma - mb + mc - md)
    sin_lat, cos_lat, tan_lat = math.sin(lat), math.cos(lat), math.tan(lat)
    nu = a * f0 / math.sqrt(1 - e2 * sin_lat**2)
    rho = a * f0 * (1 - e2) / (1 - e2 * sin_lat**2) ** 1.5
    eta2 = nu / rho - 1
    vii = tan_lat / (2 * rho * nu)
    viii = tan_lat / (24 * rho * nu**3) * (5 + 3 * tan_lat**2 + eta2 - 9 * tan_lat**2 * eta2)
    ix = tan_lat / (720 * rho * nu**5) * (61 + 90 * tan_lat**2 + 45 * tan_lat**4)
    x = 1 / (cos_lat * nu)
    xi = 1 / (cos_lat * 6 * nu**3) * (nu / rho + 2 * tan_lat**2)
    xii = 1 / (cos_lat * 120 * nu**5) * (5 + 28 * tan_lat**2 + 24 * tan_lat**4)
    xiia = 1 / (cos_lat * 5040 * nu**7) * (61 + 662 * tan_lat**2 + 1320 * tan_lat**4 + 720 * tan_lat**6)
    de = easting - e0
    lat = lat - vii * de**2 + viii * de**4 - ix * de**6
    lon = lon0 + x * de - xi * de**3 + xii * de**5 - xiia * de**7
    # OSGB36 lat/lon -> cartesian -> Helmert -> WGS84 cartesian -> lat/lon
    sin_lat, cos_lat = math.sin(lat), math.cos(lat)
    nu = a / math.sqrt(1 - e2 * sin_lat**2)
    x1, y1, z1 = nu * cos_lat * math.cos(lon), nu * cos_lat * math.sin(lon), nu * (1 - e2) * sin_lat
    tx, ty, tz, s = 446.448, -125.157, 542.060, -20.4894e-6
    rx, ry, rz = (math.radians(v / 3600) for v in (0.1502, 0.2470, 0.8421))
    x2 = tx + (1 + s) * x1 - rz * y1 + ry * z1
    y2 = ty + rz * x1 + (1 + s) * y1 - rx * z1
    z2 = tz - ry * x1 + rx * y1 + (1 + s) * z1
    a2, b2 = 6378137.000, 6356752.3142
    e2w = 1 - (b2 * b2) / (a2 * a2)
    p = math.hypot(x2, y2)
    lat = math.atan2(z2, p * (1 - e2w))
    for _ in range(10):
        nu = a2 / math.sqrt(1 - e2w * math.sin(lat) ** 2)
        lat = math.atan2(z2 + e2w * nu * math.sin(lat), p)
    return math.degrees(lat), math.degrees(math.atan2(y2, x2))


# ---------------------------------------------------------------------------
# Source loading
# ---------------------------------------------------------------------------

def load_natural_england():
    candidates = sorted(SOURCE_DIR.glob("*.geojson")) + sorted(SOURCE_DIR.glob("*.geojson.zip"))
    if not candidates:
        sys.exit(
            f"No Natural England download found in {SOURCE_DIR}.\n"
            "Download National_Trails.geojson.zip from\n"
            "  https://www.data.gov.uk/dataset/ac8c851c-99a0-4488-8973-6c8863529c45/national-trails-england3\n"
            "and put it (zipped or extracted) in that folder."
        )
    path = candidates[0]
    if path.suffix == ".zip":
        with zipfile.ZipFile(path) as z:
            name = next(n for n in z.namelist() if n.lower().endswith(".geojson"))
            return json.loads(z.read(name).decode("utf-8"))
    return json.loads(path.read_text(encoding="utf-8"))


def feature_name(properties):
    """The trail name field isn't documented; take the first string property naming a trail."""
    for key, value in properties.items():
        if isinstance(value, str) and ("way" in value.lower() or "path" in value.lower() or "trail" in value.lower()
                                       or "ridgeway" in value.lower() or "bridleway" in value.lower()):
            return value
    return None


def lines_for_trail(collection, name_match):
    """[lat, lon] polylines for every feature whose name contains name_match."""
    lines, names = [], set()
    for feature in collection["features"]:
        name = feature_name(feature.get("properties") or {})
        if name:
            names.add(name)
        if not name or name_match.lower() not in name.lower():
            continue
        geometry = feature["geometry"]
        parts = [geometry["coordinates"]] if geometry["type"] == "LineString" else geometry["coordinates"]
        for part in parts:
            coords = [c[:2] for c in part]
            if any(abs(c[0]) > 180 for c in coords):  # British National Grid metres, not degrees
                lines.append([list(osgb36_to_wgs84(e, n)) for e, n in coords])
            else:
                lines.append([[lat, lon] for lon, lat in coords])
    if not lines:
        sys.exit(f"No features matched {name_match!r}. Names in the file:\n  " + "\n  ".join(sorted(names)))
    return lines


# ---------------------------------------------------------------------------
# Network + routing
# ---------------------------------------------------------------------------

class Network:
    def __init__(self, lines):
        self.nodes = []          # [lat, lon]
        self.edges = []          # (u, v, points [lat, lon] from u to v, length_m)
        self.adjacent = {}       # node -> [(neighbour, edge index)]
        self.bridged_gaps = []
        for line in lines:
            if len(line) >= 2:
                self._add_edge(self._node_for(line[0]), self._node_for(line[-1]), line)
        self._bridge_small_gaps()

    def _node_for(self, point):
        for i, node in enumerate(self.nodes):
            if haversine_m(node, point) <= JOIN_TOLERANCE_M:
                return i
        self.nodes.append(point)
        return len(self.nodes) - 1

    def _add_edge(self, u, v, points):
        index = len(self.edges)
        self.edges.append((u, v, points, polyline_length_m(points)))
        self.adjacent.setdefault(u, []).append((v, index))
        self.adjacent.setdefault(v, []).append((u, index))

    def _bridge_small_gaps(self):
        dead_ends = [n for n, adj in self.adjacent.items() if len(adj) == 1]
        for i, a in enumerate(dead_ends):
            for b in dead_ends[i + 1:]:
                gap = haversine_m(self.nodes[a], self.nodes[b])
                if gap <= BRIDGE_GAP_M:
                    self._add_edge(a, b, [self.nodes[a], self.nodes[b]])
                    self.bridged_gaps.append(gap)

    def nearest_node(self, point):
        return min(range(len(self.nodes)), key=lambda i: haversine_m(self.nodes[i], point))

    def nearest_edge(self, point):
        best, best_distance = None, float("inf")
        for index, (_, _, points, _) in enumerate(self.edges):
            xy = to_local_xy(points, point)
            d = min(point_segment_distance((0.0, 0.0), xy[i], xy[i + 1]) for i in range(len(xy) - 1))
            if d < best_distance:
                best, best_distance = index, d
        return best, best_distance

    def shortest_path(self, source, target):
        """Dijkstra; returns [(edge index, forwards?)] from source to target."""
        distance = {source: 0.0}
        previous = {}
        queue = [(0.0, source)]
        while queue:
            d, node = heapq.heappop(queue)
            if node == target:
                break
            if d > distance.get(node, float("inf")):
                continue
            for neighbour, edge_index in self.adjacent.get(node, []):
                nd = d + self.edges[edge_index][3]
                if nd < distance.get(neighbour, float("inf")):
                    distance[neighbour] = nd
                    previous[neighbour] = (node, edge_index)
                    heapq.heappush(queue, (nd, neighbour))
        if target not in distance:
            raise ValueError(f"No connection between node {source} and node {target} - is there an unbridged gap?")
        path, node = [], target
        while node != source:
            prev, edge_index = previous[node]
            path.append((edge_index, self.edges[edge_index][0] == prev))
            node = prev
        return list(reversed(path)), distance[target]

    def route(self, start, via_points, end):
        """Start node -> through each via edge -> end node, as one [lat, lon] polyline."""
        current = self.nearest_node(start)
        steps = []
        for via in via_points:
            edge_index, off = via["edge"], via["off_m"]
            u, v, _, _ = self.edges[edge_index]
            to_u, du = self.shortest_path(current, u)
            to_v, dv = self.shortest_path(current, v)
            if du <= dv:
                steps += to_u + [(edge_index, True)]
                current = v
            else:
                steps += to_v + [(edge_index, False)]
                current = u
        tail, _ = self.shortest_path(current, self.nearest_node(end))
        steps += tail
        line = []
        for edge_index, forwards in steps:
            points = self.edges[edge_index][2]
            points = points if forwards else list(reversed(points))
            line.extend(points if not line else points[1:])
        return line


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def landmark_miles(trail_data, name):
    for landmark in trail_data["landmarks"]:
        if landmark[0] == name:
            return float(landmark[1])
    sys.exit(f"Landmark {name!r} not found in trail-data")


def build(slug, settings, source_cache):
    _, trail_data = parse_trail_file(TRAIL_DATA_DIR / f"{slug}.js")
    if settings["source"] not in source_cache:
        source_cache[settings["source"]] = {"natural-england": load_natural_england}[settings["source"]]()
    lines = lines_for_trail(source_cache[settings["source"]], settings["nameMatch"])

    network = Network(lines)
    junctions = sum(1 for adj in network.adjacent.values() if len(adj) > 2)
    dead_ends = sum(1 for adj in network.adjacent.values() if len(adj) == 1)
    print(f"{slug}: {len(lines)} source pieces -> {len(network.nodes)} nodes, {len(network.edges)} edges, "
          f"{junctions} junctions, {dead_ends} dead ends, {len(network.bridged_gaps)} gaps bridged"
          + (f" (largest {max(network.bridged_gaps):.1f} m)" if network.bridged_gaps else ""))

    via_points = []
    for via in settings.get("via", []):
        edge_index, off = network.nearest_edge(via["point"])
        print(f"  via {via['label']}: {off:.0f} m from the network")
        if off > 150:
            print("    WARNING: via point is far from any path - check its coordinates")
        via_points.append({"edge": edge_index, "off_m": off})

    line = network.route(settings["start"], via_points, settings["end"])
    simplified = douglas_peucker(line, SIMPLIFY_TOLERANCE_M)

    start_miles = landmark_miles(trail_data, settings.get("startLandmark", trail_data["landmarks"][0][0]))
    end_miles = landmark_miles(trail_data, settings.get("endLandmark", trail_data["landmarks"][-1][0]))
    raw_miles = polyline_length_m(simplified) / METRES_PER_MILE
    scale = (end_miles - start_miles) / raw_miles
    print(f"  route line {raw_miles:.1f} miles (GPS) vs {end_miles - start_miles:.1f} in trail-data "
          f"-> scale {scale:.3f}; {len(line)} points simplified to {len(simplified)}")
    if not 0.85 <= scale <= 1.1:
        print("  WARNING: lengths differ a lot - the wrong variant/bank may have been chosen; check the via points")

    out, cumulative = [], 0.0
    for i, point in enumerate(simplified):
        if i:
            cumulative += haversine_m(simplified[i - 1], point) / METRES_PER_MILE * scale
        out.append([round(point[0], 6), round(point[1], 6), round(start_miles + cumulative, 3)])

    GEO_ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    out_path = GEO_ASSETS_DIR / f"{slug}.json"
    out_path.write_text(json.dumps(out, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"  wrote {out_path.relative_to(REPO_ROOT)} ({out_path.stat().st_size // 1024} KB)")


def main():
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    slugs = sys.argv[1:] or [k for k in config if not k.startswith("_")]
    cache = {}
    for slug in slugs:
        build(slug, config[slug], cache)


if __name__ == "__main__":
    main()
