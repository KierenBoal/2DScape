package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.BillboardConstants;
import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;
import com.kierenboal.npcsnap.targeting.ObservedTileObject;
import com.kierenboal.npcsnap.targeting.ObservedTileObjectBuilder;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.slf4j.Logger;

public final class BillboardWorldOcclusionCollector
{
	private static final float TERRAIN_OCCLUSION_DEPTH_BIAS = BillboardConstants.LOCAL_TILE_SIZE * 2.0f;
	private static final int DEBUG_OCCLUSION_SAMPLE_LIMIT = 8;
	private static final int MAX_MODEL_GEOMETRY_CACHE_SIZE = 4096;
	private static final int MAX_FALLBACK_SHAPE_CACHE_SIZE = 4096;
	private static final String[] TERRAIN_OCCLUDER_SOURCES =
		{"terrain plane=0", "terrain plane=1", "terrain plane=2", "terrain plane=3"};

	private final Client client;
	private final NpcSnapConfig config;
	private final BillboardDepthCalculator depthCalculator;
	private final BillboardPerformanceMetrics performanceMetrics;
	private final Logger log;
	private final List<BillboardOcclusionMask.Occluder> worldOccluders = new ArrayList<>();
	private final List<Rectangle> interestRegions = new ArrayList<>();
	private final Set<Long> visitedTileCoordinates = new HashSet<>();
	private final Set<Tile> visitedTiles = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Tile> bridgeLinkedTiles = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<TileObject> visitedObjects = Collections.newSetFromMap(new IdentityHashMap<>());
	private final List<Tile> terrainTiles = new ArrayList<>();
	private final IdentityHashMap<Tile, TerrainGeometry> terrainGeometryCache = new IdentityHashMap<>();
	private final IdentityHashMap<Model, ModelGeometry> modelGeometryCache = new IdentityHashMap<>();
	private final IdentityHashMap<TileObject, CachedFallbackShapes> fallbackShapeCache = new IdentityHashMap<>();
	private Scene cachedTerrainScene;
	private CameraProjectionState fallbackShapeCameraState;
	private Rectangle interestBounds;
	private int debugShapesAccepted;
	private int debugTerrainTilesConsidered;
	private int debugTerrainTilesAccepted;
	private int debugTerrainFacesAccepted;
	private int debugFlatTerrainFacesSkipped;
	private int debugBridgeTerrainTilesSkipped;
	private int debugBridgeTilesIncluded;
	private int debugTriangleOccludersAccepted;
	private int debugVerticalFallbackOccludersAccepted;
	private int debugFlatFallbackOccludersAccepted;
	private int debugBridgeFallbacksSuppressed;
	private int debugBridgeSceneryNotRendered;
	private int debugOccludersRejectedBehindCamera;
	private int debugSceneObjectsConsidered;
	private int debugSceneObjectsRejectedByFilter;
	private int debugSceneObjectsSkippedOutsideInterest;
	private int debugSceneObjectsWithoutParts;
	private int debugSceneObjectsAccepted;
	private int debugSceneObjectFacesAccepted;
	private int debugTerrainTilesRejectedByBroadPhase;
	private int debugSceneObjectsRejectedByBroadPhase;
	private int debugModelFacesInspected;
	private int debugModelGeometryCacheHits;
	private int debugModelGeometryCacheMisses;
	private int debugFallbackShapeQueries;
	private int debugLastLoggedCycle = Integer.MIN_VALUE;
	private final List<String> debugAcceptedScenerySamples = new ArrayList<>();
	private final List<String> debugRejectedScenerySamples = new ArrayList<>();
	private final List<String> debugBridgeTileSamples = new ArrayList<>();

	public BillboardWorldOcclusionCollector(
		Client client,
		NpcSnapConfig config,
		BillboardDepthCalculator depthCalculator,
		BillboardPerformanceMetrics performanceMetrics,
		Logger log)
	{
		this.client = client;
		this.config = config;
		this.depthCalculator = depthCalculator;
		this.performanceMetrics = performanceMetrics;
		this.log = log;
	}

	public void resetFrame()
	{
		worldOccluders.clear();
		clearInterest();
		clearDebugStats();
	}

	public void clearInterest()
	{
		interestBounds = null;
		interestRegions.clear();
	}

	public void addInterest(Rectangle previewBounds, Rectangle viewportBounds)
	{
		if (previewBounds == null || viewportBounds == null)
		{
			return;
		}

		Rectangle clipped = viewportBounds.intersection(BillboardOcclusionRegions.expanded(previewBounds, occlusionInterestMargin()));
		if (!clipped.isEmpty())
		{
			interestRegions.add(clipped);
			interestBounds = interestBounds == null ? clipped : interestBounds.union(clipped);
		}
	}

	public void collectTerrainOccluders(WorldView worldView, List<TraceTarget> traceTargets, BillboardOcclusionQuality quality)
	{
		collectTerrainOccluders(worldView, traceTargets, quality, Collections.emptySet());
	}

	public void collectTerrainOccluders(WorldView worldView, List<TraceTarget> traceTargets, BillboardOcclusionQuality quality, Set<Renderable> renderedSceneRenderables)
	{
		if (quality == BillboardOcclusionQuality.OFF || interestBounds == null || worldView == null || traceTargets == null || traceTargets.isEmpty())
		{
			return;
		}

		Scene scene = worldView.getScene();
		Tile[][][] tiles = scene != null ? scene.getTiles() : null;
		if (tiles == null)
		{
			return;
		}
		if (scene != cachedTerrainScene)
		{
			terrainGeometryCache.clear();
			modelGeometryCache.clear();
			fallbackShapeCache.clear();
			fallbackShapeCameraState = null;
			cachedTerrainScene = scene;
		}
		refreshFallbackShapeCacheCameraState();

		visitedTileCoordinates.clear();
		visitedTiles.clear();
		bridgeLinkedTiles.clear();
		visitedObjects.clear();
		terrainTiles.clear();
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Terrain corridor traversal"))
		{
			for (TraceTarget traceTarget : traceTargets)
			{
				if (traceTarget == null || !isPlaneEligible(traceTarget.plane))
				{
					continue;
				}

				collectTerrainCorridorTiles(tiles, traceTarget, quality, visitedTileCoordinates, visitedTiles, bridgeLinkedTiles, terrainTiles);
			}
		}
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Terrain face/scenery gathering"))
		{
			try (BillboardPerformanceMetrics.Timer terrainTimer = performanceMetrics.time("Terrain gathering"))
			{
				for (Tile terrainTile : terrainTiles)
				{
					collectTerrainTileOccluder(terrainTile, bridgeLinkedTiles.contains(terrainTile));
				}
			}
			try (BillboardPerformanceMetrics.Timer sceneryTimer = performanceMetrics.time("Scenery gathering"))
			{
				for (Tile terrainTile : terrainTiles)
				{
					collectSceneTileObjectOccluders(
						terrainTile,
						quality,
						visitedObjects,
						bridgeLinkedTiles.contains(terrainTile),
						renderedSceneRenderables);
				}
			}
		}
	}

	public List<BillboardOcclusionMask.Occluder> occluders()
	{
		return worldOccluders;
	}

	public Rectangle interestBounds()
	{
		return interestBounds;
	}

	public List<Rectangle> interestRegions()
	{
		return interestRegions;
	}

