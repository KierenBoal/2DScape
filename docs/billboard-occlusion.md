# Billboard Occlusion

This note documents the world-geometry occlusion path used by the billboard renderer.

## Constraint

RuneLite's public plugin API does not expose the OSRS software depth buffer or the GPU plugin depth texture. Hub plugins can observe scene render callbacks, object hulls/clickboxes, models, camera state, and projection helpers, but they cannot read the final z-buffer directly.

Because of that, billboard occlusion is implemented as an approximate software mask rather than a true per-pixel scene depth buffer.

## Pipeline

1. Before compositing billboards, scene tiles are collected along a small camera-to-billboard corridor for each active billboard target.
2. Accepted terrain tiles contribute projected triangle shapes from `SceneTilePaint` or `SceneTileModel`.
3. Scenery on those scene tiles contributes projected model-face triangles only when it passes the scenery filter:
   - `WallObject` is always eligible.
   - `GameObject` is eligible only when its renderable has meaningful vertical height, which keeps low/loose objects out while allowing walls, arches, trees, and similar static scenery.
   - `DecorativeObject` and `GroundObject` are intentionally excluded.
4. Eligible scenery can also contribute convex-hull/clickbox fallback shapes so walls and arches can still occlude when model faces are missing or sparse.
5. Draw-callback objects are not used for occlusion; they are deliberately ignored so arbitrary objects do not affect billboard visibility.
6. Each accepted shape receives an approximate nearest camera-forward depth from sampled model vertices or terrain triangle vertices. The vertex stride is controlled by `BillboardOcclusionQuality`.
7. `BillboardOcclusionMask` stores a low-resolution depth grid clipped to the union of visible billboard draw bounds, but only rasterizes cells inside the individual billboard rectangles. Empty space between billboards is skipped.
8. Billboard compositing maps each non-transparent billboard pixel back into sprite/model space, estimates that pixel's world position on the camera-facing billboard surface, and skips it when the sampled world occluder depth is nearer, with a small bias to reduce z-fighting-style flicker.

## Quality Levels

- `OFF`: disables occlusion and clears the mask.
- `LOW`: 16px screen cells and fewer sampled model vertices.
- `MEDIUM`: 8px screen cells and balanced vertex sampling.
- `HIGH`: 4px screen cells and every exposed model vertex.
- `ULTRA`: 2px screen cells and a wider scenery corridor.
- `MAX`: 1px screen cells and the widest scenery corridor for debugging or high-end hardware.

The quality level affects the screen-space mask resolution, the model-vertex sampling cost, and the width of the terrain corridor sampled around each billboard sight line.

## Debugging

Use these debug options together:

- `Draw occlusion mask`: draws translucent cyan cells where world geometry was sampled into the mask. Billboard pixels hidden by the occlusion test are painted red.
- `Debug Log Occlusion`: logs throttled collection stats at `debug` level. The log reports `sources=terrain,scenery`; there is no draw-callback object occlusion path. Unique scenery candidates are split into accepted objects plus objects rejected by the scenery filter, skipped for being outside the billboard bounds, or skipped because RuneLite exposed no renderable parts. The log includes bounded `acceptedScenery` and `rejectedScenery` samples in `id:name` form.

Interpretation:

- Billboard draws over terrain and no cyan cells appear: the terrain corridor may be too narrow for that camera angle, the terrain triangle did not intersect the billboard interest area, or the billboard bounds were not available yet.
- Billboard draws over a wall, arch, tree, or other scenery and no cyan cells appear: check `sceneryCandidates`, `sceneryAccepted`, `sceneryRejectedByFilter`, `sceneryOutsideInterest`, `sceneryWithoutParts`, and `sceneryFaces`. These counters show whether the corridor missed the scenery, the filter rejected it, it was outside the billboard bounds, or RuneLite exposed no usable renderable model.
- Cyan cells appear but the billboard still draws over the wall: the world depth estimate or bias is wrong for that geometry, or the billboard pixel's estimated surface depth does not match the in-game model posture closely enough.
- FPS drops with many billboards: compare considered, accepted, and shape counts at `LOW`, `MEDIUM`, `HIGH`, `ULTRA`, and `MAX`.

## Known Limits

- The mask is shape-based, not true triangle depth.
- Convex hulls and clickboxes can over-cover concave or thin geometry.
- World depth is based on sampled model vertices. Billboard depth is estimated per visible pixel on a camera-facing surface, not from the original in-game model's exact triangle at that pixel.
- Terrain and scene-object geometry are sampled along camera-to-billboard corridors, not from the full rendered scene.
- Final correctness must be verified in-game because RuneLite's public API does not expose the authoritative framebuffer/depth result.
