package com.kierenboal.npcspan;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class NpcBillboardOverlay extends Overlay
{
	private static final int FULL_CIRCLE = 2048;
	private static final int MAX_PITCH = 512;
	private static final int LOCAL_TILE_SIZE = 128;
	private static final int TRANSPARENT = 0;
	private static final int COMBAT_YAW = 512;
	private static final int OPPOSITE_COMBAT_YAW = 1536;
	private static final boolean USE_UNLIT_COLORS = true;
	private static final double MIN_RENDER_QUALITY = 0.01d;
	private static final int MAX_SOURCE_BILLBOARD_SIZE = 4096;
	private static final int MAX_SOURCE_BILLBOARD_COORDINATE = 32768;
	private static final int MAX_DRAW_BILLBOARD_SIZE = 8192;
	private static final int MAX_CANVAS_COORDINATE = 1_000_000;
	private static final int OUTLINE_PADDING = 1;
	private static final int RENDER_PRIORITY_NONE = -1;
	private static final int RENDER_PRIORITY_GROUND_ITEM = 0;
	private static final int RENDER_PRIORITY_ACTOR = 1;
	private static final int RENDER_PRIORITY_EFFECT = 2;

	private final Client client;
	private final NpcSnapConfig config;
	private final NpcSnapDebug debug;
	private final AnimationFrameSnapper animationFrameSnapper;
	private final Map<Renderable, CachedBillboard> billboardCache = new HashMap<>();
	private final Map<TileItem, GroundItemBillboard> groundItems = new HashMap<>();
	private final Map<TileObject, ObservedTileObject> observedTileObjects = new IdentityHashMap<>();
	private final Map<TileObject, ObservedTileObject> visibleTileObjects = new IdentityHashMap<>();
	private final Set<Renderable> activeBillboards = new HashSet<>();
	private final Set<TileObject> activeTileObjects = Collections.newSetFromMap(new IdentityHashMap<>());
	private volatile Set<Renderable> activeBillboardSnapshot = Collections.emptySet();
	private volatile Set<TileObject> activeTileObjectSnapshot = Collections.emptySet();
	private int activeBillboardsGameCycle = Integer.MIN_VALUE;

	@Inject
	private NpcBillboardOverlay(Client client, NpcSnapConfig config, NpcSnapDebug debug, AnimationFrameSnapper animationFrameSnapper)
	{
		this.client = client;
		this.config = config;
		this.debug = debug;
		this.animationFrameSnapper = animationFrameSnapper;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			activeBillboards.clear();
			activeTileObjects.clear();
			clearActiveSnapshots();
			return null;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			activeBillboards.clear();
			activeTileObjects.clear();
			clearActiveSnapshots();
			return null;
		}

		if (!config.enable2dBillboardSprites())
		{
			activeBillboards.clear();
			activeTileObjects.clear();
			clearActiveSnapshots();
			drawDebugBoundingBoxes(graphics, worldView);
			return null;
		}

		List<BillboardTarget> visibleTargets = sortTargetsForRender(getVisibleTargets(worldView));
		for (int i = 0; i < visibleTargets.size(); i++)
		{
			renderTarget(graphics, visibleTargets.get(i), i + 1);
		}

		drawDebugBoundingBoxes(graphics, visibleTargets);
		return null;
	}

	private void drawDebugBoundingBoxes(Graphics2D graphics, WorldView worldView)
	{
		if (!config.debugDrawBoundingBox())
		{
			return;
		}

		List<BillboardTarget> candidates = collectCandidates(worldView);
		candidates.sort(Comparator.comparingDouble(BillboardTarget::getDepth));
		int limit = Math.max(1, config.billboardMaxEntities());
		drawDebugBoundingBoxes(graphics, candidates.subList(0, Math.min(limit, candidates.size())));
	}

	private void drawDebugBoundingBoxes(Graphics2D graphics, List<BillboardTarget> targets)
	{
		if (!config.debugDrawBoundingBox())
		{
			return;
		}

		for (BillboardTarget target : targets)
		{
			drawDebugBoundingBox(graphics, target);
		}
	}

	void trackGroundItem(TileItem item, Tile tile)
	{
		if (item == null || tile == null)
		{
			return;
		}

		LocalPoint localPoint = tile.getLocalLocation();
		if (localPoint == null)
		{
			return;
		}

		groundItems.put(item, new GroundItemBillboard(tile.getPlane(), localPoint));
	}

	void untrackGroundItem(TileItem item)
	{
		if (item == null)
		{
			return;
		}

		groundItems.remove(item);
		billboardCache.remove(item);
		activeBillboards.remove(item);
	}

	void clearGroundItems()
	{
		for (TileItem item : groundItems.keySet())
		{
			billboardCache.remove(item);
			activeBillboards.remove(item);
		}

		groundItems.clear();
	}

	private void clearActiveSnapshots()
	{
		activeBillboardSnapshot = Collections.emptySet();
		activeTileObjectSnapshot = Collections.emptySet();
	}

	void syncGroundItems(WorldView worldView)
	{
		if (worldView == null || worldView.getScene() == null)
		{
			clearGroundItems();
			return;
		}

		Tile[][][] tiles = worldView.getScene().getTiles();
		if (tiles == null)
		{
			clearGroundItems();
			return;
		}

		Set<TileItem> seenItems = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Tile[][] planeTiles : tiles)
		{
			if (planeTiles == null)
			{
				continue;
			}

			for (Tile[] row : planeTiles)
			{
				if (row == null)
				{
					continue;
				}

				for (Tile tile : row)
				{
					if (tile == null)
					{
						continue;
					}

					Collection<TileItem> tileItems = tile.getGroundItems();
					if (tileItems == null)
					{
						continue;
					}

					for (TileItem item : tileItems)
					{
						if (item == null)
						{
							continue;
						}

						seenItems.add(item);
						trackGroundItem(item, tile);
					}
				}
			}
		}

		List<TileItem> staleItems = new ArrayList<>();
		for (TileItem item : groundItems.keySet())
		{
			if (!seenItems.contains(item))
			{
				staleItems.add(item);
			}
		}

		for (TileItem item : staleItems)
		{
			untrackGroundItem(item);
		}
	}

	void clearTileObjects()
	{
		observedTileObjects.clear();
		visibleTileObjects.clear();
		activeTileObjects.clear();
		activeTileObjectSnapshot = Collections.emptySet();
	}

	void beginFrame()
	{
		visibleTileObjects.clear();
		visibleTileObjects.putAll(observedTileObjects);
		observedTileObjects.clear();
		activeBillboards.clear();
		activeTileObjects.clear();
		activeBillboardsGameCycle = Integer.MIN_VALUE;
	}

	void observeTileObject(TileObject tileObject)
	{
		ObservedTileObject observed = buildObservedTileObject(tileObject);
		if (observed != null)
		{
			observedTileObjects.put(tileObject, observed);
		}
	}

	void seedGroundItems(WorldView worldView)
	{
		if (worldView == null || worldView.getScene() == null)
		{
			return;
		}

		Tile[][][] tiles = worldView.getScene().getTiles();
		if (tiles == null)
		{
			return;
		}

		for (Tile[][] planeTiles : tiles)
		{
			if (planeTiles == null)
			{
				continue;
			}

			for (Tile[] row : planeTiles)
			{
				if (row == null)
				{
					continue;
				}

				for (Tile tile : row)
				{
					if (tile == null)
					{
						continue;
					}

					Collection<TileItem> tileItems = tile.getGroundItems();
					if (tileItems == null)
					{
						continue;
					}

					for (TileItem item : tileItems)
					{
						trackGroundItem(item, tile);
					}
				}
			}
		}
	}

	boolean shouldHideRenderable(Renderable renderable)
	{
		if (renderable == null)
		{
			return false;
		}

		if (client.isClientThread())
		{
			ensureActiveBillboardsCurrent();
			return activeBillboards.contains(renderable);
		}

		return activeBillboardSnapshot.contains(renderable);
	}

	boolean shouldHideTileObject(TileObject tileObject)
	{
		if (tileObject == null)
		{
			return false;
		}

		if (client.isClientThread())
		{
			ensureActiveBillboardsCurrent();
			return activeTileObjects.contains(tileObject);
		}

		return activeTileObjectSnapshot.contains(tileObject);
	}

	private void renderTarget(Graphics2D graphics, BillboardTarget target, int paintOrder)
	{
		if (target.type == BillboardTargetType.TILE_OBJECT)
		{
			renderTileObjectTarget(graphics, target, paintOrder);
			return;
		}

		BillboardRenderRequest request = buildRenderRequest(target);
		if (request == null)
		{
			return;
		}

		BillboardRenderResult result = renderRenderableBillboard(graphics, request);
		if (result == null)
		{
			return;
		}

		debug.drawBillboardDebug(graphics, NpcSnapDebug.RenderDebug.forBounds(
			result.bounds,
			paintOrder,
			result.cacheInvalidated,
			result.spriteRedrawn,
			request.frameDebugInfo
		));
	}

	private void renderTileObjectTarget(Graphics2D graphics, BillboardTarget target, int paintOrder)
	{
		if (target.observedTileObject == null)
		{
			return;
		}

		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			BillboardRenderRequest request = buildRenderRequest(target, part);
			if (request == null)
			{
				continue;
			}

			BillboardRenderResult result = renderRenderableBillboard(graphics, request);
			if (result == null)
			{
				continue;
			}

			debug.drawBillboardDebug(graphics, NpcSnapDebug.RenderDebug.forBounds(
				result.bounds,
				paintOrder,
				result.cacheInvalidated,
				result.spriteRedrawn,
				request.frameDebugInfo
			));
		}
	}

	private List<BillboardTarget> getVisibleTargets(WorldView worldView)
	{
		ensureActiveBillboardsCurrent(worldView);

		List<BillboardTarget> visibleTargets = new ArrayList<>(activeBillboards.size() + activeTileObjects.size());
		for (Renderable renderable : activeBillboards)
		{
			BillboardTarget target = buildActiveTarget(renderable);
			if (target != null)
			{
				visibleTargets.add(target);
			}
		}

		for (TileObject tileObject : activeTileObjects)
		{
			BillboardTarget target = buildActiveTarget(worldView, tileObject);
			if (target != null)
			{
				visibleTargets.add(target);
			}
		}

		return visibleTargets;
	}

	private List<BillboardTarget> sortTargetsForRender(List<BillboardTarget> targets)
	{
		if (targets.isEmpty())
		{
			return targets;
		}

		Map<PriorityTileKey, List<BillboardTarget>> groupedTargets = new HashMap<>();
		List<BillboardTargetGroup> orderedGroups = new ArrayList<>();
		for (BillboardTarget target : targets)
		{
			PriorityTileKey priorityTileKey = target.getPriorityTileKey();
			if (priorityTileKey == null)
			{
				orderedGroups.add(BillboardTargetGroup.single(target));
				continue;
			}

			groupedTargets.computeIfAbsent(priorityTileKey, ignored -> new ArrayList<>()).add(target);
		}

		for (List<BillboardTarget> grouped : groupedTargets.values())
		{
			grouped.sort(Comparator
				.comparingInt(BillboardTarget::getRenderPriority)
				.thenComparing(Comparator.comparingDouble(BillboardTarget::getDepth).reversed()));
			orderedGroups.add(BillboardTargetGroup.group(grouped));
		}

		orderedGroups.sort(Comparator.comparingDouble(BillboardTargetGroup::getDepth).reversed());
		List<BillboardTarget> orderedTargets = new ArrayList<>(targets.size());
		for (BillboardTargetGroup group : orderedGroups)
		{
			orderedTargets.addAll(group.targets);
		}

		return orderedTargets;
	}

	private void ensureActiveBillboardsCurrent()
	{
		if (!client.isClientThread())
		{
			return;
		}

		ensureActiveBillboardsCurrent(client.getTopLevelWorldView());
	}

	private void ensureActiveBillboardsCurrent(WorldView worldView)
	{
		int gameCycle = client.getGameCycle();
		if (activeBillboardsGameCycle == gameCycle)
		{
			return;
		}

		activeBillboards.clear();
		activeTileObjects.clear();
		activeBillboardsGameCycle = gameCycle;
		if (!config.enable2dBillboardSprites() || client.getGameState() != GameState.LOGGED_IN || worldView == null)
		{
			return;
		}

		List<BillboardTarget> candidates = collectCandidates(worldView);
		candidates.sort(Comparator.comparingDouble(BillboardTarget::getDepth));
		int limit = Math.max(1, config.billboardMaxEntities());
		int count = Math.min(limit, candidates.size());
		for (int i = 0; i < count; i++)
		{
			BillboardTarget target = candidates.get(i);
			if (target.renderable != null)
			{
				activeBillboards.add(target.renderable);
			}
			else if (target.tileObject != null)
			{
				activeTileObjects.add(target.tileObject);
			}
		}

		activeBillboardSnapshot = new HashSet<>(activeBillboards);
		activeTileObjectSnapshot = Collections.newSetFromMap(new IdentityHashMap<>());
		activeTileObjectSnapshot.addAll(activeTileObjects);
	}

	private List<BillboardTarget> collectCandidates(WorldView worldView)
	{
		Player localPlayer = client.getLocalPlayer();
		LocalPoint localPlayerLocation = localPlayer != null ? localPlayer.getLocalLocation() : null;
		Rectangle viewport = getViewportBounds();
		List<BillboardTarget> candidates = new ArrayList<>();

		if (config.applyToNpcs())
		{
			for (NPC npc : worldView.npcs())
			{
				if (npc == null || !isEligibleActor(localPlayerLocation, npc.getLocalLocation(), npc, viewport))
				{
					continue;
				}

				candidates.add(BillboardTarget.forRenderable(
					BillboardTargetType.NPC,
					npc,
					billboardDepth(npc),
					RENDER_PRIORITY_ACTOR,
					npc.getLocalLocation(),
					npc.getWorldView().getPlane()
				));
			}
		}

		if (config.applyToPlayers())
		{
			for (Player player : worldView.players())
			{
				if (player == null || !isEligibleActor(localPlayerLocation, player.getLocalLocation(), player, viewport))
				{
					continue;
				}

				candidates.add(BillboardTarget.forRenderable(
					BillboardTargetType.PLAYER,
					player,
					billboardDepth(player),
					RENDER_PRIORITY_ACTOR,
					player.getLocalLocation(),
					player.getWorldView().getPlane()
				));
			}
		}

		if (config.applyToProjectiles())
		{
			for (Projectile projectile : client.getProjectiles())
			{
				if (projectile == null || !isEligibleProjectile(localPlayerLocation, projectile, viewport))
				{
					continue;
				}

				candidates.add(BillboardTarget.forRenderable(
					BillboardTargetType.PROJECTILE,
					projectile,
					billboardDepth(projectile),
					RENDER_PRIORITY_EFFECT,
					projectileLocalPoint(projectile),
					projectile.getFloor()
				));
			}
		}

		if (config.applyToGraphicsObjects())
		{
			for (GraphicsObject graphicsObject : worldView.getGraphicsObjects())
			{
				if (graphicsObject == null || !isEligibleGraphicsObject(localPlayerLocation, graphicsObject, viewport))
				{
					continue;
				}

				candidates.add(BillboardTarget.forRenderable(
					BillboardTargetType.GRAPHICS_OBJECT,
					graphicsObject,
					billboardDepth(graphicsObject),
					RENDER_PRIORITY_EFFECT,
					graphicsObject.getLocation(),
					graphicsObject.getLevel()
				));
			}
		}

		if (config.applyToGroundItems())
		{
			for (Map.Entry<TileItem, GroundItemBillboard> entry : groundItems.entrySet())
			{
				TileItem item = entry.getKey();
				GroundItemBillboard groundItem = entry.getValue();
				if (item == null || groundItem == null || !isEligibleGroundItem(localPlayerLocation, item, groundItem, viewport))
				{
					continue;
				}

				candidates.add(BillboardTarget.forGroundItem(item, groundItem, billboardDepth(item)));
			}
		}

		if (config.applyToObjects() || config.applyToGraphicsObjects())
		{
			for (ObservedTileObject observed : visibleTileObjects.values())
			{
				boolean effectLike = isEffectObservedTileObject(worldView, observed);
				if (effectLike ? !config.applyToGraphicsObjects() : !config.applyToObjects())
				{
					continue;
				}

				if (!isEligibleTileObject(localPlayerLocation, observed, viewport))
				{
					continue;
				}

				candidates.add(BillboardTarget.forTileObject(observed, billboardDepth(observed), effectLike));
			}
		}

		return candidates;
	}

	private BillboardTarget buildActiveTarget(Renderable renderable)
	{
		if (renderable instanceof NPC)
		{
			NPC npc = (NPC) renderable;
			return BillboardTarget.forRenderable(
				BillboardTargetType.NPC,
				renderable,
				billboardDepth(npc),
				RENDER_PRIORITY_ACTOR,
				npc.getLocalLocation(),
				npc.getWorldView().getPlane()
			);
		}
		if (renderable instanceof Player)
		{
			Player player = (Player) renderable;
			return BillboardTarget.forRenderable(
				BillboardTargetType.PLAYER,
				renderable,
				billboardDepth(player),
				RENDER_PRIORITY_ACTOR,
				player.getLocalLocation(),
				player.getWorldView().getPlane()
			);
		}
		if (renderable instanceof Projectile)
		{
			Projectile projectile = (Projectile) renderable;
			return BillboardTarget.forRenderable(
				BillboardTargetType.PROJECTILE,
				renderable,
				billboardDepth(projectile),
				RENDER_PRIORITY_EFFECT,
				projectileLocalPoint(projectile),
				projectile.getFloor()
			);
		}
		if (renderable instanceof GraphicsObject)
		{
			GraphicsObject graphicsObject = (GraphicsObject) renderable;
			return BillboardTarget.forRenderable(
				BillboardTargetType.GRAPHICS_OBJECT,
				renderable,
				billboardDepth(graphicsObject),
				RENDER_PRIORITY_EFFECT,
				graphicsObject.getLocation(),
				graphicsObject.getLevel()
			);
		}
		if (renderable instanceof TileItem)
		{
			GroundItemBillboard groundItem = groundItems.get(renderable);
			return groundItem == null ? null : BillboardTarget.forGroundItem((TileItem) renderable, groundItem, billboardDepth((TileItem) renderable));
		}

		return null;
	}

	private BillboardTarget buildActiveTarget(WorldView worldView, TileObject tileObject)
	{
		ObservedTileObject observed = visibleTileObjects.get(tileObject);
		if (observed == null)
		{
			return null;
		}

		return BillboardTarget.forTileObject(observed, billboardDepth(observed), isEffectObservedTileObject(worldView, observed));
	}

	private BillboardRenderRequest buildRenderRequest(BillboardTarget target)
	{
		switch (target.type)
		{
			case NPC:
			case PLAYER:
				return buildActorRenderRequest((Actor) target.renderable);
			case PROJECTILE:
				return buildProjectileRenderRequest((Projectile) target.renderable);
			case GRAPHICS_OBJECT:
				return buildGraphicsObjectRenderRequest((GraphicsObject) target.renderable);
			case GROUND_ITEM:
				return buildGroundItemRenderRequest((TileItem) target.renderable, target.groundItem);
			default:
				return null;
		}
	}

	private BillboardRenderRequest buildRenderRequest(BillboardTarget target, ObjectRenderablePart part)
	{
		if (target.type != BillboardTargetType.TILE_OBJECT || part == null || target.tileObject == null)
		{
			return null;
		}

		int snappedFrame = part.renderable instanceof DynamicObject
			? snapFrame(((DynamicObject) part.renderable).getAnimation(), ((DynamicObject) part.renderable).getAnimFrame(), config.enableObjectFrameSnapping())
			: -1;
		int animationId = part.renderable instanceof DynamicObject && ((DynamicObject) part.renderable).getAnimation() != null
			? ((DynamicObject) part.renderable).getAnimation().getId()
			: target.tileObject.getId();
		NpcSnapDebug.FrameDebugInfo frameDebugInfo = snappedFrame >= 0
			? NpcSnapDebug.FrameDebugInfo.of(animationId, part.renderable instanceof DynamicObject ? ((DynamicObject) part.renderable).getAnimFrame() : snappedFrame, snappedFrame)
			: null;
		return new BillboardRenderRequest(
			part.renderable,
			part.renderable.getModel(),
			part.localPoint,
			part.plane,
			0,
			relativeYaw(),
			relativePitch(),
			animationId,
			snappedFrame,
			-1,
			-1,
			null,
			VerticalAnchor.BOTTOM,
			frameDebugInfo
		);
	}

	private BillboardRenderRequest buildActorRenderRequest(Actor actor)
	{
		LocalPoint localPoint = actor.getLocalLocation();
		return new BillboardRenderRequest(
			actor,
			actor.getModel(),
			localPoint,
			actor.getWorldView().getPlane(),
			Math.max(0, actor.getAnimationHeightOffset()),
			relativeYaw(actor),
			relativePitch(actor),
			actor.getAnimation(),
			actor.getAnimationFrame(),
			actor.getPoseAnimation(),
			actor.getPoseAnimationFrame(),
			actorHullBounds(actor),
			VerticalAnchor.BOTTOM,
			debug.actorFrameDebugInfo(actor)
		);
	}

	private BillboardRenderRequest buildProjectileRenderRequest(Projectile projectile)
	{
		int snappedFrame = snapFrame(projectile.getAnimation(), projectile.getAnimationFrame(), config.enableProjectileFrameSnapping());
		return new BillboardRenderRequest(
			projectile,
			projectile.getModel(),
			projectileLocalPoint(projectile),
			projectile.getFloor(),
			projectileVerticalOffset(projectile),
			relativeYaw(projectile),
			relativePitch(),
			projectile.getId(),
			snappedFrame,
			-1,
			-1,
			null,
			VerticalAnchor.CENTER,
			NpcSnapDebug.FrameDebugInfo.of(projectile.getId(), projectile.getAnimationFrame(), snappedFrame)
		);
	}

	private BillboardRenderRequest buildGraphicsObjectRenderRequest(GraphicsObject graphicsObject)
	{
		int snappedFrame = snapFrame(graphicsObject.getAnimation(), graphicsObject.getAnimationFrame(), config.enableGraphicsObjectFrameSnapping());
		return new BillboardRenderRequest(
			graphicsObject,
			graphicsObject.getModel(),
			graphicsObject.getLocation(),
			graphicsObject.getLevel(),
			Math.max(0, graphicsObject.getZ()),
			relativeYaw(),
			relativePitch(),
			graphicsObject.getId(),
			snappedFrame,
			-1,
			-1,
			null,
			VerticalAnchor.BOTTOM,
			NpcSnapDebug.FrameDebugInfo.of(graphicsObject.getId(), graphicsObject.getAnimationFrame(), snappedFrame)
		);
	}

	private BillboardRenderRequest buildGroundItemRenderRequest(TileItem item, GroundItemBillboard groundItem)
	{
		if (groundItem == null)
		{
			return null;
		}

		return new BillboardRenderRequest(
			item,
			item.getModel(),
			groundItem.localPoint,
			groundItem.plane,
			0,
			relativeYaw(item),
			relativePitch(),
			item.getId(),
			item.getQuantity(),
			-1,
			-1,
			null,
			VerticalAnchor.BOTTOM,
			null
		);
	}

	private void drawDebugBoundingBox(Graphics2D graphics, BillboardTarget target)
	{
		switch (target.type)
		{
			case NPC:
			case PLAYER:
			{
				Actor actor = (Actor) target.renderable;
				debug.drawRenderableBoundingBox(graphics, actor, actor.getLocalLocation(), actor.getWorldView().getPlane(), Math.max(0, actor.getAnimationHeightOffset()));
				return;
			}
			case PROJECTILE:
			{
				Projectile projectile = (Projectile) target.renderable;
				debug.drawRenderableBoundingBox(graphics, projectile, projectileLocalPoint(projectile), projectile.getFloor(), projectileVerticalOffset(projectile));
				return;
			}
			case GRAPHICS_OBJECT:
			{
				GraphicsObject graphicsObject = (GraphicsObject) target.renderable;
				debug.drawRenderableBoundingBox(graphics, graphicsObject, graphicsObject.getLocation(), graphicsObject.getLevel(), Math.max(0, graphicsObject.getZ()));
				return;
			}
			case GROUND_ITEM:
			{
				debug.drawRenderableBoundingBox(graphics, target.renderable, target.groundItem.localPoint, target.groundItem.plane, 0);
				return;
			}
			case TILE_OBJECT:
			{
				if (target.observedTileObject == null)
				{
					return;
				}

				for (ObjectRenderablePart part : target.observedTileObject.parts)
				{
					debug.drawRenderableBoundingBox(graphics, part.renderable, part.localPoint, part.plane, 0);
				}
				return;
			}
			default:
				return;
		}
	}

	private boolean isEligibleActor(LocalPoint localPlayerLocation, LocalPoint actorLocation, Actor actor, Rectangle viewport)
	{
		if (localPlayerLocation == null || actorLocation == null)
		{
			return false;
		}

		if (!isWithinRadius(localPlayerLocation, actorLocation, config.billboardRadiusTiles()))
		{
			return false;
		}

		Polygon tilePoly = actor.getCanvasTilePoly();
		if (tilePoly != null && tilePoly.getBounds().intersects(viewport))
		{
			return true;
		}

		int verticalOffset = Math.max(0, actor.getAnimationHeightOffset());
		Point canvasPoint = Perspective.localToCanvas(client, actorLocation, actor.getWorldView().getPlane(), verticalOffset + (actor.getModelHeight() / 2));
		return canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY());
	}

	private boolean isEligibleProjectile(LocalPoint localPlayerLocation, Projectile projectile, Rectangle viewport)
	{
		LocalPoint projectileLocation = projectileLocalPoint(projectile);
		if (localPlayerLocation == null || projectileLocation == null)
		{
			return false;
		}

		if (!isWithinRadius(localPlayerLocation, projectileLocation, config.billboardRadiusTiles()))
		{
			return false;
		}

		if (projectile.getModel() == null)
		{
			return false;
		}

		Point canvasPoint = Perspective.localToCanvas(client, projectileLocation, projectile.getFloor(), projectileVerticalOffset(projectile));
		return canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY());
	}

	private boolean isEligibleGraphicsObject(LocalPoint localPlayerLocation, GraphicsObject graphicsObject, Rectangle viewport)
	{
		LocalPoint localPoint = graphicsObject.getLocation();
		if (localPlayerLocation == null || localPoint == null)
		{
			return false;
		}

		if (!isWithinRadius(localPlayerLocation, localPoint, config.billboardRadiusTiles()))
		{
			return false;
		}

		if (graphicsObject.getModel() == null)
		{
			return false;
		}

		Point canvasPoint = Perspective.localToCanvas(client, localPoint, graphicsObject.getLevel(), Math.max(0, graphicsObject.getZ()));
		return canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY());
	}

	private static LocalPoint projectileLocalPoint(Projectile projectile)
	{
		return new LocalPoint((int) projectile.getX(), (int) projectile.getY());
	}

	private int projectileVerticalOffset(Projectile projectile)
	{
		LocalPoint localPoint = projectileLocalPoint(projectile);
		int tileHeight = Perspective.getTileHeight(client, localPoint, projectile.getFloor());
		return tileHeight - (int) Math.round(projectile.getZ());
	}

	private boolean isEligibleGroundItem(LocalPoint localPlayerLocation, TileItem item, GroundItemBillboard groundItem, Rectangle viewport)
	{
		if (localPlayerLocation == null || groundItem.localPoint == null)
		{
			return false;
		}

		if (!isWithinRadius(localPlayerLocation, groundItem.localPoint, config.billboardRadiusTiles()))
		{
			return false;
		}

		Point canvasPoint = Perspective.localToCanvas(client, groundItem.localPoint, groundItem.plane, Math.max(0, item.getModelHeight() / 2));
		return canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY());
	}

	private boolean isEligibleTileObject(LocalPoint localPlayerLocation, ObservedTileObject observed, Rectangle viewport)
	{
		if (localPlayerLocation == null || observed == null)
		{
			return false;
		}

		for (ObjectRenderablePart part : observed.parts)
		{
			if (part.localPoint == null || part.renderable == null)
			{
				continue;
			}

			if (!isWithinRadius(localPlayerLocation, part.localPoint, config.billboardRadiusTiles()))
			{
				continue;
			}

			Point canvasPoint = Perspective.localToCanvas(client, part.localPoint, part.plane, Math.max(0, part.renderable.getModelHeight() / 2));
			if (canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY()))
			{
				return true;
			}
		}

		return false;
	}

	private boolean isEffectObservedTileObject(WorldView worldView, ObservedTileObject observed)
	{
		if (observed == null)
		{
			return false;
		}

		if (worldView == null)
		{
			return false;
		}

		for (ObjectRenderablePart part : observed.parts)
		{
			if (part.localPoint == null)
			{
				continue;
			}

			if (overlapsActor(worldView, part.localPoint, part.plane))
			{
				return true;
			}
		}

		return false;
	}

	private static boolean overlapsActor(WorldView worldView, LocalPoint localPoint, int plane)
	{
		if (worldView == null || localPoint == null)
		{
			return false;
		}

		if (worldView.players() != null)
		{
			for (Player player : worldView.players())
			{
				if (player == null || player.getWorldView() == null || player.getWorldView().getPlane() != plane)
				{
					continue;
				}

				LocalPoint playerPoint = player.getLocalLocation();
				if (playerPoint != null && playerPoint.equals(localPoint))
				{
					return true;
				}
			}
		}

		if (worldView.npcs() != null)
		{
			for (NPC npc : worldView.npcs())
			{
				if (npc == null || npc.getWorldView() == null || npc.getWorldView().getPlane() != plane)
				{
					continue;
				}

				LocalPoint npcPoint = npc.getLocalLocation();
				if (npcPoint != null && npcPoint.equals(localPoint))
				{
					return true;
				}
			}
		}

		return false;
	}

	private boolean shouldRefreshCache(Renderable renderable, BillboardCacheKey cacheKey, Rectangle bounds)
	{
		CachedBillboard cached = billboardCache.get(renderable);
		if (cached == null)
		{
			return true;
		}

		boolean sizeChanged = cached.bounds.width != bounds.width || cached.bounds.height != bounds.height;
		if (sizeChanged)
		{
			return true;
		}

		if (cached.key.equals(cacheKey))
		{
			return false;
		}

		return true;
	}

	private BufferedImage renderBillboardImage(List<FaceDraw> faces, Rectangle bounds, int outlinePadding)
	{
		Rectangle imageBounds = expandedBounds(bounds, outlinePadding);
		double qualityScale = renderQualityScale();
		int imageWidth = Math.max(1, (int) Math.round(imageBounds.width * qualityScale));
		int imageHeight = Math.max(1, (int) Math.round(imageBounds.height * qualityScale));
		if (!isUsableDrawSize(imageWidth, imageHeight))
		{
			return null;
		}

		BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D imageGraphics = image.createGraphics();
		try
		{
			imageGraphics.scale(qualityScale, qualityScale);
			imageGraphics.translate(-imageBounds.x, -imageBounds.y);
			for (FaceDraw face : faces)
			{
				//imageGraphics.setColor(face.getColor());
				imageGraphics.setColor(NpcSnapColorBanding.snapToRamp(face.getColor(), config.billboardColorBands()));
				imageGraphics.fillPolygon(face.getPolygon());
			}
		}
		finally
		{
			imageGraphics.dispose();
		}

		if (hasAnyEdgeEffect())
		{
			BillboardOutlineRenderer.applyOutline(
				image,
				config.enableBillboardHighlightOutline(),
				config.enableBillboardShadowOutline(),
				config.enableBillboardSpriteOutline(),
				config.enableBillboardHighlightInline(),
				config.enableBillboardShadowInline(),
				config.enableBillboardSpriteInline(),
				config.billboardSpriteOutlineColor()
			);
		}

		//disabled quantize for now
		//quantize(image, config.billboardPaletteSize());
		return image;
	}

	private static Rectangle expandedBounds(Rectangle bounds, int padding)
	{
		if (padding <= 0)
		{
			return bounds;
		}

		return new Rectangle(bounds.x - padding, bounds.y - padding, bounds.width + (padding * 2), bounds.height + (padding * 2));
	}

	private static boolean isUsableSourceBounds(Rectangle bounds)
	{
		return bounds.width > 0
			&& bounds.height > 0
			&& bounds.width <= MAX_SOURCE_BILLBOARD_SIZE
			&& bounds.height <= MAX_SOURCE_BILLBOARD_SIZE
			&& Math.abs(bounds.x) <= MAX_SOURCE_BILLBOARD_COORDINATE
			&& Math.abs(bounds.y) <= MAX_SOURCE_BILLBOARD_COORDINATE;
	}

	private static boolean isUsableDistance(double distance)
	{
		return Double.isFinite(distance) && distance >= 1.0d;
	}

	private static int scaledSize(int sourceSize, double scale)
	{
		if (!Double.isFinite(scale) || scale <= 0.0d)
		{
			return -1;
		}

		double scaled = sourceSize * scale;
		if (!Double.isFinite(scaled) || scaled <= 0.0d || scaled > MAX_DRAW_BILLBOARD_SIZE)
		{
			return -1;
		}

		return Math.max(1, (int) Math.round(scaled));
	}

	private static int projectedHeight(Point basePoint, Point topPoint)
	{
		if (basePoint == null || topPoint == null)
		{
			return 0;
		}

		long height = Math.abs((long) basePoint.getY() - topPoint.getY());
		if (height > MAX_DRAW_BILLBOARD_SIZE)
		{
			return -1;
		}

		return (int) height;
	}

	private static int aspectWidth(Rectangle bounds, int targetHeight)
	{
		if (targetHeight <= 0 || bounds.height <= 0)
		{
			return -1;
		}

		double width = bounds.width * (targetHeight / (double) bounds.height);
		if (!Double.isFinite(width) || width <= 0.0d || width > MAX_DRAW_BILLBOARD_SIZE)
		{
			return -1;
		}

		return Math.max(1, (int) Math.round(width));
	}

	private static boolean isUsableDrawSize(int width, int height)
	{
		return isUsableDrawDimension(width) && isUsableDrawDimension(height);
	}

	private static boolean isUsableDrawDimension(int dimension)
	{
		return dimension > 0 && dimension <= MAX_DRAW_BILLBOARD_SIZE;
	}

	private static boolean isUsableCanvasCoordinate(int coordinate)
	{
		return Math.abs(coordinate) <= MAX_CANVAS_COORDINATE;
	}
	
	private double renderQualityScale()
	{
		return Math.max(MIN_RENDER_QUALITY, Math.min(1.0d, config.renderBillboardQuality() / 100.0d));
	}

	private int qualityKey()
	{
		return (int) Math.round(renderQualityScale() * 10_000.0d);
	}

	private double cameraDistance(Actor actor, LocalPoint localPoint, double verticalOffset)
	{
		double dx = localPoint.getX() - client.getCameraFpX();
		double dy = localPoint.getY() - client.getCameraFpY();
		double groundHeight = Perspective.getTileHeight(client, localPoint, actor.getWorldView().getPlane());
		double dz = (groundHeight + verticalOffset) - client.getCameraFpZ();
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	private double cameraDistance(int x, int y, double z)
	{
		double dx = x - client.getCameraFpX();
		double dy = y - client.getCameraFpY();
		double dz = z - client.getCameraFpZ();
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	private double cameraDistance(LocalPoint localPoint, int plane, double verticalOffset)
	{
		double dx = localPoint.getX() - client.getCameraFpX();
		double dy = localPoint.getY() - client.getCameraFpY();
		double groundHeight = Perspective.getTileHeight(client, localPoint, plane);
		double dz = (groundHeight + verticalOffset) - client.getCameraFpZ();
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	private double billboardDepth(NPC npc)
	{
		LocalPoint localPoint = npc.getLocalLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		return cameraDistance(npc, localPoint, Math.max(0, npc.getAnimationHeightOffset()) + (npc.getModelHeight() / 2.0));
	}

	private double billboardDepth(Player player)
	{
		LocalPoint localPoint = player.getLocalLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		return cameraDistance(player, localPoint, Math.max(0, player.getAnimationHeightOffset()) + (player.getModelHeight() / 2.0));
	}

	private double billboardDepth(Projectile projectile)
	{
		return cameraDistance(projectileLocalPoint(projectile), projectile.getFloor(), projectileVerticalOffset(projectile) + (projectile.getModelHeight() / 2.0));
	}

	private double billboardDepth(GraphicsObject graphicsObject)
	{
		LocalPoint localPoint = graphicsObject.getLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		return cameraDistance(localPoint, graphicsObject.getLevel(), Math.max(0, graphicsObject.getZ()) + (graphicsObject.getModelHeight() / 2.0));
	}

	private double billboardDepth(TileItem item)
	{
		GroundItemBillboard groundItem = groundItems.get(item);
		if (groundItem == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		return cameraDistance(groundItem.localPoint, groundItem.plane, item.getModelHeight() / 2.0);
	}

	private double billboardDepth(ObservedTileObject observed)
	{
		double nearest = Double.POSITIVE_INFINITY;
		for (ObjectRenderablePart part : observed.parts)
		{
			if (part.localPoint == null || part.renderable == null)
			{
				continue;
			}

			double distance = cameraDistance(part.localPoint, part.plane, part.renderable.getModelHeight() / 2.0);
			if (distance < nearest)
			{
				nearest = distance;
			}
		}

		return Double.isFinite(nearest) ? nearest : Double.NEGATIVE_INFINITY;
	}

	private boolean isWithinRadius(LocalPoint source, LocalPoint target, int radiusTiles)
	{
		int dx = source.getX() - target.getX();
		int dy = source.getY() - target.getY();
		int radius = radiusTiles * LOCAL_TILE_SIZE;
		return (dx * dx) + (dy * dy) <= (radius * radius);
	}

	private boolean isInsideViewport(int x, int y)
	{
		int viewportX = client.getViewportXOffset();
		int viewportY = client.getViewportYOffset();
		int viewportWidth = client.getViewportWidth();
		int viewportHeight = client.getViewportHeight();
		return x >= viewportX && x < viewportX + viewportWidth && y >= viewportY && y < viewportY + viewportHeight;
	}

	private boolean isOutsideViewport(int x, int y, int width, int height)
	{
		int viewportX = client.getViewportXOffset();
		int viewportY = client.getViewportYOffset();
		int viewportWidth = client.getViewportWidth();
		int viewportHeight = client.getViewportHeight();
		return (long) x + width < viewportX
			|| x > (long) viewportX + viewportWidth
			|| (long) y + height < viewportY
			|| y > (long) viewportY + viewportHeight;
	}

	private Rectangle getViewportBounds()
	{
		return new Rectangle(
			client.getViewportXOffset(),
			client.getViewportYOffset(),
			client.getViewportWidth(),
			client.getViewportHeight()
		);
	}

	private List<FaceDraw> buildFaces(
		Model model,
		float[] spriteX,
		float[] spriteY,
		float[] spriteDepth
	)
	{
		List<FaceDraw> faces = new ArrayList<>(model.getFaceCount());
		int[] faceIndices1 = model.getFaceIndices1();
		int[] faceIndices2 = model.getFaceIndices2();
		int[] faceIndices3 = model.getFaceIndices3();
		int[] faceColors1 = model.getFaceColors1();
		int[] faceColors2 = model.getFaceColors2();
		int[] faceColors3 = model.getFaceColors3();
		short[] unlitFaceColors = model.getUnlitFaceColors();
		byte[] transparencies = model.getFaceTransparencies();
		short[] textures = model.getFaceTextures();

		for (int face = 0; face < model.getFaceCount(); face++)
		{
			if (textures != null && face < textures.length && textures[face] != -1)
			{
				continue;
			}

			int a = faceIndices1[face];
			int b = faceIndices2[face];
			int c = faceIndices3[face];

			Polygon polygon = new Polygon(
				new int[]{Math.round(spriteX[a]), Math.round(spriteX[b]), Math.round(spriteX[c])},
				new int[]{Math.round(spriteY[a]), Math.round(spriteY[b]), Math.round(spriteY[c])},
				3
			);

			int alpha = transparencies == null || face >= transparencies.length ? 255 : 255 - (transparencies[face] & 0xFF);
			Color color = applyLightBoost(resolveFaceColor(face, faceColors1, faceColors2, faceColors3, unlitFaceColors, alpha));

			double depth = (
				spriteDepth[a] +
				spriteDepth[b] +
				spriteDepth[c]
			) / 3.0;

			faces.add(new FaceDraw(polygon, color, depth));
		}

		return faces;
	}

	private static Color resolveFaceColor(
		int face,
		int[] faceColors1,
		int[] faceColors2,
		int[] faceColors3,
		short[] unlitFaceColors,
		int alpha
	)
	{
		if (USE_UNLIT_COLORS && unlitFaceColors != null && face < unlitFaceColors.length && unlitFaceColors[face] != -1)
		{
			// The software billboard path has no lighting stage, so unlit face colors
			// should be unpacked directly and used as the full-bright polygon color.
			return packedHslToColor(Short.toUnsignedInt(unlitFaceColors[face]), alpha);
		}

		Color vertexA = packedHslToColor(faceColors1[face], alpha);
		Color vertexB = packedHslToColor(faceColors2[face], alpha);
		Color vertexC = packedHslToColor(faceColors3[face], alpha);
		int red = (vertexA.getRed() + vertexB.getRed() + vertexC.getRed()) / 3;
		int green = (vertexA.getGreen() + vertexB.getGreen() + vertexC.getGreen()) / 3;
		int blue = (vertexA.getBlue() + vertexB.getBlue() + vertexC.getBlue()) / 3;
		return new Color(red, green, blue, alpha);
	}

	private static Rectangle computeBounds(List<FaceDraw> faces)
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;

		for (FaceDraw face : faces)
		{
			Polygon polygon = face.getPolygon();
			for (int i = 0; i < polygon.npoints; i++)
			{
				minX = Math.min(minX, polygon.xpoints[i]);
				minY = Math.min(minY, polygon.ypoints[i]);
				maxX = Math.max(maxX, polygon.xpoints[i]);
				maxY = Math.max(maxY, polygon.ypoints[i]);
			}
		}

		if (minX == Integer.MAX_VALUE)
		{
			return new Rectangle();
		}

		return new Rectangle(minX, minY, Math.max(1, (maxX - minX) + 1), Math.max(1, (maxY - minY) + 1));
	}

	private static Rectangle actorHullBounds(Actor actor)
	{
		try
		{
			Shape hull = actor.getConvexHull();
			return hull != null ? hull.getBounds() : null;
		}
		catch (AssertionError ex)
		{
			return null;
		}
	}

	private static void quantize(BufferedImage image, int paletteSize)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
		int[] palette = buildPalette(pixels, Math.max(2, paletteSize));
		if (palette.length == 0)
		{
			return;
		}

		float[] redError = new float[pixels.length];
		float[] greenError = new float[pixels.length];
		float[] blueError = new float[pixels.length];

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int index = (y * width) + x;
				int argb = pixels[index];
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha == 0)
				{
					pixels[index] = TRANSPARENT;
					continue;
				}

				float sourceR = clampChannel(((argb >> 16) & 0xFF) + redError[index]);
				float sourceG = clampChannel(((argb >> 8) & 0xFF) + greenError[index]);
				float sourceB = clampChannel((argb & 0xFF) + blueError[index]);
				int nearest = nearestColor(palette, sourceR, sourceG, sourceB);
				pixels[index] = (alpha << 24) | nearest;

				float errorR = sourceR - ((nearest >> 16) & 0xFF);
				float errorG = sourceG - ((nearest >> 8) & 0xFF);
				float errorB = sourceB - (nearest & 0xFF);

				diffuse(width, height, x, y, redError, greenError, blueError, errorR, errorG, errorB);
			}
		}

		image.setRGB(0, 0, width, height, pixels, 0, width);
	}

	private static int[] buildPalette(int[] pixels, int paletteSize)
	{
		Map<Integer, Integer> histogram = new HashMap<>();
		for (int pixel : pixels)
		{
			int alpha = (pixel >>> 24) & 0xFF;
			if (alpha == 0)
			{
				continue;
			}

			int bucket = quantizeBucket(pixel);
			histogram.merge(bucket, 1, Integer::sum);
		}

		if (histogram.isEmpty())
		{
			return new int[0];
		}

		return histogram.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.limit(paletteSize)
			.mapToInt(entry -> bucketToColor(entry.getKey()))
			.toArray();
	}

	private static int quantizeBucket(int argb)
	{
		int red = (argb >> 16) & 0xFF;
		int green = (argb >> 8) & 0xFF;
		int blue = argb & 0xFF;
		int r = red >> 3;
		int g = green >> 3;
		int b = blue >> 3;
		return (r << 10) | (g << 5) | b;
	}

	private static int bucketToColor(int bucket)
	{
		int red = ((bucket >> 10) & 0x1F) * 255 / 31;
		int green = ((bucket >> 5) & 0x1F) * 255 / 31;
		int blue = (bucket & 0x1F) * 255 / 31;
		return (red << 16) | (green << 8) | blue;
	}

	private static int nearestColor(int[] palette, float red, float green, float blue)
	{
		int nearest = palette[0];
		float bestDistance = Float.MAX_VALUE;

		for (int color : palette)
		{
			float dr = red - ((color >> 16) & 0xFF);
			float dg = green - ((color >> 8) & 0xFF);
			float db = blue - (color & 0xFF);
			float distance = (dr * dr) + (dg * dg) + (db * db);
			if (distance < bestDistance)
			{
				bestDistance = distance;
				nearest = color;
			}
		}

		return nearest;
	}

	private static void diffuse(
		int width,
		int height,
		int x,
		int y,
		float[] redError,
		float[] greenError,
		float[] blueError,
		float errorR,
		float errorG,
		float errorB
	)
	{
		applyError(width, height, x + 1, y, redError, greenError, blueError, errorR, errorG, errorB, 7.0f / 16.0f);
		applyError(width, height, x - 1, y + 1, redError, greenError, blueError, errorR, errorG, errorB, 3.0f / 16.0f);
		applyError(width, height, x, y + 1, redError, greenError, blueError, errorR, errorG, errorB, 5.0f / 16.0f);
		applyError(width, height, x + 1, y + 1, redError, greenError, blueError, errorR, errorG, errorB, 1.0f / 16.0f);
	}

	private static void applyError(
		int width,
		int height,
		int x,
		int y,
		float[] redError,
		float[] greenError,
		float[] blueError,
		float errorR,
		float errorG,
		float errorB,
		float weight
	)
	{
		if (x < 0 || y < 0 || x >= width || y >= height)
		{
			return;
		}

		int index = (y * width) + x;
		redError[index] += errorR * weight;
		greenError[index] += errorG * weight;
		blueError[index] += errorB * weight;
	}

	private static float clampChannel(float channel)
	{
		return Math.max(0.0f, Math.min(255.0f, channel));
	}

	private BillboardRenderResult renderRenderableBillboard(Graphics2D graphics, BillboardRenderRequest request)
	{
		Renderable renderable = request.renderable;
		Model model = request.model;
		LocalPoint localPoint = request.localPoint;
		if (model == null || localPoint == null)
		{
			return null;
		}

		int vertexCount = model.getVerticesCount();
		if (vertexCount <= 0)
		{
			return null;
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		float[] spriteX = new float[vertexCount];
		float[] spriteY = new float[vertexCount];
		float[] spriteDepth = new float[vertexCount];
		for (int i = 0; i < vertexCount; i++)
		{
			double[] yawRotated = rotateYaw(verticesX[i], verticesZ[i], request.relativeYaw);
			double[] pitchRotated = rotatePitch(verticesY[i], yawRotated[1], request.relativePitch);
			spriteX[i] = (float) yawRotated[0];
			spriteY[i] = (float) pitchRotated[0];
			spriteDepth[i] = (float) pitchRotated[1];
		}

		List<FaceDraw> faces = buildFaces(model, spriteX, spriteY, spriteDepth);
		if (faces.isEmpty())
		{
			return null;
		}

		faces.sort(Comparator.comparingDouble(FaceDraw::getDepth).reversed());
		Rectangle sourceBounds = computeBounds(faces);
		if (!isUsableSourceBounds(sourceBounds))
		{
			return null;
		}

		int outlinePadding = outlinePadding();
		Rectangle imageBounds = expandedBounds(sourceBounds, outlinePadding);

		BillboardCacheKey cacheKey = new BillboardCacheKey(
			request.animationId,
			request.animationFrame,
			request.poseAnimationId,
			request.poseAnimationFrame,
			request.relativeYaw,
			request.relativePitch,
			config.billboardColorBands(),
			config.billboardLightBoostPercent(),
			outlinePadding,
			config.enableBillboardHighlightOutline(),
			config.enableBillboardShadowOutline(),
			config.enableBillboardSpriteOutline(),
			config.enableBillboardHighlightInline(),
			config.enableBillboardShadowInline(),
			config.enableBillboardSpriteInline(),
			config.billboardSpriteOutlineColor().getRGB(),
			qualityKey()
		);
		CachedBillboard cached = billboardCache.get(renderable);
		boolean cacheInvalidated = shouldRefreshCache(renderable, cacheKey, imageBounds);
		boolean spriteRedrawn = false;
		if (cacheInvalidated)
		{
			BufferedImage image = renderBillboardImage(faces, sourceBounds, outlinePadding);
			if (image == null)
			{
				return null;
			}

			cached = new CachedBillboard(cacheKey, imageBounds, image);
			billboardCache.put(renderable, cached);
			spriteRedrawn = true;
		}

		Point basePoint = Perspective.localToCanvas(client, localPoint, request.plane, request.verticalOffset);
		Point topPoint = Perspective.localToCanvas(client, localPoint, request.plane, request.verticalOffset + renderable.getModelHeight());
		Rectangle hullBounds = request.hullBounds;
		if (basePoint == null)
		{
			return null;
		}

		double distance = cameraDistance(localPoint, request.plane, request.verticalOffset + (renderable.getModelHeight() / 2.0));
		if (!isUsableDistance(distance))
		{
			return null;
		}

		double perspectiveScale = client.get3dZoom() / Math.max(1.0, distance);
		int distanceHeight = scaledSize(cached.bounds.height, perspectiveScale);
		int projectedHeight = projectedHeight(basePoint, topPoint);
		int hullHeight = hullBounds != null && isUsableDrawDimension(hullBounds.height) ? hullBounds.height : 0;
		int targetHeight = hullHeight > 0
			? hullHeight
			: distanceHeight > 0 ? distanceHeight : projectedHeight;
		int targetWidth = aspectWidth(cached.bounds, targetHeight);
		int anchorX = hullBounds != null ? hullBounds.x + (hullBounds.width / 2) : basePoint.getX();
		int anchorY = hullBounds != null ? hullBounds.y + hullBounds.height : basePoint.getY();
		if (!isUsableCanvasCoordinate(anchorX) || !isUsableCanvasCoordinate(anchorY))
		{
			return null;
		}

		int drawX = anchorX - (targetWidth / 2);
		int drawY = request.verticalAnchor == VerticalAnchor.CENTER
			? anchorY - (targetHeight / 2)
			: anchorY - targetHeight;
		if (!isUsableDrawSize(targetWidth, targetHeight) || isOutsideViewport(drawX, drawY, targetWidth, targetHeight))
		{
			return null;
		}

		Rectangle drawRect = new Rectangle(drawX, drawY, targetWidth, targetHeight);
		graphics.drawImage(cached.image, drawRect.x, drawRect.y, drawRect.width, drawRect.height, null);
		return new BillboardRenderResult(drawRect, cacheInvalidated, spriteRedrawn);
	}

	private int outlinePadding()
	{
		return config.enableBillboardShadowOutline() || config.enableBillboardSpriteOutline()
			? OUTLINE_PADDING
			: 0;
	}

	private boolean hasAnyEdgeEffect()
	{
		return config.enableBillboardHighlightOutline()
			|| config.enableBillboardShadowOutline()
			|| config.enableBillboardSpriteOutline()
			|| config.enableBillboardHighlightInline()
			|| config.enableBillboardShadowInline()
			|| config.enableBillboardSpriteInline();
	}

	private int snapFrame(net.runelite.api.Animation animation, int frame, boolean enabled)
	{
		return animationFrameSnapper.snapFrame(animation, frame, enabled, Math.max(1, config.animationFrameCount()));
	}

	private int relativeYaw()
	{
		int rawRelativeYaw = client.getCameraYaw();
		if (!config.enableRotationSnapping())
		{
			return Math.floorMod(rawRelativeYaw, FULL_CIRCLE);
		}

		return snapJauByAngles(rawRelativeYaw, config.numberOfYawRotationAngles());
	}

	private ObservedTileObject buildObservedTileObject(TileObject tileObject)
	{
		if (tileObject == null || tileObject.getLocalLocation() == null)
		{
			return null;
		}

		List<ObjectRenderablePart> parts = new ArrayList<>(2);
		if (tileObject instanceof GameObject)
		{
			addObjectRenderablePart(parts, ((GameObject) tileObject).getRenderable(), tileObject.getLocalLocation(), tileObject.getPlane());
		}
		else if (tileObject instanceof GroundObject)
		{
			addObjectRenderablePart(parts, ((GroundObject) tileObject).getRenderable(), tileObject.getLocalLocation(), tileObject.getPlane());
		}
		else if (tileObject instanceof WallObject)
		{
			WallObject wallObject = (WallObject) tileObject;
			addObjectRenderablePart(parts, wallObject.getRenderable1(), tileObject.getLocalLocation(), tileObject.getPlane());
			addObjectRenderablePart(parts, wallObject.getRenderable2(), tileObject.getLocalLocation(), tileObject.getPlane());
		}
		else if (tileObject instanceof DecorativeObject)
		{
			DecorativeObject decorativeObject = (DecorativeObject) tileObject;
			addObjectRenderablePart(parts, decorativeObject.getRenderable(), offsetLocalPoint(tileObject.getLocalLocation(), decorativeObject.getXOffset(), decorativeObject.getYOffset()), tileObject.getPlane());
			addObjectRenderablePart(parts, decorativeObject.getRenderable2(), offsetLocalPoint(tileObject.getLocalLocation(), decorativeObject.getXOffset2(), decorativeObject.getYOffset2()), tileObject.getPlane());
		}

		return parts.isEmpty() ? null : new ObservedTileObject(tileObject, parts);
	}

	private void addObjectRenderablePart(List<ObjectRenderablePart> parts, Renderable renderable, LocalPoint localPoint, int plane)
	{
		if (renderable == null || localPoint == null
			|| renderable instanceof Actor
			|| renderable instanceof Projectile
			|| renderable instanceof GraphicsObject
			|| renderable instanceof TileItem)
		{
			return;
		}

		parts.add(new ObjectRenderablePart(renderable, localPoint, plane));
	}

	private static LocalPoint offsetLocalPoint(LocalPoint base, int xOffset, int yOffset)
	{
		return base == null ? null : new LocalPoint(base.getX() + xOffset, base.getY() + yOffset);
	}

	private int relativeYaw(Actor actor)
	{
		//int rawRelativeYaw = actor.getCurrentOrientation() - client.getCameraYaw();
		int rawRelativeYaw = client.getCameraYaw() + actor.getCurrentOrientation();
		if (shouldCombatSnap(actor))
		{
			return combatYaw(rawRelativeYaw);
		}

		if (!config.enableRotationSnapping())
		{
			return Math.floorMod(rawRelativeYaw, FULL_CIRCLE);
		}

		return snapJauByAngles(rawRelativeYaw, config.numberOfYawRotationAngles());
	}

	private int relativeYaw(Projectile projectile)
	{
		//int rawRelativeYaw = projectile.getOrientation() - client.getCameraYaw();
		int rawRelativeYaw = client.getCameraYaw() - projectile.getOrientation();
		if (!config.enableRotationSnapping())
		{
			return Math.floorMod(rawRelativeYaw, FULL_CIRCLE);
		}

		return snapJauByAngles(rawRelativeYaw, config.numberOfYawRotationAngles());
	}

	private int relativeYaw(TileItem item)
	{
		int rawRelativeYaw = client.getCameraYaw();
		if (!config.enableRotationSnapping())
		{
			return Math.floorMod(rawRelativeYaw, FULL_CIRCLE);
		}

		return snapJauByAngles(rawRelativeYaw, config.numberOfYawRotationAngles());
	}

	private boolean shouldCombatSnap(Actor actor)
	{
		return config.enableBillboardCombatSnapping() && isMutuallyInteracting(actor);
	}

	private boolean isMutuallyInteracting(Actor actor)
	{
		if (actor == null)
		{
			return false;
		}

		Actor interacting = actor.getInteracting();
		return interacting != null && interacting.getInteracting() == actor;
	}

	private static int combatYaw(int rawRelativeYaw)
	{
		int normalized = Math.floorMod(rawRelativeYaw, FULL_CIRCLE);
		int combatDistance = jauDistance(normalized, COMBAT_YAW);
		int oppositeDistance = jauDistance(normalized, OPPOSITE_COMBAT_YAW);
		return combatDistance <= oppositeDistance ? COMBAT_YAW : OPPOSITE_COMBAT_YAW;
	}

	private static int jauDistance(int a, int b)
	{
		int distance = Math.abs(Math.floorMod(a, FULL_CIRCLE) - Math.floorMod(b, FULL_CIRCLE));
		return Math.min(distance, FULL_CIRCLE - distance);
	}

	private int relativePitch()
	{
		int rawPitch = Math.max(0, Math.min(MAX_PITCH, client.getCameraPitch()));
		if (!config.enableRotationSnapping())
		{
			return rawPitch;
		}

		int res = snapPitchByAngles(rawPitch, 0, config.numberOfPitchRotationAngles());
		//invert as NPCs rotate negatively to look upwards
		return -res;
	}

	private int relativePitch(Actor actor)
	{
		if (shouldCombatSnap(actor))
		{
			return 0;
		}

		return relativePitch();
	}

	private static int snapJauByAngles(int jau, int angleCount)
	{
		int clampedAngleCount = Math.max(1, angleCount);
		int step = Math.max(1, FULL_CIRCLE / clampedAngleCount);
		int normalized = Math.floorMod(jau, FULL_CIRCLE);
		return Math.floorMod(((normalized + (step / 2)) / step) * step, FULL_CIRCLE);
	}

	private static int snapPitchByAngles(int pitch, int minPitch, int angleCount)
	{
		int clampedMinPitch = Math.max(0, Math.min(minPitch, MAX_PITCH));
		int clampedPitch = Math.max(clampedMinPitch, Math.min(pitch, MAX_PITCH));
		int clampedAngleCount = Math.max(1, angleCount);
		int pitchRange = Math.max(1, MAX_PITCH - clampedMinPitch);
		if (clampedAngleCount == 1)
		{
			return clampedMinPitch;
		}

		int snappedIndex = Math.max(0, Math.min(clampedAngleCount - 1, ((clampedPitch - clampedMinPitch) * clampedAngleCount) / pitchRange));
		if (snappedIndex == 0)
		{
			return clampedMinPitch;
		}
		if (snappedIndex == clampedAngleCount - 1)
		{
			return MAX_PITCH;
		}

		int snappedPitch = clampedMinPitch + (int) Math.round(((snappedIndex + 0.5d) * pitchRange) / clampedAngleCount);
		return Math.max(clampedMinPitch, Math.min(MAX_PITCH, snappedPitch));
	}

	private Color applyLightBoost(Color color)
	{
		float boost = config.billboardLightBoostPercent() / 100.0f;
		int red = Math.min(255, Math.round(color.getRed() * boost));
		int green = Math.min(255, Math.round(color.getGreen() * boost));
		int blue = Math.min(255, Math.round(color.getBlue() * boost));
		return new Color(red, green, blue, color.getAlpha());
	}

	private static double[] rotateYaw(float x, float z, int orientation)
	{
		double sin = Perspective.SINE[orientation] / 65536.0;
		double cos = Perspective.COSINE[orientation] / 65536.0;
		double rotatedX = (x * cos) + (z * sin);
		double rotatedZ = (z * cos) - (x * sin);
		return new double[]{rotatedX, rotatedZ};
	}

	private static double[] rotatePitch(float y, double depth, int pitch)
	{
		int inversePitch = Math.floorMod(-pitch, FULL_CIRCLE);
		double sin = Perspective.SINE[inversePitch] / 65536.0;
		double cos = Perspective.COSINE[inversePitch] / 65536.0;
		double rotatedY = (y * cos) - (depth * sin);
		double rotatedDepth = (depth * cos) + (y * sin);
		return new double[]{rotatedY, rotatedDepth};
	}

	private static Color packedHslToColor(int packedHsl, int alpha)
	{
		int hue = (packedHsl >> 10) & 0x3F;
		int saturation = (packedHsl >> 7) & 0x07;
		int lightness = packedHsl & 0x7F;
		float h = hue / 64.0f;
		float s = saturation / 8.0f;
		float l = lightness / 128.0f;

		float r;
		float g;
		float b;

		if (s == 0.0f)
		{
			r = l;
			g = l;
			b = l;
		}
		else
		{
			float q = l < 0.5f ? l * (1.0f + s) : l + s - (l * s);
			float p = 2.0f * l - q;
			r = hueToRgb(p, q, h + (1.0f / 3.0f));
			g = hueToRgb(p, q, h);
			b = hueToRgb(p, q, h - (1.0f / 3.0f));
		}

		return new Color(clamp(r), clamp(g), clamp(b), Math.max(0, Math.min(255, alpha)));
	}

	private static float hueToRgb(float p, float q, float t)
	{
		if (t < 0)
		{
			t += 1.0f;
		}
		if (t > 1)
		{
			t -= 1.0f;
		}
		if (t < (1.0f / 6.0f))
		{
			return p + ((q - p) * 6.0f * t);
		}
		if (t < 0.5f)
		{
			return q;
		}
		if (t < (2.0f / 3.0f))
		{
			return p + ((q - p) * ((2.0f / 3.0f) - t) * 6.0f);
		}
		return p;
	}

	private static int clamp(float component)
	{
		return Math.max(0, Math.min(255, Math.round(component * 255.0f)));
	}

	private static final class FaceDraw
	{
		private final Polygon polygon;
		private final Color color;
		private final double depth;

		private FaceDraw(Polygon polygon, Color color, double depth)
		{
			this.polygon = polygon;
			this.color = color;
			this.depth = depth;
		}

		private Polygon getPolygon()
		{
			return polygon;
		}

		private Color getColor()
		{
			return color;
		}

		private double getDepth()
		{
			return depth;
		}
	}

	private static final class BillboardCacheKey
	{
		private final int animationId;
		private final int animationFrame;
		private final int poseAnimationId;
		private final int poseAnimationFrame;
		private final int relativeYaw;
		private final int relativePitch;
		private final int colorBands;
		private final int lightBoost;
		private final int outlinePadding;
		private final boolean highlightOutline;
		private final boolean shadowOutline;
		private final boolean solidOutline;
		private final boolean highlightInline;
		private final boolean shadowInline;
		private final boolean solidInline;
		private final int outlineColor;
		private final int renderQuality;

		private BillboardCacheKey(
			int animationId,
			int animationFrame,
			int poseAnimationId,
			int poseAnimationFrame,
			int relativeYaw,
			int relativePitch,
			int colorBands,
			int lightBoost,
			int outlinePadding,
			boolean highlightOutline,
			boolean shadowOutline,
			boolean solidOutline,
			boolean highlightInline,
			boolean shadowInline,
			boolean solidInline,
			int outlineColor,
			int renderQuality)
		{
			this.animationId = animationId;
			this.animationFrame = animationFrame;
			this.poseAnimationId = poseAnimationId;
			this.poseAnimationFrame = poseAnimationFrame;
			this.relativeYaw = relativeYaw;
			this.relativePitch = relativePitch;
			this.colorBands = colorBands;
			this.lightBoost = lightBoost;
			this.outlinePadding = outlinePadding;
			this.highlightOutline = highlightOutline;
			this.shadowOutline = shadowOutline;
			this.solidOutline = solidOutline;
			this.highlightInline = highlightInline;
			this.shadowInline = shadowInline;
			this.solidInline = solidInline;
			this.outlineColor = outlineColor;
			this.renderQuality = renderQuality;
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (!(o instanceof BillboardCacheKey))
			{
				return false;
			}
			BillboardCacheKey that = (BillboardCacheKey) o;
			return animationId == that.animationId
				&& animationFrame == that.animationFrame
				&& poseAnimationId == that.poseAnimationId
				&& poseAnimationFrame == that.poseAnimationFrame
				&& relativeYaw == that.relativeYaw
				&& relativePitch == that.relativePitch
				&& colorBands == that.colorBands
				&& lightBoost == that.lightBoost
				&& outlinePadding == that.outlinePadding
				&& highlightOutline == that.highlightOutline
				&& shadowOutline == that.shadowOutline
				&& solidOutline == that.solidOutline
				&& highlightInline == that.highlightInline
				&& shadowInline == that.shadowInline
				&& solidInline == that.solidInline
				&& outlineColor == that.outlineColor
				&& renderQuality == that.renderQuality;
		}

		@Override
		public int hashCode()
		{
			int result = animationId;
			result = 31 * result + animationFrame;
			result = 31 * result + poseAnimationId;
			result = 31 * result + poseAnimationFrame;
			result = 31 * result + relativeYaw;
			result = 31 * result + relativePitch;
			result = 31 * result + colorBands;
			result = 31 * result + lightBoost;
			result = 31 * result + outlinePadding;
			result = 31 * result + (highlightOutline ? 1 : 0);
			result = 31 * result + (shadowOutline ? 1 : 0);
			result = 31 * result + (solidOutline ? 1 : 0);
			result = 31 * result + (highlightInline ? 1 : 0);
			result = 31 * result + (shadowInline ? 1 : 0);
			result = 31 * result + (solidInline ? 1 : 0);
			result = 31 * result + outlineColor;
			result = 31 * result + renderQuality;
			return result;
		}
	}

	private static final class CachedBillboard
	{
		private final BillboardCacheKey key;
		private final Rectangle bounds;
		private final BufferedImage image;

		private CachedBillboard(BillboardCacheKey key, Rectangle bounds, BufferedImage image)
		{
			this.key = key;
			this.bounds = new Rectangle(bounds);
			this.image = image;
		}
	}

	private static final class GroundItemBillboard
	{
		private final int plane;
		private final LocalPoint localPoint;

		private GroundItemBillboard(int plane, LocalPoint localPoint)
		{
			this.plane = plane;
			this.localPoint = localPoint;
		}
	}

	private enum BillboardTargetType
	{
		NPC,
		PLAYER,
		PROJECTILE,
		GRAPHICS_OBJECT,
		GROUND_ITEM,
		TILE_OBJECT
	}

	private enum VerticalAnchor
	{
		BOTTOM,
		CENTER
	}

	private static final class BillboardTarget
	{
		private final BillboardTargetType type;
		private final Renderable renderable;
		private final TileObject tileObject;
		private final ObservedTileObject observedTileObject;
		private final GroundItemBillboard groundItem;
		private final double depth;
		private final int renderPriority;
		private final PriorityTileKey priorityTileKey;

		private BillboardTarget(
			BillboardTargetType type,
			Renderable renderable,
			TileObject tileObject,
			ObservedTileObject observedTileObject,
			GroundItemBillboard groundItem,
			double depth,
			int renderPriority,
			PriorityTileKey priorityTileKey
		)
		{
			this.type = type;
			this.renderable = renderable;
			this.tileObject = tileObject;
			this.observedTileObject = observedTileObject;
			this.groundItem = groundItem;
			this.depth = depth;
			this.renderPriority = renderPriority;
			this.priorityTileKey = priorityTileKey;
		}

		private static BillboardTarget forRenderable(
			BillboardTargetType type,
			Renderable renderable,
			double depth,
			int renderPriority,
			LocalPoint localPoint,
			int plane
		)
		{
			return new BillboardTarget(type, renderable, null, null, null, depth, renderPriority, PriorityTileKey.of(localPoint, plane, renderPriority));
		}

		private static BillboardTarget forGroundItem(TileItem item, GroundItemBillboard groundItem, double depth)
		{
			return new BillboardTarget(
				BillboardTargetType.GROUND_ITEM,
				item,
				null,
				null,
				groundItem,
				depth,
				RENDER_PRIORITY_GROUND_ITEM,
				PriorityTileKey.of(groundItem.localPoint, groundItem.plane, RENDER_PRIORITY_GROUND_ITEM)
			);
		}

		private static BillboardTarget forTileObject(ObservedTileObject observedTileObject, double depth, boolean effectLike)
		{
			int renderPriority = effectLike ? RENDER_PRIORITY_EFFECT : RENDER_PRIORITY_NONE;
			return new BillboardTarget(
				BillboardTargetType.TILE_OBJECT,
				null,
				observedTileObject.tileObject,
				observedTileObject,
				null,
				depth,
				renderPriority,
				PriorityTileKey.of(firstLocalPoint(observedTileObject), firstPlane(observedTileObject), renderPriority)
			);
		}

		private double getDepth()
		{
			return depth;
		}

		private int getRenderPriority()
		{
			return renderPriority;
		}

		private PriorityTileKey getPriorityTileKey()
		{
			return priorityTileKey;
		}

		private static LocalPoint firstLocalPoint(ObservedTileObject observedTileObject)
		{
			if (observedTileObject == null)
			{
				return null;
			}

			for (ObjectRenderablePart part : observedTileObject.parts)
			{
				if (part.localPoint != null)
				{
					return part.localPoint;
				}
			}

			return null;
		}

		private static int firstPlane(ObservedTileObject observedTileObject)
		{
			if (observedTileObject == null)
			{
				return -1;
			}

			for (ObjectRenderablePart part : observedTileObject.parts)
			{
				return part.plane;
			}

			return -1;
		}
	}

	private static final class BillboardTargetGroup
	{
		private final List<BillboardTarget> targets;
		private final double depth;

		private BillboardTargetGroup(List<BillboardTarget> targets, double depth)
		{
			this.targets = targets;
			this.depth = depth;
		}

		private static BillboardTargetGroup single(BillboardTarget target)
		{
			return new BillboardTargetGroup(Collections.singletonList(target), target.getDepth());
		}

		private static BillboardTargetGroup group(List<BillboardTarget> targets)
		{
			double maxDepth = Double.NEGATIVE_INFINITY;
			for (BillboardTarget target : targets)
			{
				maxDepth = Math.max(maxDepth, target.getDepth());
			}

			return new BillboardTargetGroup(targets, maxDepth);
		}

		private double getDepth()
		{
			return depth;
		}
	}

	private static final class PriorityTileKey
	{
		private final int plane;
		private final int tileX;
		private final int tileY;

		private PriorityTileKey(int plane, int tileX, int tileY)
		{
			this.plane = plane;
			this.tileX = tileX;
			this.tileY = tileY;
		}

		private static PriorityTileKey of(LocalPoint localPoint, int plane, int renderPriority)
		{
			if (renderPriority == RENDER_PRIORITY_NONE || localPoint == null || plane < 0)
			{
				return null;
			}

			return new PriorityTileKey(plane, localPoint.getX() / LOCAL_TILE_SIZE, localPoint.getY() / LOCAL_TILE_SIZE);
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}

			if (!(other instanceof PriorityTileKey))
			{
				return false;
			}

			PriorityTileKey that = (PriorityTileKey) other;
			return plane == that.plane && tileX == that.tileX && tileY == that.tileY;
		}

		@Override
		public int hashCode()
		{
			int result = plane;
			result = 31 * result + tileX;
			result = 31 * result + tileY;
			return result;
		}
	}

	private static final class BillboardRenderRequest
	{
		private final Renderable renderable;
		private final Model model;
		private final LocalPoint localPoint;
		private final int plane;
		private final int verticalOffset;
		private final int relativeYaw;
		private final int relativePitch;
		private final int animationId;
		private final int animationFrame;
		private final int poseAnimationId;
		private final int poseAnimationFrame;
		private final Rectangle hullBounds;
		private final VerticalAnchor verticalAnchor;
		private final NpcSnapDebug.FrameDebugInfo frameDebugInfo;

		private BillboardRenderRequest(
			Renderable renderable,
			Model model,
			LocalPoint localPoint,
			int plane,
			int verticalOffset,
			int relativeYaw,
			int relativePitch,
			int animationId,
			int animationFrame,
			int poseAnimationId,
			int poseAnimationFrame,
			Rectangle hullBounds,
			VerticalAnchor verticalAnchor,
			NpcSnapDebug.FrameDebugInfo frameDebugInfo
		)
		{
			this.renderable = renderable;
			this.model = model;
			this.localPoint = localPoint;
			this.plane = plane;
			this.verticalOffset = verticalOffset;
			this.relativeYaw = relativeYaw;
			this.relativePitch = relativePitch;
			this.animationId = animationId;
			this.animationFrame = animationFrame;
			this.poseAnimationId = poseAnimationId;
			this.poseAnimationFrame = poseAnimationFrame;
			this.hullBounds = hullBounds;
			this.verticalAnchor = verticalAnchor;
			this.frameDebugInfo = frameDebugInfo;
		}
	}

	private static final class BillboardRenderResult
	{
		private final Rectangle bounds;
		private final boolean cacheInvalidated;
		private final boolean spriteRedrawn;

		private BillboardRenderResult(Rectangle bounds, boolean cacheInvalidated, boolean spriteRedrawn)
		{
			this.bounds = bounds;
			this.cacheInvalidated = cacheInvalidated;
			this.spriteRedrawn = spriteRedrawn;
		}
	}

	private static final class ObservedTileObject
	{
		private final TileObject tileObject;
		private final List<ObjectRenderablePart> parts;

		private ObservedTileObject(TileObject tileObject, List<ObjectRenderablePart> parts)
		{
			this.tileObject = tileObject;
			this.parts = parts;
		}
	}

	private static final class ObjectRenderablePart
	{
		private final Renderable renderable;
		private final LocalPoint localPoint;
		private final int plane;

		private ObjectRenderablePart(Renderable renderable, LocalPoint localPoint, int plane)
		{
			this.renderable = renderable;
			this.localPoint = localPoint;
			this.plane = plane;
		}
	}

}
