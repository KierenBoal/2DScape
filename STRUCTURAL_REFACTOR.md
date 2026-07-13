# Structural package refactor

This journal tracks the second refactor pass: moving the flat `com.kierenboal.npcsnap` source tree into broad functional packages without changing plugin behavior.

## Agreed layout

- Root composition: plugin, config, main billboard overlay, debug service, shared constants.
- `rendering`: geometry, rasterization, textures, colors, depth, outlines, and render models.
- `occlusion`: world collection, masks, regions, filtering, and occlusion diagnostics.
- `targeting`: discovery, classification, identity, eligibility, interaction, and scene callbacks.
- `state`: caches, visibility snapshots, metrics, and update scheduling.
- `features`: ground items, login XP guarding, and skilling thought bubbles.

Production tests move with their owning production classes so package-private implementation details can remain testable.

## Boundary rules

- Do not duplicate helpers between packages.
- Keep implementation details package-private unless another package is an intentional consumer.
- Prefer public methods/accessors at package boundaries over public mutable fields.
- Keep RuneLite lifecycle and dependency composition in the root package.
- Avoid generic `util`, `common`, or catch-all `data` packages.
- Preserve Java 11 compatibility and existing runtime behavior.

## Progress

- [x] Inventory all 82 production source files.
- [x] Assign every production source file to exactly one package or the root composition layer.
- [x] Move production sources.
- [x] Move matching tests.
- [x] Resolve package imports and access boundaries.
- [x] Audit and document cross-package dependencies.
- [x] Run the complete Gradle test suite.
- [x] Run source-policy searches.
- [x] Record the manual in-game verification checklist.

## Checkpoints and decisions

- Baseline worktree was clean on branch `init`.
- The root composition layer contains six files; 76 files move into functional packages.
- `BillboardFrameBuffer` and the occlusion implementation require special attention because they currently reference one another's functional areas.
- `NpcSnapDebug.FrameDebugInfo` currently leaks the root debug service into render request data and should be decoupled if needed to keep package dependencies clean.
- Production and test compilation pass after exposing the intentional package contracts used by the root overlay and plugin.
- Final production distribution: root 6, `features` 6, `occlusion` 7, `rendering` 30, `state` 16, and `targeting` 17.
- Mirrored test distribution: root 6, `features` 4, `occlusion` 5, `rendering` 21, `state` 7, and `targeting` 8.
- Full automated verification: 49 suites, 260 tests, zero failures, zero errors, and zero skipped tests.
- `git diff --check` passes. The production policy scan found no forbidden sleeping, termination waits, external processes, desktop URL opening, disallowed HTTP clients, direct Gson construction, INFO logging, template remnants, or hardcoded widget/object lookup IDs.

## Dependency audit

The structural move intentionally preserves existing behavior and shared implementations. It therefore exposes several dependencies that were previously hidden by the flat package:

- Rendering consumes target, state, feature, and occlusion models while the root overlay orchestrates the full pipeline.
- Occlusion consumes rendering depth/projection helpers and targeting scene models.
- State keys and update heuristics consume render requests and target identities.
- Configuration and debug services remain in the root composition package and are consumed across functional packages.

Forcing these relationships into an acyclic graph would require new interfaces and model ownership changes, not just a package move. Those changes are deferred to a separately tested behavioral refactor. The first candidates are an occlusion-query abstraction for `BillboardFrameBuffer` and standalone debug data models instead of `NpcSnapDebug` nested types.

## Manual in-game verification

Automated tests cannot establish RuneLite behavior. The user should verify:

- Actor billboards render while actor clickboxes remain usable.
- Tile-object originals are suppressed correctly and their billboards remain aligned.
- Occlusion works when actors, objects, projectiles, and graphics effects move behind scenery.
- Hover and interaction outlines update and clear correctly.
- Ground-item inventory sprites, texture banding, UI texture banding, and cache invalidation still respond to configuration changes.
- Skilling thought bubbles appear, animate, fade, and clear across login/logout transitions.
- Performance/debug overlays still display their expected state and metrics.
