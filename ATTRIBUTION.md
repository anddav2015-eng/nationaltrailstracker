# Attribution

## Route lines ("Where am I")

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

The `*-full/-2m/-5m.geojson` files in `app/src/main/assets/trails/` are built by
`tools/build_map_geojson.py` from the official GPX downloads on <https://www.nationaltrail.co.uk>.

- Attribution: National Trails / Natural England and Natural Resources Wales.
- Base map (where shown): © OpenStreetMap contributors.
- Licence of the GPX downloads: not yet confirmed.

The `*-osm.geojson` files are built by the same script from OpenStreetMap route relations,
fetched from the OSM API. A few short gaps are filled from the official GPX.

- Licence: [Open Database Licence 1.0](https://opendatacommons.org/licenses/odbl/1-0/)
- Attribution: © OpenStreetMap contributors.

Trails using these sources so far: Thames Path (official GPX, and OSM relation 14519665).

## Trail mileages

Landmark mileages and stage cuts come from the owner's HTML trackers (England and Wales) and from
published section distances (Scotland). Each trail's sources are given in its `footer` text, shown
in the app behind the ⓘ button on the trail screen.
