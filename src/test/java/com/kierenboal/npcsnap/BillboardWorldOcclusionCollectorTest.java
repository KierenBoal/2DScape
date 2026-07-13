package com.kierenboal.npcsnap;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.Renderable;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BillboardWorldOcclusionCollectorTest
{
	@Test
	public void triangleBoundsIncludesAllEdgePixelsWithoutPolygonAllocation()
	{
		assertEquals(
			new Rectangle(2, 3, 9, 10),
			BillboardWorldOcclusionCollector.triangleBounds(
				new Point(2, 8), new Point(10, 3), new Point(6, 12)));
	}

	@Test
	public void addsDirectTileAndBridgeTileOnDifferentPlane()
	{
		Tile bridge = proxy(Tile.class, method("getPlane", 1));
		Tile direct = proxy(Tile.class, method("getPlane", 0), method("getBridge", bridge));
		List<Tile> tiles = new ArrayList<>();

		int bridgeTilesAdded = BillboardWorldOcclusionCollector.addTileAndBridgeTiles(direct, identitySet(), tiles);

		assertEquals(1, bridgeTilesAdded);
		assertEquals(2, tiles.size());
		assertSame(direct, tiles.get(0));
		assertSame(bridge, tiles.get(1));
	}

	@Test
	public void sharedBridgeTileIsIncludedOnce()
	{
		Tile bridge = proxy(Tile.class);
		Tile first = proxy(Tile.class, method("getBridge", bridge));
		Tile second = proxy(Tile.class, method("getBridge", bridge));
		List<Tile> tiles = new ArrayList<>();
		Set<Tile> visitedTiles = identitySet();

		assertEquals(1, BillboardWorldOcclusionCollector.addTileAndBridgeTiles(first, visitedTiles, tiles));
		assertEquals(0, BillboardWorldOcclusionCollector.addTileAndBridgeTiles(second, visitedTiles, tiles));
		assertEquals(3, tiles.size());
		assertSame(bridge, tiles.get(1));
	}

	@Test
	public void cyclicBridgeChainStopsAfterEachTileIsIncluded()
	{
		Tile[] cycle = new Tile[2];
		cycle[0] = proxy(Tile.class, methodSupplier("getBridge", () -> cycle[1]));
		cycle[1] = proxy(Tile.class, methodSupplier("getBridge", () -> cycle[0]));
		List<Tile> tiles = new ArrayList<>();

		assertEquals(1, BillboardWorldOcclusionCollector.addTileAndBridgeTiles(cycle[0], identitySet(), tiles));
		assertEquals(2, tiles.size());
		assertSame(cycle[0], tiles.get(0));
		assertSame(cycle[1], tiles.get(1));
	}

	@Test
	public void rejectsTriangleWhollyBehindOrOnNearPlane()
	{
		assertFalse(BillboardWorldOcclusionCollector.hasForwardVertex(-120.0d, 0.0d, BillboardConstants.MIN_FORWARD_VISIBLE_DEPTH));
	}

	@Test
	public void keepsTriangleThatCrossesIntoCameraFrustum()
	{
		assertTrue(BillboardWorldOcclusionCollector.hasForwardVertex(-120.0d, 0.0d, BillboardConstants.MIN_FORWARD_VISIBLE_DEPTH + 0.1d));
	}

	@Test
	public void rejectsFallbackOccluderBehindOrOnNearPlane()
	{
		assertFalse(BillboardWorldOcclusionCollector.isForwardOccluderDepth(BillboardConstants.MIN_FORWARD_VISIBLE_DEPTH));
		assertFalse(BillboardWorldOcclusionCollector.isForwardOccluderDepth(-1.0d));
		assertTrue(BillboardWorldOcclusionCollector.isForwardOccluderDepth(BillboardConstants.MIN_FORWARD_VISIBLE_DEPTH + 0.1d));
	}

	@Test
	public void skipsLevelTerrainButKeepsSlopedTerrain()
	{
		assertFalse(BillboardWorldOcclusionCollector.isUnevenTerrainTriangle(320, 320, 320));
		assertTrue(BillboardWorldOcclusionCollector.isUnevenTerrainTriangle(320, 328, 320));
	}

	@Test
	public void bridgeSceneryRequiresAPartRenderedByRuneLite()
	{
		Renderable renderedPart = proxy(Renderable.class);
		ObservedTileObject observed = new ObservedTileObject(
			null,
			Collections.singletonList(new ObjectRenderablePart(renderedPart, null, 0)),
			ClassifiedObjectType.UNKNOWN
		);
		Set<Renderable> rendered = Collections.newSetFromMap(new IdentityHashMap<>());

		assertFalse(BillboardWorldOcclusionCollector.hasRenderedPart(observed, rendered));
		rendered.add(renderedPart);
		assertTrue(BillboardWorldOcclusionCollector.hasRenderedPart(observed, rendered));
	}

	private static Set<Tile> identitySet()
	{
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}
}
