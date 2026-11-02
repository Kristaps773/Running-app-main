# Orienteering map appearance — research for OrienteerRun

**App context:** Android, OSMDroid, default basemap Mapnik (or Carto dark). Course overlay already includes dashed straight “air legs” (magenta/purple), checkpoint badge markers, optional walking path (OSRM), and air-leg distance pills.

**Question:** What would it take to look like **official orienteering maps** (ISOM/ISSprOM symbology)? Can it be done **for free**?

**Short answer:** **Partially yes for free** — especially the **purple course overprint** and an **approximate** OSM-derived basemap (e.g. OpenOrienteeringMap “Pseud-O” / self-hosted ISOM-ish raster style). **No** for a globally available, competition-faithful forest map: that needs **field-mapped terrain cartography** (vegetation, runnability, form lines, cliff detail) that OpenStreetMap does not contain and that clubs protect under **copyright**.

---

## 1. What “official” means

### IOF specifications (authoritative sources)

| Spec | Use | Scale (typical) | Contours (typical) | License |
|------|-----|-----------------|--------------------|---------|
| **ISOM 2017-2** | Foot orienteering (forest) | 1:15 000 nominal; also 1:10 000, 1:7 500 enlargements | 5 m (2.5 m in very flat terrain) | IOF; CC BY-ND 4.0 |
| **ISSprOM 2019-2** | Sprint | 1:4 000 (mandatory from Jan 2025 for IOF sprint) | 2 m or 2.5 m | IOF; CC BY-ND 4.0 |
| **IOF Control Descriptions 2024** | Pictogram control description sheet | N/A (companion to map) | N/A | IOF; CC BY-ND 4.0 |

**Primary references:**

