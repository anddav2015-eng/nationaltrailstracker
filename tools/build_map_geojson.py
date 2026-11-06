#!/usr/bin/env python3
"""Builds map route lines (GeoJSON) from the official National Trails GPX files.

For each trail in tools/map_geojson_config.json this writes three versions of the main route to
app/src/main/assets/trails/:
  <slug>-full.geojson   every GPX point (no simplification)
  <slug>-2m.geojson     Douglas-Peucker, 2 m tolerance
  <slug>-5m.geojson     Douglas-Peucker, 5 m tolerance

Simplification is done in metres in British National Grid (EPSG:27700), then converted back to
WGS84. Coordinates are [lon, lat, elevation]: lon/lat rounded to 6 decimal places, elevation
(metres, from the GPX) to 0.1 m.

Nothing is silently repaired. The script prints a report of every track in the file, plus any
gaps between merged segments, long straight steps and sharp reversals in the main route, so they
can be checked by eye.

If the trail's config has an "osmRelation", it also writes <slug>-osm.geojson: the route as mapped
in OpenStreetMap, which lines up with the paths drawn on the OSM-based map. It's built from the
relation's member ways, from the OSM node nearest the GPX start to the one nearest the GPX end.
Where the OSM ways don't connect, the gap is filled with the official GPX line and reported.
OSM has no elevation, so each vertex takes it from the nearest point on the GPX line (approximate).
Every stretch more than 20 m from the official line is reported.

The GPX is downloaded to trail-data/gpx-source/ and the OSM relation to trail-data/osm-source/ if
they aren't already there (neither folder is committed; delete a file to refresh it). See
tools/README.md for setup.

Usage: python tools/build_map_geojson.py [slug ...]   (default: every trail in the config)
"""
import bisect
import heapq
import json
import math
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

import gpxpy
from pyproj import Geod, Transformer
from shapely.geometry import LineString, Point

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

REPO_ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = REPO_ROOT / "tools" / "map_geojson_config.json"
SOURCE_DIR = REPO_ROOT / "trail-data" / "gpx-source"
OSM_SOURCE_DIR = REPO_ROOT / "trail-data" / "osm-source"
OSM_API = "https://api.openstreetmap.org/api/0.6"
USER_AGENT = "NationalTrailsTracker-build-tool/1.0 (personal, non-commercial)"
OUT_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "trails"

VERSIONS = [("full", None), ("2m", 2.0), ("5m", 5.0)]  # (file suffix, tolerance in metres)
COORD_DP = 6
ELE_DP = 1
METRES_PER_MILE = 1609.344
SEGMENT_GAP_WARN_M = 20.0   # gap between consecutive merged segments worth reporting
LONG_STEP_WARN_M = 400.0    # straight step between two GPX points worth reporting
REVERSAL_WARN_DEG = 150.0   # turn sharper than this is reported as a possible spike/backtrack
ATTRIBUTION = "National Trails / Natural England and Natural Resources Wales"
OSM_ATTRIBUTION = "© OpenStreetMap contributors"
OSM_DEVIATION_WARN_M = 20.0  # OSM line further than this from the GPX line is reported
OSM_RUN_MERGE_M = 100.0      # reported stretches closer together than this are listed as one
OSM_GAP_SEARCH_M = 1000.0    # dead ends of separate OSM pieces closer than this may be joined by GPX
OSM_GAP_PENALTY = 10.0       # a GPX-filled gap costs this many times its length, so OSM is preferred

TO_BNG = Transformer.from_crs("EPSG:4326", "EPSG:27700", always_xy=True)
TO_WGS = Transformer.from_crs("EPSG:27700", "EPSG:4326", always_xy=True)
GEOD = Geod(ellps="WGS84")


def geodesic_miles(lonlat):
    lons = [p[0] for p in lonlat]
    lats = [p[1] for p in lonlat]
    return GEOD.line_length(lons, lats) / METRES_PER_MILE


