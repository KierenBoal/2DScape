# Render Target Audit

This audit is based on the RuneLite 1.12.28 public API surface used by this plugin.

## Supported

- NPCs
  - Live frame snapping is supported through `Actor.setAnimationFrame(...)` and `Actor.setPoseAnimationFrame(...)`.
  - Billboard rendering is supported through `RenderCallback.addEntity(...)` and overlay redraw.
- Players
  - Live frame snapping is supported through the same `Actor` APIs as NPCs.
  - Players projected from sailing world views are resolved through their owning `WorldEntity` before billboard eligibility and placement.
- Sailing boats (disabled experimental implementation)
  - The configuration item is hidden and defaults off while nested-scene rendering and interaction preservation remain unreliable.
  - The implementation is retained for future investigation, but target classification is hard-disabled so previously saved configuration values cannot activate it.
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

## Partially Supported / Not Implemented

- Attached spot animations on actors
  - RuneLite exposes `ActorSpotAnim.getFrame()` and `setFrame(...)`, but the public API does not expose the associated spot-animation definition or animation metadata needed to safely derive total frame counts for generalized snapping.
  - A robust billboard path also needs parent-actor attachment and stacking logic across multiple concurrent spot anims.
  - This is the bucket that likely contains effects such as ruby bolt procs on a player, ice barrage freezing visuals attached to an actor, and similar actor-bound graphics.
  - Result: not implemented in this plugin.

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
  - Not implemented. These are most likely actor-attached spot animations, which do not expose enough public metadata for generalized snapping or billboard replacement.
- Effects like ice barrage
  - Supported when they exist as detached `GraphicsObject`s in the world.
  - Not implemented when they are actor-attached spot animations rather than detached world graphics.
- Items in inventory
  - Not supported by a comparable public render hook.
- Items in bank
  - Not supported by a comparable public render hook.
- Minimap rendering
  - Not supported by a comparable public render hook for arbitrary object or item billboard replacement.

## Practical Summary

- If a target is a world `Renderable` or `TileObject` drawn through scene rendering, this plugin can usually billboard it.
- If the target is a UI sprite or minimap sprite, RuneLite's public API does not provide an equivalent interception point for a hub plugin, so this plugin documents those limitations instead of attempting brittle cache hacks.
