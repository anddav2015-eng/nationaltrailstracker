# National Trail Tracker – Project Brief

An Android app for walkers doing the UK's 21 National Trails (14 in England, 3 in Wales and 4 in
Scotland, as listed on nationaltrails.uk) in day-long stages.
It shows each trail broken into stages of ~10–15 miles, lets the walker log completed stages
(date, steps, notes), shows progress across all trails, and can use GPS to answer
"where am I on this trail and how far to the end of today's stage?"

The owner (Andrew) has already walked the Thames Path this way and has built HTML trackers for
16 trails. Those trackers are the source data and the reference design for this app. The four
Scottish trails (West Highland Way, Great Glen Way, Speyside Way, Southern Upland Way) have no HTML
tracker; their `trail-data/` files were written from published section distances.

## Tech stack (use these unless there is a strong reason not to; ask first)

- Kotlin, Jetpack Compose, Material 3, single-activity with Navigation Compose
- MVVM: `ViewModel` + `StateFlow`, UI state as immutable data classes
- Room for user data (progress); bundled JSON in `assets/` for trail data (read-only)
- kotlinx.serialization for JSON
- Google Play Services Fused Location Provider for one-shot location
- Charts: Vico (Compose-native). No WebView charts.
- DI: keep it simple, with manual constructor injection via an `AppContainer`. No Hilt in v1.
- minSdk 26, targetSdk = latest stable. Gradle Kotlin DSL with a version catalog.
- Units: miles by default, with a km toggle in settings. Dates shown as `d MMM yyyy` (UK).

## Domain model

Trail data is **static and bundled**. User progress is **stored in Room**. Keep them separate.

```
Trail        { id (slug), name, route, startLabel, endLabel, colour, subtitle, footer,
               landmarks: List<Landmark>, defaultStages: List<StageDef>, gpxAsset: String? }
Landmark     { name, milesFromStart: Double, note: String?, lat: Double?, lon: Double? }
StageDef     { fromLandmark: String, toLandmark: String, note: String? }   // by landmark name
```

Room entities:

```
StageLog     { id, trailId, stageIndex, dateWalked: LocalDate, steps: Int?, notes: String?,
               actualMiles: Double? }        // actualMiles = optional GPS/watch reading
CustomStages { trailId, stagesJson }         // v2: user re-cuts stages; falls back to defaultStages
```

Derived values (all computed, never stored):

- stage miles = `to.milesFromStart - from.milesFromStart`
- cumulative miles and steps, % complete, and miles to the end
- a trail is complete when every stage has a `StageLog`

## Source data

The folder `trail-data/` holds one JS file per trail. The England and Wales files were copied from
the HTML tracker project; the Scottish ones follow the same format. Each file has this shape:

```js
window.TRAILS["north-downs-way"] = {
  name, route, startLabel, endLabel, colour, subtitle, footer,
  landmarks: [ ["Farnham (start, A31 / Farnham station)", 0.0], ["Guildford (River Wey)", 11.0, "station"], ... ],
  stages:    [ { from: "...", to: "...", note: "...", date: "17 Jun 2026", steps: 41131 }, ... ],
};
```

- Write a one-off converter (Kotlin script or Python in `tools/`) that turns these into
  `app/src/main/assets/trails/<slug>.json`, plus an `index.json` giving the display order.
  The order is one alphabetical list of all 21 trails (England, Wales and Scotland mixed, not
  grouped by country); the King Charles III England Coast Path is a placeholder entry with no stages.
- `date` and `steps` in the source are **the owner's existing progress**. Don't bake them into the
  trail JSON. Instead, emit `seed-progress.json`, which is imported into Room on first launch
  (Thames Path: 11 of 12 stages logged).
- Landmark positions between official section ends are **estimates (±0.5 mile)**. Keep each trail's
  `footer` text, and show it on the trail screen.
