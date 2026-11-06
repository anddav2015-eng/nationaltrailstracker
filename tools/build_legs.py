#!/usr/bin/env python3
"""Finds landmark coordinates and cuts each trail route line into landmark-to-landmark legs.

Run after tools/build_map_geojson.py (which makes the route lines). For each trail in
tools/landmark_config.json:

1. Each landmark gets a position:
   - an override 'point' from the config, if there is one; otherwise
   - a Nominatim (OpenStreetMap) search for the landmark name, with any bracketed note stripped
     ("Putney Bridge (cross to north)" -> "Putney Bridge"), or the override 'query'. Searches are
     limited to a box around the route line. Of up to 10 results, the one that fits best is used:
     close to the line, and at a point along the line that matches the landmark's table mileage.
   Results are cached in trail-data/geocode-cache/ (not committed), so re-runs make no requests.
2. Each position is snapped to the reference line. Landmarks are checked for order along the line
   and for agreement with the table mileages (after scaling the table to the line's length), and
   anything doubtful is flagged. Nothing is changed in trail-data; fix problems with an override.
3. Writes trail-data/landmark-coords/<slug>.json (the trail-point coordinates and how each was
   found). tools/convert_trails.py merges these into the landmarks' lat/lon.
4. For each line version, writes app/src/main/assets/trails/<slug>-<version>-legs.geojson: one
   LineString per leg (landmark i to i+1), in the trail's walking direction (first landmark first).
   Leg properties hold the table miles and the miles measured along the line. Table mileages are
   never changed.

Usage: python tools/build_legs.py [slug ...]    then: python tools/convert_trails.py
"""
import json
import math
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

from pyproj import Geod, Transformer
from shapely.geometry import LineString, Point
from shapely.ops import substring

sys.path.insert(0, str(Path(__file__).resolve().parent))
from convert_trails import parse_trail_file  # noqa: E402

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

REPO_ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = REPO_ROOT / "tools" / "landmark_config.json"
TRAIL_DATA_DIR = REPO_ROOT / "trail-data"
CACHE_DIR = TRAIL_DATA_DIR / "geocode-cache"
COORDS_DIR = TRAIL_DATA_DIR / "landmark-coords"
ASSETS_TRAILS = REPO_ROOT / "app" / "src" / "main" / "assets" / "trails"

NOMINATIM = "https://nominatim.openstreetmap.org/search"
USER_AGENT = "NationalTrailsTracker-build-tool/1.0 (personal, non-commercial)"
REQUEST_INTERVAL_S = 1.1          # Nominatim usage policy: at most 1 request per second
SEARCH_BOX_MARGIN_DEG = 0.05      # search box around the line (~5 km)
OFF_LINE_WARN_M = 300.0           # geocoded point further than this from the line is flagged
MAX_OFF_LINE_M = 1500.0           # matches further than this are rejected (then interpolated)
NEIGHBOURS = 3                    # landmarks either side used to judge whether one is out of step
RESIDUAL_WARN_MI = 0.5            # out of step with neighbours by more than this is flagged
COORD_DP = 6
ELE_DP = 1
METRES_PER_MILE = 1609.344

TO_BNG = Transformer.from_crs("EPSG:4326", "EPSG:27700", always_xy=True)
TO_WGS = Transformer.from_crs("EPSG:27700", "EPSG:4326", always_xy=True)
GEOD = Geod(ellps="WGS84")


def strip_note(name):
    return re.sub(r"\s*\([^)]*\)", "", name).strip()


