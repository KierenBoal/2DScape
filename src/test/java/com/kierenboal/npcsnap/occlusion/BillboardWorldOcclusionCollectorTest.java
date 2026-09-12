package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.BillboardConstants;
import com.kierenboal.npcsnap.targeting.ClassifiedObjectType;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;
import com.kierenboal.npcsnap.targeting.ObservedTileObject;
import com.kierenboal.npcsnap.TestProxies;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.runelite.api.Renderable;
import net.runelite.api.Model;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BillboardWorldOcclusionCollectorTest
{
	@Test
	public void hiddenFacesUseSeparateUnsignedTransparencyAndColorSentinel()
	{
		Model staticModel = proxy(Model.class);
		// This proxy's inherited getModel() returns null: a static Model must be used directly.
		assertSame(staticModel, BillboardWorldOcclusionCollector.occlusionModel(staticModel));
		assertSame(staticModel, BillboardWorldOcclusionCollector.occlusionModel(
			proxy(Renderable.class, method("getModel", staticModel))));
		int[] colors = {-2, -1, 0, 1234, 1234};
		byte[] transparencies = {0, 0, 0, (byte) 255, (byte) 128};
		assertTrue(BillboardWorldOcclusionCollector.isInvisibleFace(0, colors, transparencies));
		assertFalse(BillboardWorldOcclusionCollector.isInvisibleFace(1, colors, transparencies));
		assertFalse(BillboardWorldOcclusionCollector.isInvisibleFace(2, colors, transparencies));
		assertTrue(BillboardWorldOcclusionCollector.isInvisibleFace(3, colors, transparencies));
		assertFalse(BillboardWorldOcclusionCollector.isInvisibleFace(4, colors, transparencies));
		assertFalse(BillboardWorldOcclusionCollector.isInvisibleFace(0, null, null));
	}
	@Test
	public void directCandidatePlanesStartAtTheCurrentPlane()
	{
		assertArrayEquals(new int[] {0, 1, 2, 3}, BillboardWorldOcclusionCollector.directCandidatePlanes(0, 4));
		assertArrayEquals(new int[] {2, 3}, BillboardWorldOcclusionCollector.directCandidatePlanes(2, 4));
		assertArrayEquals(new int[] {3}, BillboardWorldOcclusionCollector.directCandidatePlanes(3, 4));
		assertArrayEquals(new int[0], BillboardWorldOcclusionCollector.directCandidatePlanes(4, 4));
	}

	@Test
	public void classifiesUpperPlaneTilesWithoutRoofGroupsAsNonRoof()
	{
		SceneFixture fixture = sceneFixture();
		BillboardWorldOcclusionCollector.RoofClassification classification =
			BillboardWorldOcclusionCollector.classifyRoof(fixture.scene, fixture.tile(1, 0, 0));

		assertEquals(BillboardWorldOcclusionCollector.RoofClassificationKind.NON_ROOF, classification.kind);
		assertEquals(0, classification.roofId);
	}

	@Test
	public void classifiesVisibleBelowTilesAsNonRoof()
	{
		SceneFixture fixture = sceneFixture();
		fixture.settings[1][fixture.offset][fixture.offset] = 8;

		BillboardWorldOcclusionCollector.RoofClassification classification =
			BillboardWorldOcclusionCollector.classifyRoof(fixture.scene, fixture.tile(1, 0, 0));

		assertEquals(BillboardWorldOcclusionCollector.RoofClassificationKind.NON_ROOF, classification.kind);
		assertTrue(classification.visibleBelow);
	}

	@Test
	public void classifiesRoofGroupedTilesAndRejectsGpuUploadEvidence()
	{
		SceneFixture fixture = sceneFixture();
		fixture.roofs[0][fixture.offset][fixture.offset] = 42;

		BillboardWorldOcclusionCollector.RoofClassification classification =
			BillboardWorldOcclusionCollector.classifyRoof(fixture.scene, fixture.tile(1, 0, 0));

		assertEquals(BillboardWorldOcclusionCollector.RoofClassificationKind.ROOF, classification.kind);
		assertEquals(42, classification.roofId);
		assertTrue(BillboardWorldOcclusionCollector.canUseRenderedRoofEvidence(false, true));
		assertFalse(BillboardWorldOcclusionCollector.canUseRenderedRoofEvidence(false, false));
		assertFalse(BillboardWorldOcclusionCollector.canUseRenderedRoofEvidence(true, true));
	}

	@Test
	public void rejectsRoofClassificationWhenMetadataIsOutOfBounds()
	{
		SceneFixture fixture = sceneFixture();
		BillboardWorldOcclusionCollector.RoofClassification classification =
			BillboardWorldOcclusionCollector.classifyRoof(fixture.scene, fixture.tile(1, 900, 900));

		assertEquals(BillboardWorldOcclusionCollector.RoofClassificationKind.UNKNOWN, classification.kind);
	}

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

	@Test
	public void modelGeometryCachesConservativeBounds()
	{
		Model model = proxy(
			Model.class,
			method("getVerticesCount", 3),
			method("getFaceCount", 1),
			method("getVerticesX", new float[]{-64.0f, 32.0f, 96.0f}),
			method("getVerticesY", new float[]{-180.0f, 12.0f, -40.0f}),
			method("getVerticesZ", new float[]{48.0f, -80.0f, 16.0f}),
			method("getFaceIndices1", new int[]{0}),
			method("getFaceIndices2", new int[]{1}),
			method("getFaceIndices3", new int[]{2}));

		BillboardWorldOcclusionCollector.ModelGeometry geometry =
			BillboardWorldOcclusionCollector.ModelGeometry.from(model);

		assertTrue(geometry.hasBounds);
		assertEquals(-64.0f, geometry.minX, 0.0f);
		assertEquals(96.0f, geometry.maxX, 0.0f);
		assertEquals(-180.0f, geometry.minY, 0.0f);
		assertEquals(12.0f, geometry.maxY, 0.0f);
		assertEquals(-80.0f, geometry.minZ, 0.0f);
		assertEquals(48.0f, geometry.maxZ, 0.0f);
		assertEquals(3, geometry.vertexCount);
		assertEquals(1, geometry.faceCount);
	}

	@Test
	public void modelGeometryRejectsNonFiniteBounds()
	{
		Model model = proxy(
			Model.class,
			method("getVerticesCount", 1),
			method("getFaceCount", 0),
			method("getVerticesX", new float[]{Float.NaN}),
			method("getVerticesY", new float[]{0.0f}),
			method("getVerticesZ", new float[]{0.0f}));

		assertFalse(BillboardWorldOcclusionCollector.ModelGeometry.from(model).hasBounds);
	}

	@Test
	public void fallbackShapeMustExistAndHaveArea()
	{
		assertFalse(BillboardWorldOcclusionCollector.isUsableFallbackShape(null));
		assertFalse(BillboardWorldOcclusionCollector.isUsableFallbackShape(new Rectangle2D.Double(10, 10, 0, 20)));
		assertTrue(BillboardWorldOcclusionCollector.isUsableFallbackShape(new Rectangle(10, 10, 20, 20)));
	}

	private static Set<Tile> identitySet()
	{
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	private static SceneFixture sceneFixture()
	{
		byte[][][] settings = new byte[4][184][184];
		int[][][] roofs = new int[4][184][184];
		net.runelite.api.Scene scene = proxy(
			net.runelite.api.Scene.class,
			method("getExtendedTileSettings", settings),
			method("getRoofs", roofs));
		return new SceneFixture(scene, settings, roofs, 40);
	}

	private static final class SceneFixture
	{
		private final net.runelite.api.Scene scene;
		private final byte[][][] settings;
		private final int[][][] roofs;
		private final int offset;

		private SceneFixture(net.runelite.api.Scene scene, byte[][][] settings, int[][][] roofs, int offset)
		{
			this.scene = scene;
			this.settings = settings;
			this.roofs = roofs;
			this.offset = offset;
		}

		private Tile tile(int plane, int sceneX, int sceneY)
		{
			return proxy(Tile.class, method("getPlane", plane), method("getSceneLocation", new Point(sceneX, sceneY)));
		}
	}
}
