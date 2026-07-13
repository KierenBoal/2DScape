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
			for (Tile terrainTile : terrainTiles)
			{
				boolean bridgeLinked = bridgeLinkedTiles.contains(terrainTile);
				collectTerrainTileOccluder(terrainTile, quality, visitedObjects, bridgeLinked, renderedSceneRenderables);
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
			"Billboard occlusion quality={} cameraYaw={} cameraPitch={} cameraYawIndex={} cameraPitchIndex={} cameraFp=({},{},{}) sources=terrain,scenery sceneryCandidates={} sceneryAccepted={} sceneryRejectedByFilter={} sceneryOutsideInterest={} sceneryWithoutParts={} sceneryFaces={} terrainTiles={} terrainTilesAccepted={} terrainFaces={} flatTerrainFacesSkipped={} bridgeTerrainTilesSkipped={} bridgeTiles={} bridgeTileSamples={} shapes={} triangleOccluders={} verticalFallbackOccluders={} flatFallbackOccluders={} bridgeFallbacksSuppressed={} bridgeSceneryNotRendered={} behindCameraRejected={} cells={} activeRegions={} acceptedScenery={} rejectedScenery={} preInterest={} maskInterest={} depthSamples={}",
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
			debugSceneObjectsSkippedOutsideInterest,
			debugSceneObjectsWithoutParts,
			debugSceneObjectFacesAccepted,
			debugTerrainTilesConsidered,
			debugTerrainTilesAccepted,
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
		BillboardOcclusionQuality quality,
		Set<TileObject> visitedObjects,
		boolean bridgeLinked,
		Set<Renderable> renderedSceneRenderables)
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
			SceneTileModel model = tile.getSceneTileModel();
			int acceptedFacesBefore = debugTerrainFacesAccepted;
			if (model != null)
			{
				collectTerrainModelOccluders(model, tile.getPlane());
				if (debugTerrainFacesAccepted > acceptedFacesBefore)
				{
					debugTerrainTilesAccepted++;
				}
			}
			else
			{
				SceneTilePaint paint = tile.getSceneTilePaint();
				if (paint != null)
				{
					collectTerrainPaintOccluder(localPoint, tile.getPlane());
					if (debugTerrainFacesAccepted > acceptedFacesBefore)
					{
						debugTerrainTilesAccepted++;
					}
				}
			}
		}
		else
		{
			debugBridgeTerrainTilesSkipped++;
		}

		collectSceneTileObjectOccluders(tile, quality, visitedObjects, bridgeLinked, renderedSceneRenderables);
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
		if (!BillboardSceneryOcclusionFilter.isOccluder(tileObject))
		{
			debugSceneObjectsRejectedByFilter++;
			addDebugScenerySample(debugRejectedScenerySamples, tileObject);
			return;
		}

		if (!sceneryIntersectsInterest(tileObject))
		{
			debugSceneObjectsSkippedOutsideInterest++;
			return;
		}

		ObservedTileObject observed = ObservedTileObjectBuilder.build(tileObject);
		if (observed == null || observed.parts.isEmpty())
		{
			debugSceneObjectsWithoutParts++;
			return;
		}
		if (bridgeLinked && !hasRenderedPart(observed, renderedSceneRenderables))
		{
			debugBridgeSceneryNotRendered++;
			return;
		}

		int acceptedFacesBefore = debugSceneObjectFacesAccepted;
		for (ObjectRenderablePart part : observed.parts)
		{
			collectSceneObjectPartOccluders(part, quality, debugOccluderSource(tileObject, "model"));
		}

		boolean acceptedModelFaces = debugSceneObjectFacesAccepted > acceptedFacesBefore;
		boolean acceptedFallback = !acceptedModelFaces && collectSceneObjectFallbackOccluder(tileObject, observed, quality);
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
		double fallbackDepth = tileObjectDepth(tileObject, observed, quality);
		if (!Double.isFinite(fallbackDepth))
		{
			return false;
		}

		if (addSceneryShapeFallbackOccluder(tileObject, observed, fallbackDepth, quality))
		{
			return true;
		}

		return addFallbackWorldOccluder(tileObject.getClickbox(), firstPart(observed), fallbackDepth, debugOccluderSource(tileObject, "fallback-clickbox"));
	}

	private boolean addSceneryShapeFallbackOccluder(
		TileObject tileObject,
		ObservedTileObject observed,
		double fallbackDepth,
		BillboardOcclusionQuality quality)
	{
		if (tileObject instanceof GameObject)
		{
			return addFallbackWorldOccluder(((GameObject) tileObject).getConvexHull(), part(observed, 0), partDepth(observed, 0, fallbackDepth, quality), debugOccluderSource(tileObject, "fallback-hull"));
		}

		if (tileObject instanceof WallObject)
		{
			WallObject wallObject = (WallObject) tileObject;
			boolean addedFirst = addFallbackWorldOccluder(wallObject.getConvexHull(), part(observed, 0), partDepth(observed, 0, fallbackDepth, quality), debugOccluderSource(tileObject, "fallback-hull-1"));
			boolean addedSecond = addFallbackWorldOccluder(wallObject.getConvexHull2(), part(observed, 1), partDepth(observed, 1, fallbackDepth, quality), debugOccluderSource(tileObject, "fallback-hull-2"));
			if (!addedFirst || !addedSecond)
			{
				return addFallbackWorldOccluder(tileObject.getClickbox(), firstPart(observed), fallbackDepth, debugOccluderSource(tileObject, "fallback-clickbox")) || addedFirst || addedSecond;
			}
			return true;
		}

		return false;
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

	private double partDepth(ObservedTileObject observed, int partIndex, double fallbackDepth, BillboardOcclusionQuality quality)
	{
		if (observed == null || partIndex < 0 || partIndex >= observed.parts.size())
		{
			return fallbackDepth;
		}

		double depth = conservativeModelVertexDepth(observed.parts.get(partIndex), quality.vertexStride());
		return Double.isFinite(depth) ? depth : fallbackDepth;
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
		if (tileObject == null)
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
				verticesX[c], verticesY[c], verticesZ[c],
				source
			);
		}
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
		if (!isUnevenTerrainTriangle(z0, z1, z2))
		{
			debugFlatTerrainFacesSkipped++;
			return;
		}

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
				"terrain plane=" + plane
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

	private double tileObjectDepth(TileObject tileObject, ObservedTileObject observed, BillboardOcclusionQuality quality)
	{
		LocalPoint localPoint = tileObject.getLocalLocation();
		if (localPoint == null)
		{
			return Double.NaN;
		}

		double furthest = Double.NEGATIVE_INFINITY;
		if (observed != null && !observed.parts.isEmpty())
		{
			for (ObjectRenderablePart part : observed.parts)
			{
				double depth = conservativeModelVertexDepth(part, quality.vertexStride());
				if (Double.isFinite(depth))
				{
					furthest = Math.max(furthest, depth);
				}
			}
		}

		if (Double.isFinite(furthest))
		{
			return furthest;
		}

		return depthCalculator.cameraForwardDepth(localPoint, tileObject.getPlane(), 0.0d);
	}

	private double conservativeModelVertexDepth(ObjectRenderablePart part, int vertexStride)
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
		// A fallback hull has screen-space coverage but no per-pixel world position. Using
		// its nearest vertex as a constant depth assigns the closest corner to the whole
		// shape and clips billboards that are actually in front of the wall. Prefer the
		// furthest sampled vertex: ambiguous intersections remain visible, while objects
		// definitely behind the complete hull are still occluded.
		double furthest = Double.NEGATIVE_INFINITY;
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

		return Double.isFinite(furthest)
			? furthest
			: depthCalculator.cameraForwardDepth(part.localPoint, part.plane, Math.max(0, part.renderable.getModelHeight() / 2.0d));
	}

	private boolean addFallbackWorldOccluder(Shape shape, ObjectRenderablePart part, double flatDepth, String source)
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
