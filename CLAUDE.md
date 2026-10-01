# National Trail Tracker – Project Brief

An Android app for walkers doing the National Trails of England & Wales in day-long stages.
It shows each trail broken into stages of ~10–15 miles, lets the walker log completed stages
(date, steps, notes), shows progress across all trails, and can use GPS to answer
"where am I on this trail and how far to the end of today's stage?"

The owner (Andrew) has already walked the Thames Path this way and has built HTML trackers for
16 trails. Those trackers are the source data and the reference design for this app.

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

The folder `trail-data/` holds one JS file per trail, copied from the HTML tracker project.
Each file has this shape:

```js
window.TRAILS["north-downs-way"] = {
  name, route, startLabel, endLabel, colour, subtitle, footer,
  landmarks: [ ["Farnham (start, A31 / Farnham station)", 0.0], ["Guildford (River Wey)", 11.0, "station"], ... ],
  stages:    [ { from: "...", to: "...", note: "...", date: "17 Jun 2026", steps: 41131 }, ... ],
};
```

- Write a one-off converter (Kotlin script or Python in `tools/`) that turns these into
  `app/src/main/assets/trails/<slug>.json`, plus an `index.json` giving the display order.
  The order is alphabetical, matching the HTML index; the King Charles III England Coast Path is a
  placeholder entry with no stages.
- `date` and `steps` in the source are **the owner's existing progress**. Don't bake them into the
  trail JSON. Instead, emit `seed-progress.json`, which is imported into Room on first launch
  (Thames Path: 11 of 12 stages logged).
- Landmark positions between official section ends are **estimates (±0.5 mile)**. Keep each trail's
  `footer` text, and show it on the trail screen.

### GPS geometry (needed for milestone 3)

The landmarks have mileages but **no coordinates yet**. For "where am I":

1. Get each trail's official route line as GPX. National Trails publishes GPX downloads per trail,
   and Natural England publishes National Trails open data. **Check the licence** (likely the Open
   Government Licence) and record the attribution in `ATTRIBUTION.md`. Don't scrape; if a
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
   "N of 17 complete · X miles walked".
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
- No analytics, ads, accounts or network calls in v1. The app works fully offline.
- Only ever request location while the app is in use, and only when the user taps "Where am I".
- Don't change the trail data values by hand. Fix them in `trail-data/` and re-run the converter.
- When unsure about a product decision, ask. Don't guess.