def fetch_gpx(url):
    SOURCE_DIR.mkdir(parents=True, exist_ok=True)
    path = SOURCE_DIR / url.rsplit("/", 1)[-1]
    if not path.exists():
        print(f"Downloading {url}")
        with urllib.request.urlopen(url, timeout=60) as resp:
            path.write_bytes(resp.read())
    return path


def describe_tracks(gpx):
    """Report every track/segment, so diversions and link routes are visible."""
    print(f"  waypoints: {len(gpx.waypoints)}, routes: {len(gpx.routes)}, tracks: {len(gpx.tracks)}")
    for t in gpx.tracks:
        for si, seg in enumerate(t.segments):
            pts = [(p.longitude, p.latitude) for p in seg.points]
            no_ele = sum(p.elevation is None for p in seg.points)
            print(f"  track {t.name!r} seg {si}: {len(pts)} pts, {geodesic_miles(pts):.2f} mi, "
                  f"start {pts[0][1]:.5f},{pts[0][0]:.5f} end {pts[-1][1]:.5f},{pts[-1][0]:.5f}"
                  + (f", {no_ele} pts without elevation" if no_ele else ""))


def merge_main(gpx, track_names):
    """Concatenate the named tracks' segments in file order. Gaps are reported, not bridged."""
    by_name = {t.name: t for t in gpx.tracks}
    missing = [n for n in track_names if n not in by_name]
    if missing:
        sys.exit(f"  ERROR: main track(s) not in GPX: {missing}. Names present: {list(by_name)}")
    points = []
    for name in track_names:
        for si, seg in enumerate(by_name[name].segments):
            seg_pts = [(p.longitude, p.latitude, p.elevation) for p in seg.points]
            if points:
                gap = geodesic_miles([points[-1][:2], seg_pts[0][:2]]) * METRES_PER_MILE
                if gap > SEGMENT_GAP_WARN_M:
                    print(f"  WARNING: {gap:.0f} m gap before {name!r} seg {si} (left as a straight join)")
                if gap == 0:
                    seg_pts = seg_pts[1:]
            points.extend(seg_pts)
    return points


def check_line(points):
    """Report long straight steps and sharp reversals in the merged main route."""
    xy = [TO_BNG.transform(p[0], p[1]) for p in points]
    cum = [0.0]
    for i in range(1, len(xy)):
        cum.append(cum[-1] + math.dist(xy[i - 1], xy[i]))
    total = cum[-1]

    def where(i):
        return (f"{cum[i] / METRES_PER_MILE:6.1f} mi from line start / {(total - cum[i]) / METRES_PER_MILE:6.1f} "
                f"from end, at {points[i][1]:.5f},{points[i][0]:.5f}")

    long_steps = [(i, math.dist(xy[i], xy[i + 1])) for i in range(len(xy) - 1)]
    long_steps = [(i, d) for i, d in long_steps if d > LONG_STEP_WARN_M]
    dup = sum(1 for i in range(len(xy) - 1) if xy[i] == xy[i + 1])
    steps = sorted(math.dist(xy[i], xy[i + 1]) for i in range(len(xy) - 1))
    print(f"  point spacing: median {steps[len(steps) // 2]:.0f} m, max {steps[-1]:.0f} m; "
          f"{len(long_steps)} steps over {LONG_STEP_WARN_M:.0f} m; {dup} duplicate points")
    for i, d in long_steps:
        print(f"    {d:5.0f} m step   {where(i)}")
    for i in range(1, len(xy) - 1):
        a = math.atan2(xy[i][1] - xy[i - 1][1], xy[i][0] - xy[i - 1][0])
        b = math.atan2(xy[i + 1][1] - xy[i][1], xy[i + 1][0] - xy[i][0])
        turn = math.degrees(abs((b - a + math.pi) % (2 * math.pi) - math.pi))
        if turn > REVERSAL_WARN_DEG:
            print(f"    {turn:3.0f}° reversal (legs {math.dist(xy[i - 1], xy[i]):.0f} m / "
                  f"{math.dist(xy[i], xy[i + 1]):.0f} m)   {where(i)}")
    if not LineString(xy).is_simple:
        print("  WARNING: main route crosses itself")


