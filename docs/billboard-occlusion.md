# Billboard Occlusion

This note documents the world-geometry occlusion path used by the billboard renderer.

## Constraint

RuneLite's public plugin API does not expose the OSRS software depth buffer or the GPU plugin depth texture. Hub plugins can observe scene render callbacks, object hulls/clickboxes, models, camera state, and projection helpers, but they cannot read the final z-buffer directly.

Because of that, billboard occlusion is implemented as an approximate software mask rather than a true per-pixel scene depth buffer.

## Pipeline

1. Before compositing billboards, direct scene tiles are collected along a small camera-to-billboard corridor for each active billboard target. Direct candidates start at the player's current plane and continue through plane 3; normal lower planes are never scanned. Each selected tile also contributes its linked `Tile.getBridge()` chain, which is the sole lower-plane exception.
2. Only direct current-plane tiles contribute uneven-terrain triangles from `SceneTilePaint` or `SceneTileModel`; perfectly level floor triangles are ignored so they cannot falsely occlude billboards standing on the same floor. Upper-plane terrain and terrain from bridge-linked tiles are intentionally excluded from this targeted pass.
3. Scenery on those scene tiles contributes projected model-face triangles only when it passes the scenery filter:
   - `WallObject` is always eligible.
   - `GameObject` is eligible only when its renderable has meaningful vertical height, which keeps low/loose objects out while allowing walls, arches, trees, and similar static scenery.
   - `DecorativeObject` and `GroundObject` are intentionally excluded.
4. Upper-plane scenery is classified using public roof metadata: extended tile settings, bridge and visible-below flags, and scene roof IDs. Non-roof and visible-below candidates continue through the normal depth path. Roof-grouped candidates need prior-frame render evidence under the software renderer; they are rejected under GPU rendering because a plugin cannot read the GPU renderer's authoritative per-frame hidden-roof set. Unknown metadata is rejected conservatively.
5. Static renderables that are already a Model are used directly; other renderables resolve their model through getModel(). Selected scenery contributes every available visible model face at every enabled quality. Face color-3 sentinel -2 and unsigned face transparency 255 are excluded; -1 flat shading and transparency 0 remain visible. Convex-hull/clickbox fallback shapes are used only when model geometry is unavailable, never to replace deliberately hidden or out-of-region faces. Bridge-linked scenery is accepted only when RuneLite rendered one of its parts in the preceding frame.
6. Draw-callback objects are not used for occlusion; they are deliberately ignored so arbitrary objects do not affect billboard visibility.
7. Model vertices use the object anchor height plus their signed model Y, matching RuneLite's downward-positive axis; broad-phase bounds and fallback depths use the same convention. Shared vertices are projected once per part during each collection pass. Triangle-backed shapes receive interpolated camera-forward depth from their model or terrain vertices. Geometry-less convex-hull/clickbox fallbacks project the object's base and top and vary camera-forward depth by screen row; reciprocal-depth interpolation matches perspective projection along that vertical anchor. If that projection is unavailable, the fallback conservatively uses the farthest sampled model depth for the whole shape. The vertex stride is controlled by `BillboardOcclusionQuality`.
8. `BillboardOcclusionMask` indexes candidate geometry in a coarse grid clipped to the visible billboard bounds and their individual interest rectangles. Cells retain every potentially intersecting occluder, even when their center misses the geometry. Conservative depth bounds provide uniform visible/occluded decisions; partial coverage is stored in reusable pixel-row bitsets. Full cells encode constant coverage without row storage.
9. Billboard compositing estimates depth once per sprite row. Proven uniform cells use the coarse fast path. Ambiguous cell rows resolve independent pixel visibility bits, combining cached coverage with depth checks where necessary; the compositor reuses those bits across the short span. Resolving a span may inspect transparent neighbors, but begins only when an opaque or translucent sprite pixel reaches it. No center sample is spread across geometry edges.
10. Geometry wholly behind the camera's near-plane safety threshold is discarded before it can populate the mask. This avoids false coverage from bridge or roof data behind the camera, but does not attempt to reproduce RuneLite's unexposed per-tile roof-removal decision.

## Quality Levels

- `OFF`: disables occlusion and clears the mask.
- `LOW`: 16px screen cells and fewer sampled model vertices.
- `MEDIUM`: 8px screen cells and balanced vertex sampling.
- `HIGH`: 4px screen cells and every exposed model vertex.
- `ULTRA`: 2px screen cells and a wider scenery corridor.
- `MAX`: 1px screen cells and the widest scenery corridor for debugging or high-end hardware.

