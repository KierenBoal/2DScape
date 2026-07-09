package com.kierenboal.npcsnap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
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
import net.runelite.api.GraphicsObject;
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
	private static final int FALLBACK_MAX_DRAW_SIZE = 8192;
	private static final double MIN_FORWARD_VISIBLE_DEPTH = 8.0d;
	private static final int DEBUG_OCCLUSION_DEPTH_SAMPLE_LIMIT = 24;
	private static final int DEBUG_OCCLUDED_PIXEL = new Color(255, 32, 32, 150).getRGB();
	private final Client client;
	private final NpcSnapConfig config;
	private final NpcSnapDebug debug;
	private final AnimationFrameSnapper animationFrameSnapper;
	private final BillboardDepthCalculator depthCalculator;
	private final BillboardWorldOcclusionCollector worldOcclusionCollector;
	private final BillboardOrientationCalculator orientationCalculator;
	private final Map<Renderable, CachedBillboard> billboardCache = new IdentityHashMap<>();
	private final BillboardTextureResolver textureResolver;
	private final BillboardPerformanceMetrics performanceMetrics = new BillboardPerformanceMetrics();
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
	private volatile Set<Renderable> activeBillboardSnapshot = Collections.emptySet();
	private volatile Set<Renderable> suppressedRenderableSnapshot = Collections.emptySet();
	private volatile Set<TileObject> activeTileObjectSnapshot = Collections.emptySet();
	private float[] spriteXScratch = new float[0];
	private float[] spriteYScratch = new float[0];
	private float[] spriteDepthScratch = new float[0];
	private boolean[] occlusionRowHasDepthScratch = new boolean[0];
	private double[] occlusionRowDepthScratch = new double[0];
	private BufferedImage frameCompositeImage;
	private int[] frameCompositePixels = new int[0];
	private char[] paintOrderBuffer = new char[0];
	private int frameCompositeWidth;
	private int frameCompositeHeight;
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
		this.worldOcclusionCollector = new BillboardWorldOcclusionCollector(client, config, depthCalculator, performanceMetrics, log);
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
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Render overlay"))
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

			List<BillboardTarget> visibleTargets;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Visible target lookup"))
			{
				visibleTargets = getVisibleTargets(worldView);
			}
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Hover/interaction update"))
			{
				updateHoverInteractionState();
				applyForcedHoverInteractionPlans(visibleTargets);
			}
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Target sorting"))
			{
				visibleTargets = sortTargetsForRender(visibleTargets);
			}
			List<PreparedBillboardDraw> preparedDraws = new ArrayList<>();
			for (int i = 0; i < visibleTargets.size(); i++)
			{
				renderTarget(visibleTargets.get(i), i + 1, preparedDraws);
			}
			compositePreparedDraws(graphics, preparedDraws);

			return null;
		}
		finally
		{
			performanceMetrics.finishFrame();
			drawPerformanceMetrics(graphics);
		}
	}

	private void drawPerformanceMetrics(Graphics2D graphics)
	{
		if (!performanceMetrics.isEnabled() || graphics == null)
		{
			return;
		}

		Rectangle viewport = getViewportBounds();
		if (viewport.width <= 0 || viewport.height <= 0)
		{
			return;
		}

		debug.drawPerformanceMetrics(graphics, viewport, performanceMetrics.rows(20));
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
		worldOcclusionCollector.clearInterest();
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
		performanceMetrics.beginFrame(config.debugPerformanceMetrics());
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Begin frame"))
		{
			expireCaches(System.currentTimeMillis());
			worldOcclusionCollector.resetFrame();
			debugOcclusionDepthSamples.clear();
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
	}

	void prepareFrame(WorldView worldView)
	{
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Prepare frame"))
		{
			ensureActiveBillboardsCurrent(worldView);
		}
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

	private boolean wasSceneRenderableDrawnThisFrame(Renderable renderable)
	{
		return renderable != null && sceneRenderablesThisFrame.contains(renderable);
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
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Per-target billboard render"))
		{
			if (target.type == BillboardTargetType.TILE_OBJECT)
			{
				renderTileObjectTarget(target, paintOrder, preparedDraws);
				return;
			}

			BillboardRenderRequest request;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Build render request"))
			{
				request = buildRenderRequest(target);
			}
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
	}

	private void renderTileObjectTarget(BillboardTarget target, int paintOrder, List<PreparedBillboardDraw> preparedDraws)
	{
		if (target.observedTileObject == null)
		{
			return;
		}

		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			BillboardRenderRequest request;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Build render request"))
			{
				request = buildRenderRequest(target, part);
			}
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
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Composite prepared draws"))
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

			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Clear frame buffers"))
			{
				ensureFrameCompositeCapacity(viewportWidth, viewportHeight);
				Arrays.fill(frameCompositePixels, 0, viewportWidth * viewportHeight, 0);
				Arrays.fill(paintOrderBuffer, 0, viewportWidth * viewportHeight, (char) 0);
			}
			List<Rectangle> occlusionRegions;
			Rectangle occlusionBounds;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Build occlusion regions"))
			{
				occlusionRegions = occlusionInterestRegions(preparedDraws, viewportX, viewportY, viewportWidth, viewportHeight);
				occlusionBounds = unionBounds(occlusionRegions);
			}
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Prepare occlusion mask"))
			{
				occlusionMask.prepare(worldOcclusionCollector.occluders(), config.billboardOcclusionQuality(), viewportX, viewportY, viewportWidth, viewportHeight, occlusionBounds, occlusionRegions);
			}
			collectDebugOcclusionDepthSamples(preparedDraws);
			worldOcclusionCollector.logDebugStats(occlusionMask, occlusionBounds, debugOcclusionDepthSamples);
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Per-pixel billboard compositing"))
			{
				for (int i = preparedDraws.size() - 1; i >= 0; i--)
				{
					blitPreparedDraw(preparedDraws.get(i), viewportX, viewportY, viewportWidth, viewportHeight);
				}
			}

			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Draw composite image"))
			{
				graphics.drawImage(frameCompositeImage, viewportX, viewportY, null);
			}
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Debug overlay drawing"))
			{
				if (config.debugDrawBillboardOcclusionMask())
				{
					occlusionMask.drawDebug(graphics);
				}
				for (PreparedBillboardDraw draw : preparedDraws)
				{
					drawRenderDebug(graphics, draw.result, draw.request, draw.paintOrder);
				}
			}
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

		int drawWidth = draw.bounds.width;
		int drawHeight = draw.bounds.height;
		int paintOrder = draw.paintOrder;
		boolean hasActiveOcclusion = occlusionMask.coveredCellCount() > 0;
		BillboardDepthSurface billboardDepth = hasActiveOcclusion ? billboardDepthSurface(draw) : null;
		int clippedHeight = clipBottom - clipTop;
		if (hasActiveOcclusion)
		{
			ensureOcclusionRowScratchCapacity(clippedHeight);
			Arrays.fill(occlusionRowHasDepthScratch, 0, clippedHeight, false);
		}

		long occlusionElapsedNanos = 0L;
		boolean measureOcclusion = performanceMetrics.isEnabled() && hasActiveOcclusion;
		int occlusionSampleStep = hasActiveOcclusion ? occlusionMask.sampleStep() : 0;
		for (int y = clipTop; y < clipBottom; y++)
		{
			int destY = y - viewportY;
			int sourceY = (int) (((long) (y - draw.bounds.y) * sourceHeight) / drawHeight);
			int destRow = destY * viewportWidth;
			int sourceRow = sourceY * sourceWidth;
			long sourceXNumerator = (long) (clipLeft - draw.bounds.x) * sourceWidth;
			int clippedRow = y - clipTop;
			boolean rowHasOcclusionCoverage = hasActiveOcclusion && occlusionMask.hasCoverageAt(y);
			long rowOcclusionStart = measureOcclusion && rowHasOcclusionCoverage ? System.nanoTime() : 0L;
			boolean cachedSampleOccluded = false;
			int cachedSampleX = Integer.MIN_VALUE;
			for (int x = clipLeft; x < clipRight; x++)
			{
				int destX = x - viewportX;
				int destIndex = destRow + destX;
				if (paintOrderBuffer[destIndex] > paintOrder)
				{
					sourceXNumerator += sourceWidth;
					continue;
				}

				int sourceX = (int) (sourceXNumerator / drawWidth);
				sourceXNumerator += sourceWidth;
				int sourcePixel = sourcePixels[sourceRow + sourceX];
				int sourceAlpha = (sourcePixel >>> 24) & 0xFF;
				if (sourceAlpha == 0)
				{
					continue;
				}

				if (rowHasOcclusionCoverage)
				{
					if (!occlusionRowHasDepthScratch[clippedRow])
					{
						occlusionRowDepthScratch[clippedRow] = billboardDepth.depthAtRow(sourceY, sourceHeight, y);
						occlusionRowHasDepthScratch[clippedRow] = true;
					}

					double pixelBillboardDepth = occlusionRowDepthScratch[clippedRow];
					boolean occluded;
					if (occlusionSampleStep > 1)
					{
						int sampleX = occlusionMask.sampleX(x);
						if (sampleX != cachedSampleX)
						{
							cachedSampleX = sampleX;
							cachedSampleOccluded = occlusionMask.isOccludedSample(sampleX, y, pixelBillboardDepth);
						}
						occluded = cachedSampleOccluded;
					}
					else
					{
						occluded = occlusionMask.isOccluded(x, y, pixelBillboardDepth);
					}
					if (occluded)
					{
						if (config.debugDrawBillboardOcclusionMask())
						{
							frameCompositePixels[destIndex] = BillboardTriangleRasterizer.blendPixel(frameCompositePixels[destIndex], DEBUG_OCCLUDED_PIXEL);
						}
						continue;
					}
				}

				frameCompositePixels[destIndex] = sourceAlpha == 0xFF
					? sourcePixel
					: BillboardTriangleRasterizer.blendPixel(frameCompositePixels[destIndex], sourcePixel);
				if (sourceAlpha == 0xFF)
				{
					paintOrderBuffer[destIndex] = (char) paintOrder;
				}
			}
			if (rowOcclusionStart > 0L)
			{
				occlusionElapsedNanos += System.nanoTime() - rowOcclusionStart;
			}
		}
		if (occlusionElapsedNanos > 0L)
		{
			performanceMetrics.addElapsed("Per-pixel occlusion checks", occlusionElapsedNanos);
		}
	}

	private void ensureOcclusionRowScratchCapacity(int height)
	{
		if (occlusionRowHasDepthScratch.length >= height)
		{
			return;
		}

		occlusionRowHasDepthScratch = new boolean[height];
		occlusionRowDepthScratch = new double[height];
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

		CollectedCandidates collected;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Collect candidates"))
		{
			collected = collectCandidates(worldView);
		}
		List<BillboardTarget> candidates = collected.candidates;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Sort/select targets"))
		{
			candidates.sort(Comparator
				.comparingInt(this::candidateSelectionPriority).reversed()
				.thenComparingDouble(BillboardTarget::getDepth));
			int limit = Math.max(1, config.billboardMaxEntities());
			if (candidates.size() > limit)
			{
				candidates = new ArrayList<>(candidates.subList(0, limit));
			}
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
		List<BillboardWorldOcclusionCollector.TraceTarget> occlusionTraceTargets;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Build occlusion trace targets"))
		{
			occlusionTraceTargets = occlusionQuality == BillboardOcclusionQuality.OFF
				? Collections.emptyList()
				: buildOcclusionTraceTargets(candidates);
		}
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Gather world occluders"))
		{
			worldOcclusionCollector.collectTerrainOccluders(worldView, occlusionTraceTargets, occlusionQuality);
		}
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Schedule frame updates"))
		{
			markSuppressedStackedActors(collected);
			scheduleFrameUpdates(sortTargetsForRender(new ArrayList<>(candidates)), gameCycle);
		}
		updateActiveSnapshots();
	}

	private List<BillboardWorldOcclusionCollector.TraceTarget> buildOcclusionTraceTargets(List<BillboardTarget> targets)
	{
		worldOcclusionCollector.clearInterest();
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
		List<BillboardWorldOcclusionCollector.TraceTarget> traceTargets = new ArrayList<>(targets.size());
		for (BillboardTarget target : targets)
		{
			BillboardWorldOcclusionCollector.TraceTarget traceTarget = addOcclusionTraceTarget(target, viewportBounds);
			if (traceTarget != null)
			{
				traceTargets.add(traceTarget);
			}
		}

		return worldOcclusionCollector.interestBounds() != null ? traceTargets : Collections.emptyList();
	}

	private BillboardWorldOcclusionCollector.TraceTarget addOcclusionTraceTarget(BillboardTarget target, Rectangle viewportBounds)
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
		worldOcclusionCollector.addInterest(buildSortPreviewBounds(request), viewportBounds);
		return request != null && request.localPoint != null
			? new BillboardWorldOcclusionCollector.TraceTarget(request.localPoint, request.plane)
			: null;
	}

	private BillboardWorldOcclusionCollector.TraceTarget addTileObjectOcclusionTraceTarget(BillboardTarget target, Rectangle viewportBounds)
	{
		if (target == null || target.observedTileObject == null)
		{
			return null;
		}

		BillboardWorldOcclusionCollector.TraceTarget traceTarget = null;
		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			BillboardRenderRequest request = buildRenderRequest(target, part);
			worldOcclusionCollector.addInterest(buildSortPreviewBounds(request), viewportBounds);
			if (traceTarget == null && request != null && request.localPoint != null)
			{
				traceTarget = new BillboardWorldOcclusionCollector.TraceTarget(request.localPoint, request.plane);
			}
		}

		return traceTarget;
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
		if (canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY()))
		{
			return true;
		}

		BillboardRenderRequest request = buildActorRenderRequest(actor);
		Rectangle modelBounds = projectedModelCanvasBounds(request);
		return (modelBounds != null && modelBounds.intersects(viewport)) || isSceneRenderedInFront(actor, request);
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
		if (canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY()))
		{
			return true;
		}

		BillboardRenderRequest request = buildProjectileRenderRequest(projectile);
		Rectangle modelBounds = projectedModelCanvasBounds(request);
		return (modelBounds != null && modelBounds.intersects(viewport)) || isSceneRenderedInFront(projectile, request);
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
		if (canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY()))
		{
			return true;
		}

		BillboardRenderRequest request = buildGraphicsObjectRenderRequest(graphicsObject);
		Rectangle modelBounds = projectedModelCanvasBounds(request);
		return (modelBounds != null && modelBounds.intersects(viewport)) || isSceneRenderedInFront(graphicsObject, request);
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
		if (canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY()))
		{
			return true;
		}

		BillboardRenderRequest request = buildActorSpotAnimRenderRequest(actorSpotAnim, actor);
		Rectangle modelBounds = projectedModelCanvasBounds(request);
		return (modelBounds != null && modelBounds.intersects(viewport)) || isSceneRenderedInFront(actorSpotAnim, request);
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

			BillboardRenderRequest request = new BillboardRenderRequest(
				part.renderable,
				part.renderable.getModel(),
				part.localPoint,
				part.plane,
				0,
				orientationCalculator.relativeYaw(),
				orientationCalculator.relativePitch(),
				-1,
				-1,
				-1,
				-1,
				-1,
				false,
				false,
				VerticalAnchor.BOTTOM,
				null
			);
			Rectangle modelBounds = projectedModelCanvasBounds(request);
			if ((modelBounds != null && modelBounds.intersects(viewport)) || isSceneRenderedInFront(part.renderable, request))
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

	private Rectangle projectedModelCanvasBounds(BillboardRenderRequest request)
	{
		if (request == null || request.model == null || request.localPoint == null)
		{
			return null;
		}

		Model model = request.model;
		int vertexCount = model.getVerticesCount();
		if (vertexCount <= 0)
		{
			return null;
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		if (verticesX == null || verticesY == null || verticesZ == null)
		{
			return null;
		}

		vertexCount = Math.min(vertexCount, Math.min(verticesX.length, Math.min(verticesY.length, verticesZ.length)));
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int i = 0; i < vertexCount; i++)
		{
			if (!Float.isFinite(verticesX[i]) || !Float.isFinite(verticesY[i]) || !Float.isFinite(verticesZ[i]))
			{
				continue;
			}

			int localX = (int) Math.round(request.localPoint.getX() + verticesX[i]);
			int localY = (int) Math.round(request.localPoint.getY() + verticesZ[i]);
			int height = tileHeightAt(localX, localY, request.plane) + request.verticalOffset + (int) Math.round(Math.max(0.0f, -verticesY[i]));
			Point point = Perspective.localToCanvas(client, localX, localY, height);
			if (point == null)
			{
				continue;
			}

			minX = Math.min(minX, point.getX());
			minY = Math.min(minY, point.getY());
			maxX = Math.max(maxX, point.getX());
			maxY = Math.max(maxY, point.getY());
		}

		if (minX == Integer.MAX_VALUE)
		{
			return null;
		}

		return new Rectangle(minX, minY, Math.max(1, (maxX - minX) + 1), Math.max(1, (maxY - minY) + 1));
	}

	private boolean isSceneRenderedInFront(Renderable renderable, BillboardRenderRequest request)
	{
		if (renderable == null || request == null || request.localPoint == null || !wasSceneRenderableDrawnThisFrame(renderable))
		{
			return false;
		}

		double centerDepth = depthCalculator.cameraForwardDepth(
			request.localPoint,
			request.plane,
			request.verticalOffset + Math.max(0, renderable.getModelHeight() / 2.0d)
		);
		if (centerDepth > MIN_FORWARD_VISIBLE_DEPTH)
		{
			return true;
		}

		return hasModelVertexInFront(request);
	}

	private boolean hasModelVertexInFront(BillboardRenderRequest request)
	{
		Model model = request.model;
		if (model == null || model.getVerticesCount() <= 0)
		{
			return false;
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		if (verticesX == null || verticesY == null || verticesZ == null)
		{
			return false;
		}

		int vertexCount = Math.min(model.getVerticesCount(), Math.min(verticesX.length, Math.min(verticesY.length, verticesZ.length)));
		for (int i = 0; i < vertexCount; i++)
		{
			if (!Float.isFinite(verticesX[i]) || !Float.isFinite(verticesY[i]) || !Float.isFinite(verticesZ[i]))
			{
				continue;
			}

			int localX = (int) Math.round(request.localPoint.getX() + verticesX[i]);
			int localY = (int) Math.round(request.localPoint.getY() + verticesZ[i]);
			int height = tileHeightAt(localX, localY, request.plane) + request.verticalOffset + (int) Math.round(Math.max(0.0f, -verticesY[i]));
			if (depthCalculator.cameraForwardDepth(localX, localY, height) > MIN_FORWARD_VISIBLE_DEPTH)
			{
				return true;
			}
		}

		return false;
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
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Rasterization"))
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
					try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Textured faces"))
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
					}
					continue;
				}

				try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Solid faces"))
				{
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
			}

			int[] exteriorOutlineIndices;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Outline/boundary effects"))
			{
				exteriorOutlineIndices = BillboardOutlineRenderer.captureExteriorBoundaryIndices(image, outlineScratch);

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
			}

			return new RenderedBillboardImage(image);
		}
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
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Transform vertices"))
			{
				for (int i = 0; i < vertexCount; i++)
				{
					double rotatedX = (verticesX[i] * yawCos) + (verticesZ[i] * yawSin);
					double rotatedZ = (verticesZ[i] * yawCos) - (verticesX[i] * yawSin);
					spriteX[i] = (float) rotatedX;
					spriteY[i] = (float) ((verticesY[i] * pitchCos) - (rotatedZ * pitchSin));
					spriteDepth[i] = (float) ((rotatedZ * pitchCos) + (verticesY[i] * pitchSin));
				}
			}

			BuiltFaces builtFaces;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Gather faces"))
			{
				builtFaces = buildFaces(model, spriteX, spriteY, spriteDepth, nowMillis, request.animatedTextureId, true);
				if (builtFaces.faces.isEmpty())
				{
					builtFaces = buildFaces(model, spriteX, spriteY, spriteDepth, nowMillis, request.animatedTextureId, false);
				}
			}
			if (!builtFaces.faces.isEmpty())
			{
				List<FaceDraw> faces = builtFaces.faces;
				Rectangle sourceBounds;
				try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Sort faces / compute bounds"))
				{
					faces.sort(Comparator.comparingDouble(FaceDraw::getDepth).reversed());
					sourceBounds = BillboardGeometryUtils.computeBounds(faces);
				}
				if (BillboardGeometryUtils.isUsableSourceBounds(sourceBounds))
				{
					int outlinePadding = outlinePadding();
					Rectangle imageBounds = BillboardGeometryUtils.expandedBounds(sourceBounds, outlinePadding);
					Rectangle previewDrawRect;
					try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Draw rect calculation"))
					{
						previewDrawRect = buildDrawRect(request, renderable, imageBounds);
					}
					if (previewDrawRect == null)
					{
						return cached == null ? null : drawCachedBillboard(request, renderable, cached, nowMillis, queuePosition);
					}

					BillboardCacheKey cacheKey;
					try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Cache key / cache check"))
					{
						cacheKey = buildCacheKey(
							request,
							outlinePadding,
							builtFaces.textureStateHash,
							qualityKey(updatePlan.qualityScale),
							textureResolver.animatedTextureOffsetStateHash(request.animatedTextureId, nowMillis)
						);
						cacheInvalidated = updatePlan.forceHoverInteractionRedraw
							|| shouldRefreshCache(renderable, cacheKey, imageBounds, nowMillis);
						if (!cacheInvalidated && cached != null)
						{
							cached.touch(nowMillis);
							updatePlanSucceeded = true;
						}
					}
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

		try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Draw rect calculation"))
		{
			return drawCachedBillboard(request, renderable, cached, spriteRedrawn ? -1L : nowMillis, queuePosition);
		}
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
		if (targetHeight <= 0)
		{
			targetHeight = fallbackDrawHeight(projectedModelCanvasBounds(request));
		}
		int targetWidth = BillboardGeometryUtils.aspectWidth(billboardBounds, targetHeight);
		Point anchorPoint = drawAnchorPoint(request, basePoint, centerPoint, topPoint);
		if (anchorPoint == null)
		{
			Rectangle modelBounds = projectedModelCanvasBounds(request);
			anchorPoint = modelBounds != null && !modelBounds.isEmpty()
				? new Point(modelBounds.x + (modelBounds.width / 2), modelBounds.y + (modelBounds.height / 2))
				: viewportCenterPoint();
			billboardBounds = new Rectangle(
				billboardBounds.x,
				billboardBounds.y + (billboardBounds.height / 2),
				billboardBounds.width,
				billboardBounds.height
			);
		}

		int anchorX = anchorPoint.getX();
		int anchorY = anchorPoint.getY();
		if (!BillboardGeometryUtils.isUsableCanvasCoordinate(anchorX) || !BillboardGeometryUtils.isUsableCanvasCoordinate(anchorY))
		{
			return null;
		}

		return buildDrawRect(billboardBounds, anchorX, anchorY, targetWidth, targetHeight);
	}

	private int fallbackDrawHeight(Rectangle projectedModelBounds)
	{
		if (projectedModelBounds != null && projectedModelBounds.height > 0)
		{
			return Math.min(FALLBACK_MAX_DRAW_SIZE, Math.max(1, projectedModelBounds.height));
		}

		int viewportHeight = Math.max(1, client.getViewportHeight());
		return Math.min(FALLBACK_MAX_DRAW_SIZE, Math.max(viewportHeight * 2, 1));
	}

	private Point viewportCenterPoint()
	{
		return new Point(
			client.getViewportXOffset() + (Math.max(1, client.getViewportWidth()) / 2),
			client.getViewportYOffset() + (Math.max(1, client.getViewportHeight()) / 2)
		);
	}

	private Point drawAnchorPoint(BillboardRenderRequest request, Point basePoint, Point centerPoint, Point topPoint)
	{
		if (request.verticalAnchor == VerticalAnchor.CENTER)
		{
			if (centerPoint != null)
			{
				return centerPoint;
			}

			if (basePoint != null && topPoint != null)
			{
				return new Point((basePoint.getX() + topPoint.getX()) / 2, (basePoint.getY() + topPoint.getY()) / 2);
			}

			if (basePoint != null)
			{
				return basePoint;
			}

			return topPoint;
		}

		if (basePoint != null)
		{
			return basePoint;
		}

		if (centerPoint != null && topPoint != null)
		{
			int x = (centerPoint.getX() * 2) - topPoint.getX();
			int y = (centerPoint.getY() * 2) - topPoint.getY();
			return new Point(x, y);
		}

		if (centerPoint != null)
		{
			return centerPoint;
		}

		return topPoint;
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

	private int tileHeightAt(int localX, int localY, int plane)
	{
		return Perspective.getTileHeight(client, new LocalPoint(localX, localY), plane);
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


}

