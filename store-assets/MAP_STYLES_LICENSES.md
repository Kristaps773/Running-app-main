# Map styles — licenses & attribution (OrienteerRun)

All basemaps use **OpenStreetMap** data under [ODbL 1.0](https://www.openstreetmap.org/copyright). The app sets OSMDroid `userAgentValue` to the app package name. Respect each provider’s tile usage / fair-use policy; no bulk scraping.

| In-app style | Tile source | Commercial / Play use | Attribution (shown in app) |
|--------------|-------------|------------------------|----------------------------|
| **Standard OSM** | OSM Mapnik (`TileSourceFactory.MAPNIK` / `tile.openstreetmap.org`) | Yes, with [OSMF tile policy](https://operations.osmfoundation.org/policies/tiles/) | © OpenStreetMap contributors |
| **Dark** | OSM Mapnik tiles with OSMDroid `INVERT_COLORS` (CARTO `dark_all` now needs an API key) | Yes, with [OSMF tile policy](https://operations.osmfoundation.org/policies/tiles/) | © OpenStreetMap contributors (inverted) |
| **Pseud-O** | Mapnik base + [OpenOrienteeringMap](https://oomap.dna-software.co.uk/) `LIDAR-5/{z}/{x}/{y}.png` contour overlay | OSM: ODbL. OOMap contours: OSM-derived styling; attribute OOMap + OSM. **Not** a surveyed ISOM forest map. | © OpenStreetMap contributors · Contours © OpenOrienteeringMap · Pseud-O approximation |
| **Street-O** | OSM Mapnik (urban / sprint practice look) | Yes, with OSMF tile policy. **Not** a surveyed ISSprOM map. | © OpenStreetMap contributors · Street-O approximation |
| **OpenTopoMap** | `*.tile.opentopomap.org` | Style [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/); elevation SRTM; OSM ODbL. OK for apps with attribution; do not misrepresent as official orienteering map. | © OpenStreetMap contributors, SRTM · © OpenTopoMap (CC BY-SA 3.0) |
| **CyclOSM** | `tile-cyclosm.openstreetmap.fr` | OSM ODbL; project [CyclOSM](https://www.cyclosm.org/) — community tile service, fair use with attribution and identifiable User-Agent. | © OpenStreetMap contributors © CyclOSM |

## Not included (and why)

| Source | Reason |
|--------|--------|
| OOMap Pseud-O / Street-O full raster XYZ | No reliable global slippy endpoint on oomap.dna-software.co.uk v4 (contour layer only is public as of 2026). In-app Pseud-O/Street-O are Mapnik-based approximations + classic IOF purple overprint. Self-host [oobrien/oomap](https://github.com/oobrien/oomap) styles for true Pseud-O rasters. |
| Mapbox / MapTiler / Google | API keys, paid tiers, or terms unsuitable for default free app basemap. |
| Thunderforest Outdoors | API key required. |
| Club OCAD / OpenOrienteering Mapper maps | Copyrighted surveyed maps; not redistributed in the APK. Future: optional user import of georeferenced exports. |

## Settings path

**Settings → Map appearance → Map style** (chip row). Classic IOF course overprint and “How to read an O-map” legend live in the same section.

## Privacy

Tile requests send zoom/x/y (and thus approximate map location) to the selected provider. See app privacy policy.