	public void logDebugStats(BillboardOcclusionMask occlusionMask, Rectangle maskInterestBounds, List<String> depthSamples)
	{
		if (!config.debugLogBillboardOcclusion())
		{
			return;
		}

		int gameCycle = client.getGameCycle();
		if (debugLastLoggedCycle != Integer.MIN_VALUE && gameCycle - debugLastLoggedCycle < 30)
		{
			return;
		}

		debugLastLoggedCycle = gameCycle;
		log.debug(
			"Billboard occlusion quality={} cameraYaw={} cameraPitch={} cameraYawIndex={} cameraPitchIndex={} cameraFp=({},{},{}) sources=terrain,scenery sceneryCandidates={} sceneryAccepted={} sceneryRejectedByFilter={} sceneryBroadPhaseRejected={} sceneryOutsideInterest={} sceneryWithoutParts={} sceneryFaces={} modelFacesInspected={} modelCacheHits={} modelCacheMisses={} fallbackShapeQueries={} terrainTiles={} terrainTilesAccepted={} terrainBroadPhaseRejected={} terrainFaces={} flatTerrainFacesSkipped={} bridgeTerrainTilesSkipped={} bridgeTiles={} bridgeTileSamples={} shapes={} triangleOccluders={} verticalFallbackOccluders={} flatFallbackOccluders={} bridgeFallbacksSuppressed={} bridgeSceneryNotRendered={} behindCameraRejected={} cells={} activeRegions={} acceptedScenery={} rejectedScenery={} preInterest={} maskInterest={} depthSamples={}",
			BillboardOcclusionQuality.normalize(config.billboardOcclusionQuality()),
			client.getCameraYaw(),
			client.getCameraPitch(),
			depthCalculator.cameraYawIndex(),
			depthCalculator.cameraPitchIndex(),
			client.getCameraFpX(),
			client.getCameraFpY(),
			client.getCameraFpZ(),
			debugSceneObjectsConsidered,
			debugSceneObjectsAccepted,
			debugSceneObjectsRejectedByFilter,
			debugSceneObjectsRejectedByBroadPhase,
			debugSceneObjectsSkippedOutsideInterest,
			debugSceneObjectsWithoutParts,
			debugSceneObjectFacesAccepted,
			debugModelFacesInspected,
			debugModelGeometryCacheHits,
			debugModelGeometryCacheMisses,
			debugFallbackShapeQueries,
			debugTerrainTilesConsidered,
			debugTerrainTilesAccepted,
			debugTerrainTilesRejectedByBroadPhase,
			debugTerrainFacesAccepted,
			debugFlatTerrainFacesSkipped,
			debugBridgeTerrainTilesSkipped,
			debugBridgeTilesIncluded,
			debugBridgeTileSamples,
			debugShapesAccepted,
			debugTriangleOccludersAccepted,
			debugVerticalFallbackOccludersAccepted,
			debugFlatFallbackOccludersAccepted,
			debugBridgeFallbacksSuppressed,
			debugBridgeSceneryNotRendered,
			debugOccludersRejectedBehindCamera,
			occlusionMask.coveredCellCount(),
			interestRegions.size(),
			debugAcceptedScenerySamples,
			debugRejectedScenerySamples,
			interestBounds,
			maskInterestBounds,
			depthSamples
		);
	}

	private void clearDebugStats()
	{
		debugShapesAccepted = 0;
		debugTerrainTilesConsidered = 0;
		debugTerrainTilesAccepted = 0;
		debugTerrainFacesAccepted = 0;
		debugFlatTerrainFacesSkipped = 0;
		debugBridgeTerrainTilesSkipped = 0;
		debugBridgeTilesIncluded = 0;
		debugTriangleOccludersAccepted = 0;
		debugVerticalFallbackOccludersAccepted = 0;
		debugFlatFallbackOccludersAccepted = 0;
		debugBridgeFallbacksSuppressed = 0;
		debugBridgeSceneryNotRendered = 0;
		debugOccludersRejectedBehindCamera = 0;
		debugSceneObjectsConsidered = 0;
		debugSceneObjectsRejectedByFilter = 0;
		debugSceneObjectsSkippedOutsideInterest = 0;
		debugSceneObjectsWithoutParts = 0;
		debugSceneObjectsAccepted = 0;
		debugSceneObjectFacesAccepted = 0;
		debugTerrainTilesRejectedByBroadPhase = 0;
		debugSceneObjectsRejectedByBroadPhase = 0;
		debugModelFacesInspected = 0;
		debugModelGeometryCacheHits = 0;
		debugModelGeometryCacheMisses = 0;
		debugFallbackShapeQueries = 0;
		debugAcceptedScenerySamples.clear();
		debugRejectedScenerySamples.clear();
		debugBridgeTileSamples.clear();
	}

	private void collectTerrainCorridorTiles(
		Tile[][][] tiles,
		TraceTarget target,
		BillboardOcclusionQuality quality,
		Set<Long> visitedTileCoordinates,
		Set<Tile> visitedTiles,
		Set<Tile> bridgeLinkedTiles,
		List<Tile> terrainTiles)
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
					collectTerrainTile(tiles, target.plane, centerTileX + offsetX, centerTileY + offsetY, visitedTileCoordinates, visitedTiles, bridgeLinkedTiles, terrainTiles);
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

	private void collectTerrainTile(
		Tile[][][] tiles,
		int plane,
		int tileX,
		int tileY,
		Set<Long> visitedTileCoordinates,
		Set<Tile> visitedTiles,
		Set<Tile> bridgeLinkedTiles,
		List<Tile> terrainTiles)
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
		if (!visitedTileCoordinates.add(key))
		{
			return;
		}