def simplify(points, tolerance_m):
    """Douglas-Peucker in EPSG:27700. Elevation rides along as Z on the kept vertices."""
    if tolerance_m is None:
        return points
    bng = [(*TO_BNG.transform(p[0], p[1]), p[2]) for p in points]
    kept = LineString(bng).simplify(tolerance_m, preserve_topology=False).coords
    out = []
    for x, y, z in kept:
        lon, lat = TO_WGS.transform(x, y)
        out.append((lon, lat, z))
    return out


def to_geojson(slug, points, version, tolerance_m, source_url, track_names):
    coords = []
    for lon, lat, ele in points:
        c = [round(lon, COORD_DP), round(lat, COORD_DP)]
        if ele is not None:
            c.append(round(ele, ELE_DP))
        if not coords or coords[-1][:2] != c[:2]:   # rounding can make neighbours identical
            coords.append(c)
    feature = {
        "type": "Feature",
        "properties": {
            "trail": slug,
            "version": version,
            "simplifyToleranceM": tolerance_m,
            "simplifyCrs": "EPSG:27700" if tolerance_m else None,
            "vertexCount": len(coords),
            "lengthMiles": round(geodesic_miles(coords), 2),
            "sourceGpx": source_url,
            "sourceTracks": track_names,
            "attribution": ATTRIBUTION,
        },
        "geometry": {"type": "LineString", "coordinates": coords},
    }
    return {"type": "FeatureCollection", "features": [feature]}


def http_get(url, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        print(f"  Downloading {url}")
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=300) as resp:
            path.write_bytes(resp.read())
    return path


class GpxRef:
    """The official GPX line in EPSG:27700, for measuring and borrowing elevation/gap fills."""

    def __init__(self, points):
        self.points = points
        self.xy = [TO_BNG.transform(p[0], p[1]) for p in points]
        self.line = LineString(self.xy)
        self.cum = [0.0]
        for i in range(1, len(self.xy)):
            self.cum.append(self.cum[-1] + math.dist(self.xy[i - 1], self.xy[i]))

    def elevation_at(self, xy):
        m = self.line.project(Point(xy))
        i = min(max(bisect.bisect_right(self.cum, m) - 1, 0), len(self.cum) - 2)
        e0, e1 = self.points[i][2], self.points[i + 1][2]
        if e0 is None or e1 is None:
            return e0 if e1 is None else e1
        span = self.cum[i + 1] - self.cum[i]
        return e0 + (e1 - e0) * ((m - self.cum[i]) / span if span else 0)

    def between(self, a_xy, b_xy):
        """GPX points (lon, lat, ele) strictly between the projections of a and b, in a→b order."""
        ma, mb = self.line.project(Point(a_xy)), self.line.project(Point(b_xy))
        lo, hi = min(ma, mb), max(ma, mb)
        inner = [p for p, c in zip(self.points, self.cum) if lo < c < hi]
        return inner if ma <= mb else inner[::-1]


