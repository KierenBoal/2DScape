package com.kierenboal.npcsnap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
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
import net.runelite.api.PlayerComposition;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.kit.KitType;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class NpcBillboardOverlay extends Overlay
{
	private static final int FULL_CIRCLE = 2048;
	private static final int MAX_PITCH = 512;
	private static final int LOCAL_TILE_SIZE = 128;
	private static final int COMBAT_YAW = 512;
	private static final int OPPOSITE_COMBAT_YAW = 1536;
	private static final boolean USE_UNLIT_COLORS = true;
	private static final double MIN_RENDER_QUALITY = 0.01d;
	private static final int MAX_SOURCE_BILLBOARD_SIZE = 4096;
	private static final int MAX_SOURCE_BILLBOARD_COORDINATE = 32768;
	private static final int MAX_DRAW_BILLBOARD_SIZE = 8192;
	private static final int MAX_CANVAS_COORDINATE = 1_000_000;
	private static final int OUTLINE_PADDING = 1;
	private static final long CACHE_TTL_MILLIS = 10_000L;
	private static final int[] ANIMATED_TEXTURE_IDS = {
		ItemID.TZHAAR_CAPE_FIRE,
		ItemID.INFERNAL_CAPE
	};
	private static final float ANIMATED_TEXTURE_V_SCROLL_PER_SECOND = -0.25f;
	private static final int RENDER_PRIORITY_NONE = -1;
	private static final int RENDER_PRIORITY_GROUND_ITEM = 0;
	private static final int RENDER_PRIORITY_ACTOR = 1;
	private static final int RENDER_PRIORITY_EFFECT = 2;

	private final Client client;
	private final NpcSnapConfig config;
	private final NpcSnapDebug debug;
	private final AnimationFrameSnapper animationFrameSnapper;
	private final Map<Renderable, CachedBillboard> billboardCache = new HashMap<>();
	private final Map<Integer, TextureCacheEntry> textureCache = new HashMap<>();
	private final Map<TileItem, GroundItemBillboard> groundItems = new HashMap<>();
	private final Map<TileObject, ObservedTileObject> observedTileObjects = new IdentityHashMap<>();
	private final Map<TileObject, ObservedTileObject> visibleTileObjects = new IdentityHashMap<>();
	private final Map<Renderable, BillboardTarget> activeRenderableTargets = new IdentityHashMap<>();
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
			clearActiveState();
			return null;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			clearActiveState();
			return null;
		}

		if (!config.enable2dBillboardSprites())
		{
			clearActiveState();
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
		activeRenderableTargets.remove(item);
		activeBillboards.remove(item);
	}

	void clearGroundItems()
	{
		for (TileItem item : groundItems.keySet())
		{
			billboardCache.remove(item);
			activeRenderableTargets.remove(item);
			activeBillboards.remove(item);
		}

		groundItems.clear();
	}

	private void clearActiveState()
	{
		activeRenderableTargets.clear();
		activeBillboards.clear();
		activeTileObjects.clear();
		clearActiveSnapshots();
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

	void clearTextureCache()
	{
		textureCache.clear();
	}

	void beginFrame()
	{
		expireCaches(System.currentTimeMillis());
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

		drawRenderDebug(graphics, result, request, paintOrder);
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

			drawRenderDebug(graphics, result, request, paintOrder);
		}
	}

	private void drawRenderDebug(Graphics2D graphics, BillboardRenderResult result, BillboardRenderRequest request, int paintOrder)
	{
		NpcSnapDebug.RenderDebug renderDebug = NpcSnapDebug.RenderDebug.forBounds(
			result.bounds,
			paintOrder,
			result.cacheInvalidated,
			result.spriteRedrawn,
			request.frameDebugInfo
		);
		debug.drawBillboardDebugForeground(graphics, renderDebug);
	}

	private List<BillboardTarget> getVisibleTargets(WorldView worldView)
	{
		ensureActiveBillboardsCurrent(worldView);

		List<BillboardTarget> visibleTargets = new ArrayList<>(activeRenderableTargets.size() + activeTileObjects.size());
		for (BillboardTarget target : activeRenderableTargets.values())
		{
			visibleTargets.add(target);
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

		activeRenderableTargets.clear();
		activeBillboards.clear();
		activeTileObjects.clear();
		activeBillboardsGameCycle = gameCycle;
		if (!config.enable2dBillboardSprites() || client.getGameState() != GameState.LOGGED_IN || worldView == null)
		{
			clearActiveSnapshots();
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
				activeRenderableTargets.put(target.renderable, target);
				activeBillboards.add(target.renderable);
			}
			else if (target.tileObject != null)
			{
				activeTileObjects.add(target.tileObject);
			}
		}

		updateActiveSnapshots();
	}

	private void updateActiveSnapshots()
	{
		activeBillboardSnapshot = new HashSet<>(activeBillboards);
		Set<TileObject> tileObjectSnapshot = Collections.newSetFromMap(new IdentityHashMap<>());
		tileObjectSnapshot.addAll(activeTileObjects);
		activeTileObjectSnapshot = tileObjectSnapshot;
	}

	private List<BillboardTarget> collectCandidates(WorldView worldView)
	{
		Player localPlayer = client.getLocalPlayer();
		LocalPoint localPlayerLocation = localPlayer != null ? localPlayer.getLocalLocation() : null;
		Rectangle viewport = getViewportBounds();
		List<BillboardTarget> candidates = new ArrayList<>();
		Set<EffectDedupKey> claimedActorEffects = new HashSet<>();
		Set<OccupiedTileKey> claimedActorEffectTiles = new HashSet<>();

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
				addActorSpotAnimCandidates(candidates, npc, viewport, claimedActorEffects, claimedActorEffectTiles);
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
				addActorSpotAnimCandidates(candidates, player, viewport, claimedActorEffects, claimedActorEffectTiles);
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

				if (claimedActorEffects.contains(EffectDedupKey.of(graphicsObject)))
				{
					continue;
				}

				if (claimedActorEffectTiles.contains(OccupiedTileKey.of(graphicsObject.getLocation(), graphicsObject.getLevel())))
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

				if (effectLike && claimedActorEffectTiles.contains(OccupiedTileKey.of(observedTileLocalPoint(observed), observedTilePlane(observed))))
				{
					continue;
				}

				candidates.add(BillboardTarget.forTileObject(observed, billboardDepth(observed), effectLike));
			}
		}

		return candidates;
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
			case ACTOR_SPOT_ANIM:
				return buildActorSpotAnimRenderRequest((ActorSpotAnim) target.renderable, target.parentActor);
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
			animatedTextureId(actor),
			actorHullBounds(actor),
			VerticalAnchor.BOTTOM,
			debug.actorFrameDebugInfo(actor)
		);
	}

	private BillboardRenderRequest buildActorSpotAnimRenderRequest(ActorSpotAnim actorSpotAnim, Actor actor)
	{
		if (actor == null)
		{
			return null;
		}

		LocalPoint localPoint = actor.getLocalLocation();
		if (localPoint == null)
		{
			return null;
		}

		return new BillboardRenderRequest(
			actorSpotAnim,
			actorSpotAnim.getModel(),
			localPoint,
			actor.getWorldView().getPlane(),
			Math.max(0, actor.getAnimationHeightOffset() + actorSpotAnim.getHeight()),
			relativeYaw(actor),
			relativePitch(),
			actorSpotAnim.getId(),
			actorSpotAnim.getFrame(),
			-1,
			-1,
			-1,
			null,
			VerticalAnchor.BOTTOM,
			NpcSnapDebug.FrameDebugInfo.of(actorSpotAnim.getId(), actorSpotAnim.getFrame(), actorSpotAnim.getFrame())
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
			item.getId(),
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
			case ACTOR_SPOT_ANIM:
			{
				Actor actor = target.parentActor;
				ActorSpotAnim actorSpotAnim = (ActorSpotAnim) target.renderable;
				if (actor == null)
				{
					return;
				}

				debug.drawRenderableBoundingBox(graphics, actorSpotAnim, actor.getLocalLocation(), actor.getWorldView().getPlane(), Math.max(0, actor.getAnimationHeightOffset() + actorSpotAnim.getHeight()));
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

	private void addActorSpotAnimCandidates(
		List<BillboardTarget> candidates,
		Actor actor,
		Rectangle viewport,
		Set<EffectDedupKey> claimedActorEffects,
		Set<OccupiedTileKey> claimedActorEffectTiles
	)
	{
		if (actor == null || !config.applyToGraphicsObjects())
		{
			return;
		}

		Iterable<ActorSpotAnim> spotAnims = actor.getSpotAnims();
		if (spotAnims == null)
		{
			return;
		}

		for (ActorSpotAnim actorSpotAnim : spotAnims)
		{
			if (!isEligibleActorSpotAnim(actor, actorSpotAnim, viewport))
			{
				continue;
			}

			candidates.add(BillboardTarget.forActorSpotAnim(actorSpotAnim, actor, billboardDepth(actorSpotAnim, actor)));
			claimedActorEffects.add(EffectDedupKey.of(actorSpotAnim, actor));
			claimedActorEffectTiles.add(OccupiedTileKey.of(actor.getLocalLocation(), actor.getWorldView().getPlane()));
		}
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

	private boolean isEligibleActorSpotAnim(Actor actor, ActorSpotAnim actorSpotAnim, Rectangle viewport)
	{
		if (actor == null || actorSpotAnim == null || viewport == null)
		{
			return false;
		}

		LocalPoint localPlayerLocation = client.getLocalPlayer() != null ? client.getLocalPlayer().getLocalLocation() : null;
		LocalPoint actorLocation = actor.getLocalLocation();
		if (localPlayerLocation == null || actorLocation == null)
		{
			return false;
		}

		if (!isWithinRadius(localPlayerLocation, actorLocation, config.billboardRadiusTiles()))
		{
			return false;
		}

		if (actorSpotAnim.getModel() == null)
		{
			return false;
		}

		int verticalOffset = Math.max(0, actor.getAnimationHeightOffset() + actorSpotAnim.getHeight());
		Point canvasPoint = Perspective.localToCanvas(client, actorLocation, actor.getWorldView().getPlane(), verticalOffset + (actorSpotAnim.getModelHeight() / 2));
		return canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY());
	}

	private static LocalPoint projectileLocalPoint(Projectile projectile)
	{
		return new LocalPoint((int) projectile.getX(), (int) projectile.getY());
	}

	private static LocalPoint observedTileLocalPoint(ObservedTileObject observed)
	{
		if (observed == null)
		{
			return null;
		}

		for (ObjectRenderablePart part : observed.parts)
		{
			if (part.localPoint != null)
			{
				return part.localPoint;
			}
		}

		return null;
	}

	private static int observedTilePlane(ObservedTileObject observed)
	{
		if (observed == null)
		{
			return -1;
		}

		for (ObjectRenderablePart part : observed.parts)
		{
			return part.plane;
		}

		return -1;
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

	private boolean shouldRefreshCache(Renderable renderable, BillboardCacheKey cacheKey, Rectangle bounds, long nowMillis)
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

		if (nowMillis - cached.lastRedrawMillis() > CACHE_TTL_MILLIS)
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
			int[] imagePixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
			for (FaceDraw face : faces)
			{
				if (face.isTextured())
				{
					rasterizeTexturedFace(imagePixels, imageWidth, imageHeight, imageBounds, qualityScale, face);
					continue;
				}

				imageGraphics.setColor(NpcSnapColorBanding.snapToRamp(face.getColor(), config.billboardColorBands()));
				imageGraphics.fillPolygon(scalePolygon(face.getPolygon(), imageBounds, qualityScale));
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

	private static boolean isBackFace(float[] spriteX, float[] spriteY, float[] spriteDepth, int a, int b, int c)
	{
		float abx = spriteX[b] - spriteX[a];
		float aby = spriteY[b] - spriteY[a];
		float acx = spriteX[c] - spriteX[a];
		float acy = spriteY[c] - spriteY[a];
		float normalZ = (abx * acy) - (aby * acx);
		if (Math.abs(normalZ) <= 1.0e-4f)
		{
			return true;
		}

		float abz = spriteDepth[b] - spriteDepth[a];
		float acz = spriteDepth[c] - spriteDepth[a];
		float normalDepth = (aby * acz) - (abz * acy);
		return normalZ >= 0.0f || !Float.isFinite(normalDepth);
	}

	private void expireCaches(long nowMillis)
	{
		expireIdleEntries(billboardCache.entrySet().iterator(), nowMillis);
		expireIdleEntries(textureCache.entrySet().iterator(), nowMillis);
	}

	private static <K, V extends TimedCacheEntry> void expireIdleEntries(Iterator<Map.Entry<K, V>> iterator, long nowMillis)
	{
		while (iterator.hasNext())
		{
			Map.Entry<K, V> entry = iterator.next();
			if (nowMillis - entry.getValue().lastUsedMillis() > CACHE_TTL_MILLIS)
			{
				iterator.remove();
			}
		}
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

	private double billboardDepth(ActorSpotAnim actorSpotAnim, Actor actor)
	{
		LocalPoint localPoint = actor != null ? actor.getLocalLocation() : null;
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		int verticalOffset = Math.max(0, actor.getAnimationHeightOffset() + actorSpotAnim.getHeight());
		return cameraDistance(localPoint, actor.getWorldView().getPlane(), verticalOffset + (actorSpotAnim.getModelHeight() / 2.0));
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

	private BuiltFaces buildFaces(
		Model model,
		float[] spriteX,
		float[] spriteY,
		float[] spriteDepth,
		long nowMillis,
		int animatedTextureId
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
		int textureStateHash = 1;

		for (int face = 0; face < model.getFaceCount(); face++)
		{
			int a = faceIndices1[face];
			int b = faceIndices2[face];
			int c = faceIndices3[face];
			if (isBackFace(spriteX, spriteY, spriteDepth, a, b, c))
			{
				continue;
			}

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

			int textureId = textures != null && face < textures.length ? Short.toUnsignedInt(textures[face]) : 0xFFFF;
			if (textureId != 0xFFFF)
			{
				TextureSample textureSample = resolveTextureSample(textureId, nowMillis, animatedTextureId);
				TextureUvs textureUvs = computeTextureUvs(model, face);
				if (textureSample != null && textureUvs != null)
				{
					textureStateHash = (31 * textureStateHash) + textureSample.stateHash;
					faces.add(new FaceDraw(polygon, color, depth, textureSample, textureUvs));
					continue;
				}
			}

			faces.add(new FaceDraw(polygon, color, depth, null, null));
		}

		return new BuiltFaces(faces, textureStateHash);
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

		long nowMillis = System.currentTimeMillis();
		BuiltFaces builtFaces = buildFaces(model, spriteX, spriteY, spriteDepth, nowMillis, request.animatedTextureId);
		if (builtFaces.faces.isEmpty())
		{
			return null;
		}

		List<FaceDraw> faces = builtFaces.faces;
		faces.sort(Comparator.comparingDouble(FaceDraw::getDepth).reversed());
		Rectangle sourceBounds = computeBounds(faces);
		if (!isUsableSourceBounds(sourceBounds))
		{
			return null;
		}

		int outlinePadding = outlinePadding();
		Rectangle imageBounds = expandedBounds(sourceBounds, outlinePadding);

		BillboardCacheKey cacheKey = buildCacheKey(request, outlinePadding, builtFaces.textureStateHash);
		CachedBillboard cached = billboardCache.get(renderable);
		boolean cacheInvalidated = shouldRefreshCache(renderable, cacheKey, imageBounds, nowMillis);
		boolean spriteRedrawn = false;
		if (cacheInvalidated)
		{
			BufferedImage image = renderBillboardImage(faces, sourceBounds, outlinePadding);
			if (image == null)
			{
				return null;
			}

			cached = new CachedBillboard(cacheKey, imageBounds, image, nowMillis);
			billboardCache.put(renderable, cached);
			spriteRedrawn = true;
		}
		else
		{
			cached.touch(nowMillis);
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

		Rectangle drawRect = buildDrawRect(cached.bounds, anchorX, anchorY, targetWidth, targetHeight);
		if (drawRect == null)
		{
			return null;
		}

		boolean spriteDirty = cached.consumeDirty();
		NpcSnapDebug.RenderDebug renderDebug = NpcSnapDebug.RenderDebug.forBounds(
			drawRect,
			0,
			cacheInvalidated,
			spriteDirty,
			request.frameDebugInfo
		);
		debug.drawBillboardDebug(graphics, renderDebug);
		graphics.drawImage(cached.image, drawRect.x, drawRect.y, drawRect.width, drawRect.height, null);
		return new BillboardRenderResult(drawRect, cacheInvalidated, spriteDirty);
	}

	private BillboardCacheKey buildCacheKey(BillboardRenderRequest request, int outlinePadding, int textureStateHash)
	{
		return new BillboardCacheKey(
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
			qualityKey(),
			textureStateHash
		);
	}

	private int animatedTextureId(Actor actor)
	{
		if (!(actor instanceof Player))
		{
			return -1;
		}

		PlayerComposition composition = ((Player) actor).getPlayerComposition();
		if (composition == null)
		{
			return -1;
		}

		int capeId = composition.getEquipmentId(KitType.CAPE);
		capeId = normalizeEquipmentItemId(capeId);
		if (isAnimatedTextureId(capeId))
		{
			return capeId;
		}

		int[] equipmentIds = composition.getEquipmentIds();
		if (equipmentIds == null)
		{
			return -1;
		}

		for (int equipmentId : equipmentIds)
		{
			int normalizedId = normalizeEquipmentItemId(equipmentId);
			if (isAnimatedTextureId(normalizedId))
			{
				return normalizedId;
			}
		}

		return -1;
	}

	private TextureSample resolveTextureSample(int textureId, long nowMillis, int animatedTextureId)
	{
		TextureProvider textureProvider = client.getTextureProvider();
		if (textureProvider == null)
		{
			return null;
		}

		Texture[] textures = textureProvider.getTextures();
		if (textures == null || textureId < 0 || textureId >= textures.length)
		{
			return null;
		}

		Texture texture = textures[textureId];
		int[] pixels = texture != null && texture.getPixels() != null ? texture.getPixels() : textureProvider.load(textureId);
		if (pixels == null || pixels.length == 0)
		{
			return null;
		}

		int dimension = (int) Math.round(Math.sqrt(pixels.length));
		if (dimension <= 0 || dimension * dimension != pixels.length)
		{
			return null;
		}

		TextureCacheEntry entry = textureCache.get(textureId);
		if (entry == null || entry.pixels != pixels || entry.width != dimension || entry.height != dimension)
		{
			entry = new TextureCacheEntry(pixels, dimension, dimension, nowMillis);
			textureCache.put(textureId, entry);
		}
		else
		{
			entry.touch(nowMillis);
		}

		float uOffset = texture != null ? normalizeTextureOffset(texture.getU(), entry.width) : 0f;
		float vOffset = texture != null ? normalizeTextureOffset(texture.getV(), entry.height) : 0f;
		if (isAnimatedTextureId(animatedTextureId))
		{
			uOffset = 0f;
			vOffset = animatedTextureVOffset(nowMillis, texture);
		}
		int stateHash = 31 * textureId + Float.floatToIntBits(uOffset);
		stateHash = 31 * stateHash + Float.floatToIntBits(vOffset);
		return new TextureSample(entry, uOffset, vOffset, stateHash);
	}

	private TextureUvs computeTextureUvs(Model model, int face)
	{
		float[] vertexX = model.getVerticesX();
		float[] vertexY = model.getVerticesY();
		float[] vertexZ = model.getVerticesZ();
		int[] indices1 = model.getFaceIndices1();
		int[] indices2 = model.getFaceIndices2();
		int[] indices3 = model.getFaceIndices3();
		byte[] textureFaces = model.getTextureFaces();
		int[] texIndices1 = model.getTexIndices1();
		int[] texIndices2 = model.getTexIndices2();
		int[] texIndices3 = model.getTexIndices3();

		if (textureFaces != null && face < textureFaces.length && textureFaces[face] != -1
			&& texIndices1 != null && texIndices2 != null && texIndices3 != null)
		{
			int triangleA = indices1[face];
			int triangleB = indices2[face];
			int triangleC = indices3[face];
			int textureFace = textureFaces[face] & 0xFF;
			if (textureFace >= texIndices1.length || textureFace >= texIndices2.length || textureFace >= texIndices3.length)
			{
				return null;
			}

			int texA = texIndices1[textureFace];
			int texB = texIndices2[textureFace];
			int texC = texIndices3[textureFace];

			float v1x = vertexX[texA];
			float v1y = vertexY[texA];
			float v1z = vertexZ[texA];
			float v2x = vertexX[texB] - v1x;
			float v2y = vertexY[texB] - v1y;
			float v2z = vertexZ[texB] - v1z;
			float v3x = vertexX[texC] - v1x;
			float v3y = vertexY[texC] - v1y;
			float v3z = vertexZ[texC] - v1z;

			float v4x = vertexX[triangleA] - v1x;
			float v4y = vertexY[triangleA] - v1y;
			float v4z = vertexZ[triangleA] - v1z;
			float v5x = vertexX[triangleB] - v1x;
			float v5y = vertexY[triangleB] - v1y;
			float v5z = vertexZ[triangleB] - v1z;
			float v6x = vertexX[triangleC] - v1x;
			float v6y = vertexY[triangleC] - v1y;
			float v6z = vertexZ[triangleC] - v1z;

			float v7x = v2y * v3z - v2z * v3y;
			float v7y = v2z * v3x - v2x * v3z;
			float v7z = v2x * v3y - v2y * v3x;

			float v8x = v3y * v7z - v3z * v7y;
			float v8y = v3z * v7x - v3x * v7z;
			float v8z = v3x * v7y - v3y * v7x;
			float denominator = v8x * v2x + v8y * v2y + v8z * v2z;
			if (Math.abs(denominator) < 1.0e-6f)
			{
				return null;
			}

			float factor = 1.0f / denominator;
			float u0 = (v8x * v4x + v8y * v4y + v8z * v4z) * factor;
			float u1 = (v8x * v5x + v8y * v5y + v8z * v5z) * factor;
			float u2 = (v8x * v6x + v8y * v6y + v8z * v6z) * factor;

			v8x = v2y * v7z - v2z * v7y;
			v8y = v2z * v7x - v2x * v7z;
			v8z = v2x * v7y - v2y * v7x;
			denominator = v8x * v3x + v8y * v3y + v8z * v3z;
			if (Math.abs(denominator) < 1.0e-6f)
			{
				return null;
			}

			factor = 1.0f / denominator;
			float v0 = (v8x * v4x + v8y * v4y + v8z * v4z) * factor;
			float v1 = (v8x * v5x + v8y * v5y + v8z * v5z) * factor;
			float v2 = (v8x * v6x + v8y * v6y + v8z * v6z) * factor;
			return new TextureUvs(u0, v0, u1, v1, u2, v2);
		}

		return new TextureUvs(0f, 0f, 1f, 0f, 0f, 1f);
	}

	private void rasterizeTexturedFace(int[] imagePixels, int imageWidth, int imageHeight, Rectangle imageBounds, double qualityScale, FaceDraw face)
	{
		TextureSample textureSample = face.getTextureSample();
		if (textureSample == null)
		{
			return;
		}

		float x0 = scaleCoordinate(face.getPolygon().xpoints[0], imageBounds.x, qualityScale);
		float y0 = scaleCoordinate(face.getPolygon().ypoints[0], imageBounds.y, qualityScale);
		float x1 = scaleCoordinate(face.getPolygon().xpoints[1], imageBounds.x, qualityScale);
		float y1 = scaleCoordinate(face.getPolygon().ypoints[1], imageBounds.y, qualityScale);
		float x2 = scaleCoordinate(face.getPolygon().xpoints[2], imageBounds.x, qualityScale);
		float y2 = scaleCoordinate(face.getPolygon().ypoints[2], imageBounds.y, qualityScale);

		float area = edge(x0, y0, x1, y1, x2, y2);
		if (Math.abs(area) < 1.0e-6f)
		{
			return;
		}

		int minX = clampRasterCoordinate((int) Math.floor(Math.min(x0, Math.min(x1, x2))), imageWidth);
		int maxX = clampRasterCoordinate((int) Math.ceil(Math.max(x0, Math.max(x1, x2))), imageWidth);
		int minY = clampRasterCoordinate((int) Math.floor(Math.min(y0, Math.min(y1, y2))), imageHeight);
		int maxY = clampRasterCoordinate((int) Math.ceil(Math.max(y0, Math.max(y1, y2))), imageHeight);
		if (minX > maxX || minY > maxY)
		{
			return;
		}

		Color shade = NpcSnapColorBanding.snapToRamp(face.getColor(), config.billboardColorBands());
		TextureUvs textureUvs = face.getTextureUvs();
		for (int y = minY; y <= maxY; y++)
		{
			float py = y + 0.5f;
			for (int x = minX; x <= maxX; x++)
			{
				float px = x + 0.5f;
				float w0 = edge(x1, y1, x2, y2, px, py) / area;
				float w1 = edge(x2, y2, x0, y0, px, py) / area;
				float w2 = 1.0f - w0 - w1;
				if (w0 < 0f || w1 < 0f || w2 < 0f)
				{
					continue;
				}

				float u = (float) wrapUnit((w0 * textureUvs.u0) + (w1 * textureUvs.u1) + (w2 * textureUvs.u2) + textureSample.uOffset);
				float v = (float) wrapUnit((w0 * textureUvs.v0) + (w1 * textureUvs.v1) + (w2 * textureUvs.v2) + textureSample.vOffset);
				int textureX = Math.min(textureSample.entry.width - 1, (int) (u * textureSample.entry.width));
				int textureY = Math.min(textureSample.entry.height - 1, (int) (v * textureSample.entry.height));
				int samplePixel = textureSample.entry.pixels[(textureY * textureSample.entry.width) + textureX];
				if ((samplePixel >>> 24) == 0 && (samplePixel & 0xFFFFFF) == 0)
				{
					continue;
				}

				int shadedPixel = modulateTexturePixel(samplePixel, shade);
				int pixelIndex = (y * imageWidth) + x;
				imagePixels[pixelIndex] = blendPixel(imagePixels[pixelIndex], shadedPixel);
			}
		}
	}

	private static Polygon scalePolygon(Polygon source, Rectangle imageBounds, double qualityScale)
	{
		int[] xPoints = new int[source.npoints];
		int[] yPoints = new int[source.npoints];
		for (int i = 0; i < source.npoints; i++)
		{
			xPoints[i] = Math.round(scaleCoordinate(source.xpoints[i], imageBounds.x, qualityScale));
			yPoints[i] = Math.round(scaleCoordinate(source.ypoints[i], imageBounds.y, qualityScale));
		}

		return new Polygon(xPoints, yPoints, source.npoints);
	}

	private static float scaleCoordinate(int coordinate, int origin, double qualityScale)
	{
		return (float) ((coordinate - origin) * qualityScale);
	}

	private static int clampRasterCoordinate(int coordinate, int dimension)
	{
		return Math.max(0, Math.min(dimension - 1, coordinate));
	}

	private static float edge(float ax, float ay, float bx, float by, float px, float py)
	{
		return ((px - ax) * (by - ay)) - ((py - ay) * (bx - ax));
	}

	private static float normalizeTextureOffset(float offset, int dimension)
	{
		if (!Float.isFinite(offset))
		{
			return 0f;
		}

		return Math.abs(offset) > 1.0f && dimension > 0 ? offset / dimension : offset;
	}

	private static int normalizeEquipmentItemId(int equipmentId)
	{
		if (equipmentId >= PlayerComposition.ITEM_OFFSET)
		{
			return equipmentId - PlayerComposition.ITEM_OFFSET;
		}

		return equipmentId;
	}

	private static boolean isAnimatedTextureId(int textureId)
	{
		for (int animatedTextureId : ANIMATED_TEXTURE_IDS)
		{
			if (animatedTextureId == textureId)
			{
				return true;
			}
		}

		return false;
	}

	private float animatedTextureVOffset(long nowMillis, Texture texture)
	{
		double speedMultiplier = texture != null && texture.getAnimationSpeed() > 0
			? texture.getAnimationSpeed()
			: 1.0d;
		double offset = (nowMillis / 1000.0d) * ANIMATED_TEXTURE_V_SCROLL_PER_SECOND * speedMultiplier;
		return snappedAnimatedTextureOffset(offset);
	}

	private float snappedAnimatedTextureOffset(double offset)
	{
		offset = wrapUnit(offset);
		if (!config.enableAnimationFrameSnapping())
		{
			return (float) offset;
		}

		int visibleFrameCount = Math.max(1, config.animationFrameCount());
		double snapped = Math.round(offset * visibleFrameCount) / (double) visibleFrameCount;
		return (float) wrapUnit(snapped);
	}

	private static double wrapUnit(double coordinate)
	{
		double wrapped = coordinate - Math.floor(coordinate);
		return wrapped < 0.0d ? wrapped + 1.0d : wrapped;
	}

	private int modulateTexturePixel(int samplePixel, Color shade)
	{
		int sampleAlpha = (samplePixel >>> 24) & 0xFF;
		if (sampleAlpha == 0 && (samplePixel & 0xFFFFFF) != 0)
		{
			sampleAlpha = 0xFF;
		}

		int alpha = (sampleAlpha * shade.getAlpha()) / 255;
		int red = (((samplePixel >> 16) & 0xFF) * shade.getRed()) / 255;
		int green = (((samplePixel >> 8) & 0xFF) * shade.getGreen()) / 255;
		int blue = ((samplePixel & 0xFF) * shade.getBlue()) / 255;
		return NpcSnapColorBanding.snapTexturePixel((alpha << 24) | (red << 16) | (green << 8) | blue, config.billboardColorBands());
	}

	private static int blendPixel(int destination, int source)
	{
		int sourceAlpha = (source >>> 24) & 0xFF;
		if (sourceAlpha <= 0)
		{
			return destination;
		}

		if (sourceAlpha >= 0xFF)
		{
			return source;
		}

		int destinationAlpha = (destination >>> 24) & 0xFF;
		int outAlpha = sourceAlpha + ((destinationAlpha * (255 - sourceAlpha)) / 255);
		if (outAlpha <= 0)
		{
			return 0;
		}

		int inverseAlpha = 255 - sourceAlpha;
		int sourceRed = (source >> 16) & 0xFF;
		int sourceGreen = (source >> 8) & 0xFF;
		int sourceBlue = source & 0xFF;
		int destinationRed = (destination >> 16) & 0xFF;
		int destinationGreen = (destination >> 8) & 0xFF;
		int destinationBlue = destination & 0xFF;
		int outRed = ((sourceRed * sourceAlpha) + (destinationRed * destinationAlpha * inverseAlpha / 255)) / outAlpha;
		int outGreen = ((sourceGreen * sourceAlpha) + (destinationGreen * destinationAlpha * inverseAlpha / 255)) / outAlpha;
		int outBlue = ((sourceBlue * sourceAlpha) + (destinationBlue * destinationAlpha * inverseAlpha / 255)) / outAlpha;
		return (outAlpha << 24) | (outRed << 16) | (outGreen << 8) | outBlue;
	}

	private Rectangle buildDrawRect(Rectangle cachedBounds, int anchorX, int anchorY, int targetWidth, int targetHeight)
	{
		double originX = (-cachedBounds.x) * (targetWidth / (double) cachedBounds.width);
		double originY = (-cachedBounds.y) * (targetHeight / (double) cachedBounds.height);
		int drawX = (int) Math.round(anchorX - originX);
		int drawY = (int) Math.round(anchorY - originY);
		if (!isUsableDrawSize(targetWidth, targetHeight) || isOutsideViewport(drawX, drawY, targetWidth, targetHeight))
		{
			return null;
		}

		return new Rectangle(drawX, drawY, targetWidth, targetHeight);
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
		int rawRelativeYaw = client.getCameraYaw() + projectile.getOrientation();
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
		// Invert because model pitch is applied opposite to camera pitch.
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
		private final TextureSample textureSample;
		private final TextureUvs textureUvs;

		private FaceDraw(Polygon polygon, Color color, double depth, TextureSample textureSample, TextureUvs textureUvs)
		{
			this.polygon = polygon;
			this.color = color;
			this.depth = depth;
			this.textureSample = textureSample;
			this.textureUvs = textureUvs;
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

		private boolean isTextured()
		{
			return textureSample != null && textureUvs != null;
		}

		private TextureSample getTextureSample()
		{
			return textureSample;
		}

		private TextureUvs getTextureUvs()
		{
			return textureUvs;
		}
	}

	private interface TimedCacheEntry
	{
		long lastUsedMillis();
	}

	private static final class BuiltFaces
	{
		private final List<FaceDraw> faces;
		private final int textureStateHash;

		private BuiltFaces(List<FaceDraw> faces, int textureStateHash)
		{
			this.faces = faces;
			this.textureStateHash = textureStateHash;
		}
	}

	private static final class TextureSample
	{
		private final TextureCacheEntry entry;
		private final float uOffset;
		private final float vOffset;
		private final int stateHash;

		private TextureSample(TextureCacheEntry entry, float uOffset, float vOffset, int stateHash)
		{
			this.entry = entry;
			this.uOffset = uOffset;
			this.vOffset = vOffset;
			this.stateHash = stateHash;
		}
	}

	private static final class TextureUvs
	{
		private final float u0;
		private final float v0;
		private final float u1;
		private final float v1;
		private final float u2;
		private final float v2;

		private TextureUvs(float u0, float v0, float u1, float v1, float u2, float v2)
		{
			this.u0 = u0;
			this.v0 = v0;
			this.u1 = u1;
			this.v1 = v1;
			this.u2 = u2;
			this.v2 = v2;
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
		private final int textureStateHash;

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
			int renderQuality,
			int textureStateHash)
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
			this.textureStateHash = textureStateHash;
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
				&& renderQuality == that.renderQuality
				&& textureStateHash == that.textureStateHash;
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
			result = 31 * result + textureStateHash;
			return result;
		}
	}

	private static final class CachedBillboard implements TimedCacheEntry
	{
		private final BillboardCacheKey key;
		private final Rectangle bounds;
		private final BufferedImage image;
		private long lastUsedMillis;
		private long lastRedrawMillis;
		private boolean dirty;

		private CachedBillboard(BillboardCacheKey key, Rectangle bounds, BufferedImage image, long lastUsedMillis)
		{
			this.key = key;
			this.bounds = new Rectangle(bounds);
			this.image = image;
			this.lastUsedMillis = lastUsedMillis;
			this.lastRedrawMillis = lastUsedMillis;
			this.dirty = true;
		}

		private void touch(long nowMillis)
		{
			lastUsedMillis = nowMillis;
		}

		@Override
		public long lastUsedMillis()
		{
			return lastUsedMillis;
		}

		private long lastRedrawMillis()
		{
			return lastRedrawMillis;
		}

		private boolean consumeDirty()
		{
			boolean wasDirty = dirty;
			dirty = false;
			return wasDirty;
		}
	}

	private static final class TextureCacheEntry implements TimedCacheEntry
	{
		private final int[] pixels;
		private final int width;
		private final int height;
		private long lastUsedMillis;

		private TextureCacheEntry(int[] pixels, int width, int height, long lastUsedMillis)
		{
			this.pixels = pixels;
			this.width = width;
			this.height = height;
			this.lastUsedMillis = lastUsedMillis;
		}

		private void touch(long nowMillis)
		{
			lastUsedMillis = nowMillis;
		}

		@Override
		public long lastUsedMillis()
		{
			return lastUsedMillis;
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
		ACTOR_SPOT_ANIM,
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
		private final Actor parentActor;
		private final TileObject tileObject;
		private final ObservedTileObject observedTileObject;
		private final GroundItemBillboard groundItem;
		private final double depth;
		private final int renderPriority;
		private final PriorityTileKey priorityTileKey;

		private BillboardTarget(
			BillboardTargetType type,
			Renderable renderable,
			Actor parentActor,
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
			this.parentActor = parentActor;
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
			return new BillboardTarget(type, renderable, null, null, null, null, depth, renderPriority, PriorityTileKey.of(localPoint, plane, renderPriority));
		}

		private static BillboardTarget forActorSpotAnim(ActorSpotAnim actorSpotAnim, Actor actor, double depth)
		{
			LocalPoint localPoint = actor.getLocalLocation();
			int plane = actor.getWorldView().getPlane();
			return new BillboardTarget(
				BillboardTargetType.ACTOR_SPOT_ANIM,
				actorSpotAnim,
				actor,
				null,
				null,
				null,
				depth,
				RENDER_PRIORITY_EFFECT,
				PriorityTileKey.of(localPoint, plane, RENDER_PRIORITY_EFFECT)
			);
		}

		private static BillboardTarget forGroundItem(TileItem item, GroundItemBillboard groundItem, double depth)
		{
			return new BillboardTarget(
				BillboardTargetType.GROUND_ITEM,
				item,
				null,
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

	private static final class EffectDedupKey
	{
		private final int id;
		private final int plane;
		private final int tileX;
		private final int tileY;

		private EffectDedupKey(int id, int plane, int tileX, int tileY)
		{
			this.id = id;
			this.plane = plane;
			this.tileX = tileX;
			this.tileY = tileY;
		}

		private static EffectDedupKey of(GraphicsObject graphicsObject)
		{
			LocalPoint localPoint = graphicsObject.getLocation();
			return localPoint == null ? null : new EffectDedupKey(
				graphicsObject.getId(),
				graphicsObject.getLevel(),
				localPoint.getX() / LOCAL_TILE_SIZE,
				localPoint.getY() / LOCAL_TILE_SIZE
			);
		}

		private static EffectDedupKey of(ActorSpotAnim actorSpotAnim, Actor actor)
		{
			LocalPoint localPoint = actor.getLocalLocation();
			return localPoint == null ? null : new EffectDedupKey(
				actorSpotAnim.getId(),
				actor.getWorldView().getPlane(),
				localPoint.getX() / LOCAL_TILE_SIZE,
				localPoint.getY() / LOCAL_TILE_SIZE
			);
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}

			if (!(other instanceof EffectDedupKey))
			{
				return false;
			}

			EffectDedupKey that = (EffectDedupKey) other;
			return id == that.id
				&& plane == that.plane
				&& tileX == that.tileX
				&& tileY == that.tileY;
		}

		@Override
		public int hashCode()
		{
			int result = Integer.hashCode(id);
			result = (31 * result) + Integer.hashCode(plane);
			result = (31 * result) + Integer.hashCode(tileX);
			result = (31 * result) + Integer.hashCode(tileY);
			return result;
		}
	}

	private static final class OccupiedTileKey
	{
		private final int plane;
		private final int tileX;
		private final int tileY;

		private OccupiedTileKey(int plane, int tileX, int tileY)
		{
			this.plane = plane;
			this.tileX = tileX;
			this.tileY = tileY;
		}

		private static OccupiedTileKey of(LocalPoint localPoint, int plane)
		{
			if (localPoint == null)
			{
				return null;
			}

			return new OccupiedTileKey(
				plane,
				localPoint.getX() / LOCAL_TILE_SIZE,
				localPoint.getY() / LOCAL_TILE_SIZE
			);
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}

			if (!(other instanceof OccupiedTileKey))
			{
				return false;
			}

			OccupiedTileKey that = (OccupiedTileKey) other;
			return plane == that.plane
				&& tileX == that.tileX
				&& tileY == that.tileY;
		}

		@Override
		public int hashCode()
		{
			int result = Integer.hashCode(plane);
			result = (31 * result) + Integer.hashCode(tileX);
			result = (31 * result) + Integer.hashCode(tileY);
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
		private final int animatedTextureId;
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
			int animatedTextureId,
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
			this.animatedTextureId = animatedTextureId;
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
