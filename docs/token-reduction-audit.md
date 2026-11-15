# 2DScape token reduction audit

## Executive summary

RuneLite reports **211k source tokens**. The plugin needs to lose at least
11k to pass its 200k automatic-review limit; aiming near 190k would leave a
useful margin. The best next work is behavior-preserving deduplication. The
first-pass feature cuts are recorded only as fallbacks below, not as P1 work.

The production tree has 133 Java files, about 26.7k lines and 886 KB. RuneLite's
tokenizer was not available during this audit, so every saving is a rough source
estimate, not a measured token delta. The small refactors with a favorable
risk/readability profile appear to save roughly **2–4.5k tokens together**;
they are unlikely to close the full 11k gap. Recount in RuneLite after each
change before choosing larger work.

Moving code to another Java file does not reduce source tokens. Comments,
resources, tests and the README are excluded per the warning; image assets are
therefore not a code-token target. Avoid minifying names or compressing source
formatting: the likely reduction is small and the code becomes harder to review.

## Best bang-for-buck, behavior-preserving options

Rank reflects likely net token savings, effort, regression risk and whether the
result stays straightforward to maintain.

| Rank | Refactor | Rough saving | Effort / risk | Why it ranks here |
| ---: | --- | ---: | --- | --- |
| 1 | Remove the duplicate preview cache-key model | ~0.8–1.5k | Medium / low–medium | Largest clean DRY target; one key representation replaces two near-copies. |
| 2 | Generate simple value-key equality/hash code with existing Lombok | ~0.4–0.9k | Low / low | Removes manual boilerplate across several small immutable keys without adding a dependency. |
| 3 | Share face fallback and bounds preparation between normal rendering and export | ~0.2–0.5k | Low / low | Repeats a clear sequence while keeping each caller's transformations distinct. |
| 4 | Consolidate repeated update-state change comparisons | ~0.1–0.3k | Low / low | Several methods differ only by observed/redraw field and sentinel. |
| 5 | Share the tile-object classification decision core | ~0.2–0.4k | Medium / medium | Removes parallel type branches; preserve the real difference in observed-object behavior. |
| 6 | Share export and hover object-action membership | ~0.1–0.2k | Low / low | One duplicate action list, with `EXAMINE_OBJECT` retained as export-only. |
| 7 | Small performance-metrics cleanup | ~0.1–0.2k | Low / low | Retains profiling; trims repeated type qualification and unused forwarding wrappers. |
| 8 | Consolidate actor enumeration helpers | ~0.1–0.2k | Low / low–medium | Two similar player/NPC walks; keep this only if the generic helper reads naturally. |
| 9 | Remove confirmed dead helpers/imports and redundant overloads | <~0.2k | Very low / low | Good housekeeping, but too small to materially move the reported count. |

### 1. Replace `BillboardCachePreviewKey` with a preview comparison on the full key

`state/BillboardCachePreviewKey.java` repeats almost every field, factory,
constructor and assignment in `state/BillboardCacheKey.java`. Its only purpose
is to let `CachedBillboard.matchesPreviewKey()` compare a candidate redraw with
the cached key while intentionally ignoring `textureStateHash`.

Use `BillboardCacheKey` for both cases and add a comparison that ignores only
`textureStateHash`, or compare the equivalent key subset directly. Then remove
`BillboardCachePreviewKey` and `NpcBillboardOverlay.buildPreviewCacheKey()`.
Preserve the current preview equality fields exactly: particularly
`animatedTextureOffsetStateHash`, light boost, shadow type and outline options.
This is the clearest substantial DRY win in the repository.

Both key types also expose older `boolean spriteShadow` constructor overloads
that translate to `SpriteShadowType.LEGACY`; there are no call sites in this
repository. Remove the overloads only after deciding these classes are not an
external API. The enum-based factories and constructors remain the main path.

### 2. Use Lombok for simple value-key `equals` and `hashCode`

`BillboardCacheKey`, `BillboardTargetKey`, `EffectDedupKey` and `PriorityTileKey`
contain hand-written equality/hash boilerplate for immutable primitive and enum
fields. Lombok is already configured in `build.gradle` and used by production
classes. `@EqualsAndHashCode` could replace the boilerplate while leaving the
data and call sites intact. Check the generated field selection and equality
contract for each class. `BillboardTargetKey` is used in hash collections, so
confirm no code relies on current bucket/hash behavior; no persisted hash values
were found. A generated hash may differ numerically while preserving the Java
equality/hash contract.