class Geocoder:
    def __init__(self, slug):
        self.path = CACHE_DIR / f"{slug}.json"
        self.cache = json.loads(self.path.read_text(encoding="utf-8")) if self.path.exists() else {}
        self.last = 0.0

    def search(self, query, box):
        key = f"{query}|{box}"
        if key not in self.cache:
            wait = REQUEST_INTERVAL_S - (time.monotonic() - self.last)
            if wait > 0:
                time.sleep(wait)
            params = {"q": query, "format": "jsonv2", "countrycodes": "gb", "limit": 10,
                      "viewbox": box, "bounded": 1}
            req = urllib.request.Request(f"{NOMINATIM}?{urllib.parse.urlencode(params)}",
                                         headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=60) as resp:
                results = json.load(resp)
            self.last = time.monotonic()
            self.cache[key] = [{"lat": float(r["lat"]), "lon": float(r["lon"]),
                                "name": r.get("display_name", ""), "type": f"{r.get('category')}/{r.get('type')}"}
                               for r in results]
            self.path.parent.mkdir(parents=True, exist_ok=True)
            self.path.write_text(json.dumps(self.cache, indent=1, ensure_ascii=False), encoding="utf-8")
        return self.cache[key]


class Line:
    """A route line in EPSG:27700, oriented in the trail's walking direction."""

    def __init__(self, coords_lonlat_ele):
        self.xyz = [(*TO_BNG.transform(c[0], c[1]), c[2] if len(c) > 2 else 0.0) for c in coords_lonlat_ele]
        self.line = LineString(self.xyz)
        self.length = self.line.length

    def reversed(self):
        r = Line([])
        r.xyz, r.line, r.length = self.xyz[::-1], LineString(self.xyz[::-1]), self.length
        return r

    def locate(self, lat, lon):
        """(metres along, metres off) for a WGS84 point."""
        p = Point(TO_BNG.transform(lon, lat))
        return self.line.project(p), self.line.distance(p)

    def point_at(self, m):
        x, y = self.line.interpolate(m).coords[0][:2]
        lon, lat = TO_WGS.transform(x, y)
        return lat, lon


def load_line(slug, version):
    path = ASSETS_TRAILS / f"{slug}-{version}.geojson"
    if not path.exists():
        sys.exit(f"Missing {path}; run tools/build_map_geojson.py first")
    gj = json.loads(path.read_text(encoding="utf-8"))
    return Line(gj["features"][0]["geometry"]["coordinates"])


def orient(line, landmarks, geocoded_first_last):
    """Return the line oriented so it runs from the first landmark towards the last."""
    (lat0, lon0), (lat1, lon1) = geocoded_first_last
    m0, _ = line.locate(lat0, lon0)
    m1, _ = line.locate(lat1, lon1)
    return line if m0 <= m1 else line.reversed()


def search_box(line):
    lons, lats = zip(*(TO_WGS.transform(x, y) for x, y, _ in line.xyz[::20] + [line.xyz[-1]]))
    m = SEARCH_BOX_MARGIN_DEG
    return f"{min(lons) - m:.4f},{max(lats) + m:.4f},{max(lons) + m:.4f},{min(lats) - m:.4f}"


def choose(candidates, line, expected_m):
    """Best candidate: near the line, and near the expected point along it."""
    best = None
    for c in candidates:
        along, off = line.locate(c["lat"], c["lon"])
        score = off + abs(along - expected_m) * 0.25
        if best is None or score < best[0]:
            best = (score, c, along, off)
    return best


