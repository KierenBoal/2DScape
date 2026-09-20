# Render Target Audit

This audit is based on the RuneLite 1.12.28 public API surface used by this plugin.

## Supported

- NPCs
  - Live frame snapping is supported through `Actor.setAnimationFrame(...)` and `Actor.setPoseAnimationFrame(...)`.
  - Billboard rendering is supported through `RenderCallback.addEntity(...)` and overlay redraw.
- Players
  - Live frame snapping is supported through the same `Actor` APIs as NPCs.
  - Players projected from sailing world views are resolved through their owning `WorldEntity` before billboard eligibility and placement.
- Sailing actors
  - Players projected from nested sailing world views retain their main-world location and orientation mapping.
  - Boat models remain 3D. The disabled composite boat billboard path and its geometry masking have been removed.
- Projectiles
  - Billboard rendering is supported through `RenderCallback.addEntity(...)`.
  - Billboard frame snapping is supported by snapping the billboard cache key from `Projectile.getAnimation()` and `Projectile.getAnimationFrame()`.
  - Live projectile frame mutation is not exposed by the public API.
- Graphics objects
  - Billboard rendering is supported through `RenderCallback.addEntity(...)`.
  - Billboard frame snapping is supported by snapping the billboard cache key from `GraphicsObject.getAnimation()` and `GraphicsObject.getAnimationFrame()`.
  - Live graphics object frame mutation is not exposed by the public API.
  - This is the closest public API match for many detached world effects such as spell impact or area graphics.
- World objects
  - `GameObject`, `GroundObject`, `WallObject`, and `DecorativeObject` can be hidden through `RenderCallback.drawObject(...)`.
  - Billboard rendering is supported for visible objects by observing `TileObject` render calls and redrawing their renderables in the overlay.
  - Dynamic object frame snapping is supported only in the billboard path, using `DynamicObject.getAnimation()` and `DynamicObject.getAnimFrame()`.
  - Live object frame mutation is not exposed by the public API because `DynamicObject` has no public frame setter.
  - This covers ground scenery and other visible scene objects that are backed by `TileObject` renderables.
- Ground items
  - Billboard rendering is supported through `ItemLayer` interception plus tracked `TileItem` locations.
  - Frame snapping is not applicable because `TileItem` does not expose an animation timeline.

## Partially Supported

- Attached spot animations on actors
  - RuneLite exposes `ActorSpotAnim.getFrame()` and `setFrame(...)`, but the public API does not expose the associated spot-animation definition or animation metadata needed to safely derive total frame counts for generalized snapping.
  - The billboard path anchors these effects to the parent actor and uses the effect's own height offset. Sprite redraws follow the configured cadence while placement follows the actor each frame.

## Investigated, deferred

- Sailing boat occlusion: `WorldEntity` exposes the nested `WorldView` and a main-world point transform, but not a ready stream of projected boat triangles. Rebuilding the hull from every nested scene tile each tick would require a full scene scan and is unsuitable here. Boats remain 3D and are not added to the occlusion depth buffer.
- ToA room fades: a room fade may be a widget or screen-space pass rather than scene geometry. The public scene object hooks used for face occlusion do not expose a general post-interface pixel layer with blend order. The billboard overlay therefore leaves this fade compositing unchanged.

## Not Supported By A Comparable Public Hook

- Inventory item icons
  - RuneLite exposes item sprite creation and caches, but there is no equivalent public per-draw UI render callback that lets a hub plugin replace inventory item rendering with custom billboard geometry at widget draw time.
- Bank item icons
  - Same limitation as inventory icons: item sprites can be created, but the widget draw path is not exposed as a general billboard override hook.
- Minimap object/icon billboards
  - RuneLite exposes limited minimap hooks such as tile drawing and some static sprite access, but not a general object/icon billboard replacement path comparable to scene `RenderCallback`.
- Minimap-rendered inventory or bank-style sprites
  - No public API path exists to inject arbitrary billboarded item rendering into the minimap pass.

## Requested Example Mapping

- Ground scenery
  - Supported through the world object path.
- Objects
  - Supported through the world object path.
- Animations like ruby bolts proccing on you
  - Supported when exposed as actor-attached spot animations, subject to the missing public animation metadata above.
- Effects like ice barrage
  - Supported when they exist as detached `GraphicsObject`s in the world.
  - Also supported when they are actor-attached spot animations.
- Items in inventory
  - Not supported by a comparable public render hook.
- Items in bank
  - Not supported by a comparable public render hook.
- Minimap rendering
  - Not supported by a comparable public render hook for arbitrary object or item billboard replacement.

## Practical Summary

- If a target is a world `Renderable` or `TileObject` drawn through scene rendering, this plugin can usually billboard it.
- If the target is a UI sprite or minimap sprite, RuneLite's public API does not provide an equivalent interception point for a hub plugin, so this plugin documents those limitations instead of attempting brittle cache hacks.
