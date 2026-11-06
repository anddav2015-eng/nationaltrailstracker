# tools/

| Script | Makes | Used by |
|---|---|---|
| `convert_trails.py` | `assets/trails/<slug>.json`, `index.json`, `seed-progress.json` from `trail-data/*.js` | the whole app |
| `build_geo.py` | `assets/geo/<slug>.json` (10 m simplified, mileage-scaled) from Natural England open data | "Where am I" |
| `build_map_geojson.py` | `assets/trails/<slug>-{full,2m,5m,osm}.geojson` from the official GPX (and OSM) | trail map view |
| `build_legs.py` | `trail-data/landmark-coords/<slug>.json` and `assets/trails/<slug>-<line>-legs.geojson` | trail map view; landmark lat/lon |

Order for map data: `build_map_geojson.py` → `build_legs.py` → `convert_trails.py`.

`convert_trails.py` and `build_geo.py` use only the standard library. `build_map_geojson.py`
needs a few packages, so set up a virtual environment once:

```sh
python -m venv tools/.venv
tools/.venv/Scripts/python -m pip install -r tools/requirements.txt   # Windows
# tools/.venv/bin/python -m pip install -r tools/requirements.txt     # macOS / Linux
```

## build_map_geojson.py: map route lines

```sh
tools/.venv/Scripts/python tools/build_map_geojson.py              # every configured trail
tools/.venv/Scripts/python tools/build_map_geojson.py thames-path  # just one
```

For each trail it:

1. Downloads the official GPX to `trail-data/gpx-source/` (skipped if it's already there; the
   folder isn't committed).
2. Lists every track and segment in the file, with point count, length, start and end.
3. Joins the tracks named in `mainTracks` into one line, in file order. Gaps between segments are
   reported but not bridged. Long straight steps (over 400 m) and sharp reversals (over 150°) are
   listed so they can be checked by eye. Nothing is repaired automatically.
4. Writes three GeoJSON files (one `LineString` feature each, with `[lon, lat, elevation]`):
   - `<slug>-full.geojson`: every GPX point
   - `<slug>-2m.geojson`: Douglas-Peucker with a 2 m tolerance
   - `<slug>-5m.geojson`: Douglas-Peucker with a 5 m tolerance

   Simplification is done in British National Grid (EPSG:27700), so the tolerance is in true
   metres. Lon/lat are rounded to 6 decimal places (about 0.1 m) and elevation to 0.1 m. Lengths in
   the report are geodesic (WGS84 ellipsoid).
5. Prints a table of vertex count, length (and the difference from `officialMiles`) and file size.
6. If the trail has an `osmRelation`, writes `<slug>-osm.geojson` too: the route as mapped in
   OpenStreetMap, so it sits exactly on the paths drawn by the OSM-based base map.
   - The relation is fetched once from the OSM API into `trail-data/osm-source/` (delete the file to
     refresh it).
   - The line follows the relation's ways from the node nearest the GPX start to the node nearest
     the GPX end, so it covers the same span as the official line.
   - Where the OSM ways don't connect, the gap is filled with the official GPX line. It's reported,
     and listed in the file's `gpxFilledGaps` property.
   - OSM has no elevation, so each vertex borrows it from the nearest point on the GPX line
     (approximate).
   - Every stretch more than 20 m from the official line is listed, so diversions and differences
     between the two can be checked.

### Adding a trail

1. Open the trail's page at `https://www.nationaltrail.co.uk/en_GB/trails/<trail-name>/trail-information/`
   and copy the GPX download link.
2. Add an entry to `tools/map_geojson_config.json` with `gpxUrl` and `officialMiles`. Set
   `mainTracks` to `[]` at first, run the script, and read the track list it prints.
3. Put the main-route track name(s) in `mainTracks` in walking order, then run it again. Check the
   reported gaps, long steps and reversals.
4. Optionally, find the trail's OSM route relation by searching
   <https://hiking.waymarkedtrails.org>, then add it as `osmRelation`. If the trail is a
   "superroute", use its main child route.

The Scottish trails aren't on nationaltrail.co.uk and need a different source.

## build_legs.py: landmark coordinates and legs

```sh
tools/.venv/Scripts/python tools/build_legs.py      # then: python tools/convert_trails.py
```

Settings are in `tools/landmark_config.json`.

1. **Positions.** Each landmark is looked up with Nominatim (OpenStreetMap's geocoder), with any
   bracketed note stripped. Results are cached in `trail-data/geocode-cache/` (not committed) and
   requests are limited to 1 per second. Of up to 10 matches, it keeps the one nearest the line at
   about the right mileage. Matches over 1.5 km from the line are rejected.
2. **Overrides,** per landmark:
   - `{"query": "Henley Bridge"}`: search for something more exact than the town.
   - `{"interpolate": true, "why": "..."}`: place it between its neighbours by table mileage (for
     lakes, villages away from the river, or things OSM doesn't name).
   - `{"point": [lat, lon], "why": "..."}`: a hand-set point on the path.
3. **Checks.** It prints a table of every landmark: table miles, line miles, `step` (how far it's
   out of step with its neighbours, in miles), metres off the line, how it was found, and flags.
   Fix flagged landmarks with an override; never by editing the generated files.
4. **Output.**
   - `trail-data/landmark-coords/<slug>.json`: each landmark's point on the `referenceLine`, merged
     into the trail JSON's `lat`/`lon` by `convert_trails.py`.
   - `assets/trails/<slug>-<line>-legs.geojson` for each line in `lines`: one LineString per leg
     (landmark *i* → *i*+1) in walking order, with `leg`, `stage`, `from`, `to`, `tableMiles` and
     `lineMiles`. A stage is the run of legs with that `stage` index. Table mileages are never
     changed.

### Attribution

Route data: **National Trails / Natural England and Natural Resources Wales.**
Where the map is shown, also credit the base map: **© OpenStreetMap contributors**. That also
covers the `-osm` route lines (OpenStreetMap data, ODbL 1.0).