def find_positions(slug, landmarks, overrides, ref):
    """Geocode every landmark onto the reference line (already in walking direction)."""
    geocoder = Geocoder(slug)
    box = search_box(ref)
    table_total = landmarks[-1]["milesFromStart"] - landmarks[0]["milesFromStart"]
    scale = ref.length / (table_total * METRES_PER_MILE)
    out = []
    for lm in landmarks:
        expected = (lm["milesFromStart"] - landmarks[0]["milesFromStart"]) * METRES_PER_MILE * scale
        ov = overrides.get(lm["name"], {})
        if "point" in ov:
            lat, lon = ov["point"]
            along, off = ref.locate(lat, lon)
            rec = {"method": "override", "why": ov.get("why"), "source": [lat, lon]}
        else:
            query = ov.get("query") or strip_note(lm["name"])
            cands = geocoder.search(query, box)
            if not cands and " / " in query:          # "A / B": try each part
                for part in query.split(" / "):
                    cands += geocoder.search(part, box)
            if not cands and " & " in query:          # "A & B": try each part
                for part in query.split(" & "):
                    cands += geocoder.search(part, box)
            best = choose(cands, ref, expected) if cands and not ov.get("interpolate") else None
            if best is None or best[3] > MAX_OFF_LINE_M:
                why = ov.get("why") if ov.get("interpolate") else (
                    f"no match within {MAX_OFF_LINE_M:.0f} m of the line for '{query}'")
                out.append({"name": lm["name"], "method": "interpolated", "why": why, "query": query,
                            "expected": expected, "auto": not ov.get("interpolate")})
                continue
            _, c, along, off = best
            rec = {"method": "nominatim", "query": query, "source": [c["lat"], c["lon"]],
                   "match": c["name"][:90], "matchType": c["type"], "candidates": len(cands)}
        rec.update({"name": lm["name"], "along": along, "off": off, "expected": expected})
        out.append(rec)
    interpolate(out, landmarks, ref)
    return out, scale


def interpolate(positions, landmarks, ref):
    """Place 'interpolated' landmarks between their nearest placed neighbours, by table mileage."""
    placed = [i for i, p in enumerate(positions) if p["method"] != "interpolated"]
    for i, p in enumerate(positions):
        if p["method"] != "interpolated":
            continue
        before = [j for j in placed if j < i]
        after = [j for j in placed if j > i]
        if not before or not after:
            p["why"] = (p.get("why") or "") + " (no placed neighbour on both sides; left out)"
            continue
        a, b = before[-1], after[0]
        ma, mb = positions[a]["along"], positions[b]["along"]
        fa, fb = landmarks[a]["milesFromStart"], landmarks[b]["milesFromStart"]
        along = ma + (mb - ma) * (landmarks[i]["milesFromStart"] - fa) / (fb - fa)
        lat, lon = ref.point_at(along)
        p.update({"along": along, "off": 0.0, "source": [lat, lon],
                  "between": [landmarks[a]["name"], landmarks[b]["name"]]})


