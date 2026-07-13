# Refactor progress

This is a working journal for the repository hardening pass. Percentages describe the current pass, not a claim that RuneLite behavior has been verified in-game.

## Status: complete

- Baseline: clean worktree; `./gradlew.bat test` passes (163 tests, 28 suites).
- Java target: release 11, as required by the plugin guidelines.
- Main structural hotspot: `NpcBillboardOverlay.java` is 3,682 lines and owns target discovery, interaction state, scheduling, caching, raster preparation, compositing, occlusion, and geometry. It is the primary refactor target.
- Other large files needing separation review: `BillboardWorldOcclusionCollector.java` (1,114), `NpcSnapConfig.java` (719), `NpcSnapPlugin.java` (603), and several multi-model files around 400–600 lines.
- Existing tests focus heavily on rendering utilities. Lifecycle/event routing and several live helpers have no dedicated unit suite.
- Added repeatable JaCoCo HTML/XML reporting. Initial full baseline was 28.2% line / 21.5% branch coverage; this confirms orchestration paths, not utility math, are the dominant gap.
- Added direct tests for animation-frame snapping/cache invalidation, angle conversion/snapping, texture resolution/UVs, and extracted interaction-state lifecycle.
- Extracted click/hover/interaction state from the overlay into `BillboardInteractionState`; this removes mutable lifecycle policy from the renderer and makes it directly testable.
- Removed per-pixel `Integer` allocation from outline flood fill by keeping a reusable primitive queue in renderer scratch storage.
- Avoided repeated NPC config reads inside the NPC loop and pre-sized the per-frame prepared-draw list.
- Renamed the development launcher from the misleading `NpcSnapPluginTest` to `NpcSnapPluginLauncher`.
- Extracted the login XP burst suppression policy into `LoginXpDropGuard` and covered its login/logout, grace-window, same-tick burst, and non-increase paths.
- Added config-default contract tests so performance budgets, opt-in/optional behavior, and interaction colors cannot silently drift.
- Current clean build: 186 tests passing; 32.0% line / 23.8% branch coverage (up from 163 tests, 28.2% / 21.5%).
- Extracted RuneLite render-callback routing into `BillboardSceneDrawCallbacks`. Actor clickbox preservation, UI pass-through, renderable suppression, whole tile-object suppression, part suppression, item layers, and disabled categories now have direct regression tests.
- `NpcSnapPlugin.java` is down to 490 lines from 603 and no longer contains the detailed tile-object dispatch tree.
- Added direct rasterization tests for solid/textured faces and transparent/degenerate paths, plus model-state hash sampling tests.
- Improved the per-frame skilling tracker path: explicit iterator expiry avoids a capturing predicate, the result list is capacity-sized, and iteration uses the stable tracked `EnumSet` instead of cloning `Skill.values()`.
- Removed short-lived varargs arrays from tile-object part suppression checks.
- Current clean build: 201 tests passing; 34.1% line / 25.3% branch coverage.
- Replaced duplicated 25-argument cache-key construction at overlay call sites with named cache-model factories and parity tests.
- Corrected inventory-sprite cache signatures: ground-item sprite bands now occupy the color-band field rather than accidentally invalidating through the texture-state field.
- Split the 599-line `BillboardTargetModels.java` grab-bag into eleven filename-addressable types (`BillboardTarget`, `BillboardTargetKey`, priority/dedup/tile keys, enums, and small carriers). The split was mechanical and passed a clean build.
- Added tests for priority keys, occupied-tile identity, graphics/actor effect deduplication, and target factories.
- Current clean build: 210 tests passing; 38.3% line / 28.1% branch coverage.
- Mechanically split the cache, update-scheduling, and render model grab-bags. Cache keys, cached images, texture entries, update state/plans/priorities, render requests/results, faces, and prepared draws are now addressable by filename.
- Extracted skilling fade/intro timing into `SkillingBubbleAnimation`; boundary, multi-skill, missing-state, fade-in, active, and fade-out behavior now has direct tests.
- `SkillingThoughtBubbleOverlay.java` is down to 413 lines from 464.
- Current clean build: 213 tests passing; 38.7% line / 28.4% branch coverage.
- Extracted `BillboardUpdateQueue` from the overlay. It now owns ordered membership, deduplication, polling/requeue behavior, pruning, priority replacement, and debug positions/scores, with direct state-machine tests.
- Extracted `BillboardVisibilityState` from the overlay. Live client-thread selections and published cross-thread snapshots now have explicit ownership and concurrency-semantics tests.
- Visibility snapshots now reuse the prior immutable snapshot when identity contents are unchanged, avoiding three identity-map/set allocations on stable frames.
- Extracted `BillboardOcclusionRegions`; clipping, expansion, invalid-draw filtering, and region union are tested independently and shared with the world occlusion collector.
- `NpcBillboardOverlay.java` is down to 3,500 lines from 3,682.
- Current clean build: 224 tests passing; 40.6% line / 29.7% branch coverage.
- Extracted `BillboardFrameBuffer`, which now owns reusable composite pixels, opaque paint-order protection, viewport clipping/scaling, alpha blending, row depth scratch, sampled occlusion checks, and their performance timing.
- Added direct frame-buffer tests for opaque/transparent blits, clipping, paint order, buffer reuse/resize, clearing, and invalid draws.
- Extracted `BillboardOcclusionDebugSampler`; diagnostic grid sampling, formatting, transparency filtering, frame clearing, and the sample cap now have focused tests outside the overlay.
- `NpcBillboardOverlay.java` is down to 3,232 lines from 3,682, with compositing internals no longer embedded in the orchestration class.
- Current clean build: 232 tests passing; 42.8% line / 31.4% branch coverage.
- Extracted the pure `BillboardUpdateScore` policy and covered cache urgency, forced interaction, priority/local actors, ownership, spot animations, depth, queue fairness, and model changes.
- Reused world-occlusion traversal sets/lists across frames instead of allocating four sets and a list on each collection pass.
- Removed two `Polygon` plus four temporary coordinate-array allocations per accepted triangle bounds check; direct rectangle calculation is tested.
- Extracted and fully tested configuration-change invalidation routing for global textures, UI textures, inventory sprites, unrelated keys, and unrelated config groups.
- Confirmed the remaining Gradle deprecation is environment-owned (Gradle 8.10 launched on Java 11); compilation correctly remains Java 11 as required.
- Current clean build: 242 tests passing; 43.4% line / 32.1% branch coverage. `NpcBillboardOverlay.java` is 3,196 lines and `NpcSnapPlugin.java` is 476 lines.
- Extracted `BillboardRenderRequestFactory`; actor, spot-animation, projectile, graphics-object, ground-item, static-object, and dynamic tile-part request construction no longer lives in the overlay.
- Added direct request tests for ground-item identity/quantity/orientation fields, missing tracked state, graphics-object height/frame behavior, and missing spot-animation parents.
- Extracted `BillboardTargetEligibility`; plane, circular radius, tile polygon, model, projection, and scene-front fallback policy now has a focused owner.
- Added eligibility regression tests, including a diagonal boundary test that ensured the original circular radius rule was preserved during extraction.
- `NpcBillboardOverlay.java` is down to 2,764 lines from 3,682.
- Current clean build: 251 tests passing; 44.8% line / 33.1% branch coverage.
- Extracted `BillboardCacheStore`; identity lookup, replacement, removal, TTL expiry, clearing, and image flushing are directly tested.
- Filled the geometry utility gap across bounds expansion, source/draw guards, distance, scaling sentinels, projection, aspect ratio, winding, canvas limits, and face union; added null guards for absent bounds.
- Removed the last `com.example` template remnant from test logging, corrected README wording, verified the icon has a real PNG signature, and added the required BSD-2-Clause license.
- Final automated verification: 260 tests, zero failures; 46.0% overall line / 34.6% branch coverage. Excluding six RuneLite-bound orchestration/render-debug classes, coverage is 80.1% line / 60.8% branch.
- Policy audit: no forbidden process, reflection, sleep, HTTP, desktop, Gson-construction, INFO logging, service-loader, template-package, or tracked build-artifact matches in production/test sources.
- Remaining verification is deliberately manual: actor clickboxes, visual suppression, projection/occlusion, sprites, effects, skilling bubbles, cache invalidation, and configuration behavior must be confirmed in the RuneLite development client.
- In-game verification completed by the user on 2026-07-13 with no issues found; behavior is working as expected.

## Next structural targets

- Continue extracting scheduling policy and target discovery/render-request construction from `NpcBillboardOverlay.java` (currently 3,232 lines).
- Add coverage around cache signatures/preview matching, target priority identities, and overlay early-exit/state-clearing orchestration.

## Working rules

- Preserve actor clickbox behavior: `addEntity` must not hide actors.
- Preserve tile-object visual suppression in `drawObject`.
- Keep render-frame work allocation-conscious and avoid scene-wide scans where event-maintained state is available.
- Prefer package-private, focused collaborators with direct unit tests over reflection-based testing.
- No RuneScape interaction or automated in-game verification.

## Next

1. Produce method/path coverage inventory for plugin lifecycle and overlay orchestration.
2. Add tests for currently untested deterministic helpers and event-routing branches.
3. Extract cohesive state/render collaborators from the overlay in test-backed increments.