		Tile tile = row[tileY];
		if (tile != null)
		{
			int bridgeTilesAdded = addTileAndBridgeTiles(tile, visitedTiles, terrainTiles, bridgeLinkedTiles);
			if (bridgeTilesAdded > 0)
			{
				debugBridgeTilesIncluded += bridgeTilesAdded;
				addDebugBridgeTileSample(tile.getBridge());
			}
		}
	}

	public static int addTileAndBridgeTiles(Tile tile, Set<Tile> visitedTiles, List<Tile> terrainTiles)
	{
		return addTileAndBridgeTiles(tile, visitedTiles, terrainTiles, null);
	}

	private static int addTileAndBridgeTiles(Tile tile, Set<Tile> visitedTiles, List<Tile> terrainTiles, Set<Tile> bridgeLinkedTiles)
	{
		if (tile == null || visitedTiles == null || terrainTiles == null || !visitedTiles.add(tile))
		{
			return 0;
		}

		terrainTiles.add(tile);
		int bridgeTilesAdded = 0;
		Tile bridge = tile.getBridge();
		while (bridge != null && visitedTiles.add(bridge))
		{
			terrainTiles.add(bridge);
			if (bridgeLinkedTiles != null)
			{
				bridgeLinkedTiles.add(bridge);
			}
			bridgeTilesAdded++;
			bridge = bridge.getBridge();
		}
		return bridgeTilesAdded;
	}

	private void addDebugBridgeTileSample(Tile tile)
	{
		if (!config.debugLogBillboardOcclusion() || tile == null || debugBridgeTileSamples.size() >= DEBUG_OCCLUSION_SAMPLE_LIMIT)
		{
			return;
		}

		LocalPoint localPoint = tile.getLocalLocation();
		debugBridgeTileSamples.add("plane=" + tile.getPlane() + " local=" + (localPoint == null ? "-" : localPoint.getX() + "," + localPoint.getY()));
	}

	private long terrainTileKey(int plane, int tileX, int tileY)
	{
		return ((long) plane << 48) ^ ((long) (tileX & 0xFFFFFF) << 24) ^ (tileY & 0xFFFFFFL);
	}

	private void collectTerrainTileOccluder(
		Tile tile,
		boolean bridgeLinked)
	{
		if (tile == null)
		{
			return;
		}

		LocalPoint localPoint = tile.getLocalLocation();
		if (localPoint == null)
		{
			return;
		}

		if (!bridgeLinked)
		{
			debugTerrainTilesConsidered++;
			int acceptedFacesBefore = debugTerrainFacesAccepted;
			TerrainGeometry geometry;
			try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Terrain geometry lookup/build"))
			{
				geometry = terrainGeometry(tile, localPoint);
			}
			debugFlatTerrainFacesSkipped += geometry.flatTriangleCount;
			boolean intersects;
			try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Terrain broad-phase"))
			{
				intersects = geometry.triangles.size() < 4 || terrainBroadPhaseIntersectsInterest(geometry);
			}
			if (!intersects)
			{
				debugTerrainTilesRejectedByBroadPhase++;
			}
			else
			{
				try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Terrain face projection"))
				{
					for (WorldTriangle triangle : geometry.triangles)
					{
						addTerrainTriangleOccluder(triangle, tile.getPlane());
					}
				}
			}
			if (debugTerrainFacesAccepted > acceptedFacesBefore)
			{
				debugTerrainTilesAccepted++;
			}
		}
		else
		{
			debugBridgeTerrainTilesSkipped++;
		}
	}

	private void collectSceneTileObjectOccluders(
		Tile tile,
		BillboardOcclusionQuality quality,
		Set<TileObject> visitedObjects,
		boolean bridgeLinked,
		Set<Renderable> renderedSceneRenderables)
	{
		if (tile == null)
		{
			return;
		}

		collectSceneTileObjectOccluder(tile.getWallObject(), quality, visitedObjects, bridgeLinked, renderedSceneRenderables);
		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects == null)
		{
			return;
		}

		for (GameObject gameObject : gameObjects)
		{
			collectSceneTileObjectOccluder(gameObject, quality, visitedObjects, bridgeLinked, renderedSceneRenderables);
		}
	}

	private void collectSceneTileObjectOccluder(
		TileObject tileObject,
		BillboardOcclusionQuality quality,
		Set<TileObject> visitedObjects,
		boolean bridgeLinked,
		Set<Renderable> renderedSceneRenderables)
	{
		if (tileObject == null || (!bridgeLinked && !isPlaneEligible(tileObject.getPlane())) || !visitedObjects.add(tileObject))
		{
			return;
		}

		debugSceneObjectsConsidered++;
		boolean isOccluder;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Scenery filtering"))
		{
			isOccluder = BillboardSceneryOcclusionFilter.isOccluder(tileObject);
		}
		if (!isOccluder)
		{
			debugSceneObjectsRejectedByFilter++;
			addDebugScenerySample(debugRejectedScenerySamples, tileObject);
			return;
		}

		ObservedTileObject observed;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Scenery part discovery"))
		{
			observed = ObservedTileObjectBuilder.build(tileObject);
		}
		if (observed == null || observed.parts.isEmpty())
		{
			debugSceneObjectsWithoutParts++;
			return;
		}
		boolean bridgePartRendered;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Bridge renderable check"))
		{
			bridgePartRendered = !bridgeLinked || hasRenderedPart(observed, renderedSceneRenderables);
		}
		if (!bridgePartRendered)
		{
			debugBridgeSceneryNotRendered++;
			return;
		}
		boolean intersects;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Scenery bounds broad-phase"))
		{
			intersects = sceneryBroadPhaseIntersectsInterest(observed);
		}
		if (!intersects)
		{
			debugSceneObjectsRejectedByBroadPhase++;
			return;
		}

		int acceptedFacesBefore = debugSceneObjectFacesAccepted;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Scenery model faces"))
		{
			for (ObjectRenderablePart part : observed.parts)
			{
				collectSceneObjectPartOccluders(part, quality, debugOccluderSource(tileObject, "model"));
			}
		}

		boolean acceptedModelFaces = debugSceneObjectFacesAccepted > acceptedFacesBefore;
		boolean acceptedFallback = false;
		if (!acceptedModelFaces)
		{
			try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Scenery fallback shapes"))
			{
				acceptedFallback = collectSceneObjectFallbackOccluder(tileObject, observed, quality);
			}
		}
		if (acceptedModelFaces || acceptedFallback)
		{
			debugSceneObjectsAccepted++;
			addDebugScenerySample(debugAcceptedScenerySamples, tileObject);
		}
	}

	public static boolean hasRenderedPart(ObservedTileObject observed, Set<Renderable> renderedSceneRenderables)
	{
		if (observed == null || observed.parts == null || renderedSceneRenderables == null || renderedSceneRenderables.isEmpty())
		{
			return false;
		}

		for (ObjectRenderablePart part : observed.parts)
		{
			if (part != null && part.renderable != null && renderedSceneRenderables.contains(part.renderable))
			{
				return true;
			}
		}
		return false;
	}

	private void addDebugScenerySample(List<String> samples, TileObject tileObject)
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
		FallbackDepths depths;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Fallback depth estimation"))
		{
			depths = fallbackDepths(tileObject, observed, quality);
		}
		if (!Double.isFinite(depths.fallbackDepth))
		{
			return false;
		}

		ShapeFallbackResult shapeResult = addSceneryShapeFallbackOccluder(tileObject, observed, depths);
		if (shapeResult.handled)
		{
			return shapeResult.added;
		}

		debugFallbackShapeQueries++;
		Shape clickbox;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Clickbox query"))
		{
			clickbox = tileObject.getClickbox();
		}
		return addFallbackWorldOccluder(clickbox, firstPart(observed), depths.fallbackDepth, debugOccluderSource(tileObject, "fallback-clickbox"));
	}

	private ShapeFallbackResult addSceneryShapeFallbackOccluder(
		TileObject tileObject,
		ObservedTileObject observed,
		FallbackDepths depths)
	{
		if (tileObject instanceof GameObject)
		{
			Shape hull = fallbackShapes(tileObject, observed).first;
			if (!isUsableFallbackShape(hull))
			{
				// The model-face path has already had the opportunity to represent this
				// object. RuneLite's clickbox construction is dramatically more expensive
				// than hull lookup and is not a suitable per-frame occlusion fallback.
				return ShapeFallbackResult.handled(false);
			}
			return ShapeFallbackResult.handled(
				addFallbackWorldOccluder(hull, part(observed, 0), depths.partDepth(0), debugOccluderSource(tileObject, "fallback-hull")));
		}

		if (tileObject instanceof WallObject)
		{
			CachedFallbackShapes shapes = fallbackShapes(tileObject, observed);
			Shape firstHull = shapes.first;
			Shape secondHull = shapes.second;
			boolean hasFirstHull = isUsableFallbackShape(firstHull);
			boolean hasSecondHull = isUsableFallbackShape(secondHull);
			if (!hasFirstHull && !hasSecondHull)
			{
				return ShapeFallbackResult.handled(false);
			}
			boolean addedFirst = hasFirstHull
				&& addFallbackWorldOccluder(firstHull, part(observed, 0), depths.partDepth(0), debugOccluderSource(tileObject, "fallback-hull-1"));
			boolean addedSecond = hasSecondHull
				&& addFallbackWorldOccluder(secondHull, part(observed, 1), depths.partDepth(1), debugOccluderSource(tileObject, "fallback-hull-2"));
			return ShapeFallbackResult.handled(addedFirst || addedSecond);
		}

		return ShapeFallbackResult.unhandled();
	}

	private CachedFallbackShapes fallbackShapes(TileObject tileObject, ObservedTileObject observed)
	{
		int modelSignature = fallbackModelSignature(observed);
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Fallback hull cache lookup"))
		{
			CachedFallbackShapes cached = fallbackShapeCache.get(tileObject);
			if (cached != null && cached.modelSignature == modelSignature)
			{
				return cached;
			}
		}

		Shape first = null;
		Shape second = null;
		if (tileObject instanceof GameObject)
		{
			debugFallbackShapeQueries++;
			try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Game object convex hull query"))
			{
				first = ((GameObject) tileObject).getConvexHull();
			}
		}
		else if (tileObject instanceof WallObject)
		{
			debugFallbackShapeQueries += 2;
			WallObject wallObject = (WallObject) tileObject;
			try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Wall convex hull queries"))
			{
				first = wallObject.getConvexHull();
				second = wallObject.getConvexHull2();
			}
		}

		CachedFallbackShapes shapes = new CachedFallbackShapes(modelSignature, first, second);
		if (fallbackShapeCache.size() >= MAX_FALLBACK_SHAPE_CACHE_SIZE)
		{
			fallbackShapeCache.clear();
		}
		fallbackShapeCache.put(tileObject, shapes);
		return shapes;
	}

	private int fallbackModelSignature(ObservedTileObject observed)
	{
		int signature = 1;
		if (observed == null)
		{
			return signature;
		}
		for (ObjectRenderablePart part : observed.parts)
		{
			Model model = part != null && part.renderable != null ? part.renderable.getModel() : null;
			signature = (31 * signature) + System.identityHashCode(model);
		}
		return signature;
	}

	private void refreshFallbackShapeCacheCameraState()
	{
		CameraProjectionState current = new CameraProjectionState(
			Float.floatToIntBits(client.getCameraFpX()),
			Float.floatToIntBits(client.getCameraFpY()),
			Float.floatToIntBits(client.getCameraFpZ()),
			client.getCameraPitch(),
			client.getCameraYaw(),
			client.get3dZoom(),
			client.getViewportXOffset(),
			client.getViewportYOffset(),
			client.getViewportWidth(),
			client.getViewportHeight());
		if (!current.equals(fallbackShapeCameraState))
		{
			fallbackShapeCache.clear();
			fallbackShapeCameraState = current;
		}
	}

	static boolean isUsableFallbackShape(Shape shape)
	{
		return shape != null && !shape.getBounds().isEmpty();
	}

	private ObjectRenderablePart firstPart(ObservedTileObject observed)
	{
		return part(observed, 0);
	}

	private ObjectRenderablePart part(ObservedTileObject observed, int partIndex)
	{
		return observed != null && partIndex >= 0 && partIndex < observed.parts.size()
			? observed.parts.get(partIndex)
			: null;
	}

	private boolean addWorldOccluder(Shape shape, double depth, String source)
	{
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Occluder shape creation"))
		{
			if (shape == null || shape.getBounds().isEmpty())
			{
				return false;
			}
			if (!isForwardOccluderDepth(depth))
			{
				debugOccludersRejectedBehindCamera++;
				return false;
			}

			if (interestBounds != null && !intersectsInterest(shape.getBounds()))
			{
				return false;
			}

			worldOccluders.add(new BillboardOcclusionMask.Occluder(shape, (float) depth, source));
			debugShapesAccepted++;
			debugFlatFallbackOccludersAccepted++;
			return true;
		}
	}

	private String debugOccluderSource(TileObject tileObject, String sourceType)
	{
		if (tileObject == null || !config.debugLogBillboardOcclusion())
		{
			return sourceType;
		}

		LocalPoint localPoint = tileObject.getLocalLocation();
		return sourceType + " id=" + tileObject.getId() + " plane=" + tileObject.getPlane()
			+ " local=" + (localPoint == null ? "-" : localPoint.getX() + "," + localPoint.getY());
	}

	private boolean sceneryIntersectsInterest(TileObject tileObject)
	{
		if (tileObject instanceof WallObject)
		{
			WallObject wallObject = (WallObject) tileObject;
			return intersectsInterest(wallObject.getConvexHull())
				|| intersectsInterest(wallObject.getConvexHull2())
				|| intersectsInterest(tileObject.getClickbox());
		}

		if (tileObject instanceof GameObject)
		{
			return intersectsInterest(((GameObject) tileObject).getConvexHull())
				|| intersectsInterest(tileObject.getClickbox());
		}

		return false;
	}

	private boolean sceneryBroadPhaseIntersectsInterest(ObservedTileObject observed)
	{
		boolean hadUsableBounds = false;
		for (ObjectRenderablePart part : observed.parts)
		{
			if (part == null || part.renderable == null || part.localPoint == null)
			{
				continue;
			}

			Model model;
			try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Renderable model retrieval"))
			{
				model = part.renderable.getModel();
			}
			ModelGeometry geometry;
			try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Model geometry cache"))
			{
				geometry = modelGeometry(model);
			}
			if (geometry == null || !geometry.hasBounds)
			{
				continue;
			}
			hadUsableBounds = true;
			boolean intersects;
			try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Model bounds projection"))
			{
				intersects = projectedModelBoundsIntersectsInterest(part, geometry);
			}
			if (intersects)
			{
				return true;
			}
		}

		// Unknown or unavailable model bounds must remain eligible so fallbacks preserve
		// the old behavior.
		return !hadUsableBounds;
	}

	private boolean terrainBroadPhaseIntersectsInterest(TerrainGeometry geometry)
	{
		if (!geometry.hasBounds)
		{
			return true;
		}

		int minScreenX = Integer.MAX_VALUE;
		int minScreenY = Integer.MAX_VALUE;
		int maxScreenX = Integer.MIN_VALUE;
		int maxScreenY = Integer.MIN_VALUE;
		for (int xIndex = 0; xIndex < 2; xIndex++)
		{
			int localX = xIndex == 0 ? geometry.minX : geometry.maxX;
			for (int yIndex = 0; yIndex < 2; yIndex++)
			{
				int localY = yIndex == 0 ? geometry.minY : geometry.maxY;
				for (int zIndex = 0; zIndex < 2; zIndex++)
				{
					int height = zIndex == 0 ? geometry.minHeight : geometry.maxHeight;
					double depth = depthCalculator.cameraForwardDepth(localX, localY, height);
					if (!isForwardOccluderDepth(depth))
					{
						return true;
					}
					Point point = Perspective.localToCanvas(client, localX, localY, height);
					if (point == null)
					{
						return true;
					}
					minScreenX = Math.min(minScreenX, point.getX());
					minScreenY = Math.min(minScreenY, point.getY());
					maxScreenX = Math.max(maxScreenX, point.getX());
					maxScreenY = Math.max(maxScreenY, point.getY());
				}
			}
		}
		return intersectsInterest(new Rectangle(
			minScreenX, minScreenY,
			Math.max(1, maxScreenX - minScreenX + 1),
			Math.max(1, maxScreenY - minScreenY + 1)));
	}

	private boolean projectedModelBoundsIntersectsInterest(ObjectRenderablePart part, ModelGeometry geometry)
	{
		int baseX = part.localPoint.getX();
		int baseY = part.localPoint.getY();
		int baseHeight = tileHeightAt(baseX, baseY, part.plane);
		int minScreenX = Integer.MAX_VALUE;
		int minScreenY = Integer.MAX_VALUE;
		int maxScreenX = Integer.MIN_VALUE;
		int maxScreenY = Integer.MIN_VALUE;

		for (int xIndex = 0; xIndex < 2; xIndex++)
		{
			int localX = baseX + Math.round(xIndex == 0 ? geometry.minX : geometry.maxX);
			for (int zIndex = 0; zIndex < 2; zIndex++)
			{
				int localY = baseY + Math.round(zIndex == 0 ? geometry.minZ : geometry.maxZ);
				for (int yIndex = 0; yIndex < 2; yIndex++)
				{
					int height = baseHeight + Math.round(Math.max(0.0f, -(yIndex == 0 ? geometry.minY : geometry.maxY)));
					double depth = depthCalculator.cameraForwardDepth(localX, localY, height);
					if (!isForwardOccluderDepth(depth))
					{
						// A box crossing the near plane can project beyond its corner bounds.
						return true;
					}
					Point point = Perspective.localToCanvas(client, localX, localY, height);
					if (point == null)
					{
						return true;
					}
					minScreenX = Math.min(minScreenX, point.getX());
					minScreenY = Math.min(minScreenY, point.getY());
					maxScreenX = Math.max(maxScreenX, point.getX());
					maxScreenY = Math.max(maxScreenY, point.getY());
				}
			}
		}

		return minScreenX == Integer.MAX_VALUE || intersectsInterest(
			new Rectangle(minScreenX, minScreenY, Math.max(1, maxScreenX - minScreenX + 1), Math.max(1, maxScreenY - minScreenY + 1)));
	}

	private boolean intersectsInterest(Shape shape)
	{
		return shape != null && intersectsInterest(shape.getBounds());
	}

	private boolean intersectsInterest(Rectangle bounds)
	{
		if (bounds == null || bounds.isEmpty())
		{
			return false;
		}

		if (interestRegions.isEmpty())
		{
			return interestBounds != null && bounds.intersects(interestBounds);
		}

		for (Rectangle region : interestRegions)
		{
			if (region != null && bounds.intersects(region))
			{
				return true;
			}
		}

		return false;
	}

	private void collectSceneObjectPartOccluders(ObjectRenderablePart part, BillboardOcclusionQuality quality, String source)
	{
		if (part == null || part.renderable == null || part.localPoint == null)
		{
			return;
		}

		Model model;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Renderable model retrieval"))
		{
			model = part.renderable.getModel();
		}
		ModelGeometry geometry;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Model geometry cache"))
		{
			geometry = modelGeometry(model);
		}
		if (geometry == null || geometry.vertexCount <= 0 || geometry.faceCount <= 0)
		{
			return;
		}

		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Model face traversal"))
		{
			int stride = objectFaceStride(quality);
			for (int face = 0; face < geometry.faceCount; face += stride)
			{
				debugModelFacesInspected++;
				int a = geometry.faceIndices1[face];
				int b = geometry.faceIndices2[face];
				int c = geometry.faceIndices3[face];
				if (a < 0 || b < 0 || c < 0 || a >= geometry.vertexCount || b >= geometry.vertexCount || c >= geometry.vertexCount)
				{
					continue;
				}

				addSceneObjectTriangleOccluder(
					part,
					geometry.verticesX[a], geometry.verticesY[a], geometry.verticesZ[a],
					geometry.verticesX[b], geometry.verticesY[b], geometry.verticesZ[b],
					geometry.verticesX[c], geometry.verticesY[c], geometry.verticesZ[c],
					source
				);
			}
		}
	}

	private ModelGeometry modelGeometry(Model model)
	{
		if (model == null)
		{
			return null;
		}

		ModelGeometry cached = modelGeometryCache.get(model);
		if (cached != null && cached.matches(model))
		{
			debugModelGeometryCacheHits++;
			return cached;
		}

		debugModelGeometryCacheMisses++;
		ModelGeometry geometry = ModelGeometry.from(model);
		if (modelGeometryCache.size() >= MAX_MODEL_GEOMETRY_CACHE_SIZE)
		{
			modelGeometryCache.clear();
		}
		modelGeometryCache.put(model, geometry);
		return geometry;
	}

	private int objectFaceStride(BillboardOcclusionQuality quality)
	{
		switch (quality)
		{
			case MAX:
			case ULTRA:
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
		float vz2,
		String source)
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
		addSceneObjectTriangleOccluder(p0, p1, p2, source);
	}

	private WorldVertex sceneObjectVertex(ObjectRenderablePart part, float vertexX, float vertexY, float vertexZ)
	{
		int localX = (int) Math.round(part.localPoint.getX() + vertexX);
		int localY = (int) Math.round(part.localPoint.getY() + vertexZ);
		int height = tileHeightAt(localX, localY, part.plane) + (int) Math.round(Math.max(0.0f, -vertexY));
		return new WorldVertex(localX, localY, height);
	}

	private void addSceneObjectTriangleOccluder(WorldVertex v0, WorldVertex v1, WorldVertex v2, String source)
	{
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Occluder shape creation"))
		{
			double depth0 = depthCalculator.cameraForwardDepth(v0.localX, v0.localY, v0.height);
			double depth1 = depthCalculator.cameraForwardDepth(v1.localX, v1.localY, v1.height);
			double depth2 = depthCalculator.cameraForwardDepth(v2.localX, v2.localY, v2.height);
			if (!hasForwardVertex(depth0, depth1, depth2))
			{
				debugOccludersRejectedBehindCamera++;
				return;
			}

			Point p0 = Perspective.localToCanvas(client, v0.localX, v0.localY, v0.height);
			Point p1 = Perspective.localToCanvas(client, v1.localX, v1.localY, v1.height);
			Point p2 = Perspective.localToCanvas(client, v2.localX, v2.localY, v2.height);
			if (p0 == null || p1 == null || p2 == null)
			{
				return;
			}

			if (!intersectsInterest(triangleBounds(p0, p1, p2)))
			{
				return;
			}

			BillboardOcclusionMask.Occluder occluder = BillboardOcclusionMask.Occluder.triangle(
				p0.getX(), p0.getY(), (float) depth0,
				p1.getX(), p1.getY(), (float) depth1,
				p2.getX(), p2.getY(), (float) depth2,
				source
			);
			if (occluder == null)
			{
				return;
			}

			worldOccluders.add(occluder);
			debugSceneObjectFacesAccepted++;
			debugShapesAccepted++;
			debugTriangleOccludersAccepted++;
		}
	}

	private TerrainGeometry terrainGeometry(Tile tile, LocalPoint localPoint)
	{
		SceneTileModel model = tile.getSceneTileModel();
		SceneTilePaint paint = tile.getSceneTilePaint();
		TerrainGeometry cached = terrainGeometryCache.get(tile);
		if (cached != null && cached.matches(model, paint))
		{
			return cached;
		}

		TerrainGeometry geometry = model != null
			? buildTerrainModelGeometry(model)
			: buildTerrainPaintGeometry(localPoint, tile.getPlane(), paint);
		terrainGeometryCache.put(tile, geometry);
		return geometry;
	}

	private TerrainGeometry buildTerrainPaintGeometry(LocalPoint localPoint, int plane, SceneTilePaint paint)
	{
		if (paint == null)
		{
			return TerrainGeometry.empty(null, null);
		}

		int size = BillboardConstants.LOCAL_TILE_SIZE;
		int halfSize = size / 2;
		int x = localPoint.getX() - halfSize;
		int y = localPoint.getY() - halfSize;
		int swHeight = tileHeightAt(x, y, plane);
		int seHeight = tileHeightAt(x + size, y, plane);
		int neHeight = tileHeightAt(x + size, y + size, plane);
		int nwHeight = tileHeightAt(x, y + size, plane);
		List<WorldTriangle> triangles = new ArrayList<>(2);
		int flatTriangleCount = addUnevenTriangle(triangles, x, y, swHeight, x + size, y, seHeight, x + size, y + size, neHeight);
		flatTriangleCount += addUnevenTriangle(triangles, x, y, swHeight, x + size, y + size, neHeight, x, y + size, nwHeight);
		return new TerrainGeometry(null, paint, triangles, flatTriangleCount);
	}

	private TerrainGeometry buildTerrainModelGeometry(SceneTileModel model)
	{
		int[] vertexX = model.getVertexX();
		int[] vertexY = model.getVertexY();
		int[] vertexZ = model.getVertexZ();
		int[] faceX = model.getFaceX();
		int[] faceY = model.getFaceY();
		int[] faceZ = model.getFaceZ();
		if (vertexX == null || vertexY == null || vertexZ == null || faceX == null || faceY == null || faceZ == null)
		{
			return TerrainGeometry.empty(model, null);
		}

		int faceCount = Math.min(faceX.length, Math.min(faceY.length, faceZ.length));
		int vertexCount = Math.min(vertexX.length, Math.min(vertexY.length, vertexZ.length));
		List<WorldTriangle> triangles = new ArrayList<>(faceCount);
		int flatTriangleCount = 0;
		for (int face = 0; face < faceCount; face++)
		{
			int a = faceX[face];
			int b = faceY[face];
			int c = faceZ[face];
			if (a < 0 || b < 0 || c < 0 || a >= vertexCount || b >= vertexCount || c >= vertexCount)
			{
				continue;
			}

			flatTriangleCount += addUnevenTriangle(
				triangles,
				vertexX[a], vertexZ[a], vertexY[a],
				vertexX[b], vertexZ[b], vertexY[b],
				vertexX[c], vertexZ[c], vertexY[c]
			);
		}
		return new TerrainGeometry(model, null, triangles, flatTriangleCount);
	}

	private static int addUnevenTriangle(
		List<WorldTriangle> triangles,
		int x0,
		int y0,
		int z0,
		int x1,
		int y1,
		int z1,
		int x2,
		int y2,
		int z2)
	{
		if (!isUnevenTerrainTriangle(z0, z1, z2))
		{
			return 1;
		}

		triangles.add(new WorldTriangle(x0, y0, z0, x1, y1, z1, x2, y2, z2));
		return 0;
	}

	private void addTerrainTriangleOccluder(WorldTriangle triangle, int plane)
	{
		int x0 = triangle.x0;
		int y0 = triangle.y0;
		int z0 = triangle.z0;
		int x1 = triangle.x1;
		int y1 = triangle.y1;
		int z1 = triangle.z1;
		int x2 = triangle.x2;
		int y2 = triangle.y2;
		int z2 = triangle.z2;

		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Occluder shape creation"))
		{
			double rawDepth0 = depthCalculator.cameraForwardDepth(x0, y0, z0);
			double rawDepth1 = depthCalculator.cameraForwardDepth(x1, y1, z1);
			double rawDepth2 = depthCalculator.cameraForwardDepth(x2, y2, z2);
			if (!hasForwardVertex(rawDepth0, rawDepth1, rawDepth2))
			{
				debugOccludersRejectedBehindCamera++;
				return;
			}

			Point p0 = Perspective.localToCanvas(client, x0, y0, z0);
			Point p1 = Perspective.localToCanvas(client, x1, y1, z1);
			Point p2 = Perspective.localToCanvas(client, x2, y2, z2);
			if (p0 == null || p1 == null || p2 == null)
			{
				return;
			}

			if (!intersectsInterest(triangleBounds(p0, p1, p2)))
			{
				return;
			}

			double depth0 = rawDepth0 + TERRAIN_OCCLUSION_DEPTH_BIAS;
			double depth1 = rawDepth1 + TERRAIN_OCCLUSION_DEPTH_BIAS;
			double depth2 = rawDepth2 + TERRAIN_OCCLUSION_DEPTH_BIAS;
			BillboardOcclusionMask.Occluder occluder = BillboardOcclusionMask.Occluder.triangle(
				p0.getX(), p0.getY(), (float) depth0,
				p1.getX(), p1.getY(), (float) depth1,
				p2.getX(), p2.getY(), (float) depth2,
				terrainOccluderSource(plane)
			);
			if (occluder == null)
			{
				return;
			}

			worldOccluders.add(occluder);
			debugTerrainFacesAccepted++;
			debugShapesAccepted++;
			debugTriangleOccludersAccepted++;
		}
	}

	public static boolean isUnevenTerrainTriangle(int height0, int height1, int height2)
	{
		return height0 != height1 || height1 != height2;
	}

	private static String terrainOccluderSource(int plane)
	{
		return plane >= 0 && plane < TERRAIN_OCCLUDER_SOURCES.length
			? TERRAIN_OCCLUDER_SOURCES[plane]
			: "terrain";
	}

	public static Rectangle triangleBounds(Point p0, Point p1, Point p2)
	{
		int minX = Math.min(p0.getX(), Math.min(p1.getX(), p2.getX()));
		int minY = Math.min(p0.getY(), Math.min(p1.getY(), p2.getY()));
		int maxX = Math.max(p0.getX(), Math.max(p1.getX(), p2.getX()));
		int maxY = Math.max(p0.getY(), Math.max(p1.getY(), p2.getY()));
		return new Rectangle(minX, minY, Math.max(1, maxX - minX + 1), Math.max(1, maxY - minY + 1));
	}

	public static boolean hasForwardVertex(double depth0, double depth1, double depth2)
	{
		return isForwardOccluderDepth(depth0)
			|| isForwardOccluderDepth(depth1)
			|| isForwardOccluderDepth(depth2);
	}

	public static boolean isForwardOccluderDepth(double depth)
	{
		return Double.isFinite(depth) && depth > BillboardConstants.MIN_FORWARD_VISIBLE_DEPTH;
	}

	private FallbackDepths fallbackDepths(TileObject tileObject, ObservedTileObject observed, BillboardOcclusionQuality quality)
	{
		LocalPoint localPoint = tileObject.getLocalLocation();
		if (localPoint == null)
		{
			return FallbackDepths.invalid();
		}

		double furthest = Double.NEGATIVE_INFINITY;
		double[] partDepths = observed != null ? new double[observed.parts.size()] : new double[0];
		if (observed != null && !observed.parts.isEmpty())
		{
			for (int partIndex = 0; partIndex < observed.parts.size(); partIndex++)
			{
				double depth = conservativeModelVertexDepth(observed.parts.get(partIndex), quality.vertexStride());
				partDepths[partIndex] = depth;
				if (Double.isFinite(depth))
				{
					furthest = Math.max(furthest, depth);
				}
			}
		}

		double fallbackDepth = Double.isFinite(furthest)
			? furthest
			: depthCalculator.cameraForwardDepth(localPoint, tileObject.getPlane(), 0.0d);
		return new FallbackDepths(fallbackDepth, partDepths);
	}

	private double conservativeModelVertexDepth(ObjectRenderablePart part, int vertexStride)
	{
		if (part == null || part.renderable == null || part.localPoint == null)
		{
			return Double.NaN;
		}

		Model model;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Fallback model retrieval"))
		{
			model = part.renderable.getModel();
		}
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
		// A fallback hull has screen-space coverage but no per-pixel world position. Using
		// its nearest vertex as a constant depth assigns the closest corner to the whole
		// shape and clips billboards that are actually in front of the wall. Prefer the
		// furthest sampled vertex: ambiguous intersections remain visible, while objects
		// definitely behind the complete hull are still occluded.
		double furthest = Double.NEGATIVE_INFINITY;
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Fallback vertex depth sampling"))
		{
			for (int i = 0; i < vertexCount; i += stride)
			{
				double depth = modelVertexDepth(part.localPoint, part.plane, verticesX[i], verticesY[i], verticesZ[i]);
				if (Double.isFinite(depth))
				{
					furthest = Math.max(furthest, depth);
				}
			}

			if (stride > 1 && vertexCount > 0)
			{
				double depth = modelVertexDepth(part.localPoint, part.plane, verticesX[vertexCount - 1], verticesY[vertexCount - 1], verticesZ[vertexCount - 1]);
				if (Double.isFinite(depth))
				{
					furthest = Math.max(furthest, depth);
				}
			}
		}

		return Double.isFinite(furthest)
			? furthest
			: depthCalculator.cameraForwardDepth(part.localPoint, part.plane, Math.max(0, part.renderable.getModelHeight() / 2.0d));
	}

	private boolean addFallbackWorldOccluder(Shape shape, ObjectRenderablePart part, double flatDepth, String source)
	{
		try (BillboardPerformanceMetrics.Timer ignored = performanceMetrics.time("Fallback occluder preparation"))
		{
			return addFallbackWorldOccluderInternal(shape, part, flatDepth, source);
		}
	}

	private boolean addFallbackWorldOccluderInternal(Shape shape, ObjectRenderablePart part, double flatDepth, String source)
	{
		if (shape == null || shape.getBounds().isEmpty() || part == null || part.localPoint == null || part.renderable == null)
		{
			return addWorldOccluder(shape, flatDepth, source);
		}

		int modelHeight = Math.max(0, part.renderable.getModelHeight());
		int baseHeight = tileHeightAt(part.localPoint.getX(), part.localPoint.getY(), part.plane);
		Point basePoint = Perspective.localToCanvas(client, part.localPoint, part.plane, 0);
		Point topPoint = Perspective.localToCanvas(client, part.localPoint, part.plane, modelHeight);
		double baseDepth = depthCalculator.cameraForwardDepth(part.localPoint.getX(), part.localPoint.getY(), baseHeight);
		double topDepth = depthCalculator.cameraForwardDepth(part.localPoint.getX(), part.localPoint.getY(), baseHeight + modelHeight);
		String verticalSource = config.debugLogBillboardOcclusion() && basePoint != null && topPoint != null
			? source + " verticalBase=" + basePoint.getY() + ":" + Math.round(baseDepth)
				+ " verticalTop=" + topPoint.getY() + ":" + Math.round(topDepth)
			: source;
		BillboardOcclusionMask.Occluder occluder = basePoint != null && topPoint != null
			? BillboardOcclusionMask.Occluder.vertical(
				shape,
				basePoint.getY(), (float) baseDepth,
				topPoint.getY(), (float) topDepth,
				verticalSource)
			: null;
		if (occluder == null || !isForwardOccluderDepth(baseDepth) || !isForwardOccluderDepth(topDepth))
		{
			return addWorldOccluder(shape, flatDepth, source);
		}
		if (interestBounds != null && !intersectsInterest(shape.getBounds()))
		{
			return false;
		}

		worldOccluders.add(occluder);
		debugShapesAccepted++;
		debugVerticalFallbackOccludersAccepted++;
		return true;
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

	private int tileHeightAt(int localX, int localY, int plane)
	{
		return Perspective.getTileHeight(client, new LocalPoint(localX, localY), plane);
	}

	private int occlusionInterestMargin()
	{
		BillboardOcclusionQuality quality = BillboardOcclusionQuality.normalize(config.billboardOcclusionQuality());
		return Math.max(0, quality.sampleStep());
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

	private static final class TerrainGeometry
	{
		private final SceneTileModel model;
		private final SceneTilePaint paint;
		private final List<WorldTriangle> triangles;
		private final int flatTriangleCount;
		private final boolean hasBounds;
		private final int minX;
		private final int maxX;
		private final int minY;
		private final int maxY;
		private final int minHeight;
		private final int maxHeight;

		private TerrainGeometry(SceneTileModel model, SceneTilePaint paint, List<WorldTriangle> triangles, int flatTriangleCount)
		{
			this.model = model;
			this.paint = paint;
			this.triangles = triangles;
			this.flatTriangleCount = flatTriangleCount;
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minY = Integer.MAX_VALUE;
			int maxY = Integer.MIN_VALUE;
			int minHeight = Integer.MAX_VALUE;
			int maxHeight = Integer.MIN_VALUE;
			for (WorldTriangle triangle : triangles)
			{
				minX = Math.min(minX, Math.min(triangle.x0, Math.min(triangle.x1, triangle.x2)));
				maxX = Math.max(maxX, Math.max(triangle.x0, Math.max(triangle.x1, triangle.x2)));
				minY = Math.min(minY, Math.min(triangle.y0, Math.min(triangle.y1, triangle.y2)));
				maxY = Math.max(maxY, Math.max(triangle.y0, Math.max(triangle.y1, triangle.y2)));
				minHeight = Math.min(minHeight, Math.min(triangle.z0, Math.min(triangle.z1, triangle.z2)));
				maxHeight = Math.max(maxHeight, Math.max(triangle.z0, Math.max(triangle.z1, triangle.z2)));
			}
			this.hasBounds = minX != Integer.MAX_VALUE;
			this.minX = minX;
			this.maxX = maxX;
			this.minY = minY;
			this.maxY = maxY;
			this.minHeight = minHeight;
			this.maxHeight = maxHeight;
		}

		private static TerrainGeometry empty(SceneTileModel model, SceneTilePaint paint)
		{
			return new TerrainGeometry(model, paint, Collections.emptyList(), 0);
		}

		private boolean matches(SceneTileModel model, SceneTilePaint paint)
		{
			return this.model == model && this.paint == paint;
		}
	}

	private static final class FallbackDepths
	{
		private final double fallbackDepth;
		private final double[] partDepths;

		private FallbackDepths(double fallbackDepth, double[] partDepths)
		{
			this.fallbackDepth = fallbackDepth;
			this.partDepths = partDepths;
		}

		private static FallbackDepths invalid()
		{
			return new FallbackDepths(Double.NaN, new double[0]);
		}

		private double partDepth(int partIndex)
		{
			if (partIndex < 0 || partIndex >= partDepths.length || !Double.isFinite(partDepths[partIndex]))
			{
				return fallbackDepth;
			}
			return partDepths[partIndex];
		}
	}

	private static final class ShapeFallbackResult
	{
		private static final ShapeFallbackResult UNHANDLED = new ShapeFallbackResult(false, false);
		private final boolean handled;
		private final boolean added;

		private ShapeFallbackResult(boolean handled, boolean added)
		{
			this.handled = handled;
			this.added = added;
		}

		private static ShapeFallbackResult unhandled()
		{
			return UNHANDLED;
		}

		private static ShapeFallbackResult handled(boolean added)
		{
			return new ShapeFallbackResult(true, added);
		}
	}

	private static final class CachedFallbackShapes
	{
		private final int modelSignature;
		private final Shape first;
		private final Shape second;

		private CachedFallbackShapes(int modelSignature, Shape first, Shape second)
		{
			this.modelSignature = modelSignature;
			this.first = first;
			this.second = second;
		}
	}

	private static final class CameraProjectionState
	{
		private final int cameraX;
		private final int cameraY;
		private final int cameraZ;
		private final int pitch;
		private final int yaw;
		private final int zoom;
		private final int viewportX;
		private final int viewportY;
		private final int viewportWidth;
		private final int viewportHeight;

		private CameraProjectionState(
			int cameraX, int cameraY, int cameraZ, int pitch, int yaw, int zoom,
			int viewportX, int viewportY, int viewportWidth, int viewportHeight)
		{
			this.cameraX = cameraX;
			this.cameraY = cameraY;
			this.cameraZ = cameraZ;
			this.pitch = pitch;
			this.yaw = yaw;
			this.zoom = zoom;
			this.viewportX = viewportX;
			this.viewportY = viewportY;
			this.viewportWidth = viewportWidth;
			this.viewportHeight = viewportHeight;
		}

		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof CameraProjectionState))
			{
				return false;
			}
			CameraProjectionState state = (CameraProjectionState) other;
			return cameraX == state.cameraX
				&& cameraY == state.cameraY
				&& cameraZ == state.cameraZ
				&& pitch == state.pitch
				&& yaw == state.yaw
				&& zoom == state.zoom
				&& viewportX == state.viewportX
				&& viewportY == state.viewportY
				&& viewportWidth == state.viewportWidth
				&& viewportHeight == state.viewportHeight;
		}

		@Override
		public int hashCode()
		{
			int result = cameraX;
			result = (31 * result) + cameraY;
			result = (31 * result) + cameraZ;
			result = (31 * result) + pitch;
			result = (31 * result) + yaw;
			result = (31 * result) + zoom;
			result = (31 * result) + viewportX;
			result = (31 * result) + viewportY;
			result = (31 * result) + viewportWidth;
			return (31 * result) + viewportHeight;
		}
	}

	static final class ModelGeometry
	{
		private final float[] verticesX;
		private final float[] verticesY;
		private final float[] verticesZ;
		private final int[] faceIndices1;
		private final int[] faceIndices2;
		private final int[] faceIndices3;
		final int vertexCount;
		final int faceCount;
		final boolean hasBounds;
		final float minX;
		final float maxX;
		final float minY;
		final float maxY;
		final float minZ;
		final float maxZ;

		private ModelGeometry(
			float[] verticesX, float[] verticesY, float[] verticesZ,
			int[] faceIndices1, int[] faceIndices2, int[] faceIndices3,
			int vertexCount, int faceCount, boolean hasBounds,
			float minX, float maxX, float minY, float maxY, float minZ, float maxZ)
		{
			this.verticesX = verticesX;
			this.verticesY = verticesY;
			this.verticesZ = verticesZ;
			this.faceIndices1 = faceIndices1;
			this.faceIndices2 = faceIndices2;
			this.faceIndices3 = faceIndices3;
			this.vertexCount = vertexCount;
			this.faceCount = faceCount;
			this.hasBounds = hasBounds;
			this.minX = minX;
			this.maxX = maxX;
			this.minY = minY;
			this.maxY = maxY;
			this.minZ = minZ;
			this.maxZ = maxZ;
		}

		static ModelGeometry from(Model model)
		{
			float[] x = model.getVerticesX();
			float[] y = model.getVerticesY();
			float[] z = model.getVerticesZ();
			int[] f1 = model.getFaceIndices1();
			int[] f2 = model.getFaceIndices2();
			int[] f3 = model.getFaceIndices3();
			int vertexCount = x == null || y == null || z == null
				? 0
				: Math.min(model.getVerticesCount(), Math.min(x.length, Math.min(y.length, z.length)));
			int faceCount = f1 == null || f2 == null || f3 == null
				? 0
				: Math.min(model.getFaceCount(), Math.min(f1.length, Math.min(f2.length, f3.length)));
			float minX = Float.POSITIVE_INFINITY;
			float maxX = Float.NEGATIVE_INFINITY;
			float minY = Float.POSITIVE_INFINITY;
			float maxY = Float.NEGATIVE_INFINITY;
			float minZ = Float.POSITIVE_INFINITY;
			float maxZ = Float.NEGATIVE_INFINITY;
			for (int i = 0; i < vertexCount; i++)
			{
				if (!Float.isFinite(x[i]) || !Float.isFinite(y[i]) || !Float.isFinite(z[i]))
				{
					continue;
				}
				minX = Math.min(minX, x[i]);
				maxX = Math.max(maxX, x[i]);
				minY = Math.min(minY, y[i]);
				maxY = Math.max(maxY, y[i]);
				minZ = Math.min(minZ, z[i]);
				maxZ = Math.max(maxZ, z[i]);
			}
			boolean hasBounds = Float.isFinite(minX) && Float.isFinite(maxX)
				&& Float.isFinite(minY) && Float.isFinite(maxY)
				&& Float.isFinite(minZ) && Float.isFinite(maxZ);
			return new ModelGeometry(
				x, y, z, f1, f2, f3, vertexCount, faceCount, hasBounds,
				minX, maxX, minY, maxY, minZ, maxZ);
		}

		private boolean matches(Model model)
		{
			return verticesX == model.getVerticesX()
				&& verticesY == model.getVerticesY()
				&& verticesZ == model.getVerticesZ()
				&& faceIndices1 == model.getFaceIndices1()
				&& faceIndices2 == model.getFaceIndices2()
				&& faceIndices3 == model.getFaceIndices3()
				&& vertexCount == model.getVerticesCount()
				&& faceCount == model.getFaceCount();
		}
	}

	private static final class WorldTriangle
	{
		private final int x0;
		private final int y0;
		private final int z0;
		private final int x1;
		private final int y1;
		private final int z1;
		private final int x2;
		private final int y2;
		private final int z2;

		private WorldTriangle(int x0, int y0, int z0, int x1, int y1, int z1, int x2, int y2, int z2)
		{
			this.x0 = x0;
			this.y0 = y0;
			this.z0 = z0;
			this.x1 = x1;
			this.y1 = y1;
			this.z1 = z1;
			this.x2 = x2;
			this.y2 = y2;
			this.z2 = z2;
		}
	}

	public static final class TraceTarget
	{
		final LocalPoint localPoint;
		final int plane;

		public TraceTarget(LocalPoint localPoint, int plane)
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