def check(positions, landmarks):
    """Flag positions that are off the line, out of order, or out of step with their neighbours.

    The line and the table can drift apart steadily (different routes or measuring), so each
    landmark's (line - table) difference is compared with the median of its neighbours' rather
    than with zero.
    """
    diffs = [(p["along"] - p["expected"]) / METRES_PER_MILE if "along" in p else None for p in positions]
    prev_along = -1.0
    flagged = 0
    for i, (p, lm) in enumerate(zip(positions, landmarks)):
        flags = []
        if "along" not in p:
            flags.append("NOT PLACED")
        else:
            if p["off"] > OFF_LINE_WARN_M:
                flags.append(f"{p['off']:.0f} m off line")
            if p["along"] <= prev_along:
                flags.append("OUT OF ORDER")
            nb = sorted(d for j, d in enumerate(diffs[max(0, i - NEIGHBOURS):i + NEIGHBOURS + 1])
                        if d is not None and j + max(0, i - NEIGHBOURS) != i)
            if nb:
                p["residual"] = diffs[i] - nb[len(nb) // 2]
                if abs(p["residual"]) > RESIDUAL_WARN_MI:
                    flags.append(f"{p['residual']:+.1f} mi out of step with neighbours")
            if p["method"] == "interpolated" and p.get("auto"):
                flags.append("no geocode match; interpolated")
            prev_along = max(prev_along, p["along"])
        p["flags"] = flags
        flagged += bool(flags)
    return flagged


def print_table(positions, landmarks, scale):
    print(f"  {'table':>6} {'line':>6} {'diff':>5} {'step':>5} {'off m':>6}  {'how':<12} landmark  ->  match / flags")
    for p, lm in zip(positions, landmarks):
        if "along" in p:
            line_mi = p["along"] / METRES_PER_MILE / scale + landmarks[0]["milesFromStart"]
            diff = line_mi - lm["milesFromStart"]
            res = f"{p['residual']:+5.1f}" if "residual" in p else ""
            head = f"  {lm['milesFromStart']:6.1f} {line_mi:6.1f} {diff:+5.1f} {res:>5} {p['off']:6.0f}"
        else:
            head = f"  {lm['milesFromStart']:6.1f} {'':>6} {'':>5} {'':>5} {'':>6}"
        if p["method"] == "interpolated" and "between" in p:
            detail = f"between {p['between'][0]} and {p['between'][1]}" + (f" ({p['why']})" if p.get("why") and not p.get("auto") else "")
        else:
            detail = p.get("match") or p.get("why") or p.get("query", "")
        if p["method"] == "nominatim" and p.get("query") != strip_note(lm["name"]):
            detail = f"[{p['query']}] {detail}"
        flags = ("   <<< " + "; ".join(p["flags"])) if p["flags"] else ""
        print(f"{head}  {p['method']:<12} {lm['name']}  ->  {detail[:80]}{flags}")


def write_coords(slug, positions, ref, ref_version):
    rows = []
    for p in positions:
        if "along" not in p:
            continue
        lat, lon = ref.point_at(p["along"])
        row = {"name": p["name"], "lat": round(lat, COORD_DP), "lon": round(lon, COORD_DP),
               "method": p["method"]}
        if p["method"] == "nominatim":
            row.update({"query": p["query"], "match": p["match"],
                        "sourcePoint": [round(v, COORD_DP) for v in p["source"]], "offLineM": round(p["off"])})
        elif p["method"] == "interpolated":
            row.update({"between": p["between"], "why": p.get("why")})
        else:
            row.update({"why": p.get("why"), "sourcePoint": [round(v, COORD_DP) for v in p["source"]],
                        "offLineM": round(p["off"])})
        rows.append(row)
    doc = {
        "_comment": "Generated by tools/build_legs.py; don't edit by hand (use overrides in "
                    "tools/landmark_config.json). lat/lon are the landmark's point on the "
                    f"'{ref_version}' route line. Geocoding: Nominatim / © OpenStreetMap contributors.",
        "landmarks": rows,
    }
    COORDS_DIR.mkdir(parents=True, exist_ok=True)
    path = COORDS_DIR / f"{slug}.json"
    path.write_text(json.dumps(doc, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    return path, rows


def write_legs(slug, version, line, landmarks, coords, stages):
    by_name = {r["name"]: r for r in coords}
    stage_of = {}
    names = [lm["name"] for lm in landmarks]
    for si, st in enumerate(stages):
        for i in range(names.index(st["fromLandmark"]), names.index(st["toLandmark"])):
            stage_of[i] = si
    features, worst = [], []
    for i in range(len(landmarks) - 1):
        a, b = landmarks[i], landmarks[i + 1]
        if a["name"] not in by_name or b["name"] not in by_name:
            print(f"  [{version}] leg {i} skipped: no position for {a['name']!r} or {b['name']!r}")
            continue
        ma, _ = line.locate(by_name[a["name"]]["lat"], by_name[a["name"]]["lon"])
        mb, _ = line.locate(by_name[b["name"]]["lat"], by_name[b["name"]]["lon"])
        if mb <= ma:
            print(f"  [{version}] leg {i} skipped: {b['name']!r} is not after {a['name']!r} on this line")
            continue
        seg = substring(line.line, ma, mb)
        coords_out = []
        for x, y, *z in seg.coords:
            lon, lat = TO_WGS.transform(x, y)
            c = [round(lon, COORD_DP), round(lat, COORD_DP)]
            if z:
                c.append(round(z[0], ELE_DP))
            if not coords_out or coords_out[-1][:2] != c[:2]:
                coords_out.append(c)
        line_mi = GEOD.line_length([c[0] for c in coords_out], [c[1] for c in coords_out]) / METRES_PER_MILE
        table_mi = round(b["milesFromStart"] - a["milesFromStart"], 2)
        worst.append((abs(line_mi - table_mi), i, a["name"], b["name"], table_mi, line_mi))
        features.append({
            "type": "Feature",
            "properties": {"leg": i, "stage": stage_of.get(i), "from": a["name"], "to": b["name"],
                           "tableMiles": table_mi, "lineMiles": round(line_mi, 2)},
            "geometry": {"type": "LineString", "coordinates": coords_out},
        })
    gj = {"type": "FeatureCollection",
          "properties": {"trail": slug, "line": version, "direction": f"{landmarks[0]['name']} -> {landmarks[-1]['name']}"},
          "features": features}
    path = ASSETS_TRAILS / f"{slug}-{version}-legs.geojson"
    path.write_text(json.dumps(gj, separators=(",", ":"), ensure_ascii=False), encoding="utf-8")
    total = sum(f["properties"]["lineMiles"] for f in features)
    print(f"  [{version}] {len(features)} legs, {total:.2f} mi along the line "
          f"(table {landmarks[-1]['milesFromStart'] - landmarks[0]['milesFromStart']:.1f}), "
          f"{path.stat().st_size / 1024:.0f} KB -> {path.relative_to(REPO_ROOT)}")
    worst.sort(reverse=True)
    print(f"  [{version}] legs whose line miles differ most from the table:")
    for d, i, fa, fb, t, lmi in worst[:8]:
        print(f"      leg {i:2d} {fa} -> {fb}: table {t:.1f}, line {lmi:.2f} ({lmi - t:+.2f})")


def build(slug, cfg):
    print(f"\n== {slug} ==")
    _, data = parse_trail_file(TRAIL_DATA_DIR / f"{slug}.js")
    landmarks = [{"name": l[0], "milesFromStart": float(l[1])} for l in data["landmarks"]]
    trail_json = json.loads((ASSETS_TRAILS / f"{slug}.json").read_text(encoding="utf-8"))
    stages = trail_json["defaultStages"]
    overrides = cfg.get("overrides", {})
    unknown = [n for n in overrides if n not in {lm["name"] for lm in landmarks}]
    if unknown:
        sys.exit(f"  overrides for unknown landmark(s): {unknown}")

    ref_version = cfg.get("referenceLine", cfg["lines"][0])
    ref = load_line(slug, ref_version)
    # Orient: find the first and last landmark with a rough search, so legs run in walking order.
    probe, _ = find_positions(slug, [landmarks[0], landmarks[-1]], overrides, ref)
    if all("source" in p for p in probe):
        ref = orient(ref, landmarks, [p["source"] for p in probe])
        print(f"  {ref_version} line oriented {landmarks[0]['name']} -> {landmarks[-1]['name']}")
    positions, scale = find_positions(slug, landmarks, overrides, ref)
    flagged = check(positions, landmarks)
    print(f"  reference line '{ref_version}' is {ref.length / METRES_PER_MILE:.2f} mi; table mileages scaled by {scale:.4f} to compare")
    print_table(positions, landmarks, scale)
    print(f"  {flagged} landmark(s) flagged")
    path, coords = write_coords(slug, positions, ref, ref_version)
    print(f"  wrote {path.relative_to(REPO_ROOT)} ({len(coords)} of {len(landmarks)} landmarks)")

    for version in cfg["lines"]:
        line = ref if version == ref_version else orient(load_line(slug, version), landmarks,
                                                          [(coords[0]["lat"], coords[0]["lon"]),
                                                           (coords[-1]["lat"], coords[-1]["lon"])])
        write_legs(slug, version, line, landmarks, coords, stages)


def main():
    config = {k: v for k, v in json.loads(CONFIG_PATH.read_text(encoding="utf-8")).items()
              if not k.startswith("_")}
    for slug in sys.argv[1:] or list(config):
        if slug not in config:
            sys.exit(f"Unknown trail {slug!r}; configured: {list(config)}")
        build(slug, config[slug])


if __name__ == "__main__":
    main()