Do **not** apply the annotation mechanically to `OccupiedTileKey`: it compares
its `worldView` by identity and hashes with `System.identityHashCode`, while a
generated implementation may use ordinary `equals`. Do not replace custom
shape equality in the occlusion collector either.

### 3. Reuse face preparation in normal render and PNG export

`NpcBillboardOverlay.renderRenderableBillboard()` and
`renderExportRequest()` both transform vertices, call `buildFaces()` with
back-face culling enabled, retry without culling when the result is empty, sort
faces by descending depth and compute bounds. The calls use different inputs
and coordinate offsets, but the fallback/sort/bounds stages can be shared.

Extract small helpers for “build with culling fallback” and “sort and compute
usable source bounds.” Keep vertex transformation and texture-time inputs at
the call sites. Preserve the existing performance timer boundaries in normal
rendering so the metrics panel still reports the same stages. This is a
behavior-preserving helper extraction rather than removal of export.

### 4. Reduce repeated `BillboardUpdateState` predicates

The `hasAnimationChanged`, `hasModelChanged`, `hasTextureChanged` and matching
`*SinceRedraw` methods repeat null checks, sentinel checks and inequality tests.
Small private helpers can express the repeated “known previous value differs
from current value” rule while callers retain their specific
`hasObservation`/`hasPosition` conditions. Keep the current `Integer.MIN_VALUE`
and `Long.MIN_VALUE` sentinel semantics explicit. This is a tidy low-risk
reduction, but do not hide the state-specific guards in an overly generic
framework.

### 5. Share the tile-object classification core carefully

`targeting/ObjectClassifier.classifyTileObjectDecision()` and
`targeting/BillboardClassificationDebug.classifyObservedTileObjectDecision()`
repeat the GameObject, DecorativeObject, WallObject and GroundObject type
branches. They are not identical: the observed-object path can classify
effect-like parts for `DecorativeObject`, while the direct classifier currently
returns `UNKNOWN` for that type. A shared helper can centralize the type-to-
classification outcome while callers provide the effect-like status and keep
their reason text. Preserve the direct/observed difference and existing
fallback behavior; do not “fix” it as part of a size-only refactor.

`BillboardClassificationDebug` is not dead debug-only code: its observed-object
resolver stores classifications used in targeting. Do not remove the class as
a shortcut.

### 6. Share menu-action membership without losing `EXAMINE_OBJECT`

`NpcSnapPlugin.isExportableMenuEntry()` repeats the seven object actions already
listed by `BillboardHoverInteractionResolver.isObjectAction()`. Extract a
shared object-action predicate or an export-specific predicate that delegates
to it and adds `EXAMINE_OBJECT`. Do not replace the export switch with the
existing helper directly, because that would drop examine-export behavior.

### 7. Keep performance metrics; trim their repeated source overhead

Profiling is a real feature, so removing it is not a first-priority size fix.
The three core instrumented files repeat the nested type name
`BillboardPerformanceMetrics.Timer` across roughly 60 try-with-resources blocks.
Import `BillboardPerformanceMetrics.Timer` in those files and refer to `Timer`;
the net saving is small but purely mechanical and keeps the instrumentation
readable.