def build_osm(slug, relation_id, gpx_points):
    """Route through the OSM relation's ways from the GPX start to the GPX end."""
    print(f"  -- OpenStreetMap relation {relation_id} --")
    path = http_get(f"{OSM_API}/relation/{relation_id}/full.json",
                    OSM_SOURCE_DIR / f"relation-{relation_id}-full.json")
    elements = json.loads(path.read_text(encoding="utf-8"))["elements"]
    rel = next(e for e in elements if e["type"] == "relation" and e["id"] == relation_id)
    lonlat = {e["id"]: (e["lon"], e["lat"]) for e in elements if e["type"] == "node"}
    ways = {e["id"]: e for e in elements if e["type"] == "way"}
    member_ways = [ways[m["ref"]] for m in rel["members"] if m["type"] == "way" and m["ref"] in ways]
    print(f"  relation '{rel['tags'].get('name')}' v{rel['version']} ({rel['timestamp']}): "
          f"{len(member_ways)} ways")

    xy = {n: TO_BNG.transform(*ll) for n, ll in lonlat.items()}
    adj = defaultdict(dict)   # node -> {neighbour: cost}
    for w in member_ways:
        for a, b in zip(w["nodes"], w["nodes"][1:]):
            d = math.dist(xy[a], xy[b])
            adj[a][b] = adj[b][a] = d

    # Connected pieces, so separate pieces can be bridged by the official line.
    piece, n_pieces = {}, 0
    for start in adj:
        if start in piece:
            continue
        stack = [start]
        piece[start] = n_pieces
        while stack:
            for nb in adj[stack.pop()]:
                if nb not in piece:
                    piece[nb] = n_pieces
                    stack.append(nb)
        n_pieces += 1
    dead_ends = [n for n in adj if len(adj[n]) == 1]
    print(f"  {n_pieces} connected piece(s), {len(dead_ends)} dead ends")
    gap_edges = set()
    for i, a in enumerate(dead_ends):
        for b in dead_ends[i + 1:]:
            d = math.dist(xy[a], xy[b])
            if piece[a] != piece[b] and d < OSM_GAP_SEARCH_M:
                adj[a][b] = adj[b][a] = d * OSM_GAP_PENALTY
                gap_edges |= {(a, b), (b, a)}

    gpx = GpxRef(gpx_points)
    src = min(adj, key=lambda n: math.dist(xy[n], gpx.xy[0]))
    dst = min(adj, key=lambda n: math.dist(xy[n], gpx.xy[-1]))
    print(f"  start node {math.dist(xy[src], gpx.xy[0]):.0f} m from the GPX start, "
          f"end node {math.dist(xy[dst], gpx.xy[-1]):.0f} m from the GPX end")

    dist, prev, heap = {src: 0.0}, {}, [(0.0, src)]
    while heap:
        d, n = heapq.heappop(heap)
        if n == dst:
            break
        if d > dist[n]:
            continue
        for nb, c in adj[n].items():
            if d + c < dist.get(nb, math.inf):
                dist[nb], prev[nb] = d + c, n
                heapq.heappush(heap, (d + c, nb))
    if dst not in dist:
        print("  WARNING: no OSM route between the ends, even with GPX gap fills; skipping OSM version")
        return None
    route = [dst]
    while route[-1] != src:
        route.append(prev[route[-1]])
    route.reverse()

    points, fills = [], []
    for i, n in enumerate(route):
        if i and (route[i - 1], n) in gap_edges:
            a, b = route[i - 1], n
            fill = gpx.between(xy[a], xy[b])
            fills.append({"from": [round(c, COORD_DP) for c in lonlat[a][::-1]],
                          "to": [round(c, COORD_DP) for c in lonlat[n][::-1]],
                          "metres": round(math.dist(xy[a], xy[b]))})
            print(f"  GAP in OSM route, filled with {len(fill)} official GPX point(s): "
                  f"{math.dist(xy[a], xy[b]):.0f} m between {lonlat[a][1]:.5f},{lonlat[a][0]:.5f} "
                  f"and {lonlat[b][1]:.5f},{lonlat[b][0]:.5f}")
            points.extend(fill)
        points.append((*lonlat[n], gpx.elevation_at(xy[n])))
    report_deviation(points, gpx)
    return points, fills, rel


