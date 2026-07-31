package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.features.GroundItemBillboard;
import com.kierenboal.npcsnap.features.GroundItemBillboardTracker;
import com.kierenboal.npcsnap.export.BillboardExportAngles;
import com.kierenboal.npcsnap.export.BillboardExportAnimationFrames;
import com.kierenboal.npcsnap.export.BillboardExportBatch;
import com.kierenboal.npcsnap.export.BillboardExportFrame;
import com.kierenboal.npcsnap.export.BillboardExportPaths;
import com.kierenboal.npcsnap.occlusion.BillboardOcclusionDebugSampler;
import com.kierenboal.npcsnap.occlusion.BillboardOcclusionMask;
import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;
import com.kierenboal.npcsnap.occlusion.BillboardOcclusionRegions;
import com.kierenboal.npcsnap.occlusion.BillboardWorldOcclusionCollector;
import com.kierenboal.npcsnap.rendering.AnimationFrameSnapper;
import com.kierenboal.npcsnap.rendering.BillboardAngleUtils;
import com.kierenboal.npcsnap.rendering.BillboardColorUtils;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardDepthSurface;
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
import com.kierenboal.npcsnap.rendering.BuiltFaces;
import com.kierenboal.npcsnap.rendering.FaceDraw;
import com.kierenboal.npcsnap.rendering.NpcSnapColorBanding;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;
import com.kierenboal.npcsnap.rendering.RenderedBillboardImage;
import com.kierenboal.npcsnap.rendering.TextureSample;
import com.kierenboal.npcsnap.rendering.TextureUvs;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import com.kierenboal.npcsnap.state.BillboardCacheKey;
import com.kierenboal.npcsnap.state.BillboardCachePreviewKey;
import com.kierenboal.npcsnap.state.BillboardCacheStore;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import com.kierenboal.npcsnap.state.BillboardUpdateQueue;
import com.kierenboal.npcsnap.state.BillboardUpdateScheduler;
import com.kierenboal.npcsnap.state.BillboardUpdateScore;
import com.kierenboal.npcsnap.state.BillboardUpdateState;
import com.kierenboal.npcsnap.state.BillboardVisibilityState;
import com.kierenboal.npcsnap.state.CachedBillboard;
import com.kierenboal.npcsnap.state.FrameUpdatePlan;
import com.kierenboal.npcsnap.state.QueuedBillboardTarget;
import com.kierenboal.npcsnap.state.TimedCacheEntry;
import com.kierenboal.npcsnap.state.UpdateHeuristicSnapshot;
import com.kierenboal.npcsnap.targeting.ActorStackTracker;
import com.kierenboal.npcsnap.targeting.BillboardClassificationDebug;
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
	private final Set<Renderable> sceneRenderablesThisFrame = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Renderable> sceneRenderablesLastFrame = Collections.newSetFromMap(new IdentityHashMap<>());
	private final BillboardUpdateQueue updateQueue = new BillboardUpdateQueue();
	private final BillboardClassificationDebug classificationDebug;
	private final Map<BillboardTargetKey, BillboardUpdateState> billboardUpdateStates = new HashMap<>();
	private final Map<BillboardTargetKey, FrameUpdatePlan> frameUpdatePlans = new HashMap<>();
	private final Set<Renderable> forceHoverInteractionRedraws = Collections.newSetFromMap(new IdentityHashMap<>());
	private final BillboardOutlineRenderer.Scratch outlineScratch = new BillboardOutlineRenderer.Scratch();
	private final BillboardOcclusionMask occlusionMask = new BillboardOcclusionMask();
	private float[] spriteXScratch = new float[0];
	private float[] spriteYScratch = new float[0];
	private float[] spriteDepthScratch = new float[0];
	private final BillboardFrameBuffer frameBuffer = new BillboardFrameBuffer(occlusionMask, performanceMetrics, this::billboardDepthSurface);
	private final BillboardOcclusionDebugSampler occlusionDebugSampler = new BillboardOcclusionDebugSampler(occlusionMask, this::billboardDepthSurface);
	private int activeBillboardsGameCycle = Integer.MIN_VALUE;
	private final BillboardInteractionState interactionState = new BillboardInteractionState();
	private final ActorStackTracker actorStackTracker = new ActorStackTracker();
	private final BillboardRenderRequestFactory requestFactory;
	private final BillboardTargetEligibility targetEligibility;

	@Inject
	private NpcBillboardOverlay(Client client, ItemManager itemManager, NpcSnapConfig config, NpcSnapDebug debug, AnimationFrameSnapper animationFrameSnapper)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
		this.debug = debug;
		this.depthCalculator = new BillboardDepthCalculator(client);
		this.worldOcclusionCollector = new BillboardWorldOcclusionCollector(client, config, depthCalculator, performanceMetrics, log);
		this.orientationCalculator = new BillboardOrientationCalculator(client, config);
		this.requestFactory = new BillboardRenderRequestFactory(
			client, config, debug, animationFrameSnapper, orientationCalculator, interactionState);
		this.targetEligibility = new BillboardTargetEligibility(
			client, config, requestFactory, this::projectedModelCanvasBounds, this::isSceneRenderedInFront);
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
			List<PreparedBillboardDraw> preparedDraws = new ArrayList<>(visibleTargets.size());
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
		clearInteractionIfMatches(item);
		billboardCache.remove(item);
		activeRenderableTargets.remove(item);
		visibility.activeBillboards.remove(item);
	}

	private void clearActiveState()
	{
		activeRenderableTargets.clear();
		actorStackTracker.clear();
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

	private boolean hasActiveBillboardTargets()
	{
		return !activeRenderableTargets.isEmpty() || !visibility.activeTileObjects.isEmpty();
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
		visibility.clearActiveTileObjects();
	}

	void clearTextureCache()
	{
		textureResolver.clear();
	}

	void clearBillboardCache()
	{
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
					billboardCache.remove(part.renderable);
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
			occlusionDebugSampler.clear();
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
	}

	void clearInteractionIfMatches(TileItem item)
	{
		interactionState.clearIfMatches(item);
	}

	void clearInteractionState()
	{
		interactionState.clear();
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
		if (target.type == BillboardTargetType.TILE_OBJECT)
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
		return frames.isEmpty() ? null : new BillboardExportBatch(name, frames);
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

		for (Map.Entry<TileObject, ObservedTileObject> candidate : visibleTileObjects.entrySet())
		{
			TileObject object = candidate.getKey();
			if (object == null || object.getId() != entry.getIdentifier())
			{
				continue;
			}
			for (ObjectRenderablePart part : candidate.getValue().parts)
			{
				if (part.localPoint != null
					&& part.localPoint.getSceneX() == entry.getParam0()
					&& part.localPoint.getSceneY() == entry.getParam1())
				{
					return BillboardTarget.forTileObject(candidate.getValue(), depthCalculator.depth(candidate.getValue()));
				}
			}
		}
		return null;
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
		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		int offsetX = origin != null && original.localPoint != null ? original.localPoint.getX() - origin.getX() : 0;
		int offsetZ = origin != null && original.localPoint != null ? original.localPoint.getY() - origin.getY() : 0;
		double yawSin = Perspective.SINE14[yaw] / 65536.0;
		double yawCos = Perspective.COSINE14[yaw] / 65536.0;
		int inversePitch = Math.floorMod(-pitch, BILLBOARD_FULL_CIRCLE);
		double pitchSin = Perspective.SINE14[inversePitch] / 65536.0;
		double pitchCos = Perspective.COSINE14[inversePitch] / 65536.0;
		for (int i = 0; i < vertexCount; i++)
		{
			double modelX = verticesX[i] + offsetX;
			double modelZ = verticesZ[i] + offsetZ;
			double rotatedX = (modelX * yawCos) + (modelZ * yawSin);
			double rotatedZ = (modelZ * yawCos) - (modelX * yawSin);
			spriteX[i] = (float) rotatedX;
			spriteY[i] = (float) ((verticesY[i] * pitchCos) - (rotatedZ * pitchSin));
			spriteDepth[i] = (float) ((rotatedZ * pitchCos) + (verticesY[i] * pitchSin));
		}
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
		RenderedBillboardImage rendered = renderBillboardImage(
			builtFaces.faces, bounds, outlinePadding, qualityScale,
			false, false, null, null);
		return rendered == null ? null
			: new ExportRenderedPart(rendered.image, BillboardGeometryUtils.expandedBounds(bounds, outlinePadding));
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

		return visibility.shouldHideRenderable(renderable, client.isClientThread());
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
			preparedDraws.add(new PreparedBillboardDraw(request, result, paintOrder));
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
		if (cached == null || !cached.key.equals(cacheKey) || !cached.bounds.equals(bounds))
		{
			cached = new CachedBillboard(cacheKey, bounds, prepareGroundItemSprite(inventorySprite, request), nowMillis);
			billboardCache.put(item, cached);
		}

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
				occlusionMask.prepare(worldOcclusionCollector.occluders(), config.billboardOcclusionQuality(), viewportX, viewportY, viewportWidth, viewportHeight, occlusionBounds, occlusionRegions);
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
		return BillboardDepthSurface.from(depthCalculator, request, draw.sourceBounds, draw.bounds, baseHeight);
	}

	private List<BillboardTarget> getVisibleTargets(WorldView worldView)
	{
		ensureActiveBillboardsCurrent(worldView);

		List<BillboardTarget> visibleTargets = new ArrayList<>(activeRenderableTargets.size() + visibility.activeTileObjects.size());
		for (BillboardTarget target : activeRenderableTargets.values())
		{
			visibleTargets.add(target);
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
				previewBounds != null ? previewBounds.y + previewBounds.height : Integer.MAX_VALUE,
				target.getDepth(),
				target.type == BillboardTargetType.PROJECTILE,
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
			worldOcclusionCollector.collectTerrainOccluders(worldView, occlusionTraceTargets, occlusionQuality, sceneRenderablesLastFrame);
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

		BillboardRenderRequest request = requestFactory.build(target);
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
			BillboardRenderRequest request = requestFactory.build(target, part);
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
		Renderable priorityActor = priorityHoverInteractionTarget();
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
			boolean cadenceControlledProjectile = isCadenceControlledProjectile(target);
			UpdateHeuristicSnapshot snapshot = buildUpdateHeuristicSnapshot(target);
			BillboardUpdateState updateState = billboardUpdateStates.computeIfAbsent(key, ignored -> new BillboardUpdateState(renderQualityScale()));
			boolean bypassCadence = forceHoverInteractionRedraw
				|| !targetHasCachedBillboard(target);
			if (!cadenceControlledProjectile)
			{
				bypassCadence = bypassCadence
					|| updateState.hasPositionChangedSinceRedraw(snapshot)
					|| updateState.hasViewChangedSinceRedraw(snapshot)
					|| updateState.hasModelChangedSinceRedraw(snapshot)
					|| updateState.hasAnimationChangedSinceRedraw(snapshot)
					|| updateState.hasTextureChangedSinceRedraw(snapshot);
			}
			if (!bypassCadence && !updateState.isReady(gameCycle, targetHasCachedBillboard(target)))
			{
				requeueTarget(key);
				continue;
			}

			double qualityScale = updateState.qualityScale;
			if (!forceHoverInteractionRedraw
				&& !cadenceControlledProjectile
				&& !needsBillboardRedraw(target, qualityScale))
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

	private int minimumAnimatedRedrawInterval()
	{
		int targetFramesPerSecond = Math.max(1, config.animationFrameCount());
		return Math.max(1, (int) Math.ceil(50.0d / targetFramesPerSecond));
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
		LocalPoint localPlayerLocation = localPlayer != null ? localPlayer.getLocalLocation() : null;
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
			for (NPC npc : worldView.npcs())
			{
				if (npc == null)
				{
					continue;
				}
				noteActorOccupancy(actorOccupancyByTile, npc);
				if (!wasSceneRenderableDrawnLastFrame(npc)
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
						depthCalculator.depth(npc),
						npc.getLocalLocation(),
						npc.getWorldView().getPlane()
					)
				);
				classificationDebug.logDecision(npc, ObjectClassifier.classifyDecision(npc, client));
			}
		}

		if (ObjectClassifier.isEnabled(ClassifiedObjectType.PLAYER, config))
		{
			for (Player player : worldView.players())
			{
				if (player == null)
				{
					continue;
				}
				noteActorOccupancy(actorOccupancyByTile, player);
				if (!wasSceneRenderableDrawnLastFrame(player)
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
						depthCalculator.depth(player),
						player.getLocalLocation(),
						player.getWorldView().getPlane()
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
			for (NPC npc : worldView.npcs())
			{
				if (npc != null)
				{
					effectActors.add(npc);
					noteActorOccupancy(effectActorOccupancyByTile, npc);
				}
			}
			for (Player player : worldView.players())
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

	private void applyConfirmedActorStacks(
		Map<OccupiedTileKey, Actor> topActorsByTile,
		Map<OccupiedTileKey, List<Actor>> actorsByTile,
		Map<OccupiedTileKey, List<Actor>> actorOccupancyByTile,
		Map<Actor, BillboardTarget> actorTargets)
	{
		Set<OccupiedTileKey> confirmedTiles = actorStackTracker.confirmedStackedTiles(actorOccupancyByTile, client.getTickCount());
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

		OccupiedTileKey tileKey = OccupiedTileKey.of(actor.getLocalLocation(), actor.getWorldView().getPlane());
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
		OccupiedTileKey tileKey = OccupiedTileKey.of(localPoint, actor.getWorldView().getPlane());
		if (tileKey == null)
		{
			actorTargets.put(actor, candidate);
			return;
		}

		actorsByTile.computeIfAbsent(tileKey, ignored -> new ArrayList<>()).add(actor);
		actorTargets.put(actor, candidate);
		Actor currentActor = topActorsByTile.get(tileKey);
		if (currentActor == null)
		{
			topActorsByTile.put(tileKey, actor);
			return;
		}

		BillboardTarget currentTarget = actorTargets.get(currentActor);
		if (currentTarget != null && currentTarget.getDepth() <= candidate.getDepth())
		{
			return;
		}

		topActorsByTile.put(tileKey, actor);
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
				claimedActorEffectTiles.add(OccupiedTileKey.of(actor.getLocalLocation(), actor.getWorldView().getPlane()));
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
			int height = tileHeightAt(localX, localY, request.plane) + request.verticalOffset + (int) Math.round(Math.max(0.0f, -verticesY[i]));
			if (depthCalculator.cameraForwardDepth(localX, localY, height) > BillboardConstants.MIN_FORWARD_VISIBLE_DEPTH)
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

		if (isCadenceControlledProjectile(target))
		{
			return UpdateHeuristicSnapshot.cadencedProjectile(target.getDepth());
		}

		BillboardRenderRequest request = requestFactory.build(target);
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

	private boolean isCadenceControlledProjectile(BillboardTarget target)
	{
		return target != null
			&& target.type == BillboardTargetType.PROJECTILE
			&& config.enableAnimationFrameSnapping();
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

			BillboardRenderRequest request = requestFactory.build(target, part);
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

	private RenderedBillboardImage renderBillboardImage(
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

			return new RenderedBillboardImage(image);
		}
	}

	private void expireCaches(long nowMillis)
	{
		billboardCache.expire(nowMillis);
		textureResolver.expire(nowMillis);
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
					int outlinePadding = outlinePadding(updatePlan.qualityScale);
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
							config.billboardColorBands(),
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
							request.shouldInteractOutline,
							request.hoverOutlineColor,
							request.interactionOutlineColor
						);
						if (rendered != null)
						{
							cached = new CachedBillboard(cacheKey, imageBounds, rendered.image, nowMillis);
							cached.markDebugFrameRedrawn();
							billboardCache.put(renderable, cached);
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
			animatedTextureOffsetStateHash);
	}

	private BillboardCachePreviewKey buildPreviewCacheKey(BillboardRenderRequest request, double qualityScale, long nowMillis)
	{
		return BillboardCachePreviewKey.create(
			request,
			config,
			outlinePadding(qualityScale),
			qualityKey(qualityScale),
			textureResolver.animatedTextureOffsetStateHash(request.animatedTextureId, nowMillis));
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
			double perspectiveScale = client.get3dZoom() / forwardDepth;
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
		if (config.useInventorySpritesForGroundItems() && renderable instanceof TileItem)
		{
			targetHeight = BillboardGeometryUtils.scaledSize(targetHeight, inventoryGroundItemZoomScale());
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

	private int tileHeightAt(int localX, int localY, int plane)
	{
		return Perspective.getTileHeight(client, new LocalPoint(localX, localY), plane);
	}

}

