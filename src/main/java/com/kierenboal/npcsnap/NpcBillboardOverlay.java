package com.kierenboal.npcsnap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class NpcBillboardOverlay extends Overlay
{
	private static final int BILLBOARD_FULL_CIRCLE = BillboardAngleUtils.BILLBOARD_FULL_CIRCLE;
	private static final int OUTLINE_PADDING = 1;
	private static final float TERRAIN_OCCLUSION_DEPTH_BIAS = BillboardConstants.LOCAL_TILE_SIZE * 2.0f;
	private static final int DEBUG_OCCLUSION_SAMPLE_LIMIT = 8;
	private static final int DEBUG_OCCLUSION_DEPTH_SAMPLE_LIMIT = 24;
	private static final int DEBUG_OCCLUDED_PIXEL = new Color(255, 32, 32, 150).getRGB();
	private final Client client;
	private final NpcSnapConfig config;
	private final NpcSnapDebug debug;
	private final AnimationFrameSnapper animationFrameSnapper;
	private final BillboardDepthCalculator depthCalculator;
	private final BillboardOrientationCalculator orientationCalculator;
	private final Map<Renderable, CachedBillboard> billboardCache = new IdentityHashMap<>();
	private final BillboardTextureResolver textureResolver;
	private final GroundItemBillboardTracker groundItemTracker = new GroundItemBillboardTracker();
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
	private final Map<BillboardTargetKey, Double> debugQueuePriorityScores = new HashMap<>();
	private final BillboardClassificationDebug classificationDebug;
	private final Map<BillboardTargetKey, BillboardUpdateState> billboardUpdateStates = new HashMap<>();
	private final Map<BillboardTargetKey, FrameUpdatePlan> frameUpdatePlans = new HashMap<>();
	private final Set<Renderable> forceHoverInteractionRedraws = Collections.newSetFromMap(new IdentityHashMap<>());
	private final BillboardOutlineRenderer.Scratch outlineScratch = new BillboardOutlineRenderer.Scratch();
	private final BillboardOcclusionMask occlusionMask = new BillboardOcclusionMask();
	private final List<BillboardOcclusionMask.Occluder> worldOccluders = new ArrayList<>();
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
	private Rectangle frameOcclusionInterestBounds;
	private final List<Rectangle> frameOcclusionInterestRegions = new ArrayList<>();
	private int debugOcclusionShapesAccepted;
	private int debugOcclusionTerrainTilesConsidered;
	private int debugOcclusionTerrainTilesAccepted;
	private int debugOcclusionTerrainFacesAccepted;
	private int debugOcclusionTriangleOccludersAccepted;
	private int debugOcclusionFlatFallbackOccludersAccepted;
	private int debugOcclusionSceneObjectsConsidered;
	private int debugOcclusionSceneObjectsRejectedByFilter;
	private int debugOcclusionSceneObjectsSkippedOutsideInterest;
	private int debugOcclusionSceneObjectsWithoutParts;
	private int debugOcclusionSceneObjectsAccepted;
	private int debugOcclusionSceneObjectFacesAccepted;
	private int debugOcclusionLastLoggedCycle = Integer.MIN_VALUE;
	private final List<String> debugOcclusionAcceptedScenerySamples = new ArrayList<>();
	private final List<String> debugOcclusionRejectedScenerySamples = new ArrayList<>();
	private final List<String> debugOcclusionDepthSamples = new ArrayList<>();
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
		this.depthCalculator = new BillboardDepthCalculator(client);
		this.orientationCalculator = new BillboardOrientationCalculator(client, config);
		this.textureResolver = new BillboardTextureResolver(client, config);
		this.classificationDebug = new BillboardClassificationDebug(client, config, log);
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
		groundItemTracker.track(item, tile);
	}

	void untrackGroundItem(TileItem item)
	{
		if (item == null)
		{
			return;
		}

		groundItemTracker.untrack(item);
		clearGroundItemRenderState(item);
	}

	void clearGroundItems()
	{
		for (TileItem item : groundItemTracker.items())
		{
			clearGroundItemRenderState(item);
		}

		groundItemTracker.clear();
	}

	private void clearGroundItemRenderState(TileItem item)
	{
		removeCachedBillboard(item);
		activeRenderableTargets.remove(item);
		activeBillboards.remove(item);
	}

	private void clearActiveState()
	{
		activeRenderableTargets.clear();
		frameOcclusionInterestBounds = null;
		frameOcclusionInterestRegions.clear();
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

	private boolean hasActiveBillboardTargets()
	{
		return !activeRenderableTargets.isEmpty() || !activeTileObjects.isEmpty();
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
		debugQueuePriorityScores.clear();
		classificationDebug.clear();
		billboardUpdateStates.clear();
		frameUpdatePlans.clear();
		forceHoverInteractionRedraws.clear();
	}

	void syncGroundItems(WorldView worldView)
	{
		groundItemTracker.sync(worldView, this::clearGroundItemRenderState);
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
		textureResolver.clear();
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
		worldOccluders.clear();
		frameOcclusionInterestBounds = null;
		frameOcclusionInterestRegions.clear();
		clearDebugOcclusionStats();
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

	private boolean addSceneryShapeFallbackOccluder(
		TileObject tileObject,
		ObservedTileObject observed,
		double fallbackDepth,
		BillboardOcclusionQuality quality)
	{
		if (tileObject instanceof GameObject)
		{
			return addWorldOccluder(((GameObject) tileObject).getConvexHull(), partDepth(observed, 0, fallbackDepth, quality));
		}

		if (tileObject instanceof WallObject)
		{
			WallObject wallObject = (WallObject) tileObject;
			boolean addedFirst = addWorldOccluder(wallObject.getConvexHull(), partDepth(observed, 0, fallbackDepth, quality));
			boolean addedSecond = addWorldOccluder(wallObject.getConvexHull2(), partDepth(observed, 1, fallbackDepth, quality));
			if (!addedFirst || !addedSecond)
			{
				return addWorldOccluder(tileObject.getClickbox(), fallbackDepth) || addedFirst || addedSecond;
			}
			return true;
		}

		return false;
	}

	private double partDepth(ObservedTileObject observed, int partIndex, double fallbackDepth, BillboardOcclusionQuality quality)
	{
		if (observed == null || partIndex < 0 || partIndex >= observed.parts.size())
		{
			return fallbackDepth;
		}

		double depth = nearestModelVertexDepth(observed.parts.get(partIndex), quality.vertexStride());
		if (!Double.isFinite(depth))
		{
			return fallbackDepth;
		}

		return depth;
	}

	void prepareFrame(WorldView worldView)
	{
		ensureActiveBillboardsCurrent(worldView);
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
		groundItemTracker.seed(worldView);
	}

	boolean shouldHideRenderable(Renderable renderable)
	{
		if (renderable == null)
		{
			return false;
		}

		if (client.isClientThread())
		{
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
			markDebugFrameInvalidated(target, readyToRedrawColor(target));
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
			cached != null ? cached.consumeDebugFrameInvalidatedColor() : null,
			cached != null ? cached.stateDebugInfo() : null
		);
		debug.drawBillboardDebugForeground(graphics, renderDebug);
	}

	private void markDebugFrameInvalidated(BillboardTarget target, Color color)
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
					cached.markDebugFrameInvalidated(color);
				}
			}
			return;
		}

		CachedBillboard cached = target.renderable != null ? billboardCache.get(target.renderable) : null;
		if (cached != null)
		{
			cached.markDebugFrameInvalidated(color);
		}
	}

	private Color readyToRedrawColor(BillboardTarget target)
	{
		if (target == null)
		{
			return null;
		}

		Double score = debugQueuePriorityScores.get(target.targetKey);
		if (score == null || debugQueuePriorityScores.isEmpty())
		{
			return null;
		}

		double minScore = Double.POSITIVE_INFINITY;
		double maxScore = Double.NEGATIVE_INFINITY;
		for (double candidateScore : debugQueuePriorityScores.values())
		{
			minScore = Math.min(minScore, candidateScore);
			maxScore = Math.max(maxScore, candidateScore);
		}

		return NpcSnapDebug.readyToRedrawColor(score, minScore, maxScore);
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
		hash = (31 * hash) + BillboardModelStateHash.hash(request.model);
		return hash;
	}

	private static int jauToDegrees(int jau)
	{
		return Math.floorMod((int) Math.round((jau * 360.0d) / BILLBOARD_FULL_CIRCLE), 360);
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
		List<Rectangle> occlusionRegions = occlusionInterestRegions(preparedDraws, viewportX, viewportY, viewportWidth, viewportHeight);
		Rectangle occlusionBounds = unionBounds(occlusionRegions);
		occlusionMask.prepare(worldOccluders, config.billboardOcclusionQuality(), viewportX, viewportY, viewportWidth, viewportHeight, occlusionBounds, occlusionRegions);
		collectDebugOcclusionDepthSamples(preparedDraws);
		logDebugOcclusionStats(occlusionBounds);
		for (int i = preparedDraws.size() - 1; i >= 0; i--)
		{
			blitPreparedDraw(preparedDraws.get(i), viewportX, viewportY, viewportWidth, viewportHeight);
		}

		graphics.drawImage(frameCompositeImage, viewportX, viewportY, null);
		if (config.debugDrawBillboardOcclusionMask())
		{
			occlusionMask.drawDebug(graphics);
		}
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

	private List<Rectangle> occlusionInterestRegions(List<PreparedBillboardDraw> preparedDraws, int viewportX, int viewportY, int viewportWidth, int viewportHeight)
	{
		Rectangle viewportBounds = new Rectangle(viewportX, viewportY, viewportWidth, viewportHeight);
		int margin = occlusionInterestMargin();
		List<Rectangle> regions = new ArrayList<>();
		for (PreparedBillboardDraw draw : preparedDraws)
		{
			if (draw == null || draw.bounds == null || draw.bounds.width <= 0 || draw.bounds.height <= 0)
			{
				continue;
			}

			Rectangle clipped = viewportBounds.intersection(expandedOcclusionInterest(draw.bounds, margin));
			if (clipped.isEmpty())
			{
				continue;
			}

			regions.add(clipped);
		}

		return regions;
	}

	private int occlusionInterestMargin()
	{
		BillboardOcclusionQuality quality = BillboardOcclusionQuality.normalize(config.billboardOcclusionQuality());
		return Math.max(0, quality.sampleStep());
	}

	static Rectangle expandedOcclusionInterest(Rectangle bounds, int margin)
	{
		if (bounds == null || bounds.isEmpty() || margin <= 0)
		{
			return bounds;
		}

		return new Rectangle(bounds.x - margin, bounds.y - margin, bounds.width + (margin * 2), bounds.height + (margin * 2));
	}

	private static Rectangle unionBounds(List<Rectangle> regions)
	{
		if (regions == null || regions.isEmpty())
		{
			return null;
		}

		Rectangle union = null;
		for (Rectangle region : regions)
		{
			if (region == null || region.isEmpty())
			{
				continue;
			}

			union = union == null ? new Rectangle(region) : union.union(region);
		}

		return union;
	}

	private void clearDebugOcclusionStats()
	{
		debugOcclusionShapesAccepted = 0;
		debugOcclusionTerrainTilesConsidered = 0;
		debugOcclusionTerrainTilesAccepted = 0;
		debugOcclusionTerrainFacesAccepted = 0;
		debugOcclusionTriangleOccludersAccepted = 0;
		debugOcclusionFlatFallbackOccludersAccepted = 0;
		debugOcclusionSceneObjectsConsidered = 0;
		debugOcclusionSceneObjectsRejectedByFilter = 0;
		debugOcclusionSceneObjectsSkippedOutsideInterest = 0;
		debugOcclusionSceneObjectsWithoutParts = 0;
		debugOcclusionSceneObjectsAccepted = 0;
		debugOcclusionSceneObjectFacesAccepted = 0;
		debugOcclusionAcceptedScenerySamples.clear();
		debugOcclusionRejectedScenerySamples.clear();
		debugOcclusionDepthSamples.clear();
	}

	private void collectDebugOcclusionDepthSamples(List<PreparedBillboardDraw> preparedDraws)
	{
		if (!config.debugLogBillboardOcclusion() || preparedDraws == null || preparedDraws.isEmpty())
		{
			return;
		}

		double[] xFractions = { 0.25d, 0.5d, 0.75d };
		double[] yFractions = { 0.25d, 0.5d, 0.75d, 0.9d };
		for (PreparedBillboardDraw draw : preparedDraws)
		{
			if (debugOcclusionDepthSamples.size() >= DEBUG_OCCLUSION_DEPTH_SAMPLE_LIMIT)
			{
				return;
			}

			collectDebugOcclusionDepthSamples(draw, xFractions, yFractions);
		}
	}

	private void collectDebugOcclusionDepthSamples(PreparedBillboardDraw draw, double[] xFractions, double[] yFractions)
	{
		if (draw == null || draw.image == null || draw.bounds == null || draw.bounds.width <= 0 || draw.bounds.height <= 0
			|| !(draw.image.getRaster().getDataBuffer() instanceof DataBufferInt))
		{
			return;
		}

		int sourceWidth = draw.image.getWidth();
		int sourceHeight = draw.image.getHeight();
		if (sourceWidth <= 0 || sourceHeight <= 0)
		{
			return;
		}

		int[] sourcePixels = ((DataBufferInt) draw.image.getRaster().getDataBuffer()).getData();
		BillboardDepthSurface depthSurface = billboardDepthSurface(draw);
		for (double yFraction : yFractions)
		{
			for (double xFraction : xFractions)
			{
				if (debugOcclusionDepthSamples.size() >= DEBUG_OCCLUSION_DEPTH_SAMPLE_LIMIT)
				{
					return;
				}

				int canvasX = draw.bounds.x + Math.max(0, Math.min(draw.bounds.width - 1, (int) Math.round((draw.bounds.width - 1) * xFraction)));
				int canvasY = draw.bounds.y + Math.max(0, Math.min(draw.bounds.height - 1, (int) Math.round((draw.bounds.height - 1) * yFraction)));
				int sourceX = Math.max(0, Math.min(sourceWidth - 1, ((canvasX - draw.bounds.x) * sourceWidth) / draw.bounds.width));
				int sourceY = Math.max(0, Math.min(sourceHeight - 1, ((canvasY - draw.bounds.y) * sourceHeight) / draw.bounds.height));
				int sourcePixel = sourcePixels[(sourceY * sourceWidth) + sourceX];
				int sourceAlpha = (sourcePixel >>> 24) & 0xFF;
				if (sourceAlpha == 0)
				{
					continue;
				}

				BillboardDepthSurface.DebugPoint point = depthSurface.debugPointAt(sourceX, sourceY, sourceWidth, sourceHeight, canvasY);
				float worldDepth = occlusionMask.depthAt(canvasX, canvasY);
				float bias = occlusionMask.occlusionDepthBias();
				boolean occluded = occlusionMask.isOccluded(canvasX, canvasY, point.depth);
				double worldDepthMargin = point.depth - (worldDepth + bias);
				debugOcclusionDepthSamples.add(debugOcclusionDepthSample(draw, canvasX, canvasY, sourceX, sourceY, sourceAlpha, point, worldDepth, bias, worldDepthMargin, occluded));
			}
		}
	}

	private String debugOcclusionDepthSample(
		PreparedBillboardDraw draw,
		int canvasX,
		int canvasY,
		int sourceX,
		int sourceY,
		int sourceAlpha,
		BillboardDepthSurface.DebugPoint point,
		float worldDepth,
		float bias,
		double worldDepthMargin,
		boolean occluded)
	{
		BillboardRenderRequest request = draw.request;
		String target = request != null && request.renderable != null ? request.renderable.getClass().getSimpleName() : "-";
		return String.format(
			Locale.ROOT,
			"{target=%s canvas=(%d,%d) src=(%d,%d) alpha=%d draw=%s sourceBounds=%s modelH=%d local=(%d,%d) h=%s xOff=%s zOff=%s billboardDepth=%s worldDepth=%s bias=%s worldDepthMargin=%s occluded=%s}",
			target,
			canvasX,
			canvasY,
			sourceX,
			sourceY,
			sourceAlpha,
			draw.bounds,
			draw.sourceBounds,
			request != null && request.renderable != null ? request.renderable.getModelHeight() : -1,
			point.localX,
			point.localY,
			formatDebugDouble(point.height),
			formatDebugDouble(point.horizontalOffset),
			formatDebugDouble(point.verticalOffset),
			formatDebugDouble(point.depth),
			formatDebugDouble(worldDepth),
			formatDebugDouble(bias),
			formatDebugDouble(worldDepthMargin),
			occluded
		);
	}

	private String formatDebugDouble(double value)
	{
		return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "NaN";
	}

	private void logDebugOcclusionStats(Rectangle occlusionBounds)
	{
		if (!config.debugLogBillboardOcclusion())
		{
			return;
		}

		int gameCycle = client.getGameCycle();
		if (debugOcclusionLastLoggedCycle != Integer.MIN_VALUE && gameCycle - debugOcclusionLastLoggedCycle < 30)
		{
			return;
		}

		debugOcclusionLastLoggedCycle = gameCycle;
		log.debug(
			"Billboard occlusion quality={} cameraYaw={} cameraPitch={} cameraYawIndex={} cameraPitchIndex={} cameraFp=({},{},{}) sources=terrain,scenery sceneryCandidates={} sceneryAccepted={} sceneryRejectedByFilter={} sceneryOutsideInterest={} sceneryWithoutParts={} sceneryFaces={} terrainTiles={} terrainTilesAccepted={} terrainFaces={} shapes={} triangleOccluders={} flatFallbackOccluders={} cells={} activeRegions={} acceptedScenery={} rejectedScenery={} preInterest={} maskInterest={} depthSamples={}",
			BillboardOcclusionQuality.normalize(config.billboardOcclusionQuality()),
			client.getCameraYaw(),
			client.getCameraPitch(),
			depthCalculator.cameraYawIndex(),
			depthCalculator.cameraPitchIndex(),
			client.getCameraFpX(),
			client.getCameraFpY(),
			client.getCameraFpZ(),
			debugOcclusionSceneObjectsConsidered,
			debugOcclusionSceneObjectsAccepted,
			debugOcclusionSceneObjectsRejectedByFilter,
			debugOcclusionSceneObjectsSkippedOutsideInterest,
			debugOcclusionSceneObjectsWithoutParts,
			debugOcclusionSceneObjectFacesAccepted,
			debugOcclusionTerrainTilesConsidered,
			debugOcclusionTerrainTilesAccepted,
			debugOcclusionTerrainFacesAccepted,
			debugOcclusionShapesAccepted,
			debugOcclusionTriangleOccludersAccepted,
			debugOcclusionFlatFallbackOccludersAccepted,
			occlusionMask.coveredCellCount(),
			frameOcclusionInterestRegions.size(),
			debugOcclusionAcceptedScenerySamples,
			debugOcclusionRejectedScenerySamples,
			frameOcclusionInterestBounds,
			occlusionBounds,
			debugOcclusionDepthSamples
		);
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
		BillboardDepthSurface billboardDepth = billboardDepthSurface(draw);
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

				double pixelBillboardDepth = billboardDepth.depthAt(sourceX, sourceY, sourceWidth, sourceHeight, y);
				if (occlusionMask.isOccluded(x, y, pixelBillboardDepth))
				{
					if (config.debugDrawBillboardOcclusionMask())
					{
						frameCompositePixels[destIndex] = BillboardTriangleRasterizer.blendPixel(frameCompositePixels[destIndex], DEBUG_OCCLUDED_PIXEL);
					}
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

	private BillboardDepthSurface billboardDepthSurface(PreparedBillboardDraw draw)
	{
		if (draw == null || draw.request == null || draw.request.localPoint == null || draw.request.renderable == null)
		{
			return BillboardDepthSurface.invalid();
		}

		BillboardRenderRequest request = draw.request;
		double baseHeight = tileHeightAt(request.localPoint.getX(), request.localPoint.getY(), request.plane);
		return BillboardDepthSurface.from(depthCalculator, request, draw.sourceBounds, draw.bounds, baseHeight);
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
		double yawSin = Perspective.SINE14[request.relativeYaw] / 65536.0;
		double yawCos = Perspective.COSINE14[request.relativeYaw] / 65536.0;
		int inversePitch = Math.floorMod(-request.relativePitch, BILLBOARD_FULL_CIRCLE);
		double pitchSin = Perspective.SINE14[inversePitch] / 65536.0;
		double pitchCos = Perspective.COSINE14[inversePitch] / 65536.0;
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
		if (!BillboardGeometryUtils.isUsableSourceBounds(sourceBounds))
		{
			return null;
		}

		return BillboardGeometryUtils.expandedBounds(sourceBounds, outlinePadding());
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

		BillboardOcclusionQuality occlusionQuality = BillboardOcclusionQuality.normalize(config.billboardOcclusionQuality());
		List<TerrainTraceTarget> occlusionTraceTargets = occlusionQuality == BillboardOcclusionQuality.OFF
			? Collections.emptyList()
			: buildOcclusionTraceTargets(candidates);
		collectTerrainOccluders(worldView, occlusionTraceTargets, occlusionQuality);
		markSuppressedStackedActors(collected);
		scheduleFrameUpdates(sortTargetsForRender(new ArrayList<>(candidates)), gameCycle);
		updateActiveSnapshots();
	}

	private List<TerrainTraceTarget> buildOcclusionTraceTargets(List<BillboardTarget> targets)
	{
		frameOcclusionInterestBounds = null;
		frameOcclusionInterestRegions.clear();
		if (targets == null || targets.isEmpty())
		{
			return Collections.emptyList();
		}

		int viewportX = client.getViewportXOffset();
		int viewportY = client.getViewportYOffset();
		int viewportWidth = client.getViewportWidth();
		int viewportHeight = client.getViewportHeight();
		if (viewportWidth <= 0 || viewportHeight <= 0)
		{
			return Collections.emptyList();
		}

		Rectangle viewportBounds = new Rectangle(viewportX, viewportY, viewportWidth, viewportHeight);
		List<TerrainTraceTarget> traceTargets = new ArrayList<>(targets.size());
		for (BillboardTarget target : targets)
		{
			TerrainTraceTarget traceTarget = addOcclusionTraceTarget(target, viewportBounds);
			if (traceTarget != null)
			{
				traceTargets.add(traceTarget);
			}
		}

		return frameOcclusionInterestBounds != null ? traceTargets : Collections.emptyList();
	}

	private TerrainTraceTarget addOcclusionTraceTarget(BillboardTarget target, Rectangle viewportBounds)
	{
		if (target == null)
		{
			return null;
		}

		if (target.type == BillboardTargetType.TILE_OBJECT)
		{
			return addTileObjectOcclusionTraceTarget(target, viewportBounds);
		}

		BillboardRenderRequest request = buildRenderRequest(target);
		addOcclusionInterest(buildSortPreviewBounds(request), viewportBounds);
		return request != null && request.localPoint != null
			? new TerrainTraceTarget(request.localPoint, request.plane)
			: null;
	}

	private TerrainTraceTarget addTileObjectOcclusionTraceTarget(BillboardTarget target, Rectangle viewportBounds)
	{
		if (target == null || target.observedTileObject == null)
		{
			return null;
		}

		TerrainTraceTarget traceTarget = null;
		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			BillboardRenderRequest request = buildRenderRequest(target, part);
			addOcclusionInterest(buildSortPreviewBounds(request), viewportBounds);
			if (traceTarget == null && request != null && request.localPoint != null)
			{
				traceTarget = new TerrainTraceTarget(request.localPoint, request.plane);
			}
		}

		return traceTarget;
	}

	private void addOcclusionInterest(Rectangle previewBounds, Rectangle viewportBounds)
	{
		if (previewBounds == null || viewportBounds == null)
		{
			return;
		}

		Rectangle clipped = viewportBounds.intersection(expandedOcclusionInterest(previewBounds, occlusionInterestMargin()));
		if (!clipped.isEmpty())
		{
			frameOcclusionInterestRegions.add(clipped);
			frameOcclusionInterestBounds = frameOcclusionInterestBounds == null
				? clipped
				: frameOcclusionInterestBounds.union(clipped);
		}
	}

	private void collectTerrainOccluders(WorldView worldView, List<TerrainTraceTarget> traceTargets, BillboardOcclusionQuality quality)
	{
		if (quality == BillboardOcclusionQuality.OFF || frameOcclusionInterestBounds == null || worldView == null || traceTargets == null || traceTargets.isEmpty())
		{
			return;
		}

		Scene scene = worldView.getScene();
		Tile[][][] tiles = scene != null ? scene.getTiles() : null;
		if (tiles == null)
		{
			return;
		}

		Set<Long> visitedTiles = new HashSet<>();
		Set<TileObject> visitedObjects = Collections.newSetFromMap(new IdentityHashMap<>());
		for (TerrainTraceTarget traceTarget : traceTargets)
		{
			if (traceTarget == null || !isPlaneEligible(traceTarget.plane))
			{
				continue;
			}

			collectTerrainCorridorOccluders(tiles, traceTarget, quality, visitedTiles, visitedObjects);
		}
	}

	private void collectTerrainCorridorOccluders(
		Tile[][][] tiles,
		TerrainTraceTarget target,
		BillboardOcclusionQuality quality,
		Set<Long> visitedTiles,
		Set<TileObject> visitedObjects)
	{
		int tileSize = BillboardConstants.LOCAL_TILE_SIZE;
		int cameraX = Math.round(client.getCameraFpX());
		int cameraY = Math.round(client.getCameraFpY());
		int targetX = target.localPoint.getX();
		int targetY = target.localPoint.getY();
		int dx = targetX - cameraX;
		int dy = targetY - cameraY;
		int steps = Math.max(Math.abs(dx), Math.abs(dy)) / tileSize;
		if (steps <= 0)
		{
			steps = 1;
		}

		int radiusTiles = terrainCorridorRadiusTiles(quality);
		for (int step = 0; step <= steps; step++)
		{
			int x = cameraX + (int) Math.round((double) dx * step / steps);
			int y = cameraY + (int) Math.round((double) dy * step / steps);
			int centerTileX = Math.floorDiv(x, tileSize);
			int centerTileY = Math.floorDiv(y, tileSize);
			for (int offsetX = -radiusTiles; offsetX <= radiusTiles; offsetX++)
			{
				for (int offsetY = -radiusTiles; offsetY <= radiusTiles; offsetY++)
				{
					collectTerrainTileOccluder(tiles, target.plane, centerTileX + offsetX, centerTileY + offsetY, quality, visitedTiles, visitedObjects);
				}
			}
		}
	}

	private int terrainCorridorRadiusTiles(BillboardOcclusionQuality quality)
	{
		switch (quality)
		{
			case MAX:
				return 10;
			case ULTRA:
				return 8;
			case HIGH:
				return 6;
			case MEDIUM:
				return 4;
			case LOW:
			default:
				return 2;
		}
	}

	private void collectTerrainTileOccluder(
		Tile[][][] tiles,
		int plane,
		int tileX,
		int tileY,
		BillboardOcclusionQuality quality,
		Set<Long> visitedTiles,
		Set<TileObject> visitedObjects)
	{
		if (plane < 0 || plane >= tiles.length || tiles[plane] == null || tileX < 0 || tileX >= tiles[plane].length)
		{
			return;
		}

		Tile[] row = tiles[plane][tileX];
		if (row == null || tileY < 0 || tileY >= row.length)
		{
			return;
		}

		long key = terrainTileKey(plane, tileX, tileY);
		if (!visitedTiles.add(key))
		{
			return;
		}

		collectTerrainTileOccluder(row[tileY], quality, visitedObjects);
	}

	private long terrainTileKey(int plane, int tileX, int tileY)
	{
		return ((long) plane << 48) ^ ((long) (tileX & 0xFFFFFF) << 24) ^ (tileY & 0xFFFFFFL);
	}

	private void collectTerrainTileOccluder(Tile tile, BillboardOcclusionQuality quality, Set<TileObject> visitedObjects)
	{
		if (tile == null || !isPlaneEligible(tile.getPlane()))
		{
			return;
		}

		LocalPoint localPoint = tile.getLocalLocation();
		if (localPoint == null)
		{
			return;
		}

		debugOcclusionTerrainTilesConsidered++;
		SceneTileModel model = tile.getSceneTileModel();
		int acceptedFacesBefore = debugOcclusionTerrainFacesAccepted;
		if (model != null)
		{
			collectTerrainModelOccluders(model, tile.getPlane());
			if (debugOcclusionTerrainFacesAccepted > acceptedFacesBefore)
			{
				debugOcclusionTerrainTilesAccepted++;
			}
		}
		else
		{
			SceneTilePaint paint = tile.getSceneTilePaint();
			if (paint != null)
			{
				collectTerrainPaintOccluder(localPoint, tile.getPlane());
				if (debugOcclusionTerrainFacesAccepted > acceptedFacesBefore)
				{
					debugOcclusionTerrainTilesAccepted++;
				}
			}
		}

		collectSceneTileObjectOccluders(tile, quality, visitedObjects);
	}

	private void collectSceneTileObjectOccluders(Tile tile, BillboardOcclusionQuality quality, Set<TileObject> visitedObjects)
	{
		if (tile == null)
		{
			return;
		}

		collectSceneTileObjectOccluder(tile.getWallObject(), quality, visitedObjects);
		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects == null)
		{
			return;
		}

		for (GameObject gameObject : gameObjects)
		{
			collectSceneTileObjectOccluder(gameObject, quality, visitedObjects);
		}
	}

	private void collectSceneTileObjectOccluder(TileObject tileObject, BillboardOcclusionQuality quality, Set<TileObject> visitedObjects)
	{
		if (tileObject == null || !isPlaneEligible(tileObject.getPlane()) || !visitedObjects.add(tileObject))
		{
			return;
		}

		debugOcclusionSceneObjectsConsidered++;
		if (!BillboardSceneryOcclusionFilter.isOccluder(tileObject))
		{
			debugOcclusionSceneObjectsRejectedByFilter++;
			addDebugOcclusionScenerySample(debugOcclusionRejectedScenerySamples, tileObject);
			return;
		}

		if (!sceneryIntersectsOcclusionInterest(tileObject))
		{
			debugOcclusionSceneObjectsSkippedOutsideInterest++;
			return;
		}

		ObservedTileObject observed = ObservedTileObjectBuilder.build(tileObject);
		if (observed == null || observed.parts.isEmpty())
		{
			debugOcclusionSceneObjectsWithoutParts++;
			return;
		}

		int acceptedFacesBefore = debugOcclusionSceneObjectFacesAccepted;
		for (ObjectRenderablePart part : observed.parts)
		{
			collectSceneObjectPartOccluders(part, quality);
		}

		boolean acceptedModelFaces = debugOcclusionSceneObjectFacesAccepted > acceptedFacesBefore;
		boolean acceptedFallback = !acceptedModelFaces && collectSceneObjectFallbackOccluder(tileObject, observed, quality);
		if (acceptedModelFaces || acceptedFallback)
		{
			debugOcclusionSceneObjectsAccepted++;
			addDebugOcclusionScenerySample(debugOcclusionAcceptedScenerySamples, tileObject);
		}
	}

	private void addDebugOcclusionScenerySample(List<String> samples, TileObject tileObject)
	{
		if (!config.debugLogBillboardOcclusion() || samples == null || samples.size() >= DEBUG_OCCLUSION_SAMPLE_LIMIT || tileObject == null)
		{
			return;
		}

		samples.add(tileObject.getId() + ":" + debugObjectName(tileObject));
	}

	private String debugObjectName(TileObject tileObject)
	{
		if (tileObject == null || !client.isClientThread())
		{
			return "-";
		}

		net.runelite.api.ObjectComposition objectDefinition = client.getObjectDefinition(tileObject.getId());
		if (objectDefinition == null)
		{
			return "-";
		}

		String name = objectDefinition.getName();
		return name != null && !name.trim().isEmpty() ? name : "-";
	}

	private boolean collectSceneObjectFallbackOccluder(TileObject tileObject, ObservedTileObject observed, BillboardOcclusionQuality quality)
	{
		double fallbackDepth = tileObjectDepth(tileObject, observed, quality);
		if (!Double.isFinite(fallbackDepth))
		{
			return false;
		}

		if (addSceneryShapeFallbackOccluder(tileObject, observed, fallbackDepth, quality))
		{
			return true;
		}

		return addWorldOccluder(tileObject.getClickbox(), fallbackDepth);
	}

	private boolean sceneryIntersectsOcclusionInterest(TileObject tileObject)
	{
		if (tileObject instanceof WallObject)
		{
			WallObject wallObject = (WallObject) tileObject;
			return intersectsOcclusionInterest(wallObject.getConvexHull())
				|| intersectsOcclusionInterest(wallObject.getConvexHull2())
				|| intersectsOcclusionInterest(tileObject.getClickbox());
		}

		if (tileObject instanceof GameObject)
		{
			return intersectsOcclusionInterest(((GameObject) tileObject).getConvexHull())
				|| intersectsOcclusionInterest(tileObject.getClickbox());
		}

		return false;
	}

	private boolean intersectsOcclusionInterest(Shape shape)
	{
		return shape != null && intersectsOcclusionInterest(shape.getBounds());
	}

	private boolean intersectsOcclusionInterest(Rectangle bounds)
	{
		if (bounds == null || bounds.isEmpty())
		{
			return false;
		}

		if (frameOcclusionInterestRegions.isEmpty())
		{
			return frameOcclusionInterestBounds != null && bounds.intersects(frameOcclusionInterestBounds);
		}

		for (Rectangle region : frameOcclusionInterestRegions)
		{
			if (region != null && bounds.intersects(region))
			{
				return true;
			}
		}

		return false;
	}

	private void collectSceneObjectPartOccluders(ObjectRenderablePart part, BillboardOcclusionQuality quality)
	{
		if (part == null || part.renderable == null || part.localPoint == null)
		{
			return;
		}

		Model model = part.renderable.getModel();
		if (model == null || model.getVerticesCount() <= 0 || model.getFaceCount() <= 0)
		{
			return;
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		int[] faceIndices1 = model.getFaceIndices1();
		int[] faceIndices2 = model.getFaceIndices2();
		int[] faceIndices3 = model.getFaceIndices3();
		if (verticesX == null || verticesY == null || verticesZ == null || faceIndices1 == null || faceIndices2 == null || faceIndices3 == null)
		{
			return;
		}

		int vertexCount = Math.min(model.getVerticesCount(), Math.min(verticesX.length, Math.min(verticesY.length, verticesZ.length)));
		int faceCount = Math.min(model.getFaceCount(), Math.min(faceIndices1.length, Math.min(faceIndices2.length, faceIndices3.length)));
		int stride = objectFaceStride(quality);
		for (int face = 0; face < faceCount; face += stride)
		{
			int a = faceIndices1[face];
			int b = faceIndices2[face];
			int c = faceIndices3[face];
			if (a < 0 || b < 0 || c < 0 || a >= vertexCount || b >= vertexCount || c >= vertexCount)
			{
				continue;
			}

			addSceneObjectTriangleOccluder(
				part,
				verticesX[a], verticesY[a], verticesZ[a],
				verticesX[b], verticesY[b], verticesZ[b],
				verticesX[c], verticesY[c], verticesZ[c]
			);
		}
	}

	private int objectFaceStride(BillboardOcclusionQuality quality)
	{
		switch (quality)
		{
			case MAX:
				return 1;
			case ULTRA:
				return 1;
			case HIGH:
				return 1;
			case MEDIUM:
				return 3;
			case LOW:
			default:
				return 6;
		}
	}

	private void addSceneObjectTriangleOccluder(
		ObjectRenderablePart part,
		float vx0,
		float vy0,
		float vz0,
		float vx1,
		float vy1,
		float vz1,
		float vx2,
		float vy2,
		float vz2)
	{
		if (!Float.isFinite(vx0) || !Float.isFinite(vy0) || !Float.isFinite(vz0)
			|| !Float.isFinite(vx1) || !Float.isFinite(vy1) || !Float.isFinite(vz1)
			|| !Float.isFinite(vx2) || !Float.isFinite(vy2) || !Float.isFinite(vz2))
		{
			return;
		}

		WorldVertex p0 = sceneObjectVertex(part, vx0, vy0, vz0);
		WorldVertex p1 = sceneObjectVertex(part, vx1, vy1, vz1);
		WorldVertex p2 = sceneObjectVertex(part, vx2, vy2, vz2);
		addSceneObjectTriangleOccluder(p0, p1, p2);
	}

	private WorldVertex sceneObjectVertex(ObjectRenderablePart part, float vertexX, float vertexY, float vertexZ)
	{
		int localX = (int) Math.round(part.localPoint.getX() + vertexX);
		int localY = (int) Math.round(part.localPoint.getY() + vertexZ);
		int height = tileHeightAt(localX, localY, part.plane) + (int) Math.round(Math.max(0.0f, -vertexY));
		return new WorldVertex(localX, localY, height);
	}

	private void addSceneObjectTriangleOccluder(WorldVertex v0, WorldVertex v1, WorldVertex v2)
	{
		Point p0 = Perspective.localToCanvas(client, v0.localX, v0.localY, v0.height);
		Point p1 = Perspective.localToCanvas(client, v1.localX, v1.localY, v1.height);
		Point p2 = Perspective.localToCanvas(client, v2.localX, v2.localY, v2.height);
		if (p0 == null || p1 == null || p2 == null)
		{
			return;
		}

		Polygon polygon = new Polygon(
			new int[] { p0.getX(), p1.getX(), p2.getX() },
			new int[] { p0.getY(), p1.getY(), p2.getY() },
			3
		);
		if (!intersectsOcclusionInterest(polygon.getBounds()))
		{
			return;
		}

		double depth0 = cameraForwardDepthToWorldPoint(v0.localX, v0.localY, v0.height);
		double depth1 = cameraForwardDepthToWorldPoint(v1.localX, v1.localY, v1.height);
		double depth2 = cameraForwardDepthToWorldPoint(v2.localX, v2.localY, v2.height);
		BillboardOcclusionMask.Occluder occluder = BillboardOcclusionMask.Occluder.triangle(
			p0.getX(), p0.getY(), (float) depth0,
			p1.getX(), p1.getY(), (float) depth1,
			p2.getX(), p2.getY(), (float) depth2
		);
		if (occluder == null)
		{
			return;
		}

		worldOccluders.add(occluder);
		debugOcclusionSceneObjectFacesAccepted++;
		debugOcclusionShapesAccepted++;
		debugOcclusionTriangleOccludersAccepted++;
	}

	private void collectTerrainPaintOccluder(LocalPoint localPoint, int plane)
	{
		int size = BillboardConstants.LOCAL_TILE_SIZE;
		int halfSize = size / 2;
		int x = localPoint.getX() - halfSize;
		int y = localPoint.getY() - halfSize;
		int swHeight = tileHeightAt(x, y, plane);
		int seHeight = tileHeightAt(x + size, y, plane);
		int neHeight = tileHeightAt(x + size, y + size, plane);
		int nwHeight = tileHeightAt(x, y + size, plane);
		addTerrainTriangleOccluder(x, y, swHeight, x + size, y, seHeight, x + size, y + size, neHeight, plane);
		addTerrainTriangleOccluder(x, y, swHeight, x + size, y + size, neHeight, x, y + size, nwHeight, plane);
	}

	private void collectTerrainModelOccluders(SceneTileModel model, int plane)
	{
		int[] vertexX = model.getVertexX();
		int[] vertexY = model.getVertexY();
		int[] vertexZ = model.getVertexZ();
		int[] faceX = model.getFaceX();
		int[] faceY = model.getFaceY();
		int[] faceZ = model.getFaceZ();
		if (vertexX == null || vertexY == null || vertexZ == null || faceX == null || faceY == null || faceZ == null)
		{
			return;
		}

		int faceCount = Math.min(faceX.length, Math.min(faceY.length, faceZ.length));
		int vertexCount = Math.min(vertexX.length, Math.min(vertexY.length, vertexZ.length));
		for (int face = 0; face < faceCount; face++)
		{
			int a = faceX[face];
			int b = faceY[face];
			int c = faceZ[face];
			if (a < 0 || b < 0 || c < 0 || a >= vertexCount || b >= vertexCount || c >= vertexCount)
			{
				continue;
			}

			addTerrainTriangleOccluder(
				vertexX[a], vertexZ[a], vertexY[a],
				vertexX[b], vertexZ[b], vertexY[b],
				vertexX[c], vertexZ[c], vertexY[c],
				plane
			);
		}
	}

	private void addTerrainTriangleOccluder(
		int x0,
		int y0,
		int z0,
		int x1,
		int y1,
		int z1,
		int x2,
		int y2,
		int z2,
		int plane)
	{
		Point p0 = Perspective.localToCanvas(client, x0, y0, z0);
		Point p1 = Perspective.localToCanvas(client, x1, y1, z1);
		Point p2 = Perspective.localToCanvas(client, x2, y2, z2);
		if (p0 == null || p1 == null || p2 == null)
		{
			return;
		}

		Polygon polygon = new Polygon(
			new int[] { p0.getX(), p1.getX(), p2.getX() },
			new int[] { p0.getY(), p1.getY(), p2.getY() },
			3
		);
		if (!intersectsOcclusionInterest(polygon.getBounds()))
		{
			return;
		}

		double depth0 = cameraForwardDepthToWorldPoint(x0, y0, z0) + TERRAIN_OCCLUSION_DEPTH_BIAS;
		double depth1 = cameraForwardDepthToWorldPoint(x1, y1, z1) + TERRAIN_OCCLUSION_DEPTH_BIAS;
		double depth2 = cameraForwardDepthToWorldPoint(x2, y2, z2) + TERRAIN_OCCLUSION_DEPTH_BIAS;
		BillboardOcclusionMask.Occluder occluder = BillboardOcclusionMask.Occluder.triangle(
			p0.getX(), p0.getY(), (float) depth0,
			p1.getX(), p1.getY(), (float) depth1,
			p2.getX(), p2.getY(), (float) depth2
		);
		if (occluder == null)
		{
			return;
		}

		worldOccluders.add(occluder);
		debugOcclusionTerrainFacesAccepted++;
		debugOcclusionShapesAccepted++;
		debugOcclusionTriangleOccludersAccepted++;
	}

	private int tileHeightAt(int localX, int localY, int plane)
	{
		return Perspective.getTileHeight(client, new LocalPoint(localX, localY), plane);
	}

	private double cameraForwardDepthToWorldPoint(int localX, int localY, int height)
	{
		return depthCalculator.cameraForwardDepth(localX, localY, height);
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
		Actor hoveredActor = BillboardHoverInteractionResolver.hoveredActor(client);
		Actor interactionActor = BillboardHoverInteractionResolver.interactedActor(client, interactedActor);
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
				frameUpdatePlans.put(target.targetKey, new FrameUpdatePlan(
					renderQualityScale(),
					true,
					null,
					null,
					client.getGameCycle(),
					1,
					renderQualityScale(),
					minimumAnimatedRedrawInterval()));
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
				|| updateState.hasViewChangedSinceRedraw(snapshot)
				|| updateState.hasModelChangedSinceRedraw(snapshot)
				|| updateState.hasAnimationChangedSinceRedraw(snapshot)
				|| updateState.hasTextureChangedSinceRedraw(snapshot);
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

			frameUpdatePlans.put(key, new FrameUpdatePlan(
				qualityScale,
				forceHoverInteractionRedraw,
				updateState,
				snapshot,
				gameCycle,
				baseRefreshInterval,
				renderQualityScale(),
				minimumAnimatedRedrawInterval()));
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
			debugQueuePriorityScores.put(key, score);
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

		score += BillboardUpdatePriority.stateChangeScore(state, snapshot);

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
				debugQueuePriorityScores.remove(key);
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
			double initialQualityScale = BillboardUpdateScheduler.initialQualityScale(
				targetHasCachedBillboard(target),
				renderQualityScale(),
				nearPriorityIndex,
				maxDraws);
			billboardUpdateStates.put(target.targetKey, new BillboardUpdateState(initialQualityScale));
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
						depthCalculator.depth(npc),
						npc.getLocalLocation(),
						npc.getWorldView().getPlane()
					)
				);
				classificationDebug.logDecision(npc, ObjectClassifier.classifyDecision(npc, client));
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
						depthCalculator.depth(player),
						player.getLocalLocation(),
						player.getWorldView().getPlane()
					)
				);
				classificationDebug.logDecision(player, ObjectClassifier.classifyDecision(player, client));
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
					depthCalculator.depth(projectile),
					BillboardProjectileGeometry.localPoint(projectile),
					projectile.getFloor()
				));
				classificationDebug.logDecision(projectile, ObjectClassifier.classifyDecision(projectile, client));
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
					depthCalculator.depth(graphicsObject),
					graphicsObject.getLocation(),
					graphicsObject.getLevel()
				));
				classificationDebug.logDecision(graphicsObject, ObjectClassifier.classifyDecision(graphicsObject, client));
			}
		}

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.GROUND_ITEM, config))
		{
			for (Map.Entry<TileItem, GroundItemBillboard> entry : groundItemTracker.entries())
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

				candidates.add(BillboardTarget.forGroundItem(item, groundItem, depthCalculator.depth(item, groundItem)));
				classificationDebug.logDecision(item, ObjectClassifier.classifyDecision(item, client));
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

				ClassifiedObjectType classifiedType = classificationDebug.resolveObservedTileObjectType(observed);
				if (classifiedType == ClassifiedObjectType.EFFECT)
				{
					if (ObjectClassifier.isEnabled(ClassifiedObjectType.EFFECT, config))
					{
						candidates.add(BillboardTarget.forTileObject(observed, depthCalculator.depth(observed)));
					}
				}
				else if (classifiedType == ClassifiedObjectType.OBJECT && ObjectClassifier.isEnabled(ClassifiedObjectType.OBJECT, config))
				{
					candidates.add(BillboardTarget.forTileObject(observed, depthCalculator.depth(observed)));
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

		classificationDebug.resolveObservedTileObjectType(observed);
		return BillboardTarget.forTileObject(observed, depthCalculator.depth(observed));
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
			orientationCalculator.relativeYaw(),
			orientationCalculator.relativePitch(),
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
			orientationCalculator.relativeYaw(actor),
			orientationCalculator.relativePitch(actor),
			actor.getAnimation(),
			actor.getAnimationFrame(),
			actor.getPoseAnimation(),
			actor.getPoseAnimationFrame(),
			BillboardAnimatedTextures.findAnimatedTextureId(actor),
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
			orientationCalculator.relativeYaw(actor),
			orientationCalculator.relativePitch(),
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
			BillboardProjectileGeometry.localPoint(projectile),
			projectile.getFloor(),
			BillboardProjectileGeometry.verticalOffset(client, projectile),
			orientationCalculator.relativeYaw(projectile),
			orientationCalculator.relativePitch(),
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
			orientationCalculator.relativeYaw(),
			orientationCalculator.relativePitch(),
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
			orientationCalculator.relativeGroundItemYaw(),
			orientationCalculator.relativeGroundItemPitch(),
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

		if (!isPlaneEligible(actor.getWorldView().getPlane()))
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
		LocalPoint projectileLocation = BillboardProjectileGeometry.localPoint(projectile);
		if (localPlayerLocation == null || projectileLocation == null)
		{
			return false;
		}

		if (!isPlaneEligible(projectile.getFloor()))
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

		Point canvasPoint = Perspective.localToCanvas(client, projectileLocation, projectile.getFloor(), BillboardProjectileGeometry.verticalOffset(client, projectile));
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

			candidates.add(BillboardTarget.forActorSpotAnim(actorSpotAnim, actor, depthCalculator.depth(actorSpotAnim, actor)));
			classificationDebug.logDecision(
				actorSpotAnim,
				ObjectClassifier.classifyDecision(actorSpotAnim, client),
				classificationDebug.actorDebugKey(actor)
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

		if (!isPlaneEligible(graphicsObject.getLevel()))
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

		if (!isPlaneEligible(actor.getWorldView().getPlane()))
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

	private boolean isEligibleGroundItem(LocalPoint localPlayerLocation, TileItem item, GroundItemBillboard groundItem, Rectangle viewport)
	{
		if (localPlayerLocation == null || groundItem.localPoint == null)
		{
			return false;
		}

		if (!isPlaneEligible(groundItem.plane))
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

			if (!isPlaneEligible(part.plane))
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

		if (nowMillis - cached.lastRedrawMillis() > BillboardConstants.CACHE_TTL_MILLIS)
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

		return BillboardUpdateScheduler.allTileObjectPartsCached(
			target.observedTileObject.parts,
			billboardCache::containsKey);
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
		if (nowMillis - cached.lastRedrawMillis() > BillboardConstants.CACHE_TTL_MILLIS)
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

		return UpdateHeuristicSnapshot.fromRequest(
			request,
			target.getDepth(),
			textureResolver.animatedTextureOffsetStateHash(request.animatedTextureId, System.currentTimeMillis())
		);
	}

	private UpdateHeuristicSnapshot buildTileObjectHeuristicSnapshot(BillboardTarget target)
	{
		if (target == null || target.observedTileObject == null)
		{
			return UpdateHeuristicSnapshot.empty();
		}

		long positionKey = 0L;
		int animationHash = 1;
		int modelStateHash = 1;
		int textureStateHash = 1;
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
				modelStateHash = (31 * modelStateHash) + BillboardModelStateHash.hash(request.model);
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
				modelStateHash = (31 * modelStateHash) + System.identityHashCode(part.renderable);
			}
		}

		return new UpdateHeuristicSnapshot(
			target.getDepth(),
			sawPosition ? positionKey : Long.MIN_VALUE,
			animationHash,
			modelStateHash,
			textureStateHash,
			viewHash,
			animated
		);
	}

	private RenderedBillboardImage renderBillboardImage(List<FaceDraw> faces, Rectangle bounds, int outlinePadding, double qualityScaleOverride, boolean shouldHoverOutline, boolean shouldInteractOutline)
	{
		Rectangle imageBounds = BillboardGeometryUtils.expandedBounds(bounds, outlinePadding);
		double qualityScale = renderQualityScale(qualityScaleOverride);
		int imageWidth = Math.max(1, (int) Math.round(imageBounds.width * qualityScale));
		int imageHeight = Math.max(1, (int) Math.round(imageBounds.height * qualityScale));
		if (!BillboardGeometryUtils.isUsableDrawSize(imageWidth, imageHeight))
		{
			return null;
		}

		BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
		int[] imagePixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		for (FaceDraw face : faces)
		{
			if (face.isTextured())
			{
				BillboardFaceRasterizer.rasterizeTexturedFace(
					imagePixels,
					imageWidth,
					imageHeight,
					imageBounds,
					qualityScale,
					face,
					config.billboardColorBands()
				);
				continue;
			}

			Color snappedColor = NpcSnapColorBanding.snapToRamp(face.getColor(), config.billboardColorBands());
			BillboardFaceRasterizer.rasterizeSolidFace(
				imagePixels,
				imageWidth,
				imageHeight,
				imageBounds,
				qualityScale,
				face,
				snappedColor.getRGB()
			);
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

	private void expireCaches(long nowMillis)
	{
		expireBillboardCache(nowMillis);
		textureResolver.expire(nowMillis);
	}

	private void expireBillboardCache(long nowMillis)
	{
		Iterator<Map.Entry<Renderable, CachedBillboard>> iterator = billboardCache.entrySet().iterator();
		while (iterator.hasNext())
		{
			Map.Entry<Renderable, CachedBillboard> entry = iterator.next();
			CachedBillboard cached = entry.getValue();
			if (nowMillis - cached.lastUsedMillis() > BillboardConstants.CACHE_TTL_MILLIS)
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
			if (nowMillis - entry.getValue().lastUsedMillis() > BillboardConstants.CACHE_TTL_MILLIS)
			{
				iterator.remove();
			}
		}
	}
	
	private double renderQualityScale()
	{
		return BillboardRenderQuality.fromPercent(config.renderBillboardQuality());
	}

	private int qualityKey()
	{
		return BillboardRenderQuality.key(renderQualityScale());
	}

	private double renderQualityScale(double qualityScale)
	{
		return BillboardRenderQuality.clamp(qualityScale);
	}

	private int qualityKey(double qualityScale)
	{
		return BillboardRenderQuality.key(qualityScale);
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
		return BillboardRenderQuality.seededScale(renderQualityScale(), nearPriorityIndex, maxUpdatesPerFrame);
	}

	private boolean isWithinRadius(LocalPoint source, LocalPoint target, int radiusTiles)
	{
		int dx = source.getX() - target.getX();
		int dy = source.getY() - target.getY();
		int radius = radiusTiles * BillboardConstants.LOCAL_TILE_SIZE;
		return (dx * dx) + (dy * dy) <= (radius * radius);
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
		int animatedTextureId,
		boolean cullBackFaces
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
		int debugColorLogs = 0;

		for (int face = 0; face < model.getFaceCount(); face++)
		{
			int a = faceIndices1[face];
			int b = faceIndices2[face];
			int c = faceIndices3[face];
			if (cullBackFaces && BillboardGeometryUtils.isBackFace(spriteX, spriteY, spriteDepth, a, b, c))
			{
				continue;
			}

			int alpha = transparencies == null || face >= transparencies.length ? 255 : 255 - (transparencies[face] & 0xFF);
			Color rawColor = BillboardColorUtils.resolveFaceColor(face, faceColors1, faceColors2, faceColors3, unlitFaceColors, alpha);
			if (BillboardColorUtils.isSkippedCapeArtifactFaceColor(rawColor))
			{
				continue;
			}
			Color color = BillboardColorUtils.applyLightBoost(rawColor, config.billboardLightBoostPercent());

			double depth = (
				spriteDepth[a] +
				spriteDepth[b] +
				spriteDepth[c]
			) / 3.0;

			int textureId = textures != null && face < textures.length ? Short.toUnsignedInt(textures[face]) : 0xFFFF;
			if (debugColorLogs < 96 && shouldLogBillboardFaceColor(face, model.getFaceCount(), rawColor, textureId))
			{
				debugColorLogs++;
				logBillboardFaceColor(face, model.getFaceCount(), a, b, c, textureId, faceColors1, faceColors2, faceColors3, unlitFaceColors, rawColor, color);
			}
			if (textureId != 0xFFFF)
			{
				TextureSample textureSample = textureResolver.resolveTextureSample(textureId, nowMillis, animatedTextureId);
				TextureUvs textureUvs = textureResolver.computeTextureUvs(model, face);
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

	private boolean shouldLogBillboardFaceColor(int face, int faceCount, Color rawColor, int textureId)
	{
		if (!config.debugLogBillboardColors() || rawColor == null)
		{
			return false;
		}

		float[] hsb = Color.RGBtoHSB(rawColor.getRed(), rawColor.getGreen(), rawColor.getBlue(), null);
		return hsb[2] <= 0.25f || hsb[1] <= 0.08f || textureId != 0xFFFF || face >= Math.max(0, faceCount - 180);
	}

	private void logBillboardFaceColor(
		int face,
		int faceCount,
		int vertexA,
		int vertexB,
		int vertexC,
		int textureId,
		int[] faceColors1,
		int[] faceColors2,
		int[] faceColors3,
		short[] unlitFaceColors,
		Color rawColor,
		Color boostedColor
	)
	{
		int alpha = rawColor.getAlpha();
		Integer packed1 = BillboardColorUtils.faceColorValue(faceColors1, face);
		Integer packed2 = BillboardColorUtils.faceColorValue(faceColors2, face);
		Integer packed3 = BillboardColorUtils.faceColorValue(faceColors3, face);
		Integer unlit = BillboardColorUtils.unlitFaceColorValue(unlitFaceColors, face);
		Color litColor = BillboardColorUtils.decodedLitFaceColor(face, faceColors1, faceColors2, faceColors3, alpha);
		Color unlitColor = unlit != null ? BillboardColorUtils.resolveFaceColor(face, null, null, null, unlitFaceColors, alpha) : null;
		Color snappedColor = NpcSnapColorBanding.snapToRamp(boostedColor, config.billboardColorBands());

		log.debug(
			"Billboard face color face={}/{} verts=({},{},{}) texture={} f1={} f2={} f3={} unlit={} lit={} unlitRgb={} selected={} boosted={} snapped={}",
			face,
			faceCount,
			vertexA,
			vertexB,
			vertexC,
			textureId == 0xFFFF ? "none" : textureId,
			packed1,
			packed2,
			packed3,
			unlit,
			BillboardColorUtils.formatColor(litColor),
			BillboardColorUtils.formatColor(unlitColor),
			BillboardColorUtils.formatColor(rawColor),
			BillboardColorUtils.formatColor(boostedColor),
			BillboardColorUtils.formatColor(snappedColor)
		);
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
		boolean updatePlanSucceeded = false;
		if (updatePlan != null)
		{
			float[] verticesX = model.getVerticesX();
			float[] verticesY = model.getVerticesY();
			float[] verticesZ = model.getVerticesZ();
			ensureSpriteScratchCapacity(vertexCount);
			float[] spriteX = spriteXScratch;
			float[] spriteY = spriteYScratch;
			float[] spriteDepth = spriteDepthScratch;
			double yawSin = Perspective.SINE14[request.relativeYaw] / 65536.0;
			double yawCos = Perspective.COSINE14[request.relativeYaw] / 65536.0;
			int inversePitch = Math.floorMod(-request.relativePitch, BILLBOARD_FULL_CIRCLE);
			double pitchSin = Perspective.SINE14[inversePitch] / 65536.0;
			double pitchCos = Perspective.COSINE14[inversePitch] / 65536.0;
			for (int i = 0; i < vertexCount; i++)
			{
				double rotatedX = (verticesX[i] * yawCos) + (verticesZ[i] * yawSin);
				double rotatedZ = (verticesZ[i] * yawCos) - (verticesX[i] * yawSin);
				spriteX[i] = (float) rotatedX;
				spriteY[i] = (float) ((verticesY[i] * pitchCos) - (rotatedZ * pitchSin));
				spriteDepth[i] = (float) ((rotatedZ * pitchCos) + (verticesY[i] * pitchSin));
			}

			BuiltFaces builtFaces = buildFaces(model, spriteX, spriteY, spriteDepth, nowMillis, request.animatedTextureId, true);
			if (builtFaces.faces.isEmpty())
			{
				builtFaces = buildFaces(model, spriteX, spriteY, spriteDepth, nowMillis, request.animatedTextureId, false);
			}
			if (!builtFaces.faces.isEmpty())
			{
				List<FaceDraw> faces = builtFaces.faces;
				faces.sort(Comparator.comparingDouble(FaceDraw::getDepth).reversed());
				Rectangle sourceBounds = BillboardGeometryUtils.computeBounds(faces);
				if (BillboardGeometryUtils.isUsableSourceBounds(sourceBounds))
				{
					int outlinePadding = outlinePadding();
					Rectangle imageBounds = BillboardGeometryUtils.expandedBounds(sourceBounds, outlinePadding);
					if (buildDrawRect(request, renderable, imageBounds) == null)
					{
						return cached == null ? null : drawCachedBillboard(request, renderable, cached, nowMillis, queuePosition);
					}

					BillboardCacheKey cacheKey = buildCacheKey(
						request,
						outlinePadding,
						builtFaces.textureStateHash,
						qualityKey(updatePlan.qualityScale),
						textureResolver.animatedTextureOffsetStateHash(request.animatedTextureId, nowMillis)
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
							updatePlanSucceeded = true;
						}
					}
					else if (cached != null)
					{
						cached.touch(nowMillis);
						updatePlanSucceeded = true;
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

		if (updatePlan != null && updatePlanSucceeded)
		{
			updatePlan.markRedrawSucceeded();
		}

		return drawCachedBillboard(request, renderable, cached, spriteRedrawn ? -1L : nowMillis, queuePosition);
	}

	private BillboardRenderResult drawCachedBillboard(
		BillboardRenderRequest request,
		Renderable renderable,
		CachedBillboard cached,
		long touchMillis,
		int queuePosition)
	{
		if (touchMillis >= 0L)
		{
			cached.touch(touchMillis);
		}

		cached.stateDebugInfo(buildStateDebugInfo(request, queuePosition));
		Rectangle drawRect = buildDrawRect(request, renderable, cached.bounds);
		return drawRect != null ? new BillboardRenderResult(drawRect, cached.image, cached.bounds) : null;
	}

	private BillboardCacheKey buildCacheKey(
		BillboardRenderRequest request,
		int outlinePadding,
		int textureStateHash,
		int renderQualityKey,
		int animatedTextureOffsetStateHash)
	{
		int modelStateHash = BillboardModelStateHash.hash(request.model);
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
		int modelStateHash = BillboardModelStateHash.hash(request.model);
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
			textureResolver.animatedTextureOffsetStateHash(request.animatedTextureId, nowMillis)
		);
	}

	private Rectangle buildDrawRect(Rectangle cachedBounds, int anchorX, int anchorY, int targetWidth, int targetHeight)
	{
		double originX = (-cachedBounds.x) * (targetWidth / (double) cachedBounds.width);
		double originY = (-cachedBounds.y) * (targetHeight / (double) cachedBounds.height);
		int drawX = (int) Math.round(anchorX - originX);
		int drawY = (int) Math.round(anchorY - originY);
		if (!BillboardGeometryUtils.isUsableDrawSize(targetWidth, targetHeight) || isOutsideViewport(drawX, drawY, targetWidth, targetHeight))
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
		double distance = depthCalculator.cameraDistance(request.localPoint, request.plane, request.verticalOffset + (renderable.getModelHeight() / 2.0));
		if (!BillboardGeometryUtils.isUsableDistance(distance))
		{
			return null;
		}

		double perspectiveScale = client.get3dZoom() / Math.max(1.0, distance);
		int distanceHeight = BillboardGeometryUtils.scaledSize(billboardBounds.height, perspectiveScale);
		int projectedHeight = BillboardGeometryUtils.projectedHeight(basePoint, topPoint);
		int targetHeight = distanceHeight > 0 ? distanceHeight : projectedHeight;
		int targetWidth = BillboardGeometryUtils.aspectWidth(billboardBounds, targetHeight);
		int anchorX = request.verticalAnchor == VerticalAnchor.CENTER && centerPoint != null ? centerPoint.getX() : basePoint.getX();
		int anchorY = request.verticalAnchor == VerticalAnchor.CENTER && centerPoint != null ? centerPoint.getY() : basePoint.getY();
		if (!BillboardGeometryUtils.isUsableCanvasCoordinate(anchorX) || !BillboardGeometryUtils.isUsableCanvasCoordinate(anchorY))
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

	private int snapFrame(net.runelite.api.Animation animation, int frame, boolean enabled)
	{
		return animationFrameSnapper.snapFrame(animation, frame, enabled, Math.max(1, config.animationFrameCount()));
	}

	private ObservedTileObject buildObservedTileObject(TileObject tileObject)
	{
		return ObservedTileObjectBuilder.build(tileObject);
	}

	private boolean addWorldOccluder(Shape shape, double depth)
	{
		if (shape == null || shape.getBounds().isEmpty())
		{
			return false;
		}

		if (frameOcclusionInterestBounds != null && !intersectsOcclusionInterest(shape.getBounds()))
		{
			return false;
		}

		worldOccluders.add(new BillboardOcclusionMask.Occluder(shape, (float) depth));
		debugOcclusionShapesAccepted++;
		debugOcclusionFlatFallbackOccludersAccepted++;
		return true;
	}

	private double tileObjectDepth(TileObject tileObject, ObservedTileObject observed, BillboardOcclusionQuality quality)
	{
		LocalPoint localPoint = tileObject.getLocalLocation();
		if (localPoint == null)
		{
			return Double.NaN;
		}

		double nearest = Double.POSITIVE_INFINITY;
		if (observed != null && !observed.parts.isEmpty())
		{
			for (ObjectRenderablePart part : observed.parts)
			{
				double depth = nearestModelVertexDepth(part, quality.vertexStride());
				if (Double.isFinite(depth))
				{
					nearest = Math.min(nearest, depth);
				}
			}
		}

		if (Double.isFinite(nearest))
		{
			return nearest;
		}

		return depthCalculator.cameraForwardDepth(localPoint, tileObject.getPlane(), 0.0d);
	}

	private double nearestModelVertexDepth(ObjectRenderablePart part, int vertexStride)
	{
		if (part == null || part.renderable == null || part.localPoint == null)
		{
			return Double.NaN;
		}

		Model model = part.renderable.getModel();
		if (model == null || model.getVerticesCount() <= 0)
		{
			return depthCalculator.cameraForwardDepth(part.localPoint, part.plane, Math.max(0, part.renderable.getModelHeight() / 2.0d));
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		if (verticesX == null || verticesY == null || verticesZ == null)
		{
			return depthCalculator.cameraForwardDepth(part.localPoint, part.plane, Math.max(0, part.renderable.getModelHeight() / 2.0d));
		}

		int vertexCount = Math.min(model.getVerticesCount(), Math.min(verticesX.length, Math.min(verticesY.length, verticesZ.length)));
		int stride = Math.max(1, vertexStride);
		double nearest = Double.POSITIVE_INFINITY;
		for (int i = 0; i < vertexCount; i += stride)
		{
			double depth = modelVertexDepth(part.localPoint, part.plane, verticesX[i], verticesY[i], verticesZ[i]);
			if (Double.isFinite(depth))
			{
				nearest = Math.min(nearest, depth);
			}
		}

		if (stride > 1 && vertexCount > 0)
		{
			double depth = modelVertexDepth(part.localPoint, part.plane, verticesX[vertexCount - 1], verticesY[vertexCount - 1], verticesZ[vertexCount - 1]);
			if (Double.isFinite(depth))
			{
				nearest = Math.min(nearest, depth);
			}
		}

		return Double.isFinite(nearest)
			? nearest
			: depthCalculator.cameraForwardDepth(part.localPoint, part.plane, Math.max(0, part.renderable.getModelHeight() / 2.0d));
	}

	private double modelVertexDepth(LocalPoint base, int plane, float vertexX, float vertexY, float vertexZ)
	{
		if (!Float.isFinite(vertexX) || !Float.isFinite(vertexY) || !Float.isFinite(vertexZ))
		{
			return Double.NaN;
		}

		LocalPoint vertexLocalPoint = new LocalPoint(
			(int) Math.round(base.getX() + vertexX),
			(int) Math.round(base.getY() + vertexZ)
		);
		return depthCalculator.cameraForwardDepth(vertexLocalPoint, plane, Math.max(0.0d, -vertexY));
	}

	private boolean isPlaneEligible(int targetPlane)
	{
		if (config.renderBillboardsOnAllPlanes())
		{
			return true;
		}

		Player localPlayer = client.getLocalPlayer();
		return localPlayer != null && BillboardPlaneUtils.shouldRenderTargetPlane(localPlayer.getWorldView().getPlane(), targetPlane);
	}

	private static final class TerrainTraceTarget
	{
		private final LocalPoint localPoint;
		private final int plane;

		private TerrainTraceTarget(LocalPoint localPoint, int plane)
		{
			this.localPoint = localPoint;
			this.plane = plane;
		}
	}

	private static final class WorldVertex
	{
		private final int localX;
		private final int localY;
		private final int height;

		private WorldVertex(int localX, int localY, int height)
		{
			this.localX = localX;
			this.localY = localY;
			this.height = height;
		}
	}

}