- For the Great Glen Way and Southern Upland Way, the section ends themselves are estimates (±1 mile),
  scaled to fit the official totals. The owner hasn't yet reviewed the Scottish stage cuts.

### GPS geometry (needed for milestone 3)

The landmarks have mileages but **no coordinates yet**. For "where am I":

1. Get each trail's official route line as GPX. National Trails publishes GPX downloads per trail,
   and Natural England publishes National Trails open data. **Check the licence** (likely the Open
   Government Licence) and record the attribution in `ATTRIBUTION.md`. The Scottish trails aren't
   covered by those sources: their route lines come from each trail's own website or Scotland's
   Great Trails, and their licences must be checked separately. Don't scrape; if a
   download needs a manual step, stop and tell the owner which files to fetch.
2. Simplify each polyline (Douglas–Peucker, ~10 m tolerance) and store it in `assets/geo/<slug>.json`
   as `[[lat, lon, cumulativeMiles], ...]`.
3. Scale the GPX cumulative distance so the end of the line matches the trail's final landmark mileage.
   The table mileages stay the source of truth for stages.
4. Put this in a pure-Kotlin `RouteLocator` with unit tests. Given a location, snap it to the nearest
   segment, and return the miles along the route and the distance off-route (metres).

## Screens (v1)

1. **Trails list**: styled like the owner's "Complete List" card (dark green `#1b3a2a` background,
   cream `#f4f0e8` card, serif headings). Show a checkbox per trail, then stages done out of total,
   miles walked, and a thin progress bar. Completed trails get a highlighted row. The footer reads
   "N of 21 complete · X miles walked".
2. **Trail detail**: a header in the trail colour, then a stage summary list (stage n, from → to,
   miles, cumulative, date, steps, note). Each stage has its own colour chip, as in the HTML version.
   Tapping a stage opens the log sheet. A tab or expandable section shows the full landmark table
   (miles from start, leg, miles to end, %).
3. **Log stage**: a bottom sheet with a date picker (default today), steps, notes and an optional
   actual distance. You can edit or delete a log.
4. **Progress chart**: cumulative miles against date for one trail, with a dashed line at the full
   route length. It's hidden until there is a log.
5. **Where am I** (milestone 3): a one-shot GPS fix. It shows the current mileage, the nearest
   landmarks behind and ahead, which stage you're in, the miles to the end of the stage, and a
   warning if you're more than 500 m off-route. No background location and no map in v1.
6. **Settings**: miles/km, export and import of progress as JSON (backup), and an about screen with
   attribution.

## Milestones (do them in order; stop for review at the end of each)

1. **Skeleton + data**: project setup, converter, bundled JSON, Room with seed import, trails list
   and trail detail (read-only). Unit tests for the derived values.
2. **Logging + chart**: the log/edit/delete sheet, progress on the list and detail screens, the
   chart, and export/import.
3. **Where am I**: GPX pipeline, `RouteLocator` with tests, and the location permission flow
   (foreground only, with a clear explanation), then the result screen.
4. **Polish**: dark theme, accessibility (content descriptions, 48dp targets, font scaling),
   empty and error states, and an app icon.

## Later ideas (not v1)