`NpcSnapDebug` also has a `drawPerformanceMetrics` convenience overload and a
`performanceRowCapacity` wrapper that are only called by tests; production uses
the overload/helper with counters/context height. Removing those two wrappers
and adjusting tests would save a few more production lines (tests themselves
are excluded from RuneLite's count). A lambda-based `measure(name, () -> ...)`
wrapper around each timer is not recommended: it adds abstraction and likely
runtime allocation for little or no net source saving.

### 8. Consider one shared actor-collection helper

`NpcBillboardOverlay.allPlayers(WorldView)` and `allNpcs(WorldView)` repeat the
top-level/nested-view walk, visibility filtering, identity de-duplication and
ordered append. A single helper parameterized by the actor iterable would
remove the clone. The helper should remain compact and easy to read; if a
generic selector/lambda makes the type plumbing more confusing than the
duplicated methods, skip it. This is a small optional cleanup.

### 9. Clean proven dead code and imports

Static reference scans found these candidates:

- `NpcBillboardOverlay.isWithinBillboardRadius(...)`: no call sites.
- `BillboardWorldOcclusionCollector.UpperPlaneVisibility.gpuRoof(...)`: no call
  sites; the associated `ROOF_GPU_CONSERVATIVE` state/counter appears stale
  because the live GPU path returns `ROOF_VISIBILITY_UNKNOWN`.
- `NpcSnapDebug.drawBillboardDebug(...)`: empty body and no call sites.
- `NpcSnapConfig.deterministicAnimationLooping()`: hidden retired item with no
  code references.
- `NpcSnapPlugin` imports `GraphicsObject` and `Projectile` without any use
  beyond their import lines.

Verify the config key is intentionally retired before deleting it. The hidden
retro overhead keys are **not** dead: `NpcSnapPlugin` migrates saved values to
`useRetroOverheads()`. The two cache-key boolean constructors are public and
may have consumers outside this repository, so their deletion has more API
risk than the dead private helpers/imports.

The commented-out old `DecorativeObject` branch in `ObjectClassifier` saves no
countable source tokens because comments are excluded.

## Larger files and places to resist unsafe compression

| Source | Approx. size | Audit assessment |
| --- | ---: | --- |
| `NpcBillboardOverlay.java` | 136.9 KB / 3,876 lines | Largest source hotspot. Face preparation and actor enumeration have small shared stages; splitting it into files does not reduce tokens. Export is a distinct optional feature, but removing it is a product decision. |
| `occlusion/BillboardWorldOcclusionCollector.java` | 104.1 KB / 2,879 lines | Large scene geometry/visibility code. Similar projected-bounds loops exist, but they cover different geometry and fail open on projection uncertainty; abstraction has correctness risk. |
| `occlusion/BillboardOcclusionMask.java` | 35.2 KB / 1,110 lines | Core mask construction and per-pixel behavior; avoid generic callbacks or compact rewrites in hot loops. |
| `NpcSnapPlugin.java` | 32.1 KB / 1,037 lines | RuneLite subscription handlers look repetitive because each event requires its own entry point. A generic event dispatcher adds indirection without worthwhile source savings. |
| `NpcSnapDebug.java` | 29.4 KB / 828 lines | Debug and metrics rendering. Keep the metrics feature; remove only unused production wrappers and dead methods. |
| `rendering/BillboardOutlineRenderer.java` | 18.9 KB / 639 lines | Multiple public convenience overloads and small color helpers, but most pixel traversal is shared already. Pruning overloads saves little and changes callable API; avoid collapsing hot pixel logic for token count. |

Other apparent “large” code is mostly required surface area: `NpcSnapConfig`
contains individually annotated RuneLite settings and their user-facing labels;
event subscriptions in the plugin are framework entry points; and compatibility
migration retains user settings. Removing descriptions/comments does not address
the reported source-token count.

## Fallbacks if refactors do not reach the limit

The high-confidence source-preserving work above is unlikely to save 11k by
itself. If the RuneLite recount remains above the limit, the larger available
cuts are opt-in features. These are intentionally **not** ranked ahead of the
refactors because they remove visible or diagnostic functionality:

- The performance profiler spans `state/BillboardPerformanceMetrics.java`,
  `NpcSnapDebug`, the overlay, mask and collector. Removing the feature could
  save several thousand tokens, but loses the metrics display and diagnostic
  timing/counters.
- The PNG export workflow spans `export/`, menu wiring and roughly 260 lines in
  `NpcBillboardOverlay`. Removing it could save several thousand tokens, but
  removes the on-demand animated sprite export path.
- Retro overhead replacement spans actor overhead, chat and hitsplat
  renderers. Removing it could save the most, but also removes a visible
  feature set and should be treated as a product decision.

`NpcSnapChangelog.java` has only nine releases. Moving its strings into a
resource would save little, while a resource loader adds production code, so it
is not a recommended reduction target. Similarly, combining rasterizer or
occlusion loops behind callback abstractions risks performance and clarity for
limited source savings.

## Audit scope and confidence

Reviewed all 133 production Java sources under `src/main/java`, including the
overlay, rendering, occlusion, state, targeting, compatibility, feature and
export code; the conditional compatibility source set; Gradle source-set and
dependency configuration; plugin metadata; and asset/docs/tests inventory.
Scans found no private fields with declaration-only references and the three
unused private/empty helpers listed above. Clone review found the preview cache
key as the clearest substantial duplicate; other matches were smaller or
semantically different.

No source code was changed, and no build or tests were run. Only this Markdown
handoff was written. Estimates are directional; use RuneLite's count to confirm
any actual reduction.
