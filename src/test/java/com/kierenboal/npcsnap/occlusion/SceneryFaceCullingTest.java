package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class SceneryFaceCullingTest
{
	@Test public void acceleratedStaticFacesMatchFullTraversalWithLessWork()
	{
		Fixture accelerated = new Fixture(true), reference = new Fixture(false);
		BillboardOcclusionMask fast = accelerated.collect(), full = reference.collect();
		assertTrue(accelerated.metrics.counters().get("Faces inspected") < reference.metrics.counters().get("Faces inspected"));
		assertTrue(accelerated.collector.occluders().size() > 0);
		assertEquals(reference.collector.occluders().size(), accelerated.collector.occluders().size());
		for (int y = 220; y < 320; y += 2)
		{
			for (int x = 350; x < 450; x += 2) assertEquals(full.transmittanceAt(x, y, 2048d), fast.transmittanceAt(x, y, 2048d));
		}
	}

	@Test public void mutableVertexArraysRefreshBetweenFrames()
	{
		Fixture fixture = new Fixture(false);
		fixture.collect();
		assertTrue(fixture.collector.occluders().size() > 0);
		for (int i = 0; i < fixture.x.length; i++) fixture.x[i] += 20_000f;
		fixture.collect();
		assertEquals(0, fixture.collector.occluders().size());
	}

	@Test public void objectEventsInvalidateCachedStaticBounds()
	{
		Fixture fixture = new Fixture(true);
		fixture.collect();
		for (int i = 0; i < fixture.x.length; i++) fixture.x[i] += 20_000f;
		fixture.collector.observeNestedTile(fixture.tile, fixture.view);
		fixture.collect();
		assertEquals(0, fixture.collector.occluders().size());
	}

	private static final class Fixture
	{
		final AtomicLong clock = new AtomicLong();
		final BillboardPerformanceMetrics metrics = new BillboardPerformanceMetrics(clock::get);
		final BillboardWorldOcclusionCollector collector;
		final WorldView view;
		final Tile tile;
		final float[] x = new float[768];
		Fixture(boolean stable)
		{
			float[] y = new float[x.length], z = new float[x.length];
			int[] a = new int[256], b = new int[256], c = new int[256];
			byte[] alpha = new byte[256];
			for (int face = 0; face < 256; face++)
			{
				int vertex = face * 3;
				float center = (face - 128) * 32;
				x[vertex] = center - 16; x[vertex + 1] = center + 16; x[vertex + 2] = center;
				y[vertex + 2] = -128;
				a[face] = vertex; b[face] = vertex + 1; c[face] = vertex + 2;
				alpha[face] = (byte) (face % 2 == 0 ? 128 : 0);
			}
			Model model = proxy(Model.class, method("getVerticesCount", x.length), method("getFaceCount", a.length), method("getModelHeight", 200),
				method("getVerticesX", x), method("getVerticesY", y), method("getVerticesZ", z),
				method("getFaceIndices1", a), method("getFaceIndices2", b), method("getFaceIndices3", c), method("getFaceTransparencies", alpha));
			Renderable renderable = stable ? model : proxy(Renderable.class, method("getModel", model), method("getModelHeight", 200));
			LocalPoint location = new LocalPoint(512, 1024);
			GameObject object = proxy(GameObject.class, method("getLocalLocation", location), method("getRenderable", renderable));
			Tile[][][] tiles = new Tile[4][16][16];
			tile = proxy(Tile.class, method("getPlane", 0), method("getSceneLocation", new Point(4, 8)), method("getGameObjects", new GameObject[] {object}));
			tiles[0][4][8] = tile;
			view = proxy(WorldView.class, method("isTopLevel", true), method("getScene", proxy(Scene.class, method("getTiles", tiles))),
				method("getTileSettings", new byte[4][16][16]), method("getTileHeights", new int[4][17][17]));
			Client client = proxy(Client.class, method("getWorldView", view), method("getTopLevelWorldView", view),
				method("getLocalPlayer", proxy(Player.class, method("getWorldView", view))), method("getCameraX", 512), method("getCameraFpX", 512f),
				method("getScale", 512), method("get3dZoom", 512), method("getViewportWidth", 800), method("getViewportHeight", 600));
			collector = new BillboardWorldOcclusionCollector(client, new NpcSnapConfig() {}, new BillboardDepthCalculator(client), metrics, org.slf4j.LoggerFactory.getLogger(getClass()));
		}
		BillboardOcclusionMask collect()
		{
			metrics.beginFrame(true);
			collector.resetFrame();
			Rectangle region = new Rectangle(350, 220, 100, 100);
			collector.addInterest(region, new Rectangle(0, 0, 800, 600));
			collector.collectTerrainOccluders(view, List.of(new BillboardWorldOcclusionCollector.TraceTarget(new LocalPoint(512, 1024), 0)), BillboardOcclusionQuality.HIGH);
			collector.publishCounts();
			clock.addAndGet(500_000_000L);
			metrics.finishFrame();
			BillboardOcclusionMask mask = new BillboardOcclusionMask();
			mask.prepare(collector.occluders(), BillboardOcclusionQuality.HIGH, 0, 0, 800, 600, region);
			return mask;
		}
	}
}
