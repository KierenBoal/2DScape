package com.kierenboal.npcsnap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
class NpcBillboardOverlay extends Overlay
{
	private static final int FULL_CIRCLE = 2048;
	private static final int MAX_PITCH = 512;
	private static final int GROUND_ITEM_MIN_PITCH = 128;
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
	private static final long CACHE_TTL_MILLIS = 60_000L;
	
	private static final int[] ANIMATED_TEXTURE_IDS = {
		
		ItemID.TZHAAR_CAPE_FIRE,
		ItemID.TZHAAR_CAPE_FIRE_DUMMY,
		ItemID.TZHAAR_CAPE_FIRE_TROUVER, 
		ItemID.TZHAAR_CAPE_FIRE_BROKEN, 
		
		ItemID.INFERNAL_CAPE,
		ItemID.INFERNAL_CAPE_DUMMY ,
		ItemID.INFERNAL_CAPE_TROUVER,
		ItemID.INFERNAL_CAPE_BROKEN,
		ItemID.BR_INFERNAL_CAPE
		
	};
	private static final float ANIMATED_TEXTURE_V_SCROLL_PER_SECOND = -0.25f;
	private static final int RENDER_PRIORITY_NONE = -1;
	private final Client client;
	private final NpcSnapConfig config;
	private final NpcSnapDebug debug;
	private final AnimationFrameSnapper animationFrameSnapper;
	private final Map<Renderable, CachedBillboard> billboardCache = new IdentityHashMap<>();
	private final Map<Integer, TextureCacheEntry> textureCache = new HashMap<>();
	private final Map<TileItem, GroundItemBillboard> groundItems = new HashMap<>();
	private final Map<TileObject, ObservedTileObject> observedTileObjects = new IdentityHashMap<>();
	private final Map<TileObject, ObservedTileObject> visibleTileObjects = new IdentityHashMap<>();
	private final Object observedTileObjectsLock = new Object();
	private final Map<Renderable, BillboardTarget> activeRenderableTargets = new IdentityHashMap<>();
	private final Set<Renderable> activeBillboards = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Renderable> suppressedRenderables = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<TileObject> activeTileObjects = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Renderable> sceneRenderablesThisFrame = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Renderable> sceneRenderablesLastFrame = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Deque<BillboardTargetKey> renderQueue = new ArrayDeque<>();
	private final Set<BillboardTargetKey> renderQueueEntries = new HashSet<>();
	private final Map<BillboardTargetKey, Integer> debugQueuePositions = new HashMap<>();
	private final Map<Object, String> classificationDebugMessages = new IdentityHashMap<>();
	private final Map<BillboardTargetKey, BillboardUpdateState> billboardUpdateStates = new HashMap<>();
	private final Map<BillboardTargetKey, FrameUpdatePlan> frameUpdatePlans = new HashMap<>();
	private final Set<Renderable> forceHoverInteractionRedraws = Collections.newSetFromMap(new IdentityHashMap<>());
	private final BillboardOutlineRenderer.Scratch outlineScratch = new BillboardOutlineRenderer.Scratch();
	private volatile Set<Renderable> activeBillboardSnapshot = Collections.emptySet();
	private volatile Set<Renderable> suppressedRenderableSnapshot = Collections.emptySet();
	private volatile Set<TileObject> activeTileObjectSnapshot = Collections.emptySet();
	private float[] spriteXScratch = new float[0];
	private float[] spriteYScratch = new float[0];
	private float[] spriteDepthScratch = new float[0];
	private BufferedImage frameCompositeImage;
	private int[] frameCompositePixels = new int[0];
	private char[] paintOrderBuffer = new char[0];
	private int frameCompositeWidth;
	private int frameCompositeHeight;
	private int activeBillboardsGameCycle = Integer.MIN_VALUE;
	private Actor interactedActor;
	private int interactedActorClickTick = Integer.MIN_VALUE;
	private Actor frameHoveredActor;
	private Actor frameInteractionActor;
	private Actor lastHoveredActor;
	private Actor lastInteractionActor;

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
			clearBillboardCache();
			return null;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			clearActiveState();
			clearBillboardCache();
			return null;
		}

		if (!config.enable2dBillboardSprites())
		{
			clearActiveState();
			clearBillboardCache();
			return null;
		}

		List<BillboardTarget> visibleTargets = getVisibleTargets(worldView);
		updateHoverInteractionState();
		applyForcedHoverInteractionPlans(visibleTargets);
		visibleTargets = sortTargetsForRender(visibleTargets);
		List<PreparedBillboardDraw> preparedDraws = new ArrayList<>();
		for (int i = 0; i < visibleTargets.size(); i++)
		{
			renderTarget(visibleTargets.get(i), i + 1, preparedDraws);
		}
		compositePreparedDraws(graphics, preparedDraws);

		return null;
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
		removeCachedBillboard(item);
		activeRenderableTargets.remove(item);
		activeBillboards.remove(item);
	}

	void clearGroundItems()
	{
		for (TileItem item : groundItems.keySet())
		{
			removeCachedBillboard(item);
			activeRenderableTargets.remove(item);
			activeBillboards.remove(item);
		}

		groundItems.clear();
	}

	private void clearActiveState()
	{
		activeRenderableTargets.clear();
		clearInteractionState();
		clearActiveSelections();
		clearRenderQueue();
		clearActiveSnapshots();
	}

	private void clearActiveSelections()
	{
		activeBillboards.clear();
		suppressedRenderables.clear();
		activeTileObjects.clear();
	}

	private void clearActiveSnapshots()
	{
		activeBillboardSnapshot = Collections.emptySet();
		suppressedRenderableSnapshot = Collections.emptySet();
		activeTileObjectSnapshot = Collections.emptySet();
	}

	private void clearRenderQueue()
	{
		renderQueue.clear();
		renderQueueEntries.clear();
		debugQueuePositions.clear();
		classificationDebugMessages.clear();
		billboardUpdateStates.clear();
		frameUpdatePlans.clear();
		forceHoverInteractionRedraws.clear();
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
		synchronized (observedTileObjectsLock)
		{
			clearObservedTileObjectBillboards(observedTileObjects.values());
			clearObservedTileObjectBillboards(visibleTileObjects.values());
			observedTileObjects.clear();
			visibleTileObjects.clear();
		}
		activeTileObjects.clear();
		activeTileObjectSnapshot = Collections.emptySet();
	}

	void clearTextureCache()
	{
		textureCache.clear();
	}

	void clearBillboardCache()
	{
		for (CachedBillboard cached : billboardCache.values())
		{
			cached.flush();
		}

		billboardCache.clear();
	}

	void noteSceneRenderable(Renderable renderable)
	{
		if (renderable != null)
		{
			sceneRenderablesThisFrame.add(renderable);
		}
	}

	private void clearObservedTileObjectBillboards(Collection<ObservedTileObject> observedTileObjects)
	{
		for (ObservedTileObject observed : observedTileObjects)
		{
			if (observed == null)
			{
				continue;
			}

			for (ObjectRenderablePart part : observed.parts)
			{
				if (part.renderable != null)
				{
					removeCachedBillboard(part.renderable);
				}
			}
		}
	}

	void beginFrame()
	{
		expireCaches(System.currentTimeMillis());
		sceneRenderablesLastFrame.clear();
		sceneRenderablesLastFrame.addAll(sceneRenderablesThisFrame);
		sceneRenderablesThisFrame.clear();
		synchronized (observedTileObjectsLock)
		{
			visibleTileObjects.clear();
			visibleTileObjects.putAll(observedTileObjects);
			observedTileObjects.clear();
		}
		clearActiveSelections();
		activeBillboardsGameCycle = Integer.MIN_VALUE;
	}

	void noteActorInteraction(Actor actor, int tickCount)
	{
		interactedActor = actor;
		interactedActorClickTick = actor != null ? tickCount : Integer.MIN_VALUE;
	}

	void clearStaleInteraction(Player localPlayer, int tickCount)
	{
		if (interactedActor == null || tickCount <= interactedActorClickTick)
		{
			return;
		}

		Actor currentTarget = localPlayer != null ? localPlayer.getInteracting() : null;
		if (currentTarget != interactedActor)
		{
			clearInteractionState();
		}
	}

	void onLocalPlayerInteractionChanged(Actor target, int tickCount)
	{
		if (interactedActor != null && target != interactedActor && tickCount > interactedActorClickTick)
		{
			clearInteractionState();
		}
	}

	void clearInteractionIfMatches(Actor actor)
	{
		if (actor == interactedActor)
		{
			clearInteractionState();
		}
	}

	void clearInteractionState()
	{
		interactedActor = null;
		interactedActorClickTick = Integer.MIN_VALUE;
		frameInteractionActor = null;
		lastInteractionActor = null;
	}

	private boolean wasSceneRenderableDrawnLastFrame(Renderable renderable)
	{
		return renderable != null && sceneRenderablesLastFrame.contains(renderable);
	}

	void observeTileObject(TileObject tileObject)
	{
		ObservedTileObject observed = buildObservedTileObject(tileObject);
		if (observed != null)
		{
			synchronized (observedTileObjectsLock)
			{
				observedTileObjects.put(tileObject, observed);
			}
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
			return activeBillboards.contains(renderable) || suppressedRenderables.contains(renderable);
		}

		return activeBillboardSnapshot.contains(renderable) || suppressedRenderableSnapshot.contains(renderable);
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
			return activeTileObjects.contains(tileObject) && tileObjectHasCachedBillboard(tileObject);
		}

		return activeTileObjectSnapshot.contains(tileObject);
	}

	private void renderTarget(BillboardTarget target, int paintOrder, List<PreparedBillboardDraw> preparedDraws)
	{
		if (target.type == BillboardTargetType.TILE_OBJECT)
		{
			renderTileObjectTarget(target, paintOrder, preparedDraws);
			return;
		}

		BillboardRenderRequest request = buildRenderRequest(target);
		if (request == null)
		{
			return;
		}

		int queuePosition = debugQueuePositions.getOrDefault(target.targetKey, -1);
		BillboardRenderResult result = renderRenderableBillboard(request, frameUpdatePlans.get(target.targetKey), queuePosition);
		if (result == null)
		{
			return;
		}

		isReadyToRedrawDebug(target);
		preparedDraws.add(new PreparedBillboardDraw(request, result, paintOrder));
	}

	private void renderTileObjectTarget(BillboardTarget target, int paintOrder, List<PreparedBillboardDraw> preparedDraws)
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

			int queuePosition = debugQueuePositions.getOrDefault(target.targetKey, -1);
			BillboardRenderResult result = renderRenderableBillboard(request, frameUpdatePlans.get(target.targetKey), queuePosition);
			if (result == null)
			{
				continue;
			}

			isReadyToRedrawDebug(target);
			preparedDraws.add(new PreparedBillboardDraw(request, result, paintOrder));
		}
	}

	private boolean isReadyToRedrawDebug(BillboardTarget target)
	{
		if (target == null || frameUpdatePlans.containsKey(target.targetKey))
		{
			return false;
		}

		BillboardUpdateState updateState = billboardUpdateStates.get(target.targetKey);
		if (updateState == null)
		{
			return false;
		}

		int gameCycle = client.getGameCycle();
		if (!updateState.isReady(gameCycle, targetHasCachedBillboard(target)))
		{
			return false;
		}

		boolean readyToRedraw = needsBillboardRedraw(target, updateState.qualityScale);
		if (readyToRedraw)
		{
			markDebugFrameInvalidated(target);
		}

		return readyToRedraw;
	}

	private void drawRenderDebug(Graphics2D graphics, BillboardRenderResult result, BillboardRenderRequest request, int paintOrder)
	{
		CachedBillboard cached = request != null ? billboardCache.get(request.renderable) : null;
		NpcSnapDebug.RenderDebug renderDebug = NpcSnapDebug.RenderDebug.forBounds(
			result.bounds,
			paintOrder,
			cached != null && cached.consumeDebugFrameRedrawn(),
			cached != null && cached.consumeDebugFrameInvalidated(),
			cached != null ? cached.stateDebugInfo() : null
		);
		debug.drawBillboardDebugForeground(graphics, renderDebug);
	}

	private void markDebugFrameInvalidated(BillboardTarget target)
	{
		if (target == null)
		{
			return;
		}

		if (target.type == BillboardTargetType.TILE_OBJECT && target.observedTileObject != null)
		{
			for (ObjectRenderablePart part : target.observedTileObject.parts)
			{
				CachedBillboard cached = part != null && part.renderable != null ? billboardCache.get(part.renderable) : null;
				if (cached != null)
				{
					cached.markDebugFrameInvalidated();
				}
			}
			return;
		}

		CachedBillboard cached = target.renderable != null ? billboardCache.get(target.renderable) : null;
		if (cached != null)
		{
			cached.markDebugFrameInvalidated();
		}
	}

	private NpcSnapDebug.StateDebugInfo buildStateDebugInfo(BillboardRenderRequest request, int queuePosition)
	{
		if (request == null)
		{
			return null;
		}

		NpcSnapDebug.FrameDebugInfo frameDebugInfo = request.frameDebugInfo;
		int currentFrame = frameDebugInfo != null ? frameDebugInfo.animationFrame : request.animationFrame;
		int displayedFrame = frameDebugInfo != null ? frameDebugInfo.forcedAnimationFrame : request.animationFrame;
		int animationId = frameDebugInfo != null ? frameDebugInfo.animationId : request.animationId;
		int stateHash = debugStateHash(request);
		return NpcSnapDebug.StateDebugInfo.of(
			stateHash,
			jauToDegrees(request.relativePitch),
			jauToDegrees(request.relativeYaw),
			animationId,
			displayedFrame,
			currentFrame,
			queuePosition
		);
	}

	private int debugStateHash(BillboardRenderRequest request)
	{
		int hash = 1;
		hash = (31 * hash) + request.animationId;
		hash = (31 * hash) + request.animationFrame;
		hash = (31 * hash) + request.poseAnimationId;
		hash = (31 * hash) + request.poseAnimationFrame;
		hash = (31 * hash) + request.relativeYaw;
		hash = (31 * hash) + request.relativePitch;
		hash = (31 * hash) + request.animatedTextureId;
		hash = (31 * hash) + modelStateHash(request.model);
		return hash;
	}

	private static int jauToDegrees(int jau)
	{
		return Math.floorMod((int) Math.round((jau * 360.0d) / FULL_CIRCLE), 360);
	}

	private void compositePreparedDraws(Graphics2D graphics, List<PreparedBillboardDraw> preparedDraws)
	{
		if (preparedDraws.isEmpty())
		{
			return;
		}

		int viewportX = client.getViewportXOffset();
		int viewportY = client.getViewportYOffset();
		int viewportWidth = client.getViewportWidth();
		int viewportHeight = client.getViewportHeight();
		if (viewportWidth <= 0 || viewportHeight <= 0)
		{
			return;
		}

		ensureFrameCompositeCapacity(viewportWidth, viewportHeight);
		Arrays.fill(frameCompositePixels, 0, viewportWidth * viewportHeight, 0);
		Arrays.fill(paintOrderBuffer, 0, viewportWidth * viewportHeight, (char) 0);
		for (int i = preparedDraws.size() - 1; i >= 0; i--)
		{
			blitPreparedDraw(preparedDraws.get(i), viewportX, viewportY, viewportWidth, viewportHeight);
		}

		graphics.drawImage(frameCompositeImage, viewportX, viewportY, null);
		for (PreparedBillboardDraw draw : preparedDraws)
		{
			drawRenderDebug(graphics, draw.result, draw.request, draw.paintOrder);
		}
	}

	private void ensureFrameCompositeCapacity(int width, int height)
	{
		if (frameCompositeImage != null && frameCompositeWidth == width && frameCompositeHeight == height)
		{
			return;
		}

		frameCompositeImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		frameCompositePixels = ((DataBufferInt) frameCompositeImage.getRaster().getDataBuffer()).getData();
		paintOrderBuffer = new char[width * height];
		frameCompositeWidth = width;
		frameCompositeHeight = height;
	}

	private void blitPreparedDraw(PreparedBillboardDraw draw, int viewportX, int viewportY, int viewportWidth, int viewportHeight)
	{
		if (draw == null || draw.image == null || draw.bounds == null || draw.bounds.width <= 0 || draw.bounds.height <= 0)
		{
			return;
		}

		if (!(draw.image.getRaster().getDataBuffer() instanceof DataBufferInt))
		{
			return;
		}

		int[] sourcePixels = ((DataBufferInt) draw.image.getRaster().getDataBuffer()).getData();
		int sourceWidth = draw.image.getWidth();
		int sourceHeight = draw.image.getHeight();
		int clipLeft = Math.max(draw.bounds.x, viewportX);
		int clipTop = Math.max(draw.bounds.y, viewportY);
		int clipRight = Math.min(draw.bounds.x + draw.bounds.width, viewportX + viewportWidth);
		int clipBottom = Math.min(draw.bounds.y + draw.bounds.height, viewportY + viewportHeight);
		if (clipLeft >= clipRight || clipTop >= clipBottom)
		{
			return;
		}

		int paintOrder = draw.paintOrder;
		for (int y = clipTop; y < clipBottom; y++)
		{
			int destY = y - viewportY;
			int sourceY = ((y - draw.bounds.y) * sourceHeight) / draw.bounds.height;
			int destRow = destY * viewportWidth;
			int sourceRow = sourceY * sourceWidth;
			for (int x = clipLeft; x < clipRight; x++)
			{
				int destX = x - viewportX;
				int destIndex = destRow + destX;
				if (paintOrderBuffer[destIndex] > paintOrder)
				{
					continue;
				}

				int sourceX = ((x - draw.bounds.x) * sourceWidth) / draw.bounds.width;
				int sourcePixel = sourcePixels[sourceRow + sourceX];
				int sourceAlpha = (sourcePixel >>> 24) & 0xFF;
				if (sourceAlpha == 0)
				{
					continue;
				}

				frameCompositePixels[destIndex] = BillboardTriangleRasterizer.blendPixel(frameCompositePixels[destIndex], sourcePixel);
				if (sourceAlpha == 0xFF)
				{
					paintOrderBuffer[destIndex] = (char) paintOrder;
				}
			}
		}
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

		List<BillboardPaintOrder.Entry<BillboardTarget>> entries = new ArrayList<>(targets.size());
		for (BillboardTarget target : targets)
		{
			Rectangle previewBounds = buildSortPreviewBounds(target);
			entries.add(new BillboardPaintOrder.Entry<>(
				target,
				previewBounds,
				target.getRenderPriority(),
				target.priorityGroupSortKey(),
				previewBounds != null ? previewBounds.y + previewBounds.height : Integer.MAX_VALUE,
				target.getDepth(),
				target.targetKey.stableSortOrder()
			));
		}

		return BillboardPaintOrder.sort(entries);
	}

	private Rectangle buildSortPreviewBounds(BillboardTarget target)
	{
		if (target == null)
		{
			return null;
		}

		if (target.type == BillboardTargetType.TILE_OBJECT)
		{
			Rectangle combined = null;
			if (target.observedTileObject == null)
			{
				return null;
			}

			for (ObjectRenderablePart part : target.observedTileObject.parts)
			{
				BillboardRenderRequest request = buildRenderRequest(target, part);
				Rectangle preview = buildSortPreviewBounds(request);
				if (preview == null)
				{
					continue;
				}

				combined = combined == null ? new Rectangle(preview) : combined.union(preview);
			}

			return combined;
		}

		return buildSortPreviewBounds(buildRenderRequest(target));
	}

	private Rectangle buildSortPreviewBounds(BillboardRenderRequest request)
	{
		if (request == null || request.renderable == null)
		{
			return null;
		}

		CachedBillboard cached = billboardCache.get(request.renderable);
		Rectangle bounds = cached != null ? cached.bounds : estimateBillboardImageBounds(request);
		return bounds != null ? buildDrawRect(request, request.renderable, bounds) : null;
	}

	private Rectangle estimateBillboardImageBounds(BillboardRenderRequest request)
	{
		Model model = request != null ? request.model : null;
		if (model == null)
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
		double yawSin = Perspective.SINE[request.relativeYaw] / 65536.0;
		double yawCos = Perspective.COSINE[request.relativeYaw] / 65536.0;
		int inversePitch = Math.floorMod(-request.relativePitch, FULL_CIRCLE);
		double pitchSin = Perspective.SINE[inversePitch] / 65536.0;
		double pitchCos = Perspective.COSINE[inversePitch] / 65536.0;
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int i = 0; i < vertexCount; i++)
		{
			double rotatedX = (verticesX[i] * yawCos) + (verticesZ[i] * yawSin);
			double rotatedZ = (verticesZ[i] * yawCos) - (verticesX[i] * yawSin);
			double spriteY = (verticesY[i] * pitchCos) - (rotatedZ * pitchSin);
			if (!Double.isFinite(rotatedX) || !Double.isFinite(spriteY))
			{
				continue;
			}

			int projectedX = (int) Math.round(rotatedX);
			int projectedY = (int) Math.round(spriteY);
			minX = Math.min(minX, projectedX);
			minY = Math.min(minY, projectedY);
			maxX = Math.max(maxX, projectedX);
			maxY = Math.max(maxY, projectedY);
		}

		if (minX == Integer.MAX_VALUE)
		{
			return null;
		}

		Rectangle sourceBounds = new Rectangle(minX, minY, Math.max(1, (maxX - minX) + 1), Math.max(1, (maxY - minY) + 1));
		if (!isUsableSourceBounds(sourceBounds))
		{
			return null;
		}

		return expandedBounds(sourceBounds, outlinePadding());
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
		clearActiveSelections();
		activeBillboardsGameCycle = gameCycle;
		frameUpdatePlans.clear();
		if (!config.enable2dBillboardSprites() || client.getGameState() != GameState.LOGGED_IN || worldView == null)
		{
			clearRenderQueue();
			clearActiveSnapshots();
			return;
		}

		CollectedCandidates collected = collectCandidates(worldView);
		List<BillboardTarget> candidates = collected.candidates;
		candidates.sort(Comparator
			.comparingInt(this::candidateSelectionPriority).reversed()
			.thenComparingDouble(BillboardTarget::getDepth));
		int limit = Math.max(1, config.billboardMaxEntities());
		if (candidates.size() > limit)
		{
			candidates = new ArrayList<>(candidates.subList(0, limit));
		}

		for (BillboardTarget target : candidates)
		{
			if (target.renderable != null)
			{
				activeRenderableTargets.put(target.renderable, target);
				if (billboardCache.containsKey(target.renderable))
				{
					activeBillboards.add(target.renderable);
				}
			}
			else if (target.tileObject != null)
			{
				activeTileObjects.add(target.tileObject);
				addActiveTileObjectBillboardParts(target);
			}
		}

		markSuppressedStackedActors(collected);
		scheduleFrameUpdates(sortTargetsForRender(new ArrayList<>(candidates)), gameCycle);
		updateActiveSnapshots();
	}

	private void addActiveTileObjectBillboardParts(BillboardTarget target)
	{
		if (target == null || target.observedTileObject == null)
		{
			return;
		}

		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			if (part != null && part.renderable != null && billboardCache.containsKey(part.renderable))
			{
				activeBillboards.add(part.renderable);
			}
		}
	}

	private int candidateSelectionPriority(BillboardTarget target)
	{
		if (target == null)
		{
			return Integer.MIN_VALUE;
		}

		int score = target.getRenderPriority() * 10;
		Actor owner = targetOwnerActor(target);
		Actor priorityActor = priorityHoverInteractionActor();
		Player localPlayer = client.getLocalPlayer();
		if (owner != null)
		{
			if (owner == priorityActor)
			{
				score += 1_000;
			}

			if (owner == localPlayer)
			{
				score += 900;
			}
		}
		else if (target.renderable == priorityActor)
		{
			score += 1_000;
		}
		else if (target.renderable == localPlayer)
		{
			score += 900;
		}

		return score;
	}

	private Actor targetOwnerActor(BillboardTarget target)
	{
		if (target == null)
		{
			return null;
		}

		if (target.parentActor != null)
		{
			return target.parentActor;
		}

		return target.renderable instanceof Actor ? (Actor) target.renderable : null;
	}

	private void markSuppressedStackedActors(CollectedCandidates collected)
	{
		if (collected == null)
		{
			return;
		}

		for (Map.Entry<OccupiedTileKey, List<Actor>> entry : collected.actorsByTile.entrySet())
		{
			List<Actor> actors = entry.getValue();
			if (actors == null || actors.size() < 2)
			{
				continue;
			}

			Actor topActor = collected.topActorsByTile.get(entry.getKey());
			if (topActor == null || !activeRenderableTargets.containsKey(topActor) || !billboardCache.containsKey(topActor))
			{
				continue;
			}

			for (Actor actor : actors)
			{
				if (actor != null && actor != topActor)
				{
					suppressedRenderables.add(actor);
				}
			}
		}
	}

	private void updateHoverInteractionState()
	{
		Actor hoveredActor = hoveredActor();
		Actor interactionActor = interactedActor();
		frameHoveredActor = hoveredActor;
		frameInteractionActor = interactionActor;
		if (hoveredActor == lastHoveredActor && interactionActor == lastInteractionActor)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		markHoverInteractionStateDirty(lastHoveredActor);
		markHoverInteractionStateDirty(hoveredActor);
		markHoverInteractionStateDirty(lastInteractionActor);
		markHoverInteractionStateDirty(interactionActor);
		if (interactionActor != lastInteractionActor)
		{
			markHoverInteractionStateDirty(localPlayer);
		}

		lastHoveredActor = hoveredActor;
		lastInteractionActor = interactionActor;
	}

	private void applyForcedHoverInteractionPlans(List<BillboardTarget> visibleTargets)
	{
		for (BillboardTarget target : visibleTargets)
		{
			if (shouldForceHoverInteractionRedraw(target))
			{
				frameUpdatePlans.put(target.targetKey, new FrameUpdatePlan(renderQualityScale(), true));
			}
		}
	}

	private void markHoverInteractionStateDirty(Renderable renderable)
	{
		if (renderable != null)
		{
			forceHoverInteractionRedraws.add(renderable);
		}
	}

	private boolean shouldForceHoverInteractionRedraw(BillboardTarget target)
	{
		return target != null && target.renderable != null && forceHoverInteractionRedraws.contains(target.renderable);
	}

	private void scheduleFrameUpdates(List<BillboardTarget> orderedTargets, int gameCycle)
	{
		Map<BillboardTargetKey, BillboardTarget> candidatesByKey = new HashMap<>();
		for (BillboardTarget candidate : orderedTargets)
		{
			candidatesByKey.put(candidate.targetKey, candidate);
		}

		pruneRenderQueue(candidatesByKey.keySet());
		appendNewQueueEntries(orderedTargets);
		reprioritizeRenderQueue(candidatesByKey, gameCycle);

		int maxDraws = Math.max(1, config.billboardMaxDrawsPerFrame());
		int baseRefreshInterval = Math.max(1, (int) Math.ceil((double) Math.max(1, orderedTargets.size()) / maxDraws));
		int queueBudget = renderQueue.size();
		Set<BillboardTargetKey> seenThisFrame = new HashSet<>();
		int scheduled = 0;
		while (queueBudget-- > 0 && scheduled < maxDraws && !renderQueue.isEmpty())
		{
			BillboardTargetKey key = renderQueue.pollFirst();
			renderQueueEntries.remove(key);
			if (!seenThisFrame.add(key))
			{
				continue;
			}

			BillboardTarget target = candidatesByKey.get(key);
			if (target == null)
			{
				continue;
			}

			boolean forceHoverInteractionRedraw = shouldForceHoverInteractionRedraw(target);
			UpdateHeuristicSnapshot snapshot = buildUpdateHeuristicSnapshot(target);
			BillboardUpdateState updateState = billboardUpdateStates.computeIfAbsent(key, ignored -> new BillboardUpdateState(renderQualityScale()));
			boolean bypassCadence = forceHoverInteractionRedraw
				|| !targetHasCachedBillboard(target)
				|| updateState.hasPositionChangedSinceRedraw(snapshot)
				|| updateState.hasViewChangedSinceRedraw(snapshot);
			if (!bypassCadence && !updateState.isReady(gameCycle, targetHasCachedBillboard(target)))
			{
				requeueTarget(key);
				continue;
			}

			double qualityScale = updateState.qualityScale;
			if (!forceHoverInteractionRedraw && !needsBillboardRedraw(target, qualityScale))
			{
				updateState.defer(gameCycle, baseRefreshInterval);
				requeueTarget(key);
				continue;
			}

			frameUpdatePlans.put(key, new FrameUpdatePlan(qualityScale, forceHoverInteractionRedraw));
			updateState.advance(gameCycle, baseRefreshInterval, renderQualityScale(), snapshot, minimumAnimatedRedrawInterval());
			requeueTarget(key);
			scheduled++;
		}
	}

	private void reprioritizeRenderQueue(Map<BillboardTargetKey, BillboardTarget> candidatesByKey, int gameCycle)
	{
		if (renderQueue.isEmpty())
		{
			return;
		}

		List<QueuedBillboardTarget> prioritizedTargets = new ArrayList<>(renderQueue.size());
		int queueIndex = 0;
		for (BillboardTargetKey key : renderQueue)
		{
			BillboardTarget target = candidatesByKey.get(key);
			if (target == null)
			{
				queueIndex++;
				continue;
			}

			BillboardUpdateState state = billboardUpdateStates.computeIfAbsent(key, ignored -> new BillboardUpdateState(renderQualityScale()));
			UpdateHeuristicSnapshot snapshot = buildUpdateHeuristicSnapshot(target);
			double score = computeUpdatePriorityScore(target, state, snapshot, gameCycle, queueIndex);
			prioritizedTargets.add(new QueuedBillboardTarget(key, score, queueIndex));
			state.observe(snapshot);
			queueIndex++;
		}

		prioritizedTargets.sort(Comparator
			.comparingDouble(QueuedBillboardTarget::getPriorityScore).reversed()
			.thenComparingInt(QueuedBillboardTarget::getQueueIndex));
		renderQueue.clear();
		renderQueueEntries.clear();
		for (QueuedBillboardTarget prioritizedTarget : prioritizedTargets)
		{
			requeueTarget(prioritizedTarget.key);
		}
		refreshDebugQueuePositions();
	}

	private double computeUpdatePriorityScore(
		BillboardTarget target,
		BillboardUpdateState state,
		UpdateHeuristicSnapshot snapshot,
		int gameCycle,
		int queueIndex
	)
	{
		double score = 0.0d;
		if (!targetHasCachedBillboard(target))
		{
			score += 10_000.0d;
		}

		if (shouldForceHoverInteractionRedraw(target))
		{
			score += 50_000.0d;
			if (target.renderable == priorityHoverInteractionActor())
			{
				score += 10_000.0d;
			}
			else if (target.renderable == client.getLocalPlayer())
			{
				score += 9_000.0d;
			}
		}

		Actor owner = targetOwnerActor(target);
		if (owner != null)
		{
			if (owner == client.getLocalPlayer())
			{
				score += 6_000.0d;
			}

			if (owner == priorityHoverInteractionActor())
			{
				score += 8_000.0d;
			}

			if (target.type == BillboardTargetType.ACTOR_SPOT_ANIM)
			{
				score += 2_500.0d;
			}
		}

		if (state.hasMoved(snapshot))
		{
			score += 1_200.0d;
		}

		if (state.hasAnimationChanged(snapshot))
		{
			score += 1_000.0d;
		}
		else if (snapshot.animated)
		{
			score += 450.0d;
		}

		double depthPenalty = Math.max(0.0d, snapshot.depth);
		score += 8_000.0d / Math.max(128.0d, depthPenalty + 128.0d);

		int cyclesSinceRedraw = state.cyclesSinceRedraw(gameCycle);
		score += Math.min(4_000.0d, cyclesSinceRedraw * 160.0d);

		int overdueCycles = state.overdueCycles(gameCycle);
		score += Math.min(3_000.0d, overdueCycles * 220.0d);

		score -= queueIndex * 0.01d;
		return score;
	}

	private int minimumAnimatedRedrawInterval()
	{
		int targetFramesPerSecond = Math.max(1, config.animationFrameCount());
		return Math.max(1, (int) Math.ceil(50.0d / targetFramesPerSecond));
	}

	private Actor priorityHoverInteractionActor()
	{
		return frameInteractionActor != null ? frameInteractionActor : frameHoveredActor;
	}

	private void pruneRenderQueue(Set<BillboardTargetKey> validKeys)
	{
		Iterator<BillboardTargetKey> iterator = renderQueue.iterator();
		while (iterator.hasNext())
		{
			BillboardTargetKey key = iterator.next();
			if (!validKeys.contains(key))
			{
				iterator.remove();
				renderQueueEntries.remove(key);
				debugQueuePositions.remove(key);
				billboardUpdateStates.remove(key);
				frameUpdatePlans.remove(key);
			}
		}
	}

	private void refreshDebugQueuePositions()
	{
		debugQueuePositions.clear();
		int index = 0;
		for (BillboardTargetKey key : renderQueue)
		{
			debugQueuePositions.put(key, index++);
		}
	}

	private void appendNewQueueEntries(List<BillboardTarget> orderedTargets)
	{
		List<BillboardTarget> newTargets = new ArrayList<>();
		for (BillboardTarget target : orderedTargets)
		{
			if (!renderQueueEntries.contains(target.targetKey) && !billboardUpdateStates.containsKey(target.targetKey))
			{
				newTargets.add(target);
			}
		}

		int maxDraws = Math.max(1, config.billboardMaxDrawsPerFrame());
		int newTargetCount = newTargets.size();
		for (int i = 0; i < newTargetCount; i++)
		{
			BillboardTarget target = newTargets.get(i);
			int nearPriorityIndex = (newTargetCount - 1) - i;
			double seededQualityScale = seededQualityScale(nearPriorityIndex, maxDraws);
			billboardUpdateStates.put(target.targetKey, new BillboardUpdateState(seededQualityScale));
			requeueTargetFront(target.targetKey);
		}
	}

	private void requeueTarget(BillboardTargetKey key)
	{
		if (renderQueueEntries.add(key))
		{
			renderQueue.addLast(key);
		}
	}

	private void requeueTargetFront(BillboardTargetKey key)
	{
		if (renderQueueEntries.add(key))
		{
			renderQueue.addFirst(key);
		}
	}

	private void updateActiveSnapshots()
	{
		Set<Renderable> billboardSnapshot = Collections.newSetFromMap(new IdentityHashMap<>());
		billboardSnapshot.addAll(activeBillboards);
		activeBillboardSnapshot = billboardSnapshot;
		Set<Renderable> suppressedSnapshot = Collections.newSetFromMap(new IdentityHashMap<>());
		suppressedSnapshot.addAll(suppressedRenderables);
		suppressedRenderableSnapshot = suppressedSnapshot;
		Set<TileObject> tileObjectSnapshot = Collections.newSetFromMap(new IdentityHashMap<>());
		tileObjectSnapshot.addAll(activeTileObjects);
		activeTileObjectSnapshot = tileObjectSnapshot;
	}

	private CollectedCandidates collectCandidates(WorldView worldView)
	{
		Player localPlayer = client.getLocalPlayer();
		LocalPoint localPlayerLocation = localPlayer != null ? localPlayer.getLocalLocation() : null;
		Rectangle viewport = getViewportBounds();
		List<BillboardTarget> candidates = new ArrayList<>();
		Map<OccupiedTileKey, Actor> topActorsByTile = new HashMap<>();
		Map<Actor, BillboardTarget> actorTargets = new IdentityHashMap<>();
		Map<OccupiedTileKey, List<Actor>> actorsByTile = new HashMap<>();
		List<Actor> eligibleActorsForEffects = new ArrayList<>();
		Set<EffectDedupKey> claimedActorEffects = new HashSet<>();
		Set<OccupiedTileKey> claimedActorEffectTiles = new HashSet<>();

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.NPC, config))
		{
			for (NPC npc : worldView.npcs())
			{
				if (npc == null
					|| !wasSceneRenderableDrawnLastFrame(npc)
					|| !isEligibleActor(localPlayerLocation, npc.getLocalLocation(), npc, viewport))
				{
					continue;
				}

				considerTopActorCandidate(
					topActorsByTile,
					actorsByTile,
					actorTargets,
					npc,
					BillboardTarget.forRenderable(
						BillboardTargetType.NPC,
						ClassifiedObjectType.NPC,
						npc,
						billboardDepth(npc),
						npc.getLocalLocation(),
						npc.getWorldView().getPlane()
					)
				);
				logClassificationDecision(npc, ObjectClassifier.classifyDecision(npc, client));
				eligibleActorsForEffects.add(npc);
			}
		}

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.PLAYER, config))
		{
			for (Player player : worldView.players())
			{
				if (player == null
					|| !wasSceneRenderableDrawnLastFrame(player)
					|| !isEligibleActor(localPlayerLocation, player.getLocalLocation(), player, viewport))
				{
					continue;
				}

				considerTopActorCandidate(
					topActorsByTile,
					actorsByTile,
					actorTargets,
					player,
					BillboardTarget.forRenderable(
						BillboardTargetType.PLAYER,
						ClassifiedObjectType.PLAYER,
						player,
						billboardDepth(player),
						player.getLocalLocation(),
						player.getWorldView().getPlane()
					)
				);
				logClassificationDecision(player, ObjectClassifier.classifyDecision(player, client));
				eligibleActorsForEffects.add(player);
			}
		}

		for (BillboardTarget actorTarget : actorTargets.values())
		{
			candidates.add(actorTarget);
		}

		for (Actor actor : eligibleActorsForEffects)
		{
			addActorSpotAnimCandidates(
				candidates,
				actor,
				viewport,
				claimedActorEffects,
				claimedActorEffectTiles,
				!isStackedActorTile(actorsByTile, actor)
			);
		}

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.PROJECTILE, config))
		{
			for (Projectile projectile : client.getProjectiles())
			{
				if (projectile == null
					|| !wasSceneRenderableDrawnLastFrame(projectile)
					|| !isEligibleProjectile(localPlayerLocation, projectile, viewport))
				{
					continue;
				}

				candidates.add(BillboardTarget.forRenderable(
					BillboardTargetType.PROJECTILE,
					ClassifiedObjectType.PROJECTILE,
					projectile,
					billboardDepth(projectile),
					projectileLocalPoint(projectile),
					projectile.getFloor()
				));
				logClassificationDecision(projectile, ObjectClassifier.classifyDecision(projectile, client));
			}
		}

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.EFFECT, config))
		{
			for (GraphicsObject graphicsObject : worldView.getGraphicsObjects())
			{
				if (graphicsObject == null
					|| !wasSceneRenderableDrawnLastFrame(graphicsObject)
					|| !isEligibleGraphicsObject(localPlayerLocation, graphicsObject, viewport))
				{
					continue;
				}

				if (claimedActorEffects.contains(EffectDedupKey.of(graphicsObject)))
				{
					continue;
				}

				OccupiedTileKey graphicsObjectTile = OccupiedTileKey.of(graphicsObject.getLocation(), graphicsObject.getLevel());
				if (graphicsObjectTile != null && claimedActorEffectTiles.contains(graphicsObjectTile))
				{
					continue;
				}

				candidates.add(BillboardTarget.forRenderable(
					BillboardTargetType.GRAPHICS_OBJECT,
					ClassifiedObjectType.EFFECT,
					graphicsObject,
					billboardDepth(graphicsObject),
					graphicsObject.getLocation(),
					graphicsObject.getLevel()
				));
				logClassificationDecision(graphicsObject, ObjectClassifier.classifyDecision(graphicsObject, client));
			}
		}

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.GROUND_ITEM, config))
		{
			for (Map.Entry<TileItem, GroundItemBillboard> entry : groundItems.entrySet())
			{
				TileItem item = entry.getKey();
				GroundItemBillboard groundItem = entry.getValue();
				if (item == null
					|| groundItem == null
					|| !wasSceneRenderableDrawnLastFrame(item)
					|| !isEligibleGroundItem(localPlayerLocation, item, groundItem, viewport))
				{
					continue;
				}

				candidates.add(BillboardTarget.forGroundItem(item, groundItem, billboardDepth(item)));
				logClassificationDecision(item, ObjectClassifier.classifyDecision(item, client));
			}
		}

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.OBJECT, config) || ObjectClassifier.isEnabled(ClassifiedObjectType.EFFECT, config))
		{
			for (ObservedTileObject observed : visibleTileObjects.values())
			{
				if (!isEligibleTileObject(localPlayerLocation, observed, viewport))
				{
					continue;
				}

				ClassifiedObjectType classifiedType = resolveObservedTileObjectType(observed);
				if (classifiedType == ClassifiedObjectType.EFFECT)
				{
					if (ObjectClassifier.isEnabled(ClassifiedObjectType.EFFECT, config))
					{
						candidates.add(BillboardTarget.forTileObject(observed, billboardDepth(observed)));
					}
				}
				else if (classifiedType == ClassifiedObjectType.OBJECT && ObjectClassifier.isEnabled(ClassifiedObjectType.OBJECT, config))
				{
					candidates.add(BillboardTarget.forTileObject(observed, billboardDepth(observed)));
				}
			}
		}

		return new CollectedCandidates(candidates, topActorsByTile, actorsByTile);
	}

	private boolean isStackedActorTile(Map<OccupiedTileKey, List<Actor>> actorsByTile, Actor actor)
	{
		if (actor == null)
		{
			return false;
		}

		OccupiedTileKey tileKey = OccupiedTileKey.of(actor.getLocalLocation(), actor.getWorldView().getPlane());
		if (tileKey == null)
		{
			return false;
		}

		List<Actor> actors = actorsByTile.get(tileKey);
		return actors != null && actors.size() > 1;
	}

	private void considerTopActorCandidate(
		Map<OccupiedTileKey, Actor> topActorsByTile,
		Map<OccupiedTileKey, List<Actor>> actorsByTile,
		Map<Actor, BillboardTarget> actorTargets,
		Actor actor,
		BillboardTarget candidate
	)
	{
		if (actor == null || candidate == null)
		{
			return;
		}

		LocalPoint localPoint = actor.getLocalLocation();
		OccupiedTileKey tileKey = OccupiedTileKey.of(localPoint, actor.getWorldView().getPlane());
		if (tileKey == null)
		{
			actorTargets.put(actor, candidate);
			return;
		}

		actorsByTile.computeIfAbsent(tileKey, ignored -> new ArrayList<>()).add(actor);
		Actor currentActor = topActorsByTile.get(tileKey);
		if (currentActor == null)
		{
			topActorsByTile.put(tileKey, actor);
			actorTargets.put(actor, candidate);
			return;
		}

		BillboardTarget currentTarget = actorTargets.get(currentActor);
		if (currentTarget != null && currentTarget.getDepth() <= candidate.getDepth())
		{
			return;
		}

		actorTargets.remove(currentActor);
		topActorsByTile.put(tileKey, actor);
		actorTargets.put(actor, candidate);
	}

	private BillboardTarget buildActiveTarget(WorldView worldView, TileObject tileObject)
	{
		ObservedTileObject observed = visibleTileObjects.get(tileObject);
		if (observed == null)
		{
			return null;
		}

		resolveObservedTileObjectType(observed);
		return BillboardTarget.forTileObject(observed, billboardDepth(observed));
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
			false,
			false,
			VerticalAnchor.BOTTOM,
			frameDebugInfo
		);
	}

	private BillboardRenderRequest buildActorRenderRequest(Actor actor)
	{
		LocalPoint localPoint = actor.getLocalLocation();
		boolean shouldInteractOutline = config.enableBillboardInteractionOutline() && actor == frameInteractionActor;
		boolean shouldHoverOutline = !shouldInteractOutline && config.enableBillboardHoverOutline() && actor == frameHoveredActor;
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
			shouldHoverOutline,
			shouldInteractOutline,
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
			relativePitch(actorSpotAnim),
			actorSpotAnim.getId(),
			actorSpotAnim.getFrame(),
			-1,
			-1,
			-1,
			false,
			false,
			VerticalAnchor.CENTER,
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
			false,
			false,
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
			relativePitch(graphicsObject),
			graphicsObject.getId(),
			snappedFrame,
			-1,
			-1,
			-1,
			false,
			false,
			VerticalAnchor.CENTER,
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
			relativePitch(item),
			item.getId(),
			item.getQuantity(),
			-1,
			-1,
			item.getId(),
			false,
			false,
			VerticalAnchor.BOTTOM,
			null
		);
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
		Set<OccupiedTileKey> claimedActorEffectTiles,
		boolean claimTileForGraphicsDedup
	)
	{
		if (actor == null || !ObjectClassifier.isEnabled(ClassifiedObjectType.EFFECT, config))
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
			if (!wasSceneRenderableDrawnLastFrame(actorSpotAnim) || !isEligibleActorSpotAnim(actor, actorSpotAnim, viewport))
			{
				continue;
			}

			candidates.add(BillboardTarget.forActorSpotAnim(actorSpotAnim, actor, billboardDepth(actorSpotAnim, actor)));
			logClassificationDecision(
				actorSpotAnim,
				ObjectClassifier.classifyDecision(actorSpotAnim, client),
				classificationDebugApiType(actor) + ":" + classificationDebugId(actor) + ":" + classificationDebugName(actor)
			);
			claimedActorEffects.add(EffectDedupKey.of(actorSpotAnim, actor));
			if (claimTileForGraphicsDedup)
			{
				claimedActorEffectTiles.add(OccupiedTileKey.of(actor.getLocalLocation(), actor.getWorldView().getPlane()));
			}
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

	private boolean targetHasCachedBillboard(BillboardTarget target)
	{
		if (target == null)
		{
			return false;
		}

		if (target.type == BillboardTargetType.TILE_OBJECT)
		{
			return targetHasCachedTileObjectBillboard(target);
		}

		return billboardCache.containsKey(target.renderable);
	}

	private boolean targetHasCachedTileObjectBillboard(BillboardTarget target)
	{
		if (target == null || target.observedTileObject == null)
		{
			return false;
		}

		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			if (part.renderable != null && billboardCache.containsKey(part.renderable))
			{
				return true;
			}
		}

		return false;
	}

	private boolean tileObjectHasCachedBillboard(TileObject tileObject)
	{
		ObservedTileObject observed = visibleTileObjects.get(tileObject);
		if (observed == null)
		{
			return false;
		}

		for (ObjectRenderablePart part : observed.parts)
		{
			if (part.renderable != null && billboardCache.containsKey(part.renderable))
			{
				return true;
			}
		}

		return false;
	}

	private boolean needsBillboardRedraw(BillboardTarget target, double qualityScale)
	{
		if (target.type == BillboardTargetType.TILE_OBJECT)
		{
			return needsTileObjectBillboardRedraw(target, qualityScale);
		}

		BillboardRenderRequest request = buildRenderRequest(target);
		return request != null && needsBillboardRedraw(request, qualityScale);
	}

	private boolean needsTileObjectBillboardRedraw(BillboardTarget target, double qualityScale)
	{
		if (target == null || target.observedTileObject == null)
		{
			return false;
		}

		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			BillboardRenderRequest request = buildRenderRequest(target, part);
			if (request != null && needsBillboardRedraw(request, qualityScale))
			{
				return true;
			}
		}

		return false;
	}

	private boolean needsBillboardRedraw(BillboardRenderRequest request, double qualityScale)
	{
		Renderable renderable = request.renderable;
		if (renderable == null)
		{
			return false;
		}

		CachedBillboard cached = billboardCache.get(renderable);
		if (cached == null)
		{
			return true;
		}

		long nowMillis = System.currentTimeMillis();
		if (nowMillis - cached.lastRedrawMillis() > CACHE_TTL_MILLIS)
		{
			return true;
		}

		return !cached.matchesPreviewKey(buildPreviewCacheKey(request, qualityScale, nowMillis));
	}

	private UpdateHeuristicSnapshot buildUpdateHeuristicSnapshot(BillboardTarget target)
	{
		if (target == null)
		{
			return UpdateHeuristicSnapshot.empty();
		}

		if (target.type == BillboardTargetType.TILE_OBJECT)
		{
			return buildTileObjectHeuristicSnapshot(target);
		}

		BillboardRenderRequest request = buildRenderRequest(target);
		if (request == null)
		{
			return UpdateHeuristicSnapshot.forDepth(target.getDepth());
		}

		return UpdateHeuristicSnapshot.fromRequest(request, target.getDepth(), animatedTextureOffsetStateHash(request.animatedTextureId, System.currentTimeMillis()));
	}

	private UpdateHeuristicSnapshot buildTileObjectHeuristicSnapshot(BillboardTarget target)
	{
		if (target == null || target.observedTileObject == null)
		{
			return UpdateHeuristicSnapshot.empty();
		}

		long positionKey = 0L;
		int animationHash = 1;
		int viewHash = 1;
		boolean animated = false;
		boolean sawPosition = false;
		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			if (part.localPoint != null)
			{
				positionKey = (31L * positionKey) + UpdateHeuristicSnapshot.positionKey(part.localPoint, part.plane);
				sawPosition = true;
			}

			BillboardRenderRequest request = buildRenderRequest(target, part);
			if (request != null)
			{
				viewHash = (31 * viewHash) + request.relativeYaw;
				viewHash = (31 * viewHash) + request.relativePitch;
			}

			if (part.renderable instanceof DynamicObject)
			{
				DynamicObject dynamicObject = (DynamicObject) part.renderable;
				int animationId = dynamicObject.getAnimation() != null ? dynamicObject.getAnimation().getId() : -1;
				animationHash = (31 * animationHash) + animationId;
				animationHash = (31 * animationHash) + dynamicObject.getAnimFrame();
				animated = animated || dynamicObject.getAnimation() != null;
			}
			else
			{
				animationHash = (31 * animationHash) + System.identityHashCode(part.renderable);
			}
		}

		return new UpdateHeuristicSnapshot(
			target.getDepth(),
			sawPosition ? positionKey : Long.MIN_VALUE,
			animationHash,
			viewHash,
			animated
		);
	}

	private RenderedBillboardImage renderBillboardImage(List<FaceDraw> faces, Rectangle bounds, int outlinePadding, double qualityScaleOverride, boolean shouldHoverOutline, boolean shouldInteractOutline)
	{
		Rectangle imageBounds = expandedBounds(bounds, outlinePadding);
		double qualityScale = renderQualityScale(qualityScaleOverride);
		int imageWidth = Math.max(1, (int) Math.round(imageBounds.width * qualityScale));
		int imageHeight = Math.max(1, (int) Math.round(imageBounds.height * qualityScale));
		if (!isUsableDrawSize(imageWidth, imageHeight))
		{
			return null;
		}

		BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
		int[] imagePixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		for (FaceDraw face : faces)
		{
			if (face.isTextured())
			{
				rasterizeTexturedFace(imagePixels, imageWidth, imageHeight, imageBounds, qualityScale, face);
				continue;
			}

			Color snappedColor = NpcSnapColorBanding.snapToRamp(face.getColor(), config.billboardColorBands());
			rasterizeSolidFace(imagePixels, imageWidth, imageHeight, imageBounds, qualityScale, face, snappedColor.getRGB());
		}

		int[] exteriorOutlineIndices = BillboardOutlineRenderer.captureExteriorBoundaryIndices(image, outlineScratch);

		if (hasAnyEdgeEffect())
		{
			BillboardOutlineRenderer.applyOutline(
				image,
				outlineScratch,
				config.enableBillboardHighlightOutline(),
				config.enableBillboardShadowOutline(),
				config.enableBillboardSpriteOutline(),
				config.enableBillboardSpriteShadows(),
				config.enableBillboardHighlightInline(),
				config.enableBillboardShadowInline(),
				config.enableBillboardSpriteInline(),
				config.billboardSpriteOutlineColor()
			);
		}

		if (shouldHoverOutline || shouldInteractOutline)
		{
			applyHoverInteractionOutline(image, exteriorOutlineIndices, shouldHoverOutline, shouldInteractOutline);
		}

		return new RenderedBillboardImage(image);
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
		expireBillboardCache(nowMillis);
		expireIdleEntries(textureCache.entrySet().iterator(), nowMillis);
	}

	private void expireBillboardCache(long nowMillis)
	{
		Iterator<Map.Entry<Renderable, CachedBillboard>> iterator = billboardCache.entrySet().iterator();
		while (iterator.hasNext())
		{
			Map.Entry<Renderable, CachedBillboard> entry = iterator.next();
			CachedBillboard cached = entry.getValue();
			if (nowMillis - cached.lastUsedMillis() > CACHE_TTL_MILLIS)
			{
				cached.flush();
				iterator.remove();
			}
		}
	}

	private void putCachedBillboard(Renderable renderable, CachedBillboard cached)
	{
		CachedBillboard previous = billboardCache.put(renderable, cached);
		if (previous != null && previous != cached)
		{
			previous.flush();
		}
	}

	private void removeCachedBillboard(Renderable renderable)
	{
		CachedBillboard cached = billboardCache.remove(renderable);
		if (cached != null)
		{
			cached.flush();
		}
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
		return renderQualityScale(config.renderBillboardQuality() / 100.0d);
	}

	private int qualityKey()
	{
		return qualityKey(renderQualityScale());
	}

	private double renderQualityScale(double qualityScale)
	{
		return Math.max(MIN_RENDER_QUALITY, Math.min(1.0d, qualityScale));
	}

	private int qualityKey(double qualityScale)
	{
		return (int) Math.round(renderQualityScale(qualityScale) * 10_000.0d);
	}

	private void ensureSpriteScratchCapacity(int vertexCount)
	{
		if (spriteXScratch.length >= vertexCount)
		{
			return;
		}

		spriteXScratch = new float[vertexCount];
		spriteYScratch = new float[vertexCount];
		spriteDepthScratch = new float[vertexCount];
	}

	private double seededQualityScale(int nearPriorityIndex, int maxUpdatesPerFrame)
	{
		int updatesPerFrame = Math.max(1, maxUpdatesPerFrame);
		int maxBootstrapEntries = updatesPerFrame * 3;
		int clampedPriorityIndex = Math.min(Math.max(0, nearPriorityIndex), Math.max(0, maxBootstrapEntries - 1));
		int qualityTier = clampedPriorityIndex / updatesPerFrame;
		double qualityScale = renderQualityScale();
		for (int i = 0; i < qualityTier; i++)
		{
			qualityScale *= 0.5d;
		}

		return renderQualityScale(qualityScale);
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
					faces.add(new FaceDraw(
						Math.round(spriteX[a]),
						Math.round(spriteY[a]),
						Math.round(spriteX[b]),
						Math.round(spriteY[b]),
						Math.round(spriteX[c]),
						Math.round(spriteY[c]),
						color,
						depth,
						textureSample,
						textureUvs
					));
					continue;
				}
			}

			faces.add(new FaceDraw(
				Math.round(spriteX[a]),
				Math.round(spriteY[a]),
				Math.round(spriteX[b]),
				Math.round(spriteY[b]),
				Math.round(spriteX[c]),
				Math.round(spriteY[c]),
				color,
				depth,
				null,
				null
			));
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
			minX = Math.min(minX, Math.min(face.x0, Math.min(face.x1, face.x2)));
			minY = Math.min(minY, Math.min(face.y0, Math.min(face.y1, face.y2)));
			maxX = Math.max(maxX, Math.max(face.x0, Math.max(face.x1, face.x2)));
			maxY = Math.max(maxY, Math.max(face.y0, Math.max(face.y1, face.y2)));
		}

		if (minX == Integer.MAX_VALUE)
		{
			return new Rectangle();
		}

		return new Rectangle(minX, minY, Math.max(1, (maxX - minX) + 1), Math.max(1, (maxY - minY) + 1));
	}

	private BillboardRenderResult renderRenderableBillboard(BillboardRenderRequest request, FrameUpdatePlan updatePlan, int queuePosition)
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

		long nowMillis = System.currentTimeMillis();
		CachedBillboard cached = billboardCache.get(renderable);
		boolean cacheInvalidated = false;
		boolean spriteRedrawn = false;
		if (updatePlan != null)
		{
			float[] verticesX = model.getVerticesX();
			float[] verticesY = model.getVerticesY();
			float[] verticesZ = model.getVerticesZ();
			ensureSpriteScratchCapacity(vertexCount);
			float[] spriteX = spriteXScratch;
			float[] spriteY = spriteYScratch;
			float[] spriteDepth = spriteDepthScratch;
			double yawSin = Perspective.SINE[request.relativeYaw] / 65536.0;
			double yawCos = Perspective.COSINE[request.relativeYaw] / 65536.0;
			int inversePitch = Math.floorMod(-request.relativePitch, FULL_CIRCLE);
			double pitchSin = Perspective.SINE[inversePitch] / 65536.0;
			double pitchCos = Perspective.COSINE[inversePitch] / 65536.0;
			for (int i = 0; i < vertexCount; i++)
			{
				double rotatedX = (verticesX[i] * yawCos) + (verticesZ[i] * yawSin);
				double rotatedZ = (verticesZ[i] * yawCos) - (verticesX[i] * yawSin);
				spriteX[i] = (float) rotatedX;
				spriteY[i] = (float) ((verticesY[i] * pitchCos) - (rotatedZ * pitchSin));
				spriteDepth[i] = (float) ((rotatedZ * pitchCos) + (verticesY[i] * pitchSin));
			}

			BuiltFaces builtFaces = buildFaces(model, spriteX, spriteY, spriteDepth, nowMillis, request.animatedTextureId);
			if (!builtFaces.faces.isEmpty())
			{
				List<FaceDraw> faces = builtFaces.faces;
				faces.sort(Comparator.comparingDouble(FaceDraw::getDepth).reversed());
				Rectangle sourceBounds = computeBounds(faces);
				if (isUsableSourceBounds(sourceBounds))
				{
					int outlinePadding = outlinePadding();
					Rectangle imageBounds = expandedBounds(sourceBounds, outlinePadding);
					if (buildDrawRect(request, renderable, imageBounds) == null)
					{
						return null;
					}

					BillboardCacheKey cacheKey = buildCacheKey(
						request,
						outlinePadding,
						builtFaces.textureStateHash,
						qualityKey(updatePlan.qualityScale),
						animatedTextureOffsetStateHash(request.animatedTextureId, nowMillis)
					);
					cacheInvalidated = updatePlan.forceHoverInteractionRedraw
						|| shouldRefreshCache(renderable, cacheKey, imageBounds, nowMillis);
					if (cacheInvalidated)
					{
						RenderedBillboardImage rendered = renderBillboardImage(
							faces,
							sourceBounds,
							outlinePadding,
							updatePlan.qualityScale,
							request.shouldHoverOutline,
							request.shouldInteractOutline
						);
						if (rendered != null)
						{
							cached = new CachedBillboard(cacheKey, imageBounds, rendered.image, nowMillis);
							cached.markDebugFrameRedrawn();
							putCachedBillboard(renderable, cached);
							spriteRedrawn = true;
						}
					}
					else if (cached != null)
					{
						cached.touch(nowMillis);
					}

					if (cacheInvalidated)
					{
						forceHoverInteractionRedraws.remove(renderable);
					}
				}
			}
		}

		if (cached == null)
		{
			return null;
		}

		if (!spriteRedrawn)
		{
			cached.touch(nowMillis);
		}

		cached.stateDebugInfo(buildStateDebugInfo(request, queuePosition));

		Rectangle drawRect = buildDrawRect(request, renderable, cached.bounds);
		if (drawRect == null)
		{
			return null;
		}

		boolean spriteDirty = cached.consumeDirty();
		return new BillboardRenderResult(drawRect, cached.image);
	}

	private BillboardCacheKey buildCacheKey(
		BillboardRenderRequest request,
		int outlinePadding,
		int textureStateHash,
		int renderQualityKey,
		int animatedTextureOffsetStateHash)
	{
		int modelStateHash = modelStateHash(request.model);
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
			config.enableBillboardSpriteShadows(),
			config.enableBillboardHighlightInline(),
			config.enableBillboardShadowInline(),
			config.enableBillboardSpriteInline(),
			config.billboardSpriteOutlineColor().getRGB(),
			request.shouldHoverOutline,
			request.shouldInteractOutline,
			config.billboardHoverOutlineColor().getRGB(),
			config.billboardInteractionOutlineColor().getRGB(),
			renderQualityKey,
			modelStateHash,
			textureStateHash,
			animatedTextureOffsetStateHash
		);
	}

	private BillboardCachePreviewKey buildPreviewCacheKey(BillboardRenderRequest request, double qualityScale, long nowMillis)
	{
		int modelStateHash = modelStateHash(request.model);
		return new BillboardCachePreviewKey(
			request.animationId,
			request.animationFrame,
			request.poseAnimationId,
			request.poseAnimationFrame,
			request.relativeYaw,
			request.relativePitch,
			config.billboardColorBands(),
			config.billboardLightBoostPercent(),
			outlinePadding(),
			config.enableBillboardHighlightOutline(),
			config.enableBillboardShadowOutline(),
			config.enableBillboardSpriteOutline(),
			config.enableBillboardSpriteShadows(),
			config.enableBillboardHighlightInline(),
			config.enableBillboardShadowInline(),
			config.enableBillboardSpriteInline(),
			config.billboardSpriteOutlineColor().getRGB(),
			request.shouldHoverOutline,
			request.shouldInteractOutline,
			config.billboardHoverOutlineColor().getRGB(),
			config.billboardInteractionOutlineColor().getRGB(),
			qualityKey(qualityScale),
			modelStateHash,
			animatedTextureOffsetStateHash(request.animatedTextureId, nowMillis)
		);
	}

	private static int modelStateHash(Model model)
	{
		if (model == null)
		{
			return 0;
		}

		int hash = 1;
		hash = (31 * hash) + model.getVerticesCount();
		hash = (31 * hash) + model.getModelHeight();
		hash = sampleFloatArrayHash(hash, model.getVerticesX());
		hash = sampleFloatArrayHash(hash, model.getVerticesY());
		hash = sampleFloatArrayHash(hash, model.getVerticesZ());
		hash = sampleByteArrayHash(hash, model.getFaceTransparencies());
		return hash;
	}

	private static int sampleFloatArrayHash(int seed, float[] values)
	{
		if (values == null)
		{
			return (31 * seed) - 1;
		}

		int hash = (31 * seed) + values.length;
		if (values.length > 0)
		{
			hash = (31 * hash) + Float.floatToIntBits(values[0]);
			hash = (31 * hash) + Float.floatToIntBits(values[values.length / 2]);
			hash = (31 * hash) + Float.floatToIntBits(values[values.length - 1]);
		}
		return hash;
	}

	private static int sampleByteArrayHash(int seed, byte[] values)
	{
		if (values == null)
		{
			return (31 * seed) - 1;
		}

		int hash = (31 * seed) + values.length;
		if (values.length > 0)
		{
			hash = (31 * hash) + values[0];
			hash = (31 * hash) + values[values.length / 2];
			hash = (31 * hash) + values[values.length - 1];
		}
		return hash;
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

		float x0 = scaleCoordinate(face.x0, imageBounds.x, qualityScale);
		float y0 = scaleCoordinate(face.y0, imageBounds.y, qualityScale);
		float x1 = scaleCoordinate(face.x1, imageBounds.x, qualityScale);
		float y1 = scaleCoordinate(face.y1, imageBounds.y, qualityScale);
		float x2 = scaleCoordinate(face.x2, imageBounds.x, qualityScale);
		float y2 = scaleCoordinate(face.y2, imageBounds.y, qualityScale);

		float area = BillboardTriangleRasterizer.edge(x0, y0, x1, y1, x2, y2);
		if (Math.abs(area) < 1.0e-6f)
		{
			return;
		}

		int minX = BillboardTriangleRasterizer.clampRasterCoordinate((int) Math.floor(Math.min(x0, Math.min(x1, x2))), imageWidth);
		int maxX = BillboardTriangleRasterizer.clampRasterCoordinate((int) Math.ceil(Math.max(x0, Math.max(x1, x2))), imageWidth);
		int minY = BillboardTriangleRasterizer.clampRasterCoordinate((int) Math.floor(Math.min(y0, Math.min(y1, y2))), imageHeight);
		int maxY = BillboardTriangleRasterizer.clampRasterCoordinate((int) Math.ceil(Math.max(y0, Math.max(y1, y2))), imageHeight);
		if (minX > maxX || minY > maxY)
		{
			return;
		}

		Color shade = NpcSnapColorBanding.snapToRamp(face.getColor(), config.billboardColorBands());
		TextureUvs textureUvs = face.getTextureUvs();
		for (int y = minY; y <= maxY; y++)
		{
			float py = y + 0.5f;
			int row = y * imageWidth;
			for (int x = minX; x <= maxX; x++)
			{
				float px = x + 0.5f;
				float w0 = BillboardTriangleRasterizer.edge(x1, y1, x2, y2, px, py) / area;
				float w1 = BillboardTriangleRasterizer.edge(x2, y2, x0, y0, px, py) / area;
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
				int pixelIndex = row + x;
				imagePixels[pixelIndex] = BillboardTriangleRasterizer.blendPixel(imagePixels[pixelIndex], shadedPixel);
			}
		}
	}

	private static void rasterizeSolidFace(
		int[] imagePixels,
		int imageWidth,
		int imageHeight,
		Rectangle imageBounds,
		double qualityScale,
		FaceDraw face,
		int argb)
	{
		BillboardTriangleRasterizer.rasterizeSolidTriangle(
			imagePixels,
			imageWidth,
			imageHeight,
			scaleCoordinate(face.x0, imageBounds.x, qualityScale),
			scaleCoordinate(face.y0, imageBounds.y, qualityScale),
			scaleCoordinate(face.x1, imageBounds.x, qualityScale),
			scaleCoordinate(face.y1, imageBounds.y, qualityScale),
			scaleCoordinate(face.x2, imageBounds.x, qualityScale),
			scaleCoordinate(face.y2, imageBounds.y, qualityScale),
			argb
		);
	}

	private static float scaleCoordinate(int coordinate, int origin, double qualityScale)
	{
		return (float) ((coordinate - origin) * qualityScale);
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

	private int animatedTextureOffsetStateHash(int animatedTextureId, long nowMillis)
	{
		if (!isAnimatedTextureId(animatedTextureId))
		{
			return 0;
		}

		return Float.floatToIntBits(animatedTextureVOffset(nowMillis, null));
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

	private Rectangle buildDrawRect(BillboardRenderRequest request, Renderable renderable, Rectangle billboardBounds)
	{
		Point basePoint = Perspective.localToCanvas(client, request.localPoint, request.plane, request.verticalOffset);
		if (basePoint == null)
		{
			return null;
		}

		Point centerPoint = Perspective.localToCanvas(client, request.localPoint, request.plane, request.verticalOffset + (renderable.getModelHeight() / 2));
		Point topPoint = Perspective.localToCanvas(client, request.localPoint, request.plane, request.verticalOffset + renderable.getModelHeight());
		double distance = cameraDistance(request.localPoint, request.plane, request.verticalOffset + (renderable.getModelHeight() / 2.0));
		if (!isUsableDistance(distance))
		{
			return null;
		}

		double perspectiveScale = client.get3dZoom() / Math.max(1.0, distance);
		int distanceHeight = scaledSize(billboardBounds.height, perspectiveScale);
		int projectedHeight = projectedHeight(basePoint, topPoint);
		int targetHeight = distanceHeight > 0 ? distanceHeight : projectedHeight;
		int targetWidth = aspectWidth(billboardBounds, targetHeight);
		int anchorX = request.verticalAnchor == VerticalAnchor.CENTER && centerPoint != null ? centerPoint.getX() : basePoint.getX();
		int anchorY = request.verticalAnchor == VerticalAnchor.CENTER && centerPoint != null ? centerPoint.getY() : basePoint.getY();
		if (!isUsableCanvasCoordinate(anchorX) || !isUsableCanvasCoordinate(anchorY))
		{
			return null;
		}

		return buildDrawRect(billboardBounds, anchorX, anchorY, targetWidth, targetHeight);
	}

	private int outlinePadding()
	{
		return config.enableBillboardHighlightOutline()
			|| config.enableBillboardShadowOutline()
			|| config.enableBillboardSpriteOutline()
			|| config.enableBillboardSpriteShadows()
			|| config.enableBillboardHoverOutline()
			|| config.enableBillboardInteractionOutline()
			? OUTLINE_PADDING
			: 0;
	}

	private boolean hasAnyEdgeEffect()
	{
		return config.enableBillboardHighlightOutline()
			|| config.enableBillboardShadowOutline()
			|| config.enableBillboardSpriteOutline()
			|| config.enableBillboardSpriteShadows()
			|| config.enableBillboardHighlightInline()
			|| config.enableBillboardShadowInline()
			|| config.enableBillboardSpriteInline();
	}

	private void applyHoverInteractionOutline(BufferedImage image, int[] exteriorOutlineIndices, boolean shouldHoverOutline, boolean shouldInteractOutline)
	{
		if (image == null || exteriorOutlineIndices == null || exteriorOutlineIndices.length == 0)
		{
			return;
		}

		if (!(image.getRaster().getDataBuffer() instanceof DataBufferInt))
		{
			return;
		}

		Color color = shouldInteractOutline
			? config.billboardInteractionOutlineColor()
			: shouldHoverOutline ? config.billboardHoverOutlineColor() : null;
		if (color == null || color.getAlpha() == 0)
		{
			return;
		}

		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		int argb = color.getRGB();
		for (int index : exteriorOutlineIndices)
		{
			pixels[index] = argb;
		}
	}

	private Actor hoveredActor()
	{
		MenuEntry[] menuEntries = client.getMenuEntries();
		if (menuEntries == null || menuEntries.length == 0)
		{
			return null;
		}

		MenuEntry hoveredEntry = client.isMenuOpen()
			? hoveredMenuEntry(menuEntries)
			: menuEntries[menuEntries.length - 1];
		if (hoveredEntry == null || !isActorHoverAction(hoveredEntry.getType()))
		{
			return null;
		}

		return hoveredEntry.getActor();
	}

	private Actor interactedActor()
	{
		if (interactedActor != null)
		{
			return interactedActor;
		}

		Player localPlayer = client.getLocalPlayer();
		return localPlayer != null ? localPlayer.getInteracting() : null;
	}

	private MenuEntry hoveredMenuEntry(MenuEntry[] menuEntries)
	{
		int menuX = client.getMenuX();
		int menuY = client.getMenuY();
		int menuWidth = client.getMenuWidth();
		Point mouse = client.getMouseCanvasPosition();
		int row = mouse.getY() - menuY - 19;
		if (row < 0)
		{
			return menuEntries[menuEntries.length - 1];
		}

		row = (menuEntries.length - 1) - (row / 15);
		if (mouse.getX() > menuX && mouse.getX() < menuX + menuWidth && row >= 0 && row < menuEntries.length)
		{
			return menuEntries[row];
		}

		return menuEntries[menuEntries.length - 1];
	}

	private static boolean isActorHoverAction(MenuAction action)
	{
		if (action == null)
		{
			return false;
		}

		switch (action)
		{
			case ITEM_USE_ON_NPC:
			case WIDGET_TARGET_ON_NPC:
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case ITEM_USE_ON_PLAYER:
			case WIDGET_TARGET_ON_PLAYER:
			case PLAYER_FIRST_OPTION:
			case PLAYER_SECOND_OPTION:
			case PLAYER_THIRD_OPTION:
			case PLAYER_FOURTH_OPTION:
			case PLAYER_FIFTH_OPTION:
			case PLAYER_SIXTH_OPTION:
			case PLAYER_SEVENTH_OPTION:
			case PLAYER_EIGHTH_OPTION:
				return true;
			default:
				return false;
		}
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

		return parts.isEmpty() ? null : new ObservedTileObject(tileObject, parts, ClassifiedObjectType.UNKNOWN);
	}

	private ClassifiedObjectType resolveObservedTileObjectType(ObservedTileObject observed)
	{
		if (observed == null)
		{
			return ClassifiedObjectType.UNKNOWN;
		}

		ObjectClassifier.ClassificationDecision decision = classifyObservedTileObjectDecision(observed);
		logObservedTileObjectClassification(observed, decision);
		ClassifiedObjectType classifiedType = decision.classification;
		if (classifiedType == ClassifiedObjectType.UNKNOWN)
		{
			return observed.classifiedType;
		}

		observed.classifiedType = classifiedType;
		return classifiedType;
	}

	private ObjectClassifier.ClassificationDecision classifyObservedTileObjectDecision(ObservedTileObject observed)
	{
		if (observed == null || observed.tileObject == null)
		{
			return ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "observed == null || observed.tileObject == null");
		}

		boolean hasEffectLikePart = observedHasEffectLikePart(observed);
		TileObject tileObject = observed.tileObject;
		if (tileObject instanceof GameObject)
		{
			return hasEffectLikePart
				? ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.EFFECT, "observed GameObject has effect-like part")
				: ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.OBJECT, "observed GameObject has no effect-like part");
		}

		if (tileObject instanceof DecorativeObject)
		{
			return hasEffectLikePart
				? ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.EFFECT, "observed DecorativeObject has effect-like part")
				: ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "observed DecorativeObject has no effect-like part");
		}

		if (tileObject instanceof WallObject)
		{
			return hasEffectLikePart
				? ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.EFFECT, "observed WallObject has effect-like part")
				: ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "observed WallObject has no effect-like part");
		}

		if (tileObject instanceof GroundObject)
		{
			return hasEffectLikePart
				? ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.EFFECT, "observed GroundObject has effect-like part")
				: ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "observed GroundObject has no effect-like part");
		}

		return ObjectClassifier.ClassificationDecision.of(ClassifiedObjectType.UNKNOWN, "observed tileObject wrapper not handled");
	}

	private boolean observedHasEffectLikePart(ObservedTileObject observed)
	{
		if (observed == null || observed.parts == null)
		{
			return false;
		}

		for (ObjectRenderablePart part : observed.parts)
		{
			if (part == null || part.renderable == null)
			{
				continue;
			}

			if (ObjectClassifier.classifyRenderable(part.renderable) == ClassifiedObjectType.EFFECT)
			{
				return true;
			}
		}

		return false;
	}

	private void logClassificationDecision(Object source, ObjectClassifier.ClassificationDecision decision)
	{
		logClassificationDecision(source, decision, null);
	}

	private void logClassificationDecision(Object source, ObjectClassifier.ClassificationDecision decision, String extraTypes)
	{
		if (!log.isDebugEnabled() || source == null || decision == null)
		{
			return;
		}

		String message = classificationDebugMessage(source, decision, extraTypes);
		String previousMessage = classificationDebugMessages.put(source, message);
		if (message.equals(previousMessage))
		{
			return;
		}

		log.debug(message);
	}

	private String classificationDebugMessage(Object source, ObjectClassifier.ClassificationDecision decision, String extraTypes)
	{
		String type = classificationDebugApiType(source);
		String implType = source.getClass().getSimpleName();
		String id = classificationDebugId(source);
		String name = classificationDebugName(source);
		String position = classificationDebugPosition(source);
		String relatedTypes = extraTypes == null || extraTypes.isEmpty() ? "-" : extraTypes;
		return "Classification: Type: " + type
			+ ", Impl: " + implType
			+ ", ID: " + id
			+ ", Name: " + name
			+ ", Position: " + position
			+ ", Related: " + relatedTypes
			+ ", Reason: '" + decision.reason + "'"
			+ ", Classification: " + decision.classification;
	}

	private String classificationDebugApiType(Object source)
	{
		if (source instanceof NPC)
		{
			return "NPC";
		}

		if (source instanceof Player)
		{
			return "Player";
		}

		if (source instanceof Projectile)
		{
			return "Projectile";
		}

		if (source instanceof GraphicsObject)
		{
			return "GraphicsObject";
		}

		if (source instanceof ActorSpotAnim)
		{
			return "ActorSpotAnim";
		}

		if (source instanceof TileItem)
		{
			return "TileItem";
		}

		if (source instanceof GameObject)
		{
			return "GameObject";
		}

		if (source instanceof DecorativeObject)
		{
			return "DecorativeObject";
		}

		if (source instanceof WallObject)
		{
			return "WallObject";
		}

		if (source instanceof GroundObject)
		{
			return "GroundObject";
		}

		if (source instanceof TileObject)
		{
			return "TileObject";
		}

		if (source instanceof DynamicObject)
		{
			return "DynamicObject";
		}

		if (source instanceof Renderable)
		{
			return "Renderable";
		}

		return source.getClass().getSimpleName();
	}

	private String classificationDebugId(Object source)
	{
		if (source instanceof TileObject)
		{
			return Integer.toString(((TileObject) source).getId());
		}

		if (source instanceof NPC)
		{
			return Integer.toString(((NPC) source).getId());
		}

		if (source instanceof TileItem)
		{
			return Integer.toString(((TileItem) source).getId());
		}

		if (source instanceof GraphicsObject)
		{
			return Integer.toString(((GraphicsObject) source).getId());
		}

		if (source instanceof ActorSpotAnim)
		{
			return Integer.toString(((ActorSpotAnim) source).getId());
		}

		if (source instanceof Projectile)
		{
			return Integer.toString(((Projectile) source).getId());
		}

		if (source instanceof Player)
		{
			Player player = (Player) source;
			return player.getName() != null ? player.getName() : "player";
		}

		return "-";
	}

	private String classificationDebugName(Object source)
	{
		if (source instanceof Player)
		{
			String name = ((Player) source).getName();
			return name != null && !name.isEmpty() ? name : "-";
		}

		if (source instanceof NPC)
		{
			String name = ((NPC) source).getName();
			return name != null && !name.isEmpty() ? name : "-";
		}

		if (source instanceof TileObject)
		{
			if (!client.isClientThread())
			{
				return "-";
			}

			net.runelite.api.ObjectComposition objectDefinition = client.getObjectDefinition(((TileObject) source).getId());
			if (objectDefinition == null)
			{
				return "-";
			}

			String name = objectDefinition.getName();
			return name != null && !name.trim().isEmpty() ? name : "-";
		}

		return "-";
	}

	private String classificationDebugPosition(Object source)
	{
		if (source instanceof TileObject)
		{
			TileObject tileObject = (TileObject) source;
			LocalPoint localPoint = tileObject.getLocalLocation();
			String local = formatLocalPoint(localPoint);
			String world = tileObject.getWorldLocation() != null
				? tileObject.getWorldLocation().getX() + "," + tileObject.getWorldLocation().getY() + "," + tileObject.getPlane()
				: "-";
			return "world=" + world + " local=" + local;
		}

		if (source instanceof Actor)
		{
			Actor actor = (Actor) source;
			LocalPoint localPoint = actor.getLocalLocation();
			String local = formatLocalPoint(localPoint);
			String world = actor.getWorldLocation() != null
				? actor.getWorldLocation().getX() + "," + actor.getWorldLocation().getY() + "," + actor.getWorldView().getPlane()
				: "-";
			return "world=" + world + " local=" + local;
		}

		if (source instanceof GraphicsObject)
		{
			GraphicsObject graphicsObject = (GraphicsObject) source;
			return "level=" + graphicsObject.getLevel() + " local=" + formatLocalPoint(graphicsObject.getLocation());
		}

		if (source instanceof Projectile)
		{
			Projectile projectile = (Projectile) source;
			return "floor=" + projectile.getFloor() + " local=" + formatLocalPoint(projectileLocalPoint(projectile));
		}

		return "-";
	}

	private String formatLocalPoint(LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return "-";
		}

		return localPoint.getX() + "," + localPoint.getY();
	}

	private void logObservedTileObjectClassification(ObservedTileObject observed, ObjectClassifier.ClassificationDecision decision)
	{
		if (!log.isDebugEnabled() || observed == null || observed.tileObject == null || decision == null)
		{
			return;
		}

		if (decision.classification == observed.lastLoggedClassification
			&& java.util.Objects.equals(decision.reason, observed.lastLoggedClassificationReason))
		{
			return;
		}

		observed.lastLoggedClassification = decision.classification;
		observed.lastLoggedClassificationReason = decision.reason;

		TileObject tileObject = observed.tileObject;
		String renderableTypes = observedRenderableTypes(observed);
		logClassificationDecision(tileObject, decision, renderableTypes);
	}

	private String observedRenderableTypes(ObservedTileObject observed)
	{
		if (observed == null || observed.parts == null || observed.parts.isEmpty())
		{
			return "-";
		}

		StringBuilder builder = new StringBuilder();
		for (ObjectRenderablePart part : observed.parts)
		{
			if (part == null || part.renderable == null)
			{
				continue;
			}

			if (builder.length() > 0)
			{
				builder.append(", ");
			}

			builder.append(classificationDebugApiType(part.renderable))
				.append("(")
				.append(part.renderable.getClass().getSimpleName())
				.append(")");
		}

		return builder.length() > 0 ? builder.toString() : "-";
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

	private int relativePitch(ActorSpotAnim actorSpotAnim)
	{
		return relativePitch();
	}

	private int relativePitch(GraphicsObject graphicsObject)
	{
		return relativePitch();
	}

	private int relativePitch(TileItem item)
	{
		int rawPitch = Math.max(0, Math.min(MAX_PITCH, client.getCameraPitch()));
		if (!config.enableRotationSnapping())
		{
			return rawPitch;
		}

		return -snapPitchByAngles(rawPitch, GROUND_ITEM_MIN_PITCH, config.numberOfPitchRotationAngles());
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
		private final int x0;
		private final int y0;
		private final int x1;
		private final int y1;
		private final int x2;
		private final int y2;
		private final Color color;
		private final double depth;
		private final TextureSample textureSample;
		private final TextureUvs textureUvs;

		private FaceDraw(
			int x0,
			int y0,
			int x1,
			int y1,
			int x2,
			int y2,
			Color color,
			double depth,
			TextureSample textureSample,
			TextureUvs textureUvs)
		{
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.color = color;
			this.depth = depth;
			this.textureSample = textureSample;
			this.textureUvs = textureUvs;
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
		private final boolean spriteShadow;
		private final boolean highlightInline;
		private final boolean shadowInline;
		private final boolean solidInline;
		private final int outlineColor;
		private final boolean hoverOutline;
		private final boolean interactOutline;
		private final int hoverOutlineColor;
		private final int interactOutlineColor;
		private final int renderQuality;
		private final int modelStateHash;
		private final int textureStateHash;
		private final int animatedTextureOffsetStateHash;

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
			boolean spriteShadow,
			boolean highlightInline,
			boolean shadowInline,
			boolean solidInline,
			int outlineColor,
			boolean hoverOutline,
			boolean interactOutline,
			int hoverOutlineColor,
			int interactOutlineColor,
			int renderQuality,
			int modelStateHash,
			int textureStateHash,
			int animatedTextureOffsetStateHash)
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
			this.spriteShadow = spriteShadow;
			this.highlightInline = highlightInline;
			this.shadowInline = shadowInline;
			this.solidInline = solidInline;
			this.outlineColor = outlineColor;
			this.hoverOutline = hoverOutline;
			this.interactOutline = interactOutline;
			this.hoverOutlineColor = hoverOutlineColor;
			this.interactOutlineColor = interactOutlineColor;
			this.renderQuality = renderQuality;
			this.modelStateHash = modelStateHash;
			this.textureStateHash = textureStateHash;
			this.animatedTextureOffsetStateHash = animatedTextureOffsetStateHash;
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
				&& spriteShadow == that.spriteShadow
				&& highlightInline == that.highlightInline
				&& shadowInline == that.shadowInline
				&& solidInline == that.solidInline
				&& outlineColor == that.outlineColor
				&& hoverOutline == that.hoverOutline
				&& interactOutline == that.interactOutline
				&& hoverOutlineColor == that.hoverOutlineColor
				&& interactOutlineColor == that.interactOutlineColor
				&& renderQuality == that.renderQuality
				&& modelStateHash == that.modelStateHash
				&& textureStateHash == that.textureStateHash
				&& animatedTextureOffsetStateHash == that.animatedTextureOffsetStateHash;
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
			result = 31 * result + (spriteShadow ? 1 : 0);
			result = 31 * result + (highlightInline ? 1 : 0);
			result = 31 * result + (shadowInline ? 1 : 0);
			result = 31 * result + (solidInline ? 1 : 0);
			result = 31 * result + outlineColor;
			result = 31 * result + (hoverOutline ? 1 : 0);
			result = 31 * result + (interactOutline ? 1 : 0);
			result = 31 * result + hoverOutlineColor;
			result = 31 * result + interactOutlineColor;
			result = 31 * result + renderQuality;
			result = 31 * result + modelStateHash;
			result = 31 * result + textureStateHash;
			result = 31 * result + animatedTextureOffsetStateHash;
			return result;
		}
	}

	private static final class BillboardCachePreviewKey
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
		private final boolean spriteShadow;
		private final boolean highlightInline;
		private final boolean shadowInline;
		private final boolean solidInline;
		private final int outlineColor;
		private final boolean hoverOutline;
		private final boolean interactOutline;
		private final int hoverOutlineColor;
		private final int interactOutlineColor;
		private final int renderQuality;
		private final int modelStateHash;
		private final int animatedTextureOffsetStateHash;

		private BillboardCachePreviewKey(
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
			boolean spriteShadow,
			boolean highlightInline,
			boolean shadowInline,
			boolean solidInline,
			int outlineColor,
			boolean hoverOutline,
			boolean interactOutline,
			int hoverOutlineColor,
			int interactOutlineColor,
			int renderQuality,
			int modelStateHash,
			int animatedTextureOffsetStateHash)
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
			this.spriteShadow = spriteShadow;
			this.highlightInline = highlightInline;
			this.shadowInline = shadowInline;
			this.solidInline = solidInline;
			this.outlineColor = outlineColor;
			this.hoverOutline = hoverOutline;
			this.interactOutline = interactOutline;
			this.hoverOutlineColor = hoverOutlineColor;
			this.interactOutlineColor = interactOutlineColor;
			this.renderQuality = renderQuality;
			this.modelStateHash = modelStateHash;
			this.animatedTextureOffsetStateHash = animatedTextureOffsetStateHash;
		}
	}

	private static final class RenderedBillboardImage
	{
		private final BufferedImage image;

		private RenderedBillboardImage(BufferedImage image)
		{
			this.image = image;
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
		private boolean debugFrameRedrawn;
		private boolean debugFrameInvalidated;
		private NpcSnapDebug.StateDebugInfo stateDebugInfo;

		private CachedBillboard(BillboardCacheKey key, Rectangle bounds, BufferedImage image, long lastUsedMillis)
		{
			this.key = key;
			this.bounds = new Rectangle(bounds);
			this.image = image;
			this.lastUsedMillis = lastUsedMillis;
			this.lastRedrawMillis = lastUsedMillis;
			this.dirty = true;
			this.debugFrameRedrawn = true;
			this.debugFrameInvalidated = false;
			this.stateDebugInfo = null;
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

		private boolean matchesPreviewKey(BillboardCachePreviewKey previewKey)
		{
			return previewKey != null
				&& key.animationId == previewKey.animationId
				&& key.animationFrame == previewKey.animationFrame
				&& key.poseAnimationId == previewKey.poseAnimationId
				&& key.poseAnimationFrame == previewKey.poseAnimationFrame
				&& key.relativeYaw == previewKey.relativeYaw
				&& key.relativePitch == previewKey.relativePitch
				&& key.colorBands == previewKey.colorBands
				&& key.lightBoost == previewKey.lightBoost
				&& key.outlinePadding == previewKey.outlinePadding
				&& key.highlightOutline == previewKey.highlightOutline
				&& key.shadowOutline == previewKey.shadowOutline
				&& key.solidOutline == previewKey.solidOutline
				&& key.spriteShadow == previewKey.spriteShadow
				&& key.highlightInline == previewKey.highlightInline
				&& key.shadowInline == previewKey.shadowInline
				&& key.solidInline == previewKey.solidInline
				&& key.outlineColor == previewKey.outlineColor
				&& key.hoverOutline == previewKey.hoverOutline
				&& key.interactOutline == previewKey.interactOutline
				&& key.hoverOutlineColor == previewKey.hoverOutlineColor
				&& key.interactOutlineColor == previewKey.interactOutlineColor
				&& key.renderQuality == previewKey.renderQuality
				&& key.modelStateHash == previewKey.modelStateHash
				&& key.animatedTextureOffsetStateHash == previewKey.animatedTextureOffsetStateHash;
		}

		private boolean consumeDirty()
		{
			boolean wasDirty = dirty;
			dirty = false;
			return wasDirty;
		}

		private void markDebugFrameRedrawn()
		{
			debugFrameRedrawn = true;
			debugFrameInvalidated = false;
		}

		private boolean consumeDebugFrameRedrawn()
		{
			boolean wasRedrawn = debugFrameRedrawn;
			debugFrameRedrawn = false;
			return wasRedrawn;
		}

		private void markDebugFrameInvalidated()
		{
			debugFrameInvalidated = true;
		}

		private boolean consumeDebugFrameInvalidated()
		{
			boolean wasInvalidated = debugFrameInvalidated;
			debugFrameInvalidated = false;
			return wasInvalidated;
		}

		private NpcSnapDebug.StateDebugInfo stateDebugInfo()
		{
			return stateDebugInfo;
		}

		private void stateDebugInfo(NpcSnapDebug.StateDebugInfo stateDebugInfo)
		{
			this.stateDebugInfo = stateDebugInfo;
		}

		private void flush()
		{
			image.flush();
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
		private final ClassifiedObjectType classifiedType;
		private final Renderable renderable;
		private final Actor parentActor;
		private final TileObject tileObject;
		private final ObservedTileObject observedTileObject;
		private final GroundItemBillboard groundItem;
		private final double depth;
		private final int renderPriority;
		private final PriorityTileKey priorityTileKey;
		private final BillboardTargetKey targetKey;

		private BillboardTarget(
			BillboardTargetType type,
			ClassifiedObjectType classifiedType,
			Renderable renderable,
			Actor parentActor,
			TileObject tileObject,
			ObservedTileObject observedTileObject,
			GroundItemBillboard groundItem,
			double depth,
			int renderPriority,
			PriorityTileKey priorityTileKey,
			BillboardTargetKey targetKey
		)
		{
			this.type = type;
			this.classifiedType = classifiedType;
			this.renderable = renderable;
			this.parentActor = parentActor;
			this.tileObject = tileObject;
			this.observedTileObject = observedTileObject;
			this.groundItem = groundItem;
			this.depth = depth;
			this.renderPriority = renderPriority;
			this.priorityTileKey = priorityTileKey;
			this.targetKey = targetKey;
		}

		private static BillboardTarget forRenderable(
			BillboardTargetType type,
			ClassifiedObjectType classifiedType,
			Renderable renderable,
			double depth,
			LocalPoint localPoint,
			int plane
		)
		{
			int renderPriority = ObjectClassifier.renderPriority(classifiedType);
			return new BillboardTarget(
				type,
				classifiedType,
				renderable,
				null,
				null,
				null,
				null,
				depth,
				renderPriority,
				PriorityTileKey.of(localPoint, plane, renderPriority),
				BillboardTargetKey.forRenderable(type, renderable, localPoint, plane)
			);
		}

		private static BillboardTarget forActorSpotAnim(ActorSpotAnim actorSpotAnim, Actor actor, double depth)
		{
			LocalPoint localPoint = actor.getLocalLocation();
			int plane = actor.getWorldView().getPlane();
			int renderPriority = ObjectClassifier.renderPriority(ClassifiedObjectType.EFFECT);
			return new BillboardTarget(
				BillboardTargetType.ACTOR_SPOT_ANIM,
				ClassifiedObjectType.EFFECT,
				actorSpotAnim,
				actor,
				null,
				null,
				null,
				depth,
				renderPriority,
				PriorityTileKey.of(localPoint, plane, renderPriority),
				BillboardTargetKey.forActorSpotAnim(actorSpotAnim, actor, localPoint, plane)
			);
		}

		private static BillboardTarget forGroundItem(TileItem item, GroundItemBillboard groundItem, double depth)
		{
			int renderPriority = ObjectClassifier.renderPriority(ClassifiedObjectType.GROUND_ITEM);
			return new BillboardTarget(
				BillboardTargetType.GROUND_ITEM,
				ClassifiedObjectType.GROUND_ITEM,
				item,
				null,
				null,
				null,
				groundItem,
				depth,
				renderPriority,
				PriorityTileKey.of(groundItem.localPoint, groundItem.plane, renderPriority),
				BillboardTargetKey.forGroundItem(item, groundItem)
			);
		}

		private static BillboardTarget forTileObject(ObservedTileObject observedTileObject, double depth)
		{
			ClassifiedObjectType classifiedType = observedTileObject != null ? observedTileObject.classifiedType : ClassifiedObjectType.UNKNOWN;
			int renderPriority = ObjectClassifier.renderPriority(classifiedType);
			return new BillboardTarget(
				BillboardTargetType.TILE_OBJECT,
				classifiedType,
				null,
				null,
				observedTileObject.tileObject,
				observedTileObject,
				null,
				depth,
				renderPriority,
				PriorityTileKey.of(firstLocalPoint(observedTileObject), firstPlane(observedTileObject), renderPriority),
				BillboardTargetKey.forTileObject(observedTileObject.tileObject, firstLocalPoint(observedTileObject), firstPlane(observedTileObject))
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

		private long priorityGroupSortKey()
		{
			return priorityTileKey != null ? priorityTileKey.sortKey() : BillboardPaintOrder.NO_PRIORITY_GROUP;
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

	private static final class BillboardTargetKey
	{
		private final long orderKey;
		private final long uniqueKey;

		private BillboardTargetKey(long orderKey, long uniqueKey)
		{
			this.orderKey = orderKey;
			this.uniqueKey = uniqueKey;
		}

		private static BillboardTargetKey forRenderable(BillboardTargetType type, Renderable renderable, LocalPoint localPoint, int plane)
		{
			int entityId = renderableEntityId(type, renderable);
			long locationKey = locationKey(localPoint, plane);
			long orderKey = composeOrderKey(type.ordinal(), entityId, locationKey);
			long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(renderable));
			return new BillboardTargetKey(orderKey, uniqueKey);
		}

		private static BillboardTargetKey forActorSpotAnim(ActorSpotAnim actorSpotAnim, Actor actor, LocalPoint localPoint, int plane)
		{
			long locationKey = locationKey(localPoint, plane);
			long orderKey = composeOrderKey(BillboardTargetType.ACTOR_SPOT_ANIM.ordinal(), actorSpotAnim.getId(), locationKey);
			long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(actorSpotAnim) ^ System.identityHashCode(actor));
			return new BillboardTargetKey(orderKey, uniqueKey);
		}

		private static BillboardTargetKey forGroundItem(TileItem item, GroundItemBillboard groundItem)
		{
			long locationKey = locationKey(groundItem != null ? groundItem.localPoint : null, groundItem != null ? groundItem.plane : -1);
			long orderKey = composeOrderKey(BillboardTargetType.GROUND_ITEM.ordinal(), item.getId(), locationKey);
			long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(item));
			return new BillboardTargetKey(orderKey, uniqueKey);
		}

		private static BillboardTargetKey forTileObject(TileObject tileObject, LocalPoint localPoint, int plane)
		{
			long locationKey = locationKey(localPoint, plane);
			long orderKey = composeOrderKey(BillboardTargetType.TILE_OBJECT.ordinal(), tileObject != null ? tileObject.getId() : -1, locationKey);
			long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(tileObject));
			return new BillboardTargetKey(orderKey, uniqueKey);
		}

		private static int compareForQueueOrder(BillboardTargetKey left, BillboardTargetKey right)
		{
			int byOrder = Long.compare(left.orderKey, right.orderKey);
			return byOrder != 0 ? byOrder : Long.compare(left.uniqueKey, right.uniqueKey);
		}

		private long stableSortOrder()
		{
			return uniqueKey;
		}

		private static int renderableEntityId(BillboardTargetType type, Renderable renderable)
		{
			if (renderable == null)
			{
				return -1;
			}

			switch (type)
			{
				case NPC:
					return ((NPC) renderable).getId();
				case PROJECTILE:
					return ((Projectile) renderable).getId();
				case GRAPHICS_OBJECT:
					return ((GraphicsObject) renderable).getId();
				case GROUND_ITEM:
					return ((TileItem) renderable).getId();
				default:
					return System.identityHashCode(renderable);
			}
		}

		private static long composeOrderKey(int typeOrdinal, int entityId, long locationKey)
		{
			long key = ((long) typeOrdinal & 0xFFL) << 56;
			key |= ((long) entityId & 0xFFFFFFL) << 32;
			key |= locationKey & 0xFFFFFFFFL;
			return key;
		}

		private static long composeUniqueKey(long orderKey, int identityHash)
		{
			return (orderKey * 31L) ^ (identityHash & 0xFFFFFFFFL);
		}

		private static long locationKey(LocalPoint localPoint, int plane)
		{
			if (localPoint == null)
			{
				return plane & 0x3L;
			}

			long tileX = (localPoint.getX() / LOCAL_TILE_SIZE) & 0x7FFL;
			long tileY = (localPoint.getY() / LOCAL_TILE_SIZE) & 0x7FFL;
			long planeBits = plane & 0x3L;
			return (planeBits << 22) | (tileX << 11) | tileY;
		}

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}

			if (!(other instanceof BillboardTargetKey))
			{
				return false;
			}

			BillboardTargetKey that = (BillboardTargetKey) other;
			return orderKey == that.orderKey && uniqueKey == that.uniqueKey;
		}

		@Override
		public int hashCode()
		{
			int result = Long.hashCode(orderKey);
			result = (31 * result) + Long.hashCode(uniqueKey);
			return result;
		}
	}

	private static final class BillboardUpdateState
	{
		private double qualityScale;
		private int nextEligibleGameCycle;
		private int lastRedrawGameCycle;
		private long lastRedrawPositionKey;
		private int lastRedrawAnimationHash;
		private int lastRedrawViewHash;
		private long lastObservedPositionKey;
		private int lastObservedAnimationHash;
		private boolean hasObservation;

		private BillboardUpdateState(double qualityScale)
		{
			this.qualityScale = qualityScale;
			this.nextEligibleGameCycle = Integer.MIN_VALUE;
			this.lastRedrawGameCycle = Integer.MIN_VALUE;
			this.lastRedrawPositionKey = Long.MIN_VALUE;
			this.lastRedrawAnimationHash = Integer.MIN_VALUE;
			this.lastRedrawViewHash = Integer.MIN_VALUE;
			this.lastObservedPositionKey = Long.MIN_VALUE;
			this.lastObservedAnimationHash = Integer.MIN_VALUE;
			this.hasObservation = false;
		}

		private boolean isReady(int gameCycle, boolean hasCachedBillboard)
		{
			return !hasCachedBillboard || gameCycle >= nextEligibleGameCycle;
		}

		private boolean hasMoved(UpdateHeuristicSnapshot snapshot)
		{
			return hasObservation
				&& snapshot.hasPosition()
				&& lastObservedPositionKey != Long.MIN_VALUE
				&& lastObservedPositionKey != snapshot.positionKey;
		}

		private boolean hasAnimationChanged(UpdateHeuristicSnapshot snapshot)
		{
			return hasObservation && lastObservedAnimationHash != Integer.MIN_VALUE && lastObservedAnimationHash != snapshot.animationHash;
		}

		private boolean hasPositionChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
		{
			return snapshot != null
				&& snapshot.hasPosition()
				&& lastRedrawPositionKey != Long.MIN_VALUE
				&& lastRedrawPositionKey != snapshot.positionKey;
		}

		private boolean hasViewChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
		{
			return snapshot != null
				&& lastRedrawViewHash != Integer.MIN_VALUE
				&& lastRedrawViewHash != snapshot.viewHash;
		}

		private int cyclesSinceRedraw(int gameCycle)
		{
			if (lastRedrawGameCycle == Integer.MIN_VALUE)
			{
				return 64;
			}

			return Math.max(0, gameCycle - lastRedrawGameCycle);
		}

		private int overdueCycles(int gameCycle)
		{
			if (nextEligibleGameCycle == Integer.MIN_VALUE)
			{
				return cyclesSinceRedraw(gameCycle);
			}

			return Math.max(0, gameCycle - nextEligibleGameCycle);
		}

		private void observe(UpdateHeuristicSnapshot snapshot)
		{
			if (snapshot == null)
			{
				return;
			}

			lastObservedPositionKey = snapshot.positionKey;
			lastObservedAnimationHash = snapshot.animationHash;
			hasObservation = true;
		}

		private void defer(int gameCycle, int baseRefreshInterval)
		{
			nextEligibleGameCycle = gameCycle + Math.max(1, baseRefreshInterval);
		}

		private void advance(int gameCycle, int baseRefreshInterval, double fullQualityScale, UpdateHeuristicSnapshot snapshot, int minimumAnimatedRedrawInterval)
		{
			double clampedFullQuality = Math.max(MIN_RENDER_QUALITY, Math.min(1.0d, fullQualityScale));
			double clampedCurrentQuality = Math.max(MIN_RENDER_QUALITY, Math.min(clampedFullQuality, qualityScale));
			double refreshRatio = clampedCurrentQuality / clampedFullQuality;
			int nextDelay = Math.max(1, (int) Math.round(baseRefreshInterval * refreshRatio));
			if (snapshot != null && snapshot.animated)
			{
				nextDelay = Math.max(nextDelay, Math.max(1, minimumAnimatedRedrawInterval));
			}
			nextEligibleGameCycle = gameCycle + nextDelay;
			qualityScale = Math.min(clampedFullQuality, clampedCurrentQuality * 2.0d);
			lastRedrawGameCycle = gameCycle;
			if (snapshot != null)
			{
				lastRedrawPositionKey = snapshot.positionKey;
				lastRedrawAnimationHash = snapshot.animationHash;
				lastRedrawViewHash = snapshot.viewHash;
			}
		}
	}

	private static final class FrameUpdatePlan
	{
		private final double qualityScale;
		private final boolean forceHoverInteractionRedraw;

		private FrameUpdatePlan(double qualityScale, boolean forceHoverInteractionRedraw)
		{
			this.qualityScale = qualityScale;
			this.forceHoverInteractionRedraw = forceHoverInteractionRedraw;
		}
	}

	private static final class UpdateHeuristicSnapshot
	{
		private final double depth;
		private final long positionKey;
		private final int animationHash;
		private final int viewHash;
		private final boolean animated;

		private UpdateHeuristicSnapshot(double depth, long positionKey, int animationHash, int viewHash, boolean animated)
		{
			this.depth = depth;
			this.positionKey = positionKey;
			this.animationHash = animationHash;
			this.viewHash = viewHash;
			this.animated = animated;
		}

		private static UpdateHeuristicSnapshot empty()
		{
			return new UpdateHeuristicSnapshot(Double.POSITIVE_INFINITY, Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, false);
		}

		private static UpdateHeuristicSnapshot forDepth(double depth)
		{
			return new UpdateHeuristicSnapshot(depth, Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, false);
		}

		private static UpdateHeuristicSnapshot fromRequest(BillboardRenderRequest request, double depth, int animatedTextureOffsetStateHash)
		{
			long positionKey = positionKey(request.localPoint, request.plane);
			int animationHash = 1;
			int modelStateHash = modelStateHash(request.model);
			int viewHash = 1;
			viewHash = (31 * viewHash) + request.relativeYaw;
			viewHash = (31 * viewHash) + request.relativePitch;
			animationHash = (31 * animationHash) + request.animationId;
			animationHash = (31 * animationHash) + request.animationFrame;
			animationHash = (31 * animationHash) + request.poseAnimationId;
			animationHash = (31 * animationHash) + request.poseAnimationFrame;
			animationHash = (31 * animationHash) + request.animatedTextureId;
			animationHash = (31 * animationHash) + modelStateHash;
			animationHash = (31 * animationHash) + animatedTextureOffsetStateHash;
			boolean animated = request.animationId >= 0
				|| request.poseAnimationId >= 0
				|| request.animationFrame >= 0
				|| request.poseAnimationFrame >= 0
				|| modelStateHash != 0
				|| request.animatedTextureId >= 0;
			return new UpdateHeuristicSnapshot(depth, positionKey, animationHash, viewHash, animated);
		}

		private boolean hasPosition()
		{
			return positionKey != Long.MIN_VALUE;
		}

		private static long positionKey(LocalPoint localPoint, int plane)
		{
			if (localPoint == null)
			{
				return Long.MIN_VALUE;
			}

			long x = localPoint.getX() & 0x1FFFFL;
			long y = localPoint.getY() & 0x1FFFFL;
			long z = plane & 0x3L;
			return (z << 34) | (x << 17) | y;
		}
	}

	private static final class QueuedBillboardTarget
	{
		private final BillboardTargetKey key;
		private final double priorityScore;
		private final int queueIndex;

		private QueuedBillboardTarget(BillboardTargetKey key, double priorityScore, int queueIndex)
		{
			this.key = key;
			this.priorityScore = priorityScore;
			this.queueIndex = queueIndex;
		}

		private double getPriorityScore()
		{
			return priorityScore;
		}

		private int getQueueIndex()
		{
			return queueIndex;
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

		private long sortKey()
		{
			return ((long) plane << 48)
				^ ((long) (tileX & 0xFFFFFF) << 24)
				^ (tileY & 0xFFFFFFL);
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

	private static final class CollectedCandidates
	{
		private final List<BillboardTarget> candidates;
		private final Map<OccupiedTileKey, Actor> topActorsByTile;
		private final Map<OccupiedTileKey, List<Actor>> actorsByTile;

		private CollectedCandidates(
			List<BillboardTarget> candidates,
			Map<OccupiedTileKey, Actor> topActorsByTile,
			Map<OccupiedTileKey, List<Actor>> actorsByTile
		)
		{
			this.candidates = candidates;
			this.topActorsByTile = topActorsByTile;
			this.actorsByTile = actorsByTile;
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
		private final boolean shouldHoverOutline;
		private final boolean shouldInteractOutline;
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
			boolean shouldHoverOutline,
			boolean shouldInteractOutline,
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
			this.shouldHoverOutline = shouldHoverOutline;
			this.shouldInteractOutline = shouldInteractOutline;
			this.verticalAnchor = verticalAnchor;
			this.frameDebugInfo = frameDebugInfo;
		}
	}

	private static final class BillboardRenderResult
	{
		private final Rectangle bounds;
		private final BufferedImage image;

		private BillboardRenderResult(Rectangle bounds, BufferedImage image)
		{
			this.bounds = bounds;
			this.image = image;
		}
	}

	private static final class PreparedBillboardDraw
	{
		private final BillboardRenderRequest request;
		private final BillboardRenderResult result;
		private final int paintOrder;
		private final BufferedImage image;
		private final Rectangle bounds;

		private PreparedBillboardDraw(BillboardRenderRequest request, BillboardRenderResult result, int paintOrder)
		{
			this.request = request;
			this.result = result;
			this.paintOrder = paintOrder;
			this.image = result != null ? result.image : null;
			this.bounds = result != null ? result.bounds : null;
		}
	}

	private static final class ObservedTileObject
	{
		private final TileObject tileObject;
		private final List<ObjectRenderablePart> parts;
		private ClassifiedObjectType classifiedType;
		private ClassifiedObjectType lastLoggedClassification;
		private String lastLoggedClassificationReason;

		private ObservedTileObject(TileObject tileObject, List<ObjectRenderablePart> parts, ClassifiedObjectType classifiedType)
		{
			this.tileObject = tileObject;
			this.parts = parts;
			this.classifiedType = classifiedType;
			this.lastLoggedClassification = null;
			this.lastLoggedClassificationReason = null;
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
