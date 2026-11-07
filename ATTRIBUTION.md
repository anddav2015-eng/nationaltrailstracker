# Attribution

## Route lines ("Where am I")

Where a trail has a map, "Where am I" uses the map's OpenStreetMap line (see "Route lines (trail
map)" below), with mileage spread between landmarks by table mileage. The Natural England source
below is only a fallback for trails without a map, and no lines have been built from it yet.

### England – Natural England, National Trails (England)

The route lines in `app/src/main/assets/geo/` for trails in England are derived from the
**National Trails (England)** dataset published by Natural England, simplified and rescaled to the
trail mileages by `tools/build_geo.py`.

- Source: <https://www.data.gov.uk/dataset/ac8c851c-99a0-4488-8973-6c8863529c45/national-trails-england3>
- Licence: [Open Government Licence v3.0](https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/)
- Attribution: © Natural England copyright. Contains Ordnance Survey data © Crown copyright and
  database right 2026.

Trails using this source so far: Thames Path.

### Wales and Scotland

Not yet sourced. Each will be listed here with its licence before its route line is added.

## Route lines (trail map)

The map shows `app/src/main/assets/trails/<slug>-legs.geojson`, cut by `tools/build_legs.py` from
an OpenStreetMap route line. The official line (`trail-data/map-lines/<slug>-full.geojson`, not
shipped) is built by `tools/build_map_geojson.py` from the official GPX downloads on
<https://www.nationaltrail.co.uk>. It's used to check the OSM line, fill its gaps and give it
elevation.

- Attribution: National Trails / Natural England and Natural Resources Wales.
- Base map (where shown): © OpenStreetMap contributors.
- Licence of the GPX downloads: not yet confirmed.

The OSM line (`trail-data/map-lines/<slug>-osm.geojson`) is built by the same script from the
trail's OpenStreetMap route relation, fetched from the OSM API.

- Licence: [Open Database Licence 1.0](https://opendatacommons.org/licenses/odbl/1-0/)
- Attribution: © OpenStreetMap contributors.

Trails using these sources so far: Thames Path (OSM relation 14519665), North Downs Way via Canterbury
(OSM relation 1957071 and its child relations), South Downs Way (OSM relation 77976), Cleveland Way
(OSM superroute 4087652 and its three section relations) Coast to Coast (OSM superroute 12145 and
its three section relations) and Cotswold Way (OSM relation 65239), each with its official GPX.

In the app, these credits are shown in each mapped trail's ⓘ dialog under "Map sources". The
base-map credit is also on the map, behind MapLibre's ⓘ button.

## Landmark coordinates

Landmark positions (`lat`/`lon` in the trail JSON, and the leg ends in `*-legs.geojson`) were found
with [Nominatim](https://nominatim.org) by `tools/build_legs.py`, then snapped onto the route line.
Geocoding data © OpenStreetMap contributors, ODbL 1.0.

## Trail mileages

Landmark mileages and stage cuts come from the owner's HTML trackers (England and Wales) and from
published section distances (Scotland). Each trail's sources are given in its `footer` text, shown
in the app behind the ⓘ button on the trail screen.