Quality never drops arbitrary scenery faces: Low and Medium must still fill walls. The quality level affects the coarse grid size (edges still resolve to individual pixels), the model-vertex sampling cost, and the width of the terrain corridor sampled around each billboard sight line.

## Debugging

Use these debug options together:

- `Draw occlusion mask`: draws translucent cyan coverage, using whole rectangles only for fully covered cells and exact pixels along partial edges. Billboard pixels hidden by the occlusion test are painted red.
- `Debug Log Occlusion`: logs throttled collection stats at `debug` level. It includes upper-plane tile, accepted, grouped-roof rejection, unknown-metadata rejection, and GPU-conservative-rejection counters. Bounded scenery samples include source plane, tile plane, render level, scene coordinates, bridge origin, render evidence, roof ID/flags, object category/ID, and the acceptance or rejection reason.

Interpretation:

- Billboard draws over terrain and no cyan cells appear: the terrain may be level (check `flatTerrainFacesSkipped`), the corridor may be too narrow for that camera angle, the terrain triangle did not intersect the billboard interest area, or the billboard bounds were not available yet.
- Billboard draws over a wall, arch, tree, or other scenery and no cyan cells appear: check `sceneryCandidates`, `sceneryAccepted`, `sceneryRejectedByFilter`, `sceneryOutsideInterest`, `sceneryWithoutParts`, and `sceneryFaces`. These counters show whether the corridor missed the scenery, the filter rejected it, it was outside the billboard bounds, or RuneLite exposed no usable renderable model.
- Billboard draws over an upper wall and no cyan cells appear: check `upperPlaneTiles`, the upper-plane rejection counters, and the bounded rejected samples. A grouped roof under GPU rendering is deliberately rejected rather than allowed to falsely occlude a billboard.
- Cyan cells appear but the billboard still draws over the wall: the world depth estimate or bias is wrong for that geometry, or the billboard pixel's estimated surface depth does not match the in-game model posture closely enough.
- FPS drops with many billboards: compare considered, accepted, and shape counts at `LOW`, `MEDIUM`, `HIGH`, `ULTRA`, and `MAX`.

The throttled debug log also reports candidate references for the current mask and accumulated uniform decisions, exact pixel-depth checks, and candidate tests since the preceding log. Counts can include diagnostic queries; compositor work is reported on the next log because collection precedes drawing.

RuneLite SimplePolygon hulls are converted to filled Java2D paths when cached. Their native rectangle-intersection method detects boundary crossings and can reject wholly interior cells, so it must not be used for mask coverage. The shape remains a hull, not its bounding rectangle.

Vertical fallback shapes always check containment before returning depth, including at coarse qualities. A pixel outside a hull never inherits its sampled depth.

## Performance Measurement

Before the subsequent scenery height/face-selection fixes, a local Java 11 benchmark at High, with eight 80x160 sprites in a 640x480 framebuffer, measured mask preparation plus compositing after 4,000 warm-up frames. Median times were 0.667 -> 0.653 ms for a broad wall, 0.717 -> 0.726 ms for overlapping walls/billboards, and 0.715 -> 1.021 ms for a dense 1,269-triangle fixture. Per-frame allocations were approximately unchanged. The dense case exceeds the 10% overhead target; precise edges cost about 0.306 ms more in that fixture. These measurements reuse projected geometry and exclude collection and debug rendering, so they do not predict in-game FPS.

The scenery model-input log reports direct-model count, unavailable geometry count, and hidden-face count. If sceneryFaces stays zero, these distinguish unavailable model data from deliberately excluded faces; terrain alone cannot establish that a wall is occluding.

## Known Limits

- The mask uses projected triangles and fallback shapes; it cannot read the game renderer's authoritative depth buffer.
- Convex hulls and clickboxes can over-cover concave or thin geometry when model data is unavailable. Face-level hidden/fully transparent geometry is filtered; texture cutout pixels and partially transparent materials are not reproduced by this binary mask.
- World depth is based on sampled model vertices. Billboard depth is estimated per visible pixel on a camera-facing surface, not from the original in-game model's exact triangle at that pixel. Convex-hull/clickbox fallbacks deliberately favor visibility when the billboard intersects the occluder's sampled depth range.
- Terrain and scene-object geometry are sampled along camera-to-billboard corridors, not from the full rendered scene.
- The targeted upper-plane pass handles only walls and tall game objects. It intentionally does not add upper terrain, decorative objects, ground objects, or a software recreation of RuneLite's private GPU roof visibility.
- Final correctness must be verified in-game because RuneLite's public API does not expose the authoritative framebuffer/depth result.
