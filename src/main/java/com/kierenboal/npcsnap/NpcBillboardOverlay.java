package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.features.GroundItemBillboard;
import com.kierenboal.npcsnap.features.GroundItemBillboardTracker;
import com.kierenboal.npcsnap.features.ActorOverheadRenderer;
import com.kierenboal.npcsnap.export.BillboardExportAngles;
import com.kierenboal.npcsnap.export.BillboardExportAnimationFrames;
import com.kierenboal.npcsnap.export.BillboardExportBatch;
import com.kierenboal.npcsnap.export.BillboardExportFrame;
import com.kierenboal.npcsnap.export.BillboardExportPaths;
import com.kierenboal.npcsnap.export.BillboardObjectExportTarget;
import com.kierenboal.npcsnap.export.BillboardObjectExportResolver;
import com.kierenboal.npcsnap.occlusion.BillboardOcclusionDebugSampler;
import com.kierenboal.npcsnap.occlusion.BillboardOcclusionMask;
import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;
import com.kierenboal.npcsnap.occlusion.BillboardOcclusionRegions;
import com.kierenboal.npcsnap.occlusion.BillboardWorldOcclusionCollector;
import com.kierenboal.npcsnap.occlusion.BillboardSceneVisibility;
import com.kierenboal.npcsnap.occlusion.BillboardNpcSurfaceOcclusion;
import com.kierenboal.npcsnap.rendering.BillboardModelBounds;
import com.kierenboal.npcsnap.rendering.ActorWorldPlane;
import com.kierenboal.npcsnap.rendering.BillboardAngleUtils;
import com.kierenboal.npcsnap.rendering.BillboardCanvasPoint;
import com.kierenboal.npcsnap.rendering.BillboardColorUtils;
import com.kierenboal.npcsnap.rendering.BillboardBrightness;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardDepthSurface;
import com.kierenboal.npcsnap.rendering.BillboardDrawGeometry;
import com.kierenboal.npcsnap.rendering.BillboardFaceRasterizer;
import com.kierenboal.npcsnap.rendering.BillboardFrameBuffer;
import com.kierenboal.npcsnap.rendering.BillboardGeometryUtils;
import com.kierenboal.npcsnap.rendering.BillboardModelStateHash;
import com.kierenboal.npcsnap.rendering.BillboardOrientationCalculator;
import com.kierenboal.npcsnap.rendering.BillboardOutlineRenderer;
import com.kierenboal.npcsnap.rendering.BillboardPaintOrder;
import com.kierenboal.npcsnap.rendering.BillboardProjectileGeometry;
import com.kierenboal.npcsnap.rendering.BillboardRenderQuality;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequestFactory;
import com.kierenboal.npcsnap.rendering.BillboardRenderResult;
import com.kierenboal.npcsnap.rendering.BillboardTextureResolver;
import com.kierenboal.npcsnap.state.FrameRenderableTracker;
import com.kierenboal.npcsnap.rendering.BuiltFaces;
import com.kierenboal.npcsnap.rendering.FaceDraw;
import com.kierenboal.npcsnap.rendering.NpcSnapColorBanding;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;
import com.kierenboal.npcsnap.rendering.TextureSample;
import com.kierenboal.npcsnap.rendering.TextureUvs;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import com.kierenboal.npcsnap.state.BillboardCacheKey;
import com.kierenboal.npcsnap.state.BillboardCachePreviewKey;
import com.kierenboal.npcsnap.state.BillboardCacheStore;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import com.kierenboal.npcsnap.state.BillboardRedrawReason;
import com.kierenboal.npcsnap.state.BillboardUpdateQueue;
import com.kierenboal.npcsnap.state.BillboardUpdateScheduler;
import com.kierenboal.npcsnap.state.BillboardUpdateScore;
import com.kierenboal.npcsnap.state.BillboardUpdateState;
import com.kierenboal.npcsnap.state.BillboardVisibilityState;
import com.kierenboal.npcsnap.state.CachedBillboard;
import com.kierenboal.npcsnap.state.FrameUpdatePlan;
import com.kierenboal.npcsnap.state.QueuedBillboardTarget;
import com.kierenboal.npcsnap.state.UpdateHeuristicSnapshot;
import com.kierenboal.npcsnap.targeting.ActorStackTracker;
import com.kierenboal.npcsnap.targeting.BillboardClassificationDebug;
import com.kierenboal.npcsnap.targeting.ActorDrawOrderTracker;
import com.kierenboal.npcsnap.targeting.BillboardInteractionState;
import com.kierenboal.npcsnap.targeting.BillboardHoverInteractionResolver;
import com.kierenboal.npcsnap.targeting.BillboardTarget;
import com.kierenboal.npcsnap.targeting.BillboardTargetEligibility;
import com.kierenboal.npcsnap.targeting.BillboardTargetKey;
import com.kierenboal.npcsnap.targeting.BillboardTargetType;
import com.kierenboal.npcsnap.targeting.ClassifiedObjectType;
import com.kierenboal.npcsnap.targeting.CollectedCandidates;
import com.kierenboal.npcsnap.targeting.EffectDedupKey;
import com.kierenboal.npcsnap.targeting.ObjectClassifier;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;
import com.kierenboal.npcsnap.targeting.ObservedTileObject;
import com.kierenboal.npcsnap.targeting.ObservedTileObjectBuilder;
import com.kierenboal.npcsnap.targeting.OccupiedTileKey;
import com.kierenboal.npcsnap.targeting.WorldViewLocationResolver;
import com.kierenboal.npcsnap.targeting.BillboardWorldViewVisibility;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Hitsplat;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.MenuEntry;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.game.ItemManager;
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
	private static final double INVENTORY_GROUND_ITEM_MIN_ZOOM_SCALE = 0.75d;
	private static final double INVENTORY_GROUND_ITEM_MAX_ZOOM_SCALE = 1.5d;
	private static final double GROUND_ITEM_SPRITE_SHADOW_HEIGHT_RATIO = 0.33d;
	private final Client client;
	private final ItemManager itemManager;
	private final NpcSnapConfig config;
	private final NpcSnapDebug debug;
	private final BillboardDepthCalculator depthCalculator;
	private final BillboardWorldOcclusionCollector worldOcclusionCollector;
	private WorldView occlusionWorldView;
	private final BillboardOrientationCalculator orientationCalculator;
	private final BillboardCacheStore billboardCache = new BillboardCacheStore();
	private final BillboardTextureResolver textureResolver;
	private final BillboardPerformanceMetrics performanceMetrics = new BillboardPerformanceMetrics();
	private final GroundItemBillboardTracker groundItemTracker = new GroundItemBillboardTracker();
	private final Map<TileObject, ObservedTileObject> observedTileObjects = new IdentityHashMap<>();
	private final Map<TileObject, ObservedTileObject> visibleTileObjects = new IdentityHashMap<>();
	private final Object observedTileObjectsLock = new Object();
	private final Map<Renderable, BillboardTarget> activeRenderableTargets = new IdentityHashMap<>();
	private final BillboardVisibilityState visibility = new BillboardVisibilityState();
	private final FrameRenderableTracker<Renderable> sceneRenderables = new FrameRenderableTracker<>();
	private final BillboardUpdateQueue updateQueue = new BillboardUpdateQueue();
	private final BillboardClassificationDebug classificationDebug;
	private final Map<BillboardTargetKey, BillboardUpdateState> billboardUpdateStates = new HashMap<>();
	private final Map<BillboardTargetKey, FrameUpdatePlan> frameUpdatePlans = new HashMap<>();
	private final Map<BillboardTargetKey, UpdateHeuristicSnapshot> frameSnapshots = new HashMap<>();
	private final Set<BillboardTargetKey> compositeResizes = new HashSet<>();
	private final Set<Renderable> forceHoverInteractionRedraws = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Actor> forceActorResizeRedraws = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Actor, Boolean> actorResizeObservations = new IdentityHashMap<>();
	private final BillboardOutlineRenderer.Scratch outlineScratch = new BillboardOutlineRenderer.Scratch();
	private final BillboardOcclusionMask occlusionMask = new BillboardOcclusionMask(performanceMetrics);
	private float[] spriteXScratch = new float[0];
	private float[] spriteYScratch = new float[0];
	private float[] spriteDepthScratch = new float[0];
	private final BillboardFrameBuffer frameBuffer = new BillboardFrameBuffer(occlusionMask, performanceMetrics, this::billboardDepthSurface);
	private final BillboardOcclusionDebugSampler occlusionDebugSampler = new BillboardOcclusionDebugSampler(occlusionMask, this::billboardDepthSurface);
	private int activeBillboardsGameCycle = Integer.MIN_VALUE;
	private final BillboardInteractionState interactionState = new BillboardInteractionState();
	private final ActorStackTracker actorStackTracker = new ActorStackTracker();
	private final ActorDrawOrderTracker actorDrawOrderTracker = new ActorDrawOrderTracker();
	private int lastEffectiveLightBoostPercent = Integer.MIN_VALUE;
	private final BillboardRenderRequestFactory requestFactory;
	private final BillboardTargetEligibility targetEligibility;
	private final ActorOverheadRenderer actorOverheadRenderer;
	private Actor lastSkewDebugActor;
	private int lastSkewDebugCameraPitch = Integer.MIN_VALUE;
	private int lastSkewDebugCameraYaw = Integer.MIN_VALUE;
	private volatile boolean active;
	private final BillboardWorldViewVisibility worldViewVisibility;

	NpcBillboardOverlay(Client client, ItemManager itemManager, NpcSnapConfig config, NpcSnapDebug debug, BillboardSceneVisibility sceneVisibility)
	{
		this(client, itemManager, config, debug, sceneVisibility, null);
	}

	@Inject
	NpcBillboardOverlay(Client client, ItemManager itemManager, NpcSnapConfig config, NpcSnapDebug debug, BillboardSceneVisibility sceneVisibility, BillboardWorldViewVisibility worldViewVisibility)
	{
		this.client = client;
		this.worldViewVisibility = worldViewVisibility;
		this.itemManager = itemManager;
		this.config = config;
		this.debug = debug;
		this.depthCalculator = new BillboardDepthCalculator(client);
		this.worldOcclusionCollector = new BillboardWorldOcclusionCollector(client, config, depthCalculator, performanceMetrics, log, sceneVisibility);
		this.orientationCalculator = new BillboardOrientationCalculator(client, config);
		this.requestFactory = new BillboardRenderRequestFactory(
			client, config, debug, orientationCalculator, interactionState);
		this.targetEligibility = new BillboardTargetEligibility(
			client, config, requestFactory, this::projectedModelCanvasBounds, this::isSceneRenderedInFront);
		this.textureResolver = new BillboardTextureResolver(client);
		this.classificationDebug = new BillboardClassificationDebug(client, config, log);
		this.actorOverheadRenderer = new ActorOverheadRenderer(client, config);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!active)
		{
			return null;
		}

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

			// The scene has now established the camera and viewport for this draw.
			depthCalculator.beginRender();
			List<BillboardTarget> visibleTargets;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Visible target lookup"))
			{
				visibleTargets = getVisibleTargets(worldView);
			}
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Hover/interaction update"))
			{
				updateHoverInteractionState();
				frameUpdatePlans.clear();
				frameSnapshots.clear();
				compositeResizes.clear();
				scheduleFrameUpdates(sortTargetsForRender(new ArrayList<>(visibleTargets)), client.getGameCycle());
			}
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Target sorting"))
			{
				visibleTargets = sortTargetsForRender(visibleTargets);
			}
			List<PreparedBillboardDraw> preparedDraws = new ArrayList<>(visibleTargets.size());
			for (int i = 0; i < visibleTargets.size(); i++)
			{
				renderTarget(visibleTargets.get(i), i + 1, preparedDraws);
			}
			compositePreparedDraws(graphics, preparedDraws);
			actorOverheadRenderer.render(graphics, preparedDraws);
			publishDrawableActor2d(preparedDraws);

			return null;
		}
		finally
		{
			depthCalculator.endRender();
			performanceMetrics.finishFrame();
			drawPerformanceMetrics(graphics);
		}
	}

	void setActive(boolean active)
	{
		this.active = active;
		if (!active && worldViewVisibility != null)
		{
			worldViewVisibility.clear();
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

		debug.drawPerformanceMetrics(graphics, viewport, performanceMetrics.rows(Integer.MAX_VALUE), performanceMetrics.counters());
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
		clearInteractionIfMatches(item);
		billboardCache.remove(item);
		activeRenderableTargets.remove(item);
		visibility.activeBillboards.remove(item);
	}

	private void clearActiveState()
	{
		worldOcclusionCollector.clear();
		activeRenderableTargets.clear();
		lastSkewDebugActor = null;
		lastSkewDebugCameraPitch = Integer.MIN_VALUE;
		lastSkewDebugCameraYaw = Integer.MIN_VALUE;
		actorStackTracker.clear();
		actorDrawOrderTracker.clear();
		worldOcclusionCollector.clearInterest();
		clearInteractionState();
		clearActiveSelections();
		clearRenderQueue();
		clearActiveSnapshots();
	}

	private void clearActiveSelections()
	{
		visibility.clearSelections();
	}

	private void clearActiveSnapshots()
	{
		visibility.clearSnapshots();
	}

	private void clearRenderQueue()
	{
		updateQueue.clear();
		classificationDebug.clear();
		billboardUpdateStates.clear();
		frameUpdatePlans.clear();
		forceHoverInteractionRedraws.clear();
		forceActorResizeRedraws.clear();
		actorResizeObservations.clear();
		frameSnapshots.clear();
		compositeResizes.clear();
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
		visibility.clearActiveTileObjects();
	}

	void clearTextureCache()
	{
		textureResolver.clear();
	}

	void clearBillboardCache()
	{
		billboardCache.clear();
		forceActorResizeRedraws.clear();
		actorResizeObservations.clear();
		frameSnapshots.clear();
		compositeResizes.clear();
	}

	boolean isActorWorldViewVisible(Actor actor)
	{
		return worldViewVisibility == null || worldViewVisibility.isVisible(actor);
	}

	boolean shouldForceActorResizeRedraw(Actor actor)
	{
		if (!isActorWorldViewVisible(actor) || !config.enable2dBillboardSprites() || !config.enableAnimationFrameSnapping()
			|| config.redrawOnClickboxResizePercent() <= 0)
		{
			return false;
		}
		CachedBillboard cached = billboardCache.get(actor);
		if (cached == null || cached.modelBounds == null)
		{
			return false;
		}
		Boolean observedResize = actorResizeObservations.get(actor);
		if (observedResize != null)
		{
			return observedResize;
		}
		BillboardTarget target = activeRenderableTargets.get(actor);
		UpdateHeuristicSnapshot snapshot = target != null ? frameSnapshots.get(target.targetKey) : null;
		BillboardModelBounds current = snapshot != null ? snapshot.geometry : BillboardModelBounds.sample(actor.getModel(), false);
		boolean resized = current != null && current.exceeds(cached.modelBounds, config.redrawOnClickboxResizePercent());
		actorResizeObservations.put(actor, resized);
		if (resized)
		{
			forceActorResizeRedraws.add(actor);
			return true;
		}
		return false;
	}

	void noteSceneRenderable(Renderable renderable)
	{
		sceneRenderables.record(renderable);
	}

	void noteSceneActorDraw(Actor actor)
	{
		actorDrawOrderTracker.record(actor);
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
					billboardCache.remove(part.renderable);
				}
			}
		}
	}

	void beginFrame()
	{
		if (worldViewVisibility != null)
		{
			worldViewVisibility.beginFrame();
		}
		forceActorResizeRedraws.clear();
		actorResizeObservations.clear();
		frameSnapshots.clear();
		compositeResizes.clear();
		performanceMetrics.beginFrame(config.debugPerformanceMetrics());
		depthCalculator.beginFrame();
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Begin frame"))
		{
			int effectiveLightBoost = BillboardBrightness.effectiveLightBoostPercent(client,
				config.billboardLightBoostPercent());
			if (lastEffectiveLightBoostPercent != effectiveLightBoost)
			{
				clearBillboardCache();
				lastEffectiveLightBoostPercent = effectiveLightBoost;
			}
			expireCaches(System.currentTimeMillis());
			worldOcclusionCollector.resetFrame();
			occlusionWorldView = null;
			occlusionDebugSampler.clear();
			sceneRenderables.advanceFrame();
			actorDrawOrderTracker.advanceFrame();
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
		interactionState.noteClick(actor, tickCount);
	}

	void noteGroundItemInteraction(MenuEntry entry)
	{
		interactionState.noteGroundItemClick(
			BillboardHoverInteractionResolver.groundItem(entry, groundItemTracker.entries()));
	}

	void clearStaleInteraction(Player localPlayer, int tickCount)
	{
		interactionState.clearIfStale(localPlayer, tickCount);
	}

	void onLocalPlayerInteractionChanged(Actor target, int tickCount)
	{
		interactionState.onLocalPlayerInteractionChanged(target, tickCount);
	}

	void clearInteractionIfMatches(Actor actor)
	{
		interactionState.clearIfMatches(actor);
		actorOverheadRenderer.clear(actor);
	}

	void recordHitsplat(Actor actor, Hitsplat hitsplat)
	{
		actorOverheadRenderer.recordHitsplat(actor, hitsplat);
	}

	void clearInteractionIfMatches(TileItem item)
	{
		interactionState.clearIfMatches(item);
	}

	void clearInteractionState()
	{
		interactionState.clear();
		actorOverheadRenderer.clear();
	}

	BillboardExportBatch captureExport(MenuEntry sourceEntry)
	{
		BillboardTarget target = resolveExportTarget(sourceEntry);
		return captureExport(target);
	}

	BillboardExportBatch captureExport(Player player)
	{
		return captureExport(player == null ? null : activeRenderableTargets.get(player));
	}

	BillboardExportBatch captureExport(BillboardObjectExportTarget context)
	{
		TileObject object = BillboardObjectExportResolver.resolve(client, context);
		if (object == null)
		{
			log.debug("Object export target unresolved: id={}, tile=({}, {}), worldView={}, plane={}",
				context.identifier, context.sceneX, context.sceneY, context.worldViewId, context.plane);
			return null;
		}
		ObservedTileObject observed = ObservedTileObjectBuilder.build(object);
		if (observed == null)
		{
			log.debug("Object export target has no renderable parts: id={}", object.getId());
			return null;
		}
		return captureExport(BillboardTarget.forTileObject(observed, depthCalculator.depth(observed)));
	}

	private BillboardExportBatch captureExport(BillboardTarget target)
	{
		if (target == null)
		{
			return null;
		}

		String name = exportTargetName(target);
		if (target.renderable instanceof Actor)
		{
			return captureActorExport(target, (Actor) target.renderable, name);
		}
		return captureExportAtFrame(target, name, 0);
	}

	private BillboardExportBatch captureActorExport(BillboardTarget target, Actor actor, String name)
	{
		int actionAnimation = actor.getAnimation();
		boolean poseAnimation = actionAnimation < 0;
		int animationId = poseAnimation ? actor.getPoseAnimation() : actionAnimation;
		net.runelite.api.Animation animation = animationId >= 0 ? client.loadAnimation(animationId) : null;
		int totalFrames = animation != null
			? Math.max(animation.getNumFrames(), animation.getDuration())
			: 1;
		int originalActionFrame = actor.getAnimationFrame();
		int originalPoseFrame = actor.getPoseAnimationFrame();
		List<BillboardExportFrame> frames = new ArrayList<>();
		try
		{
			for (int frame : BillboardExportAnimationFrames.sampledFrames(
				totalFrames, config.enableAnimationFrameSnapping()
					? config.animationFrameCount()
					: totalFrames))
			{
				if (poseAnimation)
				{
					actor.setPoseAnimationFrame(frame);
				}
				else
				{
					actor.setAnimationFrame(frame);
				}
				BillboardExportBatch frameBatch = captureExportAtFrame(target, name, frame);
				if (frameBatch != null)
				{
					frames.addAll(frameBatch.frames);
				}
			}
		}
		finally
		{
			actor.setAnimationFrame(originalActionFrame);
			actor.setPoseAnimationFrame(originalPoseFrame);
		}
		return frames.isEmpty() ? null : new BillboardExportBatch(name, frames);
	}

	private BillboardExportBatch captureExportAtFrame(BillboardTarget target, String name, int animationFrame)
	{
		List<BillboardRenderRequest> requests = new ArrayList<>();
		if (isCompositeObjectTarget(target))
		{
			for (ObjectRenderablePart part : target.observedTileObject.parts)
			{
				BillboardRenderRequest request = requestFactory.build(target, part);
				if (request != null)
				{
					requests.add(request);
				}
			}
		}
		else
		{
			BillboardRenderRequest request = requestFactory.build(target);
			if (request != null)
			{
				requests.add(request);
			}
		}
		if (requests.isEmpty())
		{
			log.debug("Sprite export has no available model requests: target={}", name);
			return null;
		}

		boolean groundItem = target.type == BillboardTargetType.GROUND_ITEM;
		List<BillboardExportFrame> frames = new ArrayList<>();
		for (int pitch : BillboardExportAngles.pitches(config.numberOfPitchRotationAngles(), groundItem))
		{
			for (int yaw : BillboardExportAngles.yaws(config.numberOfYawRotationAngles()))
			{
				List<ExportRenderedPart> partImages = new ArrayList<>(requests.size());
				LocalPoint origin = requests.get(0).localPoint;
				for (BillboardRenderRequest request : requests)
				{
					ExportRenderedPart image = renderExportRequest(request, yaw, pitch, origin);
					if (image != null)
					{
						partImages.add(image);
					}
				}
				BufferedImage image = compositeExportParts(partImages);
				if (image != null)
				{
					frames.add(new BillboardExportFrame(animationFrame, pitch, yaw, image));
				}
			}
		}
		if (frames.isEmpty())
		{
			log.debug("Sprite export rasterization produced no images: target={}", name);
			return null;
		}
		return new BillboardExportBatch(name, frames);
	}

	private BillboardTarget resolveExportTarget(MenuEntry entry)
	{
		if (entry == null)
		{
			return null;
		}
		Actor actor = entry.getActor();
		if (actor != null)
		{
			return activeRenderableTargets.get(actor);
		}
		if (BillboardHoverInteractionResolver.isGroundItemAction(entry.getType()))
		{
			TileItem item = BillboardHoverInteractionResolver.groundItem(entry, groundItemTracker.entries());
			return item == null ? null : activeRenderableTargets.get(item);
		}

		TileObject object = BillboardObjectExportResolver.resolve(client, BillboardObjectExportTarget.snapshot(client, entry));
		ObservedTileObject observed = ObservedTileObjectBuilder.build(object);
		return observed == null ? null : BillboardTarget.forTileObject(observed, depthCalculator.depth(observed));
	}

	private String exportTargetName(BillboardTarget target)
	{
		String name = null;
		String fallback = "Sprite";
		if (target.renderable instanceof NPC)
		{
			NPC npc = (NPC) target.renderable;
			name = npc.getName();
			fallback = "NPC_" + npc.getId();
		}
		else if (target.renderable instanceof Player)
		{
			name = ((Player) target.renderable).getName();
			fallback = "Player";
		}
		else if (target.type == BillboardTargetType.GROUND_ITEM)
		{
			TileItem item = (TileItem) target.renderable;
			name = itemManager.getItemComposition(item.getId()).getName();
			fallback = "Item_" + item.getId();
		}
		else if (target.tileObject != null)
		{
			name = client.getObjectDefinition(target.tileObject.getId()).getName();
			fallback = "Object_" + target.tileObject.getId();
		}
		return BillboardExportPaths.sanitizeName(name, fallback);
	}

	private ExportRenderedPart renderExportRequest(BillboardRenderRequest original, int yaw, int pitch, LocalPoint origin)
	{
		Model model = original.model;
		if (model == null || model.getVerticesCount() <= 0)
		{
			return null;
		}
		int vertexCount = model.getVerticesCount();
		float[] spriteX = new float[vertexCount];
		float[] spriteY = new float[vertexCount];
		float[] spriteDepth = new float[vertexCount];
		int offsetX = origin != null && original.localPoint != null ? original.localPoint.getX() - origin.getX() : 0;
		int offsetZ = origin != null && original.localPoint != null ? original.localPoint.getY() - origin.getY() : 0;
		BillboardGeometryUtils.transformVertices(model, yaw, pitch, offsetX, offsetZ,
			spriteX, spriteY, spriteDepth);
		BuiltFaces builtFaces = buildFaces(model, spriteX, spriteY, spriteDepth,
			System.currentTimeMillis(), original.animatedTextureId, true);
		if (builtFaces.faces.isEmpty())
		{
			builtFaces = buildFaces(model, spriteX, spriteY, spriteDepth,
				System.currentTimeMillis(), original.animatedTextureId, false);
		}
		if (builtFaces.faces.isEmpty())
		{
			return null;
		}
		builtFaces.faces.sort(Comparator.comparingDouble(FaceDraw::getDepth).reversed());
		Rectangle bounds = BillboardGeometryUtils.computeBounds(builtFaces.faces);
		if (!BillboardGeometryUtils.isUsableSourceBounds(bounds))
		{
			return null;
		}
		double qualityScale = renderQualityScale();
		int outlinePadding = outlinePadding(qualityScale);
		BufferedImage rendered = renderBillboardImage(
			builtFaces.faces, bounds, outlinePadding, qualityScale,
			false, false, null, null);
		return rendered == null ? null
			: new ExportRenderedPart(rendered, BillboardGeometryUtils.expandedBounds(bounds, outlinePadding));
	}

	private static BufferedImage compositeExportParts(List<ExportRenderedPart> images)
	{
		if (images.isEmpty())
		{
			return null;
		}
		if (images.size() == 1)
		{
			return images.get(0).image;
		}
		Rectangle union = null;
		for (ExportRenderedPart image : images)
		{
			union = union == null ? new Rectangle(image.bounds) : union.union(image.bounds);
		}
		BufferedImage composite = new BufferedImage(Math.max(1, union.width), Math.max(1, union.height), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = composite.createGraphics();
		try
		{
			for (ExportRenderedPart image : images)
			{
				graphics.drawImage(image.image, image.bounds.x - union.x, image.bounds.y - union.y, null);
			}
		}
		finally
		{
			graphics.dispose();
		}
		return composite;
	}

	private static final class ExportRenderedPart
	{
		private final BufferedImage image;
		private final Rectangle bounds;

		private ExportRenderedPart(BufferedImage image, Rectangle bounds)
		{
			this.image = image;
			this.bounds = bounds;
		}
	}

	private boolean wasSceneRenderableDrawnLastFrame(Renderable renderable)
	{
		return sceneRenderables.containsPrevious(renderable);
	}

	private boolean wasSceneRenderableDrawnThisFrame(Renderable renderable)
	{
		return sceneRenderables.containsCurrent(renderable);
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

	void observeOcclusionTile(net.runelite.api.Tile tile, TileObject object)
	{
		if (object != null) worldOcclusionCollector.observeNestedTile(tile, object.getWorldView());
	}

	void unloadOcclusionWorldView(WorldView view)
	{
		worldOcclusionCollector.unload(view);
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

		return visibility.shouldHideRenderable(renderable, client.isClientThread());
	}

	boolean shouldHideActor2d(Renderable renderable)
	{
		if (!(renderable instanceof Actor)
			|| !visibility.hasActiveBillboard(renderable, client.isClientThread())
			|| !visibility.hadDrawableActor2dBillboard(renderable))
		{
			return false;
		}

		return actorOverheadRenderer.shouldReplace((Actor) renderable);
	}

	private void publishDrawableActor2d(List<PreparedBillboardDraw> preparedDraws)
	{
		Set<Renderable> drawable = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<Actor> drawableActors = Collections.newSetFromMap(new IdentityHashMap<>());
		for (PreparedBillboardDraw draw : preparedDraws)
		{
			if (draw != null && draw.request != null && draw.request.renderable instanceof Actor
				&& draw.image != null && draw.bounds != null && !draw.bounds.isEmpty()
				&& actorOverheadRenderer.shouldReplace((Actor) draw.request.renderable))
			{
				drawable.add(draw.request.renderable);
				drawableActors.add((Actor) draw.request.renderable);
			}
		}
		actorOverheadRenderer.updateDrawableActors(drawableActors);
		visibility.publishDrawableActor2d(drawable);
	}

	boolean shouldHideTileObject(TileObject tileObject)
	{
		if (tileObject == null)
		{
			return false;
		}

		boolean clientThread = client.isClientThread();
		return visibility.hasActiveTileObject(tileObject, clientThread)
			&& (!clientThread || tileObjectHasCachedBillboard(tileObject));
	}

	private void renderTarget(BillboardTarget target, int paintOrder, List<PreparedBillboardDraw> preparedDraws)
	{
		Actor owner = targetOwnerActor(target);
		if (owner != null && !isActorWorldViewVisible(owner))
		{
			return;
		}
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Per-target billboard render"))
		{
			if (target.type == BillboardTargetType.GROUND_ITEM && config.useInventorySpritesForGroundItems())
			{
				renderGroundItemInventorySpriteTarget(target, paintOrder, preparedDraws);
				return;
			}

			if (target.type == BillboardTargetType.TILE_OBJECT)
			{
				renderTileObjectTarget(target, paintOrder, preparedDraws);
				return;
			}

			BillboardRenderRequest request;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Build render request"))
			{
				request = requestFactory.build(target);
			}
			if (request == null)
			{
				return;
			}

			int queuePosition = updateQueue.debugPosition(target.targetKey);
			BillboardRenderResult result = renderRenderableBillboard(request, frameUpdatePlans.get(target.targetKey), queuePosition);
			if (result == null)
			{
				return;
			}

			isReadyToRedrawDebug(target);
			preparedDraws.add(new PreparedBillboardDraw(request, result, paintOrder, null,
				BillboardNpcSurfaceOcclusion.firstOccludedRow(client, depthCalculator, request)));
		}
	}

	private void renderGroundItemInventorySpriteTarget(BillboardTarget target, int paintOrder, List<PreparedBillboardDraw> preparedDraws)
	{
		BillboardRenderRequest request = requestFactory.build(target);
		if (request == null || !(request.renderable instanceof TileItem))
		{
			return;
		}

		TileItem item = (TileItem) request.renderable;
		BufferedImage inventorySprite = itemManager.getImage(item.getId(), item.getQuantity(), false);
		if (inventorySprite == null || inventorySprite.getWidth() <= 0 || inventorySprite.getHeight() <= 0)
		{
			return;
		}
		Rectangle bounds = inventorySpriteBounds(estimateBillboardImageBounds(request), inventorySprite);
		if (bounds == null)
		{
			return;
		}

		long nowMillis = System.currentTimeMillis();
		BillboardCacheKey cacheKey = buildInventorySpriteCacheKey(request, qualityKey());
		CachedBillboard cached = billboardCache.get(item);
		FrameUpdatePlan inventoryPlan = frameUpdatePlans.get(target.targetKey);
		if (inventoryPlan != null && !inventoryPlan.isCompleted()
			&& (cached == null || inventoryPlan.isForcedRedraw() || !cached.key.equals(cacheKey)))
		{
			Set<BillboardRedrawReason> reasons = BillboardRedrawReason.cacheChanges(cached, cacheKey, bounds, nowMillis);
			cached = new CachedBillboard(cacheKey, bounds, prepareGroundItemSprite(inventorySprite, request), nowMillis);
			cached.markDebugFrameRedrawn(reasons);
			billboardCache.put(item, cached);
		}

		if (cached == null) return;
		if (inventoryPlan != null) inventoryPlan.partSucceeded(item);
		int queuePosition = updateQueue.debugPosition(target.targetKey);
		BillboardRenderResult result = drawCachedBillboard(request, item, cached, nowMillis, queuePosition);
		if (result != null)
		{
			preparedDraws.add(new PreparedBillboardDraw(request, result, paintOrder));
		}
	}

	private Rectangle inventorySpriteBounds(Rectangle modelBounds, BufferedImage image)
	{
		if (modelBounds == null || image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
		{
			return null;
		}

		int height = Math.max(1, modelBounds.height);
		int width = Math.max(1, (int) Math.round(height * (image.getWidth() / (double) image.getHeight())));
		int centeredX = modelBounds.x + ((modelBounds.width - width) / 2);
		return new Rectangle(centeredX, modelBounds.y, width, height);
	}

	private static BufferedImage bandGroundItemSprite(BufferedImage image, int colorBands)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
		BufferedImage banded = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		banded.setRGB(0, 0, width, height, NpcSnapColorBanding.bandPixels(pixels, colorBands), 0, width);
		return banded;
	}

	private static boolean isCompositeObjectTarget(BillboardTarget target)
	{
		return target != null && target.type == BillboardTargetType.TILE_OBJECT;
	}

	private BufferedImage prepareGroundItemSprite(BufferedImage image, BillboardRenderRequest request)
	{
		BufferedImage prepared = bandGroundItemSprite(image, config.billboardColorBands());
		boolean spriteShadow = config.enableBillboardSpriteShadows();
		if (!spriteShadow && !request.shouldHoverOutline && !request.shouldInteractOutline)
		{
			return prepared;
		}

		int[] exteriorOutlineIndices = BillboardOutlineRenderer.captureExteriorBoundaryIndices(prepared, outlineScratch);
		if (spriteShadow)
		{
			BillboardOutlineRenderer.applyOutline(
				prepared,
				outlineScratch,
				false,
				false,
				false,
				true,
				false,
				false,
				false,
				config.billboardSpriteOutlineColor(),
				GROUND_ITEM_SPRITE_SHADOW_HEIGHT_RATIO
			);
		}

		applyHoverInteractionOutline(
			prepared,
			exteriorOutlineIndices,
			request.shouldHoverOutline,
			request.shouldInteractOutline,
			request.hoverOutlineColor,
			request.interactionOutlineColor);
		return prepared;
	}

	private void renderTileObjectTarget(BillboardTarget target, int paintOrder, List<PreparedBillboardDraw> preparedDraws)
	{
		if (target.observedTileObject == null)
		{
			return;
		}
		if (worldViewVisibility != null && target.tileObject.getWorldView() != null
			&& !worldViewVisibility.isVisible(target.tileObject.getWorldView()))
		{
			return;
		}

		FrameUpdatePlan tilePlan = frameUpdatePlans.get(target.targetKey);
		if (tilePlan != null) tilePlan.expectParts((int) target.observedTileObject.parts.stream()
			.filter(part -> part != null && part.renderable != null).map(part -> part.renderable).distinct().count());
		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			BillboardRenderRequest request;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Build render request"))
			{
				request = requestFactory.build(target, part);
			}
			if (request == null)
			{
				continue;
			}

			int queuePosition = updateQueue.debugPosition(target.targetKey);
			BillboardRenderResult result = renderRenderableBillboard(request, frameUpdatePlans.get(target.targetKey), queuePosition);
			if (result == null)
			{
				continue;
			}

			isReadyToRedrawDebug(target);
			preparedDraws.add(new PreparedBillboardDraw(request, result, paintOrder, target.tileObject));
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
		NpcSnapDebug.RenderDebug renderDebug = NpcSnapDebug.RenderDebug.forGeometry(
			result.geometry,
			paintOrder,
			cached != null && cached.consumeDebugFrameRedrawn(),
			cached != null && cached.consumeDebugFrameInvalidated(),
			cached != null ? cached.consumeDebugFrameInvalidatedColor() : null,
			cached != null ? cached.stateDebugInfo() : null,
			cached != null ? cached.consumeDebugRedrawReasons() : Collections.emptySet()
		);
		debug.drawBillboardDebugForeground(graphics, renderDebug);
	}

	private void markDebugFrameInvalidated(BillboardTarget target, Color color)
	{
		if (target == null)
		{
			return;
		}

		if (isCompositeObjectTarget(target) && target.observedTileObject != null)
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

		Double score = updateQueue.debugScore(target.targetKey);
		if (score == null || !updateQueue.hasDebugScores())
		{
			return null;
		}

		double minScore = Double.POSITIVE_INFINITY;
		double maxScore = Double.NEGATIVE_INFINITY;
		for (double candidateScore : updateQueue.debugScores())
		{
			minScore = Math.min(minScore, candidateScore);
			maxScore = Math.max(maxScore, candidateScore);
		}

		return NpcSnapDebug.readyToRedrawColor(score, minScore, maxScore);
	}

	private NpcSnapDebug.StateDebugInfo buildStateDebugInfo(BillboardRenderRequest request, int queuePosition, CachedBillboard cached)
	{
		if (request == null)
		{
			return null;
		}

		int currentFrame = request.animationFrame;
		int displayedFrame = cached.key.animationFrame;
		int animationId = cached.key.animationId;
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
			preparedDraws.removeIf(draw -> draw.request.sceneLocation != null
				&& worldViewVisibility != null && !worldViewVisibility.isVisible(draw.request.sceneLocation.source));
			if (performanceMetrics.isEnabled()) performanceMetrics.count("Actor draws", preparedDraws.stream().filter(draw -> draw.request.renderable instanceof Actor).count());
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
				frameBuffer.begin(viewportWidth, viewportHeight);
			}
			List<Rectangle> occlusionRegions;
			Rectangle occlusionBounds;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Build occlusion regions"))
			{
				occlusionRegions = occlusionInterestRegions(preparedDraws, viewportX, viewportY, viewportWidth, viewportHeight);
				occlusionBounds = BillboardOcclusionRegions.union(occlusionRegions);
			}
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Prepare occlusion mask"))
			{
				// ABOVE_SCENE runs after preSceneDraw has supplied this frame's actual
				// roof groups and floor range. Preparing targets in BeforeRender is too
				// early to decide which scenery the renderer will hide.
				try (BillboardPerformanceMetrics.Timer gathering = performanceMetrics.time("Gather world occluders"))
				{
					// Inventory sprites can extend beyond the model preview. Gather for
					// the actual draws so their outer pixels receive scenery occlusion too.
					worldOcclusionCollector.clearInterest();
					Rectangle viewportBounds = new Rectangle(viewportX, viewportY, viewportWidth, viewportHeight);
					Set<TileObject> billboardedObjects = Collections.newSetFromMap(new IdentityHashMap<>());
					for (PreparedBillboardDraw draw : preparedDraws)
					{
						worldOcclusionCollector.addInterest(draw.bounds, viewportBounds);
						if (draw.occlusionIgnoredTileObject != null)
						{
							billboardedObjects.add(draw.occlusionIgnoredTileObject);
						}
					}
					List<BillboardWorldOcclusionCollector.TraceTarget> liveTraces = new ArrayList<>();
					for (PreparedBillboardDraw draw : preparedDraws)
					{
						liveTraces.add(new BillboardWorldOcclusionCollector.TraceTarget(draw.request.localPoint, draw.request.plane));
					}
					worldOcclusionCollector.collectTerrainOccluders(occlusionWorldView, liveTraces,
						BillboardOcclusionQuality.normalize(config.billboardOcclusionQuality()), sceneRenderables.previousSnapshot(),
						billboardedObjects);
					worldOcclusionCollector.collectNestedOccluders(occlusionWorldView, worldViewVisibility,
						BillboardOcclusionQuality.normalize(config.billboardOcclusionQuality()), sceneRenderables.previousSnapshot(), billboardedObjects);
					worldOcclusionCollector.publishCounts();
				}
				try (BillboardPerformanceMetrics.Timer maskTimer = performanceMetrics.time("Build occlusion mask"))
				{
					occlusionMask.prepare(worldOcclusionCollector.occluders(), config.billboardOcclusionQuality(), config.billboardOcclusionComposition(), viewportX, viewportY, viewportWidth, viewportHeight, occlusionBounds, occlusionRegions);
				}
			}
			occlusionDebugSampler.collect(preparedDraws, config.debugLogBillboardOcclusion());
			worldOcclusionCollector.logDebugStats(occlusionMask, occlusionBounds, occlusionDebugSampler.samples());
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Per-pixel billboard compositing"))
			{
				for (int i = preparedDraws.size() - 1; i >= 0; i--)
				{
					frameBuffer.blit(
						preparedDraws.get(i),
						viewportX,
						viewportY,
						viewportWidth,
						viewportHeight,
						config.debugDrawBillboardOcclusionMask());
				}
			}

			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Draw composite image"))
			{
				graphics.drawImage(frameBuffer.image(), viewportX, viewportY, null);
			}
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Debug overlay drawing"))
			{
				for (PreparedBillboardDraw draw : preparedDraws)
				{
					drawRenderDebug(graphics, draw.result, draw.request, draw.paintOrder);
				}
			}
		}
	}

	private List<Rectangle> occlusionInterestRegions(List<PreparedBillboardDraw> preparedDraws, int viewportX, int viewportY, int viewportWidth, int viewportHeight)
	{
		return BillboardOcclusionRegions.clippedDrawRegions(
			preparedDraws,
			new Rectangle(viewportX, viewportY, viewportWidth, viewportHeight),
			occlusionInterestMargin());
	}

	private int occlusionInterestMargin()
	{
		BillboardOcclusionQuality quality = BillboardOcclusionQuality.normalize(config.billboardOcclusionQuality());
		return Math.max(0, quality.sampleStep());
	}

	private BillboardDepthSurface billboardDepthSurface(PreparedBillboardDraw draw)
	{
		if (draw == null || draw.request == null || draw.request.localPoint == null || draw.request.renderable == null)
		{
			return BillboardDepthSurface.invalid();
		}

		BillboardRenderRequest request = draw.request;
		double baseHeight = tileHeightAt(request.localPoint.getX(), request.localPoint.getY(), request.plane);
		return BillboardDepthSurface.from(depthCalculator, draw, baseHeight);
	}

	private List<BillboardTarget> getVisibleTargets(WorldView worldView)
	{
		ensureActiveBillboardsCurrent(worldView);

		List<BillboardTarget> visibleTargets = new ArrayList<>(activeRenderableTargets.size() + visibility.activeTileObjects.size());
		for (BillboardTarget target : activeRenderableTargets.values())
		{
			Actor owner = targetOwnerActor(target);
			if (owner == null || isActorWorldViewVisible(owner))
			{
				visibleTargets.add(target);
			}
		}

		for (TileObject tileObject : visibility.activeTileObjects)
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
				sortBottomY(target, previewBounds),
				target.getDepth(),
				target.type == BillboardTargetType.PROJECTILE,
				target.targetKey.stableSortOrder()
			));
		}

		return BillboardPaintOrder.sort(entries);
	}

	int sortBottomY(BillboardTarget target, Rectangle previewBounds)
	{
		if (isCompositeObjectTarget(target) && target.observedTileObject != null)
		{
			// Sprite bottoms include projecting roofs, padding and animated vertices.
			// Use the object's world anchor so redraws cannot swap overlapping sprites.
			int anchorY = Integer.MIN_VALUE;
			for (ObjectRenderablePart part : target.observedTileObject.parts)
			{
				BillboardCanvasPoint anchor = depthCalculator.projectCanvasPoint(part.localPoint, part.plane, 0);
				if (anchor != null && Double.isFinite(anchor.y))
				{
					anchorY = Math.max(anchorY, (int) Math.round(anchor.y));
				}
			}
			if (anchorY != Integer.MIN_VALUE)
			{
				return anchorY;
			}
		}
		return previewBounds != null ? previewBounds.y + previewBounds.height : Integer.MAX_VALUE;
	}

	private Rectangle buildSortPreviewBounds(BillboardTarget target)
	{
		if (target == null)
		{
			return null;
		}
		if (isCompositeObjectTarget(target))
		{
			Rectangle combined = null;
			if (target.observedTileObject == null)
			{
				return null;
			}

			for (ObjectRenderablePart part : target.observedTileObject.parts)
			{
				BillboardRenderRequest request = requestFactory.build(target, part);
				Rectangle preview = buildSortPreviewBounds(request);
				if (preview == null)
				{
					continue;
				}

				combined = combined == null ? new Rectangle(preview) : combined.union(preview);
			}

			return combined;
		}

		return buildSortPreviewBounds(requestFactory.build(target));
	}

	private Rectangle buildSortPreviewBounds(BillboardRenderRequest request)
	{
		if (request == null || request.renderable == null)
		{
			return null;
		}

		CachedBillboard cached = billboardCache.get(request.renderable);
		Rectangle bounds = cached != null ? cached.bounds : estimateBillboardImageBounds(request);
		Rectangle contentBounds = cached != null ? cached.contentBounds : bounds;
		BillboardDrawGeometry geometry = bounds != null
			? buildDrawGeometry(request, request.renderable, bounds, contentBounds)
			: null;
		return geometry != null ? geometry.bounds : null;
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
				if (billboardCache.contains(target.renderable))
				{
					visibility.activeBillboards.add(target.renderable);
				}
			}
			else if (target.tileObject != null)
			{
				visibility.activeTileObjects.add(target.tileObject);
				addActiveTileObjectBillboardParts(target);
			}
		}

		occlusionWorldView = worldView;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Schedule frame updates"))
		{
			markSuppressedStackedActors(collected);

		}
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
			if (part != null && part.renderable != null && billboardCache.contains(part.renderable))
			{
				visibility.activeBillboards.add(part.renderable);
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
		Player localPlayer = client.getLocalPlayer();
		if (owner == localPlayer)
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
			if (topActor == null || !activeRenderableTargets.containsKey(topActor) || !billboardCache.contains(topActor))
			{
				continue;
			}

			for (Actor actor : actors)
			{
				if (actor != null && actor != topActor)
				{
					visibility.suppressedRenderables.add(actor);
				}
			}
		}
	}

	private void updateHoverInteractionState()
	{
		BillboardInteractionState.Change change = interactionState.update(client, groundItemTracker.entries());
		if (!change.changed)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		markHoverInteractionStateDirty(change.previousHoveredTarget);
		markHoverInteractionStateDirty(change.hoveredTarget);
		markHoverInteractionStateDirty(change.previousInteractionTarget);
		markHoverInteractionStateDirty(change.interactionTarget);
		if (change.interactionTarget != change.previousInteractionTarget)
		{
			markHoverInteractionStateDirty(localPlayer);
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
		int queueBudget = updateQueue.size();
		Set<BillboardTargetKey> seenThisFrame = new HashSet<>();
		int scheduled = 0;
		while (queueBudget-- > 0 && scheduled < maxDraws && !updateQueue.isEmpty())
		{
			BillboardTargetKey key = updateQueue.pollFirst();
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
			boolean forceActorResizeRedraw = config.enableAnimationFrameSnapping()
				&& (updateState.resized(snapshot, config.redrawOnClickboxResizePercent()) || compositeResizes.contains(key)
					|| (target.renderable instanceof Actor && shouldForceActorResizeRedraw((Actor) target.renderable)));
			boolean stateChange = updateState.hasCaptureException(snapshot,
				config.enableAnimationFrameSnapping() ? config.redrawOnClickboxResizePercent() : 0);
			boolean bypassCadence = forceHoverInteractionRedraw || forceActorResizeRedraw
				|| stateChange || !targetHasCachedBillboard(target);
			if (!bypassCadence && !updateState.isReady(gameCycle, targetHasCachedBillboard(target)))
			{
				requeueTarget(key);
				continue;
			}

			double qualityScale = updateState.qualityScale;
			if (!forceHoverInteractionRedraw && !forceActorResizeRedraw
				&& !snapshot.animated && !stateChange
				&& !needsBillboardRedraw(target, qualityScale))
			{
				updateState.defer(gameCycle, baseRefreshInterval);
				requeueTarget(key);
				continue;
			}

			frameUpdatePlans.put(key, new FrameUpdatePlan(
				qualityScale,
				forceHoverInteractionRedraw,
				forceActorResizeRedraw,
				updateState,
				snapshot,
				gameCycle,
				baseRefreshInterval,
				renderQualityScale(),
				minimumAnimatedRedrawInterval()));
			FrameUpdatePlan plan = frameUpdatePlans.get(key);
			plan.forceStateChangeRedraw = stateChange;
			if (stateChange) plan.redrawReasons.add(BillboardRedrawReason.CAPTURE_STATE_CHANGE);
			requeueTarget(key);
			scheduled++;
		}
	}

	private void reprioritizeRenderQueue(Map<BillboardTargetKey, BillboardTarget> candidatesByKey, int gameCycle)
	{
		if (updateQueue.isEmpty())
		{
			return;
		}

		List<QueuedBillboardTarget> prioritizedTargets = new ArrayList<>(updateQueue.size());
		int queueIndex = 0;
		for (BillboardTargetKey key : updateQueue)
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
			updateQueue.putDebugScore(key, score);
			prioritizedTargets.add(new QueuedBillboardTarget(key, score, queueIndex));
			state.observe(snapshot);
			queueIndex++;
		}

		prioritizedTargets.sort(Comparator
			.comparingDouble(QueuedBillboardTarget::getPriorityScore).reversed()
			.thenComparingInt(QueuedBillboardTarget::getQueueIndex));
		updateQueue.replaceWith(prioritizedTargets);
	}

	private double computeUpdatePriorityScore(
		BillboardTarget target,
		BillboardUpdateState state,
		UpdateHeuristicSnapshot snapshot,
		int gameCycle,
		int queueIndex
	)
	{
		Actor owner = targetOwnerActor(target);
		Actor localPlayer = client.getLocalPlayer();
		Renderable priorityActor = priorityHoverInteractionTarget();
		return BillboardUpdateScore.compute(
			targetHasCachedBillboard(target),
			shouldForceHoverInteractionRedraw(target),
			(state.resized(snapshot, config.redrawOnClickboxResizePercent()) || compositeResizes.contains(target.targetKey)),
			target.renderable == priorityActor,
			target.renderable == localPlayer,
			owner == localPlayer,
			owner == priorityActor,
			target.type == BillboardTargetType.ACTOR_SPOT_ANIM,
			state,
			snapshot,
			gameCycle,
			queueIndex);
	}

	private double minimumAnimatedRedrawInterval()
	{
		int targetFramesPerSecond = Math.max(1, config.animationFrameCount());
		return config.enableAnimationFrameSnapping() ? Math.max(1d, 50.0d / targetFramesPerSecond) : 1d;
	}

	private Renderable priorityHoverInteractionTarget()
	{
		return interactionState.priorityTarget();
	}

	private void pruneRenderQueue(Set<BillboardTargetKey> validKeys)
	{
		for (BillboardTargetKey key : updateQueue.prune(validKeys))
		{
			billboardUpdateStates.remove(key);
			frameUpdatePlans.remove(key);
		}
	}

	private void appendNewQueueEntries(List<BillboardTarget> orderedTargets)
	{
		List<BillboardTarget> newTargets = new ArrayList<>();
		for (BillboardTarget target : orderedTargets)
		{
			if (!updateQueue.contains(target.targetKey) && !billboardUpdateStates.containsKey(target.targetKey))
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
		updateQueue.addLast(key);
	}

	private void requeueTargetFront(BillboardTargetKey key)
	{
		updateQueue.addFirst(key);
	}

	private void updateActiveSnapshots()
	{
		visibility.publishSnapshots();
	}

	private CollectedCandidates collectCandidates(WorldView worldView)
	{
		Player localPlayer = client.getLocalPlayer();
		LocalPoint localPlayerLocation = WorldViewLocationResolver.toMainWorld(client, localPlayer);
		Rectangle viewport = getViewportBounds();
		List<BillboardTarget> candidates = new ArrayList<>();
		Map<OccupiedTileKey, Actor> topActorsByTile = new HashMap<>();
		Map<Actor, BillboardTarget> actorTargets = new IdentityHashMap<>();
		Map<OccupiedTileKey, List<Actor>> actorsByTile = new HashMap<>();
		Map<OccupiedTileKey, List<Actor>> actorOccupancyByTile = new HashMap<>();
		Set<EffectDedupKey> claimedActorEffects = new HashSet<>();
		Set<OccupiedTileKey> claimedActorEffectTiles = new HashSet<>();

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.NPC, config))
		{
			for (NPC npc : allNpcs(worldView))
			{
				if (npc == null)
				{
					continue;
				}
				noteActorOccupancy(actorOccupancyByTile, npc);
				if ((npc.getWorldView().isTopLevel() && !wasSceneRenderableDrawnLastFrame(npc))
					|| !targetEligibility.actor(localPlayerLocation, npc.getLocalLocation(), npc, viewport))
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
						actorDepth(worldView, npc),
						WorldViewLocationResolver.toMainWorld(client, npc),
						npc.getWorldView().isTopLevel() ? npc.getWorldView().getPlane() : 0
					)
				);
				classificationDebug.logDecision(npc, ObjectClassifier.classifyDecision(npc, client));
			}
		}

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.PLAYER, config))
		{
			for (Player player : allPlayers(worldView))
			{
				if (player == null)
				{
					continue;
				}
				noteActorOccupancy(actorOccupancyByTile, player);
				if ((player.getWorldView().isTopLevel() && !wasSceneRenderableDrawnLastFrame(player))
					|| !targetEligibility.actor(localPlayerLocation, player.getLocalLocation(), player, viewport))
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
						actorDepth(worldView, player),
						WorldViewLocationResolver.toMainWorld(client, player),
						player.getWorldView().isTopLevel() ? player.getWorldView().getPlane() : 0
					)
				);
				classificationDebug.logDecision(player, ObjectClassifier.classifyDecision(player, client));
			}
		}

		applyConfirmedActorStacks(topActorsByTile, actorsByTile, actorOccupancyByTile, actorTargets);
		for (BillboardTarget actorTarget : actorTargets.values())
		{
			candidates.add(actorTarget);
		}


		if (ObjectClassifier.isEnabled(ClassifiedObjectType.EFFECT, config))
		{
			List<Actor> effectActors = new ArrayList<>();
			Map<OccupiedTileKey, List<Actor>> effectActorOccupancyByTile = new HashMap<>();
			for (NPC npc : allNpcs(worldView))
			{
				if (npc != null)
				{
					effectActors.add(npc);
					noteActorOccupancy(effectActorOccupancyByTile, npc);
				}
			}
			for (Player player : allPlayers(worldView))
			{
				if (player != null)
				{
					effectActors.add(player);
					noteActorOccupancy(effectActorOccupancyByTile, player);
				}
			}
			for (Actor actor : effectActors)
			{
				addActorSpotAnimCandidates(
					candidates,
					actor,
					viewport,
					claimedActorEffects,
					claimedActorEffectTiles,
					!isStackedActorTile(effectActorOccupancyByTile, actor)
				);
			}
		}

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.PROJECTILE, config))
		{
			for (Projectile projectile : client.getProjectiles())
			{
				if (projectile != null && billboardCache.contains(projectile)
					&& targetEligibility.projectileInWorld(localPlayerLocation, projectile))
				{
					// Keep its 3D model hidden while the sprite is clipped by the viewport.
					visibility.suppressedRenderables.add(projectile);
				}
				if (projectile == null
					|| !wasSceneRenderableDrawnLastFrame(projectile)
					|| !targetEligibility.projectile(localPlayerLocation, projectile, viewport))
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
					|| !targetEligibility.graphicsObject(localPlayerLocation, graphicsObject, viewport))
				{
					continue;
				}

				if (claimedActorEffects.contains(EffectDedupKey.of(graphicsObject)))
				{
					continue;
				}

				OccupiedTileKey graphicsObjectTile = OccupiedTileKey.of(client.getWorldView(graphicsObject.getLocation().getWorldView()), graphicsObject.getLocation(), graphicsObject.getLevel());
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
					|| !targetEligibility.groundItem(localPlayerLocation, item, groundItem, viewport))
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
				if (worldViewVisibility != null && observed.tileObject.getWorldView() != null && !worldViewVisibility.isVisible(observed.tileObject.getWorldView())) continue;
				if (!targetEligibility.tileObject(localPlayerLocation, observed, viewport))
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

		if (performanceMetrics.isEnabled())
		{
		long actors = actorOccupancyByTile.values().stream().mapToLong(List::size).sum();
		performanceMetrics.count("Actors", actors);
		performanceMetrics.count("Actor targets", actorTargets.size());
		performanceMetrics.count("Items", candidates.stream().filter(target -> target.type == BillboardTargetType.GROUND_ITEM).count());
		performanceMetrics.count("Effects", candidates.stream().filter(target -> target.classifiedType == ClassifiedObjectType.EFFECT).count());
		}
		return new CollectedCandidates(candidates, topActorsByTile, actorsByTile);
	}

	private List<Player> allPlayers(WorldView topLevel)
	{
		List<Player> players = new ArrayList<>();
		Set<Player> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		appendOrderedIdentityDistinct(topLevel.players(), players, seen);
		if (topLevel.worldViews() != null)
		{
			for (WorldView nested : topLevel.worldViews())
			{
				if (nested != null && (worldViewVisibility == null || worldViewVisibility.isVisible(nested)))
				{
					appendOrderedIdentityDistinct(nested.players(), players, seen);
				}
			}
		}
		return players;
	}

	private double actorDepth(WorldView topLevel, Actor player)
	{
		LocalPoint point = WorldViewLocationResolver.toMainWorld(client, player);
		WorldViewLocationResolver.ResolvedLocation resolved = WorldViewLocationResolver.resolve(client, player);
		if (resolved != null)
		{
			double dx = resolved.mainPoint.getX() - client.getCameraFpX();
			double dy = resolved.mainPoint.getY() - client.getCameraFpY();
			double dz = resolved.height - player.getAnimationHeightOffset() - player.getModelHeight() / 2d - client.getCameraFpZ();
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
		return point == null ? Double.NEGATIVE_INFINITY : depthCalculator.cameraDistance(
			point, player.getWorldView().isTopLevel() ? player.getWorldView().getPlane() : 0,
			player.getAnimationHeightOffset() + (player.getModelHeight() / 2.0));
	}

	private List<NPC> allNpcs(WorldView topLevel)
	{
		List<NPC> npcs = new ArrayList<>();
		Set<NPC> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		appendOrderedIdentityDistinct(topLevel.npcs(), npcs, seen);
		if (topLevel.worldViews() != null)
		{
			for (WorldView nested : topLevel.worldViews())
			{
				if (nested != null && (worldViewVisibility == null || worldViewVisibility.isVisible(nested)))
				{
					appendOrderedIdentityDistinct(nested.npcs(), npcs, seen);
				}
			}
		}
		return npcs;
	}

	private <T extends Actor> void appendOrderedIdentityDistinct(
		Iterable<? extends T> source,
		List<T> destination,
		Set<T> seen)
	{
		if (source == null)
		{
			return;
		}

		for (T value : source)
		{
			if (value != null && isActorWorldViewVisible(value) && seen.add(value))
			{
				destination.add(value);
			}
		}
	}

	private boolean isWithinBillboardRadius(LocalPoint source, LocalPoint target)
	{
		if (source == null || target == null)
		{
			return false;
		}
		long dx = source.getX() - target.getX();
		long dy = source.getY() - target.getY();
		long radius = (long) config.billboardRadiusTiles() * BillboardConstants.LOCAL_TILE_SIZE;
		return (dx * dx) + (dy * dy) <= radius * radius;
	}

	private boolean isStackedActorTile(Map<OccupiedTileKey, List<Actor>> actorsByTile, Actor actor)
	{
		if (actor == null)
		{
			return false;
		}

		OccupiedTileKey tileKey = occupiedTileKey(actor);
		if (tileKey == null)
		{
			return false;
		}

		List<Actor> actors = actorsByTile.get(tileKey);
		return actors != null && actors.size() > 1;
	}

	private void applyConfirmedActorStacks(
		Map<OccupiedTileKey, Actor> topActorsByTile,
		Map<OccupiedTileKey, List<Actor>> actorsByTile,
		Map<OccupiedTileKey, List<Actor>> actorOccupancyByTile,
		Map<Actor, BillboardTarget> actorTargets)
	{
		Set<OccupiedTileKey> confirmedTiles = actorStackTracker.confirmedStackedTiles(
			actorOccupancyByTile,
			topActorsByTile,
			client.getTickCount());
		actorsByTile.clear();
		for (OccupiedTileKey tile : confirmedTiles)
		{
			List<Actor> occupants = actorOccupancyByTile.get(tile);
			if (occupants != null && occupants.size() > 1 && topActorsByTile.containsKey(tile))
			{
				actorsByTile.put(tile, occupants);
			}
		}
		for (Map.Entry<OccupiedTileKey, List<Actor>> entry : actorsByTile.entrySet())
		{
			Actor topActor = topActorsByTile.get(entry.getKey());
			for (Actor actor : entry.getValue())
			{
				if (actor != topActor)
				{
					actorTargets.remove(actor);
				}
			}
		}
	}

	private void noteActorOccupancy(Map<OccupiedTileKey, List<Actor>> actorOccupancyByTile, Actor actor)
	{
		if (actor == null || actor.getLocalLocation() == null || actor.getWorldView() == null)
		{
			return;
		}

		OccupiedTileKey tileKey = occupiedTileKey(actor);
		if (tileKey != null)
		{
			actorOccupancyByTile.computeIfAbsent(tileKey, ignored -> new ArrayList<>()).add(actor);
		}
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
		OccupiedTileKey tileKey = occupiedTileKey(actor);
		if (tileKey == null)
		{
			actorTargets.put(actor, candidate);
			return;
		}

		actorsByTile.computeIfAbsent(tileKey, ignored -> new ArrayList<>()).add(actor);
		actorTargets.put(actor, candidate);
		// Use the completed scene draw order, with the local player on top.
		topActorsByTile.put(tileKey, actorDrawOrderTracker.preferred(
			topActorsByTile.get(tileKey), actor, client.getLocalPlayer()));
	}

	private OccupiedTileKey occupiedTileKey(Actor actor)
	{
		if (actor == null || actor.getWorldView() == null)
		{
			return null;
		}
		WorldView view = actor.getWorldView();
		return OccupiedTileKey.of(
			view, actor.getLocalLocation(), view.getPlane());
	}

	private BillboardTarget buildActiveTarget(WorldView worldView, TileObject tileObject)
	{
		if (worldViewVisibility != null && !worldViewVisibility.isVisible(worldView))
		{
			return null;
		}
		ObservedTileObject observed = visibleTileObjects.get(tileObject);
		if (observed == null)
		{
			return null;
		}

		classificationDebug.resolveObservedTileObjectType(observed);
		return BillboardTarget.forTileObject(observed, depthCalculator.depth(observed));
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
			if (!wasSceneRenderableDrawnLastFrame(actorSpotAnim) || !targetEligibility.actorSpotAnimation(actor, actorSpotAnim, viewport))
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
				claimedActorEffectTiles.add(OccupiedTileKey.of(actor.getWorldView(), actor.getLocalLocation(), actor.getWorldView().getPlane()));
			}
		}
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

		return billboardCache.contains(target.renderable);
	}

	private boolean targetHasCachedTileObjectBillboard(BillboardTarget target)
	{
		if (target == null || target.observedTileObject == null)
		{
			return false;
		}

		return BillboardUpdateScheduler.allTileObjectPartsCached(
			target.observedTileObject.parts,
			billboardCache::contains);
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
			if (part.renderable != null && billboardCache.contains(part.renderable))
			{
				return true;
			}
		}

		return false;
	}

	private boolean needsBillboardRedraw(BillboardTarget target, double qualityScale)
	{
		if (target.type == BillboardTargetType.GROUND_ITEM && config.useInventorySpritesForGroundItems())
		{
			BillboardRenderRequest request = requestFactory.build(target);
			CachedBillboard cached = request != null ? billboardCache.get(request.renderable) : null;
			return request == null || cached == null || !cached.key.equals(buildInventorySpriteCacheKey(request, qualityKey(qualityScale)));
		}

		if (target.type == BillboardTargetType.TILE_OBJECT)
		{
			return needsTileObjectBillboardRedraw(target, qualityScale);
		}

		BillboardRenderRequest request = requestFactory.build(target);
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
			BillboardRenderRequest request = requestFactory.build(target, part);
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

	private BillboardCacheKey buildInventorySpriteCacheKey(BillboardRenderRequest request, int renderQualityKey)
	{
		return buildCacheKey(request, outlinePadding(), config.billboardColorBands(), 0, renderQualityKey, 0);
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
			int height = modelWorldHeight(request, localX, localY, verticesY[i]);
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
		if (centerDepth > BillboardConstants.MIN_FORWARD_VISIBLE_DEPTH)
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
			int height = modelWorldHeight(request, localX, localY, verticesY[i]);
			if (depthCalculator.cameraForwardDepth(localX, localY, height) > BillboardConstants.MIN_FORWARD_VISIBLE_DEPTH)
			{
				return true;
			}
		}

		return false;
	}

	private UpdateHeuristicSnapshot buildUpdateHeuristicSnapshot(BillboardTarget target)
	{
		if (target == null) return UpdateHeuristicSnapshot.empty();
		return frameSnapshots.computeIfAbsent(target.targetKey, ignored ->
		{
			if (isCompositeObjectTarget(target)) return buildTileObjectHeuristicSnapshot(target);
			BillboardRenderRequest request = requestFactory.build(target);
			return request != null ? captureSnapshot(request, target.getDepth(), isCadenceControlledTarget(target))
				: UpdateHeuristicSnapshot.forDepth(target.getDepth());
		});
	}

	private UpdateHeuristicSnapshot captureSnapshot(BillboardRenderRequest request, double depth, boolean animated)
	{
		int view = 31 * (request.renderable instanceof Projectile ? orientationCalculator.relativeYaw() : request.relativeYaw)
			+ request.relativePitch;
		// Fitted sprites also depend on the actual camera pitch, even when their source pitch is zero.
		if (request.renderable instanceof Actor && config.enableActorWorldPlaneProjection())
			view = 31 * view + (client.isGpu() ? Float.floatToIntBits(client.getCameraFpPitch()) : client.getCameraPitch());
		UpdateHeuristicSnapshot snapshot = UpdateHeuristicSnapshot.forCapture(request, depth,
			textureResolver.animatedTextureOffsetStateHash(request.animatedTextureId, System.currentTimeMillis()), view, animated, config.redrawOnClickboxResizePercent() > 0);
		return new UpdateHeuristicSnapshot(depth, snapshot.positionKey, snapshot.animationHash, snapshot.modelStateHash,
			snapshot.textureStateHash, snapshot.viewHash, snapshot.animated, snapshot.animationIdentityHash,
			31 * snapshot.appearanceHash + captureStyleHash(request), snapshot.geometry);
	}

	private int captureStyleHash(BillboardRenderRequest request)
	{
		int hash = 31 * config.billboardColorBands() + config.billboardLightBoostPercent();
		hash = 31 * hash + qualityKey();
		hash = 31 * hash + (request.shouldHoverOutline ? 1 : 0);
		hash = 31 * hash + (request.shouldInteractOutline ? 1 : 0);
		hash = 31 * hash + (request.hoverOutlineColor != null ? request.hoverOutlineColor.getRGB() : 0);
		hash = 31 * hash + (request.interactionOutlineColor != null ? request.interactionOutlineColor.getRGB() : 0);
		hash = 31 * hash + config.billboardSpriteOutlineColor().getRGB();
		hash = 31 * hash + outlinePadding();
		boolean[] flags = {config.enableBillboardHighlightOutline(), config.enableBillboardShadowOutline(),
			config.enableBillboardSpriteOutline(), config.enableBillboardSpriteShadows(),
			config.enableBillboardHighlightInline(), config.enableBillboardShadowInline(), config.enableBillboardSpriteInline()};
		for (boolean flag : flags) hash = 31 * hash + (flag ? 1 : 0);
		return hash;
	}

	private boolean isCadenceControlledTarget(BillboardTarget target)
	{
		if (target == null || !config.enableAnimationFrameSnapping()) return false;
		if (target.type != BillboardTargetType.TILE_OBJECT) return true;
		if (target.observedTileObject == null) return false;
		if (BillboardUpdateScheduler.usesTileObjectAnimationCadence(target.observedTileObject.parts, true)) return true;
		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			Model model = part.renderable != null ? part.renderable.getModel() : null;
			if (model != null && model.getFaceTextures() != null) return true;
		}
		return false;
	}

	private UpdateHeuristicSnapshot buildTileObjectHeuristicSnapshot(BillboardTarget target)
	{
		if (target.observedTileObject == null) return UpdateHeuristicSnapshot.empty();
		long position = 0;
		int animation = 1, identity = 1, model = 1, appearance = target.tileObject.getId(), texture = 1, view = 1;
		boolean animated = isCadenceControlledTarget(target);
		for (ObjectRenderablePart part : target.observedTileObject.parts)
		{
			BillboardRenderRequest request = requestFactory.build(target, part);
			if (request == null) continue;
			UpdateHeuristicSnapshot snapshot = captureSnapshot(request, target.getDepth(), animated);
			position = 31 * position + snapshot.positionKey;
			animation = 31 * animation + snapshot.animationHash;
			identity = 31 * identity + snapshot.animationIdentityHash;
			model = 31 * model + snapshot.modelStateHash;
			appearance = 31 * appearance + snapshot.appearanceHash;
			texture = 31 * texture + snapshot.textureStateHash;
			view = 31 * view + snapshot.viewHash;
			CachedBillboard cached = billboardCache.get(part.renderable);
			if (animated && cached != null && snapshot.geometry != null
				&& snapshot.geometry.exceeds(cached.modelBounds, config.redrawOnClickboxResizePercent()))
				compositeResizes.add(target.targetKey);
		}
		return new UpdateHeuristicSnapshot(target.getDepth(), position, animation, model, texture, view,
			animated, identity, appearance, null);
	}

	private BufferedImage renderBillboardImage(
		List<FaceDraw> faces,
		Rectangle bounds,
		int outlinePadding,
		double qualityScaleOverride,
		boolean shouldHoverOutline,
		boolean shouldInteractOutline,
		Color hoverOutlineColor,
		Color interactionOutlineColor)
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
			int colorBands = config.billboardColorBands();
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
							colorBands
						);
					}
					continue;
				}

				try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Solid faces"))
				{
					Color snappedColor = NpcSnapColorBanding.snapToRamp(face.getColor(), colorBands);
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

			boolean anyEdgeEffect = hasAnyEdgeEffect();
			if (anyEdgeEffect || shouldHoverOutline || shouldInteractOutline)
			{
				try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Outline/boundary effects"))
				{
					int[] exteriorOutlineIndices = BillboardOutlineRenderer.captureExteriorBoundaryIndices(image, outlineScratch);

					if (anyEdgeEffect)
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
						applyHoverInteractionOutline(
							image, exteriorOutlineIndices, shouldHoverOutline, shouldInteractOutline,
							hoverOutlineColor, interactionOutlineColor);
					}
				}
			}

			return image;
		}
	}

	private void expireCaches(long nowMillis)
	{
		billboardCache.expire(nowMillis);
		textureResolver.expire(nowMillis);
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
		int lightBoostPercent = BillboardBrightness.effectiveLightBoostPercent(client,
			config.billboardLightBoostPercent());

		for (int face = 0; face < model.getFaceCount(); face++)
		{
			int a = faceIndices1[face];
			int b = faceIndices2[face];
			int c = faceIndices3[face];
			if (BillboardColorUtils.isInvisibleFace(face, faceColors3, transparencies))
			{
				continue;
			}
			if (cullBackFaces && BillboardGeometryUtils.isBackFace(spriteX, spriteY, spriteDepth, a, b, c))
			{
				continue;
			}

			int alpha = transparencies == null || face >= transparencies.length ? 255 : 255 - (transparencies[face] & 0xFF);
			int textureId = textures != null && face < textures.length ? Short.toUnsignedInt(textures[face]) : 0xFFFF;
			Color rawColor = BillboardColorUtils.resolveFaceColor(face, faceColors1, faceColors2, faceColors3,
				unlitFaceColors, alpha, textureId == 0xFFFF ? model : null);
			if (BillboardColorUtils.isSkippedCapeArtifactFaceColor(rawColor))
			{
				continue;
			}
			Color color = BillboardColorUtils.applyLightBoost(rawColor, lightBoostPercent);

			double depth = (
				spriteDepth[a] +
				spriteDepth[b] +
				spriteDepth[c]
			) / 3.0;

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
		if (updatePlan != null && updatePlan.isCompleted()) updatePlan = null;
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
			ensureSpriteScratchCapacity(vertexCount);
			float[] spriteX = spriteXScratch;
			float[] spriteY = spriteYScratch;
			float[] spriteDepth = spriteDepthScratch;
			try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Transform vertices"))
			{
				BillboardGeometryUtils.transformVertices(model, request.relativeYaw, request.relativePitch,
					0, 0, spriteX, spriteY, spriteDepth);
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
					int outlinePadding = outlinePadding(updatePlan.qualityScale);
					Rectangle imageBounds = BillboardGeometryUtils.expandedBounds(sourceBounds, outlinePadding);
					BillboardDrawGeometry previewGeometry;
					try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Draw rect calculation"))
					{
						previewGeometry = buildDrawGeometry(request, renderable, imageBounds, sourceBounds);
					}
					if (previewGeometry == null)
					{
						return cached == null ? null : drawCachedBillboard(request, renderable, cached, nowMillis, queuePosition);
					}

					BillboardCacheKey cacheKey;
					try (BillboardPerformanceMetrics.Timer timer = performanceMetrics.time("Cache key / cache check"))
					{
						cacheKey = buildCacheKey(
							request,
							outlinePadding,
							config.billboardColorBands(),
							builtFaces.textureStateHash,
							qualityKey(updatePlan.qualityScale),
							textureResolver.animatedTextureOffsetStateHash(request.animatedTextureId, nowMillis)
						);
						cacheInvalidated = updatePlan.isForcedRedraw()
							|| shouldRefreshCache(renderable, cacheKey, imageBounds, nowMillis);
						if (cacheInvalidated)
						{
							updatePlan.redrawReasons.addAll(BillboardRedrawReason.cacheChanges(cached, cacheKey, imageBounds, nowMillis));
						}
						if (!cacheInvalidated && cached != null)
						{
							cached.touch(nowMillis);
							updatePlanSucceeded = true;
						}
					}
					if (cacheInvalidated)
					{
						BufferedImage rendered = renderBillboardImage(
							faces,
							sourceBounds,
							outlinePadding,
							updatePlan.qualityScale,
							request.shouldHoverOutline,
							request.shouldInteractOutline,
							request.hoverOutlineColor,
							request.interactionOutlineColor
						);
						if (rendered != null)
						{
							BillboardModelBounds modelBounds = BillboardModelBounds.sample(model, renderable instanceof Projectile);
							cached = new CachedBillboard(cacheKey, imageBounds, sourceBounds, rendered, nowMillis,
								null, modelBounds);
							if (config.logBillboardAnimationData())
								log.debug("Billboard capture: type={} animation={} frame={} pose={} poseFrame={} reasons={}",
									renderable.getClass().getSimpleName(), request.animationId, request.animationFrame,
									request.poseAnimationId, request.poseAnimationFrame, updatePlan.redrawReasons);
							cached.markDebugFrameRedrawn(updatePlan.redrawReasons);
							billboardCache.put(renderable, cached);
							actorResizeObservations.remove(renderable);
							forceActorResizeRedraws.remove(renderable);
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
			updatePlan.partSucceeded(renderable);
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

		cached.stateDebugInfo(buildStateDebugInfo(request, queuePosition, cached));
		if (renderable instanceof Actor)
			debug.recordActorFrames((Actor) renderable, request.animationId, request.animationFrame,
				cached.key.animationFrame, request.poseAnimationFrame, cached.key.poseAnimationFrame);
		BillboardDrawGeometry geometry = buildDrawGeometry(request, renderable, cached.bounds, cached.contentBounds);
		return geometry != null ? new BillboardRenderResult(geometry, cached.image, cached.bounds) : null;
	}

	private BillboardCacheKey buildCacheKey(
		BillboardRenderRequest request,
		int outlinePadding,
		int colorBands,
		int textureStateHash,
		int renderQualityKey,
		int animatedTextureOffsetStateHash)
	{
		return BillboardCacheKey.create(
			request,
			config,
			outlinePadding,
			colorBands,
			renderQualityKey,
			textureStateHash,
			animatedTextureOffsetStateHash,
			BillboardBrightness.effectiveLightBoostPercent(client, config.billboardLightBoostPercent()));
	}

	private BillboardCachePreviewKey buildPreviewCacheKey(BillboardRenderRequest request, double qualityScale, long nowMillis)
	{
		return BillboardCachePreviewKey.create(
			request,
			config,
			outlinePadding(qualityScale),
			qualityKey(qualityScale),
			textureResolver.animatedTextureOffsetStateHash(request.animatedTextureId, nowMillis),
			BillboardBrightness.effectiveLightBoostPercent(client, config.billboardLightBoostPercent()));
	}

	private Rectangle buildDrawRect(Rectangle cachedBounds, int anchorX, int anchorY, int targetWidth, int targetHeight)
	{
		double originX = (-cachedBounds.x) * (targetWidth / (double) cachedBounds.width);
		double originY = (-cachedBounds.y) * (targetHeight / (double) cachedBounds.height);
		int drawX = (int) Math.round(anchorX - originX);
		int drawY = (int) Math.round(anchorY - originY);
		if (!BillboardGeometryUtils.isUsableDrawSize(targetWidth, targetHeight))
		{
			return null;
		}

		return new Rectangle(drawX, drawY, targetWidth, targetHeight);
	}

	BillboardDrawGeometry buildDrawGeometry(
		BillboardRenderRequest request,
		Renderable renderable,
		Rectangle billboardBounds,
		Rectangle contentBounds)
	{
		boolean actor = renderable instanceof Actor;
		if (actor && config.enableActorWorldPlaneProjection())
		{
			BillboardDrawGeometry projected = actorWorldPlaneGeometry((Actor) renderable, request,
				billboardBounds, contentBounds);
			if (projected != null)
			{
				Rectangle visible = projected.bounds;
				return !isOutsideViewport(visible.x, visible.y, visible.width, visible.height) ? projected : null;
			}
		}
		// Scenery and items must keep the cached sprite's scale independent of
		// mutable model height. Effects retain their specialized anchors below.
		boolean modelOrigin = !(renderable instanceof Projectile)
			&& !(renderable instanceof ActorSpotAnim) && !(renderable instanceof GraphicsObject);
		BillboardCanvasPoint base = modelOrigin
			? depthCalculator.projectCanvasPoint(request.localPoint, request.plane, request.verticalOffset)
			: null;
		double canvasScale = client.getScale();
		if (renderable instanceof TileItem && config.useInventorySpritesForGroundItems())
		{
			canvasScale *= inventoryGroundItemZoomScale();
		}
		Rectangle drawRect = renderable instanceof Projectile
			? BillboardProjectileGeometry.drawBounds(depthCalculator, (Projectile) renderable, billboardBounds, client.getScale())
			: modelOrigin
				? BillboardGeometryUtils.projectedDrawBounds(billboardBounds, base, canvasScale)
				: buildLegacyDrawRect(request, renderable, billboardBounds);
		if (drawRect == null)
		{
			return null;
		}
		if (request.verticalAnchor == VerticalAnchor.GROUND_CONTACT)
		{
			Point groundPoint = base != null
				? new Point((int) Math.round(base.x), (int) Math.round(base.y))
				: Perspective.localToCanvas(client, request.localPoint, request.plane, request.verticalOffset);
			drawRect = alignGroundContact(drawRect, billboardBounds, contentBounds, groundPoint);
			if (drawRect == null)
			{
				return null;
			}
		}

		BillboardDrawGeometry geometry = BillboardDrawGeometry.rectangular(drawRect);
		if (actor && base != null && !config.enableActorWorldPlaneProjection() && !config.ignoreProjectionSkewCorrection())
		{
			double slope = BillboardGeometryUtils.projectionShearSlope(base.verticalRight, base.verticalUp);
			geometry = BillboardDrawGeometry.actorSkew(drawRect, billboardBounds, contentBounds, slope);
			logProjectionSkew((Actor) renderable, request, geometry, base, slope);
		}
		Rectangle visibleBounds = geometry.bounds;
		return !isOutsideViewport(visibleBounds.x, visibleBounds.y, visibleBounds.width, visibleBounds.height)
			? geometry
			: null;
	}

	private BillboardDrawGeometry actorWorldPlaneGeometry(Actor actor, BillboardRenderRequest request,
		Rectangle image, Rectangle content)
	{
		LocalPoint location = request.localPoint;
		if (location == null)
		{
			return null;
		}
		double viewBearing = ActorWorldPlane.viewingBearing(client, location);
		double tileHeight = request.sceneLocation != null
			? Perspective.getTileHeight(client, location, request.plane)
			: actor.getWorldView() != null && actor.getWorldView().isTopLevel()
			? Perspective.getFootprintTileHeight(client, location, request.plane, actor.getFootprintSize())
			: Perspective.getTileHeight(client, location, request.plane);
		return ActorWorldPlane.project(depthCalculator, location,
			BillboardDepthCalculator.worldHeight(tileHeight, request.verticalOffset), viewBearing, image, content);
	}

	static Rectangle alignGroundContact(
		Rectangle drawRect,
		Rectangle imageBounds,
		Rectangle contentBounds,
		Point groundPoint)
	{
		if (drawRect == null || imageBounds == null || imageBounds.isEmpty()
			|| contentBounds == null || contentBounds.isEmpty() || groundPoint == null
			|| drawRect.height <= 0)
		{
			return drawRect;
		}

		double scaleY = drawRect.height / (double) imageBounds.height;
		double contentBottomOffset = (contentBounds.getMaxY() - imageBounds.y) * scaleY;
		if (!Double.isFinite(contentBottomOffset))
		{
			return drawRect;
		}

		return new Rectangle(
			drawRect.x,
			(int) Math.round(groundPoint.getY() - contentBottomOffset),
			drawRect.width,
			drawRect.height);
	}

	private Rectangle buildLegacyDrawRect(
		BillboardRenderRequest request,
		Renderable renderable,
		Rectangle billboardBounds)
	{
		Point basePoint = Perspective.localToCanvas(client, request.localPoint, request.plane, request.verticalOffset);
		Point centerPoint = Perspective.localToCanvas(client, request.localPoint, request.plane, request.verticalOffset + (renderable.getModelHeight() / 2));
		Point topPoint = Perspective.localToCanvas(client, request.localPoint, request.plane, request.verticalOffset + renderable.getModelHeight());
		// Scale in model space using the camera-forward depth. Radial distance makes
		// targets at the edge of the FOV too small, while the projected endpoint height
		// varies excessively because the endpoints have different perspective depths.
		double forwardDepth = depthCalculator.cameraForwardDepth(
			request.localPoint,
			request.plane,
			request.verticalOffset + (renderable.getModelHeight() / 2.0));
		int targetHeight = 0;
		if (BillboardGeometryUtils.isUsableDistance(forwardDepth))
		{
			// Perspective.localToCanvas uses the client scale for both CPU and GPU
			// projections. Keep effect sprites on the same scale as occlusion rays.
			double perspectiveScale = client.getScale() / forwardDepth;
			targetHeight = BillboardGeometryUtils.scaledSize(billboardBounds.height, perspectiveScale);
		}
		if (targetHeight <= 0)
		{
			targetHeight = BillboardGeometryUtils.projectedHeight(basePoint, topPoint);
		}
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

	private void logProjectionSkew(
		Actor actor,
		BillboardRenderRequest request,
		BillboardDrawGeometry geometry,
		BillboardCanvasPoint base,
		double slope)
	{
		if (!config.debugLogProjectionSkew() || !log.isDebugEnabled()
			|| !isProjectionSkewDebugTarget(actor, geometry.bounds))
		{
			return;
		}

		int cameraPitch = client.getCameraPitch();
		int cameraYaw = client.getCameraYaw();
		if (actor == lastSkewDebugActor
			&& cameraPitch == lastSkewDebugCameraPitch
			&& cameraYaw == lastSkewDebugCameraYaw)
		{
			return;
		}
		lastSkewDebugActor = actor;
		lastSkewDebugCameraPitch = cameraPitch;
		lastSkewDebugCameraYaw = cameraYaw;

		log.debug(String.format(Locale.ROOT,
			"Projection skew: actor='%s' cameraPitch=%d cameraYaw=%d relativePitch=%d "
				+ "base=(%.3f, %.3f) vertical=(%.5f, %.5f) slope=%.5f draw=%s",
			actor.getName(), cameraPitch, cameraYaw, request.relativePitch,
			base.x, base.y, base.verticalRight, base.verticalUp, slope, geometry.bounds));
	}

	private boolean isProjectionSkewDebugTarget(Actor actor, Rectangle drawRect)
	{
		if (interactionState.priorityTarget() == actor || interactionState.clickedActor() == actor)
		{
			return true;
		}

		Point mouse = client.getMouseCanvasPosition();
		return mouse != null && drawRect != null && drawRect.contains(mouse.getX(), mouse.getY());
	}
	private double inventoryGroundItemZoomScale()
	{
		int zoom = client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG);
		int minimumZoom = client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG_MIN);
		int maximumZoom = client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG_MAX);
		if (maximumZoom <= minimumZoom)
		{
			return INVENTORY_GROUND_ITEM_MIN_ZOOM_SCALE;
		}

		double zoomedOutFraction = 1.0d - ((zoom - minimumZoom) / (double) (maximumZoom - minimumZoom));
		zoomedOutFraction = Math.max(0.0d, Math.min(1.0d, zoomedOutFraction));
		return INVENTORY_GROUND_ITEM_MIN_ZOOM_SCALE
			+ ((INVENTORY_GROUND_ITEM_MAX_ZOOM_SCALE - INVENTORY_GROUND_ITEM_MIN_ZOOM_SCALE) * zoomedOutFraction);
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
			|| config.enablePlayerInteractionOutline()
			|| config.enableNpcInteractionOutline()
			|| config.enableGroundItemInteractionOutline()
			? OUTLINE_PADDING
			: 0;
	}

	private int outlinePadding(double qualityScale)
	{
		return BillboardRenderQuality.rasterSafePadding(outlinePadding(), qualityScale);
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

	private void applyHoverInteractionOutline(
		BufferedImage image,
		int[] exteriorOutlineIndices,
		boolean shouldHoverOutline,
		boolean shouldInteractOutline,
		Color hoverOutlineColor,
		Color interactionOutlineColor)
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
			? interactionOutlineColor
			: shouldHoverOutline ? hoverOutlineColor : null;
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

	private ObservedTileObject buildObservedTileObject(TileObject tileObject)
	{
		return ObservedTileObjectBuilder.build(tileObject);
	}

	private int modelWorldHeight(BillboardRenderRequest request, int localX, int localY, float vertexY)
	{
		if (request == null || !Float.isFinite(vertexY))
		{
			return 0;
		}

		// Model Y is signed in RuneLite model space: negative values are above
		// the model origin. Height offsets use the same upward-positive convention
		// as Perspective.localToCanvas, so both are subtracted from world z.
		if (Double.isFinite(request.resolvedWorldHeight)) return (int) Math.round(request.resolvedWorldHeight + vertexY);
		return (int) Math.round(BillboardDepthCalculator.worldHeight(
			tileHeightAt(localX, localY, request.plane), request.verticalOffset) + vertexY);
	}

	private int tileHeightAt(int localX, int localY, int plane)
	{
		return Perspective.getTileHeight(client, new LocalPoint(localX, localY), plane);
	}

}