def report_deviation(points, gpx):
    """List every stretch of the OSM line more than OSM_DEVIATION_WARN_M from the official line."""
    line = LineString([TO_BNG.transform(p[0], p[1]) for p in points])
    total = line.length
    samples = [(m, gpx.line.distance(line.interpolate(m))) for m in range(0, int(total), 10)]
    devs = sorted(d for _, d in samples)
    n = len(devs)
    print(f"  distance from the official line (every 10 m): median {devs[n // 2]:.1f} m, "
          f"p90 {devs[int(n * .9)]:.1f} m, p99 {devs[int(n * .99)]:.0f} m, max {devs[-1]:.0f} m; "
          f"{sum(d > OSM_DEVIATION_WARN_M for d in devs) / n * 100:.1f}% over {OSM_DEVIATION_WARN_M:.0f} m")
    runs = []
    for m, d in samples:
        if d > OSM_DEVIATION_WARN_M:
            if runs and m - runs[-1][1] <= OSM_RUN_MERGE_M:
                runs[-1][1], runs[-1][2] = m, max(runs[-1][2], d)
            else:
                runs.append([m, m, d])
    print(f"  {len(runs)} stretch(es) more than {OSM_DEVIATION_WARN_M:.0f} m from the official line "
          f"(miles from line start / end, length, max distance):")
    for a, b, mx in runs:
        lon, lat = TO_WGS.transform(*line.interpolate((a + b) / 2).coords[0])
        print(f"    {a / METRES_PER_MILE:6.1f} / {(total - b) / METRES_PER_MILE:6.1f} mi  "
              f"{b - a + 10:5.0f} m long  max {mx:4.0f} m   at {lat:.5f},{lon:.5f}")


def build(slug, cfg):
    print(f"\n== {slug} ==")
    gpx_path = fetch_gpx(cfg["gpxUrl"])
    with open(gpx_path, encoding="utf-8") as f:
        gpx = gpxpy.parse(f)
    describe_tracks(gpx)
    others = [t.name for t in gpx.tracks if t.name not in cfg["mainTracks"]]
    print(f"  main route: {cfg['mainTracks']}; left out: {others or 'none'}")
    points = merge_main(gpx, cfg["mainTracks"])
    check_line(points)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    official = cfg.get("officialMiles")
    print(f"  {'version':<8}{'vertices':>10}{'miles':>9}{'vs official':>13}{'size KB':>10}")
    for version, tol in VERSIONS:
        gj = to_geojson(slug, simplify(points, tol), version, tol, cfg["gpxUrl"], cfg["mainTracks"])
        path = OUT_DIR / f"{slug}-{version}.geojson"
        path.write_text(json.dumps(gj, separators=(",", ":")), encoding="utf-8")
        props = gj["features"][0]["properties"]
        diff = f"{props['lengthMiles'] - official:+.2f}" if official else ""
        print(f"  {version:<8}{props['vertexCount']:>10}{props['lengthMiles']:>9.2f}{diff:>13}"
              f"{path.stat().st_size / 1024:>10.1f}")

    if "osmRelation" in cfg:
        result = build_osm(slug, cfg["osmRelation"], points)
        if result:
            osm_points, fills, rel = result
            gj = to_geojson(slug, osm_points, "osm", None, cfg["gpxUrl"], cfg["mainTracks"])
            props = gj["features"][0]["properties"]
            del props["sourceGpx"], props["sourceTracks"]
            props.update({
                "source": f"OpenStreetMap relation {rel['id']} v{rel['version']} ({rel['timestamp']})",
                "licence": "ODbL 1.0",
                "attribution": OSM_ATTRIBUTION,
                "elevation": "approximate, from the nearest point on the official GPX line",
                "gpxFilledGaps": fills,
            })
            path = OUT_DIR / f"{slug}-osm.geojson"
            path.write_text(json.dumps(gj, separators=(",", ":"), ensure_ascii=False), encoding="utf-8")
            diff = f"{props['lengthMiles'] - official:+.2f}" if official else ""
            print(f"  {'osm':<8}{props['vertexCount']:>10}{props['lengthMiles']:>9.2f}{diff:>13}"
                  f"{path.stat().st_size / 1024:>10.1f}")


def main():
    config = {k: v for k, v in json.loads(CONFIG_PATH.read_text(encoding="utf-8")).items()
              if not k.startswith("_")}
    slugs = sys.argv[1:] or list(config)
    for slug in slugs:
        if slug not in config:
            sys.exit(f"Unknown trail {slug!r}; configured: {list(config)}")
        build(slug, config[slug])


if __name__ == "__main__":
    main()
