package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.hooks.DrawCallbacks;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class BillboardRoofOcclusionTest
{
	@Test
	public void paintedAndModelledRoofsFollowHoverWithoutLosingWallOcclusion()
	{
		for (boolean modelled : new boolean[] {false, true})
		{
			Fixture f = new Fixture(modelled);
			for (Set<Integer> hidden : List.of(Set.<Integer>of(), Set.of(42), Set.<Integer>of()))
			{
				f.collect(hidden, 3);
				boolean visible = hidden.isEmpty();
				assertEquals("Roof surface", visible, f.blocked(576, 1088, -240));
				assertEquals("Roof trim", visible, f.blocked(670, 1088, -300));
				assertTrue("Foreground wall survives roof removal", f.blocked(576, 1000, -40));
				if (visible)
				{
					Point p = f.camera.localToCanvas(576, 1088, -240);
					assertEquals("Roof keeps actual depth", f.camera.cameraForwardDepth(576, 1088, -240),
						f.mask.depthAt(p.getX(), p.getY()), 3d);
				}
			}
			f.collect(Set.of(), 0);
			assertFalse(f.blocked(576, 1088, -240));
			assertFalse(f.blocked(670, 1088, -300));
			assertTrue(f.blocked(576, 1000, -40));
		}
	}

	@Test
	public void selectiveGroupsAndFloorChangesReuseCompleteGeometry()
	{
		Fixture f = new Fixture(false);
		f.tiles[1][5][8] = f.tile(1, 5, 8, 1, null, proxy(SceneTilePaint.class), null);
		f.roofs[0][45][48] = 17;
		f.collect(Set.of(42), 3);
		assertFalse(f.blocked(576, 1088, -240));
		assertTrue(f.blocked(730, 1088, -240));
		f.plane.set(1);
		f.collect(Set.of(), 3);
		assertFalse("Current floor flat terrain stays excluded", f.blocked(576, 1088, -240));
		f.plane.set(0);
		f.collect(Set.of(), 3);
		assertTrue("Returning below reuses flat roof geometry", f.blocked(576, 1088, -240));
	}

	@Test
	public void missingSnapshotDoesNotAddUpperSurfacesAndVisBelowRemainsVisible()
	{
		Fixture f = new Fixture(false);
		f.visibility.beginFrame();
		f.gather();
		assertFalse(f.blocked(576, 1088, -240));
		f.flags[1][44][48] = Constants.TILE_FLAG_VIS_BELOW;
		f.collect(Set.of(42), 0);
		assertTrue(f.blocked(576, 1088, -240));
	}

	@Test
	public void paintUsesRenderLevelCornersWithoutBridgeHeightAdjustment()
	{
		Fixture f = new Fixture(false);
		f.tiles[1][4][8] = f.tile(1, 4, 8, 2, null, proxy(SceneTilePaint.class), null);
		f.flags[1][44][48] = Constants.TILE_FLAG_BRIDGE;
		f.collect(Set.of(), 3);
		assertTrue(f.blocked(576, 1088, -480));
		Point p = f.camera.localToCanvas(576, 1088, -480);
		// Rasterized integer vertices/pixels introduce a few units of interpolation error.
		assertEquals(f.camera.cameraForwardDepth(576, 1088, -480), f.mask.depthAt(p.getX(), p.getY()), 6d);
	}

	private static final class Fixture
	{
		final Tile[][][] tiles = new Tile[4][104][104];
		final int[][][] roofs = new int[4][184][184];
		final byte[][][] flags = new byte[4][184][184];
		final AtomicInteger plane = new AtomicInteger();
		final AtomicReference<DrawCallbacks> callbacks = new AtomicReference<>(new DrawCallbacks()
		{
			@Override public void draw(int color) { }
			@Override public void swapScene(Scene scene) { }
		});
		final BillboardSceneVisibility visibility = new BillboardSceneVisibility(callbacks::get, callbacks::set);
		final BillboardDepthCalculator camera;
		final BillboardWorldOcclusionCollector collector;
		final BillboardOcclusionMask mask = new BillboardOcclusionMask();
		final Scene scene;
		final WorldView view;

		Fixture(boolean modelled)
		{
			int[][][] heights = new int[4][185][185];
			for (int p = 0; p < 4; p++) for (int[] row : heights[p]) Arrays.fill(row, -240 * p);
			scene = proxy(Scene.class, method("getWorldViewId", WorldView.TOPLEVEL), method("getTiles", tiles),
				method("getTileHeights", heights), method("getRoofs", roofs), method("getExtendedTileSettings", flags));
			view = proxy(WorldView.class, method("getScene", scene), methodSupplier("getPlane", plane::get),
				method("getTileHeights", new int[4][105][105]), method("getTileSettings", new byte[4][104][104]));
			Client client = proxy(Client.class, method("isGpu", true), method("getTopLevelWorldView", view),
				method("getWorldView", view), method("getLocalPlayer", proxy(Player.class, method("getWorldView", view))),
				method("getCameraFpX", 576f), method("getCameraFpZ", -700f), method("getCameraFpPitch", .5f),
				method("getScale", 512), method("getViewportWidth", 800), method("getViewportHeight", 600));
			camera = new BillboardDepthCalculator(client);
			camera.beginRender();
			collector = new BillboardWorldOcclusionCollector(client, new NpcSnapConfig() {}, camera,
				new BillboardPerformanceMetrics(), org.slf4j.LoggerFactory.getLogger(getClass()), visibility);
			roofs[0][44][48] = 42;
			SceneTileModel model = modelled ? proxy(SceneTileModel.class,
				method("getVertexX", new int[] {512, 640, 640, 512}),
				method("getVertexY", new int[] {-240, -240, -240, -240}),
				method("getVertexZ", new int[] {1024, 1024, 1152, 1152}),
				method("getFaceX", new int[] {0, 0}), method("getFaceY", new int[] {1, 2}),
				method("getFaceZ", new int[] {2, 3})) : null;
			tiles[1][4][8] = tile(1, 4, 8, 1, model, modelled ? null : proxy(SceneTilePaint.class), wall(670, 1088, -240));
			tiles[0][4][7] = tile(0, 4, 7, 0, null, proxy(SceneTilePaint.class), wall(576, 1000, 0));
		}

		Tile tile(int p, int x, int y, int renderLevel, SceneTileModel model, SceneTilePaint paint, WallObject wall)
		{
			return proxy(Tile.class, method("getPlane", p), method("getRenderLevel", renderLevel),
				method("getSceneLocation", new Point(x, y)), method("getLocalLocation", new LocalPoint(x * 128 + 64, y * 128 + 64)),
				method("getSceneTileModel", model), method("getSceneTilePaint", paint), method("getWallObject", wall));
		}

		WallObject wall(int x, int y, int z)
		{
			Model model = proxy(Model.class, method("getVerticesCount", 3), method("getFaceCount", 1),
				method("getVerticesX", new float[] {-45, 45, 0}), method("getVerticesY", new float[] {0, 0, -160}),
				method("getVerticesZ", new float[3]), method("getFaceIndices1", new int[] {0}),
				method("getFaceIndices2", new int[] {1}), method("getFaceIndices3", new int[] {2}),
				method("getFaceColors3", new int[] {5000}));
			return proxy(WallObject.class, method("getLocalLocation", new LocalPoint(x, y)),
				method("getZ", z), method("getRenderable1", model));
		}

		void collect(Set<Integer> hidden, int max)
		{
			visibility.beginFrame();
			callbacks.get().preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, plane.get(), max, hidden);
			gather();
		}

		void gather()
		{
			collector.resetFrame();
			collector.addInterest(new Rectangle(0, 0, 800, 600), new Rectangle(0, 0, 800, 600));
			collector.collectTerrainOccluders(view, List.of(new BillboardWorldOcclusionCollector.TraceTarget(new LocalPoint(576, 1200), plane.get())),
				BillboardOcclusionQuality.HIGH, Collections.emptySet());
			mask.prepare(collector.occluders(), BillboardOcclusionQuality.HIGH, 0, 0, 800, 600);
		}

		boolean blocked(int x, int y, int z)
		{
			Point pixel = camera.localToCanvas(x, y, z);
			// A sprite pixel only 100 units behind the surface exposes the old 256-unit ground bias.
			return mask.isOccluded(pixel.getX(), pixel.getY(), camera.cameraForwardDepth(x, y, z) + 100);
		}
	}
}