- [IOF Mapping hub](https://orienteering.sport/iof/mapping/) — ISOM, ISSprOM, MTB, ski specs; **ISOM 2030** revision is in progress (do not assume 2017-2 forever).
- [ISOM 2017-2 PDF (example mirror)](https://www.orienteeringaustria.at/wp-content/uploads/2025/01/IOF-ISOM-2017-2-Revision-6-January-2024.pdf)
- [ISSprOM 2019-2](https://www.orienteeringaustria.at/wp-content/uploads/2025/01/IOF-ISSprOM-2019-2-Revision-6-January-2024.pdf)
- [IOF Control Descriptions 2024](https://orienteering.sport/iof/rules/control-descriptions/)
- [IOF printing / colour definitions (purple overprint, CMYK)](https://orienteering.sport/wp-content/uploads/2018/06/ISOM-2017-Appendix-1-CMYK-Printing-and-Colour-Definitions.pdf)

### Basemap symbology (terrain cartography)

Official maps are **not** “styled OSM”. They are **purpose-built** cartography with fixed symbol codes, minimum dimensions, and a restricted palette:

| Colour role | Typical use (ISOM) |
|-------------|-------------------|
| **Yellow / white / green** | Open land, runnable forest, vegetation density & **runnability** (slow vs fight vs impassable) |
| **Brown** | Contours, form lines, earth banks, knolls, depressions — relief is the navigation backbone |
| **Blue** | Water (lakes, marshes, ditches, wells) |
| **Black** | Man-made: roads, paths, buildings, fences, walls, power lines |
| **Grey** | Paved areas, buildings (variants), undergrowth patterns in sprint |
| **Purple** | **Course overprint only** — must not replace terrain mapping |

Key ISOM ideas the app cannot infer from GPS alone:

- **Vegetation boundaries** and **runnable vs non-runnable** green symbols (410–414 etc.) require **field survey**.
- **Minimum dimensions** and **generalisation** rules depend on print scale (dimensions given at 1:15 000 for ISOM, 1:4 000 for ISSprOM).
- **Magnetic north** lines and declination date on the printed map.

### Course overprint (purple layer)

Symbols **701–714** (course planning). All are **purple**, printed **over** the map without obscuring black/brown/blue detail (IOF recommends true **overprint** in offset print; on screen, simulate with transparency / “lower purple” vs “upper purple”).

| Symbol | Meaning | Notes (ISOM @ 1:15 000) |
|--------|---------|-------------------------|
| **701** | Start | Equilateral **triangle**, centre = start point, points toward **first control** |
| **702** | Map issue point | Circle (marked route to start) |
| **703** | Control | **Circle**, centre on control feature; **75 m footprint** on ground (forest); gaps allowed so map detail shows |
| **704** | Control number | **Arial 4.0 mm**, north-up, near circle |
| **705** | Finish | **Double circle** (inner + outer) |
| **706+** | Course line, overprint boundary, out-of-bounds, etc. | Dashed/solid purple lines per spec |

**Scaling:** Dimensions are defined at nominal scale; **strict enlargement** for larger print scales (e.g. 1:10 000 → 150% symbol size, same 75 m control circle footprint).

**Sprint (ISSprOM):** Control circle **6 mm @ 1:4 000** → **24 m** footprint; thick black lines only for **uncrossable** features — different readability rules than forest ISOM.

**Purple colour (print):** IOF Appendix 1 — e.g. **PMS Purple**, CMYK **C35 M85 Y0 K0**; “lower” vs “upper” purple for overprint simulation.

### Control descriptions (separate from the map image)

The **IOF Control Descriptions 2024** pictogram sheet (columns A–I: number, code, feature, appearance, dimensions, location of flag, etc.) is **black on white**, not purple. It is part of the “official event package” but is a **UI/print artifact**, not a map tile layer.

---

## 2. Two layers of the problem

```
┌─────────────────────────────────────────────────────────┐
│  Layer B: Course overprint (purple)                     │
│  start triangle, control circles, numbers, leg lines,   │
│  finish, optional OOB / marked route                    │
├─────────────────────────────────────────────────────────┤
│  Layer A: Basemap (ISOM/ISSprOM terrain cartography)    │
│  vegetation, contours, rocks, paths, buildings, …       │
└─────────────────────────────────────────────────────────┘
```

### Layer B — OrienteerRun today

Implemented in `RouteMapOverlay.kt`, `CheckpointMarkerIcon.kt`, `AirLegLabelIcon.kt`, theme colours in `Color.kt`:

| Feature | Current behaviour | IOF gap |
|---------|-------------------|---------|
| Straight legs | Dashed polyline + halo (`addOrienteeringAirLegOverlays`) | Colour is in the right family; line weight/dash pattern not spec-calibrated; legs close loop to start (score/O-style) — IOF **line** courses use purple **course lines** between controls with specific width |
| Checkpoints | Filled **Material primary/tertiary circles** with “S” / number | Not hollow purple **703** circles; no **701** triangle / **705** double finish; size not tied to map scale |
| Leg numbers | Optional distance pills on midpoint | IOF **704** is **control sequence number**, not leg distance (distance belongs on description sheet or course planner tools) |
| Walking path | OSRM polyline (mint/green) | Official map shows **runnable paths from survey**, not GPS routing overlay |

**Effort:** Layer B is almost entirely **in-app drawing** — no copyright on ISOM **symbol geometry** (spec is CC BY-ND; **implementing** rendering from the spec is fine; **redistributing modified spec PDFs** is not). This is the **highest ROI** path for “feels like orienteering” on any basemap.

### Layer A — Basemap

Standard OSM **Mapnik** tiles are general-purpose street maps — wrong colours, no ISOM vegetation, no form lines, contour treatment unlike ISOM brown.

Getting Layer A “official” requires either:

1. **Real orienteering map data** (club OCAD/OOM, georeferenced PDF/GeoTIFF), or  
2. **Approximation** from OSM + DEM (contours) + custom styling (OpenOrienteeringMap, self-hosted Mapnik/Planetiler style).

---

## 3. Free / open options

### 3.1 Styling OpenStreetMap to look “orienteering-like”

| Approach | Cost | Legal | Quality vs real ISOM |
|----------|------|-------|----------------------|
| **OpenOrienteeringMap (OOMap)** — [oomap.dna-software.co.uk](https://oomap.dna-software.co.uk/), styles on [GitHub oobrien/oomap](https://github.com/oobrien/oomap) | Free to use; project asks for support for events | OSM **ODbL** — attribute © OpenStreetMap contributors; OOMap adds contours from various DEM/LIDAR sources (attribution on PDF exports) | **Street-O** good for urban informal events; **Pseud-O** mimics look but authors state OSM detail is **insufficient for proper forest O** |
| **OpenOrienteering Mapper (OOM)** — [openorienteering.org](https://www.openorienteering.org/apps/mapper/) | **Free**, open source (GPL) | You create maps; OSM template is a **background for drawing**, not finished ISOM | **Proper** map if you **survey**; not automatic |
| **Custom raster tiles** (Mapnik XML, TileServer GL, etc.) using OOMap styles or your own ISOM palette | Self-host: server/bandwidth cost only | ODbL + respect source licenses for DEM | Tunable; still **missing OSM vegetation/runnability** |
| **Vector pipeline** (Planetiler + MapLibre style) | Software free; compute/storage for tiles | ODbL + tile schema licenses (e.g. OpenMapTiles attribution) | OSMDroid is **raster-first**; switching to MapLibre Native is a **large** app change |

**Honesty:** OSM tags do not encode **fight vegetation**, **single trees**, **narrow reentrants**, **contour detail** at ISOM standards. You can paint **yellow/green/brown/blue/black** aesthetics on roads and landuse — that reads as “orienteering themed”, not as a **map you could use in an IOF forest race**.

### 3.2 Contours and relief (free DEM)

| Source | Resolution | Use |
|--------|------------|-----|
| **SRTM / Copernicus DEM / ASTER** | ~30 m (varies) | Good for **approximate** contours in OOMap and DIY tile pipelines; **not** equivalent to **LiDAR** (1–2 m) used for many competition maps |
| **National open LiDAR** (e.g. parts of UK, US, Nordics) | Sub-metre | Can improve **free** basemaps **regionally**; still need **cartographic generalisation** to ISOM brown |

OOMap v4 advertises **global satellite contours (5 m/10 m)** and **LiDAR contours where available** ([help](https://oomap.dna-software.co.uk/help/)).

### 3.3 Tile services and Android (OSMDroid)

OrienteerRun uses `XYTileSource` / `TileSourceFactory.MAPNIK` in `OsmMapHost.kt`.

- **OSMF standard tiles** (`tile.openstreetmap.org`): free data, **strict usage policy** — custom User-Agent (already set to package name), attribution, no bulk scraping; not ISOM styled. [Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/)
- **OpenOrienteeringMap XYZ** (example pattern from project docs): `https://oomap.dna-software.co.uk/global/{z}/{x}/{y}.png` — **no published SLA**; for production apps prefer **self-hosting** the open Mapnik styles from GitHub or negotiate with maintainers.
- **MapTiler / Mapbox**: free tiers exist but are **not ISOM** out of the box; commercial limits; still need custom style work for Pseud-O-like appearance.

### 3.4 User-provided club maps (offline, free *software*, not free *data*)

| Format | Tooling | In OrienteerRun |
|--------|---------|-----------------|
| **.omap / .xmap / .ocd** | **OpenOrienteering Mapper** (free); OCAD (commercial) | Not integrated — would need export to **georeferenced raster** (PNG + world file, GeoTIFF, KMZ) or implement a heavy viewer |
| **Georeferenced PDF / PNG** | OOM export, Condes, Purple Pen print | Feasible as **GroundOverlay** or tile pyramid offline |
| **GeoPackage** | OOM/GDAL export | Good long-term interchange |

**Copyright:** Possession of a file ≠ permission to redistribute inside an app. See §4.

---

## 4. Not free / licensed

### Official club competition maps

- **Copyright:** Mapper holds copyright; **club** typically controls **reproduction** of the printed map ([British Orienteering mapper guidance](https://www.britishorienteering.org.uk/images/uploaded/downloads/mappers_procurementofmaps.pdf)).
- **Federation map registers** (e.g. Ireland): **each use** may require approval; OCAD file possession does not imply ongoing permission ([OI Rule 7](https://www.orienteering.ie/wp-content/uploads/2026/01/Rule-7-Maps-Map-Register.pdf)).
- **Typical club terms:** single-event use, no publishing full map online, report print counts ([example club guidelines](https://www.graneorientering.no/retningslinjer-ved-bruk-av-kart/)).

**For OrienteerRun:** Treat user-imported maps as **private user content**; do not ship club maps in the APK; show copyright/permission reminder on import.

### Commercial tooling

- **OCAD** — subscription for full desktop workflow; OCAD App free but tied to OCAD ecosystem ([OCAD App](https://www.ocad.com/en/app/)).
- **Condes / Purple Pen** — course setting (often used with club-licensed basemap).
- **National map databases / LIDAR packages** — sometimes fee-based outside open-data regions.

### “Look and feel” / trademark

- IOF specifications are **public** (CC BY-ND); using **purple control circles** and **ISOM colours** is **not** generally trademark-blocked for a training app.
- Marketing as **“IOF official map”** when showing OSM Pseud-O **would be misleading**.
- **Redistributing** scanned club maps or OCAD files without permission is **copyright infringement**.

---

## 5. Practical roadmap for OrienteerRun (free-first, ranked)

Effort: **S** ≈ days, **M** ≈ 1–2 weeks, **L** ≈ multi-week / architectural.

| Priority | Task | Effort | Impact | Notes |
|----------|------|--------|--------|-------|
| **1** | **IOF-like course overprint** — purple `#AD0078`-class colour (already close in `LightMapAirLeg`); hollow **703** circles; **701** start triangle oriented to first control; **705** finish when last ≠ start; **704** numbers north-up; scale-aware pixel sizes from map zoom (approximate 75 m / 24 m footprint) | **M** | High | Pure Kotlin/Android overlays; optional toggle “Classic overprint” vs current badges |
| **2** | **Separate concerns** — keep OSRM walking path visually distinct (not confused with ISOM black paths); user setting for “planning legs only” vs “show routed path” | **S** | Medium | Already partially in settings |
| **3** | **Optional basemap: OOMap Pseud-O / Street-O** as alternate `XYTileSource` + OSM attribution string | **S–M** | Medium | Cache tiles; self-host for reliability; respect OOMap/OSM attribution |
| **4** | **Control description sheet** (IOF 2024 pictograms) for generated checkpoints — static symbol font or SVG set | **M–L** | Medium for “official feel” | Needs feature taxonomy beyond lat/lon (or simplified generic descriptions) |
| **5** | **Custom ISOM-ish OSM style** (fork OOMap Mapnik or MapLibre style + Planetiler for region) | **L** | Medium basemap | Ongoing maintenance; regional extracts |
| **6** | **Import georeferenced user map** (PDF/PNG+PGW, GeoTIFF, KMZ from OOM) as offline basemap | **L** | **High** where club maps exist | Best path to **real** ISOM look legally if user has rights |
| **7** | **MapLibre Native** vector tiles | **XL** | Long-term flexibility | Only if raster limits bite (rotation, scale-dependent ISOM symbols on basemap) |

### Technical notes for overlay work (Priority 1)

- Convert **ground metres → screen pixels** using OSMDroid projection at current zoom (control circle diameter = footprint / metresPerPixel).
- Draw **leg lines** behind circles; break lines at circle boundary like IOF examples.
- **Start triangle**: equilateral, apex toward next control bearing.
- **Overprint simulation**: semi-transparent purple stroke; avoid opaque filled Material buttons on map for “classic” mode.

### What the app should *not* promise

- Global, competition-grade **forest ISOM** basemap from OSM alone.
- Automatic **control descriptions** that match real map features without feature data from a real map or rich OSM tagging.

---

## 6. Verdict

| Goal | Free? | Realistic? |
|------|-------|------------|
| **Purple course overprint** (start, controls, legs, finish) aligned with IOF dimensions/colours | **Yes** (engineering only) | **Yes** — best first step for OrienteerRun |
| **Control description sheet (IOF pictograms)** | **Yes** (spec CC BY-ND; implement UI) | **Partial** — needs feature metadata |
| **Urban “Street-O” feel** (OOMap) | **Yes** (OSM + OOMap tiles/styles) | **Yes** in well-mapped cities |
| **Forest map indistinguishable from club ISOM** | **No** for worldwide automatic data | **No** without **licensed/surveyed** map data |
| **User’s own club map offline** | **Software free**; **map data** subject to club permission | **Yes** — import pipeline + user responsibility |

**Bottom line:** OrienteerRun can get **much closer to official event graphics** for **free** by fixing **Layer B** (course overprint + optional descriptions). **Layer A** can be **approximated for free** with OpenOrienteeringMap or self-hosted styles, but **true official terrain cartography** is **not globally available for free** — it is **locally produced, copyrighted, and survey-based**.

---

## References (quick links)

- IOF Mapping: https://orienteering.sport/iof/mapping/
- IOF Control Descriptions: https://orienteering.sport/iof/rules/control-descriptions/
- OpenOrienteering Mapper: https://www.openorienteering.org/apps/mapper/
- OpenOrienteeringMap / OOMap: https://oomap.dna-software.co.uk/ · https://github.com/oobrien/oomap
- OSM Wiki — OpenOrienteeringMap: https://wiki.openstreetmap.org/wiki/OpenOrienteeringMap
- OSM Tile Usage Policy: https://operations.osmfoundation.org/policies/tiles/
- Planetiler (self-hosted vector tiles): https://github.com/onthegomap/planetiler

*Document generated for OrienteerRun product research — October 2025.*
