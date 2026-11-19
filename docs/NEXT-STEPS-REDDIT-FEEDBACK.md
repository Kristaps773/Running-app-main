# Next steps from Reddit feedback (r/orienteering)

Source: closed beta tester review (map UX, IOF map, descriptions, defaults, distance wording).  
Reply posted acknowledging the points below.

## Priority plan

### P0 — Quick wins (defaults / clarity)

1. **Turn off walking path (shortest / foot path) by default** — DONE  
   Defaults in `UserPreferencesStore` / `MapDisplayPreferences` are now off. Still available in Settings.

2. **Turn off purple dashed air legs / distance labels by default** — DONE  
   Same defaults off. Still available in Settings for “Waze style” / lost runners.

3. **Clarify distance target in UI**  
   Make it obvious the slider is straight line (air) course length, and show path length as a separate result after generation. Optional copy near the slider / preview panel.

### P1 — Map interaction UX

4. **Basemap style picker instead of cycle button**  
   Top right map type control: open a list / sheet of all styles instead of cycling through many options.

5. **Two finger map rotation** — shipped  
   Interactive maps: two-finger rotate + reset-to-north control beside zoom. Compact history thumbnails stay fixed.

6. **North lines when map can rotate** — shipped  
   True-north teal meridians under the course (Settings toggle, default on).

### P2 — Content / generation quality

7. **Control descriptions**  
   Descriptions are unreliable (often wrong, e.g. always “junction”). Overpass / bulk object fetch hits API limits. Find a scalable approach (cache, thinner query, offline tags, fewer enrichments).

8. **IOF style basemap (longer term)**  
   Research OpenOrienteering / OCAD OSM→IOF style conversion. End goal: a more normal orienteering style map. Current Pseud O / Street O / IOF overprint are only approximations.

### P3 — Settings UX (optional follow up)

9. **Air / path generation knobs**  
   Already exist but are unintuitive and can increase generation failures. Later: clearer labels, safer defaults, maybe presets (“strict” / “relaxed”) instead of raw sliders only.

## Done when

- [x] New users get walking path off by default  
- [x] New users get air leg labels (and ideally air legs) off or less noisy by default  
- [ ] Distance wording explains air vs path  
- [ ] Map style is choosable from a list  
- [x] Map rotation + north lines designed and implemented  
- [ ] Control description strategy decided (even if v1 is “no description” / landmark name only)  
- [ ] IOF map research note or spike (OpenOrienteering / OCAD path)

## Reply text (for reference)

Hi, thanks for trying the app and for the detailed feedback.

- The map type button and map rotation/alignment: agree, I had not thought of that. A list picker and rotating the map itself make a lot of sense. And the north lines are definitely needed.
- The IOF style map: thanks for the OpenOrienteering and OCAD recommendations. I will look into them. The end goal is to include a more normal orienteering-style map. But so far I've struggled with it.
- Control point descriptions: yes, they are pretty buggy right now, and 99.99% don't work as intended :-( Pulling that many objects tends to get API blocked, so I am looking for the simplest solution that could scale with more requests.
- The shortest path: that was left on by default while I was debugging. In a next update, I will turn it off. But you can change it in Settings now.
- The distance target: yes, that was intentional. Orienteering course length is usually measured as straight line distance, so the slider follows that idea. But you're right, I should try to make that clearer in the UI.
  In Settings you can already tune how close the air distance must be, and how much the real walking path can differ from it. Those controls can make generation fail more often if you push them hard, but they keep the generated course closer to what you intended.
- The purple dashed line: that was meant as a test feature if someone gets lost and wants a lead toward a faster route, or simply does not want to think about the right route choice and just wants to run more like using Waze/google maps. It still seems I left related options on by default in Settings, so I will turn those defaults off too.

Thanks again for the great review. I did not expect anyone would check it so deeply. Thanks :-)