- Re-cutting a trail's stages yourself (the owner re-balanced several trails for hilly terrain)
- Estimated walking time per stage from climb (Naismith's rule), which needs elevation data
- A map view with the route line (osmdroid or MapLibre; no Google Maps API key in v1)
- Step import from Health Connect
- Public transport and taxi notes at stage ends, already present as landmark `note`s
- The King Charles III England Coast Path, region by region

## Working rules

- Work in small, reviewable steps. At the start of each milestone, summarise the plan in a few
  bullets and wait for a go-ahead.
- Run `./gradlew test lint` before saying a step is done. Keep the build warning-free.
- Keep distance and mileage maths in plain Kotlin (no Android types) so it can be unit tested.
- No analytics, ads or accounts. The only network use is fetching base-map tiles (OpenFreeMap) on
  the map screen. Everything else works fully offline, and the map still shows the trail line
  without a connection.
- Only ever request location while the app is in use, and only when the user taps "Where am I" or
  "Show my location" on the map. On the map, location updates are live but stop when the walker
  leaves the screen or the app goes to the background. No background location.
- Don't change the trail data values by hand. Fix them in `trail-data/` and re-run the converter.
- When unsure about a product decision, ask. Don't guess.



## Feature: Trail map view (done for the Thames Path)

### Goal
Tapping a leg of a trail opens a map showing the trail line, the selected leg highlighted, and my live position. Personal app for one user (me), not for publication.

### Decisions already made
- **Map library:** MapLibre Native for Android. Avoid Google Maps (needs a billing account).
- **Base map tiles:** OpenFreeMap (`https://tiles.openfreemap.org/styles/liberty`), no API key. The service is provided as-is, so keep the style URL in one config place so it's easy to swap. Don't use tile.openstreetmap.org (usage policy).
- **Trail data source:** the official GPX file for each trail from nationaltrail.co.uk (found on each trail's "Trail information & map" page), plus an OpenStreetMap version of the line built from the trail's OSM route relation.
    - **The map uses the OSM line, as it is** (chosen after comparing on the phone): it sits on the paths drawn on the base map and matches what's on the ground better. Where OSM's routing differs from the official GPX, OSM wins; the build script still reports those stretches. The official GPX stays as the reference for checks, gap fills and elevation.
    - Don't reshape the line to match the route I personally walked (banks, diversions). Report differences instead.
    - Known example: https://nationaltrails.s3.eu-west-2.amazonaws.com/uploads/Thames-Path-elevation-1Nov24.gpx
    - The site warns the full GPX can contain multiple tracks. Always inspect and merge only the main route, and report anything odd (gaps, diversions, link routes).
    - Scottish trails are not on this site, so they need a separate source later.
- **Close-up accuracy matters.** I want to zoom right in and see exactly where the trail goes. Default to full-detail geometry. Only simplify if performance demands it, and then use a small tolerance (2 m or less preferred, 5 m at most). Do metre-based simplification in a projected CRS (EPSG:27700), not in degrees. Round coordinates to 6 decimal places. I choose the final version after comparing on my phone.
- **Attribution:** "National Trails / Natural England and Natural Resources Wales" and "© OpenStreetMap contributors" go in each mapped trail's ⓘ dialog ("Map sources"). On the map itself, the base-map credit is behind MapLibre's ⓘ button; there's no separate credit line under the map.
- **Shipped map data:** only `assets/trails/<slug>-legs.geojson` (the OSM line cut into legs). Source lines live in `trail-data/map-lines/`. The app builds for arm64 only, to keep MapLibre's native library small.
- **Where am I** uses the same OSM legs line when a trail has a map. Its mileages stay in my table miles: within each leg, mileage is spread in proportion to distance along the line, so each landmark sits at its table mileage. Older `assets/geo` lines from `tools/build_geo.py` are only a fallback.
- **"Miles along" panel:** distances are measured along the map (OSM) line, not scaled to my table. All tables, totals and progress keep my table mileages.
- **Offline maps:** a "Save map for offline use" button saves the base map around the stage (MapLibre offline region, up to zoom 14). Saved maps never expire, so Settings (the ⋮ menu on the trails list) lists them with sizes, with Remove and Remove all.
- **Opening the map:** tap any landmark row in the Landmarks table. The map opens with that landmark's whole stage highlighted. (Tapping a stage on the Stages tab still opens the log sheet.)
- **From Where am I:** a "Show on map" button opens the map on the stage you're in, with live location already on (you've just asked for it).
- **Live location:** off when the map opens. A "Show my location" button asks for foreground permission if needed, then shows a live dot until the walker leaves the screen.

### Data notes
- Trails are processed with a re-runnable script (not one-off commands) so other trails can use the same pipeline. Outputs go in `assets/trails/`.
- Thames Path: the official length is 185.2 miles, but my existing leg distances come from walkthethames.co.uk (183.1 miles total). Distances measured along the GPX line will differ slightly. Don't silently overwrite my existing leg mileages. Ask before changing any displayed distance.
- Landmark names in the Thames Path data may include notes in brackets, e.g. "Putney Bridge (cross to north)". Strip the bracketed text when geocoding or matching.

### Planned sequence
1. Data prep: GPX to clean GeoJSON, Thames Path only, three simplification levels for comparison.
2. Landmark coordinates, then slice the trail line into legs between landmarks.
3. Map screen: MapLibre view, trail layer, highlighted leg, live location marker.
4. Polish: attribution line, optional offline tiles, "X miles along this leg" display.
   Do one step at a time and stop when each is done. Don't build UI before the data is checked.

### Status
- [x] Step 1 data prep
- [x] Step 2 landmarks and legs
  - [x] Step 3 map screen
- [x] Step 4 polish
'
### Latest status 6th NOv 26
We have coded maps, location and plugged in the THames Path and North Downs Way. I will now try this
out on the trail over the next couple of weeks, and identify and new features. 


## Trail map data progress

Each trail goes through `build_map_geojson.py` → `build_legs.py` → `convert_trails.py` (see
`tools/README.md`), then is committed after the owner reviews it. Claude keeps this list up to date.

**Done** (map line, landmark coordinates and legs):
- [x] Thames Path
- [x] North Downs Way (via Canterbury)
- [x] South Downs Way: OSM follows the official walking alternative near Washington
- [x] Cleveland Way: "Filey" renamed "Filey Country Park"; Scarborough stage end at Scalby Mills
- [x] Coast to Coast: "Gunnerside" renamed "Gunnerside Gill (Blakethwaite)"; Osmotherley pinned at
      the Cleveland Way junction; the A19 detour before Ingleby Cross isn't in the table's miles
- [x] Cotswold Way: the GPX is a reversed `<rte>`; OSM
      includes the Stinchcombe Hill loop
- [x] Glyndŵr's Way: Castell y Blaidd moved to 20 mi (it comes before Llanbadarn Fynydd)
- [x] Hadrian's Wall Path: the GPX is a reversed `<rte>` with no elevation
- [x] Offa's Dyke Path: White Castle moved to 28.5 mi (it comes after Llantilio Crossenny);
      Brompton Crossroads and Chirk Mill pinned to the A489 and River Ceiriog crossings;
      Hatterrall Ridge (Longtown path) is still placed by table mileage

**To do** (England and Wales: official GPX from nationaltrail.co.uk):
- [ ] Peddars Way and Norfolk Coast Path
- [ ] Pembrokeshire Coast Path
- [ ] Pennine Bridleway
- [ ] Pennine Way
- [ ] The Ridgeway
- [ ] South West Coast Path
- [ ] Yorkshire Wolds Way

**To do** (Scotland: not on nationaltrail.co.uk, so the source and licence need checking first):
- [ ] Great Glen Way
- [ ] Southern Upland Way
- [ ] Speyside Way
- [ ] West Highland Way

**Not planned:** King Charles III England Coast Path (a placeholder with no stages).

**Open table questions** (line and table mileages differ; the table is unchanged until the owner decides):
- South Downs Way: Housedean Farm and Devil's Dyke → Pyecombe
- Coast to Coast: Blakey Ridge (Lion Inn) is about 3 mi too far along
- Cotswold Way: Painswick is about 1.2 mi too far along
- Glyndŵr's Way: Dolanog is about 2.7 mi too early (Dolanog → Meifod is 7.1 mi on the line, 10.0 in the table)
- Hadrian's Wall Path: Birdoswald → Carlisle is 18.0 mi on the line, 16.0 in the table
- Offa's Dyke Path: Hay Bluff and Hay-on-Wye (Hatterrall Ridge → Hay is 8.9 mi on the line, 11.8 in the table)
