# tools/

| Script | Makes | Used by |
|---|---|---|
| `convert_trails.py` | `assets/trails/<slug>.json`, `index.json`, `seed-progress.json` from `trail-data/*.js` | the whole app |
| `build_geo.py` | `assets/geo/<slug>.json` (10 m simplified, mileage-scaled) from Natural England open data | "Where am I" |
| `build_map_geojson.py` | `assets/trails/<slug>-{full,2m,5m}.geojson` from the official GPX | trail map view |

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

### Attribution

Route data: **National Trails / Natural England and Natural Resources Wales.**
Where the map is shown, also credit the base map: **© OpenStreetMap contributors**. That also
covers the `-osm` route lines (OpenStreetMap data, ODbL 1.0).
